package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.domain.model.AutoConnectNetworkConfig
import com.inik.camcon.domain.model.CameraCaptureCallback
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.NetworkSuggestionResult
import com.inik.camcon.domain.repository.PtpipRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PtpipDataSource를 래핑하여 domain의 PtpipRepository 인터페이스를 구현한다.
 *
 * presentation 레이어가 PtpipDataSource를 직접 참조하지 않도록
 * 중간 계층 역할을 수행한다.
 */
@Singleton
class PtpipRepositoryImpl @Inject constructor(
    private val ptpipDataSource: PtpipDataSource
) : PtpipRepository {

    // ── StateFlow 위임 ──

    override val connectionState: StateFlow<PtpipConnectionState>
        get() = ptpipDataSource.connectionState

    override val connectionProgressMessage: StateFlow<String>
        get() = ptpipDataSource.connectionProgressMessage

    override val discoveredCameras: StateFlow<List<PtpipCamera>>
        get() = ptpipDataSource.discoveredCameras

    override val cameraInfo: StateFlow<PtpipCameraInfo?>
        get() = ptpipDataSource.cameraInfo

    override val wifiNetworkState: StateFlow<WifiNetworkState>
        get() = ptpipDataSource.wifiNetworkState

    override val connectionLostMessage: StateFlow<String?>
        get() = ptpipDataSource.connectionLostMessage

    // ── 카메라 연결/해제 ──

    override suspend fun connectToCamera(camera: PtpipCamera, forceApMode: Boolean): Boolean =
        ptpipDataSource.connectToCamera(camera, forceApMode)

    override suspend fun disconnect() { ptpipDataSource.disconnect() }

    override fun cleanup() = ptpipDataSource.cleanup()

    // ── 카메라 검색 ──

    override suspend fun discoverCameras(forceApMode: Boolean): List<PtpipCamera> =
        ptpipDataSource.discoverCameras(forceApMode)

    // ── 촬영 ──

    override suspend fun capturePhoto(callback: CameraCaptureCallback?) {
        val listener: CameraCaptureListener? = if (callback != null) {
            object : CameraCaptureListener {
                override fun onFlushComplete() = callback.onFlushComplete()
                override fun onPhotoCaptured(filePath: String, fileName: String) =
                    callback.onPhotoCaptured(filePath, fileName)
                override fun onPhotoDownloaded(
                    filePath: String,
                    fileName: String,
                    imageData: ByteArray
                ) = callback.onPhotoDownloaded(filePath, fileName, imageData)
                override fun onCaptureFailed(errorCode: Int) = callback.onCaptureFailed(errorCode)
                override fun onUsbDisconnected() = callback.onUsbDisconnected()
            }
        } else null
        ptpipDataSource.capturePhoto(listener)
    }

    // ── 네트워크 상태 조회 ──

    override fun isWifiConnected(): Boolean = ptpipDataSource.isWifiConnected()

    override fun isWifiEnabled(): Boolean = ptpipDataSource.isWifiEnabled()

    override fun isLocationEnabled(): Boolean = ptpipDataSource.isLocationEnabled()

    override fun isStaConcurrencySupported(): Boolean = ptpipDataSource.isStaConcurrencySupported()

    override fun getWifiCapabilities(): WifiCapabilities = ptpipDataSource.getWifiCapabilities()

    override fun getCurrentWifiNetworkState(): WifiNetworkState =
        ptpipDataSource.getCurrentWifiNetworkState()

    // ── Wi-Fi 연결 관리 ──

    override fun requestWifiConnection(
        ssid: String,
        passphrase: String?,
        onResult: (Boolean) -> Unit,
        onError: ((String) -> Unit)?
    ) = ptpipDataSource.requestWifiSpecifierConnection(ssid, passphrase, onResult, onError)

    override fun getWifiSecurityType(ssid: String): String? =
        ptpipDataSource.getWifiHelper().getWifiSecurityType(ssid)

    override fun getCurrentBssid(): String? =
        ptpipDataSource.getWifiHelper().getCurrentBssid()

    override fun detectCameraIPFromCurrentNetwork(): String? =
        ptpipDataSource.getWifiHelper().detectCameraIPFromCurrentNetwork()

    override fun releaseWifiLock() =
        ptpipDataSource.getWifiHelper().releaseWifiLock()

    override fun isWifiLockHeld(): Boolean =
        ptpipDataSource.getWifiHelper().isWifiLockHeld()

    // ── 자동 연결 관련 ──

    override fun registerNetworkSuggestion(
        config: AutoConnectNetworkConfig
    ): NetworkSuggestionResult {
        val result = ptpipDataSource.getWifiHelper().registerNetworkSuggestion(config)
        return NetworkSuggestionResult(result.success, result.message)
    }

    override fun removeNetworkSuggestion(
        config: AutoConnectNetworkConfig
    ): NetworkSuggestionResult {
        val result = ptpipDataSource.getWifiHelper().removeNetworkSuggestion(config)
        return NetworkSuggestionResult(result.success, result.message)
    }

    override fun sendAutoConnectBroadcast(ssid: String) =
        ptpipDataSource.getWifiHelper().sendAutoConnectBroadcast(ssid)

    override fun setAutoReconnectEnabled(enabled: Boolean) =
        ptpipDataSource.setAutoReconnectEnabled(enabled)

    override fun clearConnectionLostMessage() =
        ptpipDataSource.clearConnectionLostMessage()

    // ── 위치 설정 ──

    override fun checkLocationSettings(
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        ptpipDataSource.getWifiHelper().checkLocationSettingsForScan()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    // ── Wi-Fi SSID 스캔 ──

    override suspend fun scanNearbyWifiSSIDs(): List<String> =
        ptpipDataSource.scanNearbyWifiSSIDs()
}
