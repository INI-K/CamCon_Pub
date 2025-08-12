package com.inik.camcon.data.network.ptpip.authentication

import android.util.Log
import com.inik.camcon.data.constants.PtpipConstants
import com.inik.camcon.domain.model.PtpipCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 니콘 카메라 STA 모드 인증 서비스
 * 니콘 카메라의 Wi-Fi STA 모드 연결 시 필요한 인증 프로세스 처리
 */
@Singleton
class NikonAuthenticationService @Inject constructor() {
    companion object {
        private const val TAG = "NikonAuthService"
        private const val NIKON_952B_COMMAND = 0x952b
        private const val NIKON_935A_COMMAND = 0x935a
        private const val MAX_RETRIES = 3
    }

    /**
     * 니콘 STA 모드 전체 인증 시퀀스
     */
    suspend fun performStaAuthentication(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "니콘 STA 모드 인증 시퀀스 시작")

                // Phase 1: 승인 요청 임시 연결
                if (!performPhase1Authentication(camera)) {
                    Log.e(TAG, "Phase 1 인증 실패")
                    return@withContext false
                }

                // Phase 1과 2 사이의 대기 (카메라 내부 처리)
                Log.i(TAG, "카메라 내부 처리 대기 중... (5초)")
                delay(5000)

                // Phase 2: 인증된 메인 연결
                if (!performPhase2Authentication(camera)) {
                    Log.e(TAG, "Phase 2 인증 실패")
                    return@withContext false
                }

                Log.i(TAG, "✅ 니콘 STA 모드 인증 완료!")
                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "니콘 STA 모드 인증 중 오류: ${e.message}")
                return@withContext false
            }
        }

    /**
     * Phase 1: 승인 요청 임시 연결
     */
    private suspend fun performPhase1Authentication(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            var commandSocket: Socket? = null
            var eventSocket: Socket? = null
            var retryCount = 0

            while (retryCount < MAX_RETRIES) {
                try {
                    Log.i(TAG, "Phase 1: 승인 요청 임시 연결 시작")
                    Log.d(TAG, "연결 대상: ${camera.ipAddress}:${camera.port}")

                    // 연결 전 대기 시간 추가 (기존 연결과의 충돌 방지)
                    Log.d(TAG, "기존 연결 해제 대기 중... (2초)")
                    delay(2000)

                    // 1-1: Command 소켓 연결 및 초기화
                    Log.d(TAG, "Step 1-1: Command 소켓 연결 시도")
                    commandSocket = Socket()

                    try {
                        commandSocket.connect(
                            InetSocketAddress(camera.ipAddress, camera.port),
                            5000
                        )
                        Log.d(TAG, "✅ Command 소켓 연결 성공")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Command 소켓 연결 실패: ${e.message}")
                        retryCount++
                        continue
                    }

                    val connectionNumber = performCommandInitialization(commandSocket)
                    if (connectionNumber == -1) {
                        Log.e(TAG, "❌ Command 초기화 실패")
                        retryCount++
                        continue
                    }
                    Log.d(TAG, "✅ Command 초기화 성공, Connection Number: $connectionNumber")

                    // 1-2: Event 소켓 연결 및 초기화
                    Log.d(TAG, "Step 1-2: Event 소켓 연결 시도")
                    eventSocket = Socket()

                    try {
                        eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)
                        Log.d(TAG, "✅ Event 소켓 연결 성공")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Event 소켓 연결 실패: ${e.message}")
                        retryCount++
                        continue
                    }

                    if (!performEventInitialization(eventSocket, connectionNumber)) {
                        Log.e(TAG, "❌ Event 초기화 실패")
                        retryCount++
                        continue
                    }
                    Log.d(TAG, "✅ Event 초기화 성공")

                    // 1-3: GetDeviceInfo
                    Log.d(TAG, "Step 1-3: GetDeviceInfo 전송")
                    if (!sendGetDeviceInfo(commandSocket)) {
                        Log.w(TAG, "⚠️ GetDeviceInfo 실패하지만 계속 진행")
                    } else {
                        Log.d(TAG, "✅ GetDeviceInfo 성공")
                    }

                    // 1-4: OpenSession
                    Log.d(TAG, "Step 1-4: OpenSession 전송")
                    if (!sendOpenSession(commandSocket)) {
                        Log.w(TAG, "⚠️ OpenSession 실패하지만 계속 진행")
                    } else {
                        Log.d(TAG, "✅ OpenSession 성공")
                    }

                    // 1-5: 니콘 전용 0x952b 명령
                    Log.d(TAG, "Step 1-5: 니콘 0x952b 명령 전송")
                    if (!sendNikon952bCommand(commandSocket)) {
                        Log.w(TAG, "⚠️ 0x952b 실패하지만 계속 진행")
                    } else {
                        Log.d(TAG, "✅ 0x952b 성공")
                    }

                    // 1-6: 연결 승인 요청 0x935a
                    Log.d(TAG, "Step 1-6: 연결 승인 요청 0x935a 전송")
                    if (!sendNikon935aCommand(commandSocket)) {
                        Log.w(TAG, "⚠️ 0x935a 실패하지만 계속 진행")
                    } else {
                        Log.d(TAG, "✅ 0x935a 성공")
                    }

                    Log.i(TAG, "✅ Phase 1 완료")
                    return@withContext true

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Phase 1 중 오류: ${e.message}")
                    Log.e(TAG, "오류 상세: ${e.stackTraceToString()}")
                    retryCount++
                } finally {
                    // 연결 해제
                    try {
                        Log.d(TAG, "Phase 1 연결 해제 시작")
                        commandSocket?.close()
                        eventSocket?.close()
                        Log.d(TAG, "Phase 1 연결 해제 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "Phase 1 연결 해제 중 오류: ${e.message}")
                    }
                }
            }

            Log.e(TAG, "Phase 1 인증 실패 (최대 재시도 횟수 초과)")
            return@withContext false
        }

    /**
     * Phase 2: 인증된 메인 연결
     */
    private suspend fun performPhase2Authentication(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            var commandSocket: Socket? = null
            var eventSocket: Socket? = null

            try {
                Log.i(TAG, "Phase 2: 인증된 메인 연결 시작")

                // 2-1: Command 소켓 연결 및 초기화
                commandSocket = Socket()
                commandSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                val connectionNumber = performCommandInitialization(commandSocket)
                if (connectionNumber == -1) return@withContext false

                // 2-2: Event 소켓 연결 및 초기화
                eventSocket = Socket()
                eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                if (!performEventInitialization(eventSocket, connectionNumber)) {
                    return@withContext false
                }

                // 2-3: 인증된 상태 GetDeviceInfo
                if (!sendGetDeviceInfo(commandSocket)) {
                    Log.w(TAG, "인증된 GetDeviceInfo 실패하지만 계속 진행")
                }

                // 2-4: 인증된 OpenSession
                if (!sendOpenSession(commandSocket)) {
                    Log.w(TAG, "인증된 OpenSession 실패하지만 계속 진행")
                }

                // 2-5: 저장소 정보 확인 (인증된 상태에서만 가능)
                if (!sendGetStorageIDs(commandSocket)) {
                    Log.w(TAG, "GetStorageIDs 실패하지만 계속 진행")
                }

                Log.i(TAG, "✅ Phase 2 완료 - 인증된 연결 유지")
                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "Phase 2 중 오류: ${e.message}")
                return@withContext false
            } finally {
                // Phase 2에서는 연결을 유지하므로 해제하지 않음
            }
        }

    /**
     * 니콘 0x952b 명령 전송
     */
    private fun sendNikon952bCommand(socket: Socket): Boolean {
        return try {
            Log.d(TAG, "니콘 0x952b 명령 전송")

            val output = socket.getOutputStream()
            val packet = createNikon952bPacket()

            output.write(packet)
            output.flush()

            // 응답 수신
            socket.soTimeout = 10000
            val response = ByteArray(4096)
            val bytesRead = socket.getInputStream().read(response)

            if (bytesRead > 0) {
                Log.d(TAG, "0x952b 응답 수신: $bytesRead bytes")
                true
            } else {
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "0x952b 전송 실패: ${e.message}")
            false
        }
    }

    /**
     * 니콘 0x935a 연결 승인 요청
     */
    private fun sendNikon935aCommand(socket: Socket): Boolean {
        return try {
            Log.d(TAG, "니콘 0x935a 연결 승인 요청")

            val output = socket.getOutputStream()
            val packet = createNikon935aPacket()

            output.write(packet)
            output.flush()

            // 응답 수신
            socket.soTimeout = 10000
            val response = ByteArray(1024)
            val bytesRead = socket.getInputStream().read(response)

            if (bytesRead > 0) {
                Log.d(TAG, "0x935a 응답 수신: $bytesRead bytes")

                // 성공 응답 확인
                if (bytesRead >= 14) {
                    val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(4)
                    val packetType = buffer.int

                    if (packetType == PtpipConstants.PTPIP_OPERATION_RESPONSE) {
                        val responseCode = buffer.short.toInt() and 0xFFFF
                        val transactionId = buffer.int

                        if (responseCode == 0x2001 && transactionId == 2) {
                            Log.i(TAG, "✅ 0x935a 성공 응답 확인")
                            return true
                        }
                    }
                }
            }

            false

        } catch (e: Exception) {
            Log.e(TAG, "0x935a 전송 실패: ${e.message}")
            false
        }
    }

    /**
     * 니콘 0x952b 패킷 생성
     */
    private fun createNikon952bPacket(): ByteArray {
        val packetLength = 18
        val buffer = ByteBuffer.allocate(packetLength).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(packetLength)
        buffer.putInt(PtpipConstants.PTPIP_OPERATION_REQUEST)
        buffer.putInt(1) // data_phase
        buffer.putShort(NIKON_952B_COMMAND.toShort())
        buffer.putInt(1) // Transaction ID

        return buffer.array()
    }

    /**
     * 니콘 0x935a 패킷 생성
     */
    private fun createNikon935aPacket(): ByteArray {
        val packetLength = 22
        val buffer = ByteBuffer.allocate(packetLength).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(packetLength)
        buffer.putInt(PtpipConstants.PTPIP_OPERATION_REQUEST)
        buffer.putInt(1) // data_phase
        buffer.putShort(NIKON_935A_COMMAND.toShort())
        buffer.putInt(2) // Transaction ID
        buffer.putInt(0x2001) // 매개변수

        return buffer.array()
    }

    /**
     * Command 초기화 (간단한 버전)
     */
    private fun performCommandInitialization(socket: Socket): Int {
        return try {
            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            // Init Command 패킷 전송
            val initPacket = createInitCommandRequest()
            output.write(initPacket)
            output.flush()

            // ACK 응답 수신
            socket.soTimeout = 5000
            val response = ByteArray(1024)
            val bytesRead = input.read(response)

            if (bytesRead >= 12) {
                val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
                buffer.position(4)
                val responseType = buffer.int

                if (responseType == PtpipConstants.PTPIP_INIT_COMMAND_ACK) {
                    val connectionNumber = buffer.int
                    Log.d(TAG, "Command 초기화 성공, Connection Number: $connectionNumber")
                    return connectionNumber
                }
            }

            -1
        } catch (e: Exception) {
            Log.e(TAG, "Command 초기화 실패: ${e.message}")
            -1
        }
    }

    /**
     * Event 초기화 (간단한 버전)
     */
    private fun performEventInitialization(socket: Socket, connectionNumber: Int): Boolean {
        return try {
            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            // Init Event 패킷 전송
            val initPacket = createInitEventRequest(connectionNumber)
            output.write(initPacket)
            output.flush()

            // ACK 응답 수신
            socket.soTimeout = 3000
            val response = ByteArray(1024)
            val bytesRead = input.read(response)

            if (bytesRead >= 8) {
                val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
                buffer.position(4)
                val responseType = buffer.int

                if (responseType == PtpipConstants.PTPIP_INIT_EVENT_ACK) {
                    Log.d(TAG, "Event 초기화 성공")
                    return true
                }
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Event 초기화 실패: ${e.message}")
            false
        }
    }

    /**
     * GetDeviceInfo 전송
     */
    private fun sendGetDeviceInfo(socket: Socket): Boolean {
        return try {
            val output = socket.getOutputStream()
            val packet = createOperationRequest(PtpipConstants.PTP_OC_GetDeviceInfo, 0)

            output.write(packet)
            output.flush()

            // 응답 수신
            socket.soTimeout = 5000
            val response = ByteArray(4096)
            val bytesRead = socket.getInputStream().read(response)

            bytesRead > 0
        } catch (e: Exception) {
            Log.e(TAG, "GetDeviceInfo 실패: ${e.message}")
            false
        }
    }

    /**
     * OpenSession 전송
     */
    private fun sendOpenSession(socket: Socket): Boolean {
        return try {
            val output = socket.getOutputStream()
            val sessionId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            val packet = createOperationRequest(PtpipConstants.PTP_OC_OpenSession, 0, sessionId)

            output.write(packet)
            output.flush()

            // 응답 수신
            socket.soTimeout = 5000
            val response = ByteArray(1024)
            val bytesRead = socket.getInputStream().read(response)

            bytesRead > 0
        } catch (e: Exception) {
            Log.e(TAG, "OpenSession 실패: ${e.message}")
            false
        }
    }

    /**
     * GetStorageIDs 전송
     */
    private fun sendGetStorageIDs(socket: Socket): Boolean {
        return try {
            val output = socket.getOutputStream()
            val packet = createOperationRequest(PtpipConstants.PTP_OC_GetStorageIDs, 1)

            output.write(packet)
            output.flush()

            // 응답 수신
            socket.soTimeout = 5000
            val response = ByteArray(4096)
            val bytesRead = socket.getInputStream().read(response)

            bytesRead > 0
        } catch (e: Exception) {
            Log.e(TAG, "GetStorageIDs 실패: ${e.message}")
            false
        }
    }

    /**
     * 기본 패킷 생성 함수들
     */
    private fun createInitCommandRequest(): ByteArray {
        val commandGuid = byteArrayOf(
            0xd5.toByte(), 0xb4.toByte(), 0x6b.toByte(), 0xcb.toByte(),
            0xd6.toByte(), 0x2a.toByte(), 0x4d.toByte(), 0xbb.toByte(),
            0xb0.toByte(), 0x97.toByte(), 0x87.toByte(), 0x20.toByte(),
            0xcf.toByte(), 0x83.toByte(), 0xe0.toByte(), 0x84.toByte()
        )

        val hostNameBytes = byteArrayOf(
            0x41, 0x00, 0x6e, 0x00, 0x64, 0x00, 0x72, 0x00,
            0x6f, 0x00, 0x69, 0x00, 0x64, 0x00, 0x20, 0x00,
            0x44, 0x00, 0x65, 0x00, 0x76, 0x00, 0x69, 0x00,
            0x63, 0x00, 0x65, 0x00, 0x00, 0x00
        )

        val totalLength = 4 + 4 + 16 + hostNameBytes.size + 4
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(totalLength)
        buffer.putInt(PtpipConstants.PTPIP_INIT_COMMAND_REQUEST)
        buffer.put(commandGuid)
        buffer.put(hostNameBytes)
        buffer.putInt(0x00010001)

        return buffer.array()
    }

    private fun createInitEventRequest(connectionNumber: Int): ByteArray {
        val totalLength = 12
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(totalLength)
        buffer.putInt(PtpipConstants.PTPIP_INIT_EVENT_REQUEST)
        buffer.putInt(connectionNumber)

        return buffer.array()
    }

    private fun createOperationRequest(
        operation: Int,
        transactionId: Int,
        vararg parameters: Int
    ): ByteArray {
        val paramSize = parameters.size * 4
        val totalSize = 18 + paramSize

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(totalSize)
        buffer.putInt(PtpipConstants.PTPIP_OPERATION_REQUEST)
        buffer.putInt(1)
        buffer.putShort(operation.toShort())
        buffer.putInt(transactionId)

        parameters.forEach { buffer.putInt(it) }

        return buffer.array()
    }

    /**
     * Phase 1 인증 테스트 (별도 메서드)
     */
    suspend fun testPhase1Authentication(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "=== Phase 1 인증 테스트 시작 ===")
            return@withContext performPhase1Authentication(camera)
        }

    /**
     * Phase 2 인증 테스트 (별도 메서드)
     */
    suspend fun testPhase2Authentication(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "=== Phase 2 인증 테스트 시작 ===")
            return@withContext performPhase2Authentication(camera)
        }

    /**
     * 소켓 연결 테스트
     */
    suspend fun testSocketConnection(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            return@withContext try {
                Log.i(TAG, "소켓 연결 테스트: ${camera.ipAddress}:${camera.port}")

                socket = Socket()
                socket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                Log.i(TAG, "✅ 소켓 연결 성공")
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ 소켓 연결 실패: ${e.message}")
                false
            } finally {
                socket?.close()
            }
        }

    /**
     * 니콘 0x952b 명령 테스트
     */
    suspend fun testNikon952bCommand(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            var commandSocket: Socket? = null
            var eventSocket: Socket? = null

            return@withContext try {
                Log.i(TAG, "니콘 0x952b 명령 테스트 시작")

                // 기본 연결 설정
                commandSocket = Socket()
                commandSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                val connectionNumber = performCommandInitialization(commandSocket)
                if (connectionNumber == -1) {
                    Log.e(TAG, "Command 초기화 실패")
                    return@withContext false
                }

                eventSocket = Socket()
                eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                if (!performEventInitialization(eventSocket, connectionNumber)) {
                    Log.e(TAG, "Event 초기화 실패")
                    return@withContext false
                }

                // 0x952b 명령 테스트
                val success = sendNikon952bCommand(commandSocket)
                Log.i(TAG, "0x952b 명령 결과: ${if (success) "성공" else "실패"}")
                success

            } catch (e: Exception) {
                Log.e(TAG, "0x952b 명령 테스트 중 오류: ${e.message}")
                false
            } finally {
                commandSocket?.close()
                eventSocket?.close()
            }
        }

    /**
     * 니콘 0x935a 명령 테스트
     */
    suspend fun testNikon935aCommand(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            var commandSocket: Socket? = null
            var eventSocket: Socket? = null

            return@withContext try {
                Log.i(TAG, "니콘 0x935a 명령 테스트 시작")

                // 기본 연결 설정
                commandSocket = Socket()
                commandSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                val connectionNumber = performCommandInitialization(commandSocket)
                if (connectionNumber == -1) {
                    Log.e(TAG, "Command 초기화 실패")
                    return@withContext false
                }

                eventSocket = Socket()
                eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                if (!performEventInitialization(eventSocket, connectionNumber)) {
                    Log.e(TAG, "Event 초기화 실패")
                    return@withContext false
                }

                // 0x935a 명령 테스트
                val success = sendNikon935aCommand(commandSocket)
                Log.i(TAG, "0x935a 명령 결과: ${if (success) "성공" else "실패"}")
                success

            } catch (e: Exception) {
                Log.e(TAG, "0x935a 명령 테스트 중 오류: ${e.message}")
                false
            } finally {
                commandSocket?.close()
                eventSocket?.close()
            }
        }

    /**
     * GetDeviceInfo 명령 테스트
     */
    suspend fun testGetDeviceInfo(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            var commandSocket: Socket? = null
            var eventSocket: Socket? = null

            return@withContext try {
                Log.i(TAG, "GetDeviceInfo 명령 테스트 시작")

                // 기본 연결 설정
                commandSocket = Socket()
                commandSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                val connectionNumber = performCommandInitialization(commandSocket)
                if (connectionNumber == -1) {
                    Log.e(TAG, "Command 초기화 실패")
                    return@withContext false
                }

                eventSocket = Socket()
                eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                if (!performEventInitialization(eventSocket, connectionNumber)) {
                    Log.e(TAG, "Event 초기화 실패")
                    return@withContext false
                }

                // GetDeviceInfo 명령 테스트
                val success = sendGetDeviceInfo(commandSocket)
                Log.i(TAG, "GetDeviceInfo 명령 결과: ${if (success) "성공" else "실패"}")
                success

            } catch (e: Exception) {
                Log.e(TAG, "GetDeviceInfo 명령 테스트 중 오류: ${e.message}")
                false
            } finally {
                commandSocket?.close()
                eventSocket?.close()
            }
        }

    /**
     * OpenSession 명령 테스트
     */
    suspend fun testOpenSession(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            var commandSocket: Socket? = null
            var eventSocket: Socket? = null

            return@withContext try {
                Log.i(TAG, "OpenSession 명령 테스트 시작")

                // 기본 연결 설정
                commandSocket = Socket()
                commandSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                val connectionNumber = performCommandInitialization(commandSocket)
                if (connectionNumber == -1) {
                    Log.e(TAG, "Command 초기화 실패")
                    return@withContext false
                }

                eventSocket = Socket()
                eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                if (!performEventInitialization(eventSocket, connectionNumber)) {
                    Log.e(TAG, "Event 초기화 실패")
                    return@withContext false
                }

                // OpenSession 명령 테스트
                val success = sendOpenSession(commandSocket)
                Log.i(TAG, "OpenSession 명령 결과: ${if (success) "성공" else "실패"}")
                success

            } catch (e: Exception) {
                Log.e(TAG, "OpenSession 명령 테스트 중 오류: ${e.message}")
                false
            } finally {
                commandSocket?.close()
                eventSocket?.close()
            }
        }

    /**
     * 포트 스캔 테스트
     */
    suspend fun scanPorts(ipAddress: String): List<Int> =
        withContext(Dispatchers.IO) {
            val commonPorts = listOf(15740, 80, 443, 8080, 8443, 5000, 5001, 8000, 8001, 9000)
            val openPorts = mutableListOf<Int>()

            Log.i(TAG, "포트 스캔 시작: $ipAddress")

            for (port in commonPorts) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ipAddress, port), 1000)
                    socket.close()
                    openPorts.add(port)
                    Log.d(TAG, "포트 $port: 열림")
                } catch (e: Exception) {
                    Log.d(TAG, "포트 $port: 닫힘 (${e.message})")
                }
            }

            Log.i(TAG, "포트 스캔 완료. 열린 포트: ${openPorts.joinToString(", ")}")
            return@withContext openPorts
        }
}