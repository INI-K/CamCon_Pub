package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.camera.GetCameraPhotosPagedUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraThumbnailUseCase
import com.inik.camcon.domain.usecase.camera.PhotoCaptureEventManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PhotoPreviewUiState(
    val photos: List<CameraPhoto> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val selectedPhoto: CameraPhoto? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
    val thumbnailCache: Map<String, ByteArray> = emptyMap(),
    val isConnected: Boolean = false,
    val isInitialized: Boolean = false,
    val fileTypeFilter: FileTypeFilter = FileTypeFilter.JPG,
    val allPhotos: List<CameraPhoto> = emptyList()
)

enum class FileTypeFilter {
    ALL,
    JPG,
    RAW
}

@HiltViewModel
class PhotoPreviewViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val getCameraPhotosPagedUseCase: GetCameraPhotosPagedUseCase,
    private val getCameraThumbnailUseCase: GetCameraThumbnailUseCase,
    private val photoCaptureEventManager: PhotoCaptureEventManager,
    private val globalManager: CameraConnectionGlobalManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoPreviewUiState())
    val uiState: StateFlow<PhotoPreviewUiState> = _uiState.asStateFlow()

    // 실제 파일 다운로드 캐시 (고해상도 이미지용)
    private val _fullImageCache = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val fullImageCache: StateFlow<Map<String, ByteArray>> = _fullImageCache.asStateFlow()

    // 실제 파일 다운로드 상태 관리
    private val _downloadingImages = MutableStateFlow<Set<String>>(emptySet())
    val downloadingImages: StateFlow<Set<String>> = _downloadingImages.asStateFlow()

    // 프리로딩 상태 추적
    private val _prefetchedPage = MutableStateFlow(0)

    companion object {
        private const val TAG = "PhotoPreviewViewModel"
        private const val PREFETCH_PAGE_SIZE = 50
    }

    init {
        android.util.Log.d(TAG, "=== PhotoPreviewViewModel 초기화 시작 ===")
        loadInitialPhotos()
        observePhotoCaptureEvents()
        observeCameraConnection()
        android.util.Log.d(TAG, "=== PhotoPreviewViewModel 초기화 완료 ===")
    }

    private fun observeCameraConnection() {
        android.util.Log.d(TAG, "=== observeCameraConnection 시작 ===")
        viewModelScope.launch {
            globalManager.globalConnectionState.collect { connectionState ->
                val isConnected = connectionState.isAnyConnectionActive
                android.util.Log.d(TAG, "전역 카메라 연결 상태 변경: $isConnected")
                android.util.Log.d(TAG, "  - USB 연결: ${connectionState.isUsbConnected}")
                android.util.Log.d(TAG, "  - PTPIP 연결: ${connectionState.ptpipConnectionState}")
                android.util.Log.d(TAG, "  - 활성 연결 타입: ${connectionState.activeConnectionType}")

                val previousConnected = _uiState.value.isConnected
                _uiState.value = _uiState.value.copy(isConnected = isConnected)

                if (isConnected && !previousConnected) {
                    android.util.Log.d(TAG, "카메라 연결됨 - 자동으로 사진 목록 불러오기")
                    loadInitialPhotos()
                } else if (!isConnected && previousConnected) {
                    android.util.Log.d(TAG, "카메라 연결 해제됨 - 에러 상태 설정")
                    _uiState.value = _uiState.value.copy(
                        error = "카메라 연결이 해제되었습니다",
                        isInitialized = false
                    )
                }
            }
        }
    }

    private suspend fun checkCameraInitialization() {
        // 카메라 초기화 상태 확인 (Repository에 함수 추가 필요)
        // 임시로 연결 상태와 동일하게 처리
        _uiState.value = _uiState.value.copy(isInitialized = _uiState.value.isConnected)
    }

    private fun observePhotoCaptureEvents() {
        // 사진 촬영 이벤트 자동 새로고침 비활성화
        // (카메라 제어 화면에서 이벤트 리스너가 중지되는 문제 방지)
        /*
        photoCaptureEventManager.photoCaptureEvent
            .onEach {
                // 사진이 촬영되면 목록을 자동으로 새로고침
                loadInitialPhotos()
            }
            .launchIn(viewModelScope)
        */

        // 수동 새로고침만 허용하도록 변경
        android.util.Log.d("PhotoPreviewViewModel", "사진 촬영 이벤트 자동 새로고침 비활성화 - 수동 새로고침만 허용")
    }

    fun loadInitialPhotos() {
        android.util.Log.d(TAG, "=== loadInitialPhotos 호출 ===")
        viewModelScope.launch {
            android.util.Log.d(TAG, "loadInitialPhotos 코루틴 시작")
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                currentPage = 0,
                allPhotos = emptyList()
            )
            android.util.Log.d(TAG, "UI 상태 업데이트: isLoading=true")

            // 카메라 연결 상태 확인
            val isConnected = _uiState.value.isConnected
            android.util.Log.d(TAG, "현재 카메라 연결 상태: $isConnected")

            if (!isConnected) {
                android.util.Log.w(TAG, "카메라가 연결되지 않음 - 에러 상태로 설정")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "카메라가 연결되지 않았습니다. 카메라를 연결해주세요.",
                    photos = emptyList()
                )
                return@launch
            }

            android.util.Log.d(TAG, "getCameraPhotosPagedUseCase 호출 시작")
            getCameraPhotosPagedUseCase(page = 0, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    android.util.Log.d(TAG, "사진 목록 불러오기 성공: ${paginatedPhotos.photos.size}개")
                    _uiState.value = _uiState.value.copy(
                        allPhotos = paginatedPhotos.photos,
                        photos = filterPhotos(
                            paginatedPhotos.photos,
                            _uiState.value.fileTypeFilter
                        ),
                        isLoading = false,
                        currentPage = paginatedPhotos.currentPage,
                        totalPages = paginatedPhotos.totalPages,
                        hasNextPage = paginatedPhotos.hasNext
                    )

                    // 첫 번째 페이지의 썸네일 로드 시작
                    android.util.Log.d(TAG, "썸네일 로드 시작")
                    loadThumbnailsForCurrentPage()
                },
                onFailure = { exception ->
                    android.util.Log.e(TAG, "사진 목록 불러오기 실패", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "사진을 불러오는데 실패했습니다"
                    )
                }
            )
            android.util.Log.d(TAG, "loadInitialPhotos 코루틴 완료")
        }
    }

    fun loadNextPage() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasNextPage) {
            android.util.Log.d(
                TAG,
                "loadNextPage 건너뛰기: isLoadingMore=${_uiState.value.isLoadingMore}, hasNextPage=${_uiState.value.hasNextPage}"
            )
            return
        }

        android.util.Log.d(TAG, "=== loadNextPage 시작 ===")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            android.util.Log.d(TAG, "isLoadingMore = true 설정됨")

            val nextPage = _uiState.value.currentPage + 1
            getCameraPhotosPagedUseCase(page = nextPage, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    android.util.Log.d(TAG, "loadNextPage 성공: ${paginatedPhotos.photos.size}개 추가")
                    val currentPhotos = _uiState.value.allPhotos
                    val newPhotos = currentPhotos + paginatedPhotos.photos

                    _uiState.value = _uiState.value.copy(
                        allPhotos = newPhotos,
                        photos = filterPhotos(newPhotos, _uiState.value.fileTypeFilter),
                        isLoadingMore = false,
                        currentPage = paginatedPhotos.currentPage,
                        totalPages = paginatedPhotos.totalPages,
                        hasNextPage = paginatedPhotos.hasNext
                    )
                    android.util.Log.d(TAG, "isLoadingMore = false 설정됨")

                    // 새로 로드된 사진들의 썸네일 로드
                    loadThumbnailsForNewPhotos(paginatedPhotos.photos)
                },
                onFailure = { exception ->
                    android.util.Log.e(TAG, "loadNextPage 실패", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = exception.message ?: "추가 사진을 불러오는데 실패했습니다"
                    )
                    android.util.Log.d(TAG, "isLoadingMore = false 설정됨 (실패)")
                }
            )
        }
    }

    /**
     * 테스트용: 강제로 다음 페이지 로드 (로딩 인디케이터 테스트)
     */
    fun forceLoadNextPage() {
        android.util.Log.d(TAG, "🧪 강제 로딩 테스트 시작")
        loadNextPage()
    }

    fun loadCameraPhotos() {
        loadInitialPhotos()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun downloadPhoto(photo: CameraPhoto) {
        viewModelScope.launch {
            try {
                cameraRepository.downloadPhotoFromCamera(photo.path)
                    .onSuccess {
                        // 다운로드 성공
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            error = error.message
                        )
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message
                )
            }
        }
    }

    private fun loadThumbnailsForCurrentPage() {
        viewModelScope.launch {
            val photos = _uiState.value.photos
            loadThumbnailsForNewPhotos(photos)
        }
    }

    private fun loadThumbnailsForNewPhotos(photos: List<CameraPhoto>) {
        viewModelScope.launch {
            val currentCache = _uiState.value.thumbnailCache.toMutableMap()

            photos.forEach { photo ->
                if (!currentCache.containsKey(photo.path)) {
                    // RAW 파일인지 확인
                    val isRawFile = photo.path.endsWith(".nef", true) ||
                            photo.path.endsWith(".cr2", true) ||
                            photo.path.endsWith(".arw", true) ||
                            photo.path.endsWith(".dng", true)

                    // 모든 파일에 대해 썸네일 가져오기 시도 (RAW, JPG 구분 없이)
                    getCameraThumbnailUseCase(photo.path).fold(
                        onSuccess = { thumbnailData ->
                            currentCache[photo.path] = thumbnailData
                            _uiState.value = _uiState.value.copy(
                                thumbnailCache = currentCache.toMap()
                            )
                            android.util.Log.d(
                                TAG,
                                "썸네일 로드 성공: ${photo.name} (${thumbnailData.size} bytes)"
                            )
                        },
                        onFailure = { exception ->
                            android.util.Log.w(TAG, "썸네일 로드 실패: ${photo.path}", exception)

                            // RAW 파일의 경우 더 긴 지연 후 재시도
                            val retryDelay = if (isRawFile) 800L else 300L

                            viewModelScope.launch {
                                delay(retryDelay)
                                getCameraThumbnailUseCase(photo.path).fold(
                                    onSuccess = { retryThumbnailData ->
                                        currentCache[photo.path] = retryThumbnailData
                                        _uiState.value = _uiState.value.copy(
                                            thumbnailCache = currentCache.toMap()
                                        )
                                        android.util.Log.d(
                                            TAG,
                                            "썸네일 재시도 성공: ${photo.name} (${retryThumbnailData.size} bytes)"
                                        )
                                    },
                                    onFailure = { retryException ->
                                        android.util.Log.e(
                                            TAG,
                                            "썸네일 재시도 실패: ${photo.path}",
                                            retryException
                                        )

                                        // 재시도도 실패하면 빈 ByteArray로 캐시에 추가 (무한 재시도 방지)
                                        currentCache[photo.path] = ByteArray(0)
                                        _uiState.value = _uiState.value.copy(
                                            thumbnailCache = currentCache.toMap()
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * 전체화면 뷰어용 실제 파일 다운로드
     */
    fun downloadFullImage(photoPath: String) {
        android.util.Log.d(TAG, "=== downloadFullImage 호출: $photoPath ===")

        // 이미 캐시에 있는지 확인
        if (_fullImageCache.value.containsKey(photoPath)) {
            android.util.Log.d(TAG, "이미 캐시에 있음, 다운로드 생략")
            return
        }

        // 이미 다운로드 중인지 확인 (동시성 안전)
        synchronized(this) {
            if (_downloadingImages.value.contains(photoPath)) {
                android.util.Log.d(TAG, "이미 다운로드 중, 중복 요청 무시")
                return
            }
            // 다운로드 중 상태로 즉시 설정
            _downloadingImages.value = _downloadingImages.value + photoPath
        }

        viewModelScope.launch {
            try {
                android.util.Log.d(TAG, "실제 파일 다운로드 시작: $photoPath")

                // 네이티브 코드에서 직접 다운로드 (Main 스레드에서 실행 방지)
                val imageData = withContext(Dispatchers.IO) {
                    android.util.Log.d(TAG, "downloadCameraPhoto 호출")
                    val folderPath = photoPath.substringBeforeLast("/")
                    val fileName = photoPath.substringAfterLast("/")
                    android.util.Log.d(TAG, "이미지 다운로드: 폴더=$folderPath, 파일=$fileName")
                    
                    com.inik.camcon.NativeCall(photoPath)
                }

                if (imageData != null && imageData.isNotEmpty()) {
                    // 캐시 업데이트를 한 번만 수행 (기존 캐시 전체를 복사하지 않고 효율적으로 처리)
                    val currentCache = _fullImageCache.value
                    if (!currentCache.containsKey(photoPath)) {
                        val newCache = currentCache + (photoPath to imageData)
                        _fullImageCache.value = newCache
                        
                        android.util.Log.d(TAG, "이미지 데이터 반환: ${imageData.size} 바이트")
                        android.util.Log.d(TAG, "실제 파일 다운로드 성공: ${imageData.size} bytes")
                        
                        // 캐시 업데이트 로그 출력 (디버깅용)
                        android.util.Log.d(TAG, "=== 고화질 캐시 업데이트 ===")
                        android.util.Log.d(
                            TAG,
                            "이전 캐시 크기: ${currentCache.size}, 새 캐시 크기: ${newCache.size}"
                        )
                        android.util.Log.d(
                            TAG,
                            "캐시된 사진들: ${newCache.keys.map { it.substringAfterLast("/") }}"
                        )

                        // 캐시 업데이트 후 StateFlow를 한 번 더 업데이트하여 UI 반응성 보장
                        delay(50)
                        if (_fullImageCache.value == newCache) {
                            // StateFlow 변경 감지를 위해 새 Map 인스턴스 생성
                            _fullImageCache.value = newCache.toMap()
                            android.util.Log.d(
                                TAG,
                                "🔄 StateFlow 강제 업데이트 완료: ${photoPath.substringAfterLast("/")}"
                            )
                        }
                    } else {
                        android.util.Log.d(
                            TAG,
                            "이미 캐시에 존재하여 중복 추가 방지: ${photoPath.substringAfterLast("/")}"
                        )
                    }
                } else {
                    android.util.Log.e(TAG, "실제 파일 다운로드 실패: 데이터가 비어있음")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "실제 파일 다운로드 중 예외", e)
            } finally {
                // 다운로드 완료 후 상태에서 제거
                _downloadingImages.value = _downloadingImages.value - photoPath
                android.util.Log.d(TAG, "다운로드 상태 정리 완료: $photoPath")
            }
        }
    }

    /**
     * 전체화면 뷰어용 실제 파일 데이터 가져오기
     */
    fun getFullImage(photoPath: String): ByteArray? {
        return _fullImageCache.value[photoPath]
    }

    /**
     * 특정 사진이 다운로드 중인지 확인
     */
    fun isDownloadingFullImage(photoPath: String): Boolean {
        return _downloadingImages.value.contains(photoPath)
    }

    fun getThumbnail(photoPath: String): ByteArray? {
        return _uiState.value.thumbnailCache[photoPath]
    }

    fun selectPhoto(photo: CameraPhoto?) {
        _uiState.value = _uiState.value.copy(selectedPhoto = photo)
    }

    /**
     * 선택된 사진과 인접 사진들을 미리 다운로드하는 함수 (성능 최적화)
     * UI 스레드를 차단하지 않고 백그라운드에서 실행
     */
    fun preloadAdjacentImages(selectedPhoto: CameraPhoto, photos: List<CameraPhoto>) {
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d(TAG, "=== preloadAdjacentImages 시작: ${selectedPhoto.name} ===")

            // 먼저 선택된 사진 다운로드 (우선순위) - 이미 있거나 다운로드 중이면 건너뛰기
            val hasSelectedPhoto = _fullImageCache.value.containsKey(selectedPhoto.path)
            val isDownloadingSelected = _downloadingImages.value.contains(selectedPhoto.path)

            if (!hasSelectedPhoto && !isDownloadingSelected) {
                downloadFullImage(selectedPhoto.path)
                // 선택된 사진 다운로드 완료 대기 (최대 2초)
                var waitCount = 0
                while (!_fullImageCache.value.containsKey(selectedPhoto.path) &&
                    _downloadingImages.value.contains(selectedPhoto.path) &&
                    waitCount < 20
                ) {
                    delay(100)
                    waitCount++
                }
            } else {
                android.util.Log.d(TAG, "선택된 사진 다운로드 건너뛰기 (이미 처리됨): ${selectedPhoto.name}")
            }

            // 200ms 지연 후 인접 사진 다운로드 (슬라이딩 성능 보호)
            delay(200)

            // 인접 사진 찾기 (범위 축소: 앞뒤 1장씩만)
            val currentIndex = photos.indexOfFirst { it.path == selectedPhoto.path }
            if (currentIndex == -1) {
                android.util.Log.w(TAG, "선택된 사진을 목록에서 찾을 수 없음: ${selectedPhoto.name}")
                return@launch
            }

            val adjacentIndices = listOf(currentIndex - 1, currentIndex + 1)
                .filter { it in photos.indices }

            android.util.Log.d(TAG, "인접 사진 인덱스: $adjacentIndices")

            // 인접 사진들 순차적으로 다운로드 (이미 캐시에 있거나 다운로드 중이면 건너뛰기)
            adjacentIndices.forEach { index ->
                val adjacentPhoto = photos[index]
                val hasAdjacent = _fullImageCache.value.containsKey(adjacentPhoto.path)
                val isDownloadingAdjacent = _downloadingImages.value.contains(adjacentPhoto.path)

                if (!hasAdjacent && !isDownloadingAdjacent) {
                    android.util.Log.d(TAG, "인접 사진 다운로드: ${adjacentPhoto.name}")
                    downloadFullImage(adjacentPhoto.path)

                    // 각 다운로드 간 짧은 지연 (시스템 부하 방지)
                    delay(100)
                } else {
                    android.util.Log.d(TAG, "인접 사진 다운로드 건너뛰기 (이미 처리됨): ${adjacentPhoto.name}")
                }
            }

            android.util.Log.d(TAG, "preloadAdjacentImages 완료")
        }
    }

    /**
     * 빠른 미리 로딩 - 현재 사진만 우선 다운로드 (슬라이딩 성능 우선)
     */
    fun quickPreloadCurrentImage(selectedPhoto: CameraPhoto) {
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d(TAG, "빠른 다운로드: ${selectedPhoto.name}")
            downloadFullImage(selectedPhoto.path)
        }
    }

    /**
     * 사진의 EXIF 정보를 가져오는 함수
     */
    fun getCameraPhotoExif(photoPath: String): String? {
        return try {
            com.inik.camcon.NativeCall(photoPath)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "EXIF 정보 가져오기 실패: $photoPath", e)
            null
        }
    }

    /**
     * 프리로딩: 사용자가 특정 인덱스에 도달했을 때 호출
     * 필터링된 사진 수에 따라 동적으로 임계값 조정 (엄격한 조건)
     */
    fun onPhotoIndexReached(currentIndex: Int) {
        val filteredPhotos = _uiState.value.photos
        val totalFilteredPhotos = filteredPhotos.size
        val currentPage = _uiState.value.currentPage

        // 매우 엄격한 동적 임계값 계산: 더 높은 임계값 적용
        val dynamicThreshold = when {
            totalFilteredPhotos <= 20 -> totalFilteredPhotos - 3  // 끝에서 3개 이전
            totalFilteredPhotos <= 50 -> (totalFilteredPhotos * 0.8).toInt()  // 80% 지점
            else -> (totalFilteredPhotos * 0.85).toInt().coerceAtLeast(40)  // 85% 지점, 최소 40개
        }

        // 더 엄격한 조건들 추가
        val shouldPrefetch = currentIndex >= dynamicThreshold &&
                !_uiState.value.isLoadingMore &&
                _uiState.value.hasNextPage &&
                _prefetchedPage.value <= currentPage && // 아직 프리로드하지 않은 페이지만
                currentIndex >= totalFilteredPhotos - 5 && // 끝에서 5개 이내에서만
                totalFilteredPhotos >= 20 // 최소 20개 이상일 때만

        android.util.Log.d(
            TAG, """
            프리로딩 체크 (엄격한 조건):
            - 현재 인덱스: $currentIndex
            - 필터링된 사진 수: $totalFilteredPhotos
            - 동적 임계값: $dynamicThreshold
            - 끝에서 5개 이내: ${currentIndex >= totalFilteredPhotos - 5}
            - 최소 20개 조건: ${totalFilteredPhotos >= 20}
            - 프리로드 조건 만족: $shouldPrefetch
            - hasNextPage: ${_uiState.value.hasNextPage}
            - isLoadingMore: ${_uiState.value.isLoadingMore}
            - prefetchedPage: ${_prefetchedPage.value} vs currentPage: $currentPage
        """.trimIndent()
        )

        if (shouldPrefetch) {
            android.util.Log.d(TAG, "🚀 프리로드 트리거: 현재 인덱스 $currentIndex, 동적 임계값 $dynamicThreshold 도달")
            prefetchNextPage()
            _prefetchedPage.value = currentPage + 1
        } else {
            android.util.Log.v(TAG, "프리로드 조건 미만족 - 대기 중")
        }
    }

    /**
     * 백그라운드에서 다음 페이지를 미리 로드 (사용자가 느끼지 못하게)
     */
    private fun prefetchNextPage() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasNextPage) {
            android.util.Log.d(
                TAG,
                "프리로드 건너뛰기: isLoadingMore=${_uiState.value.isLoadingMore}, hasNextPage=${_uiState.value.hasNextPage}"
            )
            return
        }

        android.util.Log.d(TAG, "=== prefetchNextPage 시작 ===")
        viewModelScope.launch {
            android.util.Log.d(TAG, "백그라운드 프리로드 시작")
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            android.util.Log.d(TAG, "prefetch isLoadingMore = true 설정됨")

            val nextPage = _uiState.value.currentPage + 1
            getCameraPhotosPagedUseCase(page = nextPage, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    val currentPhotos = _uiState.value.allPhotos
                    val newPhotos = currentPhotos + paginatedPhotos.photos

                    _uiState.value = _uiState.value.copy(
                        allPhotos = newPhotos,
                        photos = filterPhotos(newPhotos, _uiState.value.fileTypeFilter),
                        isLoadingMore = false,
                        currentPage = paginatedPhotos.currentPage,
                        totalPages = paginatedPhotos.totalPages,
                        hasNextPage = paginatedPhotos.hasNext
                    )

                    android.util.Log.d(
                        TAG,
                        "백그라운드 프리로드 완료: 추가된 사진 ${paginatedPhotos.photos.size}개, 총 ${newPhotos.size}개"
                    )
                    android.util.Log.d(TAG, "prefetch isLoadingMore = false 설정됨")

                    // 새로 로드된 사진들의 썸네일을 백그라운드에서 로드
                    loadThumbnailsForNewPhotos(paginatedPhotos.photos)
                },
                onFailure = { exception ->
                    android.util.Log.e(TAG, "백그라운드 프리로드 실패", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = exception.message ?: "추가 사진을 불러오는데 실패했습니다"
                    )
                    android.util.Log.d(TAG, "prefetch isLoadingMore = false 설정됨 (실패)")
                }
            )
        }
    }

    private fun filterPhotos(
        photos: List<CameraPhoto>,
        filter: FileTypeFilter = _uiState.value.fileTypeFilter
    ): List<CameraPhoto> {
        android.util.Log.d(TAG, "filterPhotos 호출: 필터=$filter, 전체사진=${photos.size}개")

        val filtered = when (filter) {
            FileTypeFilter.ALL -> photos
            FileTypeFilter.JPG -> photos.filter {
                val isJpg = it.path.endsWith(".jpg", true) || it.path.endsWith(".jpeg", true)
                android.util.Log.v(TAG, "JPG 필터 확인: ${it.path} -> $isJpg")
                isJpg
            }
            FileTypeFilter.RAW -> photos.filter {
                val isRaw = it.path.endsWith(".arw", true) ||
                        it.path.endsWith(".cr2", true) ||
                        it.path.endsWith(".nef", true) ||
                        it.path.endsWith(".dng", true)
                android.util.Log.v(TAG, "RAW 필터 확인: ${it.path} -> $isRaw")
                isRaw
            }
        }

        android.util.Log.d(TAG, "필터링 결과: ${filtered.size}개")
        return filtered
    }

    /**
     * 파일 타입 필터 변경
     */
    fun changeFileTypeFilter(filter: FileTypeFilter) {
        android.util.Log.d(TAG, "파일 타입 필터 변경: ${_uiState.value.fileTypeFilter} -> $filter")

        val filteredPhotos = filterPhotos(_uiState.value.allPhotos, filter)

        _uiState.value = _uiState.value.copy(
            fileTypeFilter = filter,
            photos = filteredPhotos,
            // 필터 변경 시 프리로딩 관련 상태는 유지 (hasNextPage는 전체 데이터 기준)
        )

        // 프리로딩 페이지 리셋 - 새 필터에서 다시 프리로딩 가능하도록
        _prefetchedPage.value = _uiState.value.currentPage

        android.util.Log.d(
            TAG,
            "필터링 완료: 전체 ${_uiState.value.allPhotos.size}개 -> 필터링된 ${filteredPhotos.size}개, hasNextPage: ${_uiState.value.hasNextPage}"
        )

        // 필터 변경 시 새로 필터링된 사진들의 썸네일 로드
        android.util.Log.d(TAG, "필터 변경으로 인한 썸네일 재로드 시작")
        loadThumbnailsForNewPhotos(filteredPhotos)
    }
}