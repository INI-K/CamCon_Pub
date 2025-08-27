package com.inik.camcon.data.datasource.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.inik.camcon.CameraNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * USB 연결 복구를 담당하는 클래스
 * UsbCameraManager에서 분리하여 복구 로직을 전담
 */
@Singleton
class UsbConnectionRecovery @Inject constructor(
    private val context: Context
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    companion object {
        private const val TAG = "USB복구"
    }

    /**
     * 단순하고 효과적인 USB 연결 복구 시도
     * 네이티브에서 완전 정리 → 권한 재요청 → 새 연결 생성
     */
    suspend fun attemptSimpleRecovery(
        currentDevice: UsbDevice?,
        currentConnection: UsbDeviceConnection?,
        getDevicesFunction: () -> List<UsbDevice>,
        errorCallback: ((errorCode: Int, errorMessage: String) -> Unit)? = null
    ): RecoveryResult = withContext(Dispatchers.IO) {

        return@withContext try {
            Log.d(TAG, "🔄 단순 USB 복구 시작 (네이티브 완전 정리 + 권한 재요청)")

            // 1단계: 네이티브에서 모든 리소스 완전 정리
            cleanupNativeCompletely()

            // 2단계: 코틀린에서 USB 연결 완전 해제
            releaseUsbConnectionCompletely(currentConnection)

            // 3단계: 잠시 대기 (시스템 안정화)
            delay(2000)

            // 4단계: 디바이스 재검색
            val devices = getDevicesFunction()
            Log.d(TAG, "복구 후 발견된 디바이스: ${devices.size}개")

            if (devices.isEmpty()) {
                Log.e(TAG, "복구 후 디바이스를 찾을 수 없음")
                return@withContext RecoveryResult.failure("디바이스 없음")
            }

            val device = devices.first()
            Log.d(TAG, "복구 대상 디바이스: ${device.deviceName}")

            // 5단계: 권한 상태 확인
            val hasPermission = usbManager.hasPermission(device)
            Log.d(TAG, "현재 권한 상태: $hasPermission")

            if (!hasPermission) {
                Log.e(TAG, "권한이 없음 - 사용자가 권한을 재요청해야 함")
                return@withContext RecoveryResult.failure("권한 필요 - 사용자 권한 재요청 필요")
            }

            // 6단계: 새로운 USB 연결 생성
            val newConnection = usbManager.openDevice(device)
            if (newConnection == null) {
                Log.e(TAG, "새 USB 연결 생성 실패")
                return@withContext RecoveryResult.failure("USB 연결 생성 실패")
            }

            val fd = newConnection.fileDescriptor
            Log.d(TAG, "새 USB 연결 성공 - FD=$fd")

            // 7단계: PTP 인터페이스 클레임
            claimPtpInterface(device, newConnection)

            // 8단계: USB 인터페이스 안정화를 위한 충분한 대기
            Log.d(TAG, "⏳ USB 인터페이스 안정화 대기 중...")
            delay(2000) // 2초로 증가

            // 9단계: 네이티브 초기화 전 추가 검증
            if (!newConnection.fileDescriptor.toString().contains("-1")) {
                Log.d(TAG, "✅ USB 연결 상태 검증 완료 - FD는 유효함")
            } else {
                Log.e(TAG, "❌ USB 연결 상태 검증 실패 - FD가 유효하지 않음")
                newConnection.close()
                return@withContext RecoveryResult.failure("USB 연결 검증 실패")
            }

            // 10단계: 네이티브 카메라 초기화 (1번만 시도)
            Log.d(TAG, "🚀 네이티브 카메라 초기화 시도 (FD=$fd)")
            val initResult =
                CameraNative.initCameraWithFd(fd, context.applicationInfo.nativeLibraryDir)
            Log.d(TAG, "단순 복구 초기화 결과: $initResult")

            if (initResult == 0) {
                Log.d(TAG, "✅ 단순 USB 복구 성공!")
                errorCallback?.invoke(0, "USB 연결 복구 성공!")
                RecoveryResult.success(device, newConnection, fd)
            } else {
                Log.e(TAG, "❌ 단순 복구 후에도 초기화 실패: $initResult")
                newConnection.close()

                // -7 오류인 경우 권한 재요청을 권장
                if (initResult == -7) {
                    Log.w(TAG, "⚠️ libgphoto2 커널 드라이버 접근 실패 - 안드로이드 시스템 한계")
                    Log.i(TAG, "💡 해결방안:")
                    Log.i(TAG, "  1) 카메라 USB 모드를 'Mass Storage'에서 'PTP/MTP'로 변경")
                    Log.i(TAG, "  2) 다른 USB 케이블 사용 (데이터 전송 지원 케이블)")
                    Log.i(TAG, "  3) 카메라 전원을 완전히 끈 후 다시 켜기")
                    Log.i(TAG, "  4) 안드로이드 개발자 옵션에서 'USB 디버깅' 비활성화")
                    Log.i(TAG, "  5) 앱 재시작 후 다시 시도")

                    Log.i(TAG, "🔄 고급 복구 모드로 전환 - 이것은 오류가 아닌 정상적인 복구 과정입니다")

                    // 먼저 일반 초기화 시도 (FD 없이)
                    Log.i(TAG, "🔄 Fallback: 일반 초기화 시도 (FD 없이)")
                    try {
                        val fallbackResult = CameraNative.initCamera()
                        Log.d(TAG, "일반 초기화 결과: $fallbackResult")

                        if (fallbackResult.contains("OK", ignoreCase = true)) {
                            Log.d(TAG, "✅ 일반 초기화 성공!")
                            // 새 연결 다시 생성
                            val fallbackConnection = usbManager.openDevice(device)
                            if (fallbackConnection != null) {
                                errorCallback?.invoke(0, "일반 초기화를 통해 카메라 연결 성공!")
                                return@withContext RecoveryResult.success(
                                    device,
                                    fallbackConnection,
                                    fallbackConnection.fileDescriptor
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "일반 초기화 fallback 실패", e)
                    }

                    // 일반 초기화도 실패하면 권한 재요청 모드로 전환
                    Log.i(TAG, "⚠️ 일반 초기화도 실패 - 고급 복구 모드 필요")
                    // 권한 재요청을 요구하는 특별한 실패 케이스
                    RecoveryResult.failure("PERMISSION_REFRESH_REQUIRED")
                } else {
                    Log.e(TAG, "기타 초기화 오류: $initResult")
                    RecoveryResult.failure("초기화 실패: $initResult")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "단순 USB 복구 중 예외 발생", e)
            RecoveryResult.failure("복구 중 예외: ${e.message}")
        }
    }

    /**
     * 네이티브에서 모든 리소스 완전 정리 (libusb 포함)
     */
    private suspend fun cleanupNativeCompletely() {
        try {
            Log.d(TAG, "🧹 네이티브 완전 정리 시작")

            // 진행 중인 모든 작업 중단
            CameraNative.cancelAllOperations()
            delay(500)

            // 카메라 완전 종료를 백그라운드 스레드에서 실행
            withContext(Dispatchers.Default) {
                try {
                    // 카메라 완전 종료 (gp_camera_exit + gp_context_unref + libusb_close)
                    CameraNative.closeCamera()
                    delay(1000)

                    // 추가로 한번 더 호출하여 확실히 정리
                    CameraNative.closeCamera()
                    Log.d(TAG, "네이티브 완전 정리 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "네이티브 정리 중 오류 (정상적일 수 있음)", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "네이티브 정리 중 외부 오류", e)
        }
    }

    /**
     * 코틀린에서 USB 연결 완전 해제
     */
    private fun releaseUsbConnectionCompletely(connection: UsbDeviceConnection?) {
        try {
            Log.d(TAG, "🔌 USB 연결 완전 해제")
            connection?.close()
            Log.d(TAG, "USB 연결 해제 완료")
        } catch (e: Exception) {
            Log.w(TAG, "USB 연결 해제 중 오류", e)
        }
    }

    /**
     * PTP 인터페이스를 더 적극적으로 클레임
     * 안드로이드에서 libgphoto2의 커널 드라이버 접근 문제를 완화
     */
    private fun claimPtpInterface(device: UsbDevice, connection: UsbDeviceConnection) {
        try {
            Log.d(TAG, "📷 모든 인터페이스 적극적 클레임 시작")

            var ptpInterfaceClaimed = false

            for (i in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(i)
                Log.d(
                    TAG,
                    "인터페이스 $i: 클래스=${usbInterface.interfaceClass}, 서브클래스=${usbInterface.interfaceSubclass}, 프로토콜=${usbInterface.interfaceProtocol}"
                )

                // PTP 인터페이스 (클래스 6) 또는 Vendor Specific (클래스 255)을 강제로 클레임
                if (usbInterface.interfaceClass == 6 ||
                    usbInterface.interfaceClass == 255 ||
                    usbInterface.interfaceClass == 8
                ) { // Mass Storage도 포함

                    try {
                        // 강제로 인터페이스 클레임 (다른 드라이버로부터 해제)
                        val claimed = connection.claimInterface(usbInterface, true)
                        if (claimed) {
                            Log.d(TAG, "✅ 인터페이스 $i (클래스=${usbInterface.interfaceClass}) 클레임 성공")

                            if (usbInterface.interfaceClass == 6) {
                                ptpInterfaceClaimed = true
                            }

                            // 인터페이스의 모든 엔드포인트 정보 로깅
                            for (j in 0 until usbInterface.endpointCount) {
                                val endpoint = usbInterface.getEndpoint(j)
                                Log.d(
                                    TAG,
                                    "  엔드포인트 $j: 주소=0x${endpoint.address.toString(16)}, 타입=${endpoint.type}, 방향=${endpoint.direction}"
                                )
                            }
                        } else {
                            Log.w(TAG, "❌ 인터페이스 $i 클레임 실패")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "인터페이스 $i 클레임 중 예외", e)
                    }
                }
            }

            if (ptpInterfaceClaimed) {
                Log.d(TAG, "🎯 PTP 인터페이스 클레임 성공 - libgphoto2 접근 가능성 향상")
            } else {
                Log.w(TAG, "⚠️ PTP 인터페이스를 찾을 수 없음 - Vendor Specific 인터페이스로 대체")
            }

        } catch (e: Exception) {
            Log.e(TAG, "인터페이스 클레임 중 오류", e)
        }
    }

    /**
     * USB 복구 결과를 나타내는 데이터 클래스
     */
    data class RecoveryResult(
        val isSuccess: Boolean,
        val device: UsbDevice? = null,
        val connection: UsbDeviceConnection? = null,
        val fileDescriptor: Int = -1,
        val errorMessage: String? = null
    ) {
        companion object {
            fun success(device: UsbDevice, connection: UsbDeviceConnection, fd: Int) =
                RecoveryResult(true, device, connection, fd)

            fun failure(message: String) =
                RecoveryResult(false, errorMessage = message)
        }
    }
}