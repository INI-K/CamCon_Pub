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
    val isInitializing: Boolean = false, // 카메라 초기화 상태 추가
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

    // EXIF 정보 캐시 추가
    private val _exifCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val exifCache: StateFlow<Map<String, String>> = _exifCache.asStateFlow()

    // 프리로딩 상태 추적
    private val _prefetchedPage = MutableStateFlow(0)

    companion object {
        private const val TAG = "PhotoPreviewViewModel"
        private const val PREFETCH_PAGE_SIZE = 50
    }

    // 작업 취소를 위한 플래그 추가
    private var isViewModelActive = true

    init {
        android.util.Log.d(TAG, "=== PhotoPreviewViewModel 초기화 시작 ===")

        // 1. 동기 초기화 (즉시 필요한 것들만)
        _uiState.value = _uiState.value.copy(isInitializing = true)

        // 2. 사진 미리보기 탭 진입 시 이벤트 리스너 즉시 중단
        viewModelScope.launch {
            try {
                android.util.Log.d(TAG, "📸 사진 미리보기 탭 진입 - 이벤트 리스너 즉시 중단")

                // ★★★ 사진 미리보기 모드 활성화 (자동 시작 방지)
                cameraRepository.setPhotoPreviewMode(true)

                // **이벤트 리스너만 중단 (글로벌 작업 중단 제거)**
                cameraRepository.stopCameraEventListener()
                android.util.Log.d(TAG, "✓ 이벤트 리스너 중단 완료")

                // 카메라 연결 상태 관찰 시작은 별도 launch 블록에서 실행
                // 중복 호출 방지: 아래 launch 블록에서 이미 호출됨
                // observeCameraConnection()
                // android.util.Log.d(TAG, "=== observeCameraConnection 시작 ===")
            } catch (e: Exception) {
                android.util.Log.w(TAG, "이벤트 리스너 중단 실패 (무시하고 계속)", e)
            }
        }

        // 3. 리스너들 설정 (백그라운드에서)
        viewModelScope.launch {
            launch { observeCameraConnection() }
            launch { observeCameraInitialization() }
            launch { observePhotoCaptureEvents() }
        }

        // 4. 중복 호출 방지: observeCameraConnection()에서 이미 처리함
        // viewModelScope.launch {
        //     globalManager.globalConnectionState.collect { connectionState ->
        //         if (connectionState.isAnyConnectionActive && _uiState.value.photos.isEmpty() && !loadedInitialPhotos) {
        //             android.util.Log.d(TAG, "카메라 연결 확인됨 - 사진 목록 로딩 시작")
        //             loadInitialPhotos()
        //             loadedInitialPhotos = true
        //         }
        //     }
        // }

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
                    if (_uiState.value.photos.isEmpty()) {
                        loadInitialPhotos()
                    }
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

    private fun observeCameraInitialization() {
        viewModelScope.launch {
            cameraRepository.isInitializing().collect { isInitializing ->
                _uiState.value = _uiState.value.copy(isInitializing = isInitializing)
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

            // 즉시 중단 체크
            if (!isViewModelActive) {
                android.util.Log.d(TAG, "⛔ loadInitialPhotos 작업 중단됨 (ViewModel 비활성)")
                return@launch
            }

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

            // 작업 중단 체크
            if (!isViewModelActive) {
                android.util.Log.d(TAG, "⛔ loadInitialPhotos 중단됨 (카메라 확인 후)")
                return@launch
            }

            android.util.Log.d(TAG, "getCameraPhotosPagedUseCase 호출 시작")
            getCameraPhotosPagedUseCase(page = 0, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    // 작업 중단 체크
                    if (!isViewModelActive) {
                        android.util.Log.d(TAG, "⛔ loadInitialPhotos 중단됨 (사진 목록 로딩 후)")
                        return@launch
                    }

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

                    // 작업 중단 체크 후 썸네일 로드
                    if (isViewModelActive) {
                        android.util.Log.d(TAG, "썸네일 로드 시작")
                        loadThumbnailsForCurrentPage()
                    } else {
                        android.util.Log.d(TAG, "⛔ 썸네일 로드 중단됨")
                    }
                },
                onFailure = { exception ->
                    if (isViewModelActive) {
                        android.util.Log.e(TAG, "사진 목록 불러오기 실패", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: "사진을 불러오는데 실패했습니다"
                        )
                    } else {
                        android.util.Log.d(TAG, "⛔ 사진 목록 로딩 실패 처리 중단됨")
                    }
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

        // 즉시 중단 체크
        if (!isViewModelActive) {
            android.util.Log.d(TAG, "⛔ loadNextPage 작업 중단됨 (ViewModel 비활성)")
            return
        }

        android.util.Log.d(TAG, "=== loadNextPage 시작 ===")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            android.util.Log.d(TAG, "isLoadingMore = true 설정됨")

            // 중단 체크
            if (!isViewModelActive) {
                android.util.Log.d(TAG, "⛔ loadNextPage 중단됨 (시작 후)")
                return@launch
            }

            val nextPage = _uiState.value.currentPage + 1
            getCameraPhotosPagedUseCase(page = nextPage, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    // 성공 후 중단 체크
                    if (!isViewModelActive) {
                        android.util.Log.d(TAG, "⛔ loadNextPage 중단됨 (성공 후)")
                        return@launch
                    }

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

                    // 중단 체크 후 썸네일 로드
                    if (isViewModelActive) {
                        loadThumbnailsForNewPhotos(paginatedPhotos.photos)
                    } else {
                        android.util.Log.d(TAG, "⛔ 새 페이지 썸네일 로드 중단됨")
                    }
                },
                onFailure = { exception ->
                    if (isViewModelActive) {
                        android.util.Log.e(TAG, "loadNextPage 실패", exception)
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            error = exception.message ?: "추가 사진을 불러오는데 실패했습니다"
                        )
                        android.util.Log.d(TAG, "isLoadingMore = false 설정됨 (실패)")
                    } else {
                        android.util.Log.d(TAG, "⛔ loadNextPage 실패 처리 중단됨")
                    }
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
            // 즉시 중단 체크
            if (!isViewModelActive) {
                android.util.Log.d(TAG, "⛔ loadThumbnailsForNewPhotos 작업 중단됨")
                return@launch
            }

            val currentCache = _uiState.value.thumbnailCache.toMutableMap()

            // 카메라 초기화 대기 (최대 2초)
            var waitCount = 0
            while (!_uiState.value.isConnected && waitCount < 20 && isViewModelActive) {
                delay(100)
                waitCount++
            }

            if (!_uiState.value.isConnected) {
                android.util.Log.w(TAG, "카메라 연결 대기 시간 초과 - 썸네일 로딩 중단")
                return@launch
            }

            // 중단 체크
            if (!isViewModelActive) {
                android.util.Log.d(TAG, "⛔ 썸네일 로딩 중단됨 (카메라 대기 후)")
                return@launch
            }

            // forEach 대신 각 사진별로 독립된 코루틴으로 병렬 처리
            photos.forEach { photo ->
                // 각 사진별로 독립된 코루틴 실행 (예외가 전파되지 않도록)
                viewModelScope.launch {
                    try {
                        // 개별 썸네일 로딩 시작 전 중단 체크
                        if (!isViewModelActive) {
                            android.util.Log.d(TAG, "⛔ 개별 썸네일 로딩 중단됨: ${photo.name}")
                            return@launch
                        }

                        if (!currentCache.containsKey(photo.path)) {
                            android.util.Log.d(TAG, "썸네일 로딩 시작: ${photo.name}")
                            
                            // RAW 파일인지 확인
                            val isRawFile = photo.path.endsWith(".nef", true) ||
                                    photo.path.endsWith(".cr2", true) ||
                                    photo.path.endsWith(".arw", true) ||
                                    photo.path.endsWith(".dng", true)

                            // 빠른 포기 전략: 초기 시도에서 빠르게 실패하면 재시도 간격 단축
                            var retryDelay = if (isRawFile) 200L else 100L
                            var maxRetries = 2

                            // 모든 파일에 대해 썸네일 가져오기 시도 (RAW, JPG 구분 없이)
                            getCameraThumbnailUseCase(photo.path).fold(
                                onSuccess = { thumbnailData ->
                                    // 성공 후 중단 체크
                                    if (!isViewModelActive) {
                                        android.util.Log.d(TAG, "⛔ 썸네일 성공 처리 중단됨: ${photo.name}")
                                        return@launch
                                    }

                                    synchronized(currentCache) {
                                        currentCache[photo.path] = thumbnailData
                                        _uiState.value = _uiState.value.copy(
                                            thumbnailCache = currentCache.toMap()
                                        )
                                    }
                                    android.util.Log.d(
                                        TAG,
                                        "썸네일 로드 성공: ${photo.name} (${thumbnailData.size} bytes)"
                                    )
                                },
                                onFailure = { exception ->
                                    // 실패 후 중단 체크
                                    if (!isViewModelActive) {
                                        android.util.Log.d(TAG, "⛔ 썸네일 실패 처리 중단됨: ${photo.name}")
                                        return@launch
                                    }

                                    android.util.Log.w(TAG, "썸네일 로드 실패: ${photo.path}", exception)

                                    // 재시도 로직도 독립된 코루틴으로 실행
                                    viewModelScope.launch {
                                        repeat(maxRetries) { retryIndex ->
                                            try {
                                                // 재시도 전 중단 체크
                                                if (!isViewModelActive) {
                                                    android.util.Log.d(
                                                        TAG,
                                                        "⛔ 썸네일 재시도 중단됨: ${photo.name}"
                                                    )
                                                    return@launch
                                                }

                                                delay(retryDelay)
                                                android.util.Log.d(
                                                    TAG,
                                                    "썸네일 재시도 ${retryIndex + 1}/${maxRetries}: ${photo.name}"
                                                )

                                                getCameraThumbnailUseCase(photo.path).fold(
                                                    onSuccess = { retryThumbnailData ->
                                                        // 재시도 성공 후 중단 체크
                                                        if (!isViewModelActive) {
                                                            android.util.Log.d(
                                                                TAG,
                                                                "⛔ 썸네일 재시도 성공 처리 중단됨: ${photo.name}"
                                                            )
                                                            return@launch
                                                        }

                                                        synchronized(currentCache) {
                                                            currentCache[photo.path] =
                                                                retryThumbnailData
                                                            _uiState.value = _uiState.value.copy(
                                                                thumbnailCache = currentCache.toMap()
                                                            )
                                                        }
                                                        android.util.Log.d(
                                                            TAG,
                                                            "썸네일 재시도 성공: ${photo.name} (${retryThumbnailData.size} bytes)"
                                                        )
                                                        return@repeat // 성공하면 재시도 중단
                                                    },
                                                    onFailure = { retryException ->
                                                        android.util.Log.e(
                                                            TAG,
                                                            "썸네일 재시도 ${retryIndex + 1} 실패: ${photo.path}",
                                                            retryException
                                                        )

                                                        // 마지막 재시도에서도 실패하면 빈 ByteArray로 캐시에 추가
                                                        if (retryIndex == maxRetries - 1 && isViewModelActive) {
                                                            synchronized(currentCache) {
                                                                currentCache[photo.path] =
                                                                    ByteArray(0)
                                                                _uiState.value =
                                                                    _uiState.value.copy(
                                                                        thumbnailCache = currentCache.toMap()
                                                                    )
                                                            }
                                                        }
                                                    }
                                                )

                                                // 재시도 간격 증가 (점진적 백오프)
                                                retryDelay = (retryDelay * 1.5).toLong()

                                            } catch (retryException: Exception) {
                                                android.util.Log.e(
                                                    TAG,
                                                    "썸네일 재시도 중 예외: ${photo.name}",
                                                    retryException
                                                )
                                                // 마지막 재시도에서 예외 발생 시에도 빈 데이터로 캐시 추가
                                                if (retryIndex == maxRetries - 1 && isViewModelActive) {
                                                    synchronized(currentCache) {
                                                        currentCache[photo.path] = ByteArray(0)
                                                        _uiState.value = _uiState.value.copy(
                                                            thumbnailCache = currentCache.toMap()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        } else {
                            android.util.Log.d(TAG, "썸네일 캐시에 이미 존재: ${photo.name}")
                        }
                    } catch (exception: Exception) {
                        android.util.Log.e(TAG, "썸네일 로딩 중 예외: ${photo.name}", exception)
                        // 예외 발생 시에도 빈 데이터로 캐시 추가하여 무한 로딩 방지
                        if (isViewModelActive) {
                            synchronized(currentCache) {
                                currentCache[photo.path] = ByteArray(0)
                                _uiState.value = _uiState.value.copy(
                                    thumbnailCache = currentCache.toMap()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 전체화면 뷰어용 실제 파일 다운로드 및 EXIF 파싱
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

                    val result = com.inik.camcon.NativeCall(photoPath)
                    android.util.Log.d(
                        TAG,
                        "downloadCameraPhoto 결과: ${if (result == null) "null" else "${result.size} bytes"}"
                    )
                    result
                }

                if (imageData != null && imageData.isNotEmpty()) {
                    android.util.Log.d(TAG, "이미지 데이터 확인: 유효함 (${imageData.size} bytes)")

                    // 고화질 이미지 캐시 업데이트
                    val currentCache = _fullImageCache.value
                    if (!currentCache.containsKey(photoPath)) {
                        val newCache = currentCache + (photoPath to imageData)
                        _fullImageCache.value = newCache
                        
                        android.util.Log.d(TAG, "이미지 데이터 반환: ${imageData.size} 바이트")
                        android.util.Log.d(TAG, "실제 파일 다운로드 성공: ${imageData.size} bytes")

                        // EXIF 파싱도 함께 처리 (EXIF 캐시에 없는 경우에만)
                        val hasExifCache = _exifCache.value.containsKey(photoPath)
                        android.util.Log.d(TAG, "EXIF 캐시 확인: ${if (hasExifCache) "있음" else "없음"}")

                        if (!hasExifCache) {
                            android.util.Log.d(TAG, "고화질 다운로드와 함께 EXIF 파싱 시작: $photoPath")
                            withContext(Dispatchers.IO) {
                                try {
                                    // 임시 파일 생성
                                    val tempFile = java.io.File.createTempFile("temp_exif", ".jpg")
                                    tempFile.writeBytes(imageData)

                                    try {
                                        // Android ExifInterface로 상세 EXIF 정보 읽기
                                        val exif =
                                            androidx.exifinterface.media.ExifInterface(tempFile.absolutePath)

                                        // JSON 형태로 EXIF 정보 구성
                                        val exifMap = mutableMapOf<String, Any>()

                                        // 기본 이미지 정보 추가 (네이티브에서 가져온 것)
                                        val basicInfo =
                                            com.inik.camcon.NativeCall(
                                                photoPath
                                            )
                                        basicInfo?.let { basic ->
                                            try {
                                                val basicJson = org.json.JSONObject(basic)
                                                if (basicJson.has("width")) {
                                                    exifMap["width"] = basicJson.getInt("width")
                                                }
                                                if (basicJson.has("height")) {
                                                    exifMap["height"] = basicJson.getInt("height")
                                                } else {
                                                    exifMap["height"] = exif.getAttributeInt(
                                                        androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH,
                                                        0
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.w(TAG, "기본 정보 파싱 실패", e)
                                            }
                                        }

                                        // 카메라 정보
                                        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE)
                                            ?.let {
                                                exifMap["make"] = it
                                            }
                                        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL)
                                            ?.let {
                                                exifMap["model"] = it
                                            }

                                        // 촬영 설정
                                        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER)
                                            ?.let {
                                                exifMap["f_number"] = it
                                            }
                                        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME)
                                            ?.let {
                                                exifMap["exposure_time"] = it
                                            }
                                        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH)
                                            ?.let {
                                                exifMap["focal_length"] = it
                                            }
                                        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                                            ?.let {
                                                exifMap["iso"] = it
                                            }

                                        // 기타 정보
                                        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION)
                                            ?.let {
                                                exifMap["orientation"] = it.toIntOrNull() ?: 1
                                            }
                                        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE)
                                            ?.let {
                                                exifMap["white_balance"] = it
                                            }
                                        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_FLASH)
                                            ?.let {
                                                exifMap["flash"] = it
                                            }
                                        exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                                            ?.let {
                                                exifMap["date_time_original"] = it
                                            }

                                        // GPS 정보
                                        val latLong = FloatArray(2)
                                        if (exif.getLatLong(latLong)) {
                                            exifMap["gps_latitude"] = latLong[0]
                                            exifMap["gps_longitude"] = latLong[1]
                                        }

                                        // JSON 문자열로 변환
                                        val jsonObject = org.json.JSONObject()
                                        exifMap.forEach { (key, value) ->
                                            jsonObject.put(key, value)
                                        }

                                        val exifJson = jsonObject.toString()
                                        android.util.Log.d(
                                            TAG,
                                            "고화질 다운로드와 함께 EXIF 파싱 완료: $exifJson"
                                        )

                                        // EXIF 캐시에 추가
                                        _exifCache.value =
                                            _exifCache.value + (photoPath to exifJson)

                                    } finally {
                                        // 임시 파일 삭제
                                        tempFile.delete()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e(TAG, "고화질 다운로드 중 EXIF 파싱 실패", e)
                                }
                            }
                        } else {
                            android.util.Log.d(TAG, "EXIF 이미 캐시에 있음, 파싱 생략")
                        }
                        
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
     * 사진의 EXIF 정보를 가져오는 함수 (캐시에서만 조회)
     */
    fun getCameraPhotoExif(photoPath: String): String? {
        // EXIF 캐시에서 확인
        val cachedExif = _exifCache.value[photoPath]
        if (cachedExif != null) {
            android.util.Log.d(TAG, "EXIF 캐시에서 반환: $photoPath")
            return cachedExif
        }

        // 이미 다운로드 중인지 확인
        val isDownloading = _downloadingImages.value.contains(photoPath)
        val hasFullImage = _fullImageCache.value.containsKey(photoPath)

        android.util.Log.d(
            TAG,
            "EXIF 상태 확인 - 캐시: ${cachedExif != null}, 다운로드중: $isDownloading, 고화질있음: $hasFullImage"
        )

        if (!isDownloading && !hasFullImage) {
            // 캐시에 없고 다운로드 중이 아니면 고화질 다운로드 트리거
            android.util.Log.d(TAG, "EXIF 캐시 없음, 고화질 다운로드 트리거: $photoPath")
            downloadFullImage(photoPath)
        } else if (isDownloading) {
            android.util.Log.d(TAG, "이미 다운로드 중, EXIF 파싱 대기: $photoPath")
        } else if (hasFullImage) {
            android.util.Log.d(TAG, "고화질 있지만 EXIF 없음, 별도 파싱 필요: $photoPath")
            // 고화질은 있지만 EXIF가 없는 경우 (드물지만 가능)
            downloadFullImage(photoPath)
        }

        // 즉시 null 반환 (비동기 처리 후 캐시에 저장됨)
        return null
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

    /**
     * ViewModel 정리 시 모든 작업 중단 및 이벤트 리스너 재시작
     */
    override fun onCleared() {
        super.onCleared()
        android.util.Log.d(TAG, "=== PhotoPreviewViewModel 정리 시작 ===")

        // 1. 즉시 모든 작업 중단 플래그 설정
        isViewModelActive = false
        android.util.Log.d(TAG, "⛔ 모든 진행 중인 작업 중단 요청")

        // 2. ViewModelScope 취소 (모든 코루틴 즉시 중단)
        // viewModelScope는 onCleared()에서 자동으로 취소되지만 명시적으로 확인
        android.util.Log.d(TAG, "🚫 ViewModelScope 취소 - 모든 코루틴 중단")

        // 3. 사진 미리보기 탭에서 나갈 때 이벤트 리스너 재시작
        viewModelScope.launch {
            try {
                if (_uiState.value.isConnected) {
                    android.util.Log.d(TAG, "📸 사진 미리보기 탭 종료 - 이벤트 리스너 재시작")

                    // **네이티브 작업 재개 (반드시 먼저 실행)**
                    com.inik.camcon.NativeCall()
                    android.util.Log.d(TAG, "▶️ 네이티브 작업 재개 완료 (중단 플래그 해제)")

                    // 짧은 지연 후 이벤트 리스너 재시작
                    kotlinx.coroutines.delay(100)
                    
                    cameraRepository.startCameraEventListener()
                    android.util.Log.d(TAG, "✅ 이벤트 리스너 재시작 완료")
                    
                    // ★★★ 사진 미리보기 모드 비활성화
                    cameraRepository.setPhotoPreviewMode(false)
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "이벤트 리스너 재시작 실패", e)
            }
        }

        android.util.Log.d(TAG, "=== PhotoPreviewViewModel 정리 완료 ===")
    }
}