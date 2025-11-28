package com.inik.camcon.data.network.ptpip.authentication

import android.util.Log
import com.inik.camcon.data.constants.PtpipConstants
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.utils.LogcatManager
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
        private const val NIKON_935B_COMMAND = 0x935b  // Pairing code 전송
        private const val MAX_RETRIES = 3
    }

    /**
     * 니콘 STA 모드 전체 인증 시퀀스
     */
    suspend fun performStaAuthentication(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            try {
                LogcatManager.i(TAG, "니콘 STA 모드 인증 시퀀스 시작")

                // Phase 1: 승인 요청 임시 연결
                if (!performPhase1Authentication(camera)) {
                    LogcatManager.e(TAG, "Phase 1 인증 실패")
                    return@withContext false
                }

                // Phase 1 완료 후 libgphoto2 연결 준비 대기
                LogcatManager.i(TAG, "libgphoto2 연결 준비 대기 중... (5초)")
                delay(5000)  // 카메라가 준비될 시간 필요

                LogcatManager.i(TAG, "✅ 니콘 STA 모드 인증 완료!")
                LogcatManager.i(TAG, "→ 카메라가 인증된 상태로 libgphoto2 연결 대기 중")
                return@withContext true

            } catch (e: Exception) {
                LogcatManager.e(TAG, "니콘 STA 모드 인증 중 오류: ${e.message}")
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
                    LogcatManager.i(TAG, "Phase 1: 승인 요청 임시 연결 시작")
                    LogcatManager.d(TAG, "연결 대상: ${camera.ipAddress}:${camera.port}")

                    // 연결 전 대기 시간 추가 (기존 연결과의 충돌 방지)
                    LogcatManager.d(TAG, "기존 연결 해제 대기 중... (2초)")
                    delay(2000)

                    // 1-1: Command 소켓 연결 및 초기화
                    LogcatManager.d(TAG, "Step 1-1: Command 소켓 연결 시도")
                    commandSocket = Socket()

                    try {
                        commandSocket.connect(
                            InetSocketAddress(camera.ipAddress, camera.port),
                            5000
                        )
                        LogcatManager.d(TAG, "✅ Command 소켓 연결 성공")
                    } catch (e: Exception) {
                        LogcatManager.e(TAG, "❌ Command 소켓 연결 실패: ${e.message}")
                        retryCount++
                        continue
                    }

                    val connectionNumber = performCommandInitialization(commandSocket)
                    if (connectionNumber == -1) {
                        LogcatManager.e(TAG, "❌ Command 초기화 실패")
                        retryCount++
                        continue
                    }
                    LogcatManager.d(TAG, "✅ Command 초기화 성공, Connection Number: $connectionNumber")

                    // 1-2: Event 소켓 연결 및 초기화
                    LogcatManager.d(TAG, "Step 1-2: Event 소켓 연결 시도")
                    eventSocket = Socket()

                    try {
                        eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)
                        LogcatManager.d(TAG, "✅ Event 소켓 연결 성공")
                    } catch (e: Exception) {
                        LogcatManager.e(TAG, "❌ Event 소켓 연결 실패: ${e.message}")
                        retryCount++
                        continue
                    }

                    if (!performEventInitialization(eventSocket, connectionNumber)) {
                        LogcatManager.e(TAG, "❌ Event 초기화 실패")
                        retryCount++
                        continue
                    }
                    LogcatManager.d(TAG, "✅ Event 초기화 성공")

                    // 1-3: GetDeviceInfo
                    LogcatManager.d(TAG, "Step 1-3: GetDeviceInfo 전송")
                    if (!sendGetDeviceInfo(commandSocket)) {
                        LogcatManager.w(TAG, "⚠️ GetDeviceInfo 실패지만 계속 진행")
                    } else {
                        LogcatManager.d(TAG, "✅ GetDeviceInfo 성공")
                    }

                    // 1-4: OpenSession
                    LogcatManager.d(TAG, "Step 1-4: OpenSession 전송")
                    if (!sendOpenSession(commandSocket)) {
                        LogcatManager.w(TAG, "⚠️ OpenSession 실패지만 계속 진행")
                    } else {
                        LogcatManager.d(TAG, "✅ OpenSession 성공")
                    }

                    // 1-5: 니콘 전용 0x952b 명령
                    LogcatManager.d(TAG, "Step 1-5: 니콘 0x952b 명령 전송")
                    if (!sendNikon952bCommand(commandSocket)) {
                        LogcatManager.w(TAG, "⚠️ 0x952b 실패지만 계속 진행")
                    } else {
                        LogcatManager.d(TAG, "✅ 0x952b 성공")
                    }

                    // 1-6: 연결 승인 요청 0x935a
                    LogcatManager.d(TAG, "Step 1-6: 연결 승인 요청 0x935a 전송")
                    val (success, needsPairingCode) = sendNikon935aCommand(commandSocket,
                        eventSocket)

                    if (success) {
                        LogcatManager.i(TAG, "✅ 0x935a 자동 승인 성공")
                    } else if (needsPairingCode) {
                        LogcatManager.w(TAG, "⚠️ Pairing Code 필요")

                        // Pairing code 자동 전송 시도
                        // 카메라 화면의 코드를 앱에서 입력하는 기능은 추후 구현
                        // 현재는 사용자가 카메라에서 OK를 누를 때까지 대기
                        LogcatManager.i(TAG, "⏳ 카메라 화면에서 OK 버튼을 눌러주세요 (30초)")
                        delay(30000)  // 30초 대기
                    } else {
                        LogcatManager.w(TAG, "⚠️ 0x935a 알 수 없는 응답 - 계속 진행")
                    }

                    LogcatManager.i(TAG, "✅ Phase 1 완료")

                    // 소켓을 닫지 않고 일정 시간 유지 (카메라가 세션을 열어둠)
                    LogcatManager.i(TAG, "⏳ 세션 유지 대기 중... (카메라 내부 처리)")
                    delay(2000) // 2초 대기

                    // 이제 소켓을 닫아도 카메라는 인증 상태를 유지
                    LogcatManager.d(TAG, "Phase 1 소켓 정리 시작 (인증 상태는 유지됨)")
                    try {
                        commandSocket?.close()
                        eventSocket?.close()
                        LogcatManager.d(TAG, "Phase 1 소켓 정리 완료")
                    } catch (e: Exception) {
                        LogcatManager.w(TAG, "소켓 정리 중 오류: ${e.message}")
                    }

                    return@withContext true

                } catch (e: Exception) {
                    LogcatManager.e(TAG, "❌ Phase 1 중 오류: ${e.message}")
                    LogcatManager.e(TAG, "오류 상세: ${e.stackTraceToString()}")
                    retryCount++

                    // 오류 시에만 즉시 소켓 정리
                    try {
                        commandSocket?.close()
                        eventSocket?.close()
                    } catch (e2: Exception) {
                        // 무시
                    }
                }
            }

            LogcatManager.e(TAG, "Phase 1 인증 실패 (최대 재시도 횟수 초과)")
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
                LogcatManager.i(TAG, "Phase 2: 인증된 메인 연결 시작")

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
                    LogcatManager.w(TAG, "인증된 GetDeviceInfo 실패지만 계속 진행")
                }

                // 2-4: 인증된 OpenSession
                if (!sendOpenSession(commandSocket)) {
                    LogcatManager.w(TAG, "인증된 OpenSession 실패지만 계속 진행")
                }

                // 2-5: GetVendorPropCodes 호출 (중요! Nikon Z6 촬영 기능 활성화)
                LogcatManager.i(TAG, "Step 2-5: GetVendorPropCodes (0x90CA) 호출하여 추가 기능 활성화")
                if (!sendGetVendorPropCodes(commandSocket)) {
                    LogcatManager.w(TAG, "⚠️ GetVendorPropCodes 실패 - 일부 기능이 제한될 수 있음")
                } else {
                    LogcatManager.i(TAG, "✅ GetVendorPropCodes 성공 - 촬영 기능 활성화됨")
                }

                // 2-6: 업데이트된 DeviceInfo 다시 가져오기
                LogcatManager.i(TAG, "Step 2-6: 업데이트된 DeviceInfo 재조회")
                if (!sendGetDeviceInfo(commandSocket)) {
                    LogcatManager.w(TAG, "DeviceInfo 재조회 실패지만 계속 진행")
                } else {
                    LogcatManager.i(TAG, "✅ DeviceInfo 재조회 성공 - 업데이트된 capabilities 확인")
                }

                // 2-7: 저장소 정보 확인 (인증된 상태에서만 가능)
                if (!sendGetStorageIDs(commandSocket)) {
                    LogcatManager.w(TAG, "GetStorageIDs 실패지만 계속 진행")
                }

                LogcatManager.i(TAG, "✅ Phase 2 완료 - 인증된 연결 유지")
                return@withContext true

            } catch (e: Exception) {
                LogcatManager.e(TAG, "Phase 2 중 오류: ${e.message}")
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
            LogcatManager.d(TAG, "니콘 0x952b 명령 전송")

            val output = socket.getOutputStream()
            val packet = createNikon952bPacket()

            output.write(packet)
            output.flush()

            // 응답 수신
            socket.soTimeout = 10000
            val response = ByteArray(4096)
            val bytesRead = socket.getInputStream().read(response)

            if (bytesRead > 0) {
                LogcatManager.d(TAG, "0x952b 응답 수신: $bytesRead bytes")

                // 응답 내용 로깅 (디버깅용)
                if (bytesRead > 14) {
                    val hexDump = response.take(bytesRead).joinToString(" ") {
                        "%02x".format(it)
                    }
                    LogcatManager.d(TAG, "0x952b 응답 내용: $hexDump")

                    // Pairing code 추출 시도
                    val pairingCode = extractPairingCode(response, bytesRead)
                    if (pairingCode != null) {
                        LogcatManager.i(TAG, "⚠️ 0x952b에서 Pairing Code 감지: $pairingCode")
                    }
                }

                true
            } else {
                false
            }

        } catch (e: Exception) {
            LogcatManager.e(TAG, "0x952b 전송 실패: ${e.message}")
            false
        }
    }

    /**
     * 니콘 0x935a 연결 승인 요청
     * @return Pair<Boolean, Boolean> - (성공 여부, pairing code 필요 여부)
     */
    private fun sendNikon935aCommand(
        commandSocket: Socket,
        eventSocket: Socket?
    ): Pair<Boolean, Boolean> {
        return try {
            LogcatManager.d(TAG, "니콘 0x935a 연결 승인 요청")

            val output = commandSocket.getOutputStream()
            val packet = createNikon935aPacket()

            output.write(packet)
            output.flush()

            // Command 소켓 응답 수신 (여러 번 read 가능)
            commandSocket.soTimeout = 5000
            val response = ByteArray(1024)
            var totalBytesRead = 0

            // 첫 번째 read
            var bytesRead = commandSocket.getInputStream().read(response)
            if (bytesRead > 0) {
                totalBytesRead = bytesRead
                LogcatManager.d(TAG, "0x935a Command 응답 수신 (1차): $bytesRead bytes")

                // OPERATION_RESPONSE가 없으면 추가로 read 시도
                val hasOperationResponse = checkForOperationResponse(response, bytesRead)
                if (!hasOperationResponse) {
                    LogcatManager.d(TAG, "OPERATION_RESPONSE 없음 - 추가 read 시도")
                    try {
                        commandSocket.soTimeout = 1000  // 1초만 대기
                        val additionalBytes = commandSocket.getInputStream()
                            .read(response, bytesRead, response.size - bytesRead)
                        if (additionalBytes > 0) {
                            totalBytesRead += additionalBytes
                            LogcatManager.d(
                                TAG,
                                "0x935a Command 응답 수신 (2차): $additionalBytes bytes"
                            )
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        LogcatManager.d(TAG, "추가 응답 없음 (정상)")
                    }
                }

                // 전체 응답 로깅
                val hexDump = response.take(totalBytesRead).joinToString(" ") {
                    "%02x".format(it)
                }
                LogcatManager.d(TAG, "0x935a 전체 응답 ($totalBytesRead bytes): $hexDump")

                // Pairing code 확인
                val pairingCode = extractPairingCode(response, totalBytesRead)
                if (pairingCode != null) {
                    LogcatManager.i(TAG, "⚠️ Pairing Code 감지: $pairingCode")
                    return Pair(false, true)
                }

                // 성공 응답 확인
                val responseCode = findResponseCode(response, totalBytesRead)
                if (responseCode == 0x2001) {
                    LogcatManager.i(TAG, "✅ 0x935a 자동 승인 성공")
                    return Pair(true, false)
                } else if (responseCode == 0x2019) {
                    LogcatManager.i(TAG, "⏳ 카메라가 pairing code 입력 대기 중")
                    return Pair(false, true)
                }
            }

            LogcatManager.w(TAG, "⚠️ 0x935a 응답 분석 실패 - pairing code 필요 가능성 있음")
            Pair(false, true)  // 실패 시 pairing code 필요하다고 가정

        } catch (e: Exception) {
            LogcatManager.e(TAG, "0x935a 전송 실패: ${e.message}")
            Pair(false, false)
        }
    }

    /**
     * 응답에 OPERATION_RESPONSE 패킷이 있는지 확인
     */
    private fun checkForOperationResponse(data: ByteArray, length: Int): Boolean {
        var offset = 0
        while (offset + 8 <= length) {
            val buffer = ByteBuffer.wrap(data, offset, length - offset)
                .order(ByteOrder.LITTLE_ENDIAN)
            val packetLength = buffer.int
            val packetType = buffer.int

            if (packetType == PtpipConstants.PTPIP_OPERATION_RESPONSE) {
                return true
            }

            offset += packetLength
        }
        return false
    }

    /**
     * 응답에서 ResponseCode 찾기
     */
    private fun findResponseCode(data: ByteArray, length: Int): Int {
        var offset = 0
        while (offset + 14 <= length) {
            val buffer = ByteBuffer.wrap(data, offset, length - offset)
                .order(ByteOrder.LITTLE_ENDIAN)
            val packetLength = buffer.int
            val packetType = buffer.int

            if (packetType == PtpipConstants.PTPIP_OPERATION_RESPONSE) {
                val responseCode = buffer.short.toInt() and 0xFFFF
                val transactionId = buffer.int
                LogcatManager.d(
                    TAG,
                    "응답 코드: 0x${"%04x".format(responseCode)}, Transaction ID: $transactionId"
                )
                return responseCode
            }

            offset += packetLength
        }
        return 0
    }

    /**
     * Command 소켓 응답에서 Pairing Code 추출 (다중 패킷 처리)
     */
    private fun extractPairingCode(data: ByteArray, length: Int): String? {
        return try {
            if (length < 12) return null

            var offset = 0
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // 여러 패킷을 순회하면서 pairing code 찾기
            while (offset + 8 <= length) {
                buffer.position(offset)
                val packetLength = buffer.int
                val packetType = buffer.int

                LogcatManager.d(
                    TAG,
                    "패킷 [$offset]: 길이=$packetLength, 타입=0x${"%08x".format(packetType)}"
                )

                // PTPIP_DATA (0x0c)에 pairing code가 들어있음
                if (packetType == 0x0000000c && packetLength >= 16) {
                    // 다음 4 bytes: Transaction ID (건너뜀)
                    buffer.int

                    // 다음 4 bytes: 데이터 길이
                    val dataLength = buffer.int
                    LogcatManager.d(TAG, "데이터 길이: $dataLength bytes")

                    if (dataLength >= 3 && buffer.position() + dataLength <= length) {
                        // Pairing code 읽기 (BCD 형식)
                        val pairingBytes = ByteArray(dataLength)
                        buffer.get(pairingBytes)

                        val hexDump = pairingBytes.joinToString(" ") { "%02x".format(it) }
                        LogcatManager.i(TAG, "Pairing code 바이트: $hexDump")

                        // 각 바이트가 1자리 숫자를 나타냄 (4자리 또는 6자리)
                        // 예: 00 09 08 06 → "0986" (4자리)
                        val code1 = pairingBytes.joinToString("") {
                            "%d".format(it.toInt() and 0xFF)
                        }

                        if ((code1.length == 4 || code1.length == 6) && code1.all { it.isDigit() }) {
                            LogcatManager.i(TAG, "✅ Pairing Code 추출 성공: $code1")
                            return code1
                        }

                        // 대안: 역순으로 시도
                        val code2 = pairingBytes.reversed().joinToString("") {
                            "%d".format(it.toInt() and 0xFF)
                        }

                        if ((code2.length == 4 || code2.length == 6) && code2.all { it.isDigit() }) {
                            LogcatManager.i(TAG, "✅ Pairing Code 추출 성공 (역순): $code2")
                            return code2
                        }

                        // 0을 제거한 버전도 시도
                        val code3 = pairingBytes.filter { it != 0.toByte() }
                            .joinToString("") { "%d".format(it.toInt() and 0xFF) }

                        if ((code3.length == 4 || code3.length == 6) && code3.all { it.isDigit() }) {
                            LogcatManager.i(TAG, "✅ Pairing Code 추출 성공 (0 제거): $code3")
                            return code3
                        }
                    }
                }

                // 다음 패킷으로 이동
                offset += packetLength
            }

            null
        } catch (e: Exception) {
            LogcatManager.e(TAG, "Pairing code 추출 실패: ${e.message}")
            LogcatManager.e(TAG, "스택 트레이스: ${e.stackTraceToString()}")
            null
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
                    LogcatManager.d(TAG, "Command 초기화 성공, Connection Number: $connectionNumber")
                    return connectionNumber
                }
            }

            -1
        } catch (e: Exception) {
            LogcatManager.e(TAG, "Command 초기화 실패: ${e.message}")
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
                    LogcatManager.d(TAG, "Event 초기화 성공")
                    return true
                }
            }

            false
        } catch (e: Exception) {
            LogcatManager.e(TAG, "Event 초기화 실패: ${e.message}")
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
            LogcatManager.e(TAG, "GetDeviceInfo 실패: ${e.message}")
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
            LogcatManager.e(TAG, "OpenSession 실패: ${e.message}")
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
            LogcatManager.e(TAG, "GetStorageIDs 실패: ${e.message}")
            false
        }
    }

    /**
     * GetVendorPropCodes (0x90CA) 전송
     * Nikon 카메라의 추가 기능 정보를 가져옴
     */
    private fun sendGetVendorPropCodes(socket: Socket): Boolean {
        return try {
            LogcatManager.d(TAG, "GetVendorPropCodes (0x90CA) 전송")

            val output = socket.getOutputStream()
            val packet = createOperationRequest(0x90CA, 3) // 0x90CA = GetVendorPropCodes

            output.write(packet)
            output.flush()

            // 응답 수신
            socket.soTimeout = 10000
            val response = ByteArray(4096)
            val bytesRead = socket.getInputStream().read(response)

            if (bytesRead > 0) {
                LogcatManager.d(TAG, "GetVendorPropCodes 응답 수신: $bytesRead bytes")

                // 응답 내용 분석 (추가 prop 코드가 포함됨)
                if (bytesRead >= 20) {
                    val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(4)
                    val packetType = buffer.int

                    if (packetType == PtpipConstants.PTPIP_START_DATA ||
                        packetType == PtpipConstants.PTPIP_DATA
                    ) {
                        LogcatManager.i(TAG, "✅ Vendor Prop Codes 데이터 수신 성공")
                        return true
                    }
                }

                true
            } else {
                false
            }

        } catch (e: Exception) {
            LogcatManager.e(TAG, "GetVendorPropCodes 전송 실패: ${e.message}")
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
            LogcatManager.i(TAG, "=== Phase 1 인증 테스트 시작 ===")
            return@withContext performPhase1Authentication(camera)
        }

    /**
     * Phase 2 인증 테스트 (별도 메서드)
     */
    suspend fun testPhase2Authentication(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            LogcatManager.i(TAG, "=== Phase 2 인증 테스트 시작 ===")
            return@withContext performPhase2Authentication(camera)
        }

    /**
     * 소켓 연결 테스트
     */
    suspend fun testSocketConnection(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            var socket: Socket? = null
            return@withContext try {
                LogcatManager.i(TAG, "소켓 연결 테스트: ${camera.ipAddress}:${camera.port}")

                socket = Socket()
                socket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                LogcatManager.i(TAG, "✅ 소켓 연결 성공")
                true
            } catch (e: Exception) {
                LogcatManager.e(TAG, "❌ 소켓 연결 실패: ${e.message}")
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
                LogcatManager.i(TAG, "니콘 0x952b 명령 테스트 시작")

                // 기본 연결 설정
                commandSocket = Socket()
                commandSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                val connectionNumber = performCommandInitialization(commandSocket)
                if (connectionNumber == -1) {
                    LogcatManager.e(TAG, "Command 초기화 실패")
                    return@withContext false
                }

                eventSocket = Socket()
                eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                if (!performEventInitialization(eventSocket, connectionNumber)) {
                    LogcatManager.e(TAG, "Event 초기화 실패")
                    return@withContext false
                }

                // 0x952b 명령 테스트
                val success = sendNikon952bCommand(commandSocket)
                LogcatManager.i(TAG, "0x952b 명령 결과: ${if (success) "성공" else "실패"}")
                success

            } catch (e: Exception) {
                LogcatManager.e(TAG, "0x952b 명령 테스트 중 오류: ${e.message}")
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
                LogcatManager.i(TAG, "니콘 0x935a 명령 테스트 시작")

                // 기본 연결 설정
                commandSocket = Socket()
                commandSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                val connectionNumber = performCommandInitialization(commandSocket)
                if (connectionNumber == -1) {
                    LogcatManager.e(TAG, "Command 초기화 실패")
                    return@withContext false
                }

                eventSocket = Socket()
                eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                if (!performEventInitialization(eventSocket, connectionNumber)) {
                    LogcatManager.e(TAG, "Event 초기화 실패")
                    return@withContext false
                }

                // 0x935a 명령 테스트
                val (success, needsPairingCode) = sendNikon935aCommand(commandSocket, eventSocket)
                LogcatManager.i(TAG, "0x935a 명령 결과: 성공=$success, pairing code 필요=$needsPairingCode")
                success

            } catch (e: Exception) {
                LogcatManager.e(TAG, "0x935a 명령 테스트 중 오류: ${e.message}")
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
                LogcatManager.i(TAG, "GetDeviceInfo 명령 테스트 시작")

                // 기본 연결 설정
                commandSocket = Socket()
                commandSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                val connectionNumber = performCommandInitialization(commandSocket)
                if (connectionNumber == -1) {
                    LogcatManager.e(TAG, "Command 초기화 실패")
                    return@withContext false
                }

                eventSocket = Socket()
                eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                if (!performEventInitialization(eventSocket, connectionNumber)) {
                    LogcatManager.e(TAG, "Event 초기화 실패")
                    return@withContext false
                }

                // GetDeviceInfo 명령 테스트
                val success = sendGetDeviceInfo(commandSocket)
                LogcatManager.i(TAG, "GetDeviceInfo 명령 결과: ${if (success) "성공" else "실패"}")
                success

            } catch (e: Exception) {
                LogcatManager.e(TAG, "GetDeviceInfo 명령 테스트 중 오류: ${e.message}")
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
                LogcatManager.i(TAG, "OpenSession 명령 테스트 시작")

                // 기본 연결 설정
                commandSocket = Socket()
                commandSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                val connectionNumber = performCommandInitialization(commandSocket)
                if (connectionNumber == -1) {
                    LogcatManager.e(TAG, "Command 초기화 실패")
                    return@withContext false
                }

                eventSocket = Socket()
                eventSocket.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                if (!performEventInitialization(eventSocket, connectionNumber)) {
                    LogcatManager.e(TAG, "Event 초기화 실패")
                    return@withContext false
                }

                // OpenSession 명령 테스트
                val success = sendOpenSession(commandSocket)
                LogcatManager.i(TAG, "OpenSession 명령 결과: ${if (success) "성공" else "실패"}")
                success

            } catch (e: Exception) {
                LogcatManager.e(TAG, "OpenSession 명령 테스트 중 오류: ${e.message}")
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

            LogcatManager.i(TAG, "포트 스캔 시작: $ipAddress")

            for (port in commonPorts) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ipAddress, port), 1000)
                    socket.close()
                    openPorts.add(port)
                    LogcatManager.d(TAG, "포트 $port: 열림")
                } catch (e: Exception) {
                    LogcatManager.d(TAG, "포트 $port: 닫힘 (${e.message})")
                }
            }

            LogcatManager.i(TAG, "포트 스캔 완료. 열린 포트: ${openPorts.joinToString(", ")}")
            return@withContext openPorts
        }
}