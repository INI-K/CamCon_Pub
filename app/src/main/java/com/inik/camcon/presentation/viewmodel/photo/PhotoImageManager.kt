package com.inik.camcon.presentation.viewmodel.photo

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.usecase.camera.DownloadCameraPhotoUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraPhotoExifJsonUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraThumbnailUseCase
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.inik.camcon.utils.Constants
import com.inik.camcon.utils.LogMask
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사진 이미지 관리 전용 매니저
 * 단일책임: 이미지 다운로드, 캐싱, 리사이징 작업만 담당
 */
@Singleton
class PhotoImageManager @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val getCameraThumbnailUseCase: GetCameraThumbnailUseCase,
    private val downloadCameraPhotoUseCase: DownloadCameraPhotoUseCase,
    private val getCameraPhotoExifJsonUseCase: GetCameraPhotoExifJsonUseCase,
    private val validateImageFormatUseCase: ValidateImageFormatUseCase,
    private val galleryDownloadStore: GalleryDownloadStore,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "사진이미지매니저"
    }

    // 앱 scope의 자식 scope — cancelChildren해도 앱 scope에 영향 없음
    private var managerScope = createManagerScope()

    private fun createManagerScope(): CoroutineScope =
        CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext.job))

    // 썸네일 캐시 갱신 직렬화용 단일 락 (F31: launch별 독립 스냅샷 교차 손상 방지)
    private val thumbnailCacheLock = Any()

    // 풀이미지 캐시 갱신 직렬화용 단일 락 (서로 다른 path 동시 다운로드 시 lost-update 방지)
    private val fullImageCacheLock = Any()

    // 풀이미지 캐시 바이트 예산. 개수 FIFO(10)만으로는 RAW 다중선택 시 힙이 수백 MB까지 쌓여
    // OOM 위험이 있어(largeHeap 로도 위험) 바이트 예산 축출을 둔다.
    // 저사양 기기 보호: min(상수 상한, maxMemory/8). 하한 24MB(너무 작은 힙에서도 최소 1장 표시).
    private val fullImageCacheBudgetBytes: Long by lazy {
        val heapBudget = Runtime.getRuntime().maxMemory() / 8
        minOf(Constants.Cache.MAX_FULL_IMAGE_CACHE_BYTES, heapBudget)
            .coerceAtLeast(24L * 1024 * 1024)
    }

    /**
     * [_fullImageCache] 에 바이트 예산을 적용해 넣는다(예산 초과 시 오래된 항목부터 축출).
     * 반드시 [fullImageCacheLock] 보유 상태에서 호출.
     *
     * @return 갱신 후 캐시 항목 수.
     */
    private fun putFullImageLocked(photoPath: String, data: ByteArray): Int {
        val updated = _fullImageCache.value.toMutableMap()
        updated.remove(photoPath) // 갱신이면 기존 항목 제거 후 재삽입(삽입 순서 = 최신)
        var totalBytes = updated.values.sumOf { it.size.toLong() }
        val incoming = data.size.toLong()
        val iterator = updated.entries.iterator()
        // LinkedHashMap 삽입 순서 = 오래된 것부터. 바이트 예산 또는 개수 상한 확보 전까지 축출.
        // (개수 상한은 소용량 항목이 다수 쌓이는 경우의 보조 방어선)
        while (
            (totalBytes + incoming > fullImageCacheBudgetBytes ||
                updated.size >= Constants.Cache.MAX_FULL_IMAGE_CACHE_SIZE) &&
            iterator.hasNext()
        ) {
            val evicted = iterator.next()
            totalBytes -= evicted.value.size.toLong()
            iterator.remove()
        }
        updated[photoPath] = data
        _fullImageCache.value = updated
        return updated.size
    }

    // 기기 저장(MediaStore)까지 완료된 path 집합. 표시용 캐시(FIFO 10슬롯 축출)와 독립적으로
    // 유지해, 이미 저장한 사진을 명시적 재다운로드 시 중복 저장하지 않는다.
    private val persistedPathsLock = Any()
    private val persistedPaths = mutableSetOf<String>()

    private fun isAlreadyPersisted(photoPath: String): Boolean =
        synchronized(persistedPathsLock) { persistedPaths.contains(photoPath) }

    private fun markPersisted(photoPath: String) {
        synchronized(persistedPathsLock) { persistedPaths.add(photoPath) }
    }

    // 썸네일 캐시
    private val _thumbnailCache = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val thumbnailCache: StateFlow<Map<String, ByteArray>> = _thumbnailCache.asStateFlow()

    // 고해상도 이미지 캐시
    private val _fullImageCache = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val fullImageCache: StateFlow<Map<String, ByteArray>> = _fullImageCache.asStateFlow()

    // 다운로드 상태 관리
    private val _downloadingImages = MutableStateFlow<Set<String>>(emptySet())
    val downloadingImages: StateFlow<Set<String>> = _downloadingImages.asStateFlow()

    // 썸네일 로딩 상태 관리 (중복 방지용)
    private val _loadingThumbnails = MutableStateFlow<Set<String>>(emptySet())
    val loadingThumbnails: StateFlow<Set<String>> = _loadingThumbnails.asStateFlow()

    /**
     * 이 세션의 카메라가 쓸 수 있는 썸네일을 주지 못한다.
     *
     * **광고가 아니라 실제로 받은 바이트로 판정한다.** 어떤 카메라는 `0x100A GetThumb` 을
     * 광고하면서도 JPEG 이 아닌 것을 돌려준다(소니 a7m5 실측 2026-08-30: 117KB, `BitmapFactory`
     * 디코딩 실패). 반대로 같은 세대라도 정상인 바디가 있으므로(a7M4) 기종명이나 오퍼레이션
     * 광고로 미리 가려내면 멀쩡한 카메라에 오탐이 난다. 그래서 첫 응답의 JPEG 서명을 보고 판정한다.
     *
     * true 가 되면 남은 썸네일 요청을 중단한다. 계속 받아 봐야 쓸 수 없는 데이터를 사진당
     * 100KB 넘게 실어 나르고 로그만 채운다. 세션이 끝나면 [resetThumbnailSupport] 로 푼다.
     */
    private val _thumbnailUnsupported = MutableStateFlow(false)
    val thumbnailUnsupported: StateFlow<Boolean> = _thumbnailUnsupported.asStateFlow()

    // EXIF 정보 캐시
    private val _exifCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val exifCache: StateFlow<Map<String, String>> = _exifCache.asStateFlow()

    // 풀이미지 다운로드 결과 이벤트 — 조용한 실패 제거(필수1).
    // 호출자(ViewModel)가 구독하여 성공/실패 피드백 및 다중선택 진행 집계를 처리한다.
    private val _downloadResult = MutableSharedFlow<DownloadResult>(
        replay = 0,
        extraBufferCapacity = 16
    )
    val downloadResult: SharedFlow<DownloadResult> = _downloadResult.asSharedFlow()

    /**
     * 풀이미지 다운로드 결과. path 기준으로 어떤 사진인지 식별 가능.
     */
    data class DownloadResult(val photoPath: String, val isSuccess: Boolean)

    // 작업 취소를 위한 플래그
    private var isManagerActive = true

    // 썸네일 순차 로딩 잡 — 단일 잡으로 유지(탭 이탈 시 취소). 이전에는 목록 변화마다 루프가
    // 중첩 실행되어 탭을 떠나도 계속 받는 문제가 있었다(2026-07-03 실측).
    private var thumbnailLoadJob: kotlinx.coroutines.Job? = null

    // 아직 받지 않은 썸네일 대기열. 목록이 늘어날 때 **처음부터 다시 받지 않기 위한** 장치다.
    //
    // 페이징은 목록을 51 → 101 → 151 로 늘리는데, 예전에는 그때마다 진행 중 잡을 취소하고
    // 0번부터 다시 시작했다. 그래서 사용자가 스크롤할수록 앞쪽 썸네일을 반복해서 받고 정작
    // 지금 보는 구간은 계속 뒤로 밀렸다. 이제는 새로 들어온 것만 뒤에 붙이고 워커 하나가
    // 이어서 비운다.
    private val pendingLock = Any()
    private val pendingThumbnails = ArrayDeque<CameraPhoto>()

    // 대기열에 이미 들어간 경로. 목록 갱신이 같은 사진을 다시 실어 보내도 중복 적재를 막는다.
    private val queuedPaths = HashSet<String>()

    /**
     * 진행 중인 썸네일 로딩 취소 (탭 이탈 시). 캐시는 유지해 재진입 시 재사용.
     */
    fun cancelThumbnailLoading() {
        if (thumbnailLoadJob?.isActive == true) {
            Log.d(TAG, "썸네일 로딩 취소 (탭 이탈)")
        }
        thumbnailLoadJob?.cancel()
        thumbnailLoadJob = null
        synchronized(pendingLock) {
            pendingThumbnails.clear()
            queuedPaths.clear()
        }
        synchronized(thumbnailCacheLock) {
            _loadingThumbnails.value = emptySet()
        }
    }

    /** 유효한 JPEG 인가. 모든 JPEG 은 `FF D8 FF` 로 시작한다. */
    private fun isJpeg(data: ByteArray): Boolean =
        data.size >= 3 &&
                data[0] == 0xFF.toByte() &&
                data[1] == 0xD8.toByte() &&
                data[2] == 0xFF.toByte()

    /**
     * 새 카메라 세션이 시작되면 썸네일 지원 판정을 다시 하게 한다.
     *
     * 판정은 바디마다 다르므로 세션을 넘겨 유지하면 안 된다. 연결이 끊길 때 호출한다.
     */
    fun resetThumbnailSupport() {
        if (_thumbnailUnsupported.value) {
            Log.d(TAG, "썸네일 지원 판정 초기화 — 새 세션에서 다시 확인한다")
        }
        _thumbnailUnsupported.value = false
    }

    /**
     * 썸네일 로드.
     *
     * 목록이 바뀔 때마다 **처음부터 다시 받지 않는다.** 아직 받지 않은 사진만 대기열 뒤에 붙이고,
     * 워커가 이미 돌고 있으면 그대로 이어서 처리한다. 페이징으로 목록이 51 → 101 → 151 로 늘 때
     * 앞쪽을 반복해서 받느라 지금 보는 구간이 뒤로 밀리던 문제를 없앤다.
     */
    fun loadThumbnailsForPhotos(photos: List<CameraPhoto>) {
        if (_thumbnailUnsupported.value) {
            Log.d(TAG, "썸네일 미지원 세션 — 로딩을 건너뛴다")
            return
        }

        val added = synchronized(pendingLock) {
            var n = 0
            for (photo in photos) {
                // 이미 받았거나 대기열에 있으면 넣지 않는다.
                if (_thumbnailCache.value.containsKey(photo.path)) continue
                if (!queuedPaths.add(photo.path)) continue
                pendingThumbnails.addLast(photo)
                n++
            }
            n
        }

        val queueSize = synchronized(pendingLock) { pendingThumbnails.size }
        Log.d(TAG, "썸네일 대기열에 ${added}개 추가 (대기 ${queueSize}개 / 목록 ${photos.size}개)")

        if (added == 0) return

        // 워커가 이미 돌고 있으면 대기열만 늘리고 끝낸다. 취소·재시작하지 않는 것이 핵심이다.
        if (thumbnailLoadJob?.isActive == true) return

        thumbnailLoadJob = managerScope.launch {
            if (!isManagerActive) {
                Log.d(TAG, "썸네일 로딩 중단됨 (매니저 비활성)")
                return@launch
            }

            while (true) {
                val photo = synchronized(pendingLock) { pendingThumbnails.removeFirstOrNull() }
                    ?: break

                // 대기 중에 다른 경로로 이미 받았을 수 있다.
                if (_thumbnailCache.value.containsKey(photo.path) ||
                    _loadingThumbnails.value.contains(photo.path)
                ) {
                    synchronized(pendingLock) { queuedPaths.remove(photo.path) }
                    continue
                }

                // 매니저 비활성화 체크
                if (!isManagerActive) {
                    return@launch
                }

                // 이 세션이 쓸 수 없는 썸네일을 준다고 판정되면 남은 대기열을 통째로 버린다.
                if (_thumbnailUnsupported.value) {
                    synchronized(pendingLock) {
                        pendingThumbnails.clear()
                        queuedPaths.clear()
                    }
                    return@launch
                }

                // 로딩 상태에 추가 — 동시 launch 간 손상 방지를 위해 최신 값 기준으로 갱신
                synchronized(thumbnailCacheLock) {
                    _loadingThumbnails.value = _loadingThumbnails.value + photo.path
                }

                try {
                    // 네이티브 썸네일 호출
                    getCameraThumbnailUseCase(photo.path).fold(
                        onSuccess = { thumbnailData ->
                            if (!isManagerActive) {
                                return@fold
                            }

                            if (thumbnailData.isNotEmpty()) {
                                // JPEG 서명 검사가 곧 "이 카메라가 쓸 수 있는 썸네일을 주는가"의
                                // 판정이다. 실패하면 이 세션의 나머지 요청을 통째로 중단한다 —
                                // 같은 카메라가 다음 사진에서 갑자기 정상 JPEG 을 줄 이유가 없고,
                                // 계속 받으면 쓸 수 없는 데이터만 사진당 100KB 넘게 실어 나른다.
                                if (!isJpeg(thumbnailData)) {
                                    Log.w(
                                        TAG,
                                        "썸네일이 JPEG 이 아니다: ${photo.name} " +
                                                "(${thumbnailData.size}바이트) — 이 세션의 썸네일을 중단한다"
                                    )
                                    _thumbnailUnsupported.value = true
                                    // 루프의 finally 가 로딩 상태를 정리하므로 여기서는 빠져나가기만 한다.
                                    return@launch
                                }

                                val newSize = synchronized(thumbnailCacheLock) {
                                    val updated = _thumbnailCache.value.toMutableMap()
                                    // 캐시 크기 제한 적용 (삽입 순서 기준 FIFO 축출)
                                    if (updated.size >= Constants.Cache.MAX_THUMBNAIL_CACHE_SIZE) {
                                        val oldestKey = updated.keys.firstOrNull()
                                        if (oldestKey != null) {
                                            updated.remove(oldestKey)
                                        }
                                    }
                                    updated[photo.path] = thumbnailData
                                    _thumbnailCache.value = updated
                                    updated.size
                                }
                                Log.d(TAG, "썸네일 캐시 저장 완료: ${photo.name} (캐시 크기: $newSize)")
                            } else {
                                Log.w(TAG, "빈 썸네일 데이터 수신: ${photo.name}")
                                // 빈 데이터도 캐시에 저장하여 재시도 방지
                                synchronized(thumbnailCacheLock) {
                                    _thumbnailCache.value =
                                        _thumbnailCache.value + (photo.path to ByteArray(0))
                                }
                            }
                        },
                        onFailure = { exception ->
                            if (!isManagerActive) {
                                return@fold
                            }

                            val errorMessage = exception.message ?: "알 수 없는 오류"
                            Log.e(
                                TAG,
                                "썸네일 로드 실패: ${LogMask.path(photo.path)} (${exception.javaClass.simpleName}): $errorMessage"
                            )

                            // 카메라 사용 중 오류에 대해 더 관대하게 처리
                            val isCameraBusyError =
                                errorMessage.contains("사용 중", ignoreCase = true) ||
                                        errorMessage.contains("초기화 중", ignoreCase = true) ||
                                        errorMessage.contains("카메라가 연결되지 않음", ignoreCase = true)

                            if (isCameraBusyError) {
                                Log.w(TAG, "카메라 사용 중/초기화 중 오류 - 캐시 저장 없이 재시도 허용")
                                // 카메라 사용 중인 경우 캐시에 저장하지 않음 (자연스러운 재시도 허용)
                                return@fold
                            }

                            // timeout·연결 끊김·빈 데이터 등 일시적/회복 가능 오류는
                            // 빈 데이터로 굳히지 않고 재시도를 허용한다(영구 빈 썸네일 방지)
                            val isRecoverableError =
                                errorMessage.contains("timeout", ignoreCase = true) ||
                                        errorMessage.contains("연결이 끊어", ignoreCase = true) ||
                                        errorMessage.contains("끊어짐", ignoreCase = true) ||
                                        errorMessage.contains("비어있음", ignoreCase = true)

                            if (isRecoverableError) {
                                Log.w(TAG, "일시적 오류 - 캐시 저장 없이 재시도 허용")
                                return@fold
                            }

                            // 영구적 실패(파일 없음 등)만 빈 데이터로 캐시하여 재시도 방지
                            synchronized(thumbnailCacheLock) {
                                _thumbnailCache.value =
                                    _thumbnailCache.value + (photo.path to ByteArray(0))
                            }
                        }
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(TAG, "썸네일 로딩 중 예외: ${photo.name}", exception)
                    if (isManagerActive) {
                        synchronized(thumbnailCacheLock) {
                            _thumbnailCache.value =
                                _thumbnailCache.value + (photo.path to ByteArray(0))
                        }
                    }
                } finally {
                    // 로딩 상태에서 제거 — 최신 값 기준으로 갱신
                    synchronized(thumbnailCacheLock) {
                        _loadingThumbnails.value = _loadingThumbnails.value - photo.path
                    }
                    synchronized(pendingLock) { queuedPaths.remove(photo.path) }
                }
            }

            Log.d(TAG, "썸네일 대기열 비움")
        }
    }

    /**
     * 고해상도 이미지 다운로드(fire-and-forget).
     *
     * @param persistToDevice true 면 다운로드 결과를 기기 저장소(MediaStore, DCIM/CamCon)까지
     *   영속화한다(사용자가 명시적으로 다운로드 버튼을 누른 경우). false(기본)는 표시용 캐시만
     *   채운다(풀스크린 진입·인접 프리로드). 결과는 [downloadResult] 로 통지된다.
     */
    fun downloadFullImage(
        photoPath: String,
        currentTier: SubscriptionTier,
        persistToDevice: Boolean = false
    ) {
        Log.d(TAG, "downloadFullImage 호출: ${LogMask.path(photoPath)} (저장=$persistToDevice)")
        managerScope.launch {
            val success = performDownload(photoPath, currentTier, persistToDevice)
            _downloadResult.tryEmit(DownloadResult(photoPath, isSuccess = success))
        }
    }

    /**
     * 다운로드 후 기기 저장까지 수행하고 저장 성공 여부를 반환하는 suspend 진입점(다중선택 배치용).
     *
     * 호출자(ViewModel)가 세마포어로 동시성을 제한한 채 순차/유계로 await 하여, 네이티브 커맨드
     * 큐의 직렬 처리 + JNI 60초 대기(제출 시점 기산)로 인한 후순위 구조적 타임아웃을 방지한다.
     *
     * @return 기기 저장까지 성공하면 true, 다운로드/게이팅/저장 중 실패면 false.
     */
    suspend fun downloadAndPersist(photoPath: String, currentTier: SubscriptionTier): Boolean =
        performDownload(photoPath, currentTier, persistToDevice = true)

    /**
     * 다운로드 + (요청 시) 기기 저장 실제 로직.
     *
     * @return persistToDevice=false 면 표시용 캐시 적재 성공 여부, true 면 기기 저장 성공 여부.
     */
    private suspend fun performDownload(
        photoPath: String,
        currentTier: SubscriptionTier,
        persistToDevice: Boolean
    ): Boolean {
        // 이미 표시용 바이트가 캐시에 있으면 재다운로드하지 않는다.
        _fullImageCache.value[photoPath]?.let { cached ->
            if (!persistToDevice) return true
            if (isAlreadyPersisted(photoPath)) return true
            // FREE 는 캐시본이 2000px 로 리사이즈됐다는 보장이 없으므로(프리로드 시 리사이즈 실패 가능)
            // 저장 전 재보장한다. 이미 작은 이미지면 복사로 빠르게 통과(멱등).
            var persistBytes = cached
            if (currentTier == SubscriptionTier.FREE) {
                if (!processImageForFreeTier(photoPath, cached)) {
                    Log.w(TAG, "FREE 리사이즈 실패(캐시) — 원본 저장 방지: ${LogMask.path(photoPath)}")
                    return false
                }
                persistBytes = _fullImageCache.value[photoPath] ?: cached
            }
            return persistBytesToDevice(photoPath, persistBytes, currentTier)
        }

        // 동일 path 중복 다운로드 방지.
        val proceed = synchronized(this) {
            if (_downloadingImages.value.contains(photoPath)) {
                false
            } else {
                _downloadingImages.value = _downloadingImages.value + photoPath
                true
            }
        }
        if (!proceed) {
            // 다른 코루틴이 같은 path 다운로드 중 — 표시 목적이면 캐시 적재 여부로 성공 판정.
            return _fullImageCache.value.containsKey(photoPath)
        }

        val startedAtMs = System.currentTimeMillis()
        try {
            val imageData = withContext(ioDispatcher) {
                downloadCameraPhotoUseCase(photoPath)
            }

            if (imageData == null || imageData.isEmpty()) {
                Log.e(TAG, "실제 파일 다운로드 실패: 데이터가 비어있음")
                return false
            }

            val (added, cacheSize) = synchronized(fullImageCacheLock) {
                if (_fullImageCache.value.containsKey(photoPath)) {
                    false to _fullImageCache.value.size
                } else {
                    // 바이트 예산 축출 적용(오래된 항목부터). RAW 다중선택 힙 폭주 방지.
                    val newSize = putFullImageLocked(photoPath, imageData)
                    true to newSize
                }
            }

            if (added) {
                Log.d(TAG, "실제 파일 다운로드 성공: ${imageData.size} bytes (캐시 크기: $cacheSize)")
                // EXIF 파싱
                if (!_exifCache.value.containsKey(photoPath)) {
                    parseExifFromImageData(photoPath, imageData)
                }
            }

            // Free 티어 표시 리사이즈(원본 캐시를 2000px 로 교체). 성공 여부는 저장 게이팅에 사용.
            var freeResizeOk = true
            if (currentTier == SubscriptionTier.FREE) {
                freeResizeOk = processImageForFreeTier(photoPath, imageData)
            }

            // 표시 목적(프리로드)이면 캐시 적재로 완료.
            if (!persistToDevice) {
                return true
            }

            // FREE 는 2000px 리사이즈가 성공해야만 저장한다(원본 저장은 게이팅 우회).
            if (currentTier == SubscriptionTier.FREE && !freeResizeOk) {
                Log.w(TAG, "FREE 리사이즈 실패 — 원본 저장 방지, 다운로드 실패 처리: ${LogMask.path(photoPath)}")
                return false
            }

            // 저장 바이트: FREE 는 리사이즈된 캐시본, 그 외는 원본.
            val persistBytes = _fullImageCache.value[photoPath] ?: imageData
            return persistBytesToDevice(photoPath, persistBytes, currentTier)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "실제 파일 다운로드 중 예외", e)
            return false
        } finally {
            _downloadingImages.value = _downloadingImages.value - photoPath
            val elapsedMs = System.currentTimeMillis() - startedAtMs
            Log.d(TAG, "다운로드 상태 정리 완료: ${LogMask.path(photoPath)} (${elapsedMs}ms)")
        }
    }

    /**
     * 다운로드 바이트를 기기 저장소(MediaStore, DCIM/CamCon)에 저장한다.
     *
     * 포맷/RAW 게이팅은 [ValidateImageFormatUseCase] 단일 지점 경유(CLAUDE.md §2). 자체 티어 분기
     * 신설 금지 — FREE 2000px 축소는 상류 [processImageForFreeTier] 에서 이미 적용된 바이트를 받는다.
     */
    private suspend fun persistBytesToDevice(
        photoPath: String,
        imageData: ByteArray,
        currentTier: SubscriptionTier
    ): Boolean {
        val fileName = photoPath.substringAfterLast("/")
        if (!isPersistAllowedByGating(fileName)) {
            Log.w(TAG, "저장 게이팅 차단 — 기기 저장 생략: $fileName (티어: $currentTier)")
            return false
        }
        val savedPath = galleryDownloadStore.save(photoPath, imageData)
        return if (savedPath != null) {
            markPersisted(photoPath)
            Log.d(TAG, "기기 저장 완료: ${LogMask.path(savedPath)}")
            true
        } else {
            Log.e(TAG, "기기 저장 실패(MediaStore): $fileName")
            false
        }
    }

    /**
     * 포맷/RAW 게이팅 단일 지점 방어([ValidateImageFormatUseCase.isDownloadAllowed]). RAW 는 상류
     * ViewModel 이 이미 차단하지만, 저장 진입부에서 다시 한 번 검증한다(방어선 일관성 — 데이터 레이어
     * [com.inik.camcon.data.repository.managers.PhotoDownloadManager] 의 게이팅과 동일 규칙).
     * 미지 확장자는 fail-closed(차단) — HEIF 등 미지 포맷 우회(C1) 봉합.
     */
    private suspend fun isPersistAllowedByGating(fileName: String): Boolean {
        return validateImageFormatUseCase.isDownloadAllowed(fileName)
    }

    /**
     * Free 티어 사용자를 위한 이미지 처리.
     *
     * @return 리사이즈(또는 이미 작은 이미지 복사)가 성공해 표시 캐시가 2000px 이하로 갱신됐으면
     *   true, 실패면 false. false 면 호출부는 FREE 원본 저장을 하지 않는다(게이팅 우회 방지).
     */
    private suspend fun processImageForFreeTier(photoPath: String, imageData: ByteArray): Boolean {
        val extension = File(photoPath).extension.lowercase()
        if (extension !in Constants.ImageProcessing.JPEG_EXTENSIONS) return false

        return withContext(ioDispatcher) {
            try {
                Log.d(TAG, "Free 티어 사용자 - 리사이징 처리 시작")

                val tempFile = File.createTempFile("temp_resize", ".jpg")
                tempFile.writeBytes(imageData)

                val resizedFile = File.createTempFile("temp_resized", ".jpg")

                val resizeSuccess = resizeImageForFreeTier(
                    tempFile.absolutePath,
                    resizedFile.absolutePath
                )

                val ok = if (resizeSuccess && resizedFile.exists()) {
                    val resizedData = resizedFile.readBytes()

                    // 캐시 업데이트 (리사이즈된 이미지로 교체 — 바이트 예산 축출 적용)
                    synchronized(fullImageCacheLock) {
                        putFullImageLocked(photoPath, resizedData)
                    }

                    Log.d(TAG, "Free 티어 리사이징 완료: ${resizedData.size} bytes")
                    true
                } else {
                    Log.w(TAG, "Free 티어 리사이징 실패, 원본 유지")
                    false
                }

                // 임시 파일 정리
                tempFile.delete()
                resizedFile.delete()
                ok
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Free 티어 리사이징 처리 중 오류", e)
                false
            }
        }
    }

    /**
     * Free 티어 이미지 리사이징
     */
    private suspend fun resizeImageForFreeTier(inputPath: String, outputPath: String): Boolean {
        return withContext(ioDispatcher) {
            try {
                Log.d(TAG, "Free 티어 이미지 리사이즈 시작: ${LogMask.path(inputPath)}")

                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(inputPath, options)

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight
                val maxDimension = kotlin.math.max(originalWidth, originalHeight)

                if (maxDimension <= Constants.ImageProcessing.FREE_TIER_MAX_DIMENSION) {
                    Log.d(TAG, "이미 작은 이미지 - 리사이즈 불필요")
                    return@withContext File(inputPath).copyTo(File(outputPath), overwrite = true)
                        .exists()
                }

                val scale =
                    Constants.ImageProcessing.FREE_TIER_MAX_DIMENSION.toFloat() / maxDimension.toFloat()
                val newWidth = (originalWidth * scale).toInt()
                val newHeight = (originalHeight * scale).toInt()

                val sampleSize =
                    calculateInSampleSize(originalWidth, originalHeight, newWidth, newHeight)

                options.apply {
                    inJustDecodeBounds = false
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }

                val bitmap = android.graphics.BitmapFactory.decodeFile(inputPath, options)
                    ?: return@withContext false

                try {
                    val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        newWidth,
                        newHeight,
                        true
                    )

                    val originalExif = ExifInterface(inputPath)
                    val orientation = originalExif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )

                    val rotatedBitmap = rotateImageIfRequired(resizedBitmap, orientation)

                    java.io.FileOutputStream(outputPath).use { out ->
                        rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    if (resizedBitmap != rotatedBitmap) {
                        resizedBitmap.recycle()
                    }
                    rotatedBitmap.recycle()

                    copyAllExifData(inputPath, outputPath, newWidth, newHeight)

                    Log.d(TAG, "Free 티어 리사이즈 완료 (EXIF 보존)")
                    true
                } finally {
                    bitmap.recycle()
                }

            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "메모리 부족으로 리사이즈 실패", e)
                System.gc()
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "이미지 리사이즈 실패", e)
                false
            }
        }
    }

    /**
     * EXIF 정보 파싱
     */
    private fun parseExifFromImageData(photoPath: String, imageData: ByteArray) {
        managerScope.launch {
            try {
                Log.d(TAG, "EXIF 파싱 시작: ${LogMask.path(photoPath)}")

                val tempFile = File.createTempFile("temp_exif", ".jpg")
                tempFile.writeBytes(imageData)

                try {
                    val exif = ExifInterface(tempFile.absolutePath)
                    val exifMap = mutableMapOf<String, Any>()

                    // 기본 정보
                    val basicInfo = getCameraPhotoExifJsonUseCase(photoPath)
                    basicInfo?.let { basic ->
                        try {
                            val basicJson = JSONObject(basic)
                            if (basicJson.has("width")) {
                                exifMap["width"] = basicJson.getInt("width")
                            }
                            if (basicJson.has("height")) {
                                exifMap["height"] = basicJson.getInt("height")
                            }else{

                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "기본 정보 파싱 실패", e)
                        }
                    }

                    // 카메라 정보
                    exif.getAttribute(ExifInterface.TAG_MAKE)?.let { exifMap["make"] = it }
                    exif.getAttribute(ExifInterface.TAG_MODEL)?.let { exifMap["model"] = it }
                    exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { exifMap["f_number"] = it }
                    exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                        ?.let { exifMap["exposure_time"] = it }
                    exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                        ?.let { exifMap["focal_length"] = it }
                    exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                        ?.let { exifMap["iso"] = it }
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?.let { exifMap["date_time_original"] = it }

                    // GPS 정보
                    val latLong = FloatArray(2)
                    if (exif.getLatLong(latLong)) {
                        exifMap["gps_latitude"] = latLong[0]
                        exifMap["gps_longitude"] = latLong[1]
                    }

                    val jsonObject = JSONObject()
                    exifMap.forEach { (key, value) ->
                        jsonObject.put(key, value)
                    }

                    val exifJson = jsonObject.toString()
                    Log.d(TAG, "EXIF 파싱 완료: ${exifMap.size}개 태그")

                    _exifCache.value = _exifCache.value + (photoPath to exifJson)

                } finally {
                    tempFile.delete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "EXIF 파싱 실패", e)
            }
        }
    }

    /**
     * 이미지에서 썸네일 가져오기
     */
    fun getThumbnail(photoPath: String): ByteArray? {
        return _thumbnailCache.value[photoPath]
    }

    /**
     * 고해상도 이미지 가져오기
     */
    fun getFullImage(photoPath: String): ByteArray? {
        return _fullImageCache.value[photoPath]
    }

    /**
     * 다운로드 상태 확인
     */
    fun isDownloadingFullImage(photoPath: String): Boolean {
        return _downloadingImages.value.contains(photoPath)
    }

    /**
     * EXIF 정보 가져오기
     */
    fun getCameraPhotoExif(photoPath: String): String? {
        return _exifCache.value[photoPath]
    }

    /**
     * 유틸리티 메서드들
     */
    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rotateImageIfRequired(
        bitmap: android.graphics.Bitmap,
        orientation: Int
    ): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }

        return try {
            val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "이미지 회전 중 메모리 부족", e)
            bitmap
        }
    }

    private fun copyAllExifData(
        originalPath: String,
        newPath: String,
        newWidth: Int,
        newHeight: Int
    ) {
        try {
            val originalExif = ExifInterface(originalPath)
            val newExif = ExifInterface(newPath)

            val tagsToPreserve = arrayOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_ORIENTATION
            )

            var copiedCount = 0
            for (tag in tagsToPreserve) {
                val value = originalExif.getAttribute(tag)
                if (value != null) {
                    newExif.setAttribute(tag, value)
                    copiedCount++
                }
            }

            newExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, newWidth.toString())
            newExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, newHeight.toString())
            newExif.setAttribute(ExifInterface.TAG_SOFTWARE, "CamCon (Free Tier Resize)")

            newExif.saveAttributes()
            Log.d(TAG, "EXIF 정보 복사 완료: ${copiedCount}개 태그 복사됨")

        } catch (e: Exception) {
            Log.e(TAG, "EXIF 정보 복사 실패", e)
        }
    }

    /**
     * 정리
     */
    fun cleanup() {
        // 진행 중 작업 취소 → scope 재생성 후 즉시 재활성화하여
        // @Singleton 재진입(미리보기 재진입) 시 로딩이 영구 차단되지 않도록 한다.(F19/F27)
        managerScope.coroutineContext.job.cancel()
        managerScope = createManagerScope()
        isManagerActive = true
        _thumbnailCache.value = emptyMap()
        _fullImageCache.value = emptyMap()
        _downloadingImages.value = emptySet()
        _loadingThumbnails.value = emptySet()
        _exifCache.value = emptyMap()
        synchronized(persistedPathsLock) { persistedPaths.clear() }
    }
}