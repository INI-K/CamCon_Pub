package com.inik.camcon.presentation.viewmodel

import android.util.Log
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.repository.PtpipDebugRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PtpipViewModel의 디버그/테스트 관련 로직을 담당하는 헬퍼.
 *
 * 담당 기능:
 * - 기본 PTP/IP 연결 테스트
 * - Nikon Phase 1/2 인증 테스트
 * - 개별 Nikon 명령 테스트
 * - 소켓 연결 테스트
 * - 포트 스캔
 */
@Singleton
class PtpipDebugHelper @Inject constructor(
    private val debugRepository: PtpipDebugRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "PtpipDebugHelper"
    }

    /**
     * 디버그 테스트 결과를 전달하기 위한 콜백 인터페이스.
     */
    interface DebugCallback {
        fun onConnectingChanged(connecting: Boolean)
        fun onErrorChanged(message: String?)
    }

    /**
     * 1단계: 기본 PTPIP 연결 테스트
     */
    fun testBasicPtpipConnection(camera: PtpipCamera, callback: DebugCallback) {
        Log.i(TAG, "=== 디버그: 기본 PTPIP 연결 테스트 시작 ===")

        scope.launch {
            try {
                callback.onConnectingChanged(true)
                callback.onErrorChanged(null)

                delay(1000)

                val result = debugRepository.testBasicConnection(camera)

                if (result.success) {
                    Log.i(TAG, "기본 PTPIP 연결 성공")

                    delay(500)

                    if (result.deviceInfo != null) {
                        Log.i(TAG, "디바이스 정보 획득 성공: ${result.deviceInfo.manufacturer} ${result.deviceInfo.model}")
                        delay(1000)
                    } else {
                        Log.w(TAG, "디바이스 정보 획득 실패")
                    }

                    callback.onErrorChanged(result.message)
                    Log.d(TAG, "연결 유지 (카메라 Wi-Fi 종료 방지)")
                } else {
                    Log.e(TAG, "기본 PTPIP 연결 실패")
                    callback.onErrorChanged(result.message)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "기본 연결 테스트 중 오류: ${e.message}")
                callback.onErrorChanged("기본 연결 테스트 오류: ${e.message}")
            } finally {
                callback.onConnectingChanged(false)
            }
        }
    }

    /**
     * 2단계: 니콘 Phase 1 인증 테스트
     */
    fun testNikonPhase1Authentication(camera: PtpipCamera, callback: DebugCallback) {
        Log.i(TAG, "=== 디버그: 니콘 Phase 1 인증 테스트 시작 ===")

        scope.launch {
            try {
                callback.onConnectingChanged(true)
                callback.onErrorChanged(null)

                val success = debugRepository.testPhase1Auth(camera)

                if (success) {
                    Log.i(TAG, "니콘 Phase 1 인증 성공")
                    callback.onErrorChanged("Phase 1 인증 성공")
                } else {
                    Log.e(TAG, "니콘 Phase 1 인증 실패")
                    callback.onErrorChanged("Phase 1 인증 실패")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Phase 1 테스트 중 오류: ${e.message}")
                callback.onErrorChanged("Phase 1 테스트 오류: ${e.message}")
            } finally {
                callback.onConnectingChanged(false)
            }
        }
    }

    /**
     * 3단계: 니콘 Phase 2 인증 테스트
     */
    fun testNikonPhase2Authentication(camera: PtpipCamera, callback: DebugCallback) {
        Log.i(TAG, "=== 디버그: 니콘 Phase 2 인증 테스트 시작 ===")

        scope.launch {
            try {
                callback.onConnectingChanged(true)
                callback.onErrorChanged(null)

                val success = debugRepository.testPhase2Auth(camera)

                if (success) {
                    Log.i(TAG, "니콘 Phase 2 인증 성공")
                    callback.onErrorChanged("Phase 2 인증 성공")
                } else {
                    Log.e(TAG, "니콘 Phase 2 인증 실패")
                    callback.onErrorChanged("Phase 2 인증 실패")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Phase 2 테스트 중 오류: ${e.message}")
                callback.onErrorChanged("Phase 2 테스트 오류: ${e.message}")
            } finally {
                callback.onConnectingChanged(false)
            }
        }
    }

    /**
     * 4단계: 개별 니콘 명령 테스트
     */
    fun testNikonCommand(camera: PtpipCamera, command: String, callback: DebugCallback) {
        Log.i(TAG, "=== 디버그: 니콘 명령 테스트 시작 ($command) ===")

        scope.launch {
            try {
                callback.onConnectingChanged(true)
                callback.onErrorChanged(null)

                val success = debugRepository.testNikonCommand(camera, command)

                if (success) {
                    Log.i(TAG, "니콘 명령 ($command) 성공")
                    callback.onErrorChanged("$command 명령 성공")
                } else {
                    Log.e(TAG, "니콘 명령 ($command) 실패")
                    callback.onErrorChanged("$command 명령 실패")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "명령 테스트 중 오류: ${e.message}")
                callback.onErrorChanged("명령 테스트 오류: ${e.message}")
            } finally {
                callback.onConnectingChanged(false)
            }
        }
    }

    /**
     * 5단계: 소켓 연결 테스트
     */
    fun testSocketConnection(camera: PtpipCamera, callback: DebugCallback) {
        Log.i(TAG, "=== 디버그: 소켓 연결 테스트 시작 ===")

        scope.launch {
            try {
                callback.onConnectingChanged(true)
                callback.onErrorChanged(null)

                val success = debugRepository.testSocketConnection(camera)

                if (success) {
                    Log.i(TAG, "소켓 연결 성공")
                    callback.onErrorChanged("소켓 연결 성공")
                } else {
                    Log.e(TAG, "소켓 연결 실패")
                    callback.onErrorChanged("소켓 연결 실패")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "소켓 연결 테스트 중 오류: ${e.message}")
                callback.onErrorChanged("소켓 연결 테스트 오류: ${e.message}")
            } finally {
                callback.onConnectingChanged(false)
            }
        }
    }

    /**
     * 6단계: 포트 스캔 테스트
     */
    fun testPortScan(ipAddress: String, callback: DebugCallback) {
        Log.i(TAG, "=== 디버그: 포트 스캔 테스트 시작 ===")

        scope.launch {
            try {
                callback.onConnectingChanged(true)
                callback.onErrorChanged(null)

                val openPorts = debugRepository.scanPorts(ipAddress)

                if (openPorts.isNotEmpty()) {
                    Log.i(TAG, "열린 포트 발견: ${openPorts.joinToString(", ")}")
                    callback.onErrorChanged("열린 포트: ${openPorts.joinToString(", ")}")
                } else {
                    Log.w(TAG, "열린 포트 없음")
                    callback.onErrorChanged("열린 포트 없음")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "포트 스캔 테스트 중 오류: ${e.message}")
                callback.onErrorChanged("포트 스캔 테스트 오류: ${e.message}")
            } finally {
                callback.onConnectingChanged(false)
            }
        }
    }
}
