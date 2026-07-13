package com.inik.camcon.data.repository.managers

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.inik.camcon.utils.LogMask
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeDataSource: NativeCameraDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val cameraStateObserver: com.inik.camcon.domain.manager.CameraStateObserver,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private companion object {
        // connectToCamera 비동기 초기화 완료(isNativeCameraConnected=true) 대기 한도.
        // 내부 initializeNativeCamera 가 delay(500)+네이티브 초기화를 수행하므로 넉넉히 잡는다.
        private const val INIT_AWAIT_TIMEOUT_MS = 20_000L
    }

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
            withContext(ioDispatcher) {
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

                    val connectionResult = connectCameraInternal()
                    connectionResult
                } catch (e: CancellationException) {
                    _isInitializing.value = false
                    throw e
                } catch (e: Exception) {
                    Log.e("카메라연결매니저", "카메라 연결 중 예외 발생", e)
                    // 예외 발생 시에만 초기화 상태 해제
                    _isInitializing.value = false
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
        Log.d("카메라연결매니저", "연결된 USB 디바이스 발견: ${LogMask.path(device.deviceName)}")

        // USB 권한 요청
        if (!usbCameraManager.hasUsbPermission.value) {
            Log.d("카메라연결매니저", "USB 권한 없음, 권한 요청")
            withContext(Dispatchers.Main) {
                usbCameraManager.requestPermission(device)
            }
            // 권한 요청은 예외가 아닌 Result.failure로 반환되므로 connectCamera의 catch가
            // 동작하지 않는다. 초기화 플래그를 직접 해제해 UI 오버레이 고착을 방지한다.
            _isInitializing.value = false
            return Result.failure(Exception("USB 권한이 필요합니다"))
        }

        // 중요: USB 연결 + 네이티브 초기화(initCameraWithFd)는 전적으로
        // UsbConnectionManager.connectToCamera 단일 경로에 위임한다.
        // (C1) 과거에는 여기서 getFileDescriptor()+initCameraWithFd를 직접 한 번 더 호출해
        // 동일 FD에 대해 서로 다른 Mutex로 initCameraWithFd가 이중 호출되어
        // libusb 더블오픈/세션 손상(-7/-53)이 간헐 발생했다. 직접 호출을 제거하고
        // connectToCamera가 세팅하는 isNativeCameraConnected StateFlow의 완료만 대기한다.
        Log.d("카메라연결매니저", "USB 디바이스 연결 시작(단일 초기화 경로 위임): ${LogMask.path(device.deviceName)}")
        usbCameraManager.connectToCamera(device)

        // connectToCamera는 비동기(scope.launch)로 초기화를 수행하므로
        // isNativeCameraConnected=true 가 될 때까지(또는 타임아웃까지) 대기한다.
        val connected = kotlinx.coroutines.withTimeoutOrNull(INIT_AWAIT_TIMEOUT_MS) {
            usbCameraManager.isNativeCameraConnected.first { it }
        } ?: false

        return if (connected) {
            Log.d("카메라연결매니저", "네이티브 카메라 초기화 성공(connectToCamera 경로)")
            _isConnected.value = true
            _isInitializing.value = false
            updateCameraList()
            Result.success(true)
        } else {
            Log.e("카메라연결매니저", "네이티브 카메라 초기화 완료 대기 타임아웃 - USB 연결 실패")
            // 초기화 완료 신호를 받지 못함. 실패 사유는 UsbConnectionManager.lastErrorKind에 담긴다.
            // PtpTimeoutException으로 반환해야 UsbAutoConnectManager가 '앱 재시작' 안내 다이얼로그를
            // 띄운다(C1 회귀 방지: generic Exception은 해당 분기를 타지 못해 안내가 사라짐).
            _isInitializing.value = false
            Result.failure(
                com.inik.camcon.domain.model.PtpTimeoutException(
                    "USB 카메라 초기화에 실패했습니다.\n케이블 재연결 후 다시 시도해주세요."
                )
            )
        }
    }

    private suspend fun connectGeneralCamera(): Result<Boolean> {
        // USB 연결이 안되면 일반 초기화 시도
        Log.d("카메라연결매니저", "일반 카메라 초기화 시도")
        // 네이티브 initCamera()는 성공 시 정확히 "OK"를, 실패 시 gphoto2 에러 문자열을 반환한다.
        // 부분 일치(contains)는 우연한 매칭/누락 위험이 있으므로 성공 토큰을 정확히 비교한다.
        val result = nativeDataSource.initCamera()
        return if (result.trim().equals("OK", ignoreCase = true)) {
            Log.d("카메라연결매니저", "일반 카메라 초기화 성공")
            _isConnected.value = true
            _isInitializing.value = false  // 초기화 완료 시 상태 해제
            updateCameraList()
            Result.success(true)
        } else {
            Log.e("카메라연결매니저", "일반 카메라 초기화 실패: $result")
            _isInitializing.value = false  // 실패 시에도 상태 해제
            Result.failure(Exception("카메라 연결 실패: $result"))
        }
    }

    suspend fun disconnectCamera(): Result<Boolean> {
        return withContext(ioDispatcher) {
            try {
                Log.d("카메라연결매니저", "카메라 연결 해제 시작")

                // 네이티브 카메라 연결 해제
                nativeDataSource.closeCamera()

                // UsbConnectionManager 의 연결 추적 상태도 리셋한다. 누락 시
                // _isNativeCameraConnected / lastInitializedFd / currentConnection 이 stale true 로 남아
                // 재연결이 '이미 연결됨'으로 조기 성공(네이티브는 닫힘 → UI 만 연결로 오표시)한다.
                usbCameraManager.resetConnectionStateForDisconnect()

                _isConnected.value = false
                _isPtpipConnected.value = false
                _cameraFeed.value = emptyList()

                Log.d("카메라연결매니저", "카메라 연결 해제 완료")
                Result.success(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("카메라연결매니저", "카메라 연결 해제 중 오류", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun updateCameraList() = withContext(ioDispatcher) {
        try {
            Log.d("카메라연결매니저", "카메라 목록 업데이트")
            val detected = nativeDataSource.detectCamera()
            if (com.inik.camcon.BuildConfig.DEBUG) {
                Log.d("카메라연결매니저", "detectCamera 반환값: $detected")
            }

            if (detected != "No camera detected") {
                // 감지된 카메라 문자열을 줄 단위로 분할
                val lines = detected.split("\n")
                    .filter { it.isNotBlank() }

                Log.d("카메라연결매니저", "분할된 줄 수: ${lines.size}")

                // 실제 카메라 정보가 포함된 줄만 필터링
                val cameraLines = lines.filter { line ->
                    line.contains("@") && (line.contains("[") || line.matches(Regex(".*\\w+.*@.*")))
                }

                Log.d("카메라연결매니저", "필터링된 카메라 라인 수: ${cameraLines.size}")

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

                _cameraFeed.value = cameras
                Log.d("카메라연결매니저", "카메라 목록 업데이트 완료: ${cameras.size}개")
            } else {
                Log.d("카메라연결매니저", "카메라가 감지되지 않음")
                _cameraFeed.value = emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("카메라연결매니저", "카메라 목록 업데이트 실패", e)
        }
    }

    private fun observeNativeCameraConnection() {
        scope.launch(ioDispatcher) {
            usbCameraManager.isNativeCameraConnected.collect { isConnected ->
                Log.d("카메라연결매니저", "네이티브 카메라 연결 상태 변경: $isConnected")

                // 상태가 실제로 변경된 경우만 업데이트
                val currentConnected = _isConnected.value
                if (currentConnected != isConnected) {
                    _isConnected.value = isConnected
                    Log.d("카메라연결매니저", "연결 상태 업데이트: $currentConnected -> $isConnected")
                }

                if (isConnected && !currentConnected) {
                    // 연결 상태가 false -> true로 변경된 경우에만 업데이트
                    Log.d("카메라연결매니저", "새로운 연결 감지 - 기능 정보 업데이트")

                    // 연결 안정화를 위한 짧은 지연 후 업데이트
                    kotlinx.coroutines.delay(500)

                    // 카메라 목록 업데이트는 생략 (detectCamera가 느리므로)
                    // 이미 카메라가 초기화되어 있으므로 목록 조회 불필요
                    Log.d("카메라연결매니저", "카메라 목록 업데이트 건너뜀 (성능 최적화)")

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
                    _cameraFeed.value = emptyList()
                    _cameraCapabilities.value = null
                }
            }
        }
    }

    private suspend fun updateCameraCapabilities() = withContext(ioDispatcher) {
        try {
            Log.d(
                "카메라연결매니저",
                "카메라 기능 정보 업데이트 (USB=${usbCameraManager.isNativeCameraConnected.value}, PTPIP=${_isPtpipConnected.value})"
            )

            // 마스터 데이터 사용 여부 결정 (USB 또는 PTPIP 연결 시)
            val capabilities =
                if (usbCameraManager.isNativeCameraConnected.value || _isPtpipConnected.value) {
                    Log.d("카메라연결매니저", "카메라 연결됨 - 네이티브에서 직접 기능 정보 가져오기")

                    // 네이티브에서 직접 CameraCapabilities 가져오기
                    nativeDataSource.getCameraCapabilities()
                } else {
                    Log.d("카메라연결매니저", "카메라 미연결 - 기능 정보 없음")
                    null
            }

            if (capabilities != null) {
                Log.d(
                    "카메라연결매니저",
                    "카메라 기능 정보 업데이트 완료: ${capabilities.model} (라이브뷰=${capabilities.canLiveView})"
                )

                try {
                    _cameraCapabilities.value = capabilities
                    cameraStateObserver.updateCameraCapabilities(capabilities)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("카메라연결매니저", "  UI 업데이트 실패", e)
                }
            } else {
                Log.w("카메라연결매니저", "카메라 기능 정보가 null입니다")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("카메라연결매니저", "카메라 기능 정보 업데이트 실패", e)
        }
    }

    /**
     * PTPIP 연결 상태 업데이트
     */
    fun updatePtpipConnectionStatus(isConnected: Boolean) {
        // 동일 상태 중복 호출 무시 — 초기화 시 GlobalManager가 5개 Flow 초깃값마다 false를
        // 반복 방출하던 로그 폭주/다운스트림 재계산을 차단한다.
        if (_isPtpipConnected.value == isConnected) return
        Log.d("카메라연결매니저", "PTPIP 연결 상태 업데이트: $isConnected")
        _isPtpipConnected.value = isConnected

        // UI 상태에도 PTPIP 연결 상태 반영 (CameraUiState.isConnected 갱신)
        cameraStateObserver.updatePtpipConnectionState(isConnected)

        // PTPIP 연결 시에도 카메라 기능 정보 업데이트
        if (isConnected) {
            Log.d("카메라연결매니저", "PTPIP 연결됨 - 카메라 기능 정보 업데이트 시작")
            scope.launch(ioDispatcher) {
                updateCameraCapabilities()
            }
        }
    }
}