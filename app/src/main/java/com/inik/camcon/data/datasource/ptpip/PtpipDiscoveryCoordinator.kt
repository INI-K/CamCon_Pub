package com.inik.camcon.data.datasource.ptpip

import android.util.Log
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.data.network.ptpip.discovery.PtpipDiscoveryService
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.NikonConnectionMode
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * PTP/IP 카메라 디스커버리 조율 협력자 (PtpipDataSource에서 분리).
 *
 * mDNS/AP 검색, 수동 IP 입력·수동 카메라 등록, 발견 목록 상태([discoveredCameras])를 소유한다.
 * 연결 진행 중(CONNECTING) 검색 스킵을 위해 연결 상태는 [connectionStateProvider]로 읽는다
 * (연결 엔진은 파사드가 소유 — 상태 공유는 읽기 전용 람다로 최소화).
 */
internal class PtpipDiscoveryCoordinator(
    private val wifiHelper: WifiNetworkHelper,
    private val discoveryService: PtpipDiscoveryService,
    private val connectionManager: PtpipConnectionManager,
    private val connectionStateProvider: () -> PtpipConnectionState,
    private val ioDispatcher: CoroutineDispatcher
) {
    private companion object {
        private const val TAG = "PtpipDataSource"
    }

    private val _discoveredCameras = MutableStateFlow<List<PtpipCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<PtpipCamera>> = _discoveredCameras.asStateFlow()

    // 사용자가 직접 입력한 카메라 IP. 폰 핫스팟 모드의 mDNS 폴백용.
    private val _manualIp = MutableStateFlow("")
    val manualIp: StateFlow<String> = _manualIp.asStateFlow()

    /**
     * 사용자 입력 IP 갱신. UI/ViewModel에서 호출.
     *
     * 빈 문자열은 초기화 신호로 그대로 받는다. 그 외에는 사설망/link-local 화이트리스트만 허용한다.
     * 위반 시 상태를 갱신하지 않고 경고만 남긴다 (UI는 기존 입력 유지).
     */
    fun setManualIp(ip: String) {
        if (ip.isBlank()) {
            _manualIp.value = ""
            return
        }
        if (!com.inik.camcon.data.network.ptpip.IpAddressValidator.isAllowedCameraIp(ip)) {
            Log.w(TAG, "setManualIp 거부: 허용되지 않은 IP 형식/대역 - ${LogMask.id(ip)}")
            return
        }
        _manualIp.value = ip
    }

    /**
     * 사용자가 입력한 IP를 카메라 후보로 등록한다.
     * 동일 IP가 이미 있으면 사용자 입력 정보(이름/포트)로 갱신한다.
     * `distinctBy`는 첫 occurrence를 유지하므로 사용자가 mDNS로 발견된 카메라의
     * 이름/포트를 수동 입력으로 덮어쓸 수 없는 문제가 있어 명시적 filterNot+append로 처리.
     *
     * IP는 사설망/link-local만 허용. 화이트리스트 외에는 `IllegalArgumentException`을 던진다.
     */
    fun addManualCamera(ipAddress: String, name: String, port: Int): PtpipCamera {
        require(
            com.inik.camcon.data.network.ptpip.IpAddressValidator.isAllowedCameraIp(ipAddress)
        ) {
            "허용되지 않은 카메라 IP: ${ipAddress.take(45)} (사설망/link-local만 허용)"
        }
        val safeName = name.ifBlank { "Manual ($ipAddress)" }
        val safePort = if (port > 0) port else 15740
        val cam = PtpipCamera(ipAddress, safePort, safeName, isOnline = true)
        _discoveredCameras.value =
            _discoveredCameras.value.filterNot { it.ipAddress == cam.ipAddress } + cam
        return cam
    }

    /**
     * mDNS를 사용하여 PTPIP 지원 카메라 검색
     */
    suspend fun discoverCameras(forceApMode: Boolean): List<PtpipCamera> {
        return try {
            Log.d(TAG, "카메라 검색 시작")

            // 연결 시도와 검색이 겹치지 않도록 직렬화: 연결 중이면 기존 목록 유지 반환
            if (connectionStateProvider() == PtpipConnectionState.CONNECTING) {
                Log.d(TAG, "연결 진행 중 - 검색 건너뜀 (직렬화 보호)")
                return _discoveredCameras.value
            }

            // Wi-Fi 연결 상태 확인. 단 폰 핫스팟(STA_PHONE_HOTSPOT) 모드에선 폰이 SoftAP라
            // 클라이언트 연결이 없는 게 정상이므로, 핫스팟이 켜져 있으면 mDNS 검색을 진행한다.
            if (!wifiHelper.isWifiConnected() && !wifiHelper.isHotspotEnabled()) {
                Log.w(TAG, "Wi-Fi 네트워크에 연결되어 있지 않음 (핫스팟도 꺼짐)")
                return emptyList()
            }

            // AP모드인지 확인하고 직접 IP 사용
            if (wifiHelper.isConnectedToCameraAP()) {
                Log.d(TAG, "AP모드 감지: libgphoto2 기반 카메라 IP 검색 시작")
                val cameraIP = wifiHelper.findAvailableCameraIP()
                if (cameraIP != null) {
                    Log.i(TAG, "AP모드: libgphoto2로 검증된 카메라 IP ${LogMask.id(cameraIP)} 발견")
                    val networkName = wifiHelper.getCurrentSSID() ?: "카메라 AP"
                    val apCamera = PtpipCamera(
                        ipAddress = cameraIP,
                        port = 15740, // 표준 PTP/IP 포트
                        name = "$networkName (AP모드)",
                        isOnline = true
                    )
                    _discoveredCameras.value = listOf(apCamera)
                    return listOf(apCamera)
                } else {
                    Log.w(TAG, "❌ AP모드이지만 libgphoto2로 연결 가능한 카메라 IP를 찾을 수 없음")
                    // 빈 리스트 반환하여 사용자에게 상황을 알림
                    _discoveredCameras.value = emptyList()
                    return emptyList()
                }
            }

            // STA모드에서는 mDNS 검색 사용
            Log.d(TAG, "STA모드 또는 일반 네트워크: mDNS 검색 시작")
            val cameras = discoveryService.discoverCameras(forceApMode)
            _discoveredCameras.value = cameras
            cameras
        } catch (e: Exception) {
            Log.e(TAG, "카메라 검색 중 오류", e)
            emptyList()
        }
    }

    // 호환성용 무파라미터 래퍼
    suspend fun discoverCameras(): List<PtpipCamera> = discoverCameras(false)

    /**
     * 니콘 카메라 연결 모드 감지 (AP/STA/UNKNOWN)
     */
    suspend fun detectNikonConnectionMode(camera: PtpipCamera): NikonConnectionMode =
        withContext(ioDispatcher) {
            try {
                Log.d(TAG, "니콘 카메라 연결 모드 감지 시작: ${LogMask.serial(camera.name)}")

                // 기본 연결 시도 - AP 모드는 즉시 연결 가능
                if (connectionManager.establishConnection(camera)) {
                    val deviceInfo = connectionManager.getDeviceInfo()
                    connectionManager.closeConnections()

                    if (deviceInfo?.manufacturer?.contains("Nikon", ignoreCase = true) == true) {
                        Log.d(TAG, "AP 모드 감지 (즉시 연결 성공)")
                        return@withContext NikonConnectionMode.AP_MODE
                    }
                }

                // AP 모드 실패 시 STA 모드로 판단
                Log.d(TAG, "STA 모드 감지 (기본 연결 실패)")
                return@withContext NikonConnectionMode.STA_MODE

            } catch (e: Exception) {
                Log.e(TAG, "니콘 카메라 모드 감지 중 오류", e)
                return@withContext NikonConnectionMode.UNKNOWN
            }
        }
}
