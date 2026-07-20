package com.inik.camcon.data.datasource.ptpip

import android.util.Log
import com.inik.camcon.data.network.ptpip.PtpipTetherService
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 물리 셔터 무선 수신(테더링) 협력자 (PtpipDataSource에서 분리).
 *
 * 제조사 자동 판별로 니콘=vendor 잠금우회, 그 외=표준 PTP. 단일 PTP/IP 세션 제약상
 * libgphoto2(원격촬영/라이브뷰)와 공존 불가. 이 모드는 libgphoto2 연결을 끊은 뒤(호출부 책임)
 * 단독 세션으로 카드 핸들을 폴링해 새로 찍힌 컷만 풀해상도로 받는다. 받은 bytes는 기존
 * PhotoDownloadManager(RAW게이팅·FREE축소·EXIF·MediaStore) → [photoEvents] 스트림으로
 * 흘려보내 capturedPhotos/미리보기에 동일하게 등장시킨다.
 *
 * 연결 엔진과 공유 상태 없음(자체 Job만 소유). disconnect 시 파사드가 [stopShutterListeningAndJoin]을,
 * cleanup 시 [stopShutterListening]을 호출한다.
 */
internal class PtpipTetheringController(
    private val tetherService: PtpipTetherService,
    private val photoDownloadManager: PhotoDownloadManager,
    private val photoEvents: MutableSharedFlow<PtpipPhotoEvent>,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    private companion object {
        private const val TAG = "PtpipDataSource"
    }

    private var shutterListenerJob: Job? = null

    val isShutterListening: Boolean
        get() = shutterListenerJob?.isActive == true

    fun startShutterListening(camera: PtpipCamera) {
        if (shutterListenerJob?.isActive == true) {
            Log.w(TAG, "물리 셔터 리스너가 이미 실행 중")
            return
        }
        Log.i(TAG, "물리 셔터 리스너 시작 요청: ${LogMask.serial(camera.name)}")
        shutterListenerJob = coroutineScope.launch(ioDispatcher) {
            tetherService.listenForNewShots(camera) { fileName, bytes ->
                try {
                    val saved = photoDownloadManager.handleNativePhotoDownload(
                        filePath = fileName,
                        fileName = fileName,
                        imageData = bytes,
                        cameraCapabilities = null,
                        cameraSettings = null
                    )
                    if (saved != null) {
                        Log.i(TAG, "새 컷 수신·저장: ${LogMask.path(saved.filePath)}")
                        photoEvents.emit(
                            PtpipPhotoEvent.Downloaded(saved.filePath, fileName, bytes)
                        )
                    } else {
                        Log.w(TAG, "새 컷 저장 차단/실패(게이팅 등): $fileName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "새 컷 처리 오류: ${e.message}")
                }
            }
        }
    }

    fun stopShutterListening() {
        shutterListenerJob?.cancel()
        shutterListenerJob = null
        Log.i(TAG, "물리 셔터 리스너 중지")
    }

    /**
     * 물리 셔터 리스너를 중지하고 **완전히 종료될 때까지 대기**한다(J7).
     *
     * cancel만 하고 join하지 않으면 리스너 코루틴의 finally(CloseSession 전송 + 소켓 close)가
     * 아직 끝나기 전에 다음 일반 연결이 진행돼 Z8 계열에서 세션 슬롯 잠금·-7 재연결 거부가 난다.
     * 대용량 NEF 수신 중이면 blocking read가 소켓 op 타임아웃(최대 ~30s)까지 갈 수 있으므로 상한을 둔다.
     * (PtpipTetherService의 finally가 NonCancellable로 CloseSession을 best-effort 전송한다.)
     */
    suspend fun stopShutterListeningAndJoin(timeoutMs: Long = 8000L) {
        val job = shutterListenerJob ?: return
        shutterListenerJob = null
        Log.i(TAG, "물리 셔터 리스너 중지(정상 종료 대기)")
        job.cancel()
        val joined = withTimeoutOrNull(timeoutMs) {
            job.join()
            true
        } ?: false
        if (!joined) Log.w(TAG, "물리 셔터 리스너 종료 대기 초과(${timeoutMs}ms) — 계속 진행")
    }
}
