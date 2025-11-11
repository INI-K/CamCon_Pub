# PTPIP AP/STA 모드 구현 로드맵

## 📋 목표

**주요 4개 제조사의 AP/STA 모드 완전 지원**

- ✅ Canon (EOS, PowerShot)
- ✅ Nikon (DSLR, Z 시리즈, Coolpix)
- ⚠️ Sony (Alpha, RX 시리즈) - 제한적
- ⚠️ Fujifilm (GFX, X 시리즈) - 자체 프로토콜

---

## 제조사별 구현 상태 및 계획

### 1️⃣ Canon - 우선순위: ⭐⭐⭐⭐⭐

#### 현재 상태

```
✅ AP 모드: 구현 완료 (표준 PTPIP)
✅ STA 모드: 구현 완료 (표준 PTPIP)
✅ 인증: 불필요
✅ 테스트: 필요
```

#### 지원 모델

- **EOS 시리즈**: 5D, 6D, 7D, 80D, 90D, R, RP, R5, R6
- **EOS M 시리즈**: M5, M6, M50, M100
- **PowerShot**: SX720 HS, SX740 HS, G5X, G7X

#### 구현 계획

```kotlin
// CanonConnectionService.kt (신규 생성)
class CanonConnectionService @Inject constructor(
    private val connectionManager: PtpipConnectionManager
) {
    /**
     * Canon AP 모드 연결
     * - 카메라가 Wi-Fi AP 생성
     * - IP: 보통 192.168.1.1
     * - 인증 불필요
     */
    suspend fun connectApMode(camera: PtpipCamera): Boolean {
        // 1. 표준 PTPIP 연결
        if (!connectionManager.establishConnection(camera)) return false
        
        // 2. GetDeviceInfo로 Canon 확인
        val deviceInfo = connectionManager.getDeviceInfo() ?: return false
        if (!deviceInfo.manufacturer.contains("Canon", ignoreCase = true)) {
            return false
        }
        
        // 3. OpenSession
        if (!connectionManager.openSession()) return false
        
        Log.i(TAG, "✅ Canon AP 모드 연결 성공")
        return true
    }
    
    /**
     * Canon STA 모드 연결
     * - 카메라와 기기가 같은 공유기에 연결
     * - Canon은 인증 절차 불필요
     */
    suspend fun connectStaMode(camera: PtpipCamera): Boolean {
        // AP 모드와 동일한 절차
        return connectApMode(camera)
    }
    
    /**
     * Canon EOS 전용 설정
     */
    suspend fun configureEosCamera(): Boolean {
        // capturetarget 설정 (SDRAM vs Card)
        // viewfinder 설정
        // eosremoterelease 설정
        return true
    }
}
```

#### 테스트 항목

- [ ] EOS R 시리즈 AP 모드
- [ ] EOS M 시리즈 STA 모드
- [ ] PowerShot AP 모드
- [ ] 라이브뷰 스트리밍
- [ ] 원격 촬영 및 다운로드

---

### 2️⃣ Nikon - 우선순위: ⭐⭐⭐⭐⭐

#### 현재 상태

```
✅ AP 모드: 구현 완료
⚠️ STA 모드: 부분 구현 (인증 로직 있음, 테스트 필요)
⚠️ 인증: 구현됨 (NikonAuthenticationService)
❌ 테스트: 실제 카메라 테스트 필요
```

#### 지원 모델

**완전 지원 목표**:

- **DSLR**: D90, D7000, D7100, D7200, D500, D780, D850
- **Z 시리즈**: Z5, Z6, Z7, Z8, Z9, Z50
- **Coolpix**: P1000, P950 (Wi-Fi 모델)

**제한적 지원**:

- **D3xxx 시리즈**: D3200, D3300, D3400, D3500 (명령어 제한적)
- **D5xxx 시리즈**: D5100, D5200, D5300, D5600 (기본 기능만)

#### 구현 개선 계획

```kotlin
// NikonConnectionService.kt (개선)
class NikonConnectionService @Inject constructor(
    private val connectionManager: PtpipConnectionManager,
    private val authService: NikonAuthenticationService
) {
    /**
     * Nikon 연결 모드 자동 감지
     */
    suspend fun detectConnectionMode(camera: PtpipCamera): NikonConnectionMode {
        // 1. 기본 연결 시도 (타임아웃 짧게)
        val socket = withContext(Dispatchers.IO) {
            try {
                Socket().apply {
                    connect(InetSocketAddress(camera.ipAddress, camera.port), 2000)
                    close()
                }
                true
            } catch (e: Exception) {
                false
            }
        }
        
        if (!socket) return NikonConnectionMode.UNKNOWN
        
        // 2. 간단한 Init 시도
        if (connectionManager.establishConnection(camera)) {
            val deviceInfo = connectionManager.getDeviceInfo()
            connectionManager.closeConnections()
            
            if (deviceInfo != null) {
                // 즉시 연결 성공 = AP 모드
                return NikonConnectionMode.AP_MODE
            }
        }
        
        // 3. 연결 실패 = STA 모드 (인증 필요)
        return NikonConnectionMode.STA_MODE
    }
    
    /**
     * Nikon AP 모드 연결
     */
    suspend fun connectApMode(camera: PtpipCamera): Boolean {
        Log.i(TAG, "=== Nikon AP 모드 연결 시작 ===")
        
        // 1. 표준 PTPIP 연결
        if (!connectionManager.establishConnection(camera)) {
            Log.e(TAG, "기본 연결 실패")
            return false
        }
        
        // 2. GetDeviceInfo
        val deviceInfo = connectionManager.getDeviceInfo()
        if (deviceInfo == null) {
            Log.e(TAG, "DeviceInfo 조회 실패")
            connectionManager.closeConnections()
            return false
        }
        
        // 3. OpenSession
        if (!connectionManager.openSession()) {
            Log.e(TAG, "OpenSession 실패")
            connectionManager.closeConnections()
            return false
        }
        
        Log.i(TAG, "✅ Nikon AP 모드 연결 성공")
        return true
    }
    
    /**
     * Nikon STA 모드 연결 (인증 포함)
     */
    suspend fun connectStaMode(
        camera: PtpipCamera,
        onAuthRequired: () -> Unit  // 카메라 승인 필요 콜백
    ): Boolean {
        Log.i(TAG, "=== Nikon STA 모드 연결 시작 ===")
        
        // 1. 기본 연결
        if (!connectionManager.establishConnection(camera)) {
            Log.e(TAG, "기본 연결 실패")
            return false
        }
        
        // 2. GetDeviceInfo
        val deviceInfo = connectionManager.getDeviceInfo()
        if (deviceInfo == null) {
            Log.e(TAG, "DeviceInfo 조회 실패")
            connectionManager.closeConnections()
            return false
        }
        
        Log.i(TAG, "카메라 정보: ${deviceInfo.manufacturer} ${deviceInfo.model}")
        
        // 3. STA 인증 수행
        Log.i(TAG, "=== STA 인증 절차 시작 ===")
        
        // 사용자에게 카메라 승인 필요 알림
        withContext(Dispatchers.Main) {
            onAuthRequired()
        }
        
        // NikonAuthenticationService 사용
        val authResult = authService.performStaAuthentication(camera)
        
        if (!authResult) {
            Log.e(TAG, "❌ STA 인증 실패")
            connectionManager.closeConnections()
            return false
        }
        
        Log.i(TAG, "✅ Nikon STA 모드 연결 성공 (인증 완료)")
        return true
    }
    
    /**
     * 연결 재시도 로직 (STA 모드용)
     */
    suspend fun retryStaConnection(
        camera: PtpipCamera,
        maxRetries: Int = 3
    ): Boolean {
        repeat(maxRetries) { attempt ->
            Log.i(TAG, "STA 연결 시도 ${attempt + 1}/$maxRetries")
            
            if (connectStaMode(camera) { /* UI 콜백 */ }) {
                return true
            }
            
            // 실패 시 GUID 재설정 후 재시도
            if (attempt < maxRetries - 1) {
                Log.w(TAG, "GUID 재설정 후 재시도")
                // JNI 호출
                CameraNative.resetPtpipGuid()
                delay(1000)
            }
        }
        
        return false
    }
}
```

#### 테스트 항목

- [ ] Z6/Z7 AP 모드
- [ ] Z6/Z7 STA 모드 + 인증
- [ ] D850 STA 모드
- [ ] D3xxx 시리즈 제한 사항 확인
- [ ] 라이브뷰 (모델별 차이)

---

### 3️⃣ Sony - 우선순위: ⭐⭐⭐

#### 현재 상태

```
❌ AP 모드: 미구현 (Sony는 AP 모드 거의 없음)
❌ STA 모드: 미구현
⚠️ 제한: 원격 제어 매우 제한적
⚠️ 다운로드 위주로 구현 권장
```

#### 지원 모델 (제한적)

**부분 지원 가능**:

- **Alpha 시리즈**: A7, A7R, A7S, A9 (일부 모델)
- **RX 시리즈**: RX100 (제한적)

**미지원 (Mass Storage만)**:

- 대부분의 Cybershot 시리즈
- 오래된 Alpha 모델

#### 구현 계획

```kotlin
// SonyConnectionService.kt (신규 생성)
class SonyConnectionService @Inject constructor(
    private val connectionManager: PtpipConnectionManager
) {
    /**
     * Sony 카메라 연결 (제한적)
     * - 대부분 다운로드만 지원
     * - Alpha 시리즈 일부만 원격 제어 가능
     */
    suspend fun connect(camera: PtpipCamera): SonyConnectionResult {
        Log.i(TAG, "=== Sony 카메라 연결 시작 ===")
        
        // 1. 기본 연결
        if (!connectionManager.establishConnection(camera)) {
            return SonyConnectionResult.FAILED
        }
        
        // 2. DeviceInfo로 모델 확인
        val deviceInfo = connectionManager.getDeviceInfo()
        if (deviceInfo == null) {
            connectionManager.closeConnections()
            return SonyConnectionResult.FAILED
        }
        
        // 3. 모델별 기능 제한 확인
        val capabilities = detectSonyCapabilities(deviceInfo.model)
        
        Log.i(TAG, "Sony 카메라 감지: ${deviceInfo.model}")
        Log.i(TAG, "지원 기능: $capabilities")
        
        return when {
            capabilities.supportsRemoteControl -> {
                // Alpha A7/A9 시리즈 등
                Log.i(TAG, "✅ 원격 제어 지원 모델")
                SonyConnectionResult.FULL_SUPPORT
            }
            capabilities.supportsDownload -> {
                // 대부분의 Sony 카메라
                Log.i(TAG, "⚠️ 다운로드만 지원 (원격 제어 불가)")
                SonyConnectionResult.DOWNLOAD_ONLY
            }
            else -> {
                Log.w(TAG, "❌ PTP 기능 제한적")
                SonyConnectionResult.LIMITED
            }
        }
    }
    
    /**
     * Sony 카메라 기능 감지
     */
    private fun detectSonyCapabilities(model: String): SonyCapabilities {
        val modelLower = model.lowercase()
        
        return SonyCapabilities(
            supportsRemoteControl = when {
                modelLower.contains("ilce-7") -> true  // A7 시리즈
                modelLower.contains("ilce-9") -> true  // A9 시리즈
                modelLower.contains("slt-a") -> false  // SLT 시리즈
                modelLower.contains("dsc-rx100") -> false  // RX100
                else -> false
            },
            supportsDownload = true,  // 대부분 다운로드는 지원
            supportsLiveview = when {
                modelLower.contains("ilce-7") -> true
                modelLower.contains("ilce-9") -> true
                else -> false
            }
        )
    }
}

data class SonyCapabilities(
    val supportsRemoteControl: Boolean,
    val supportsDownload: Boolean,
    val supportsLiveview: Boolean
)

enum class SonyConnectionResult {
    FULL_SUPPORT,      // 원격 제어 + 다운로드
    DOWNLOAD_ONLY,     // 다운로드만
    LIMITED,           // 매우 제한적
    FAILED             // 연결 실패
}
```

#### 구현 우선순위

1. **Phase 1**: 다운로드 기능 (모든 모델)
2. **Phase 2**: Alpha A7/A9 원격 제어
3. **Phase 3**: 라이브뷰 (지원 모델만)

---

### 4️⃣ Fujifilm - 우선순위: ⭐⭐⭐⭐

#### 현재 상태

```
❌ GFX: 표준 PTPIP 미구현 (구현 필요)
❌ X 시리즈: 자체 프로토콜 미구현 (역공학 필요)
⚠️ 포트: X 시리즈는 55740 사용
```

#### 지원 모델

**표준 PTPIP (구현 우선)**:

- **GFX 시리즈**: GFX 50S, GFX 50R, GFX 100, GFX 100S

**자체 프로토콜 (나중에 구현)**:

- **X-T 시리즈**: X-T1, X-T2, X-T3, X-T4, X-T5
- **X-H 시리즈**: X-H1, X-H2, X-H2S
- **X-Pro 시리즈**: X-Pro2, X-Pro3

**미지원 (Wi-Fi 없음)**:

- X-T10, X-T20, X-T30, X-A, X-E 시리즈

#### 구현 계획

```kotlin
// FujifilmConnectionService.kt (신규 생성)
class FujifilmConnectionService @Inject constructor(
    private val connectionManager: PtpipConnectionManager
) {
    /**
     * GFX 시리즈 연결 (표준 PTPIP)
     */
    suspend fun connectGfx(camera: PtpipCamera): Boolean {
        Log.i(TAG, "=== Fujifilm GFX 연결 시작 ===")
        
        // GFX는 표준 PTPIP 사용 (Canon과 유사)
        // 1. 표준 연결
        if (!connectionManager.establishConnection(camera)) return false
        
        // 2. DeviceInfo 확인
        val deviceInfo = connectionManager.getDeviceInfo()
        if (deviceInfo == null || 
            !deviceInfo.manufacturer.contains("FUJIFILM", ignoreCase = true)) {
            connectionManager.closeConnections()
            return false
        }
        
        // 3. OpenSession
        if (!connectionManager.openSession()) {
            connectionManager.closeConnections()
            return false
        }
        
        Log.i(TAG, "✅ Fujifilm GFX 연결 성공")
        return true
    }
    
    /**
     * X 시리즈 연결 (자체 프로토콜)
     * - 포트: 55740 (표준 15740 아님!)
     * - 핸드셰이크가 다름
     */
    suspend fun connectXSeries(camera: PtpipCamera): Boolean {
        Log.i(TAG, "=== Fujifilm X 시리즈 연결 시작 ===")
        Log.w(TAG, "⚠️ X 시리즈는 자체 프로토콜 사용 (구현 필요)")
        
        // X 시리즈 전용 포트
        val xSeriesPort = 55740
        val xCamera = camera.copy(port = xSeriesPort)
        
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(xCamera.ipAddress, xSeriesPort), 5000)
                
                // Fuji 전용 Init 패킷 (역공학 필요)
                // fuji-cam-wifi-tool 참조
                val initPacket = createFujiInitPacket()
                socket.getOutputStream().write(initPacket)
                
                // 응답 확인
                val response = ByteArray(1024)
                val bytesRead = socket.getInputStream().read(response)
                
                socket.close()
                
                // TODO: Fuji 응답 분석 및 세션 수립
                Log.w(TAG, "❌ Fuji X 시리즈 프로토콜 미구현")
                false
                
            } catch (e: Exception) {
                Log.e(TAG, "X 시리즈 연결 실패: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Fuji 전용 Init 패킷 생성 (역공학 필요)
     */
    private fun createFujiInitPacket(): ByteArray {
        // TODO: fuji-cam-wifi-tool 분석하여 구현
        // https://github.com/mzealey/fuji-cam-wifi-tool
        return ByteArray(0)
    }
    
    /**
     * Fuji 카메라 타입 감지
     */
    fun detectFujiType(model: String): FujiCameraType {
        return when {
            model.startsWith("GFX", ignoreCase = true) -> FujiCameraType.GFX
            model.startsWith("X-T", ignoreCase = true) -> FujiCameraType.X_T
            model.startsWith("X-H", ignoreCase = true) -> FujiCameraType.X_H
            model.startsWith("X-Pro", ignoreCase = true) -> FujiCameraType.X_PRO
            else -> FujiCameraType.UNKNOWN
        }
    }
}

enum class FujiCameraType {
    GFX,        // 표준 PTPIP
    X_T,        // 자체 프로토콜 (55740)
    X_H,        // 자체 프로토콜 (55740)
    X_PRO,      // 자체 프로토콜 (55740)
    UNKNOWN
}
```

#### 구현 우선순위

1. **Phase 1**: GFX 시리즈 (표준 PTPIP) ⭐⭐⭐⭐⭐
2. **Phase 2**: X 시리즈 역공학 (fuji-cam-wifi-tool 참조) ⭐⭐⭐
3. **Phase 3**: 라이브뷰 및 원격 제어 ⭐⭐

---

## 통합 연결 매니저 설계

### PtpipUnifiedService.kt (신규 생성)

```kotlin
/**
 * 모든 제조사를 통합 관리하는 PTPIP 서비스
 */
@Singleton
class PtpipUnifiedService @Inject constructor(
    private val canonService: CanonConnectionService,
    private val nikonService: NikonConnectionService,
    private val sonyService: SonyConnectionService,
    private val fujiService: FujifilmConnectionService,
    private val connectionManager: PtpipConnectionManager
) {
    /**
     * 제조사 자동 감지 및 연결
     */
    suspend fun autoConnectWithDetection(
        camera: PtpipCamera,
        onAuthRequired: (String) -> Unit = {}  // "카메라에서 연결을 승인해주세요"
    ): ConnectionResult {
        
        // 1단계: 기본 연결로 제조사 확인
        if (!connectionManager.establishConnection(camera)) {
            return ConnectionResult.Failed("기본 연결 실패")
        }
        
        val deviceInfo = connectionManager.getDeviceInfo()
        if (deviceInfo == null) {
            connectionManager.closeConnections()
            return ConnectionResult.Failed("DeviceInfo 조회 실패")
        }
        
        val manufacturer = detectManufacturer(deviceInfo.manufacturer)
        Log.i(TAG, "감지된 제조사: $manufacturer")
        
        // 기본 연결 닫기 (제조사별 재연결)
        connectionManager.closeConnections()
        delay(500)
        
        // 2단계: 제조사별 연결
        return when (manufacturer) {
            Manufacturer.CANON -> {
                Log.i(TAG, "Canon 전용 연결 프로세스")
                if (canonService.connectApMode(camera)) {
                    ConnectionResult.Success(manufacturer, ConnectionMode.AP)
                } else if (canonService.connectStaMode(camera)) {
                    ConnectionResult.Success(manufacturer, ConnectionMode.STA)
                } else {
                    ConnectionResult.Failed("Canon 연결 실패")
                }
            }
            
            Manufacturer.NIKON -> {
                Log.i(TAG, "Nikon 전용 연결 프로세스")
                
                // AP/STA 모드 자동 감지
                val mode = nikonService.detectConnectionMode(camera)
                Log.i(TAG, "감지된 Nikon 모드: $mode")
                
                when (mode) {
                    NikonConnectionMode.AP_MODE -> {
                        if (nikonService.connectApMode(camera)) {
                            ConnectionResult.Success(manufacturer, ConnectionMode.AP)
                        } else {
                            ConnectionResult.Failed("Nikon AP 연결 실패")
                        }
                    }
                    NikonConnectionMode.STA_MODE -> {
                        onAuthRequired("카메라 LCD에서 '연결 허용'을 눌러주세요 (30초 이내)")
                        
                        if (nikonService.connectStaMode(camera) { onAuthRequired(it) }) {
                            ConnectionResult.Success(manufacturer, ConnectionMode.STA)
                        } else {
                            ConnectionResult.Failed("Nikon STA 인증 실패")
                        }
                    }
                    NikonConnectionMode.UNKNOWN -> {
                        ConnectionResult.Failed("Nikon 모드 감지 실패")
                    }
                }
            }
            
            Manufacturer.SONY -> {
                Log.i(TAG, "Sony 카메라 연결 (제한적)")
                
                val result = sonyService.connect(camera)
                when (result) {
                    SonyConnectionResult.FULL_SUPPORT -> {
                        ConnectionResult.Success(manufacturer, ConnectionMode.STA, 
                            limitations = "일부 기능만 지원")
                    }
                    SonyConnectionResult.DOWNLOAD_ONLY -> {
                        ConnectionResult.PartialSuccess(manufacturer, 
                            "다운로드만 가능 (원격 제어 불가)")
                    }
                    else -> {
                        ConnectionResult.Failed("Sony 연결 실패")
                    }
                }
            }
            
            Manufacturer.FUJIFILM -> {
                Log.i(TAG, "Fujifilm 카메라 연결")
                
                val fujiType = fujiService.detectFujiType(deviceInfo.model)
                
                when (fujiType) {
                    FujiCameraType.GFX -> {
                        // GFX는 표준 PTPIP
                        if (fujiService.connectGfx(camera)) {
                            ConnectionResult.Success(manufacturer, ConnectionMode.AP)
                        } else {
                            ConnectionResult.Failed("GFX 연결 실패")
                        }
                    }
                    FujiCameraType.X_T, FujiCameraType.X_H, FujiCameraType.X_PRO -> {
                        // X 시리즈는 자체 프로토콜
                        Log.w(TAG, "⚠️ X 시리즈는 자체 프로토콜 (55740 포트)")
                        ConnectionResult.NotImplemented(
                            "X 시리즈는 현재 미지원 (향후 업데이트 예정)"
                        )
                    }
                    FujiCameraType.UNKNOWN -> {
                        ConnectionResult.Failed("Fuji 모델 감지 실패")
                    }
                }
            }
            
            Manufacturer.UNKNOWN -> {
                Log.w(TAG, "알 수 없는 제조사, 표준 PTP로 시도")
                // 표준 PTP로 시도
                if (connectionManager.openSession()) {
                    ConnectionResult.Success(Manufacturer.UNKNOWN, ConnectionMode.UNKNOWN)
                } else {
                    ConnectionResult.Failed("표준 PTP 연결 실패")
                }
            }
        }
    }
    
    /**
     * 제조사 감지
     */
    private fun detectManufacturer(manufacturerStr: String): Manufacturer {
        val lower = manufacturerStr.lowercase()
        return when {
            lower.contains("canon") -> Manufacturer.CANON
            lower.contains("nikon") -> Manufacturer.NIKON
            lower.contains("sony") -> Manufacturer.SONY
            lower.contains("fuji") -> Manufacturer.FUJIFILM
            lower.contains("olympus") -> Manufacturer.OLYMPUS
            lower.contains("panasonic") -> Manufacturer.PANASONIC
            else -> Manufacturer.UNKNOWN
        }
    }
}

enum class Manufacturer {
    CANON, NIKON, SONY, FUJIFILM, OLYMPUS, PANASONIC, UNKNOWN
}

enum class ConnectionMode {
    AP,      // Access Point 모드
    STA,     // Station 모드 (공유기 경유)
    UNKNOWN
}

sealed class ConnectionResult {
    data class Success(
        val manufacturer: Manufacturer,
        val mode: ConnectionMode,
        val limitations: String? = null
    ) : ConnectionResult()
    
    data class PartialSuccess(
        val manufacturer: Manufacturer,
        val message: String
    ) : ConnectionResult()
    
    data class NotImplemented(val message: String) : ConnectionResult()
    data class Failed(val reason: String) : ConnectionResult()
}
```

---

## C++ Native 레이어 개선

### camera_ptpip_vendor.cpp (신규 생성)

```cpp
#include "camera_common.h"

/**
 * Canon 전용 초기화
 */
int initCanonPtpip(Camera *camera, const char *ipAddress, int port) {
    LOGI("=== Canon PTPIP 초기화 ===");
    
    // Canon은 표준 PTPIP 그대로 사용
    // 추가 설정 불필요
    
    return GP_OK;
}

/**
 * Nikon 전용 초기화
 */
int initNikonPtpip(Camera *camera, const char *ipAddress, int port, 
                   bool isStaMode) {
    LOGI("=== Nikon PTPIP 초기화 (STA=%s) ===", isStaMode ? "YES" : "NO");
    
    if (!isStaMode) {
        // AP 모드: 표준 초기화
        return GP_OK;
    }
    
    // STA 모드: 추가 설정
    gp_setting_set((char*)"ptp2_ip", (char*)"timeout", (char*)"15000");
    gp_setting_set((char*)"ptp2_ip", (char*)"nikon_sta_mode", (char*)"1");
    
    LOGI("Nikon STA 모드 설정 완료");
    return GP_OK;
}

/**
 * Sony 전용 초기화
 */
int initSonyPtpip(Camera *camera, const char *ipAddress, int port) {
    LOGI("=== Sony PTPIP 초기화 ===");
    
    // Sony는 기능이 제한적이므로 짧은 타임아웃
    gp_setting_set((char*)"ptp2_ip", (char*)"timeout", (char*)"8000");
    
    return GP_OK;
}

/**
 * Fujifilm 전용 초기화
 */
int initFujifilmPtpip(Camera *camera, const char *ipAddress, int port,
                      bool isXSeries) {
    LOGI("=== Fujifilm PTPIP 초기화 (X시리즈=%s) ===", 
         isXSeries ? "YES" : "NO");
    
    if (isXSeries) {
        LOGW("⚠️ X 시리즈는 자체 프로토콜 사용 (55740 포트)");
        LOGW("⚠️ 현재 미구현 - fuji-cam-wifi-tool 참조 필요");
        return -1;
    }
    
    // GFX 시리즈: 표준 PTPIP
    return GP_OK;
}

/**
 * 제조사별 초기화 통합 함수
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_inik_camcon_CameraNative_initPtpipByManufacturer(
    JNIEnv *env, jobject thiz,
    jstring jIpAddress, jint port, jstring libDir,
    jstring jManufacturer, jboolean isStaMode) {
    
    const char *ipAddress = env->GetStringUTFChars(jIpAddress, nullptr);
    const char *libDir_ = env->GetStringUTFChars(libDir, nullptr);
    const char *manufacturer = env->GetStringUTFChars(jManufacturer, nullptr);
    
    LOGI("============================================");
    LOGI("=== 제조사별 PTPIP 초기화 ===");
    LOGI("제조사: %s", manufacturer);
    LOGI("IP: %s:%d", ipAddress, port);
    LOGI("STA 모드: %s", isStaMode ? "YES" : "NO");
    LOGI("============================================");
    
    // 기본 환경 설정
    setupLibraryPathsOnce(libDir_);
    
    // 기존 카메라 정리
    std::lock_guard<std::mutex> lock(cameraMutex);
    if (camera) {
        gp_camera_exit(camera, context);
        gp_camera_free(camera);
        camera = nullptr;
    }
    
    if (!context) {
        context = gp_context_new();
        gp_context_set_error_func(context, errordumper_context, nullptr);
    }
    
    // 새 카메라 생성
    int ret = gp_camera_new(&camera);
    if (ret < GP_OK) {
        LOGE("카메라 객체 생성 실패");
        goto cleanup;
    }
    
    // 제조사별 초기화
    std::string mfg(manufacturer);
    std::transform(mfg.begin(), mfg.end(), mfg.begin(), ::tolower);
    
    if (mfg.find("canon") != std::string::npos) {
        ret = initCanonPtpip(camera, ipAddress, port);
    } else if (mfg.find("nikon") != std::string::npos) {
        ret = initNikonPtpip(camera, ipAddress, port, isStaMode);
    } else if (mfg.find("sony") != std::string::npos) {
        ret = initSonyPtpip(camera, ipAddress, port);
    } else if (mfg.find("fuji") != std::string::npos) {
        ret = initFujifilmPtpip(camera, ipAddress, port, false);
    } else {
        LOGW("알 수 없는 제조사, 표준 설정 사용");
        ret = GP_OK;
    }
    
    if (ret < GP_OK) {
        LOGE("제조사별 초기화 실패");
        goto cleanup;
    }
    
    // 포트 설정
    ret = setupPtpipPort(camera, ipAddress, port);
    if (ret < GP_OK) {
        LOGE("포트 설정 실패");
        goto cleanup;
    }
    
    // 드라이버 설정
    CameraAbilitiesList *abilitiesList;
    ret = gp_abilities_list_new(&abilitiesList);
    if (ret >= GP_OK) {
        ret = gp_abilities_list_load(abilitiesList, context);
        if (ret >= GP_OK) {
            int model_idx = gp_abilities_list_lookup_model(abilitiesList, "PTP/IP Camera");
            if (model_idx >= GP_OK) {
                CameraAbilities abilities;
                ret = gp_abilities_list_get_abilities(abilitiesList, model_idx, &abilities);
                if (ret >= GP_OK) {
                    gp_camera_set_abilities(camera, abilities);
                }
            }
        }
        gp_abilities_list_free(abilitiesList);
    }
    
    // 카메라 초기화
    LOGI("=== gp_camera_init 시작 ===");
    ret = gp_camera_init(camera, context);
    if (ret < GP_OK) {
        LOGE("카메라 초기화 실패: %s (코드: %d)", gp_result_as_string(ret), ret);
        goto cleanup;
    }
    
    LOGI("✅ 제조사별 PTPIP 초기화 완료");
    
    env->ReleaseStringUTFChars(jIpAddress, ipAddress);
    env->ReleaseStringUTFChars(libDir, libDir_);
    env->ReleaseStringUTFChars(jManufacturer, manufacturer);
    return 0;

cleanup:
    if (camera) {
        gp_camera_free(camera);
        camera = nullptr;
    }
    
    env->ReleaseStringUTFChars(jIpAddress, ipAddress);
    env->ReleaseStringUTFChars(libDir, libDir_);
    env->ReleaseStringUTFChars(jManufacturer, manufacturer);
    return -1;
}
```

---

## UI/UX 개선 계획

### 사용자 경험 향상

```kotlin
// PtpipConnectionScreen.kt (개선)

/**
 * 제조사별 연결 가이드 표시
 */
@Composable
fun ManufacturerSpecificGuide(manufacturer: Manufacturer, mode: ConnectionMode) {
    when (manufacturer) {
        Manufacturer.CANON -> {
            Text("📸 Canon 카메라")
            Text("✅ 별도 인증 불필요")
            Text("⏱️ 연결 시간: 약 5초")
        }
        
        Manufacturer.NIKON -> {
            Text("📸 Nikon 카메라")
            when (mode) {
                ConnectionMode.AP -> {
                    Text("📡 AP 모드 감지됨")
                    Text("✅ 별도 인증 불필요")
                }
                ConnectionMode.STA -> {
                    Text("🏠 STA 모드 감지됨")
                    Text("⚠️ 카메라 승인 필요")
                    Text("👆 카메라 LCD에서 '연결 허용' 버튼을 눌러주세요")
                    CircularProgressIndicator()
                    Text("대기 시간: 최대 30초")
                }
            }
        }
        
        Manufacturer.SONY -> {
            Text("📸 Sony 카메라")
            Text("⚠️ 원격 제어 제한적")
            Text("✅ 파일 다운로드는 가능")
            Text("ℹ️ Alpha 시리즈만 일부 제어 가능")
        }
        
        Manufacturer.FUJIFILM -> {
            Text("📸 Fujifilm 카메라")
            if (deviceInfo.model.startsWith("GFX")) {
                Text("✅ GFX 시리즈: 완전 지원")
            } else {
                Text("⚠️ X 시리즈: 부분 지원")
                Text("ℹ️ 향후 업데이트 예정")
            }
        }
    }
}
```

---

## 구현 우선순위

### Phase 1: 핵심 기능 (1-2주)

- [x] Canon AP/STA 모드 (코드 완성)
- [x] Nikon AP 모드 (코드 완성)
- [ ] Nikon STA 모드 테스트 (실제 카메라 필요)
- [ ] Sony 다운로드 기능
- [ ] 기본 에러 처리

### Phase 2: 고급 기능 (2-3주)

- [ ] Fujifilm GFX 시리즈
- [ ] Sony Alpha 원격 제어
- [ ] Nikon STA 인증 안정화
- [ ] 제조사별 설정 최적화

### Phase 3: 확장 기능 (3-4주)

- [ ] Fujifilm X 시리즈 (자체 프로토콜)
- [ ] Olympus E 시리즈
- [ ] Panasonic Lumix
- [ ] 라이브뷰 스트리밍 최적화

---

## 테스트 계획

### 필요한 테스트 장비

```
Canon:
  └─ EOS R 또는 EOS M 시리즈 (Wi-Fi 지원 모델)

Nikon:
  ├─ Z 시리즈 (Z6/Z7) 또는
  └─ D7xxx 시리즈 (D7500)

Sony (선택):
  └─ Alpha A7 시리즈

Fujifilm (선택):
  ├─ GFX 50S/100 또는
  └─ X-T3/X-T4
```

### 테스트 시나리오

```
각 제조사별:
1. AP 모드 연결 테스트
   ├─ 카메라 검색
   ├─ 연결 수립
   ├─ DeviceInfo 조회
   └─ 파일 목록 조회

2. STA 모드 연결 테스트
   ├─ mDNS 검색
   ├─ 연결 시도
   ├─ 인증 절차 (Nikon만)
   └─ 세션 수립

3. 기능 테스트
   ├─ 파일 다운로드
   ├─ 원격 촬영
   ├─ 라이브뷰
   └─ 설정 변경
```

---

## 다음 단계

### 즉시 실행 가능

1. ✅ C++ 개선 사항 적용 완료
2. ✅ 문서 작성 완료
3. ⏳ **다음: Kotlin 레이어 구현**

### Kotlin 레이어 구현 순서

1. `CanonConnectionService.kt` 생성
2. `NikonConnectionService.kt` 개선
3. `SonyConnectionService.kt` 생성
4. `FujifilmConnectionService.kt` 생성
5. `PtpipUnifiedService.kt` 통합 매니저

### Native 레이어 추가

1. `camera_ptpip_vendor.cpp` 생성
2. `CameraNative.kt`에 JNI 함수 추가
3. CMakeLists.txt 업데이트

**지금 바로 구현을 시작할까요?**
제조사별 서비스 클래스를 생성하고 통합 매니저를 만들겠습니다! 🚀
