package com.inik.camcon.data.repository.managers

import com.inik.camcon.domain.model.TransferQueueState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 다운로드/처리 진행 카운트 추적기 (요구 E2).
 *
 * 외부 셔터 촬영 → 바이트 수신(DOWNLOADING) → 후처리·저장(PROCESSING) → 완료(제거)의
 * 파일별 단계를 [TransferQueueState] 로 집계해 StateFlow 로 노출한다.
 *
 * 키 설계: fileName(basename+확장자) 단독.
 *  - 듀얼슬롯(store_00010001/00020001)이 같은 fileName 을 두 번 내려보내도 같은 키라 카운트가 부풀지 않는다.
 *  - RAW+JPG 는 확장자가 달라 별개 키로 정확히 2건 집계된다.
 *
 * 스레드 안전: 네이티브 콜백 스레드/IO 디스패처 등 여러 스레드에서 호출되므로
 * [lock] 으로 [stages] 갱신과 재계산을 원자적으로 수행한다.
 */
@Singleton
class TransferProgressTracker @Inject constructor() {

    private enum class Stage { DOWNLOADING, PROCESSING }

    private val lock = Any()

    // 등장 순서를 보존해 "마지막 전이 파일" 결정에 사용한다.
    private val stages = LinkedHashMap<String, Stage>()

    private val _state = MutableStateFlow(TransferQueueState())
    val state: StateFlow<TransferQueueState> = _state.asStateFlow()

    /** 파일 바이트 수신 시작(외부 셔터 촬영 감지 시점). */
    fun markDownloading(fileName: String) {
        synchronized(lock) {
            stages[fileName] = Stage.DOWNLOADING
            recompute()
        }
    }

    /**
     * 후처리·저장 시작. 동일 fileName 이 DOWNLOADING 상태였다면 PROCESSING 으로 전이한다.
     * onPhotoCaptured 를 거치지 않은 경로(직접 다운로드 등)면 곧장 PROCESSING 으로 등장한다.
     */
    fun markProcessing(fileName: String) {
        synchronized(lock) {
            stages[fileName] = Stage.PROCESSING
            recompute()
        }
    }

    /** 처리 완료(성공·실패 무관). 큐에서 제거한다. */
    fun markDone(fileName: String) {
        synchronized(lock) {
            stages.remove(fileName)
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

        _state.value = TransferQueueState(
            downloading = downloading,
            processing = processing,
            currentFileName = current
        )
    }
}
