package com.inik.camcon.presentation.viewmodel

import android.util.Log
import com.inik.camcon.R
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.domain.model.DiscoveryAttemptResult
import com.inik.camcon.domain.model.DiscoveryEmptyReason
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PtpipViewModel의 카메라 검색 및 Wi-Fi 스캔 관련 로직을 담당하는 헬퍼.
 *
 * 담당 기능:
 * - Wi-Fi SSID 스캔 (주변 네트워크 검색)
 * - mDNS 기반 PTP/IP 카메라 검색 (**검색 전용** — 선택·연결 결정은 하지 않는다)
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
     * Wi-Fi 네트워크에서 PTP/IP 카메라를 **검색만** 한다.
     *
     * ⚠️ 자동 선택·자동 연결은 이 클래스에서 제거됐다. 이 헬퍼는 `@Singleton` + `@ApplicationScope`
     * 이므로 연결 결정이 남아 있으면 화면이 종료된 뒤에도 연결이 계속 진행된다(취소 불가).
     * 후보 0/1/2+ 분기와 자동 연결 허용 판정은 `CameraSelectionPolicy` 단일 지점이 담당하고,
     * 그 결정을 실행하는 것은 ViewModel이다.
     *
     * @param forceApMode AP 모드 강제 사용 여부
     * @param onDiscoveringChanged 검색 중 상태 변경 콜백
     * @param onResult 검색 결과 + 0건 사유 + (예외 시) 표시 메시지
     */
    fun discoverCameras(
        forceApMode: Boolean = false,
        onDiscoveringChanged: (Boolean) -> Unit,
        onResult: (DiscoveryAttemptResult) -> Unit
    ) {
        Log.i(TAG, "사용자가 카메라 검색을 요청했습니다")

        scope.launch {
            try {
                onDiscoveringChanged(true)

                // 폰 핫스팟(STA_PHONE_HOTSPOT) 모드에선 폰이 SoftAP라서 Wi-Fi 클라이언트 연결이 없어
                // isWifiConnected()=false 가 정상이다(폰이 게이트웨이). 따라서 클라이언트 연결도 없고
                // 핫스팟도 꺼져 있을 때(=진짜로 네트워크 없음)에만 차단하고, 핫스팟이 켜져 있으면 진행한다.
                val networkState = ptpipRepository.getCurrentWifiNetworkState()
                if (!ptpipRepository.isWifiConnected() && !networkState.isHotspotEnabled) {
                    Log.w(TAG, "Wi-Fi 미연결 + 핫스팟 꺼짐 - 검색 차단")
                    onResult(
                        DiscoveryAttemptResult(emptyList(), DiscoveryEmptyReason.NO_NETWORK)
                    )
                    return@launch
                }

                // 세션 점유 중(CONNECTING/CONNECTED/무선수신)에는 검색하지 않는다 — 중복 검색이
                // 단일 PTP/IP 세션을 흔든다. 기존 목록은 그대로 유지해 UI 목록 소실을 막는다.
                if (ptpipRepository.isDiscoveryBlocked()) {
                    Log.i(TAG, "세션 점유 중 - 검색 스킵 (기존 목록 유지)")
                    onResult(
                        DiscoveryAttemptResult(
                            ptpipRepository.discoveredCameras.value,
                            DiscoveryEmptyReason.BLOCKED_BUSY
                        )
                    )
                    return@launch
                }

                if (networkState.isConnectedToCameraAP) {
                    Log.i(TAG, "AP 모드 연결 감지됨: ${LogMask.ssid(networkState.ssid)}")
                } else if (networkState.isHotspotEnabled) {
                    Log.i(TAG, "폰 핫스팟(STA_PHONE_HOTSPOT) 모드 감지됨")
                } else {
                    Log.i(TAG, "STA 모드 또는 일반 네트워크 연결")
                }

                Log.i(TAG, "네트워크 확인됨, 카메라 검색 시작...")
                val cameras = ptpipRepository.discoverCameras(forceApMode)
                Log.i(TAG, "카메라 검색 완료: ${cameras.size}개 발견")

                val reason = when {
                    cameras.isNotEmpty() -> DiscoveryEmptyReason.NONE
                    networkState.isConnectedToCameraAP -> DiscoveryEmptyReason.CAMERA_AP_EMPTY
                    else -> DiscoveryEmptyReason.NOT_FOUND
                }
                onResult(DiscoveryAttemptResult(cameras, reason))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 검색 중 오류", e)
                onResult(
                    DiscoveryAttemptResult(
                        cameras = emptyList(),
                        reason = DiscoveryEmptyReason.NOT_FOUND,
                        error = UiText.Resource(
                            R.string.ptpip_discovery_error_fmt,
                            listOf(e.message.orEmpty())
                        )
                    )
                )
            } finally {
                onDiscoveringChanged(false)
                Log.d(TAG, "카메라 검색 작업 완료")
            }
        }
    }
}
