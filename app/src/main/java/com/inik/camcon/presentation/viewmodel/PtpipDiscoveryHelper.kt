package com.inik.camcon.presentation.viewmodel

import android.util.Log
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.repository.PtpipRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PtpipViewModel의 카메라 검색 및 Wi-Fi 스캔 관련 로직을 담당하는 헬퍼.
 *
 * 담당 기능:
 * - Wi-Fi SSID 스캔 (주변 네트워크 검색)
 * - mDNS 기반 PTP/IP 카메라 검색
 * - 검색 결과 기반 자동 연결
 * - 위치 서비스 확인
 */
@Singleton
class PtpipDiscoveryHelper @Inject constructor(
    private val ptpipRepository: PtpipRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "PtpipDiscoveryHelper"
    }

    // ── Wi-Fi SSID 스캔 ──────────────────────────────────────────

    /**
     * 주변 Wi-Fi 네트워크 스캔 (SSID 리스트 반환).
     *
     * @param onDiscoveringChanged 검색 중 상태 변경 콜백
     * @param onErrorChanged 에러 메시지 변경 콜백
     * @param onNearbyWifiUpdated 스캔 결과 SSID 목록 콜백
     * @param onNeedLocationSettings 위치 설정 필요 시 콜백
     */
    fun scanNearbyWifiNetworks(
        onDiscoveringChanged: (Boolean) -> Unit,
        onErrorChanged: (String?) -> Unit,
        onNearbyWifiUpdated: (List<String>) -> Unit,
        onNeedLocationSettings: (Boolean) -> Unit
    ) {
        Log.d(TAG, "scanNearbyWifiNetworks 메서드 호출됨")

        scope.launch {
            try {
                Log.d(TAG, "Wi-Fi 스캔 시작 - 사전 점검 진행")

                val wifiEnabled = ptpipRepository.isWifiEnabled()
                Log.d(TAG, "Wi-Fi 활성화 상태: $wifiEnabled")

                if (!wifiEnabled) {
                    Log.w(TAG, "Wi‑Fi가 꺼져 있음")
                    onErrorChanged("Wi‑Fi가 꺼져 있습니다. Wi‑Fi를 켜주세요.")
                    return@launch
                }

                val locationEnabled = ptpipRepository.isLocationEnabled()
                Log.d(TAG, "위치 서비스 활성화 상태: $locationEnabled")

                if (!locationEnabled) {
                    Log.w(TAG, "위치 서비스가 꺼져 있음 - Google Play Services 설정 확인 시도")
                    onNeedLocationSettings(true)
                    onErrorChanged("Wi-Fi 스캔을 위해 위치 서비스가 필요합니다.")
                    return@launch
                }

                Log.d(TAG, "사전 점검 완료 - Wi-Fi 스캔 시작")
                onDiscoveringChanged(true)
                onErrorChanged(null)

                val ssids = ptpipRepository.scanNearbyWifiSSIDs()
                Log.d(TAG, "Wi-Fi 스캔 결과: ${ssids.size}개 SSID 발견")

                ssids.forEach { ssid ->
                    Log.d(TAG, "  발견된 SSID: $ssid")
                }

                onNearbyWifiUpdated(ssids)

                if (ssids.isEmpty()) {
                    Log.i(TAG, "주변에 Wi-Fi 네트워크가 없음")
                } else {
                    Log.i(TAG, "Wi-Fi 스캔 성공: ${ssids.size}개 발견")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi 스캔 중 오류 발생", e)
                onNearbyWifiUpdated(emptyList())
                onErrorChanged("주변 Wi‑Fi 스캔 중 오류: ${e.message}")
            } finally {
                onDiscoveringChanged(false)
                Log.d(TAG, "Wi-Fi 스캔 작업 완료")
            }
        }
    }

    /**
     * Google Play Services를 통한 위치 설정 확인.
     *
     * @param onNeedLocationSettings 위치 설정 필요 상태 변경 콜백
     * @param onRescanRequested 스캔 재시도 콜백
     */
    fun checkLocationSettings(
        onNeedLocationSettings: (Boolean) -> Unit,
        onRescanRequested: () -> Unit
    ) {
        Log.d(TAG, "Google Play Services 위치 설정 확인 시작")
        ptpipRepository.checkLocationSettings(
            onSuccess = {
                Log.d(TAG, "위치 설정 확인 성공 - Wi-Fi 스캔 재시도")
                onNeedLocationSettings(false)
                onRescanRequested()
            },
            onFailure = { exception ->
                Log.w(TAG, "위치 설정 확인 실패: ${exception.message}")
                onNeedLocationSettings(true)
            }
        )
    }

    // ── 카메라 검색 ──────────────────────────────────────────

    /**
     * Wi-Fi 네트워크에서 PTP/IP 카메라를 검색하고 자동으로 첫 번째 카메라에 연결한다.
     *
     * @param forceApMode AP 모드 강제 사용 여부
     * @param onDiscoveringChanged 검색 중 상태 변경 콜백
     * @param onConnectingChanged 연결 중 상태 변경 콜백
     * @param onErrorChanged 에러 메시지 변경 콜백
     * @param onCameraSelected 카메라 선택 콜백
     */
    fun discoverCameras(
        forceApMode: Boolean = false,
        onDiscoveringChanged: (Boolean) -> Unit,
        onConnectingChanged: (Boolean) -> Unit,
        onErrorChanged: (String?) -> Unit,
        onCameraSelected: (PtpipCamera) -> Unit
    ) {
        Log.i(TAG, "사용자가 카메라 검색을 요청했습니다")

        scope.launch {
            try {
                onDiscoveringChanged(true)
                onErrorChanged(null)

                if (!ptpipRepository.isWifiConnected()) {
                    val errorMsg = "Wi-Fi가 연결되어 있지 않습니다. Wi-Fi를 켜고 네트워크에 연결해주세요."
                    Log.w(TAG, errorMsg)
                    onErrorChanged(errorMsg)
                    return@launch
                }

                val networkState = ptpipRepository.getCurrentWifiNetworkState()
                if (networkState.isConnectedToCameraAP) {
                    Log.i(TAG, "AP 모드 연결 감지됨: ${networkState.ssid}")
                    Log.i(TAG, "카메라 IP: ${networkState.detectedCameraIP}")
                } else {
                    Log.i(TAG, "STA 모드 또는 일반 네트워크 연결")
                }

                Log.i(TAG, "Wi-Fi 연결 확인됨, 카메라 검색 시작...")
                val cameras = ptpipRepository.discoverCameras(forceApMode)

                Log.i(TAG, "카메라 검색 완료: ${cameras.size}개 발견")

                if (cameras.isEmpty()) {
                    val errorMsg = if (networkState.isConnectedToCameraAP) {
                        "카메라 AP에 연결되어 있지만 카메라를 찾을 수 없습니다.\n" +
                                "카메라의 Wi-Fi 설정을 확인하고 다시 시도해주세요."
                    } else {
                        "PTPIP 지원 카메라를 찾을 수 없습니다. 같은 네트워크에 카메라가 연결되어 있는지 확인해주세요."
                    }
                    Log.w(TAG, errorMsg)
                    onErrorChanged(errorMsg)
                } else {
                    Log.i(TAG, "카메라 검색 성공:")
                    cameras.forEachIndexed { index, camera ->
                        Log.i(
                            TAG,
                            "  ${index + 1}. ${camera.name} (${camera.ipAddress}:${camera.port})"
                        )
                    }
                    onErrorChanged(null)

                    val firstCamera = cameras.first()
                    Log.i(
                        TAG,
                        "첫 번째 카메라 자동 선택: ${firstCamera.name} (${firstCamera.ipAddress}:${firstCamera.port})"
                    )
                    onCameraSelected(firstCamera)

                    val modeText = if (forceApMode) "AP 모드" else "STA 모드"
                    Log.i(TAG, "=== libgphoto2 초기화 및 카메라 연결 시작 ($modeText) ===")
                    onConnectingChanged(true)

                    delay(500)

                    val connectionSuccess =
                        ptpipRepository.connectToCamera(firstCamera, forceApMode)

                    if (connectionSuccess) {
                        Log.i(TAG, "카메라 연결 성공! ($modeText)")
                        onErrorChanged(null)
                    } else {
                        Log.e(TAG, "카메라 연결 실패 ($modeText)")
                        onErrorChanged("카메라 연결에 실패했습니다")
                        onConnectingChanged(false)
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = "카메라 검색 중 오류가 발생했습니다: ${e.message}"
                Log.e(TAG, errorMsg, e)
                onErrorChanged(errorMsg)
            } finally {
                onDiscoveringChanged(false)
                Log.d(TAG, "카메라 검색 작업 완료")
            }
        }
    }
}
