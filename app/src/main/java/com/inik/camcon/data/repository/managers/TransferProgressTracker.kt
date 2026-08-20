package com.inik.camcon.data.repository.managers

import com.inik.camcon.domain.model.TransferQueueState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 다운로드/처리 진행 카운트 + 처리율 추적기 (요구 E2, D).
 *
 * 외부 셔터 촬영 → 바이트 수신(DOWNLOADING) → 후처리·저장(PROCESSING) → 완료(제거)의
 * 파일별 단계를 [TransferQueueState] 로 집계해 StateFlow 로 노출한다.
 *
 * 키 설계: fileName(basename+확장자) 단독.
 *  - 듀얼슬롯(store_00010001/00020001)이 같은 fileName 을 두 번 내려보내도 같은 키라 카운트가 부풀지 않는다.
 *  - RAW+JPG 는 확장자가 달라 별개 키로 정확히 2건 집계된다.
 *
 * 처리율(요구 D): [markDownloading] 시각과 [markProcessing] 시 넘어온 바이트 수로 파일별 순간
 * 처리율(B/s)을 구하고 EMA(지수이동평균)로 완만화해 [TransferQueueState.speedBytesPerSec] 로 노출한다.
 * 큐가 완전히 비면(idle) EMA 는 0 으로 리셋하되(다음 세션이 이전 속도에 오염되지 않게),
 * **표시값은 마지막 측정 속도를 유지**한다 — 전송이 끝나는 순간 상단바 속도 칩이 사라지면
 * 사용자가 방금 전송이 얼마나 빨랐는지 확인할 수 없다(사용자 요구 2026-08-18). 표시값은
 * [clear]\(연결 해제·리스너 정지\) 시에만 사라진다.
 *
 * 스레드 안전: 네이티브 콜백 스레드/IO 디스패처 등 여러 스레드에서 호출되므로
 * [lock] 으로 [stages] 갱신과 재계산을 원자적으로 수행한다.
 *
 * [clock] 주입: 기본(주 생성자)은 [Clock] 을 받아 단위 테스트가 고정 시계를 주입해 소요시간을
 * 결정적으로 검증할 수 있다. Hilt 는 `@Inject` 무인자 보조 생성자를 사용해 시스템 시계를 쓴다
 * (별도 Clock 바인딩 불필요). `@Inject` 생성자가 하나뿐이라 Dagger 다중 생성자 제약도 만족한다.
 */
@Singleton
class TransferProgressTracker(
    private val clock: Clock
) {

    @Inject
    constructor() : this(Clock.systemUTC())

    private companion object {
        /** EMA 가중치 — 최근 파일에 0.4, 과거 누적에 0.6. 튀는 값 완화 + 빠른 반영 절충. */
        const val EMA_ALPHA = 0.4
    }

    private enum class Stage { DOWNLOADING, PROCESSING }

    private val lock = Any()

    // 등장 순서를 보존해 "마지막 전이 파일" 결정에 사용한다.
    private val stages = LinkedHashMap<String, Stage>()

    // 파일별 다운로드 시작 시각(ms). markProcessing/markDone/clear 에서 정리해 누수 방지.
    private val downloadStartMs = HashMap<String, Long>()

    // 처리율 이동평균(B/s). 측정값이 없으면 0.
    private var speedEmaBps = 0.0

    // 마지막 측정 속도(B/s) — 큐가 비어도 표시용으로 유지. clear() 에서만 소멸.
    private var lastSpeedBps = 0.0

    // 네이티브 실측 전송시간이 이미 반영된 파일명. markProcessing 의 시계-창 계산이
    // 배관 지연(~20ms → 550MB/s 오표시)으로 실측을 덮어쓰지 않게 막는다.
    private val authoritativeSpeedFiles = LinkedHashSet<String>()

    private val _state = MutableStateFlow(TransferQueueState())
    val state: StateFlow<TransferQueueState> = _state.asStateFlow()

    /**
     * 네이티브가 실측한 전송시간으로 처리율을 반영한다(소니 PTP/IP 인라인 다운로드 경로).
     *
     * wire 전송이 wait_for_event 내부에서 끝나는 경로는 markDownloading→markProcessing 창이
     * 콜백 배관 지연만 재므로, 네이티브의 wait+download 합산 창을 정본으로 쓴다. 이 호출이
     * 있었던 파일은 [markProcessing] 의 창 기반 갱신을 건너뛴다(이중 반영 방지).
     */
    fun recordTransferDuration(fileName: String, bytes: Long, durationMs: Long) {
        if (bytes <= 0 || durationMs <= 0) return
        synchronized(lock) {
            val instantBps = bytes.toDouble() * 1000.0 / durationMs
            speedEmaBps =
                if (speedEmaBps <= 0.0) instantBps
                else EMA_ALPHA * instantBps + (1 - EMA_ALPHA) * speedEmaBps
            lastSpeedBps = speedEmaBps
            authoritativeSpeedFiles.add(fileName)
            // 상한 초과 시 가장 오래된 항목 제거(markProcessing 미도달 파일의 영구 누적 방지)
            if (authoritativeSpeedFiles.size > 512) {
                val it = authoritativeSpeedFiles.iterator()
                it.next(); it.remove()
            }
            recompute()
        }
    }

    /** 파일 바이트 수신 시작(외부 셔터 촬영 감지 시점). */
    fun markDownloading(fileName: String) {
        synchronized(lock) {
            stages[fileName] = Stage.DOWNLOADING
            // 동일 파일이 중복 통지돼도 최초 시작 시각을 유지(putIfAbsent 의미) — 소요시간 과소평가 방지.
            if (!downloadStartMs.containsKey(fileName)) {
                downloadStartMs[fileName] = clock.millis()
            }
            recompute()
        }
    }

    /**
     * 후처리·저장 시작. 동일 fileName 이 DOWNLOADING 상태였다면 PROCESSING 으로 전이한다.
     * onPhotoCaptured 를 거치지 않은 경로(직접 다운로드 등)면 곧장 PROCESSING 으로 등장한다.
     *
     * @param downloadedBytes 방금 수신 완료한 바이트 수(요구 D). 0 이하이거나 시작 시각이
     *   없으면(직접 다운로드 등) 처리율을 갱신하지 않는다.
     */
    fun markProcessing(fileName: String, downloadedBytes: Long = -1L) {
        synchronized(lock) {
            stages[fileName] = Stage.PROCESSING

            // 처리율 갱신 — 다운로드(수신) 구간 = markDownloading ~ 지금.
            // 단 네이티브 실측(recordTransferDuration)이 이미 반영된 파일은 건너뛴다 —
            // 이 창은 그 경로에선 배관 지연이라 550MB/s 급 오표시를 만든다.
            val startMs = downloadStartMs.remove(fileName)
            if (authoritativeSpeedFiles.remove(fileName)) {
                // no-op: 실측 반영 완료
            } else if (startMs != null && downloadedBytes > 0) {
                val elapsedMs = (clock.millis() - startMs).coerceAtLeast(1L)
                val instantBps = downloadedBytes.toDouble() * 1000.0 / elapsedMs
                speedEmaBps =
                    if (speedEmaBps <= 0.0) instantBps
                    else EMA_ALPHA * instantBps + (1 - EMA_ALPHA) * speedEmaBps
                lastSpeedBps = speedEmaBps
            }
            recompute()
        }
    }

    /** 처리 완료(성공·실패 무관). 큐에서 제거한다. */
    fun markDone(fileName: String) {
        synchronized(lock) {
            stages.remove(fileName)
            // markProcessing 없이 곧장 markDone 되는 경로(차단·미지원 파일)의 시작 시각 누수 정리.
            downloadStartMs.remove(fileName)
            recompute()
        }
    }

    /**
     * 전체 진행 큐를 비운다(이벤트 리스너 정지·연결 해제 시점).
     *
     * 다운로드 도중 연결이 끊겨 onPhotoDownloaded 가 끝내 도착하지 않으면
     * DOWNLOADING 항목이 영구 잔존해 진행 배지가 멈춘다. 정지/해제 시 호출해
     * 빈 상태(isActive=false)를 방출시켜 배지를 해소한다.
     *
     * 정상 촬영 진행 중에는 호출되지 않아야 한다(진행 중 카운트 유실 방지).
     */
    fun clear() {
        synchronized(lock) {
            stages.clear()
            downloadStartMs.clear()
            authoritativeSpeedFiles.clear()
            speedEmaBps = 0.0
            lastSpeedBps = 0.0
            recompute()
        }
    }

    /** 반드시 [lock] 안에서 호출. 현재 stages 로부터 스냅샷을 만든다. */
    private fun recompute() {
        val downloading = stages.count { it.value == Stage.DOWNLOADING }
        val processing = stages.count { it.value == Stage.PROCESSING }

        // 마지막 PROCESSING 우선, 없으면 마지막 DOWNLOADING 의 파일명.
        val current = stages.entries.lastOrNull { it.value == Stage.PROCESSING }?.key
            ?: stages.entries.lastOrNull { it.value == Stage.DOWNLOADING }?.key

        // 큐가 완전히 비면 EMA 만 리셋 — 다음 전송 세션이 이전 속도를 이어받지 않게 한다.
        // 표시값(lastSpeedBps)은 유지: 전송 완료 후에도 속도 칩이 마지막 속도를 계속 보여준다.
        if (downloading == 0 && processing == 0) {
            speedEmaBps = 0.0
        }

        _state.value = TransferQueueState(
            downloading = downloading,
            processing = processing,
            currentFileName = current,
            speedBytesPerSec = (if (speedEmaBps > 0.0) speedEmaBps else lastSpeedBps).toLong()
        )
    }
}
