package com.inik.camcon.data.repository.managers

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

class PtpTimeoutException(message: String) : Exception(message)

@Singleton
class CameraConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeDataSource: NativeCameraDataSource,
    private val usbCameraManager: UsbCameraManager
) {
    // Mutex 동기화 추가(중복 connectCamera 방지)
    private val connectCameraMutex = Mutex()

    private val _cameraFeed = MutableStateFlow<List<Camera>>(emptyList())
    val cameraFeed = _cameraFeed.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    // 초기화 중 UI 블록 상태 추가
    private val _isInitializing = MutableStateFlow(false)
    val isInitializing = _isInitializing.asStateFlow()

    // PTPIP 연결 상태 추가
    private val _isPtpipConnected = MutableStateFlow(false)
    val isPtpipConnected = _isPtpipConnected.asStateFlow()

    private val _cameraCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    val cameraCapabilities = _cameraCapabilities.asStateFlow()

    init {
        // USB 카메라 매니저의 네이티브 카메라 연결 상태를 관찰
        observeNativeCameraConnection()
    }

    suspend fun connectCamera(cameraId: String): Result<Boolean> {
        return connectCameraMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    Log.d("카메라연결매니저", "카메라 연결 시작: $cameraId (Mutex 보호)")

                    // 이미 연결되어 있으면 성공 반환 - 더 엄격한 체크
                    if (_isConnected.value) {
                        Log.d(
                            "카메라연결매니저",
                            "카메라가 이미 연결되어 있음 - 중복 연결 방지 (connected=${_isConnected.value})"
                        )
                        return@withContext Result.success(true)
                    }

                    // 초기화 중인 경우도 체크
                    if (_isInitializing.value) {
                        Log.d(
                            "카메라연결매니저",
                            "카메라가 이미 초기화 중임 - 중복 연결 방지 (initializing=${_isInitializing.value})"
                        )
                        return@withContext Result.success(true)
                    }

                    // 초기화 시작 - UI 블록
                    _isInitializing.value = true
                    Log.d("카메라연결매니저", "카메라 초기화 상태 변경: true")

                    val connectionResult = connectCameraInternal()
                    connectionResult
                } catch (e: Exception) {
                    Log.e("카메라연결매니저", "카메라 연결 중 예외 발생", e)
                    // 예외 발생 시에만 초기화 상태 해제
                    _isInitializing.value = false
                    Log.d("카메라연결매니저", "예외로 인한 카메라 초기화 상태 변경: false")
                    Result.failure(e)
                }
            }
        }
    }

    private suspend fun connectCameraInternal(): Result<Boolean> {
        // USB 디바이스 확인 및 연결
        val usbDevices = usbCameraManager.connectedDevices.value
        return if (usbDevices.isNotEmpty()) {
            connectUsbCamera(usbDevices.first())
        } else {
            connectGeneralCamera()
        }
    }

    private suspend fun connectUsbCamera(device: UsbDevice): Result<Boolean> {
        Log.d("카메라연결매니저", "연결된 USB 디바이스 발견: ${device.deviceName}")

        // USB 권한 요청
        if (!usbCameraManager.hasUsbPermission.value) {
            Log.d("카메라연결매니저", "USB 권한 없음, 권한 요청")
            withContext(Dispatchers.Main) {
                usbCameraManager.requestPermission(device)
            }
            return Result.failure(Exception("USB 권한이 필요합니다"))
        }

        // 중요: 먼저 USB 디바이스에 연결하여 currentDevice와 currentConnection을 설정
        Log.d("카메라연결매니저", "USB 디바이스 연결 시작: ${device.deviceName}")
        usbCameraManager.connectToCamera(device)

        // 연결 안정화를 위한 짧은 대기
        kotlinx.coroutines.delay(100)

        // 파일 디스크립터를 사용한 네이티브 초기화
        val fd = usbCameraManager.getFileDescriptor()
        return if (fd != null) {
            Log.d("카메라연결매니저", "파일 디스크립터로 카메라 초기화: $fd")
            val nativeLibDir = "/data/data/com.inik.camcon/lib"
            val result = nativeDataSource.initCameraWithFd(fd, nativeLibDir)
            handleInitializationResult(result)
        } else {
            Log.e("카메라연결매니저", "파일 디스크립터를 가져올 수 없음 - USB 연결 실패")
            Result.failure(Exception("파일 디스크립터를 가져올 수 없음 - USB 연결 실패"))
        }
    }

    private suspend fun connectGeneralCamera(): Result<Boolean> {
        // USB 연결이 안되면 일반 초기화 시도
        Log.d("카메라연결매니저", "일반 카메라 초기화 시도")
        val result = nativeDataSource.initCamera()
        return if (result.contains("success", ignoreCase = true)) {
            Log.d("카메라연결매니저", "일반 카메라 초기화 성공")
            withContext(Dispatchers.Main) {
                _isConnected.value = true
                _isInitializing.value = false  // 초기화 완료 시 상태 해제
            }
            Log.d("카메라연결매니저", "카메라 초기화 상태 변경: false (일반 카메라 연결 성공)")
            updateCameraList()
            Result.success(true)
        } else if (result.contains("-2000")) {
            // PTP 타임아웃 오류 감지 (문자열 형태)
            Log.e("카메라연결매니저", "일반 초기화에서 PTP 타임아웃 오류 감지: $result")
            _isInitializing.value = false  // 실패 시에도 상태 해제
            Log.d("카메라연결매니저", "카메라 초기화 상태 변경: false (일반 PTP 타임아웃)")
            Result.failure(PtpTimeoutException("카메라 연결 시 PTP 타임아웃이 발생했습니다. 앱을 재시작해주세요."))
        } else if (result.contains("-10")) {
            // -10 타임아웃 오류 감지 - 재시도 없이 바로 재시작 요청
            Log.e("카메라연결매니저", "일반 초기화에서 -10 타임아웃 오류 감지: $result")
            _isInitializing.value = false  // 실패 시에도 상태 해제
            Log.d("카메라연결매니저", "카메라 초기화 상태 변경: false (일반 -10 타임아웃)")
            Result.failure(PtpTimeoutException("카메라 연결에 실패했습니다.\n연결 상태를 확인하고 앱을 재시작해주세요."))
        } else {
            Log.e("카메라연결매니저", "일반 카메라 초기화 실패: $result")
            _isInitializing.value = false  // 실패 시에도 상태 해제
            Log.d("카메라연결매니저", "카메라 초기화 상태 변경: false (일반 카메라 연결 실패)")
            Result.failure(Exception("카메라 연결 실패: $result"))
        }
    }

    private suspend fun handleInitializationResult(result: Int): Result<Boolean> {
        return when {
            result == 0 -> {
                Log.d("카메라연결매니저", "네이티브 카메라 초기화 성공")
                withContext(Dispatchers.Main) {
                    _isConnected.value = true
                    _isInitializing.value = false  // 초기화 완료 시 상태 해제
                }
                Log.d("카메라연결매니저", "카메라 초기화 상태 변경: false (연결 성공)")
                updateCameraList()
                Result.success(true)
            }

            result == -7 -> {
                // I/O 오류 감지 - USB 권한/커널 드라이버 문제로 앱 재시작 필요
                Log.e("카메라연결매니저", "USB I/O 오류 감지: $result")
                _isInitializing.value = false  // 실패 시에도 상태 해제
                Log.d("카메라연결매니저", "카메라 초기화 상태 변경: false (I/O 오류)")
                Result.failure(PtpTimeoutException("USB I/O 오류가 발생했습니다.\n\n가능한 해결 방법:\n1. USB 권한을 다시 허용해주세요\n2. USB 케이블을 분리 후 재연결\n3. 카메라 전원을 껐다 켜기\n4. 앱을 완전히 재시작해주세요\n\n문제가 지속되면 카메라의 USB 모드를 PTP/MTP로 변경해보세요."))
            }

            result == -10 -> {
                // -10 타임아웃 오류 감지 - 재시도 없이 바로 재시작 요청
                Log.e("카메라연결매니저", "-10 타임아웃 오류 감지: $result")
                _isInitializing.value = false  // 실패 시에도 상태 해제
                Log.d("카메라연결매니저", "카메라 초기화 상태 변경: false (-10 타임아웃)")
                Result.failure(PtpTimeoutException("카메라 연결에 실패했습니다.\n연결 상태를 확인하고 앱을 재시작해주세요."))
            }

            result == -2000 -> {
                // PTP 타임아웃 오류 감지 - 더 구체적인 안내
                Log.e("카메라연결매니저", "PTP 타임아웃 오류 감지: $result")
                _isInitializing.value = false  // 실패 시에도 상태 해제
                Log.d("카메라연결매니저", "카메라 초기화 상태 변경: false (PTP 타임아웃)")
                Result.failure(PtpTimeoutException("카메라와의 PTP 통신에서 타임아웃이 발생했습니다.\n\n가능한 해결 방법:\n1. USB 케이블을 분리 후 재연결\n2. 카메라 전원을 껐다 켜기\n3. 앱 완전 종료 후 재시작\n\n그래도 문제가 지속되면 카메라를 PC 모드에서 해제해보세요."))
            }

            result == -1000 -> {
                // 일반적인 재시작 요청
                Log.e("카메라연결매니저", "시스템 오류로 인한 재시작 요청: $result")
                _isInitializing.value = false  // 실패 시에도 상태 해제
                Log.d("카메라연결매니저", "카메라 초기화 상태 변경: false (시스템 오류)")
                Result.failure(Exception("USB 디바이스 감지에 실패했습니다.\n앱을 재시작해주세요."))
            }

            else -> {
                Log.e("카메라연결매니저", "네이티브 카메라 초기화 실패: $result")
                _isInitializing.value = false  // 실패 시에도 상태 해제
                Log.d("카메라연결매니저", "카메라 초기화 상태 변경: false (기타 오류)")
                Result.failure(Exception("카메라 연결 실패 (오류 코드: $result)"))
            }
        }
    }

    suspend fun disconnectCamera(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("카메라연결매니저", "카메라 연결 해제 시작")

                // 네이티브 카메라 연결 해제
                nativeDataSource.closeCamera()

                withContext(Dispatchers.Main) {
                    _isConnected.value = false
                    _isPtpipConnected.value = false
                    _cameraFeed.value = emptyList()
                }

                Log.d("카메라연결매니저", "카메라 연결 해제 완료")
                Result.success(true)
            } catch (e: Exception) {
                Log.e("카메라연결매니저", "카메라 연결 해제 중 오류", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun updateCameraList() = withContext(Dispatchers.IO) {
        try {
            Log.d("카메라연결매니저", "카메라 목록 업데이트")
            val detected = nativeDataSource.detectCamera()
            Log.d("카메라연결매니저", "detectCamera 반환값: $detected")

            if (detected != "No camera detected") {
                // 감지된 카메라 문자열을 줄 단위로 분할
                val lines = detected.split("\n")
                    .filter { it.isNotBlank() }

                Log.d("카메라연결매니저", "분할된 줄 수: ${lines.size}")
                lines.forEachIndexed { index, line ->
                    Log.d("카메라연결매니저", "줄 $index: '$line'")
                }

                // 실제 카메라 정보가 포함된 줄만 필터링
                val cameraLines = lines.filter { line ->
                    line.contains("@") && (line.contains("[") || line.matches(Regex(".*\\w+.*@.*")))
                }

                Log.d("카메라연결매니저", "필터링된 카메라 라인 수: ${cameraLines.size}")
                cameraLines.forEachIndexed { index, line ->
                    Log.d("카메라연결매니저", "카메라 라인 $index: '$line'")
                }

                val cameras = cameraLines.mapIndexed { index, line ->
                    val parts = line.split(" @ ")
                    val name = parts.getOrNull(0)?.trim()?.let { rawName ->
                        // "[1] " 같은 번호 제거
                        rawName.replace(Regex("^\\[\\d+\\]\\s*"), "")
                    } ?: "알 수 없음"

                    Camera(
                        id = "camera_$index",
                        name = name,
                        isActive = true
                    )
                }

                withContext(Dispatchers.Main) {
                    _cameraFeed.value = cameras
                }
                Log.d("카메라연결매니저", "카메라 목록 업데이트 완료: ${cameras.size}개")
            } else {
                Log.d("카메라연결매니저", "카메라가 감지되지 않음")
                withContext(Dispatchers.Main) {
                    _cameraFeed.value = emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("카메라연결매니저", "카메라 목록 업데이트 실패", e)
        }
    }

    private fun observeNativeCameraConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            usbCameraManager.isNativeCameraConnected.collect { isConnected ->
                Log.d("카메라연결매니저", "네이티브 카메라 연결 상태 변경: $isConnected")

                // 상태가 실제로 변경된 경우만 업데이트
                val currentConnected = _isConnected.value
                if (currentConnected != isConnected) {
                    withContext(Dispatchers.Main) {
                        _isConnected.value = isConnected
                    }
                    Log.d("카메라연결매니저", "연결 상태 업데이트: $currentConnected -> $isConnected")
                } else {
                    Log.d("카메라연결매니저", "연결 상태 이미 동일: $isConnected - 업데이트 생략")
                }

                if (isConnected && !currentConnected) {
                    // 연결 상태가 false -> true로 변경된 경우에만 업데이트
                    Log.d("카메라연결매니저", "새로운 연결 감지 - 목록 및 기능 정보 업데이트")

                    // 연결 안정화를 위한 짧은 지연 후 업데이트
                    kotlinx.coroutines.delay(500)

                    updateCameraList()

                    // 카메라 기능 정보는 연결 완료 후 한 번만 업데이트
                    if (_cameraCapabilities.value == null) {
                        Log.d("카메라연결매니저", "카메라 기능 정보 없음 - 업데이트 시작")
                        updateCameraCapabilities()
                    } else {
                        Log.d("카메라연결매니저", "카메라 기능 정보 이미 존재 - 업데이트 생략")
                    }
                } else if (!isConnected && currentConnected) {
                    // 연결 상태가 true -> false로 변경된 경우에만 초기화
                    Log.d("카메라연결매니저", "연결 해제 감지 - 상태 초기화")
                    withContext(Dispatchers.Main) {
                        _cameraFeed.value = emptyList()
                        _cameraCapabilities.value = null
                    }
                } else {
                    Log.d("카메라연결매니저", "연결 상태 변경 없음 또는 중복 - 업데이트 생략")
                }
            }
        }
    }

    private suspend fun updateCameraCapabilities() = withContext(Dispatchers.IO) {
        try {
            Log.d("카메라연결매니저", "카메라 기능 정보 업데이트")

            // 마스터 데이터 사용 여부 결정
            val capabilities = if (usbCameraManager.isNativeCameraConnected.value) {
                Log.d("카메라연결매니저", "USB 카메라 연결됨 - 마스터 데이터로 기능 정보 생성")

                // 마스터 데이터에서 정보 가져오기
                val abilitiesJson = usbCameraManager.getCameraAbilitiesFromMaster()
                val widgetJson = usbCameraManager.buildWidgetJsonFromMaster()

                // CameraCapabilities 생성 로직을 직접 처리
                parseCameraCapabilitiesFromJson(abilitiesJson, widgetJson)
            } else {
                Log.d("카메라연결매니저", "USB 카메라 미연결 - 직접 네이티브 호출")
                nativeDataSource.getCameraCapabilities()
            }

            capabilities?.let {
                withContext(Dispatchers.Main) {
                    _cameraCapabilities.value = it
                }
                Log.d("카메라연결매니저", "카메라 기능 정보 업데이트 완료: ${it.model}")
            }
        } catch (e: Exception) {
            Log.e("카메라연결매니저", "카메라 기능 정보 업데이트 실패", e)
        }
    }

    /**
     * JSON 데이터에서 CameraCapabilities 객체를 생성하는 함수
     */
    private fun parseCameraCapabilitiesFromJson(
        abilitiesJson: String,
        widgetJson: String
    ): CameraCapabilities? {
        return try {
            Log.d("카메라연결매니저", "마스터 데이터에서 CameraCapabilities 파싱 시작")

            // abilities JSON 파싱
            val abilities = try {
                org.json.JSONObject(abilitiesJson)
            } catch (e: Exception) {
                Log.e("카메라연결매니저", "abilities JSON 파싱 실패: $abilitiesJson", e)
                org.json.JSONObject()
            }

            // 모델명 추출
            val model = abilities.optString("model", "알 수 없음")

            // 라이브뷰 지원 확인
            val supportsLiveView = widgetJson.contains("liveviewsize", ignoreCase = true) ||
                    widgetJson.contains("liveview", ignoreCase = true)

            val capabilities = CameraCapabilities(
                model = model,
                canCapturePhoto = abilities.optBoolean("canCapturePhoto", true),
                canCaptureVideo = abilities.optBoolean("canCaptureVideo", false),
                canLiveView = supportsLiveView,
                canTriggerCapture = abilities.optBoolean("canTriggerCapture", true),
                supportsAutofocus = abilities.optBoolean("supportsAutofocus", true),
                supportsManualFocus = abilities.optBoolean("supportsManualFocus", false),
                supportsFocusPoint = abilities.optBoolean("supportsFocusPoint", false),
                supportsBurstMode = abilities.optBoolean("supportsBurstMode", false),
                supportsTimelapse = abilities.optBoolean("supportsTimelapse", false),
                supportsBracketing = abilities.optBoolean("supportsBracketing", false),
                supportsBulbMode = abilities.optBoolean("supportsBulbMode", false),
                canDownloadFiles = abilities.optBoolean("canDownloadFiles", true),
                canDeleteFiles = abilities.optBoolean("canDeleteFiles", false),
                canPreviewFiles = abilities.optBoolean("canPreviewFiles", false),
                availableIsoSettings = emptyList(), // TODO: 위젯에서 파싱 필요
                availableShutterSpeeds = emptyList(), // TODO: 위젯에서 파싱 필요
                availableApertures = emptyList(), // TODO: 위젯에서 파싱 필요
                availableWhiteBalanceSettings = emptyList(), // TODO: 위젯에서 파싱 필요
                supportsRemoteControl = abilities.optBoolean("supportsRemoteControl", false),
                supportsConfigChange = abilities.optBoolean("supportsConfigChange", false),
                batteryLevel = null
            )

            Log.d("카메라연결매니저", "마스터 데이터에서 CameraCapabilities 파싱 완료: ${capabilities.model}")
            capabilities
        } catch (e: Exception) {
            Log.e("카메라연결매니저", "CameraCapabilities 파싱 실패", e)
            null
        }
    }

    /**
     * PTPIP 연결 상태 업데이트
     */
    fun updatePtpipConnectionStatus(isConnected: Boolean) {
        Log.d("카메라연결매니저", "PTPIP 연결 상태 업데이트: $isConnected")
        _isPtpipConnected.value = isConnected
    }
}