package com.inik.camcon.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraPhotosPagedUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraThumbnailUseCase
import com.inik.camcon.domain.usecase.camera.PhotoCaptureEventManager
import com.inik.camcon.utils.SubscriptionUtils
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
    val allPhotos: List<CameraPhoto> = emptyList(),
    // 멀티 선택 관련 상태
    val isMultiSelectMode: Boolean = false, // 멀티 선택 모드 활성화 여부
    val selectedPhotos: Set<String> = emptySet(), // 선택된 사진들의 path 집합
    // 구독 관련 상태
    val currentTier: SubscriptionTier = SubscriptionTier.FREE
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
    private val globalManager: CameraConnectionGlobalManager,
    private val getSubscriptionUseCase: GetSubscriptionUseCase
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
            launch { observeSubscriptionTier() }  
        }

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

    private fun observeSubscriptionTier() {
        viewModelScope.launch {
            getSubscriptionUseCase.getSubscriptionTier().collect { tier ->
                android.util.Log.d(TAG, "사용자 구독 티어 변경: $tier")
                _uiState.value = _uiState.value.copy(currentTier = tier)

                // 티어 변경 시 현재 필터에 따라 사진 목록 다시 필터링
                val currentFilter = _uiState.value.fileTypeFilter
                val filteredPhotos = filterPhotos(_uiState.value.allPhotos, currentFilter)
                _uiState.value = _uiState.value.copy(photos = filteredPhotos)
            }
        }
    }

    private fun canAccessRawFiles(): Boolean {
        val tier = _uiState.value.currentTier
        return tier == SubscriptionTier.PRO || 
               tier == SubscriptionTier.REFERRER || 
               tier == SubscriptionTier.ADMIN
    }

    private fun handleRawFileAccess(photo: CameraPhoto): Boolean {
        if (SubscriptionUtils.isRawFile(photo.path) && !canAccessRawFiles()) {
            val message = when (_uiState.value.currentTier) {
                SubscriptionTier.FREE -> SubscriptionUtils.getRawRestrictionMessage()
                SubscriptionTier.BASIC -> SubscriptionUtils.getRawRestrictionMessageForBasic()
                else -> "RAW 파일에 접근할 수 없습니다."
            }
            _uiState.value = _uiState.value.copy(error = message)
            return false
        }
        return true
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
        if (!handleRawFileAccess(photo)) {
            return
        }
        
        viewModelScope.launch {
            try {
                // 현재 구독 티어 확인
                val currentTier = _uiState.value.currentTier
                android.util.Log.d(TAG, "사진 다운로드 시작: ${photo.name}, 티어: $currentTier")
                
                // 카메라에서 원본 사진 다운로드
                val downloadResult = cameraRepository.downloadPhotoFromCamera(photo.path)
                
                downloadResult.onSuccess { capturedPhoto ->
                    android.util.Log.d(TAG, "✅ 사진 다운로드 성공: ${photo.name}")
                    
                    // Free 티어 사용자의 경우 추가 리사이징 처리
                    if (currentTier == SubscriptionTier.FREE) {
                        android.util.Log.d(TAG, "🎯 Free 티어 사용자 - 리사이징 처리 시작")
                        
                        val originalFile = java.io.File(capturedPhoto.filePath)
                        if (originalFile.exists() && photo.name.endsWith(".jpg", true)) {
                            try {
                                // 리사이즈된 파일 생성
                                val resizedFile = java.io.File(
                                    originalFile.parent, 
                                    "${originalFile.nameWithoutExtension}_resized.jpg"
                                )
                                
                                // 리사이즈 구현 (PhotoDownloadManager.kt에서 복사)
                                val resizeSuccess = resizeImageForFreeTier(
                                    originalFile.absolutePath, 
                                    resizedFile.absolutePath
                                )
                                
                                if (resizeSuccess) {
                                    // 원본 파일 삭제하고 리사이즈된 파일로 교체
                                    originalFile.delete()
                                    resizedFile.renameTo(originalFile)
                                    android.util.Log.d(TAG, "✅ Free 티어 리사이징 완료: ${photo.name}")
                                } else {
                                    android.util.Log.w(TAG, "⚠️ Free 티어 리사이징 실패, 원본 유지: ${photo.name}")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(TAG, "❌ Free 티어 리사이징 처리 중 오류: ${photo.name}", e)
                            }
                        }
                    }
                }.onFailure { error ->
                    android.util.Log.e(TAG, "❌ 사진 다운로드 실패: ${photo.name}", error)
                    _uiState.value = _uiState.value.copy(
                        error = error.message
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ 사진 다운로드 중 예외: ${photo.name}", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message
                )
            }
        }
    }

    /**
     * Free 티어 사용자를 위한 이미지 리사이즈 처리 (PhotoDownloadManager에서 복사)
     * 장축 기준 2000픽셀로 리사이즈하고 모든 EXIF 정보 보존
     */
    private suspend fun resizeImageForFreeTier(inputPath: String, outputPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d(TAG, "🔧 Free 티어 이미지 리사이즈 시작: $inputPath")

                // 원본 이미지 크기 확인
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(inputPath, options)

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight
                val maxDimension = kotlin.math.max(originalWidth, originalHeight)

                android.util.Log.d(TAG, "원본 이미지 크기: ${originalWidth}x${originalHeight}")

                // 이미 작은 이미지인 경우 리사이즈하지 않음
                if (maxDimension <= 2000) {
                    android.util.Log.d(TAG, "이미 작은 이미지 - 리사이즈 불필요")
                    return@withContext java.io.File(inputPath).copyTo(java.io.File(outputPath), overwrite = true).exists()
                }

                // 리사이즈 비율 계산
                val scale = 2000.toFloat() / maxDimension.toFloat()
                val newWidth = (originalWidth * scale).toInt()
                val newHeight = (originalHeight * scale).toInt()

                android.util.Log.d(TAG, "리사이즈 목표 크기: ${newWidth}x${newHeight} (비율: $scale)")

                // 메모리 효율적인 리사이즈를 위한 샘플링
                val sampleSize = calculateInSampleSize(originalWidth, originalHeight, newWidth, newHeight)

                options.apply {
                    inJustDecodeBounds = false
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }

                val bitmap = android.graphics.BitmapFactory.decodeFile(inputPath, options) ?: run {
                    android.util.Log.e(TAG, "이미지 디코딩 실패: $inputPath")
                    return@withContext false
                }

                try {
                    // 정확한 크기로 최종 리사이즈
                    val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

                    // EXIF 정보 읽기 (회전 정보)
                    val originalExif = androidx.exifinterface.media.ExifInterface(inputPath)
                    val orientation = originalExif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )

                    // 회전 적용
                    val rotatedBitmap = rotateImageIfRequired(resizedBitmap, orientation)

                    // 파일로 저장
                    java.io.FileOutputStream(outputPath).use { out ->
                        rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    // 메모리 정리
                    if (resizedBitmap != rotatedBitmap) {
                        resizedBitmap.recycle()
                    }
                    rotatedBitmap.recycle()

                    // ★★★ 모든 EXIF 정보를 새 파일에 복사 ★★★
                    copyAllExifData(inputPath, outputPath, newWidth, newHeight)

                    val outputFile = java.io.File(outputPath)
                    val finalSize = outputFile.length()
                    android.util.Log.d(TAG, "✅ Free 티어 리사이즈 완료 (EXIF 보존) - 최종 크기: ${finalSize / 1024}KB")

                    true
                } finally {
                    bitmap.recycle()
                }

            } catch (e: OutOfMemoryError) {
                android.util.Log.e(TAG, "❌ 메모리 부족으로 리사이즈 실패", e)
                System.gc()
                false
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ 이미지 리사이즈 실패", e)
                false
            }
        }
    }

    /**
     * 원본 이미지의 모든 EXIF 정보를 리사이즈된 이미지에 복사
     * 이미지 크기 정보는 새로운 값으로 업데이트
     */
    private fun copyAllExifData(originalPath: String, newPath: String, newWidth: Int, newHeight: Int) {
        try {
            android.util.Log.d(TAG, "EXIF 정보 복사 시작: $originalPath -> $newPath")
            
            val originalExif = androidx.exifinterface.media.ExifInterface(originalPath)
            val newExif = androidx.exifinterface.media.ExifInterface(newPath)

            // 복사할 EXIF 태그들 - 거의 모든 중요한 EXIF 정보
            val tagsToPreserve = arrayOf(
                // 카메라 정보
                androidx.exifinterface.media.ExifInterface.TAG_MAKE,
                androidx.exifinterface.media.ExifInterface.TAG_MODEL,
                androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE,
                
                // 촬영 설정
                androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER,
                androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME,
                androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS,
                androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED,
                androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH,
                androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                androidx.exifinterface.media.ExifInterface.TAG_APERTURE_VALUE,
                androidx.exifinterface.media.ExifInterface.TAG_SHUTTER_SPEED_VALUE,
                androidx.exifinterface.media.ExifInterface.TAG_BRIGHTNESS_VALUE,
                androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
                androidx.exifinterface.media.ExifInterface.TAG_MAX_APERTURE_VALUE,
                androidx.exifinterface.media.ExifInterface.TAG_METERING_MODE,
                androidx.exifinterface.media.ExifInterface.TAG_LIGHT_SOURCE,
                androidx.exifinterface.media.ExifInterface.TAG_FLASH,
                androidx.exifinterface.media.ExifInterface.TAG_SCENE_CAPTURE_TYPE,
                androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE,
                androidx.exifinterface.media.ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
                androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_MODE,
                androidx.exifinterface.media.ExifInterface.TAG_GAIN_CONTROL,
                androidx.exifinterface.media.ExifInterface.TAG_CONTRAST,
                androidx.exifinterface.media.ExifInterface.TAG_SATURATION,
                androidx.exifinterface.media.ExifInterface.TAG_SHARPNESS,
                
                // 날짜/시간 정보
                androidx.exifinterface.media.ExifInterface.TAG_DATETIME,
                androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL,
                androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED,
                androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME,
                androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                androidx.exifinterface.media.ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
                androidx.exifinterface.media.ExifInterface.TAG_SUBSEC_TIME,
                androidx.exifinterface.media.ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                androidx.exifinterface.media.ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
                
                // GPS 정보
                androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE_REF,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_TIMESTAMP,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_DATESTAMP,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_PROCESSING_METHOD,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_SPEED,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_SPEED_REF,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_TRACK,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_TRACK_REF,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_IMG_DIRECTION,
                androidx.exifinterface.media.ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                
                // 기타 메타데이터
                androidx.exifinterface.media.ExifInterface.TAG_ARTIST,
                androidx.exifinterface.media.ExifInterface.TAG_COPYRIGHT,
                androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION,
                androidx.exifinterface.media.ExifInterface.TAG_USER_COMMENT,
                androidx.exifinterface.media.ExifInterface.TAG_CAMERA_OWNER_NAME,
                androidx.exifinterface.media.ExifInterface.TAG_BODY_SERIAL_NUMBER,
                androidx.exifinterface.media.ExifInterface.TAG_LENS_MAKE,
                androidx.exifinterface.media.ExifInterface.TAG_LENS_MODEL,
                androidx.exifinterface.media.ExifInterface.TAG_LENS_SERIAL_NUMBER,
                androidx.exifinterface.media.ExifInterface.TAG_LENS_SPECIFICATION,
                
                // 색상 공간 및 렌더링
                androidx.exifinterface.media.ExifInterface.TAG_COLOR_SPACE,
                androidx.exifinterface.media.ExifInterface.TAG_GAMMA,
                androidx.exifinterface.media.ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION,
                androidx.exifinterface.media.ExifInterface.TAG_REFERENCE_BLACK_WHITE,
                androidx.exifinterface.media.ExifInterface.TAG_WHITE_POINT,
                androidx.exifinterface.media.ExifInterface.TAG_PRIMARY_CHROMATICITIES,
                androidx.exifinterface.media.ExifInterface.TAG_Y_CB_CR_COEFFICIENTS,
                androidx.exifinterface.media.ExifInterface.TAG_Y_CB_CR_POSITIONING,
                androidx.exifinterface.media.ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING,
                
                // 방향 정보 (변경되지 않음 - 회전은 이미 적용됨)
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION
            )

            var copiedCount = 0
            // 모든 태그 복사
            for (tag in tagsToPreserve) {
                val value = originalExif.getAttribute(tag)
                if (value != null) {
                    newExif.setAttribute(tag, value)
                    copiedCount++
                }
            }

            // 새로운 이미지 크기 정보 설정 (필수)
            newExif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, newWidth.toString())
            newExif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, newHeight.toString())
            newExif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_PIXEL_X_DIMENSION, newWidth.toString())
            newExif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_PIXEL_Y_DIMENSION, newHeight.toString())

            // 처리 소프트웨어 정보 추가
            newExif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE, "CamCon (Free Tier Resize)")

            // EXIF 정보 저장
            newExif.saveAttributes()
            
            android.util.Log.d(TAG, "✅ EXIF 정보 복사 완료: ${copiedCount}개 태그 복사됨")
            android.util.Log.d(TAG, "   새 이미지 크기 정보: ${newWidth}x${newHeight}")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ EXIF 정보 복사 실패", e)
            // EXIF 복사 실패해도 이미지 리사이즈는 성공으로 처리
        }
    }

    /**
     * 메모리 효율적인 샘플링 크기 계산
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

    /**
     * EXIF 정보에 따른 이미지 회전 처리
     */
    private fun rotateImageIfRequired(bitmap: android.graphics.Bitmap, orientation: Int): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()

        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap // 회전 불필요
        }

        return try {
            val rotatedBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        } catch (e: OutOfMemoryError) {
            android.util.Log.e(TAG, "이미지 회전 중 메모리 부족", e)
            bitmap // 회전 실패 시 원본 반환
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
                            android.util.Log.d(TAG, "썸네일 로드 시작: ${photo.name}")
                            
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
        // RAW 파일 접근 권한 체크를 위한 임시 CameraPhoto 객체
        val tempPhoto = CameraPhoto(
            path = photoPath,
            name = photoPath.substringAfterLast("/"),
            size = 0L,
            date = System.currentTimeMillis()
        )
        
        if (!handleRawFileAccess(tempPhoto)) {
            return
        }
        
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

                    val result = com.inik.camcon.CameraNative.downloadCameraPhoto(photoPath)
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
                                            com.inik.camcon.CameraNative.getCameraPhotoExif(
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
        if (photo != null && !handleRawFileAccess(photo)) {
            return
        }
        
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
        if (!handleRawFileAccess(selectedPhoto)) {
            return
        }
        
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
        android.util.Log.d(
            TAG,
            "filterPhotos 호출: 필터=$filter, 전체사진=${photos.size}개, 현재티어=${_uiState.value.currentTier}"
        )

        // 먼저 티어에 따른 접근 가능한 파일만 필터링
        val accessiblePhotos = if (canAccessRawFiles()) {
            // RAW 접근 권한이 있으면 모든 파일 접근 가능
            photos
        } else {
            // RAW 접근 권한이 없으면 RAW 파일 제외
            photos.filter { photo ->
                val isRaw = SubscriptionUtils.isRawFile(photo.path)
                if (isRaw) {
                    android.util.Log.v(TAG, "RAW 파일 숨김 (권한없음): ${photo.path}")
                }
                !isRaw
            }
        }

        // 그 다음 사용자가 선택한 필터 적용
        val filtered = when (filter) {
            FileTypeFilter.ALL -> accessiblePhotos
            FileTypeFilter.JPG -> accessiblePhotos.filter {
                val isJpg = it.path.endsWith(".jpg", true) || it.path.endsWith(".jpeg", true)
                android.util.Log.v(TAG, "JPG 필터 확인: ${it.path} -> $isJpg")
                isJpg
            }
            FileTypeFilter.RAW -> {
                if (!canAccessRawFiles()) {
                    // RAW 필터 선택했지만 권한 없는 경우 에러 메시지 표시
                    val message = when (_uiState.value.currentTier) {
                        SubscriptionTier.FREE -> "RAW 파일 보기는 준비중입니다.\nJPG 파일만 확인하실 수 있습니다."
                        SubscriptionTier.BASIC -> "RAW 파일은 PRO 구독에서만 볼 수 있습니다.\nPRO로 업그레이드해주세요!"
                        else -> "RAW 파일에 접근할 수 없습니다."
                    }
                    _uiState.value = _uiState.value.copy(error = message)
                    // 빈 목록 반환
                    emptyList()
                } else {
                    // RAW 권한 있는 경우 RAW 파일만 필터링
                    accessiblePhotos.filter {
                        val isRaw = SubscriptionUtils.isRawFile(it.path)
                        android.util.Log.v(TAG, "RAW 필터 확인: ${it.path} -> $isRaw")
                        isRaw
                    }
                }
            }
        }

        android.util.Log.d(TAG, "필터링 결과: 접근가능=${accessiblePhotos.size}개, 최종필터링=${filtered.size}개")
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
                    com.inik.camcon.CameraNative.resumeOperations()
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

    /**
     * 선택된 사진들을 다운로드합니다.
     */
    fun downloadSelectedPhotos() {
        val selectedPaths = _uiState.value.selectedPhotos
        android.util.Log.d(TAG, "선택된 사진들 다운로드 시작: ${selectedPaths.size}개")

        selectedPaths.forEach { photoPath ->
            // RAW 파일 접근 권한 체크를 위한 임시 CameraPhoto 객체
            val tempPhoto = CameraPhoto(
                path = photoPath,
                name = photoPath.substringAfterLast("/"),
                size = 0L,
                date = System.currentTimeMillis()
            )

            if (!handleRawFileAccess(tempPhoto)) {
                return@forEach
            }
            
            downloadFullImage(photoPath)
        }
    }

    // 멀티 선택 관련 메서드들

    /**
     * 멀티 선택 모드를 시작합니다.
     * 일반적으로 사진을 롱클릭했을 때 호출됩니다.
     */
    fun startMultiSelectMode(initialPhotoPath: String) {
        android.util.Log.d(TAG, "멀티 선택 모드 시작: $initialPhotoPath")
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = true,
            selectedPhotos = setOf(initialPhotoPath),
            selectedPhoto = null // 전체화면 뷰어 닫기
        )
    }

    /**
     * 멀티 선택 모드를 종료합니다.
     */
    fun exitMultiSelectMode() {
        android.util.Log.d(TAG, "멀티 선택 모드 종료")
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = false,
            selectedPhotos = emptySet()
        )
    }

    /**
     * 사진을 선택/해제합니다.
     * 멀티 선택 모드에서 사진을 클릭했을 때 호출됩니다.
     */
    fun togglePhotoSelection(photoPath: String) {
        val currentSelection = _uiState.value.selectedPhotos
        val newSelection = if (currentSelection.contains(photoPath)) {
            currentSelection - photoPath
        } else {
            currentSelection + photoPath
        }

        android.util.Log.d(TAG, "사진 선택 토글: $photoPath, 선택된 사진 수: ${newSelection.size}")

        // 선택된 사진이 하나도 없으면 멀티 선택 모드를 종료
        if (newSelection.isEmpty()) {
            exitMultiSelectMode()
        } else {
            _uiState.value = _uiState.value.copy(selectedPhotos = newSelection)
        }
    }

    /**
     * 모든 사진을 선택합니다.
     */
    fun selectAllPhotos() {
        val allPhotoPaths = _uiState.value.photos.map { it.path }.toSet()
        android.util.Log.d(TAG, "모든 사진 선택: ${allPhotoPaths.size}개")
        _uiState.value = _uiState.value.copy(selectedPhotos = allPhotoPaths)
    }

    /**
     * 모든 사진 선택을 해제합니다.
     */
    fun deselectAllPhotos() {
        android.util.Log.d(TAG, "모든 사진 선택 해제")
        _uiState.value = _uiState.value.copy(selectedPhotos = emptySet())
    }
}