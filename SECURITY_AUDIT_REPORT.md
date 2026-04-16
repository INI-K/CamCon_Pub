# CamCon Security Audit Report

**Audit Date**: 2026-04-15  
**Project**: CamCon - Android Camera Control App  
**Risk Level**: MEDIUM (Multiple HIGH issues found)

---

## Executive Summary

The CamCon application demonstrates reasonable security practices in several areas (HTTPS enforcement, cleartext traffic restrictions, proper Hilt injection), but contains **multiple HIGH-severity vulnerabilities** that must be remediated before production release.

### Critical Findings
- **CRITICAL x1**: Open Firestore security rules (allow read/write to all users)
- **HIGH x4**: User ID logging, no rate limiting, client-side subscription validation, unauthenticated PTP/IP protocol
- **MEDIUM x3**: Incomplete backup rules, exported broadcast receivers, missing CSRF protection

---

## 1. CRITICAL Issues

### C-1: Open Firestore Security Rules ⚠️ CRITICAL

**File**: `/Users/ini-k/CamCon/firestore.rules`  
**Severity**: CRITICAL  
**Type**: Broken Access Control (OWASP #5)  
**Status**: 🔴 **NOT FIXED**

**Issue**:
```rules
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    // 임시: 모든 접근 허용 (관리자 설정용)
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

The rules currently allow **ANY user (authenticated or not) to read and write ALL data** in Firestore.

**Risk**: 
- **Data Breach**: All user subscription data, auth tokens, purchase information exposed
- **Privilege Escalation**: Users can modify their own subscription tier to ADMIN
- **Subscription Bypass**: Users can grant themselves PRO access without paying
- **PII Exposure**: Email addresses, display names, photo URLs accessible to anyone

**Impact**: 
- Complete compromise of all user accounts and payment data
- Financial loss (lost subscription revenue)
- Regulatory exposure (GDPR violations for unencrypted PII exposure)

**Remediation**:
```rules
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    // Only authenticated users can access
    match /users/{userId} {
      // Users can only read their own documents
      allow read, write: if request.auth.uid == userId;
      
      // Subscription subcollection - read-only for users
      match /subscriptions/{document=**} {
        allow read: if request.auth.uid == userId;
        // Only Cloud Functions (admin) can write
        allow write: if false;
      }
    }
    
    // Deny all other access
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

**Action Required**: 
- [ ] **URGENT**: Deploy new Firestore rules to production immediately
- [ ] Audit user data for unauthorized modifications (tier changes, fraud)
- [ ] Review Firebase Console > Firestore > Audit Logs for unauthorized access
- [ ] Notify affected users if data breach detected
- [ ] Consider rotating user credentials after deployment

**Timeline**: Deploy within 24 hours

---

## 2. HIGH Issues

### H-1: User ID Exposed in Uncontrolled Logs

**File**: `/Users/ini-k/CamCon/app/src/main/java/com/inik/camcon/data/repository/SubscriptionRepositoryImpl.kt`  
**Lines**: 39, 40, 43-72  
**Severity**: HIGH  
**Type**: Sensitive Data Exposure (OWASP #3)  
**Status**: 🔴 **NOT FIXED**

**Issue**:
```kotlin
// Lines 38-40
android.util.Log.d("SubscriptionRepo", "📡 Firebase에서 구독 정보 조회 시작")
android.util.Log.d("SubscriptionRepo", "   사용자 ID: $userId")  // ⚠️ LOGS UID
android.util.Log.d("SubscriptionRepo", "   경로: users/$userId/subscriptions/current")
```

User IDs (Firebase UIDs) and paths containing UIDs are logged in plaintext. Unlike `LogcatManager` which respects `BuildConfig.DEBUG`, these direct `android.util.Log` calls may appear in:
- Device logcat (accessible to other apps via ADB)
- Crash reports
- Cloud logging services
- Developer logs shared in bug reports

**Risk**:
- UIDs visible to unauthorized parties
- UID leakage enables:
  - Targeted phishing via email + UID correlation
  - Firestore queries if rules are misconfigured (already an issue!)
  - User enumeration attacks

**Remediation**:
```kotlin
// Replace direct Log with LogcatManager
LogcatManager.d(TAG, "Firebase에서 구독 정보 조회 시작")
LogcatManager.d(TAG, "사용자 문서 조회 중...")  // Don't log actual UID
LogcatManager.d(TAG, "경로: users/{userId}/subscriptions/current")

// If UID logging is needed:
LogcatManager.d(TAG, "사용자 ID (last 4): ...${userId.takeLast(4)}")
```

**Action Required**:
- [ ] Replace all `android.util.Log` with `LogcatManager` in SubscriptionRepositoryImpl
- [ ] Search entire codebase for similar direct Log calls:
  ```bash
  grep -r "android\.util\.Log\|Log\.d\|Log\.i\|Log\.e" app/src/main --include="*.kt"
  ```
- [ ] Redact or truncate sensitive identifiers (UIDs, email, IP addresses) in logs
- [ ] Test in DEBUG and RELEASE builds to verify no logs leak

**Timeline**: 2-3 hours

---

### H-2: No Rate Limiting on API Endpoints

**Files**: 
- `SubscriptionRepositoryImpl` (lines 30-102)
- `AuthRemoteDataSourceImpl` (lines 15-51)  
**Severity**: HIGH  
**Type**: Insufficient Logging / DoS (OWASP #9, #10)  
**Status**: 🔴 **NOT IMPLEMENTED**

**Issue**:
No rate limiting on:
- `getUserSubscription()` - can call unlimited times (triggers Firestore read quota)
- `syncSubscriptionStatus()` - can call unlimited times (triggers Firestore write quota + Google Play Billing API)
- `signInWithGoogle()` - can be called repeatedly from different email accounts

**Risk**:
- **Brute-force Attacks**: Try thousands of user IDs to find valid accounts
- **DoS**: Exhaust Firestore read/write quotas (costs money, denies service to legitimate users)
- **Quota Exhaustion**: Trigger Firestore auto-scaling limits
- **Billing Attacks**: Malicious user repeatedly calls `syncSubscriptionStatus()` to inflate API costs

**Example Attack Vector**:
```kotlin
// Attacker code
for (i in 1..10000) {
    getSubscriptionUseCase.syncSubscriptionStatus()  // No throttling!
}
// Each call = 1 Firestore write operation
// 10,000 calls = $5 (at Firestore pricing)
```

**Remediation**:
```kotlin
// Add to SubscriptionRepositoryImpl
private val rateLimitMap = ConcurrentHashMap<String, Long>()
private const val MIN_SYNC_INTERVAL_MS = 5000L  // 5 second minimum

override suspend fun syncSubscriptionStatus() {
    try {
        val userId = firebaseAuth.currentUser?.uid ?: return
        
        // Check rate limit
        val lastSyncTime = rateLimitMap[userId] ?: 0
        if (System.currentTimeMillis() - lastSyncTime < MIN_SYNC_INTERVAL_MS) {
            logger.w(TAG, "Rate limit exceeded for subscription sync")
            return
        }
        
        // Perform sync
        val userId = firebaseAuth.currentUser?.uid ?: return
        val activeSubscriptions = billingDataSource.getActiveSubscriptions()
        // ... rest of sync logic
        
        // Update rate limit timestamp
        rateLimitMap[userId] = System.currentTimeMillis()
    } catch (e: Exception) {
        logger.e(TAG, "Sync failed", e)
    }
}
```

**Action Required**:
- [ ] Implement client-side rate limiting (5-10 second minimum between syncs)
- [ ] Set Firestore quotas in Firebase Console:
  - Read: 100 per minute per user
  - Write: 50 per minute per user
- [ ] Enable Cloud Monitoring alerts for unusual API usage
- [ ] Consider Cloud Function + Cloud Tasks for rate-limited operations

**Timeline**: 2-3 hours

---

### H-3: Client-Side Subscription Tier Validation (Bypassable)

**Files**: 
- `/Users/ini-k/CamCon/app/src/main/java/com/inik/camcon/domain/usecase/ValidateImageFormatUseCase.kt` (lines 38-63)
- `/Users/ini-k/CamCon/app/src/main/java/com/inik/camcon/utils/SubscriptionUtils.kt` (lines 44-63)  
**Severity**: HIGH  
**Type**: Broken Access Control (OWASP #5)  
**Status**: 🔴 **NOT FIXED**

**Issue**:
Subscription tier validation happens entirely on the client:

```kotlin
suspend fun isFormatSupported(filePath: String): Boolean {
    val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()
    val isSupported = SubscriptionUtils.isFormatSupported(format, currentTier)
    return isSupported  // ⚠️ Can be bypassed
}
```

**Bypass Methods**:
1. **Memory Patching**: Use Frida/Xposed to intercept `getSubscriptionTier()` and return PRO
2. **DataStore Modification**: Modify app's DataStore file (with root access or file extraction)
3. **Reverse Engineering**: Recompile APK with tier checks removed
4. **Mock Server**: Intercept Firebase calls via Burp Suite and return PRO tier

**Risk**:
- Users can access RAW files without paying for PRO ($10/month value)
- Access to PNG export, WebP export, advanced filters without subscription
- Zero revenue from subscription tier bypass
- Feature piracy affects business model

**Remediation** - Move validation to server:

1. **Cloud Function**:
```javascript
// Firebase Cloud Function
import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const validateImageFormat = functions.https.onCall(async (data, context) => {
  // 1. Verify user is authenticated
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User not authenticated");
  }
  
  const userId = context.auth.uid;
  const filePath = data.filePath;
  
  // 2. Get subscription from Firestore (server-side, cannot be modified by client)
  const subscriptionRef = admin.firestore()
    .collection("users")
    .doc(userId)
    .collection("subscriptions")
    .doc("current");
  
  const subscriptionDoc = await subscriptionRef.get();
  if (!subscriptionDoc.exists) {
    return { isSupported: false, reason: "No subscription found" };
  }
  
  const tier = subscriptionDoc.data()?.tier || "FREE";
  
  // 3. Validate on server
  const isRaw = isRawFile(filePath);
  const isSupported = isFormatSupportedForTier(filePath, tier);
  
  return {
    isSupported,
    format: getFormat(filePath),
    reason: isSupported ? "" : "Format not supported for your tier"
  };
});

function isRawFile(filePath: string): boolean {
  const rawExtensions = ["cr2", "cr3", "nef", "nrw", "arw", "orf", "dng"];
  const ext = filePath.split(".").pop()?.toLowerCase() || "";
  return rawExtensions.includes(ext);
}

function isFormatSupportedForTier(filePath: string, tier: string): boolean {
  const isRaw = isRawFile(filePath);
  if (isRaw) {
    return tier === "PRO" || tier === "ADMIN" || tier === "REFERRER";
  }
  return true;  // All tiers support JPG/JPEG
}
```

2. **Client calls Cloud Function**:
```kotlin
// In ValidateImageFormatUseCase
suspend fun isFormatSupported(filePath: String): Boolean {
    return try {
        val functions = FirebaseFunctions.getInstance()
        val result = functions.getHttpsCallable("validateImageFormat")
            .call(mapOf("filePath" to filePath))
            .await()
        
        @Suppress("UNCHECKED_CAST")
        (result.data as? Map<String, Any>)?.get("isSupported") as? Boolean ?: false
    } catch (e: Exception) {
        logger.e(TAG, "Server validation failed", e)
        false  // Fail securely
    }
}
```

**Action Required**:
- [ ] Create Cloud Function `validateImageFormat`
- [ ] Remove all client-side tier checks from ValidateImageFormatUseCase
- [ ] Update all file access code to call Cloud Function before allowing download
- [ ] Test that modified tier in DataStore doesn't grant access
- [ ] Monitor Cloud Function logs for abuse

**Timeline**: 4-6 hours

---

### H-4: Unauthenticated PTP/IP Protocol (No Auth/Encryption)

**Files**: 
- `/Users/ini-k/CamCon/app/src/main/java/com/inik/camcon/data/network/ptpip/authentication/NikonAuthenticationService.kt`
- `/Users/ini-k/CamCon/app/src/main/java/com/inik/camcon/data/network/ptpip/connection/PtpipConnectionManager.kt`  
**Severity**: HIGH  
**Type**: Broken Authentication + Insecure Communication (OWASP #2, #3)  
**Status**: ⚠️ **DESIGN LIMITATION**

**Issue**:
PTP/IP protocol operates over **plaintext TCP/UDP** on local Wi-Fi (port 15740):
- **NO encryption** - all commands visible to network sniffers
- **NO authentication** - any device on the network can control the camera
- **NO mutual authentication** - camera doesn't verify client identity

```kotlin
// File: NikonAuthenticationService.kt, line 83-86
commandSocket = Socket()
commandSocket.connect(
    InetSocketAddress(camera.ipAddress, camera.port),  // Plain TCP!
    5000
)
```

**Risk**:
- **Network Eavesdropping**: Any device on WiFi can observe commands (change settings, see shot count)
- **Man-in-the-Middle**: Attacker intercepts commands, modifies them (delete photos, change aperture)
- **Unauthorized Control**: Any app on same WiFi can control camera (if it knows IP)
- **Photo Theft**: Attacker commands camera to download all photos
- **Camera Hijacking**: Attacker changes settings (resolution, ISO, white balance)

**Example Attack on Coffee Shop WiFi**:
```
1. Attacker connects to same WiFi as photographer
2. Runs: nmap -p 15740 192.168.1.0/24  → Finds camera at 192.168.1.100
3. Connects to socket and sends unencrypted PTP/IP commands
4. Commands to download all photos to attacker's device
5. No authentication required - camera has no way to verify attacker
```

**Root Cause**: 
PTP/IP is a 2000s-era protocol designed for trusted, wired lab networks. Modern security wasn't a consideration.

**Remediation** (Partial - Protocol Limitation):

1. **User Warning**:
```kotlin
// Show in PtpipConnectionActivity
fun showSecurityWarning() {
    AlertDialog.Builder(context)
        .setTitle("⚠️ Wi-Fi Security Warning")
        .setMessage(
            "PTP/IP operates over unencrypted, unauthenticated Wi-Fi.\n\n" +
            "SECURITY RISKS:\n" +
            "• Other apps/devices on this Wi-Fi can control your camera\n" +
            "• Anyone can download your photos\n" +
            "• Unencrypted communication (network sniffing possible)\n\n" +
            "RECOMMENDATIONS:\n" +
            "• Only use on trusted private networks (home WiFi)\n" +
            "• Avoid public Wi-Fi (coffee shops, airports)\n" +
            "• Enable VPN if on untrusted networks\n" +
            "• Disconnect when not in use"
        )
        .setPositiveButton("I Understand, Continue", null)
        .setCancelable(false)
        .show()
}
```

2. **Ensure App Isolation** (Already correct):
```xml
<!-- AndroidManifest.xml - Services are not exported ✅ -->
<service
    android:name=".data.service.AutoConnectForegroundService"
    android:exported="false"  <!-- ✅ Prevents other apps from triggering -->
    android:foregroundServiceType="connectedDevice" />
```

3. **Network Verification** (Optional):
```kotlin
// Warn if on shared/public network
fun isSharedWifiNetwork(): Boolean {
    val networkName = getCurrentNetworkSsid()
    // Check against list of known public networks
    val publicNetworks = listOf("Starbucks", "Airport WiFi", "McDonald's", "Marriott", "Hilton")
    return publicNetworks.any { networkName?.contains(it, ignoreCase = true) ?: false }
}

if (isSharedWifiNetwork()) {
    showAdditionalSecurityWarning()
}
```

4. **Document for Users**:
Add to in-app Help/FAQ:
```
Q: Is PTP/IP secure?
A: PTP/IP is designed for trusted, wired lab networks from the early 2000s.
   It does not provide encryption or authentication.
   
ONLY use PTP/IP on:
✓ Your home Wi-Fi (personal network)
✓ Corporate networks with VPN

DO NOT use on:
✗ Public Wi-Fi (coffee shops, airports)
✗ Shared networks (dorms, hotels)
✗ Untrusted hotspots

If you must use PTP/IP on untrusted networks, connect via VPN first.
```

**Action Required**:
- [ ] Add prominent security warning dialog in PtpipConnectionActivity
- [ ] Document network security requirements in Help/FAQ
- [ ] Recommend VPN usage for untrusted networks
- [ ] Disable PTP/IP on public networks (optional: use SSID check)
- [ ] Monitor Firestore for unusual file downloads (bulk access patterns)

**Timeline**: 2-3 hours (mostly documentation + warnings)

---

## 3. MEDIUM Issues

### M-1: Incomplete Cloud Backup/Data Extraction Rules

**Files**:
- `/Users/ini-k/CamCon/app/src/main/res/xml/data_extraction_rules.xml`
- `/Users/ini-k/CamCon/app/src/main/res/xml/backup_rules.xml`  
**Severity**: MEDIUM  
**Type**: Data Protection (OWASP #3)  
**Status**: 🔴 **NOT FIXED**

**Issue**:
Both files are template placeholders with no actual rules:

```xml
<!-- data_extraction_rules.xml (empty, no includes/excludes) -->
<data-extraction-rules>
    <cloud-backup>
        <!-- TODO: Use <include> and <exclude> to control what is backed up. -->
    </cloud-backup>
</data-extraction-rules>
```

Without explicit rules, Android **defaults to backing up ALL app data**, including:
- DataStore files (app settings, subscription info)
- SharedPreferences (cached auth tokens)
- Databases
- Temp files

**Risk**:
- If user's Google account is compromised, attacker restores old backups
- Sensitive settings (cached subscription tier) restored on new device
- Data persists if user uninstalls + reinstalls (privacy issue)
- GDPR violation: unintended PII backup

**Remediation**:
```xml
<!-- data_extraction_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <!-- Exclude sensitive app settings -->
        <exclude domain="datastore" path="app_settings.preferences_pb" />
        
        <!-- Exclude subscription cache -->
        <exclude domain="sharedpref" path="subscription_cache.xml" />
        
        <!-- Exclude auth-related preferences -->
        <exclude domain="sharedpref" path="firebase_settings.xml" />
    </cloud-backup>
    
    <device-transfer>
        <!-- Don't transfer sensitive data to new devices via Quick Switch -->
        <exclude domain="datastore" path="app_settings.preferences_pb" />
        <exclude domain="sharedpref" path="subscription_cache.xml" />
    </device-transfer>
</data-extraction-rules>
```

```xml
<!-- backup_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <!-- Exclude sensitive preferences from full backup -->
    <exclude domain="sharedpref" path="firebase_settings.xml" />
    <exclude domain="sharedpref" path="subscription_cache.xml" />
</full-backup-content>
```

**Action Required**:
- [ ] Add explicit exclude rules for sensitive DataStore/SharedPreferences
- [ ] Test backup/restore to verify no sensitive data is included
- [ ] Document what data is backed up in Privacy Policy

**Timeline**: 1 hour

---

### M-2: Exported BroadcastReceiver with Implicit Intent Filters

**File**: `/Users/ini-k/CamCon/app/src/main/AndroidManifest.xml` (line 165-179)  
**Severity**: MEDIUM  
**Type**: Broken Access Control (OWASP #5)  
**Status**: 🟡 **PARTIALLY FIXED**

**Issue**:
```xml
<receiver
    android:name=".data.receiver.WifiSuggestionBroadcastReceiver"
    android:enabled="true"
    android:exported="true">  <!-- ⚠️ Visible to other apps -->
    <intent-filter>
        <action android:name="android.net.wifi.action.WIFI_NETWORK_SUGGESTION_POST_CONNECTION" />
        <action android:name="com.inik.camcon.action.AUTO_CONNECT_TRIGGER" />  <!-- Custom action! -->
        <action android:name="com.inik.camcon.action.AUTO_CONNECT_SUCCESS" />
        <action android:name="android.net.wifi.STATE_CHANGE" />
        <action android:name="android.net.wifi.supplicant.CONNECTION_CHANGE" />
    </intent-filter>
</receiver>
```

**Risks**:
- Malicious apps can send `AUTO_CONNECT_TRIGGER` broadcast to interfere with Wi-Fi
- DoS: Send rapid triggers to drain battery (continuous reconnection attempts)
- Redirect: Spoof WiFi connection events to trigger unwanted connections

**Remediation**:

Split into two receivers:
```xml
<!-- For system broadcasts only (required to be exported) -->
<receiver
    android:name=".data.receiver.WifiSuggestionBroadcastReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <!-- System broadcasts only, no custom actions -->
        <action android:name="android.net.wifi.action.WIFI_NETWORK_SUGGESTION_POST_CONNECTION" />
        <action android:name="android.net.wifi.STATE_CHANGE" />
        <action android:name="android.net.wifi.supplicant.CONNECTION_CHANGE" />
    </intent-filter>
</receiver>

<!-- For internal broadcasts only (do not export) -->
<receiver
    android:name=".data.receiver.InternalAutoConnectReceiver"
    android:enabled="true"
    android:exported="false">
    <intent-filter>
        <action android:name="com.inik.camcon.action.AUTO_CONNECT_TRIGGER" />
        <action android:name="com.inik.camcon.action.AUTO_CONNECT_SUCCESS" />
    </intent-filter>
</receiver>
```

Or register WiFi state receiver at runtime (preferred):
```kotlin
// In Application.onCreate() or MainActivity
val filter = IntentFilter().apply {
    addAction("android.net.wifi.STATE_CHANGE")
    addAction("android.net.wifi.supplicant.CONNECTION_CHANGE")
}

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    context.registerReceiver(
        wifiReceiver,
        filter,
        Context.RECEIVER_NOT_EXPORTED  // ✅ Not accessible to other apps
    )
} else {
    context.registerReceiver(wifiReceiver, filter)
}
```

**Action Required**:
- [ ] Remove custom intent-filter actions from manifest receiver
- [ ] Move WiFi state monitoring to runtime-registered receiver
- [ ] Verify custom broadcasts only work from app's own context

**Timeline**: 1-2 hours

---

### M-3: Missing CSRF Protection

**Severity**: MEDIUM  
**Type**: Cross-Site Request Forgery (OWASP #8)  
**Status**: 🔴 **NOT IMPLEMENTED**

**Issue**:
Firestore write operations lack CSRF token validation:

```kotlin
// SubscriptionRepositoryImpl.kt, line 148-154
firestore
    .collection(USERS_COLLECTION)
    .document(userId)
    .collection(SUBSCRIPTIONS_COLLECTION)
    .document("current")
    .set(subscriptionData)  // ⚠️ No CSRF token
    .await()
```

While less likely on native Android (not a web app with cookies), malicious activity via WebView or deep links could trigger mutations without CSRF tokens.

**Risk**: LOW for native app, but important for security posture if adding WebView components later.

**Remediation**:

1. **Server-side token generation**:
```javascript
// Cloud Function
export const generateCsrfToken = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new Error("Unauthenticated");
  
  const token = crypto.randomBytes(32).toString("hex");
  
  // Store token in Firestore with TTL
  await admin.firestore()
    .collection("csrf_tokens")
    .doc(token)
    .set({
      userId: context.auth.uid,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      // Auto-delete after 1 hour via TTL policy
    });
  
  return { csrfToken: token };
});
```

2. **Client includes token in mutations**:
```kotlin
// Get CSRF token
val csrfResult = FirebaseFunctions.getInstance()
    .getHttpsCallable("generateCsrfToken")
    .call()
    .await()

val csrfToken = (csrfResult.data as? Map<*, *>)?.get("csrfToken") as? String

// Include in Firestore write
firestore.collection("users").document(userId)
    .collection("subscriptions").document("current")
    .set(mapOf(
        "tier" to SubscriptionTier.PRO.name,
        "csrfToken" to csrfToken
    ))
    .await()
```

3. **Server validates token** (in Firestore rules):
```rules
match /subscriptions/{doc} {
  allow write: if 
    request.auth.uid == userId &&
    request.resource.data.csrfToken != null &&
    csrfTokenIsValid(request.resource.data.csrfToken);
}
```

**Action Required**:
- [ ] Implement CSRF token Cloud Function
- [ ] Add token to Firestore writes
- [ ] Validate token server-side before accepting mutations

**Timeline**: 3-4 hours

---

## 4. LOW Issues / Recommendations

### L-1: Excessive Direct Logging (Bypasses LogcatManager)

**Impact**: Debug information leakage  
**Recommendation**: Audit all `android.util.Log` calls:
```bash
grep -r "android\.util\.Log\|^\s*Log\." app/src/main --include="*.kt" | wc -l
```

Migrate all to LogcatManager for proper debug gating.

---

### L-2: BootCompletedReceiver Exported Flag

**File**: `/Users/ini-k/CamCon/app/src/main/AndroidManifest.xml` (line 182-189)  
**Issue**: `android:exported="true"` for system broadcast

```xml
<receiver
    android:name=".data.receiver.BootCompletedReceiver"
    android:exported="true">  <!-- Can be true for system broadcasts, but not recommended -->
```

**Recommendation**: Change to `android:exported="false"` - system broadcasts are still received:
```xml
<receiver
    android:name=".data.receiver.BootCompletedReceiver"
    android:exported="false">  <!-- ✅ System broadcasts still work -->
```

---

## 5. Security Controls Currently Implemented ✅

| Control | Status | Notes |
|---------|--------|-------|
| **HTTPS Enforcement** | ✅ | `network_security_config.xml` blocks cleartext for Firebase/Google |
| **Cleartext for Local Networks** | ✅ | Allowed only for 192.168.x, 10.x.x (PTP/IP - correct) |
| **Firebase Auth** | ✅ | Google Sign-In, no hardcoded secrets |
| **Dependency Injection** | ✅ | Hilt DI (no direct service instantiation) |
| **No Hardcoded Secrets** | ✅ | Verified via grep (no API keys in source) |
| **Controlled Logging** | ✅ | LogcatManager respects BuildConfig.DEBUG |
| **DataStore Encryption** | ✅ | Android DataStore (encrypted at rest on A12+) |
| **FileProvider** | ✅ | Secure URI grants for photo sharing |
| **USB Validation** | ✅ | Device ID validation (not universal acceptance) |

---

## 6. Remediation Timeline & Priority

### 🔴 Phase 1: CRITICAL (Before Production)
| # | Issue | Effort | Timeline |
|---|-------|--------|----------|
| 1 | **C-1: Fix Firestore Rules** | 1-2 hrs | **Deploy within 24 hours** |
| 2 | **H-1: Remove UID Logs** | 30 min | 2-3 hours |
| 3 | **H-3: Server-Side Validation** | 4-6 hrs | 1-2 days |

### 🟠 Phase 2: HIGH (Before Release)
| # | Issue | Effort | Timeline |
|---|-------|--------|----------|
| 4 | **H-2: Add Rate Limiting** | 2-3 hrs | 1 day |
| 5 | **H-4: PTP/IP Security Warnings** | 2-3 hrs | 1 day |

### 🟡 Phase 3: MEDIUM (Next Release)
| # | Issue | Effort | Timeline |
|---|-------|--------|----------|
| 6 | **M-1: Backup Rules** | 1 hr | 1-2 days |
| 7 | **M-2: Broadcast Receiver** | 1-2 hrs | 2 days |
| 8 | **M-3: CSRF Protection** | 3-4 hrs | 2-3 days |

**Total Remediation Effort**: ~20-25 hours over 2-3 weeks

---

## 7. Testing Checklist

**Phase 1 Testing** (Post Firestore Fix):
- [ ] Unauthenticated user cannot read user documents (403 error)
- [ ] User A cannot read User B's subscription (403 error)
- [ ] Non-admin cannot write subscription documents (403 error)
- [ ] Admin Cloud Function can update subscriptions (200 OK)

**Phase 2 Testing**:
- [ ] No UIDs in `adb logcat` output
- [ ] Rate limit prevents 5+ calls within 5 seconds
- [ ] Modify tier in DataStore doesn't grant access to RAW files
- [ ] Server returns 403 if subscription tier is forged

**Phase 3 Testing**:
- [ ] Backup/restore doesn't include sensitive DataStore files
- [ ] Custom broadcasts from other apps fail silently
- [ ] CSRF token validation blocks invalid tokens

---

## 8. Compliance Notes

**GDPR**:
- Firestore rules must prevent unauthorized PII access (personal email, photoUrl)
- Backup rules must exclude personal data

**Privacy Policy Must Disclose**:
- Firebase data collection (Analytics, Crashlytics, Remote Config)
- Local Wi-Fi sniffing risk (PTP/IP plaintext)
- Automatic cloud backup of app data

---

## 9. Conclusion

**Current Risk Level**: 🔴 **MEDIUM-HIGH** (multiple HIGH vulnerabilities)  
**Post-Remediation Risk**: 🟡 **MEDIUM** (baseline + known PTP/IP limitations)

**Recommendation**: 
1. **STOP** all production deployments until C-1 (Firestore rules) is fixed
2. Deploy emergency Firestore rules fix within 24 hours
3. Complete Phase 1 & 2 remediations before release
4. Plan Phase 3 for next sprint

---

**Report Generated**: 2026-04-15  
**Status**: 🔴 Ready for action  
**Escalation**: YES - Firestore rules require immediate DevOps attention
