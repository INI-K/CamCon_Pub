package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.manager.ErrorHandlingManager
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.camera.PhotoCaptureEventManager
import com.inik.camcon.presentation.viewmodel.photo.FileTypeFilter
import com.inik.camcon.presentation.viewmodel.photo.PhotoImageManager
import com.inik.camcon.presentation.viewmodel.photo.PhotoListManager
import com.inik.camcon.presentation.viewmodel.photo.PhotoSelectionManager
import com.inik.camcon.utils.SubscriptionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 사진 미리보기 UI 상태 데이터
 */
data class PhotoPreviewUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedPhoto: CameraPhoto? = null,
    val isConnected: Boolean = false,
    val isInitialized: Boolean = false,
    val isInitializing: Boolean = false,
    val currentTier: SubscriptionTier = SubscriptionTier.FREE
)

/**
 * 사진 미리보기를 위한 ViewModel - MVVM 패턴 준수
 * 단일책임: UI 상태 관리 및 매니저들 간의 조정만 담당
 * View Layer와 Domain Layer 사이의 중재자 역할
 */
@HiltViewModel
class PhotoPreviewViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val photoCaptureEventManager: PhotoCaptureEventManager,
    private val globalManager: CameraConnectionGlobalManager,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,

    // 매니저 의존성 주입 (단일책임원칙 적용)
    private val photoListManager: PhotoListManager,
    private val photoImageManager: PhotoImageManager,
    private val photoSelectionManager: PhotoSelectionManager,
    private val errorHandlingManager: ErrorHandlingManager
) : ViewModel() {

    companion object {
        private const val TAG = "사진미리보기뷰모델"
    }

    // UI 상태
    private val _uiState = MutableStateFlow(PhotoPreviewUiState())
    val uiState: StateFlow<PhotoPreviewUiState> = _uiState.asStateFlow()

    // 매니저들의 상태 노출 (읽기 전용)
    val photos = photoListManager.filteredPhotos
    val allPhotos = photoListManager.allPhotos
    val isLoadingPhotos = photoListManager.isLoading
    val isLoadingMorePhotos = photoListManager.isLoadingMore
    val hasNextPage = photoListManager.hasNextPage
    val currentFilter = photoListManager.currentFilter

    // 페이지 정보 노출
    val currentPage = photoListManager.currentPage
    val totalPages = photoListManager.totalPages

    // 이미지 관련 상태
    val thumbnailCache = photoImageManager.thumbnailCache
    val fullImageCache = photoImageManager.fullImageCache
    val downloadingImages = photoImageManager.downloadingImages
    val exifCache = photoImageManager.exifCache

    // 선택 관련 상태
    val isMultiSelectMode = photoSelectionManager.isMultiSelectMode
    val selectedPhotos = photoSelectionManager.selectedPhotos

    init {
        initializeViewModel()
    }

    /**
     * ViewModel 초기화
     */
    private fun initializeViewModel() {
        Log.d(TAG, "=== PhotoPreviewViewModel 초기화 시작 ===")

        // 초기 상태 설정
        _uiState.value = _uiState.value.copy(isInitializing = true)

        // 사진 미리보기 탭 진입 시 이벤트 리스너 즉시 중단
        viewModelScope.launch {
            try {
                Log.d(TAG, "📸 사진 미리보기 탭 진입 - 이벤트 리스너 즉시 중단")
                
                // 사진 미리보기 모드 활성화 (자동 시작 방지)
                cameraRepository.setPhotoPreviewMode(true)
                
                // 이벤트 리스너만 중단
                cameraRepository.stopCameraEventListener()
                Log.d(TAG, "✓ 이벤트 리스너 중단 완료")
                
            } catch (e: Exception) {
                Log.w(TAG, "이벤트 리스너 중단 실패 (무시하고 계속)", e)
            }
        }

        // 옵저버들 설정
        setupObservers()

        Log.d(TAG, "=== PhotoPreviewViewModel 초기화 완료 ===")
    }

    /**
     * 옵저버들 설정
     */
    private fun setupObservers() {
        // 카메라 연결 상태 관찰
        observeCameraConnection()
        
        // 카메라 초기화 상태 관찰
        observeCameraInitialization()
        
        // 구독 티어 관찰
        observeSubscriptionTier()
        
        // 에러 이벤트 관찰
        observeErrorEvents()
    }

    /**
     * 카메라 연결 상태 관찰
     */
    private fun observeCameraConnection() {
        Log.d(TAG, "=== observeCameraConnection 시작 ===")
        viewModelScope.launch {
            globalManager.globalConnectionState.collect { connectionState ->
                val isConnected = connectionState.isAnyConnectionActive
                Log.d(TAG, "전역 카메라 연결 상태 변경: $isConnected")

                val previousConnected = _uiState.value.isConnected
                _uiState.value = _uiState.value.copy(isConnected = isConnected)

                if (isConnected && !previousConnected) {
                    Log.d(TAG, "카메라 연결됨 - 사진 목록 불러오기")
                    photoListManager.loadInitialPhotos(isConnected)
                } else if (!isConnected && previousConnected) {
                    Log.d(TAG, "카메라 연결 해제됨")
                    _uiState.value = _uiState.value.copy(isInitialized = false)
                    errorHandlingManager.emitError(
                        com.inik.camcon.domain.manager.ErrorType.CONNECTION,
                        "카메라 연결이 해제되었습니다",
                        null,
                        com.inik.camcon.domain.manager.ErrorSeverity.MEDIUM
                    )
                }
            }
        }
    }

    /**
     * 카메라 초기화 상태 관찰
     */
    private fun observeCameraInitialization() {
        viewModelScope.launch {
            cameraRepository.isInitializing().collect { isInitializing ->
                _uiState.value = _uiState.value.copy(isInitializing = isInitializing)
            }
        }
    }

    /**
     * 구독 티어 관찰
     */
    private fun observeSubscriptionTier() {
        viewModelScope.launch {
            getSubscriptionUseCase.getSubscriptionTier().collect { tier ->
                Log.d(TAG, "사용자 구독 티어 변경: $tier")
                _uiState.value = _uiState.value.copy(currentTier = tier)

                // 티어 변경 시 현재 필터에 따라 사진 목록 다시 필터링
                photoListManager.changeFileTypeFilter(
                    photoListManager.currentFilter.value,
                    tier
                )
            }
        }
    }

    /**
     * 에러 이벤트 관찰
     */
    private fun observeErrorEvents() {
        viewModelScope.launch {
            errorHandlingManager.errorEvent.collect { errorEvent ->
                _uiState.value = _uiState.value.copy(error = errorEvent.message)
                Log.e(TAG, "에러 이벤트 수신: ${errorEvent.type} - ${errorEvent.message}")
            }
        }
    }

    // MARK: - Public Methods (UI에서 호출)

    /**
     * 초기 사진 목록 로드 (PhotoListManager에 위임)
     */
    fun loadInitialPhotos() {
        photoListManager.loadInitialPhotos(_uiState.value.isConnected)
    }

    /**
     * 다음 페이지 로드 (PhotoListManager에 위임)
     */
    fun loadNextPage() {
        photoListManager.loadNextPage()
    }

    /**
     * 사진 목록 새로고침 (PhotoListManager에 위임)
     */
    fun refreshPhotos() {
        Log.d(TAG, "사진 목록 새로고침")
        photoListManager.refreshPhotos(_uiState.value.isConnected)
    }

    /**
     * 파일 타입 필터 변경 (PhotoListManager에 위임)
     */
    fun changeFileTypeFilter(filter: FileTypeFilter) {
        photoListManager.changeFileTypeFilter(filter, _uiState.value.currentTier)
    }

    /**
     * 프리로딩 체크 (PhotoListManager에 위임)
     */
    fun onPhotoIndexReached(currentIndex: Int) {
        photoListManager.onPhotoIndexReached(currentIndex)
    }

    /**
     * 사진 선택 (UI 상태 업데이트)
     */
    fun selectPhoto(photo: CameraPhoto?) {
        if (photo != null && !handleRawFileAccess(photo)) {
            return
        }
        
        _uiState.value = _uiState.value.copy(selectedPhoto = photo)
    }

    /**
     * 사진 다운로드 (PhotoImageManager에 위임)
     */
    fun downloadPhoto(photo: CameraPhoto) {
        if (!handleRawFileAccess(photo)) {
            return
        }
        
        photoImageManager.downloadFullImage(photo.path, _uiState.value.currentTier)
    }

    /**
     * 고해상도 이미지 가져오기 (PhotoImageManager에 위임)
     */
    fun getFullImage(photoPath: String): ByteArray? {
        return photoImageManager.getFullImage(photoPath)
    }

    /**
     * 썸네일 가져오기 (PhotoImageManager에 위임)
     */
    fun getThumbnail(photoPath: String): ByteArray? {
        return photoImageManager.getThumbnail(photoPath)
    }

    /**
     * 다운로드 상태 확인 (PhotoImageManager에 위임)
     */
    fun isDownloadingFullImage(photoPath: String): Boolean {
        return photoImageManager.isDownloadingFullImage(photoPath)
    }

    /**
     * EXIF 정보 가져오기 (PhotoImageManager에 위임)
     */
    fun getCameraPhotoExif(photoPath: String): String? {
        return photoImageManager.getCameraPhotoExif(photoPath)
    }

    /**
     * 인접 이미지 미리 로드 (PhotoImageManager에 위임)
     */
    fun preloadAdjacentImages(selectedPhoto: CameraPhoto, photos: List<CameraPhoto>) {
        if (!handleRawFileAccess(selectedPhoto)) {
            return
        }

        // 선택된 사진과 인접 사진들을 순차적으로 다운로드
        photoImageManager.downloadFullImage(selectedPhoto.path, _uiState.value.currentTier)

        // 인접 사진들도 미리 로드 (성능 최적화)
        val currentIndex = photos.indexOfFirst { it.path == selectedPhoto.path }
        if (currentIndex != -1) {
            // 앞뒤 1장씩만 미리 로드
            listOf(currentIndex - 1, currentIndex + 1)
                .filter { it in photos.indices }
                .forEach { index ->
                    val adjacentPhoto = photos[index]
                    if (handleRawFileAccess(adjacentPhoto)) {
                        photoImageManager.downloadFullImage(
                            adjacentPhoto.path,
                            _uiState.value.currentTier
                        )
                    }
                }
        }
    }

    /**
     * 빠른 미리 로드 (PhotoImageManager에 위임)
     */
    fun quickPreloadCurrentImage(selectedPhoto: CameraPhoto) {
        if (!handleRawFileAccess(selectedPhoto)) {
            return
        }

        photoImageManager.downloadFullImage(selectedPhoto.path, _uiState.value.currentTier)
    }

    /**
     * 카메라 사진 로드 (별칭)
     */
    fun loadCameraPhotos() {
        loadInitialPhotos()
    }

    /**
     * 강제로 다음 페이지 로드
     */
    fun forceLoadNextPage() {
        loadNextPage()
    }

    // MARK: - Private Helper Methods

    /**
     * RAW 파일 접근 권한 확인
     */
    private fun canAccessRawFiles(): Boolean {
        val tier = _uiState.value.currentTier
        return tier == SubscriptionTier.PRO ||
                tier == SubscriptionTier.REFERRER ||
                tier == SubscriptionTier.ADMIN
    }

    /**
     * RAW 파일 접근 권한 처리
     */
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

    /**
     * 에러 메시지 클리어
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 뷰모델 상태 로깅 (디버깅용)
     */
    fun logCurrentState() {
        Log.d(
            TAG, """
            현재 PhotoPreview 상태:
            - 연결됨: ${_uiState.value.isConnected}
            - 초기화중: ${_uiState.value.isInitializing}
            - 구독 티어: ${_uiState.value.currentTier}
            - 선택된 사진: ${_uiState.value.selectedPhoto?.name}
            - 에러: ${_uiState.value.error}
        """.trimIndent()
        )

        // 각 매니저의 상태도 로깅
        photoListManager.logCurrentState()
        photoSelectionManager.logCurrentState()
    }

    // MARK: - 멀티 선택 관련 메서드들 (PhotoSelectionManager에 위임)

    /**
     * 멀티 선택 모드 시작
     */
    fun startMultiSelectMode(initialPhotoPath: String) {
        photoSelectionManager.startMultiSelectMode(initialPhotoPath)
        _uiState.value = _uiState.value.copy(selectedPhoto = null)
    }

    /**
     * 멀티 선택 모드 종료
     */
    fun exitMultiSelectMode() {
        photoSelectionManager.exitMultiSelectMode()
    }

    /**
     * 사진 선택 토글
     */
    fun togglePhotoSelection(photoPath: String) {
        photoSelectionManager.togglePhotoSelection(photoPath)
    }

    /**
     * 모든 사진 선택
     */
    fun selectAllPhotos() {
        val allPhotoPaths = photoListManager.filteredPhotos.value.map { it.path }
        photoSelectionManager.selectAllPhotos(allPhotoPaths)
    }

    /**
     * 모든 사진 선택 해제
     */
    fun deselectAllPhotos() {
        photoSelectionManager.deselectAllPhotos()
    }

    /**
     * 선택된 사진들 다운로드
     */
    fun downloadSelectedPhotos() {
        val selectedPaths = photoSelectionManager.getSelectedPaths()
        Log.d(TAG, "선택된 사진들 다운로드 시작: ${selectedPaths.size}개")

        selectedPaths.forEach { photoPath ->
            val tempPhoto = CameraPhoto(
                path = photoPath,
                name = photoPath.substringAfterLast("/"),
                size = 0L,
                date = System.currentTimeMillis()
            )

            if (handleRawFileAccess(tempPhoto)) {
                photoImageManager.downloadFullImage(photoPath, _uiState.value.currentTier)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "=== PhotoPreviewViewModel 정리 시작 ===")

        // 사진 미리보기 탭에서 나갈 때 이벤트 리스너 재시작
        viewModelScope.launch {
            try {
                if (_uiState.value.isConnected) {
                    Log.d(TAG, "📸 사진 미리보기 탭 종료 - 이벤트 리스너 재시작")

                    // 네이티브 작업 재개
                    com.inik.camcon.NativeCall()
                    Log.d(TAG, "▶️ 네이티브 작업 재개 완료")

                    // 짧은 지연 후 이벤트 리스너 재시작
                    kotlinx.coroutines.delay(100)
                    
                    cameraRepository.startCameraEventListener()
                    Log.d(TAG, "✅ 이벤트 리스너 재시작 완료")
                    
                    // 사진 미리보기 모드 비활성화
                    cameraRepository.setPhotoPreviewMode(false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "이벤트 리스너 재시작 실패", e)
            }
        }

        // 매니저들 정리
        photoListManager.cleanup()
        photoImageManager.cleanup()
        photoSelectionManager.clearSelection()

        Log.d(TAG, "=== PhotoPreviewViewModel 정리 완료 ===")
    }
}