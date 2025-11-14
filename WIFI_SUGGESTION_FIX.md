# WiFi Suggestion 자동 연결 문제 해결

## 문제점 분석

### 1. 자동 연결이 안되는 문제

#### 원인

1. **`setIsAppInteractionRequired(true)` 설정 오류**
    - `true`로 설정하면 사용자가 알림을 탭해야만 연결됨
    - 자동 연결을 위해서는 `false`로 설정 필요

2. **Android 버전별 설정 차이**
    - **Android 10 (API 29)**: `setIsInternetRequired(false)` 필요 (리플렉션 사용)
    - **Android 11 (API 30)**: `setCredentialSharedWithUser(true)` + `setIsMetered(false)` 필요
    - **Android 12 (API 31-32)**: `setCredentialSharedWithUser(true)` + `setIsMetered(false)` 필요
    - **Android 13+ (API 33)**: `setCredentialSharedWithUser(true)` + `setIsMetered(false)` 필요

3. **`setCredentialSharedWithUser(true)`의 중요성**
    - Android 11 이상에서 **자동 연결의 핵심** 설정
    - 이 설정이 없으면 시스템이 NetworkRequest가 필요하다며 suggestion을 무시
    - `setUntrusted(true)`와 동시 사용 불가 (서로 모순됨)

#### 수정 내용

```kotlin
// 수정 전
.setIsAppInteractionRequired(true)  // 사용자 상호작용 없이 자동 연결

// 수정 후
.setIsAppInteractionRequired(false)  // false로 변경: 사용자 상호작용 없이 자동 연결
.setPriority(Int.MAX_VALUE)  // 최고 우선순위로 자동 연결 유도
```

### 1-2. **추가 발견: Credential 공유 필요 (핵심!)**

#### 증상

시스템 로그에서 발견:

```
Ignoring network since it needs corresponding NetworkRequest: "Z_6_50007811"
```

네트워크를 감지했지만 "NetworkRequest가 필요하다"며 자동 연결을 무시함.

#### 원인

Android R(API 30) 이상에서 **`setCredentialSharedWithUser(true)`** 설정이 누락되어 있었습니다.

이 설정이 없으면:

- 시스템이 네트워크를 감지해도 자동 연결하지 않음
- 사용자가 명시적으로 연결 요청(`NetworkRequest`)을 해야만 연결됨

#### 중요한 제약사항

**`setCredentialSharedWithUser(true)`와 `setUntrusted(true)`는 동시 사용 불가!**

오류:

```
java.lang.IllegalStateException: Should not be both setCredentialSharedWithUser and setUntrusted or setRestricted to true
```

이유:

- `setCredentialSharedWithUser(true)`: 사용자 설정에 credential 공유 → **신뢰된 네트워크**
- `setUntrusted(true)`: 신뢰되지 않은 네트워크 (인터넷 없음) → **신뢰 안됨**

두 설정은 상충됩니다!

#### 최종 해결 방법

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    builder.setIsMetered(false)  // 데이터 요금 부과 네트워크가 아님
    builder.setCredentialSharedWithUser(true)  // ✅ 이것만 사용!
    // ❌ setUntrusted(true) 제거 - 동시 사용 불가
}
```

**`setCredentialSharedWithUser(true)` 의미:**

- WiFi credential(SSID, 비밀번호)을 사용자 설정에 공유
- 이렇게 해야 시스템이 앱의 suggestion을 신뢰하고 자동 연결함
- `false`(기본값)면 앱만 해당 네트워크에 연결 가능하고 시스템은 자동 연결 안함

**인터넷 없는 네트워크 처리:**

- Android R+에서는 `setCredentialSharedWithUser(true)`를 사용하면
- 시스템이 인터넷 검증을 자동으로 처리하고 연결을 유지함
- 카메라 AP처럼 인터넷이 없어도 연결 유지됨

### 2. 브로드캐스팅이 안되는 문제

#### 증상

자동 연결이 성공했지만, 브로드캐스트를 받지 못하는 문제

#### 해결 방법

**NetworkCallback을 사용한 직접 감지** (최종 해결책)

시스템 브로드캐스트에 의존하지 않고, `ConnectivityManager.NetworkCallback`으로 WiFi 연결 변화를 직접 모니터링:

```kotlin
// CamCon.kt (Application)
private fun startWifiAutoConnectMonitoring() {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            checkAndTriggerAutoConnect()
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                checkAndTriggerAutoConnect()
            }
        }
    }
    
    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()
        
    connectivityManager.registerNetworkCallback(request, networkCallback!!)
}

private fun checkAndTriggerAutoConnect() {
    applicationScope.launch {
        // 1. 자동 연결 설정 확인
        val isEnabled = preferencesDataSource.isAutoConnectEnabledNow()
        if (!isEnabled) return@launch
        
        // 2. 현재 SSID 확인
        val currentSSID = wifiNetworkHelper.getCurrentSSID()
        if (currentSSID.isNullOrEmpty()) return@launch
        
        // 3. 설정된 SSID와 일치하는지 확인
        val config = preferencesDataSource.getAutoConnectNetworkConfig()
        if (currentSSID != config?.ssid) return@launch
        
        // 4. 카메라 AP인지 확인
        if (!wifiNetworkHelper.isConnectedToCameraAP()) return@launch
        
        // 5. AutoConnectForegroundService 시작
        AutoConnectForegroundService.start(applicationContext, currentSSID)
    }
}
```

## 트러블슈팅

// ... existing code ...

## 등록 성공 후 추가 확인사항

### WiFi Suggestion 등록 성공 확인

다음 로그가 나타나면 등록 성공:

```
✅✅✅ WiFi Suggestion 등록 성공! ✅✅✅
📊 addNetworkSuggestions 결과 코드: 0
```

**시스템에 등록된 Suggestion 확인 (선택사항):**

1. 설정 → 네트워크 및 인터넷 → 인터넷 (WiFi)
2. 저장된 네트워크 보기
3. 앱이 제안한 네트워크에서 해당 SSID 확인

### 자동 연결이 작동하지 않는 경우

#### 1. 사용자 동의 필요 확인

처음 WiFi Suggestion을 등록할 때 시스템이 사용자에게 동의를 요청합니다.

**알림 확인:**

- 상단 알림바에 "CamConT가 WiFi 네트워크를 제안합니다" 알림이 올 수 있습니다
- 이 알림을 탭하여 "허용" 선택

**강제로 동의 상태 확인:**

```bash
# WiFi Suggestion 권한 상태 확인
adb shell dumpsys wifi | grep -A 20 "Network Suggestions"
```

#### 2. 수동으로 테스트 연결

자동 연결이 안 될 경우, 수동으로 먼저 연결 시도:

1. 설정 → WiFi → `Z_6_50007811` 선택
2. 비밀번호 입력 후 연결
3. 연결 성공 후 다시 끊기
4. 카메라 WiFi 껐다 켜기
5. 자동 연결되는지 확인

#### 3. WiFi가 자동 연결되어도 브로드캐스트가 안 오는 경우

**BroadcastReceiver 등록 확인:**

AndroidManifest.xml에서 다음 확인:

```xml
<receiver
    android:name=".data.receiver.WifiSuggestionBroadcastReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.net.wifi.action.WIFI_NETWORK_SUGGESTION_POST_CONNECTION" />
    </intent-filter>
</receiver>
```

**브로드캐스트 강제 발송 테스트:**

```bash
# 수동으로 브로드캐스트 전송 (테스트용)
adb shell am broadcast -a android.net.wifi.action.WIFI_NETWORK_SUGGESTION_POST_CONNECTION -p com.inik.camcon
```

#### 4. Android R+ 인터넷 검증 문제

Android 11(R) 이상에서는 `setCredentialSharedWithUser(true)` 설정이 필수입니다.

**확인 로그:**

```
Android R+ 인터넷 검증 제외 플래그 설정 완료
```

이 로그가 없으면 다시 빌드 필요.

#### 5. 앱이 백그라운드일 때 자동 연결

일부 기기에서는 앱이 백그라운드일 때 BroadcastReceiver가 제한될 수 있습니다.

**해결방법:**

- 배터리 최적화 제외 추가
- 앱을 포그라운드 상태로 유지

### 로그 모니터링 명령어

**전체 자동 연결 플로우:**

```bash
adb logcat -s WifiNetworkHelper:D WifiSuggestionReceiver:D AutoConnectManager:D AutoConnectTaskRunner:D AutoConnectFGService:D
```

**시스템 WiFi 상태:**

```bash
adb logcat -s WifiManager:D ConnectivityManager:D
```

**현재 등록된 Suggestion 확인:**

```bash
adb shell dumpsys wifi | grep -A 50 "Network Suggestions"
```

## 참고 자료

## Android 버전별 WiFi Suggestion 처리

다음은 Android 버전별 WiFi Suggestion 처리 로직입니다.

```kotlin
private fun buildWifiNetworkSuggestion(config: AutoConnectNetworkConfig): WifiNetworkSuggestion? {
    val builder = WifiNetworkSuggestion.Builder()
        .setSsid(config.ssid)
        .setIsHiddenSsid(config.isHidden)
        .setIsAppInteractionRequired(false)  // ✅ 자동 연결 활성화
        .setPriority(Int.MAX_VALUE)  // ✅ 최고 우선순위

    // 보안 설정 적용
    if (!applySecurityToSuggestion(builder, config)) {
        return null
    }

    // Android 버전별 추가 설정
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            // Android 13+ (API 33)
            builder.setIsMetered(false)
            builder.setCredentialSharedWithUser(true)
            Log.d(TAG, "Android 13+ 설정 완료")
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Android 12 (API 31-32)
            builder.setIsMetered(false)
            builder.setCredentialSharedWithUser(true)
            Log.d(TAG, "Android 12 설정 완료")
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            // Android 11 (API 30)
            builder.setIsMetered(false)
            builder.setCredentialSharedWithUser(true)
            Log.d(TAG, "Android 11 설정 완료")
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            // Android 10 (API 29) - 리플렉션 사용
            try {
                val method = builder.javaClass.getMethod(
                    "setIsInternetRequired",
                    Boolean::class.javaPrimitiveType
                )
                method.invoke(builder, false)
                Log.d(TAG, "Android 10 설정 완료")
            } catch (error: Exception) {
                Log.w(TAG, "Android 10 설정 실패: ${error.message}")
            }
        }
        else -> {
            Log.w(TAG, "WiFi Suggestion은 Android 10 이상에서만 지원됩니다")
            return null
        }
    }

    return builder.build()
}
```

## 최종 작동 방식

### 자동 연결 플로우

1. **WiFi Suggestion 등록** (설정 화면)
   ```
   사용자가 자동 연결 ON → WifiNetworkSuggestion 등록
   ```

2. **시스템이 자동 연결** (백그라운드)
   ```
   카메라 WiFi 켜짐 → 시스템이 자동으로 연결
   ```

3. **NetworkCallback 감지** (CamCon Application)
   ```
   WiFi 연결 변화 감지 → 자동 연결 조건 확인
   ```

4. **AutoConnectForegroundService 시작**
   ```
   조건 충족 → Foreground Service 시작 → 카메라 연결
   ```

### 브로드캐스트 vs NetworkCallback 비교

| 방식                    | 장점            | 단점               | 사용 여부            |
|-----------------------|---------------|------------------|------------------|
| **BroadcastReceiver** | 시스템 표준 방식     | Android 버전별로 제한적 | ⚠️ 보조 (Fallback) |
| **NetworkCallback**   | 안정적, 모든 버전 지원 | 항상 실행 필요         | ✅ 주 사용           |

### 이중 안전장치

현재 구현은 두 가지 방식을 모두 사용:

1. **NetworkCallback (주)**: Application에서 항상 모니터링
2. **BroadcastReceiver (보조)**: 혹시 모를 경우 대비

→ 어느 방식이든 하나가 작동하면 자동 연결 성공!
