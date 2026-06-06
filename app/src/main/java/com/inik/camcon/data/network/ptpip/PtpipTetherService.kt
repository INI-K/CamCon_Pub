package com.inik.camcon.data.network.ptpip

import android.content.Context
import com.inik.camcon.data.network.ptpip.authentication.NikonAuthenticationService
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.utils.LogcatManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi(PTP/IP) **물리 셔터 리스너** — 카메라에서 직접 촬영한 새 컷만 풀해상도로 무선 수신한다.
 * 순수 Kotlin(libgphoto2/JNI 비경유). PTP/IP over TCP(15740), 모든 정수 LITTLE_ENDIAN,
 * 각 프레임 = [outerLen:u32 = payloadLen+4][payload].
 *
 * **벤더 중립 설계.** 부트스트랩/세션/프레임 I/O/핸들 폴링은 표준 PTP라 제조사 무관이다.
 * 제조사별로 갈리는 부분은 두 가지뿐이며 GetDeviceInfo의 VendorExtensionID로 판별해 분기한다:
 *   1) **세션 승인** — 니콘 STA(PC 연결) 모드만 vendor 핸드셰이크(0x952b→0x935a)가 필요하다.
 *      나머지 제조사는 표준 PTP라 승인 불필요(no-op).
 *   2) **객체 다운로드** —
 *      · 니콘 STA: 카드 객체가 액세스 잠금(0x200F)이라 표준 1008/1009/101b가 막힌다
 *        (libgphoto2 #976, uwes-ufo). 잠금 비대상 vendor op(0x9421 크기 + 0x9431 부분)로 우회.
 *      · 그 외(캐논/소니/후지/파나소닉): 표준 PTP(0x1008 정보·크기 + 0x101b 부분, 미지원 시 0x1009 전체).
 *
 * 새 컷 감지는 GetObjectHandles(0x1007, 잠금 비대상)를 주기 폴링해 diff 한다(제조사 무관).
 *
 * vendor op 파라미터는 libgphoto2 ptp.c, 와이어 프레임은 airnef(mtpwifi.py)에서 확정.
 *
 * ⚠️ 카메라는 PTP/IP 세션을 1개만 허용하므로, 이 서비스 호출 전에 기존 libgphoto2 연결을
 * 반드시 닫아야 한다(호출부 책임).
 */
@Singleton
class PtpipTetherService @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        // TAG에 "PTPIP"를 포함시켜 LogcatManager의 PTPIP 로그 게이트를 통과시킨다.
        private const val TAG = "PtpipTether-PTPIP"

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

        // ── 표준 MTP opcode (mtpdef.py) ──
        private const val OP_GetDeviceInfo = 0x1001
        private const val OP_OpenSession = 0x1002
        private const val OP_CloseSession = 0x1003
        private const val OP_GetStorageIDs = 0x1004
        private const val OP_GetObjectHandles = 0x1007
        private const val OP_GetObjectInfo = 0x1008
        private const val OP_GetObject = 0x1009            // 전체 객체(부분 미지원 카메라 폴백)
        private const val OP_GetPartialObject = 0x101b     // [handle, off:u32, max:u32] 부분(표준)

        // ── 니콘 STA 연결 승인 opcode (NikonAuthenticationService와 동일 와이어 포맷) ──
        private const val OP_Nikon952b = 0x952b           // 페어링/연결 사전 명령
        private const val OP_Nikon935a = 0x935a           // 연결 승인 요청
        private const val PARAM_935A_APPROVAL = 0x2001    // 0x935a 매개변수

        // ── 니콘 vendor 다운로드 opcode (인프라/STA 액세스 잠금 우회, libgphoto2 #976 uwes-ufo) ──
        // 표준 1008/1009/101b/100a는 STA 모드에서 0x200F(AccessDenied)로 잠기지만 아래 vendor op는 안 잠긴다.
        private const val OP_NikonGetObjectSize = 0x9421       // [handle] → u64 크기 (libgphoto2 ptp.c)
        private const val OP_NikonGetPartialObjectEx = 0x9431  // [handle, offLo, offHi, maxLo, maxHi] → 부분 데이터

        // ── WTU 정석 경로(실측 tt1cap.pcapng): AdvancedTransfer ──
        // WTU는 파일마다 0x9010(param=0x2001 고정)을 호출 → 응답으로 [handle + 실제경로 "DCIM/.../KY6_7585.JPG"]를
        // 받고 그 핸들의 카드 잠금이 풀려 표준 0x1008/0x101b로 다운로드. param 고정이고 호출마다 큐의 다음 항목 반환.
        private const val OP_NikonAdvancedTransfer = 0x9010
        private const val PARAM_ADVANCEDTRANSFER = 0x2001

        // ── 응답 코드 ──
        private const val RC_OK = 0x2001
        private const val RC_OperationNotSupported = 0x2005
        private const val RC_AccessDenied = 0x200F

        // ── PTP VendorExtensionID (libgphoto2 ptp.h PTP_VENDOR_*) ──
        private const val VENDOR_EXT_NIKON = 0x0000000A
        private const val VENDOR_EXT_CANON = 0x0000000B
        private const val VENDOR_EXT_FUJI = 0x0000000E
        private const val VENDOR_EXT_SONY = 0x00000011
        private const val VENDOR_EXT_PANASONIC = 0x0000001C

        // ObjectInfo 데이터셋 오프셋 (PTP 표준)
        private const val OBJINFO_OFF_FORMAT = 4          // ObjectFormat u16
        private const val OBJINFO_OFF_COMPRESSED_SIZE = 8 // ObjectCompressedSize u32
        private const val OBJFORMAT_ASSOCIATION = 0x3001  // 폴더/연관 객체

        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 30000

        // 부분 다운로드 청크 크기(1MB). airnef 실측: 니콘 펌웨어가 대용량 전송에서 erratic/타임아웃
        // (내부 리소스 누수) → 1MB로 받으면 성능 동일하며 안정적(airnefcmd.py downloadMtpFileObjects 주석).
        private const val CHUNK_BYTES = 1L * 1024 * 1024
    }

    /** 카메라 제조사(PTP VendorExtensionID 기반). */
    private enum class PtpVendor { NIKON, CANON, SONY, FUJI, PANASONIC, GENERIC }

    /** execMtpOp 결과: 응답코드 + 누적 수신 데이터(payload 헤더 제외). */
    private data class MtpResult(val respCode: Int, val data: ByteArray)

    /** 부트스트랩 산출: 제어/이벤트 소켓 + 판별된 제조사. */
    private data class Session(val cmd: Socket, val event: Socket, val vendor: PtpVendor)

    /** 송신 트랜잭션 ID 카운터(1부터 증가). */
    private var txCounter = 0

    /**
     * 단독 PTP/IP 세션 부트스트랩: 소켓 2개 연결 + InitCmdReq + InitEvents + Probe +
     * GetDeviceInfo(→제조사 판별) + OpenSession + (니콘이면)연결 승인. 성공 시 [Session] 반환.
     */
    private suspend fun bootstrapSession(camera: PtpipCamera): Session? {
        txCounter = 0
        val cmdSocket = Socket().apply {
            tcpNoDelay = true
            connect(InetSocketAddress(camera.ipAddress, camera.port), CONNECT_TIMEOUT_MS)
            soTimeout = READ_TIMEOUT_MS
        }
        LogcatManager.i(TAG, "Command 소켓 연결 성공")

        var sessionId = sendInitCommandRequest(cmdSocket)
        if (sessionId == null) {
            LogcatManager.e(TAG, "InitCommandRequest 실패(세션 ID 미수신)")
            try { cmdSocket.close() } catch (_: Exception) {}
            return null
        }
        LogcatManager.i(TAG, "세션 ID 수신: 0x%08x".format(sessionId))

        // Event 소켓 + InitEvents + Probe. airnef: probe 생략 시 MTP 세션이 hang.
        val eventSocket = Socket().apply {
            tcpNoDelay = true
            connect(InetSocketAddress(camera.ipAddress, camera.port), CONNECT_TIMEOUT_MS)
            soTimeout = READ_TIMEOUT_MS
        }
        if (sendInitEvents(eventSocket, sessionId)) {
            LogcatManager.i(TAG, "Event 소켓 InitEvents ACK")
            if (sendProbeRequest(eventSocket)) LogcatManager.i(TAG, "Probe ACK")
            else LogcatManager.w(TAG, "Probe 응답 비정상 — 계속 진행")
        } else {
            LogcatManager.w(TAG, "InitEvents ACK 실패 — 계속 진행")
        }

        // GetDeviceInfo로 제조사 판별(세션 전 허용 op). VendorExtensionID는 데이터셋 offset 2(u32).
        val deviceInfo = execMtpOp(cmdSocket, OP_GetDeviceInfo, dataDir = DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        val vendor = detectVendor(deviceInfo.data)
        LogcatManager.i(TAG, "제조사 판별: $vendor")

        var open = execMtpOp(cmdSocket, OP_OpenSession, intArrayOf(sessionId), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        if (open.respCode != RC_OK && sessionId != 0x1) {
            LogcatManager.w(TAG, "OpenSession 실패(0x%04x) — sessionId=0x1로 재시도".format(open.respCode))
            sessionId = 0x1
            open = execMtpOp(cmdSocket, OP_OpenSession, intArrayOf(sessionId), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        }
        if (open.respCode != RC_OK) {
            LogcatManager.e(TAG, "OpenSession 최종 실패: 0x%04x".format(open.respCode))
            try { cmdSocket.close() } catch (_: Exception) {}
            try { eventSocket.close() } catch (_: Exception) {}
            return null
        }

        // 제조사별 세션 승인(니콘만 vendor 핸드셰이크, 나머지 no-op).
        approveSession(cmdSocket, vendor)
        return Session(cmdSocket, eventSocket, vendor)
    }

    /**
     * 물리 셔터 리스너 (정식 경로): 단독 세션을 유지하며 GetObjectHandles(0x1007, 잠금 비대상)를
     * 주기적으로 diff 해 **새로 찍힌 핸들만** 제조사별 경로로 풀해상도 수신한다. 코루틴이 취소될
     * 때까지 무한 루프(수신 전용; 원격 촬영/라이브뷰는 동일 세션을 점유하는 libgphoto2와 공존
     * 불가하므로 이 모드에선 비활성).
     *
     * @param onShot 새 사진 1건 수신 시 (fileName, bytes) 콜백 — 호출부(PtpipDataSource)가
     *               PhotoDownloadManager/onPhotoDownloadedCallback 체인으로 흘려보내 기존
     *               capturedPhotos/미리보기 UI에 일관되게 등장시킨다.
     */
    suspend fun listenForNewShots(
        camera: PtpipCamera,
        pollIntervalMs: Long = 1500L,
        onShot: suspend (fileName: String, bytes: ByteArray) -> Unit
    ): Result<Unit> = withContext(ioDispatcher) {
        var session: Session? = null
        try {
            LogcatManager.i(TAG, "=== 물리 셔터 리스너 시작: ${camera.ipAddress}:${camera.port} ===")
            session = bootstrapSession(camera)
                ?: return@withContext Result.failure(IllegalStateException("세션 부트스트랩 실패"))
            val cmd = session.cmd
            val vendor = session.vendor

            // 니콘 STA는 승인 반영에 시간이 걸린다. 표준 제조사는 대기 불필요.
            if (vendor == PtpVendor.NIKON) delay(1500)

            // 니콘: WTU 정석 경로(0x9010 전송큐) 우선 시도 — 실제 파일명/경로를 직접 받고 잠금이 풀려
            // 표준 0x1008/0x101b로 받는다. 0x9010 미지원/실패면 vendor(0x9421/0x9431) 핸들 폴링으로 폴백.
            var wtuMode = false
            if (vendor == PtpVendor.NIKON) {
                val first = drainTransferQueue(cmd, onShot)
                if (first >= 0) {
                    wtuMode = true
                    LogcatManager.i(TAG, "WTU식 0x9010 전송큐 모드 (초기 ${first}건). 카메라 '자동 전송' 큐에서 실제 파일명으로 수신.")
                } else {
                    LogcatManager.w(TAG, "0x9010 미지원/실패 — vendor(0x9421/0x9431) 핸들 폴링으로 폴백")
                }
            }

            if (wtuMode) {
                while (isActive) {
                    delay(pollIntervalMs)
                    if (!isActive) break
                    drainTransferQueue(cmd, onShot)
                }
            } else {
                // 폴링 모드: GetObjectHandles(0x1007, 잠금 비대상) diff → 새 핸들만 제조사별 다운로드.
                val seen = HashSet(fetchAllCardHandles(cmd))
                LogcatManager.i(TAG, "폴링 리스닝 — 기준 핸들 ${seen.size}개. 이제 촬영하면 그 컷만 받습니다.")
                while (isActive) {
                    delay(pollIntervalMs)
                    if (!isActive) break
                    val current = fetchAllCardHandles(cmd)
                    val fresh = current.filter { it !in seen }
                    if (fresh.isEmpty()) continue
                    LogcatManager.i(TAG, "📸 새 컷 ${fresh.size}개 감지 — 수신 시작")
                    for (handle in fresh) {
                        if (!isActive) break
                        seen.add(handle) // 실패해도 재시도 폭주 방지 위해 기록
                        val (fileName, bytes) = downloadObjectBytes(cmd, handle, vendor) ?: continue
                        onShot(fileName, bytes)
                    }
                }
            }
            closeSessionQuietly(cmd)
            Result.success(Unit)
        } catch (e: CancellationException) {
            LogcatManager.i(TAG, "물리 셔터 리스너 취소")
            throw e
        } catch (e: Exception) {
            LogcatManager.e(TAG, "물리 셔터 리스너 오류: ${e.message}")
            Result.failure(e)
        } finally {
            try { session?.cmd?.close() } catch (_: Exception) {}
            try { session?.event?.close() } catch (_: Exception) {}
        }
    }

    /**
     * WTU 정석 전송큐 드레인(니콘). 0x9010(AdvancedTransfer, param=0x2001 고정)을 반복 호출해
     * 카메라 전송큐의 다음 항목을 (실제 파일명+경로와 함께) 꺼낸다. 0x9010이 그 핸들의 카드 잠금도
     * 풀어주므로 곧바로 표준 0x1008+0x101b(downloadStandard)로 풀해상도 수신.
     * @return 수신 건수. 0x9010 자체가 미지원(0x2005)이면 -1(폴백 신호). 큐가 비면 0(정상 종료).
     */
    private suspend fun drainTransferQueue(
        sock: Socket,
        onShot: suspend (String, ByteArray) -> Unit
    ): Int {
        var count = 0
        while (true) {
            val res = execMtpOp(
                sock, OP_NikonAdvancedTransfer,
                intArrayOf(PARAM_ADVANCEDTRANSFER), DATA_DIR_CAMERA_TO_HOST_OR_NONE
            )
            if (res.respCode == RC_OperationNotSupported) return -1 // 미지원 → 폴백
            if (res.respCode != RC_OK) break                        // 큐 빔/일시 에러 → 종료(다음 폴에서 재시도)
            val item = parseAdvancedTransferResponse(res.data) ?: break
            val (handle, fileName) = item
            LogcatManager.i(TAG, "📥 전송큐 항목 handle=0x%08x name=%s".format(handle, fileName))
            val dl = downloadStandard(sock, handle, PtpVendor.NIKON)
            if (dl != null) {
                onShot(fileName, dl.second) // 0x9010이 준 실제 파일명 우선
                count++
            }
            yield() // 취소 응답성
        }
        return count
    }

    /** 0x9010 응답 파싱: [handle u32][counted-UTF16LE 경로]... → (handle, basename). 실측 포맷 기준. */
    private fun parseAdvancedTransferResponse(data: ByteArray): Pair<Int, String>? {
        if (data.size < 6) return null
        val handle = leInt(data, 0)
        val charLen = data[4].toInt() and 0xFF // 널 포함 문자 수
        if (charLen == 0) return null
        val byteLen = charLen * 2
        val start = 5
        if (start + byteLen > data.size) return null
        val path = try {
            String(data, start, byteLen - 2, Charsets.UTF_16LE).trimEnd('\u0000', ' ')
        } catch (e: Exception) {
            return null
        }
        val name = path.substringAfterLast('/')
        if (name.isBlank()) return null
        return handle to name
    }

    /** GetDeviceInfo 데이터셋의 VendorExtensionID(offset 2, u32)로 제조사 판별. */
    private fun detectVendor(deviceInfo: ByteArray): PtpVendor {
        if (deviceInfo.size < 6) return PtpVendor.GENERIC
        return when (leInt(deviceInfo, 2)) {
            VENDOR_EXT_NIKON -> PtpVendor.NIKON
            VENDOR_EXT_CANON -> PtpVendor.CANON
            VENDOR_EXT_FUJI -> PtpVendor.FUJI
            VENDOR_EXT_SONY -> PtpVendor.SONY
            VENDOR_EXT_PANASONIC -> PtpVendor.PANASONIC
            else -> PtpVendor.GENERIC
        }
    }

    /** 제조사별 세션 승인. 니콘 STA만 vendor 핸드셰이크 필요, 나머지는 표준 PTP라 no-op. */
    private fun approveSession(sock: Socket, vendor: PtpVendor) {
        if (vendor == PtpVendor.NIKON) {
            performNikonApproval(sock)
        } else {
            LogcatManager.i(TAG, "$vendor — 표준 PTP, 세션 승인 불필요")
        }
    }

    /**
     * 단일 핸들의 풀해상도 bytes 수신. 제조사에 따라 경로가 갈린다(저장은 안 함).
     *   · 니콘: vendor 잠금우회(0x9421 크기 + 0x9431 부분).
     *   · 그 외: 표준 PTP(0x1008 정보 + 0x101b 부분, 미지원 시 0x1009 전체).
     * @return (파일명, 원본 bytes) — 폴더/실패 시 null.
     */
    private fun downloadObjectBytes(sock: Socket, handle: Int, vendor: PtpVendor): Pair<String, ByteArray>? =
        if (vendor == PtpVendor.NIKON) downloadNikonVendor(sock, handle)
        else downloadStandard(sock, handle, vendor)

    /**
     * 니콘 vendor 잠금우회 경로. 표준 1008/1009/101b는 STA 모드에서 0x200F(AccessDenied)로 잠기므로
     * 잠금 비대상인 0x9421(크기) → 0x9431(부분) 청크로 원본 풀해상도를 받는다.
     * 파일명은 1008이 잠겨 못 읽으면 매직바이트로 추정한다.
     */
    private fun downloadNikonVendor(sock: Socket, handle: Int): Pair<String, ByteArray>? {
        // 파일명: 1008이 열려있으면(스마트기기 모드 등) 실제 이름, 잠겨있으면 매직바이트로 추정.
        var fileName: String? = null
        val infoRes = execMtpOp(sock, OP_GetObjectInfo, intArrayOf(handle), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        if (infoRes.respCode == RC_OK) {
            fileName = parseObjectInfoFileName(infoRes.data)
        } else {
            LogcatManager.w(TAG, "GetObjectInfo 잠김(0x%04x) handle=0x%08x — vendor 경로로 진행".format(infoRes.respCode, handle))
        }

        // 1) 객체 크기 (0x9421) — 잠금 비대상 vendor op. 폴더/디렉토리 객체는 size=0 → 스킵.
        val sizeRes = execMtpOp(sock, OP_NikonGetObjectSize, intArrayOf(handle), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        val objectSize = if (sizeRes.respCode == RC_OK && sizeRes.data.size >= 8) leLong(sizeRes.data, 0) else -1L
        if (objectSize <= 0L) {
            LogcatManager.w(TAG, "GetObjectSize 0/무효(resp=0x%04x size=%d) handle=0x%08x — 폴더 등 스킵".format(sizeRes.respCode, objectSize, handle))
            return null
        }
        LogcatManager.i(TAG, "객체 크기 handle=0x%08x size=%d".format(handle, objectSize))

        // 2) GetPartialObjectEx (0x9431) 청크 다운로드 — 잠금 비대상 vendor op
        val out = ByteArrayOutputStream()
        var offset = 0L
        while (offset < objectSize) {
            val remaining = objectSize - offset
            val chunk = if (remaining < CHUNK_BYTES) remaining else CHUNK_BYTES
            val res = execMtpOp(
                sock, OP_NikonGetPartialObjectEx,
                intArrayOf(
                    handle,
                    (offset and 0xFFFFFFFFL).toInt(), (offset ushr 32).toInt(),
                    (chunk and 0xFFFFFFFFL).toInt(), (chunk ushr 32).toInt()
                ),
                DATA_DIR_CAMERA_TO_HOST_OR_NONE
            )
            if (res.respCode != RC_OK) {
                LogcatManager.e(TAG, "❌ GetPartialObjectEx 실패(0x%04x) handle=0x%08x off=%d".format(res.respCode, handle, offset))
                return null
            }
            if (res.data.isEmpty()) {
                LogcatManager.w(TAG, "GetPartialObjectEx 0바이트 반환 off=%d — 중단".format(offset))
                break
            }
            out.write(res.data)
            offset += res.data.size
        }
        return finalize(out.toByteArray(), fileName, handle, PtpVendor.NIKON)
    }

    /**
     * 표준 PTP 경로(니콘 외 제조사 기본). GetObjectInfo(0x1008)로 이름·크기를 읽고
     * GetPartialObject(0x101b)로 1MB씩 받는다. 부분 op 미지원(0x2005)이면 GetObject(0x1009) 전체로 폴백.
     * ⚠️ 실기 검증은 니콘 외 기기 보유 시 진행 필요(현재 구조만 확보).
     */
    private fun downloadStandard(sock: Socket, handle: Int, vendor: PtpVendor): Pair<String, ByteArray>? {
        val infoRes = execMtpOp(sock, OP_GetObjectInfo, intArrayOf(handle), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        if (infoRes.respCode != RC_OK) {
            LogcatManager.w(TAG, "GetObjectInfo 실패(0x%04x) handle=0x%08x — 스킵".format(infoRes.respCode, handle))
            return null
        }
        // 폴더/연관 객체는 스킵.
        if (infoRes.data.size >= OBJINFO_OFF_FORMAT + 2 &&
            leShort(infoRes.data, OBJINFO_OFF_FORMAT) == OBJFORMAT_ASSOCIATION
        ) {
            return null
        }
        val fileName = parseObjectInfoFileName(infoRes.data)
        val objectSize =
            if (infoRes.data.size >= OBJINFO_OFF_COMPRESSED_SIZE + 4)
                leInt(infoRes.data, OBJINFO_OFF_COMPRESSED_SIZE).toLong() and 0xFFFFFFFFL
            else -1L
        if (objectSize <= 0L) {
            LogcatManager.w(TAG, "객체 크기 0/무효(size=%d) handle=0x%08x — 스킵".format(objectSize, handle))
            return null
        }
        LogcatManager.i(TAG, "객체 크기 handle=0x%08x size=%d".format(handle, objectSize))

        val out = ByteArrayOutputStream()
        var offset = 0L
        while (offset < objectSize) {
            val remaining = objectSize - offset
            val chunk = if (remaining < CHUNK_BYTES) remaining else CHUNK_BYTES
            val res = execMtpOp(
                sock, OP_GetPartialObject,
                intArrayOf(handle, (offset and 0xFFFFFFFFL).toInt(), (chunk and 0xFFFFFFFFL).toInt()),
                DATA_DIR_CAMERA_TO_HOST_OR_NONE
            )
            if (res.respCode == RC_OperationNotSupported && offset == 0L) {
                // 부분 전송 미지원 → 전체(0x1009) 폴백.
                LogcatManager.w(TAG, "GetPartialObject 미지원 — GetObject(0x1009) 전체 폴백 handle=0x%08x".format(handle))
                val whole = execMtpOp(sock, OP_GetObject, intArrayOf(handle), DATA_DIR_CAMERA_TO_HOST_OR_NONE)
                if (whole.respCode != RC_OK) {
                    LogcatManager.e(TAG, "❌ GetObject 실패(0x%04x) handle=0x%08x".format(whole.respCode, handle))
                    return null
                }
                out.write(whole.data)
                offset = objectSize
                break
            }
            if (res.respCode != RC_OK) {
                LogcatManager.e(TAG, "❌ GetPartialObject 실패(0x%04x) handle=0x%08x off=%d".format(res.respCode, handle, offset))
                return null
            }
            if (res.data.isEmpty()) {
                LogcatManager.w(TAG, "GetPartialObject 0바이트 반환 off=%d — 중단".format(offset))
                break
            }
            out.write(res.data)
            offset += res.data.size
        }
        return finalize(out.toByteArray(), fileName, handle, vendor)
    }

    /**
     * 수신 바이트 검증 + 파일명 확정. STA 잠금으로 GetObjectInfo(0x1008)가 막혀 실제 파일명을 못 받을 때,
     * 핸들 hex로 합성하면 카메라 표시명과 달라 'DSC_1234.NEF'를 흉내내는 가짜가 된다. 대신:
     *   (1) 0x1008이 열려 실제 파일명을 받았으면 그대로.
     *   (2) 니콘: 수신 바이트의 MakerNote FileInfo에서 실제 FileNumber 복원 → DSC_<NNNN>.
     *   (3) EXIF DateTimeOriginal 기반 시간순 합성(제조사 무관, 정직한 추정명).
     *   (4) 최후: 실제 니콘명으로 오인되지 않도록 비-DSC 접두(CAM_<handle>).
     */
    private fun finalize(bytes: ByteArray, fileName: String?, handle: Int, vendor: PtpVendor): Pair<String, ByteArray>? {
        if (bytes.isEmpty()) {
            LogcatManager.e(TAG, "❌ 수신 0바이트 handle=0x%08x".format(handle))
            return null
        }
        if (fileName != null) return fileName to bytes

        val ext = detectExtension(bytes, vendor).uppercase()
        val info = NikonMakerNoteFileName.parse(bytes)

        if (vendor == PtpVendor.NIKON && info.fileNumber != null) {
            val name = "DSC_%04d.%s".format(info.fileNumber, ext)
            LogcatManager.i(TAG, "MakerNote FileInfo 복원 성공 FileNumber=%d → %s".format(info.fileNumber, name))
            return name to bytes
        }

        val ts = info.dateTimeOriginal?.let { toTimestampName(it) }
        if (ts != null) {
            val name = "$ts.$ext"
            LogcatManager.w(TAG, "파일명 복원 실패 — 촬영시각 기반 합성: $name (카메라 표시명과 다를 수 있음)")
            return name to bytes
        }

        val name = "CAM_%08x.%s".format(handle, ext)
        LogcatManager.w(TAG, "메타 복원 전부 실패 — 핸들 기반 합성: $name")
        return name to bytes
    }

    /** "YYYY:MM:DD HH:MM:SS"(EXIF DateTimeOriginal) → "YYYYMMDD_HHMMSS". 형식 불일치 시 null. */
    private fun toTimestampName(exifDateTime: String): String? {
        val m = Regex("""(\d{4}):(\d{2}):(\d{2})\s+(\d{2}):(\d{2}):(\d{2})""").find(exifDateTime) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        return "$y$mo${d}_$h$mi$s"
    }

    /** 매직바이트로 확장자 추정(1008 잠김 시 파일명 합성용). TIFF 계열 RAW는 제조사 기본값 사용. */
    private fun detectExtension(b: ByteArray, vendor: PtpVendor): String = when {
        b.size >= 3 && (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xFF) == 0xD8 &&
            (b[2].toInt() and 0xFF) == 0xFF -> "jpg"
        // RAW는 대부분 TIFF 컨테이너("II*\0" 리틀엔디안 / "MM\0*" 빅엔디안)라 매직만으론 제조사 구분 불가 → 제조사 기본 확장자.
        isTiffContainer(b) -> rawExtFor(vendor)
        else -> "bin"
    }

    private fun isTiffContainer(b: ByteArray): Boolean =
        (b.size >= 4 && b[0].toInt() == 0x49 && b[1].toInt() == 0x49 &&
            (b[2].toInt() and 0xFF) == 0x2A && b[3].toInt() == 0x00) ||
            (b.size >= 4 && b[0].toInt() == 0x4D && b[1].toInt() == 0x4D &&
                b[2].toInt() == 0x00 && (b[3].toInt() and 0xFF) == 0x2A)

    private fun rawExtFor(vendor: PtpVendor): String = when (vendor) {
        PtpVendor.NIKON -> "nef"
        PtpVendor.CANON -> "cr3"
        PtpVendor.SONY -> "arw"
        PtpVendor.FUJI -> "raf"
        PtpVendor.PANASONIC -> "rw2"
        PtpVendor.GENERIC -> "tif"
    }

    /**
     * 니콘 STA 연결 승인(0x952b → 0x935a)을 현재 세션에서 수행한다.
     * 와이어 포맷은 NikonAuthenticationService와 동일(PTP/IP OPERATION_REQUEST = airnef CmdReq).
     * @return 0x935a가 RC=0x2001(자동 승인)이면 true.
     */
    private fun performNikonApproval(sock: Socket): Boolean {
        val r952b = execMtpOp(sock, OP_Nikon952b, dataDir = DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        LogcatManager.i(TAG, "0x952b resp=0x%04x".format(r952b.respCode))
        val r935a = execMtpOp(
            sock, OP_Nikon935a,
            intArrayOf(PARAM_935A_APPROVAL), DATA_DIR_CAMERA_TO_HOST_OR_NONE
        )
        return if (r935a.respCode == RC_OK) {
            LogcatManager.i(TAG, "✅ 0x935a 연결 승인 성공 (RC=0x2001)")
            true
        } else {
            LogcatManager.w(TAG, "⚠️ 0x935a 승인 비정상(0x%04x) — 계속 시도".format(r935a.respCode))
            false
        }
    }

    /**
     * GetStorageIDs → 각 저장소 GetObjectHandles를 합쳐 전체 카드 객체 핸들 목록을 만든다.
     * 니콘 STA는 승인(performNikonApproval) 이후 호출해야 0x200F(AccessDenied) 없이 접근된다.
     */
    private fun fetchAllCardHandles(sock: Socket): List<Int> {
        val storageRes = execMtpOp(sock, OP_GetStorageIDs, dataDir = DATA_DIR_CAMERA_TO_HOST_OR_NONE)
        if (storageRes.respCode != RC_OK) {
            LogcatManager.w(TAG, "GetStorageIDs 실패: 0x%04x".format(storageRes.respCode))
            return emptyList()
        }
        val storageIds = parseCountedWordList(storageRes.data)
        if (storageIds.isEmpty()) {
            LogcatManager.w(TAG, "저장소 없음")
            return emptyList()
        }
        val all = ArrayList<Int>()
        for (storageId in storageIds) {
            val handlesRes = execMtpOp(
                sock, OP_GetObjectHandles,
                intArrayOf(storageId, 0, 0), DATA_DIR_CAMERA_TO_HOST_OR_NONE
            )
            if (handlesRes.respCode == RC_OK) {
                all.addAll(parseCountedWordList(handlesRes.data))
            } else {
                LogcatManager.w(
                    TAG,
                    "GetObjectHandles 실패(storage=0x%08x): 0x%04x".format(storageId, handlesRes.respCode)
                )
            }
        }
        return all
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
            .putInt(0x00010001) // 호스트 프로토콜 버전
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

    // ── LE 헬퍼 ──

    private fun leInt(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun leShort(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun leLong(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = v or ((b[off + i].toLong() and 0xFF) shl (8 * i))
        return v
    }
}
