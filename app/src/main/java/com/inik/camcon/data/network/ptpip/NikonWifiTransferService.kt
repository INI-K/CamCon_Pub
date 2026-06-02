package com.inik.camcon.data.network.ptpip

import android.content.Context
import com.inik.camcon.data.network.ptpip.authentication.NikonAuthenticationService
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.utils.LogcatManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 니콘 Wi-Fi "전송목록(Transfer List)" 다운로드 경로 (순수 Kotlin, libgphoto2/JNI 비경유).
 *
 * 표준 libgphoto2 경로(ObjectAdded→GetObjectInfo)는 STA 모드에서 카메라 AccessLock 때문에
 * GetObjectInfo가 PTP_RC_AccessDenied(0x200F)로 거부된다. 정상 경로는 니콘 전송 메커니즘:
 * 카메라에서 "전송 표시"된 이미지를 GetTransferList(0x9408)로 조회 → NotifyFileAcquisitionStart
 * / GetObject / NotifyFileAcquisitionEnd로 받으면 0x200F 없이 원본 풀해상도가 온다.
 *
 * 와이어 포맷은 airnef(mtpwifi.py/mtpdef.py/airnefcmd.py)에서 확정. PTP/IP over TCP(15740).
 * 모든 정수는 LITTLE_ENDIAN. 각 프레임 = [outerLen:u32 = payloadLen+4][payload].
 *
 * ⚠️ 카메라는 PTP/IP 세션을 1개만 허용하므로, 이 서비스를 호출하기 전에 기존 libgphoto2 연결을
 * 반드시 닫아야 한다(호출부 책임).
 */
@Singleton
class NikonWifiTransferService @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        // TAG에 "PTPIP"를 포함시켜 LogcatManager의 PTPIP 로그 게이트를 통과시킨다.
        private const val TAG = "NikonWifiXfer-PTPIP"

        // ── 프레임 payload ID (airnef mtpwifi.py) ──
        private const val PAYLOAD_ID_CMD_REQ = 0x06
        private const val PAYLOAD_ID_CMD_RESPONSE = 0x07
        private const val PAYLOAD_ID_DATA_START = 0x09
        private const val PAYLOAD_ID_DATA_PAYLOAD = 0x0a
        private const val PAYLOAD_ID_DATA_PAYLOAD_LAST = 0x0c

        // ── CmdReq 데이터 방향 코드 ──
        private const val DATA_DIR_CAMERA_TO_HOST_OR_NONE = 0x1
        private const val DATA_DIR_HOST_TO_CAMERA = 0x2

        // ── TCP/IP 핸드셰이크 요청 타입 ──
        private const val TCPIP_REQ_INIT_CMD_REQ = 0x01
        private const val TCPIP_REQ_INIT_EVENTS = 0x03
        private const val TCPIP_REQ_PROBE = 0x0d

        // ── MTP opcode (mtpdef.py) ──
        private const val OP_GetDeviceInfo = 0x1001
        private const val OP_OpenSession = 0x1002
        private const val OP_CloseSession = 0x1003
        private const val OP_GetStorageIDs = 0x1004
        private const val OP_GetObjectHandles = 0x1007
        private const val OP_GetObjectInfo = 0x1008
        private const val OP_GetObject = 0x1009
        private const val OP_GetTransferList = 0x9408
        private const val OP_NotifyFileAcquisitionStart = 0x9409
        private const val OP_NotifyFileAcquisitionEnd = 0x940a

        // ── 응답 코드 ──
        private const val RC_OK = 0x2001
        private const val RC_AccessDenied = 0x200F

        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 30000
        private const val MAX_TEST_DOWNLOADS = 5
    }

    /** 다운로드 결과 1건. */
    data class DownloadedItem(
        val handle: Int,
        val fileName: String,
        val filePath: String,
        val sizeBytes: Int
    )

    /** execMtpOp 결과: 응답코드 + 누적 수신 데이터(payload 헤더 제외). */
    private data class MtpResult(val respCode: Int, val data: ByteArray)

    /** 송신 트랜잭션 ID 카운터(1부터 증가). */
    private var txCounter = 0

    /**
     * 전송목록 다운로드 전체 시퀀스.
     *
     * 1) 단독 PTP/IP 세션 부트스트랩(InitCmdReq + InitEvents + Probe + OpenSession)
     * 2) GetTransferList(0x9408)로 전송 표시된 핸들 조회
     *    - 비어있거나 실패하면 대조 증거용으로 GetStorageIDs→GetObjectHandles→GetObjectInfo 시도(→ 0x200F 예상)
     * 3) 각 핸들: GetObjectInfo → NotifyFileAcquisitionStart → GetObject → NotifyFileAcquisitionEnd
     * 4) 받은 원본 바이트를 앱 파일 디렉터리에 저장
     * 5) CloseSession + 소켓 정리
     */
    suspend fun downloadTransferList(camera: PtpipCamera): Result<List<DownloadedItem>> =
        withContext(ioDispatcher) {
            var cmdSocket: Socket? = null
            var eventSocket: Socket? = null
            txCounter = 0
            try {
                LogcatManager.i(TAG, "=== 전송목록 다운로드 시작: ${camera.ipAddress}:${camera.port} ===")

                cmdSocket = Socket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(camera.ipAddress, camera.port), CONNECT_TIMEOUT_MS)
                    soTimeout = READ_TIMEOUT_MS
                }
                LogcatManager.i(TAG, "Command 소켓 연결 성공")

                // InitCommandRequest → 카메라가 세션 ID 반환
                var sessionId = sendInitCommandRequest(cmdSocket)
                    ?: return@withContext Result.failure(IllegalStateException("InitCommandRequest 실패(세션 ID 미수신)"))
                LogcatManager.i(TAG, "세션 ID 수신: 0x%08x".format(sessionId))

                // Event 소켓 + InitEvents + Probe.
                // airnef 주석: probe 생략 시 MTP 세션이 hang 하므로 1차부터 포함한다.
                eventSocket = Socket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(camera.ipAddress, camera.port), CONNECT_TIMEOUT_MS)
                    soTimeout = READ_TIMEOUT_MS
                }
                if (sendInitEvents(eventSocket, sessionId)) {
                    LogcatManager.i(TAG, "Event 소켓 InitEvents ACK")
                    if (sendProbeRequest(eventSocket)) {
                        LogcatManager.i(TAG, "Probe ACK")
                    } else {
                        LogcatManager.w(TAG, "Probe 응답 비정상 — 계속 진행")
                    }
                } else {
                    LogcatManager.w(TAG, "InitEvents ACK 실패 — 계속 진행")
                }

                // GetDeviceInfo (일부 모델에서 후속 op 안정화)
                execMtpOp(cmdSocket, OP_GetDeviceInfo, dataDir = DATA_DIR_CAMERA_TO_HOST_OR_NONE)

                // OpenSession. 실패하면 sessionId=0x1로 재시도(airnef와 동일).
                var open = execMtpOp(
                    cmdSocket, OP_OpenSession,
                    intArrayOf(sessionId), DATA_DIR_CAMERA_TO_HOST_OR_NONE
                )
                if (open.respCode != RC_OK && sessionId != 0x1) {
                    LogcatManager.w(TAG, "OpenSession 실패(0x%04x) — sessionId=0x1로 재시도".format(open.respCode))
                    sessionId = 0x1
                    open = execMtpOp(
                        cmdSocket, OP_OpenSession,
                        intArrayOf(sessionId), DATA_DIR_CAMERA_TO_HOST_OR_NONE
                    )
                }
                if (open.respCode != RC_OK) {
                    LogcatManager.e(TAG, "OpenSession 최종 실패: 0x%04x".format(open.respCode))
                    return@withContext Result.failure(IllegalStateException("OpenSession 실패: 0x%04x".format(open.respCode)))
                }

                // ── GetTransferList ──
                val transfer = execMtpOp(cmdSocket, OP_GetTransferList, dataDir = DATA_DIR_CAMERA_TO_HOST_OR_NONE)
                val handles: List<Int>
                if (transfer.respCode == RC_OK) {
                    handles = parseCountedWordList(transfer.data)
                    LogcatManager.i(TAG, "✅ 전송목록 수신: ${handles.size}개 핸들")
                } else {
                    LogcatManager.w(
                        TAG,
                        "전송목록 없음/실패(0x%04x). 대조용으로 GetStorageIDs→GetObjectHandles→GetObjectInfo 시도".format(transfer.respCode)
                    )
                    runAccessDeniedProbe(cmdSocket)
                    handles = emptyList()
                }

                if (handles.isEmpty()) {
                    LogcatManager.w(TAG, "다운로드할 전송목록 핸들이 없습니다. 카메라에서 사진을 '전송 표시' 했는지 확인하세요.")
                    closeSessionQuietly(cmdSocket)
                    return@withContext Result.success(emptyList())
                }

                if (handles.size > MAX_TEST_DOWNLOADS) {
                    LogcatManager.i(TAG, "핸들 ${handles.size}개 중 테스트로 처음 ${MAX_TEST_DOWNLOADS}개만 다운로드")
                }

                val results = mutableListOf<DownloadedItem>()
                for (handle in handles.take(MAX_TEST_DOWNLOADS)) {
                    val item = downloadOne(cmdSocket, handle)
                    if (item != null) results.add(item)
                }

                closeSessionQuietly(cmdSocket)
                LogcatManager.i(TAG, "=== 전송목록 다운로드 완료: ${results.size}건 ===")
                Result.success(results)
            } catch (e: Exception) {
                LogcatManager.e(TAG, "전송목록 다운로드 중 오류: ${e.message}")
                Result.failure(e)
            } finally {
                try { cmdSocket?.close() } catch (_: Exception) {}
                try { eventSocket?.close() } catch (_: Exception) {}
            }
        }

    /** 단일 핸들 다운로드: ObjectInfo → NotifyStart → GetObject → NotifyEnd → 파일 저장. */
    private fun downloadOne(sock: Socket, handle: Int): DownloadedItem? {
        // 파일명 조회 (실패해도 handle 기반 이름으로 진행)
        var fileName = "%08x.jpg".format(handle)
        val infoRes = execMtpOp(sock, OP_GetObjectInfo, intArrayOf(handle), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        if (infoRes.respCode == RC_OK) {
            parseObjectInfoFileName(infoRes.data)?.let { fileName = it }
        } else {
            LogcatManager.w(TAG, "GetObjectInfo 실패(0x%04x) handle=0x%08x — handle 기반 이름 사용".format(infoRes.respCode, handle))
        }

        // 전송 메커니즘 마킹: Start
        val start = execMtpOp(sock, OP_NotifyFileAcquisitionStart, intArrayOf(handle), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        if (start.respCode != RC_OK) {
            LogcatManager.w(TAG, "NotifyFileAcquisitionStart 비정상(0x%04x) handle=0x%08x — 계속 시도".format(start.respCode, handle))
        }

        // 원본 데이터 수신
        val obj = execMtpOp(sock, OP_GetObject, intArrayOf(handle), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        if (obj.respCode != RC_OK) {
            LogcatManager.e(TAG, "❌ GetObject 실패(0x%04x) handle=0x%08x file=$fileName".format(obj.respCode, handle))
            execMtpOp(sock, OP_NotifyFileAcquisitionEnd, intArrayOf(handle), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
            return null
        }

        // 전송 종료 통지
        execMtpOp(sock, OP_NotifyFileAcquisitionEnd, intArrayOf(handle), DATA_DIR_CAMERA_TO_HOST_OR_NONE)

        // 파일 저장
        val dir = (context.getExternalFilesDir(null) ?: context.cacheDir).resolve("nikon_transfer")
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, sanitizeFileName(fileName))
        return try {
            outFile.writeBytes(obj.data)
            LogcatManager.i(
                TAG,
                "✅ 저장 완료 handle=0x%08x file=%s size=%d path=%s".format(handle, fileName, obj.data.size, outFile.absolutePath)
            )
            DownloadedItem(handle, fileName, outFile.absolutePath, obj.data.size)
        } catch (e: Exception) {
            LogcatManager.e(TAG, "파일 저장 실패: ${e.message}")
            null
        }
    }

    /**
     * 전송목록이 없을 때 0x200F(AccessDenied) 대조 증거를 남기기 위한 표준 경로 프로브.
     * GetStorageIDs → GetObjectHandles → 첫 핸들 GetObjectInfo. 결과는 로그로만 남긴다.
     */
    private fun runAccessDeniedProbe(sock: Socket) {
        val storageRes = execMtpOp(sock, OP_GetStorageIDs, dataDir = DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        if (storageRes.respCode != RC_OK) {
            LogcatManager.w(TAG, "[대조] GetStorageIDs 실패: 0x%04x".format(storageRes.respCode))
            return
        }
        val storageIds = parseCountedWordList(storageRes.data)
        if (storageIds.isEmpty()) {
            LogcatManager.w(TAG, "[대조] 저장소 없음")
            return
        }
        val storageId = storageIds.first()
        val handlesRes = execMtpOp(
            sock, OP_GetObjectHandles,
            intArrayOf(storageId, 0, 0), DATA_DIR_CAMERA_TO_HOST_OR_NONE
        )
        if (handlesRes.respCode != RC_OK) {
            LogcatManager.w(TAG, "[대조] GetObjectHandles 실패: 0x%04x".format(handlesRes.respCode))
            return
        }
        val allHandles = parseCountedWordList(handlesRes.data)
        LogcatManager.i(TAG, "[대조] 전체 핸들 수: ${allHandles.size}")
        if (allHandles.isEmpty()) return
        val firstHandle = allHandles.first()
        val infoRes = execMtpOp(sock, OP_GetObjectInfo, intArrayOf(firstHandle), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        if (infoRes.respCode == RC_AccessDenied) {
            LogcatManager.w(
                TAG,
                "[대조] 예상대로 표준 GetObjectInfo가 0x200F(AccessDenied)로 거부됨 handle=0x%08x → 전송목록 경로 필요 확정".format(firstHandle)
            )
        } else {
            LogcatManager.i(TAG, "[대조] GetObjectInfo resp=0x%04x handle=0x%08x".format(infoRes.respCode, firstHandle))
        }
    }

    /**
     * MTP op 실행 헬퍼.
     * - CmdReq 프레임 송신.
     * - HOST_TO_CAMERA면 DataStart + DataPayloadLast 송신(이번 범위엔 사용 안 함).
     * - CmdResponse 받을 때까지 프레임 루프: DataStart→DataPayload(s) 누적→CmdResponse에서 respCode 추출.
     */
    private fun execMtpOp(
        sock: Socket,
        opcode: Int,
        params: IntArray = intArrayOf(),
        dataDir: Int,
        dataToSend: ByteArray = ByteArray(0)
    ): MtpResult {
        val txid = ++txCounter

        // CmdReq payload: [0x06][dataDir:u32][opcode:u16][txid:u32][params:u32...]
        val cmdReq = ByteBuffer.allocate(4 + 4 + 2 + 4 + params.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(PAYLOAD_ID_CMD_REQ)
            .putInt(dataDir)
            .putShort(opcode.toShort())
            .putInt(txid)
        params.forEach { cmdReq.putInt(it) }
        writeFrame(sock, cmdReq.array())

        // Host→Camera 데이터(이번 범위엔 호출 안 됨, 포맷 보존)
        if (dataDir == DATA_DIR_HOST_TO_CAMERA) {
            val dataStart = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(PAYLOAD_ID_DATA_START)
                .putInt(txid)
                .putInt(dataToSend.size)
                .putInt(0)
            writeFrame(sock, dataStart.array())
            val payload = ByteBuffer.allocate(8 + dataToSend.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(PAYLOAD_ID_DATA_PAYLOAD_LAST)
                .putInt(txid)
                .put(dataToSend)
            writeFrame(sock, payload.array())
        }

        val input = DataInputStream(sock.getInputStream())
        // ByteArrayOutputStream: 원본 풀해상도(수십 MB)도 박싱 없이 누적한다.
        val accumulated = ByteArrayOutputStream()
        var respCode = 0
        var totalDataLen = 0

        while (true) {
            val frame = readFrame(input)
            val payloadId = leInt(frame, 0)
            when (payloadId) {
                PAYLOAD_ID_DATA_START -> {
                    totalDataLen = leInt(frame, 8)
                }
                PAYLOAD_ID_DATA_PAYLOAD, PAYLOAD_ID_DATA_PAYLOAD_LAST -> {
                    // data는 payload[8:]
                    if (frame.size > 8) accumulated.write(frame, 8, frame.size - 8)
                }
                PAYLOAD_ID_CMD_RESPONSE -> {
                    respCode = leShort(frame, 4)
                    break
                }
                else -> {
                    LogcatManager.w(TAG, "알 수 없는 payloadId=0x%08x op=0x%04x — 무시".format(payloadId, opcode))
                }
            }
        }

        val data = accumulated.toByteArray()
        LogcatManager.i(
            TAG,
            "MTP op=0x%04x resp=0x%04x dataLen=%d (expected=%d)".format(opcode, respCode, data.size, totalDataLen)
        )
        if (respCode == RC_AccessDenied) {
            LogcatManager.w(TAG, "⚠️ op=0x%04x → 0x200F AccessDenied".format(opcode))
        }
        return MtpResult(respCode, data)
    }

    // ── 핸드셰이크 ──

    /** InitCommandRequest 송신 → 응답에서 세션 ID 추출(ACK=0x02 다음 4바이트). */
    private fun sendInitCommandRequest(sock: Socket): Int? {
        // GUID는 NikonAuthenticationService와 동일한 STA_PAIRING_GUID 사용(콜론 hex → 16바이트).
        val guid = NikonAuthenticationService.STA_PAIRING_GUID.split(":")
            .map { it.toInt(16).toByte() }
            .toByteArray()
        // hostname "Android Device" UTF-16LE + NULL(0x0000) 종단
        val hostName = "Android Device"
        val hostNameUtf16 = ByteBuffer.allocate((hostName.length + 1) * 2).order(ByteOrder.LITTLE_ENDIAN)
        hostName.forEach { hostNameUtf16.putChar(it) }
        hostNameUtf16.putChar('\u0000')

        val payload = ByteBuffer
            .allocate(4 + guid.size + hostNameUtf16.capacity() + 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(TCPIP_REQ_INIT_CMD_REQ)
            .put(guid)
            .put(hostNameUtf16.array())
            .putInt(0x00010001) // host protocol version
        writeFrame(sock, payload.array())

        val input = DataInputStream(sock.getInputStream())
        val resp = readFrame(input)
        val word = leInt(resp, 0)
        // ACK == 0x2, 이후 4바이트가 세션 ID
        return if (word == 0x2 && resp.size >= 8) {
            leInt(resp, 4)
        } else {
            LogcatManager.e(TAG, "InitCommandRequest 비정상 응답: word=0x%08x len=%d".format(word, resp.size))
            null
        }
    }

    /** InitEvents 송신(이벤트 소켓). ACK == 0x4. */
    private fun sendInitEvents(sock: Socket, sessionId: Int): Boolean {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(TCPIP_REQ_INIT_EVENTS)
            .putInt(sessionId)
        writeFrame(sock, payload.array())
        val resp = readFrame(DataInputStream(sock.getInputStream()))
        return leInt(resp, 0) == 0x4
    }

    /** Probe 송신(이벤트 소켓). Probe 응답 == 0xe. */
    private fun sendProbeRequest(sock: Socket): Boolean {
        val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(TCPIP_REQ_PROBE)
        writeFrame(sock, payload.array())
        val resp = readFrame(DataInputStream(sock.getInputStream()))
        return leInt(resp, 0) == 0xe
    }

    private fun closeSessionQuietly(sock: Socket) {
        try {
            execMtpOp(sock, OP_CloseSession, dataDir = DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        } catch (e: Exception) {
            LogcatManager.w(TAG, "CloseSession 중 오류(무시): ${e.message}")
        }
    }

    // ── 프레임 입출력 ──

    /** payload 앞에 [outerLen = payloadLen+4]를 붙여 송신. */
    private fun writeFrame(sock: Socket, payload: ByteArray) {
        val frame = ByteBuffer.allocate(4 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(payload.size + 4)
            .put(payload)
        val out = sock.getOutputStream()
        out.write(frame.array())
        out.flush()
    }

    /** 한 프레임 수신: outerLen 읽고 payload를 정확히 채운다(부분수신 루프). 반환은 payload. */
    private fun readFrame(input: DataInputStream): ByteArray {
        val lenBuf = ByteArray(4)
        input.readFully(lenBuf)
        val outerLen = leInt(lenBuf, 0)
        val payloadLen = outerLen - 4
        if (payloadLen < 0) throw IllegalStateException("잘못된 프레임 길이: $outerLen")
        val payload = ByteArray(payloadLen)
        if (payloadLen > 0) input.readFully(payload)
        return payload
    }

    // ── 파싱 ──

    /** [count:u32][handle:u32 × count] → List<Int>. */
    private fun parseCountedWordList(data: ByteArray): List<Int> {
        if (data.size < 4) return emptyList()
        val count = leInt(data, 0)
        val list = ArrayList<Int>(count.coerceAtMost((data.size - 4) / 4))
        var offset = 4
        var i = 0
        while (i < count && offset + 4 <= data.size) {
            list.add(leInt(data, offset))
            offset += 4
            i++
        }
        return list
    }

    /**
     * ObjectInfo에서 파일명만 추출.
     * 고정 헤더 52바이트 이후: [charLenIncludingNull:u8][UTF-16LE chars...].
     * (airnefcmd.py parseMtpObjectInfo / mtpCountedUtf16ToPythonUnicodeStr 참고)
     */
    private fun parseObjectInfoFileName(data: ByteArray): String? {
        val offset = 52
        if (data.size <= offset) return null
        val charLen = data[offset].toInt() and 0xFF
        if (charLen == 0) return null
        val byteLen = charLen * 2
        val start = offset + 1
        if (start + byteLen > data.size) return null
        return try {
            // 마지막 NULL 종단 2바이트 제외
            String(data, start, byteLen - 2, Charsets.UTF_16LE).trimEnd('\u0000', ' ')
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
        return cleaned.ifBlank { "unnamed.bin" }
    }

    // ── LE 헬퍼 ──

    private fun leInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun leShort(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
}
