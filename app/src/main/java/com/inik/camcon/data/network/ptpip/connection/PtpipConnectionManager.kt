package com.inik.camcon.data.network.ptpip.connection

import android.util.Log
import com.inik.camcon.data.constants.PtpipConstants
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PTPIP 연결 관리자
 * 소켓 연결, 패킷 송수신, 세션 관리 담당
 */
@Singleton
class PtpipConnectionManager @Inject constructor() {
    private var commandSocket: Socket? = null
    private var eventSocket: Socket? = null
    private var sessionId: Int = 0
    private var transactionId: Int = 0

    companion object {
        private const val TAG = "PtpipConnectionManager"
    }

    /**
     * 기본 PTPIP 연결 설정
     */
    suspend fun establishConnection(camera: PtpipCamera): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "PTPIP 연결 시작: ${camera.ipAddress}:${camera.port}")

            // 포트 연결 확인
            if (!isPortReachable(camera.ipAddress, camera.port)) {
                return@withContext false
            }

            // Command 소켓 연결 및 초기화
            commandSocket = Socket()
            commandSocket?.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

            val connectionNumber = performCommandInitialization()
            if (connectionNumber == -1) return@withContext false

            kotlinx.coroutines.delay(200)

            // Event 소켓 연결 및 초기화
            eventSocket = Socket()
            eventSocket?.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

            if (!performEventInitialization(connectionNumber)) {
                return@withContext false
            }

            Log.d(TAG, "✅ PTPIP 연결 성공")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "PTPIP 연결 실패: ${e.message}")
            closeConnections()
            return@withContext false
        }
    }

    /**
     * GetDeviceInfo 요청
     */
    suspend fun getDeviceInfo(): PtpipCameraInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "GetDeviceInfo 요청")

            val socket = commandSocket ?: run {
                Log.e(TAG, "❌ GetDeviceInfo 실패: Command 소켓이 null")
                return@withContext null
            }

            val output = socket.getOutputStream()

            // GetDeviceInfo 패킷 생성
            val packet = createOperationRequest(PtpipConstants.PTP_OC_GetDeviceInfo)
            output.write(packet)
            output.flush()
            Log.d(TAG, "GetDeviceInfo 패킷 전송 완료")

            // 응답 처리
            val responses = readCompleteResponse(socket)
            Log.d(TAG, "GetDeviceInfo 응답 수신: ${responses.size}개 패킷")

            if (responses.isNotEmpty()) {
                val deviceDataPacket = responses.find { it.size > 50 }
                if (deviceDataPacket != null) {
                    Log.d(TAG, "디바이스 데이터 패킷 크기: ${deviceDataPacket.size} bytes")
                    val deviceInfo = parseDeviceInfo(deviceDataPacket)
                    Log.i(TAG, "✅ 파싱된 카메라 정보: ${deviceInfo.manufacturer}")
                    return@withContext deviceInfo
                } else {
                    Log.e(TAG, "❌ GetDeviceInfo 실패: 유효한 디바이스 데이터 패킷 없음")
                }
            } else {
                Log.e(TAG, "❌ GetDeviceInfo 실패: 응답 패킷 없음")
            }

            return@withContext null

        } catch (e: Exception) {
            Log.e(TAG, "❌ GetDeviceInfo 실패: ${e.message}")
            return@withContext null
        }
    }

    /**
     * OpenSession 요청
     */
    suspend fun openSession(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "OpenSession 요청")

            val socket = commandSocket ?: return@withContext false
            val output = socket.getOutputStream()

            sessionId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            val packet = createOperationRequest(PtpipConstants.PTP_OC_OpenSession, sessionId)

            output.write(packet)
            output.flush()

            // 응답 대기
            socket.soTimeout = 5000
            val response = ByteArray(1024)
            val bytesRead = socket.getInputStream().read(response)

            if (bytesRead >= 8) {
                val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
                buffer.position(4)
                val responseType = buffer.int

                if (responseType == PtpipConstants.PTPIP_OPERATION_RESPONSE) {
                    Log.d(TAG, "✅ OpenSession 성공")
                    return@withContext true
                }
            }

            return@withContext false

        } catch (e: Exception) {
            Log.e(TAG, "OpenSession 실패: ${e.message}")
            return@withContext false
        }
    }

    /**
     * CloseSession 요청
     */
    suspend fun closeSession(forceClose: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        // libgphoto2 호환성을 위해 forceClose가 true일 때만 실제로 세션 닫기
        if (!forceClose) {
            Log.d(TAG, "libgphoto2 호환성을 위해 세션 유지 (닫지 않음)")
            return@withContext true
        }

        try {
            Log.d(TAG, "CloseSession 요청")

            val socket = commandSocket ?: return@withContext false
            val output = socket.getOutputStream()

            val packet = createOperationRequest(PtpipConstants.PTP_OC_CloseSession)
            output.write(packet)
            output.flush()

            // 응답 대기 (타임아웃 짧게 설정)
            socket.soTimeout = 2000
            val response = ByteArray(1024)
            val bytesRead = try {
                socket.getInputStream().read(response)
            } catch (e: Exception) {
                Log.d(TAG, "CloseSession 응답 대기 중 타임아웃: ${e.message}")
                -1
            }

            if (bytesRead > 0) {
                Log.d(TAG, "CloseSession 응답 수신: $bytesRead bytes")
            }

            Log.d(TAG, "✅ CloseSession 완료")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "CloseSession 실패: ${e.message}")
            return@withContext false
        }
    }

    /**
     * 연결 종료
     */
    suspend fun closeConnections(closeSession: Boolean = true) = withContext(Dispatchers.IO) {
        try {
            // 세션 닫기 (필요한 경우에만)
            if (closeSession && commandSocket?.isConnected == true) {
                closeSession(forceClose = true)
            }

            commandSocket?.close()
            eventSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "연결 종료 중 오류: ${e.message}")
        } finally {
            commandSocket = null
            eventSocket = null
            sessionId = 0
            transactionId = 0
        }
    }

    /**
     * 연결 상태 확인
     */
    fun isConnected(): Boolean {
        return commandSocket?.isConnected == true && eventSocket?.isConnected == true
    }

    /**
     * 포트 연결 가능성 확인
     */
    private suspend fun isPortReachable(ipAddress: String, port: Int): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                Log.d(TAG, "포트 연결 확인: $ipAddress:$port")
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, port), 2000)
                socket.close()
                true
            } catch (e: Exception) {
                Log.w(TAG, "포트 연결 실패: $ipAddress:$port - ${e.message}")
                false
            }
        }

    /**
     * Command 채널 초기화
     */
    private fun performCommandInitialization(): Int {
        return try {
            val socket = commandSocket ?: return -1
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

            if (bytesRead >= 8) {
                val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
                buffer.position(4)
                val responseType = buffer.int

                if (responseType == PtpipConstants.PTPIP_INIT_COMMAND_ACK) {
                    // Connection Number 추출
                    buffer.position(8)
                    val connectionNumber = buffer.int
                    Log.d(TAG, "✅ Command 초기화 성공, Connection Number: $connectionNumber")
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
     * Event 채널 초기화
     */
    private fun performEventInitialization(connectionNumber: Int): Boolean {
        return try {
            val socket = eventSocket ?: return false
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
                    Log.d(TAG, "✅ Event 초기화 성공")
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
     * Init Command Request 패킷 생성
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
            0x63, 0x00, 0x65, 0x00
        )
        val nullTerminator = byteArrayOf(0x00, 0x00)

        val totalLength = 4 + 4 + 16 + hostNameBytes.size + nullTerminator.size + 4
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(totalLength)
        buffer.putInt(PtpipConstants.PTPIP_INIT_COMMAND_REQUEST)
        buffer.put(commandGuid)
        buffer.put(hostNameBytes)
        buffer.put(nullTerminator)
        buffer.putInt(0x00010001)

        return buffer.array()
    }

    /**
     * Init Event Request 패킷 생성
     */
    private fun createInitEventRequest(connectionNumber: Int): ByteArray {
        val totalLength = 12
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(totalLength)
        buffer.putInt(PtpipConstants.PTPIP_INIT_EVENT_REQUEST)
        buffer.putInt(connectionNumber)

        return buffer.array()
    }

    /**
     * Operation Request 패킷 생성
     */
    private fun createOperationRequest(operation: Int, vararg parameters: Int): ByteArray {
        val currentTransactionId =
            if (operation == PtpipConstants.PTP_OC_OpenSession) 0 else transactionId++
        val paramSize = parameters.size * 4
        val totalSize = 18 + paramSize

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(totalSize)
        buffer.putInt(PtpipConstants.PTPIP_OPERATION_REQUEST)
        buffer.putInt(1)
        buffer.putShort(operation.toShort())
        buffer.putInt(currentTransactionId)

        parameters.forEach { buffer.putInt(it) }

        return buffer.array()
    }

    /**
     * 완전한 응답 패킷 읽기
     */
    private fun readCompleteResponse(socket: Socket): List<ByteArray> {
        try {
            val input = socket.getInputStream()
            val responses = mutableListOf<ByteArray>()
            var operationResponseReceived = false

            socket.soTimeout = 2000

            while (!operationResponseReceived) {
                val response = ByteArray(4096)
                val bytesRead = try {
                    input.read(response)
                } catch (e: java.net.SocketTimeoutException) {
                    break
                }

                if (bytesRead <= 0) break

                val actualResponse = response.copyOf(bytesRead)
                responses.add(actualResponse)

                // 패킷 타입 확인
                if (bytesRead >= 8) {
                    val buffer = ByteBuffer.wrap(actualResponse).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(4)
                    val packetType = buffer.int

                    if (packetType == PtpipConstants.PTPIP_OPERATION_RESPONSE) {
                        operationResponseReceived = true
                    }
                }
            }

            return responses
        } catch (e: Exception) {
            Log.e(TAG, "응답 읽기 실패: ${e.message}")
            return emptyList()
        }
    }

    /**
     * 디바이스 정보 파싱 (개선된 버전)
     */
    private fun parseDeviceInfo(data: ByteArray): PtpipCameraInfo {
        return try {
            Log.d(TAG, "디바이스 정보 파싱 시작: ${data.size} bytes")
            
            // 전체 바이너리 데이터 분석
            val hexDump = data.take(200).chunked(16).mapIndexed { index, bytes ->
                val offset = "%04X".format(index * 16)
                val hex = bytes.joinToString(" ") { "%02X".format(it) }
                "$offset: $hex"
            }.joinToString("\n")
            Log.d(TAG, "바이너리 덤프:\n$hexDump")

            // PTPIP 헤더 정보 확인
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val packetLength = buffer.int
            val packetType = buffer.int
            Log.d(TAG, "패킷 길이: $packetLength, 타입: $packetType")

            // 문자열 패턴 직접 검색
            val deviceInfo = extractDeviceInfoFromStrings(data)
            
            Log.d(TAG, "추출된 디바이스 정보: $deviceInfo")
            return deviceInfo

        } catch (e: Exception) {
            Log.e(TAG, "디바이스 정보 파싱 실패: ${e.message}")
            return createFallbackDeviceInfo()
        }
    }

    /**
     * 문자열 패턴에서 디바이스 정보 추출
     */
    private fun extractDeviceInfoFromStrings(data: ByteArray): PtpipCameraInfo {
        val deviceStrings = mutableListOf<String>()
        
        // PTP 문자열 구조 검색 (길이 바이트 + UTF-16LE 문자열)
        for (i in 0 until data.size - 10) {
            val lengthByte = data[i].toInt() and 0xFF
            
            if (lengthByte in 1..100) {
                val stringStartIndex = i + 1
                val stringByteLength = lengthByte * 2
                
                if (stringStartIndex + stringByteLength <= data.size) {
                    try {
                        val stringBytes = data.sliceArray(stringStartIndex until stringStartIndex + stringByteLength)
                        val utf16String = String(stringBytes, Charsets.UTF_16LE)
                            .replace("\u0000", "")
                            .trim()
                        
                        // 유효한 문자열인지 확인 (ASCII 문자만 허용)
                        if (utf16String.isNotEmpty() && 
                            utf16String.length <= 50 && 
                            utf16String.all { it.code in 32..126 }) {
                            
                            deviceStrings.add(utf16String)
                            Log.d(TAG, "발견된 문자열 at $i: '$utf16String'")
                        }
                    } catch (e: Exception) {
                        // 무시하고 계속
                    }
                }
            }
        }

        Log.d(TAG, "=== 전체 발견된 문자열 목록 (${deviceStrings.size}개) ===")
        deviceStrings.forEachIndexed { idx, str ->
            Log.d(TAG, "[$idx] '$str'")
        }
        Log.d(TAG, "=== 문자열 목록 끝 ===")

        // 제조사 찾기
        val manufacturer = deviceStrings.find { str ->
            listOf("Nikon", "Canon", "Sony", "Fujifilm", "Olympus", "Panasonic", "Leica")
                .any { brand -> str.contains(brand, ignoreCase = true) }
        }?.let { foundStr ->
            when {
                foundStr.contains("Nikon", ignoreCase = true) -> "Nikon"
                foundStr.contains("Canon", ignoreCase = true) -> "Canon"
                foundStr.contains("Sony", ignoreCase = true) -> "Sony"
                foundStr.contains("Fujifilm", ignoreCase = true) -> "Fujifilm"
                foundStr.contains("Olympus", ignoreCase = true) -> "Olympus"
                foundStr.contains("Panasonic", ignoreCase = true) -> "Panasonic"
                foundStr.contains("Leica", ignoreCase = true) -> "Leica"
                else -> "Unknown"
            }
        } ?: "Unknown"
        
        // 모델 찾기 (우선순위: 단독 모델명 > 제조사 포함 문자열)
        val model = run {
            // 1. 단독 모델명 패턴 찾기 (Z 8, D850, EOS R5 등)
            val standaloneModel = deviceStrings.find { str ->
                str.matches(Regex("^[A-Z]\\d+[A-Z]?\\d*$")) || // D850, R5, Z8
                str.matches(Regex("^[A-Z] \\d+[A-Z]?\\d*$")) || // Z 8, R 5
                str.matches(Regex("^[A-Z]{2,} [A-Z]?\\d+[A-Z]?$")) // EOS R5
            }
            
            if (standaloneModel != null) {
                Log.d(TAG, "단독 모델명 발견: '$standaloneModel'")
                return@run standaloneModel
            }
            
            // 2. 제조사 이름이 포함된 문자열에서 추출
            val manufacturerIncluded = deviceStrings.find { str ->
                str.contains(manufacturer, ignoreCase = true) && 
                str.length > manufacturer.length + 1 &&
                str != "$manufacturer Corporation" // "Nikon Corporation" 제외
            }?.let { foundStr ->
                // 제조사 이름 제거하고 정리
                foundStr.replace(manufacturer, "", ignoreCase = true)
                    .replace("Corporation", "", ignoreCase = true)
                    .trim()
            }
            
            if (!manufacturerIncluded.isNullOrEmpty()) {
                Log.d(TAG, "제조사 포함 문자열에서 모델명 추출: '$manufacturerIncluded'")
                return@run manufacturerIncluded
            }

            Log.d(TAG, "모델명을 찾을 수 없음")
            return@run "Unknown"
        }
        
        // 버전 찾기 (V로 시작하는 버전 패턴)
        val version = deviceStrings.find { str ->
            str.matches(Regex("^V\\d+\\.\\d+.*"))
        } ?: "Unknown"
        
        // 시리얼 번호 찾기 (숫자로만 구성된 긴 문자열, 단 모든 0으로 시작하는 것은 제외)
        val serialNumber = deviceStrings.find { str ->
            str.matches(Regex("^\\d{10,}$")) && 
            !str.matches(Regex("^0+\\d{1,4}$")) // 00000...3869 같은 패턴 제외
        } ?: "Unknown"
        
        Log.d(TAG, "최종 파싱 결과 - 제조사: '$manufacturer', 모델: '$model', 버전: '$version', 시리얼: '$serialNumber'")
        
        return PtpipCameraInfo(
            manufacturer = manufacturer,
            model = model,
            version = version,
            serialNumber = serialNumber
        )
    }

    /**
     * 폴백 디바이스 정보 생성
     */
    private fun createFallbackDeviceInfo(): PtpipCameraInfo {
        return PtpipCameraInfo(
            manufacturer = "Unknown",
            model = "Unknown",
            version = "Unknown",
            serialNumber = "Unknown"
        )
    }

    /**
     * PTP 문자열 읽기 (수정된 버전)
     */
    private fun readPtpString(buffer: ByteBuffer): String {
        return try {
            if (buffer.remaining() < 1) {
                Log.d(TAG, "PTP 문자열 읽기 실패: 버퍼 부족")
                return ""
            }

            val length = buffer.get().toInt() and 0xFF
            Log.d(TAG, "PTP 문자열 길이: $length")

            if (length == 0) {
                Log.d(TAG, "PTP 문자열 길이 0")
                return ""
            }

            // 문자열 길이 유효성 검사
            if (length > 255) {
                Log.w(TAG, "PTP 문자열 길이 초과: $length")
                return ""
            }

            if (buffer.remaining() < length * 2) {
                Log.d(TAG, "PTP 문자열 읽기 실패: 버퍼 부족 (필요: ${length * 2}, 남은: ${buffer.remaining()})")
                return ""
            }

            val stringBytes = ByteArray(length * 2)
            buffer.get(stringBytes)

            // 원본 바이트 데이터 로그 출력 (디버깅용)
            val hexString = stringBytes.take(20).joinToString(" ") { "%02x".format(it) }
            Log.d(TAG, "PTP 문자열 원본 바이트: $hexString")

            // UTF-16LE 디코딩 시도
            val utf16Result = try {
                String(stringBytes, Charsets.UTF_16LE)
                    .replace("\u0000", "")
                    .trim()
            } catch (e: Exception) {
                Log.d(TAG, "UTF-16LE 디코딩 실패: ${e.message}")
                ""
            }

            // ASCII 디코딩 시도 (홀수 바이트 건너뛰기)
            val asciiResult = try {
                stringBytes
                    .filterIndexed { index, byte -> index % 2 == 0 && byte != 0.toByte() }
                    .filter { it in 32..126 } // 출력 가능한 ASCII 범위
                    .map { it.toInt().toChar() }
                    .joinToString("")
                    .trim()
            } catch (e: Exception) {
                Log.d(TAG, "ASCII 디코딩 실패: ${e.message}")
                ""
            }

            // 가장 적절한 결과 선택
            val finalResult = when {
                // 카메라 제조사 이름이 포함된 경우
                listOf("Nikon", "Canon", "Sony", "Fujifilm", "Olympus", "Panasonic", "Leica")
                    .any { brand -> utf16Result.contains(brand, ignoreCase = true) } -> utf16Result

                listOf("Nikon", "Canon", "Sony", "Fujifilm", "Olympus", "Panasonic", "Leica")
                    .any { brand -> asciiResult.contains(brand, ignoreCase = true) } -> asciiResult

                // ASCII 결과가 더 유효해 보이는 경우
                asciiResult.isNotEmpty() && asciiResult.length < 50
                        && asciiResult.all { it.isLetterOrDigit() || it.isWhitespace() || it in ".-_()" } -> asciiResult

                // UTF-16 결과가 더 유효해 보이는 경우
                utf16Result.isNotEmpty() && utf16Result.length < 50
                        && utf16Result.count { it.isLetterOrDigit() } > utf16Result.length / 2 -> utf16Result

                // 그 외의 경우 더 짧은 결과
                else -> if (asciiResult.length > 0 && asciiResult.length < utf16Result.length) asciiResult else utf16Result
            }

            Log.d(TAG, "PTP 문자열 파싱 결과: '$finalResult'")
            return finalResult.take(100) // 최대 100자 제한

        } catch (e: Exception) {
            Log.d(TAG, "PTP 문자열 읽기 예외: ${e.message}")
            return ""
        }
    }

    /**
     * PTP 배열 건너뛰기 (반환값 추가)
     */
    private fun skipPtpArray(buffer: ByteBuffer, arrayName: String): Boolean {
        return try {
            if (buffer.remaining() < 4) {
                Log.w(TAG, "$arrayName 건너뛰기 실패: 버퍼 부족")
                return false
            }

            val arrayLength = buffer.int
            Log.d(TAG, "$arrayName 배열 길이: $arrayLength")

            // 배열 길이 유효성 검사
            if (arrayLength < 0 || arrayLength > 10000) {
                Log.w(TAG, "$arrayName 건너뛰기 실패: 유효하지 않은 배열 길이 ($arrayLength)")
                return false
            }

            // 빈 배열인 경우
            if (arrayLength == 0) {
                Log.d(TAG, "$arrayName 빈 배열 - 건너뛰기 완료")
                return true
            }

            // 배열 요소는 2바이트씩 (PTP 표준)
            val skipBytes = arrayLength * 2
            Log.d(TAG, "$arrayName 건너뛸 바이트: $skipBytes")

            if (buffer.remaining() >= skipBytes) {
                buffer.position(buffer.position() + skipBytes)
                Log.d(TAG, "$arrayName 건너뛰기 완료")
                return true
            } else {
                Log.w(TAG, "$arrayName 건너뛰기 실패: 버퍼 부족 (필요: $skipBytes, 남은: ${buffer.remaining()})")
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "$arrayName 건너뛰기 예외: ${e.message}")
            return false
        }
    }
}