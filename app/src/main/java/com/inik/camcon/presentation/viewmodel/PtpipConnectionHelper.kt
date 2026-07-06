package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.Manifest
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.inik.camcon.R
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.AutoConnectNetworkConfig
import com.inik.camcon.domain.model.CameraCaptureCallback
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.SavedWifiCredential
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.domain.usecase.camera.DeleteGphotoSettingsUseCase
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PtpipViewModel의 Wi-Fi 연결/해제 및 카메라 연결 관련 로직을 담당하는 헬퍼.
 *
 * 담당 기능:
 * - Wi-Fi SSID 연결 (WifiNetworkSpecifier)
 * - 저장된 자격 증명 관리 및 연결
 * - 카메라 연결/해제
 * - 자동 연결 설정 관리 (updateAutoConnectEnabled)
 * - 설정 변경 (PTPIP 활성화, 타임아웃, 포트 등)
 * - 수동 촬영
 * - 네트워크 상태 조회
 */
@Singleton
class PtpipConnectionHelper @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val ptpipRepository: PtpipRepository,
    private val preferencesRepository: PtpipPreferencesRepository,
    private val globalManager: CameraConnectionGlobalManager,
    private val deleteGphotoSettingsUseCase: DeleteGphotoSettingsUseCase,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "PtpipConnectionHelper"
    }

    /** 마지막으로 연결 성공한 Wi-Fi 구성 (자동 연결 설정에 사용) */
    var lastConnectedWifiConfig: AutoConnectNetworkConfig? = null

    // ── Wi-Fi SSID 연결 ──────────────────────────────────────────

    /**
     * WifiNetworkSpecifier로 SSID 연결 요청 (패스워드 포함).
     *
     * 콜백을 통해 연결 성공/실패 결과를 반환한다.
     * @param onConnectionStateChanged isConnecting 상태 변경 콜백
     * @param onErrorChanged 에러 메시지 변경 콜백
     * @param onCameraCreated 연결 성공 후 카메라 정보 생성 콜백
     */
    fun connectToWifiSsid(
        ssid: String,
        passphrase: String? = null,
        onConnectionStateChanged: (Boolean) -> Unit,
        onErrorChanged: (String?) -> Unit,
        onCameraCreated: (PtpipCamera) -> Unit
    ) {
        Log.d(TAG, "Wi-Fi 연결 시작: ssid='${LogMask.ssid(ssid)}', 패스워드 제공=${!passphrase.isNullOrEmpty()}")

        onConnectionStateChanged(true)
        onErrorChanged(null)

        scope.launch(ioDispatcher) {
            // libgphoto2 설정 초기화
            try {
                deleteGphotoSettingsUseCase()
                delay(500)
                Log.i(TAG, "libgphoto2 설정 초기화 완료")
            } catch (e: Exception) {
                Log.w(TAG, "설정 삭제 중 오류 (계속 진행): ${e.message}")
            }

            try {
                val securityType = ptpipRepository.getWifiSecurityType(ssid)
                val currentBssid = ptpipRepository.getCurrentBssid()
                val candidateConfig = AutoConnectNetworkConfig(
                    ssid = ssid,
                    passphrase = passphrase,
                    securityType = securityType,
                    isHidden = false,
                    bssid = currentBssid
                )

                ptpipRepository.requestWifiConnection(
                    ssid = ssid,
                    passphrase = passphrase,
                    onResult = { success: Boolean ->
                        scope.launch {
                            Log.d(TAG, "WifiNetworkSpecifier 결과: success=$success")
                            if (!success) {
                                val errorMsg = appContext.getString(
                                    R.string.progress_wifi_auto_connect_failed,
                                    ssid
                                )
                                Log.e(TAG, errorMsg)
                                onErrorChanged(errorMsg)
                                onConnectionStateChanged(false)
                                try {
                                    ptpipRepository.disconnect()
                                } catch (e: Exception) {
                                    Log.w(TAG, "연결 정리 중 오류: ${e.message}")
                                }
                            } else {
                                Log.i(TAG, "Wi-Fi 연결 성공: ${LogMask.ssid(ssid)} - 카메라 정보 직접 생성")
                                onErrorChanged(null)

                                val nextBssid =
                                    ptpipRepository.getCurrentBssid() ?: candidateConfig.bssid
                                val updatedConfig =
                                    candidateConfig.copy(
                                        lastUpdatedEpochMillis = System.currentTimeMillis(),
                                        bssid = nextBssid
                                    )
                                lastConnectedWifiConfig = updatedConfig

                                preferencesRepository.saveAutoConnectNetworkConfig(updatedConfig)

                                // Wi-Fi 자격 증명 저장
                                if (!passphrase.isNullOrEmpty()) {
                                    preferencesRepository.saveWifiCredential(
                                        SavedWifiCredential(
                                            ssid = ssid,
                                            passphrase = passphrase,
                                            security = securityType ?: "WPA2",
                                            bssid = nextBssid,
                                            lastConnectedAt = System.currentTimeMillis()
                                        )
                                    )
                                    Log.d(TAG, "Wi-Fi 자격 증명 저장 완료: ${LogMask.ssid(ssid)}")
                                }

                                val autoConnectEnabled =
                                    preferencesRepository.isAutoConnectEnabledNow()
                                if (autoConnectEnabled) {
                                    ptpipRepository.sendAutoConnectBroadcast(ssid)
                                }

                                // 연결 성공 후 카메라 정보 생성
                                createCameraFromConnectedWifi(
                                    ssid,
                                    onConnectionStateChanged,
                                    onErrorChanged,
                                    onCameraCreated
                                )
                            }
                        }
                    },
                    onError = { errorMsg: String ->
                        scope.launch {
                            Log.e(TAG, "WifiNetworkSpecifier 상세 오류: $errorMsg")
                            onErrorChanged(errorMsg)
                            onConnectionStateChanged(false)
                            try {
                                ptpipRepository.disconnect()
                            } catch (e: Exception) {
                                Log.w(TAG, "연결 정리 중 오류: ${e.message}")
                            }
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = appContext.getString(
                    R.string.progress_wifi_request_exception,
                    e.message.orEmpty()
                )
                Log.e(TAG, errorMsg, e)
                onErrorChanged(errorMsg)
                onConnectionStateChanged(false)
                try {
                    ptpipRepository.disconnect()
                } catch (disconnectError: Exception) {
                    Log.w(TAG, "연결 정리 중 오류: ${disconnectError.message}")
                }
            }
        }
    }

    private fun createCameraFromConnectedWifi(
        ssid: String,
        onConnectionStateChanged: (Boolean) -> Unit,
        onErrorChanged: (String?) -> Unit,
        onCameraCreated: (PtpipCamera) -> Unit
    ) {
        scope.launch {
            try {
                Log.d(TAG, "Wi-Fi 연결 확인 — 카메라 IP 감지 시작")

                val cameraIP = ptpipRepository.detectCameraIPFromCurrentNetwork() ?: "192.168.1.1"
                val currentPortValue = 15740

                val camera = PtpipCamera(
                    name = appContext.getString(R.string.progress_camera_label_connected, ssid),
                    ipAddress = cameraIP,
                    port = currentPortValue,
                    isOnline = true
                )

                Log.i(
                    TAG,
                    "Wi-Fi 연결 성공 후 카메라 정보 생성: ${camera.name} (${camera.ipAddress}:${camera.port})"
                )

                onCameraCreated(camera)

                Log.i(TAG, "Wi-Fi 연결 성공 후 카메라 연결 시도")

                val connectionSuccess = ptpipRepository.connectToCamera(camera, forceApMode = true)
                if (!connectionSuccess) {
                    Log.e(TAG, "카메라 연결 실패 - 네트워크 상태 재확인")
                    onErrorChanged(appContext.getString(R.string.progress_camera_connect_failed))
                    onConnectionStateChanged(false)
                } else {
                    onConnectionStateChanged(false)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi 연결 후 카메라 연결 과정에서 오류", e)
                onErrorChanged(
                    appContext.getString(
                        R.string.progress_camera_connect_exception,
                        e.message.orEmpty()
                    )
                )
                onConnectionStateChanged(false)
            }
        }
    }

    /**
     * 저장된 비밀번호로 SSID 연결
     */
    suspend fun getSavedCredentialPassphrase(ssid: String): String? {
        val credential = preferencesRepository.getSavedWifiCredential(ssid)
        return credential?.passphrase
    }

    /**
     * 저장된 Wi-Fi 자격 증명 삭제
     */
    suspend fun deleteSavedWifiCredential(ssid: String) {
        preferencesRepository.deleteSavedWifiCredential(ssid)
        Log.d(TAG, "저장된 Wi-Fi 자격 증명 삭제: ${LogMask.ssid(ssid)}")
    }

    // ── 카메라 연결/해제 ──────────────────────────────────────────

    /**
     * 카메라 연결 (AP/STA 모드 지원)
     */
    suspend fun connectToCamera(camera: PtpipCamera, forceApMode: Boolean = false): Boolean {
        val success = ptpipRepository.connectToCamera(camera, forceApMode)
        if (success) {
            if (globalManager.isApModeConnected()) {
                Log.i(TAG, "AP 모드 연결 완료 - 파일 수신 리스너 활성화")
            } else {
                Log.i(TAG, "카메라 연결 완료 - 파일 수신 리스너 활성화")
            }
            preferencesRepository.saveLastConnectedCamera(camera.ipAddress, camera.name)
        }
        return success
    }

    /**
     * 카메라 연결 해제
     */
    suspend fun disconnect() {
        Log.d(TAG, "카메라 연결 해제 시작")
        ptpipRepository.disconnect()
        ptpipRepository.releaseWifiLock()
        Log.d(TAG, "카메라 연결 해제 완료")
    }

    /**
     * 수동 사진 촬영
     */
    suspend fun capturePhoto(listener: CameraCaptureCallback?) {
        ptpipRepository.capturePhoto(listener)
        Log.d(TAG, "수동 촬영 명령 전송 완료")
    }

    /** 물리 셔터 무선 수신 모드 시작/중지 (니콘 STA vendor 풀해상도). */
    fun startShutterListening(camera: PtpipCamera) {
        ptpipRepository.startShutterListening(camera)
        Log.i(TAG, "물리 셔터 무선 수신 시작: ${camera.name}")
    }

    fun stopShutterListening() {
        ptpipRepository.stopShutterListening()
        Log.i(TAG, "물리 셔터 무선 수신 중지")
    }

    // ── 설정 관리 ──────────────────────────────────────────

    suspend fun setPtpipEnabled(enabled: Boolean) {
        preferencesRepository.setPtpipEnabled(enabled)
    }

    suspend fun setAutoReconnectEnabled(enabled: Boolean) {
        preferencesRepository.setAutoReconnectEnabled(enabled)
    }

    suspend fun setConnectionTimeout(timeout: Int) {
        preferencesRepository.setConnectionTimeout(timeout)
    }

    suspend fun setDiscoveryTimeout(timeout: Int) {
        preferencesRepository.setDiscoveryTimeout(timeout)
    }

    suspend fun setPtpipPort(port: Int) {
        preferencesRepository.setPtpipPort(port)
    }

    suspend fun resetSettings() {
        preferencesRepository.clearAllSettings()
    }

    /**
     * 자동 연결 활성화/비활성화 with network config
     */
    fun updateAutoConnectEnabled(
        enabled: Boolean,
        onResult: (Boolean, String) -> Unit,
        onRequestNotificationPermission: (() -> Unit)? = null
    ) {
        // UI 콜백(onResult→Toast, onRequestNotificationPermission)이 여기서 직접 호출되므로
        // 메인 디스패처에서 실행해야 한다. @ApplicationScope(Default)에서 돌면 Toast가
        // "Can't toast on a thread that has not called Looper.prepare()"로 크래시한다.
        // (내부 preferences/WifiManager 호출은 전부 빠른 suspend/Binder라 Main에서 안전.)
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            if (enabled) {
                val storedConfig = preferencesRepository.getAutoConnectNetworkConfig()
                val networkConfig = storedConfig ?: lastConnectedWifiConfig

                // STA 핫스팟은 AP networkConfig가 없어도 정상이다. 전제조건은
                // "직전에 한 번이라도 성공 연결한 카메라(last_connected_ip)"의 존재.
                // AP·STA 둘 다 아무 근거가 없을 때만 "먼저 수동 연결" 안내.
                val hasLastCamera = preferencesRepository.getLastConnectedCameraInfo() != null
                if (networkConfig == null && !hasLastCamera) {
                    onResult(
                        false,
                        appContext.getString(R.string.auto_connect_requires_setup)
                    )
                    return@launch
                }

                val requiresNotificationPermission =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !isNotificationPermissionGranted()

                if (requiresNotificationPermission) {
                    onRequestNotificationPermission?.invoke()
                    return@launch
                }

                if (networkConfig != null) {
                    // AP 모드 경로 보존 — 네트워크 suggestion 등록 후 서비스 기동.
                    val suggestionResult =
                        ptpipRepository.registerNetworkSuggestion(networkConfig)
                    if (suggestionResult.success) {
                        val latestBssid = ptpipRepository.getCurrentBssid()
                        val updatedConfig =
                            networkConfig.copy(
                                lastUpdatedEpochMillis = System.currentTimeMillis(),
                                bssid = latestBssid ?: networkConfig.bssid
                            )
                        preferencesRepository.saveAutoConnectNetworkConfig(updatedConfig)
                        preferencesRepository.updateAutoConnectNetworkTimestamp()
                        preferencesRepository.setAutoConnectEnabled(true)
                        lastConnectedWifiConfig = updatedConfig

                        ptpipRepository.startWifiMonitoring()
                        Log.d(TAG, "WifiMonitoringService 시작됨 (AP suggestion 등록)")

                        onResult(true, suggestionResult.message)
                    } else {
                        onResult(false, suggestionResult.message)
                    }
                } else {
                    // STA 핫스팟 경로 — networkConfig 없이 직전 카메라만 기억. suggestion 없이 서비스만 기동.
                    preferencesRepository.setAutoConnectEnabled(true)
                    ptpipRepository.startWifiMonitoring()
                    Log.d(TAG, "WifiMonitoringService 시작됨 (STA 핫스팟, 직전 카메라 자동 연결)")

                    onResult(
                        true,
                        appContext.getString(R.string.auto_connect_enabled_message)
                    )
                }
            } else {
                val existingConfig = preferencesRepository.getAutoConnectNetworkConfig()
                if (existingConfig != null) {
                    val removalResult =
                        ptpipRepository.removeNetworkSuggestion(existingConfig)
                    if (!removalResult.success) {
                        onResult(false, removalResult.message)
                        return@launch
                    }
                }
                preferencesRepository.setAutoConnectEnabled(false)

                ptpipRepository.stopWifiMonitoring()
                Log.d(TAG, "WifiMonitoringService 중지됨")

                onResult(
                    true,
                    appContext.getString(R.string.auto_connect_disabled_message)
                )
            }
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ── 상태 조회 ──────────────────────────────────────────

    fun isWifiConnected(): Boolean = ptpipRepository.isWifiConnected()

    fun isStaConcurrencySupported(): Boolean = ptpipRepository.isStaConcurrencySupported()

    fun getWifiCapabilities(): WifiCapabilities = ptpipRepository.getWifiCapabilities()

    fun getCurrentWifiNetworkState(): WifiNetworkState = ptpipRepository.getCurrentWifiNetworkState()

    fun getConnectionStatusText(currentState: PtpipConnectionState): String {
        return when (currentState) {
            PtpipConnectionState.DISCONNECTED -> "연결 안됨"
            PtpipConnectionState.CONNECTING -> "연결 중..."
            PtpipConnectionState.CONNECTED -> "연결됨"
            PtpipConnectionState.ERROR -> "연결 오류"
        }
    }

    fun getNetworkStatusMessage(networkState: WifiNetworkState): String {
        return when {
            !networkState.isConnected -> "Wi-Fi 연결 안됨"
            networkState.isConnectedToCameraAP -> "카메라 AP 연결됨 (${networkState.ssid})"
            networkState.ssid != null -> "일반 Wi-Fi 연결됨 (${networkState.ssid})"
            else -> "네트워크 연결됨"
        }
    }

    fun getComprehensiveStatusMessage(
        currentConnectionState: PtpipConnectionState,
        networkState: WifiNetworkState
    ): String {
        return when {
            !networkState.isConnected -> "Wi-Fi 연결 필요"
            currentConnectionState == PtpipConnectionState.CONNECTED -> "카메라 연결됨"
            currentConnectionState == PtpipConnectionState.CONNECTING -> "카메라 연결 중..."
            currentConnectionState == PtpipConnectionState.ERROR -> "카메라 연결 오류"
            networkState.isConnectedToCameraAP -> "카메라 AP 연결됨 - 카메라 검색 가능"
            else -> "카메라 연결 안됨"
        }
    }

    fun isApModeConnected() = globalManager.isApModeConnected()

    fun isStaModeConnected() = globalManager.isStaModeConnected()

    fun clearConnectionLostMessage() {
        ptpipRepository.clearConnectionLostMessage()
    }

    fun setAutoReconnectOnDataSource(enabled: Boolean) {
        ptpipRepository.setAutoReconnectEnabled(enabled)
    }

    // ── 클린업 ──────────────────────────────────────────

    suspend fun cleanup() {
        ptpipRepository.disconnect()
        ptpipRepository.cleanup()
        globalManager.cleanup()

        try {
            if (ptpipRepository.isWifiLockHeld()) {
                ptpipRepository.releaseWifiLock()
                Log.d(TAG, "클린업 시 Wi-Fi 락 해제")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "클린업 시 Wi-Fi 락 해제 실패: ${e.message}")
        }
    }
}
