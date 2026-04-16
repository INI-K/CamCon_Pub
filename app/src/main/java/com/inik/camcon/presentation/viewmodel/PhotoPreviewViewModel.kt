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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 사진 미리보기 UI 상태 데이터
 */
data class PhotoPreviewUiState(
    val isLoading: Boolean = false,
    val selectedPhoto: CameraPhoto? = null,
    val isConnected: Boolean = false,
    val isInitialized: Boolean = false,
    val isInitializing: Boolean = false,
    val currentTier: SubscriptionTier = SubscriptionTier.FREE,
    val isPtpipConnected: Boolean = false
)

/**
 * 일회성 UI 이벤트 (스낵바 메시지)
 */
sealed class PhotoPreviewUiEvent {
    data class ShowError(val message: String) : PhotoPreviewUiEvent()
}

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

    // 일회성 이벤트용 SharedFlow
    private val _uiEvent = MutableSharedFlow<PhotoPreviewUiEvent>(replay = 0)
    val uiEvent: SharedFlow<PhotoPreviewUiEvent> = _uiEvent.asSharedFlow()

    private fun emitError(message: String) {
        viewModelScope.launch {
            _uiEvent.emit(PhotoPreviewUiEvent.ShowError(message))
        }
    }

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

    // 옵저버 Job 필드들 — Flow collect 중복 방지
    private var connectionObserveJob: Job? = null
    private var ptpipObserveJob: Job? = null
    private var initObserveJob: Job? = null
    private var tierObserveJob: Job? = null
    private var errorObserveJob: Job? = null
    private var photosObserveJob: Job? = null

    init {
        initializeViewModel()
    }

    /**
     * ViewModel 초기화
     */
    private fun initializeViewModel() {
        Log.d(TAG, "=== PhotoPreviewViewModel 초기화 시작 ===")

        // 초기 상태 설정
        _uiState.update { it.copy(isInitializing = true) }

        // PTPIP 연결 상태에 따라 선택적 이벤트 리스너 관리
        viewModelScope.launch {
            try {
                // PTPIP 연결 상태 확인
                val isPtpipConnected = cameraRepository.isPtpipConnected().first()
                Log.d(TAG, "📸 사진 미리보기 탭 진입 - PTPIP 연결 상태: $isPtpipConnected")

                if (isPtpipConnected) {
                    Log.d(TAG, "PTPIP 연결 상태 - 이벤트 리스너 유지 (파일 목록만 차단)")
                } else {
                    Log.d(TAG, "USB 연결 상태 - 이벤트 리스너 중지")

                    // 사진 미리보기 모드 활성화 (자동 시작 방지)
                    cameraRepository.setPhotoPreviewMode(true)

                    // 이벤트 리스너만 중단
                    cameraRepository.stopCameraEventListener()
                    Log.d(TAG, "✓ 이벤트 리스너 중단 완료")
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "이벤트 리스너 관리 실패 (무시하고 계속)", e)
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

        // PTPIP 연결 상태 관찰
        observePtpipConnection()

        // 카메라 초기화 상태 관찰
        observeCameraInitialization()
        
        // 구독 티어 관찰
        observeSubscriptionTier()
        
        // 에러 이벤트 관찰
        observeErrorEvents()

        // 사진 목록 변화 감지 및 썸네일 로드 (한 번만 설정)
        observePhotosAndLoadThumbnails()
    }

    /**
     * 카메라 연결 상태 관찰
     */
    private fun observeCameraConnection() {
        // 이미 active인 Job이 있으면 재실행하지 않음
        if (connectionObserveJob?.isActive == true) return

        Log.d(TAG, "=== observeCameraConnection 시작 ===")
        connectionObserveJob = viewModelScope.launch {
            globalManager.globalConnectionState.collect { connectionState ->
                val isConnected = connectionState.isAnyConnectionActive
                Log.d(TAG, "전역 카메라 연결 상태 변경: $isConnected")

                val previousConnected = _uiState.value.isConnected
                _uiState.update { it.copy(isConnected = isConnected) }

                if (isConnected && !previousConnected) {
                    Log.d(TAG, "카메라 연결됨 - PTPIP 상태 확인 후 사진 목록 처리")

                    // PTPIP 연결 상태를 확인하여 파일 목록 로딩 여부 결정
                    val isPtpipConnected = _uiState.value.isPtpipConnected
                    if (isPtpipConnected) {
                        Log.d(TAG, "⚠️ PTPIP 연결 상태 - 파일 목록 로딩 완전 차단")
                        // PTPIP 연결 시에는 어떤 파일 목록 작업도 수행하지 않음
                        return@collect
                    } else {
                        Log.d(TAG, "USB 연결 상태 - 파일 목록 불러오기")
                        photoListManager.loadInitialPhotos(
                            _uiState.value.isConnected,
                            _uiState.value.isPtpipConnected
                        )
                    }

                    // observePhotosAndLoadThumbnails()는 이미 setupObservers()에서 설정됨
                    // 여기서 별도로 호출하지 않음
                } else if (!isConnected && previousConnected) {
                    Log.d(TAG, "카메라 연결 해제됨")
                    _uiState.update { it.copy(isInitialized = false) }
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
     * PTPIP 연결 상태 관찰
     */
    private fun observePtpipConnection() {
        // 이미 active인 Job이 있으면 재실행하지 않음
        if (ptpipObserveJob?.isActive == true) return

        ptpipObserveJob = viewModelScope.launch {
            cameraRepository.isPtpipConnected().collect { isPtpipConnected ->
                _uiState.update { it.copy(isPtpipConnected = isPtpipConnected) }
            }
        }
    }

    /**
     * 사진 목록 변화를 감지하고 썸네일 로드
     */
    private fun observePhotosAndLoadThumbnails() {
        // 이미 active인 Job이 있으면 재실행하지 않음
        if (photosObserveJob?.isActive == true) return

        Log.d(TAG, "[TRACE] observePhotosAndLoadThumbnails() 호출됨")

        photosObserveJob = viewModelScope.launch {
            Log.d(TAG, "[TRACE] photoListManager.filteredPhotos.collect 시작")
            var collectCount = 0

            photoListManager.filteredPhotos.collect { photos ->
                collectCount++
                Log.d(TAG, "[TRACE] filteredPhotos collect 실행 #$collectCount - 사진 ${photos.size}개")

                if (photos.isNotEmpty()) {
                    Log.d(TAG, "사진 목록 변화 감지 (${photos.size}개) - 썸네일 로딩 시작")
                    photoImageManager.loadThumbnailsForPhotos(photos)
                }
            }
        }
    }

    /**
     * 카메라 초기화 상태 관찰
     */
    private fun observeCameraInitialization() {
        // 이미 active인 Job이 있으면 재실행하지 않음
        if (initObserveJob?.isActive == true) return

        initObserveJob = viewModelScope.launch {
            cameraRepository.isInitializing().collect { isInitializing ->
                _uiState.update { it.copy(isInitializing = isInitializing) }
            }
        }
    }

    /**
     * 구독 티어 관찰
     */
    private fun observeSubscriptionTier() {
        // 이미 active인 Job이 있으면 재실행하지 않음
        if (tierObserveJob?.isActive == true) return

        tierObserveJob = viewModelScope.launch {
            getSubscriptionUseCase.getSubscriptionTier().collect { tier ->
                Log.d(TAG, "사용자 구독 티어 변경: $tier")
                _uiState.update { it.copy(currentTier = tier) }

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
        // 이미 active인 Job이 있으면 재실행하지 않음
        if (errorObserveJob?.isActive == true) return

        errorObserveJob = viewModelScope.launch {
            errorHandlingManager.errorEvent.collect { errorEvent ->
                emitError(errorEvent.message)
                Log.e(TAG, "에러 이벤트 수신: ${errorEvent.type} - ${errorEvent.message}")
            }
        }
    }

    // MARK: - Public Methods (UI에서 호출)

    /**
     * 초기 사진 목록 로드 (PhotoListManager에 위임)
     */
    fun loadInitialPhotos() {
        if (_uiState.value.isPtpipConnected) {
            Log.d(TAG, "PTPIP 연결 상태로 인해 파일 목록 로딩 차단")
            return
        }
        photoListManager.loadInitialPhotos(
            _uiState.value.isConnected,
            _uiState.value.isPtpipConnected
        )
    }

    /**
     * 다음 페이지 로드 (PhotoListManager에 위임)
     */
    fun loadNextPage() {
        if (_uiState.value.isPtpipConnected) {
            Log.d(TAG, "PTPIP 연결 상태로 인해 파일 목록 로딩 차단")
            return
        }
        photoListManager.loadNextPage(_uiState.value.isPtpipConnected)

        // 다음 페이지 로드 시에는 observePhotosAndLoadThumbnails()의 collect가
        // 자동으로 filteredPhotos 변화를 감지하여 썸네일 로딩을 처리함
        // 직접 호출하지 않음 (중복 방지)
    }

    /**
     * 사진 목록 새로고침 (PhotoListManager에 위임)
     */
    fun refreshPhotos() {
        if (_uiState.value.isPtpipConnected) {
            Log.d(TAG, "PTPIP 연결 상태로 인해 파일 목록 로딩 차단")
            return
        }
        Log.d(TAG, "사진 목록 새로고침")
        photoListManager.refreshPhotos(_uiState.value.isConnected, _uiState.value.isPtpipConnected)
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
        photoListManager.onPhotoIndexReached(currentIndex, _uiState.value.isPtpipConnected)
    }

    /**
     * 사진 선택 (UI 상태 업데이트)
     */
    fun selectPhoto(photo: CameraPhoto?) {
        if (photo != null && !handleRawFileAccess(photo)) {
            return
        }
        
        _uiState.update { it.copy(selectedPhoto = photo) }
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
            emitError(message)
            return false
        }
        return true
    }

    @Deprecated("SharedFlow 이벤트로 대체됨")
    fun clearError() {
        // SharedFlow 사용으로 더 이상 필요 없음
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
            - PTPIP 연결 상태: ${_uiState.value.isPtpipConnected}
        """.trimIndent()
        )

        // 각 매니저의 상태도 로깅
        photoListManager.logCurrentState()
        photoSelectionManager.logCurrentState()
    }

    /**
     * 탭 이탈 시 이벤트 리스너 재시작 처리
     */
    fun onTabExit() {
        Log.d(TAG, "📸 사진 미리보기 탭 이탈 감지 - 연결 상태별 처리")

        viewModelScope.launch {
            try {
                val currentConnected = _uiState.value.isConnected
                val isPtpipConnected = _uiState.value.isPtpipConnected

                Log.d(TAG, "📸 사진 미리보기 탭 종료 - 연결상태: $currentConnected, PTPIP: $isPtpipConnected")

                if (currentConnected) {
                    if (isPtpipConnected) {
                        Log.d(TAG, "PTPIP 연결 상태 - 이벤트 리스너 재시작 불필요")
                        // PTPIP에서는 이벤트 리스너가 계속 실행 중이므로 재시작 불필요
                    } else {
                        Log.d(TAG, "USB 연결 상태 - 이벤트 리스너 재시작 처리")

                        // 사진 미리보기 모드 비활성화 (먼저 실행)
                        cameraRepository.setPhotoPreviewMode(false)
                        Log.d(TAG, "📴 사진 미리보기 모드 비활성화 완료")

                        // 네이티브 작업 재개
                        try {
                            com.inik.camcon.CameraNative.resumeOperations()
                            Log.d(TAG, "▶️ 네이티브 작업 재개 완료")
                        } catch (e: Exception) {
                            Log.w(TAG, "네이티브 작업 재개 실패 (무시)", e)
                        }

                        // 카메라 연결 상태 재확인
                        kotlinx.coroutines.delay(200) // 지연 시간

                        val isStillConnected = try {
                            cameraRepository.isCameraConnected().first()
                        } catch (e: Exception) {
                            Log.w(TAG, "연결 상태 확인 실패", e)
                            false
                        }

                        if (isStillConnected) {
                            Log.d(TAG, "🔄 카메라 여전히 연결됨, 이벤트 리스너 재시작 시도")

                            try {
                                cameraRepository.startCameraEventListener()
                                Log.d(TAG, "✅ 이벤트 리스너 재시작 성공")
                            } catch (e: Exception) {
                                Log.e(TAG, "이벤트 리스너 재시작 실패", e)

                                // 재시도 1번 더
                                kotlinx.coroutines.delay(500)
                                try {
                                    cameraRepository.startCameraEventListener()
                                    Log.d(TAG, "✅ 이벤트 리스너 재시작 성공 (재시도)")
                                } catch (e2: Exception) {
                                    Log.e(TAG, "이벤트 리스너 재시작 최종 실패", e2)
                                }
                            }
                        } else {
                            Log.w(TAG, "카메라 연결 해제됨, 이벤트 리스너 재시작 건너뛰기")
                        }
                    }
                } else {
                    Log.d(TAG, "카메라 연결되지 않음, 이벤트 리스너 작업 건너뛰기")
                }
            } catch (e: Exception) {
                Log.e(TAG, "탭 이탈 시 이벤트 리스너 관리 실패", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "=== PhotoPreviewViewModel 정리 시작 ===")

        // 사진 미리보기 탭에서 나갈 때 이벤트 리스너 재시작
        viewModelScope.launch {
            try {
                val currentConnected = _uiState.value.isConnected
                val isPtpipConnected = _uiState.value.isPtpipConnected

                Log.d(TAG, "📸 사진 미리보기 탭 종료 - 연결상태: $currentConnected, PTPIP: $isPtpipConnected")

                if (currentConnected) {
                    if (isPtpipConnected) {
                        Log.d(TAG, "PTPIP 연결 상태 - 이벤트 리스너 재시작 불필요")
                        // PTPIP에서는 이벤트 리스너가 계속 실행 중이므로 재시작 불필요
                    } else {
                        Log.d(TAG, "USB 연결 상태 - 이벤트 리스너 재시작 처리")

                        // 사진 미리보기 모드 비활성화 (먼저 실행)
                        cameraRepository.setPhotoPreviewMode(false)
                        Log.d(TAG, "📴 사진 미리보기 모드 비활성화 완료")

                        // 네이티브 작업 재개
                        try {
                            com.inik.camcon.CameraNative.resumeOperations()
                            Log.d(TAG, "▶️ 네이티브 작업 재개 완료")
                        } catch (e: Exception) {
                            Log.w(TAG, "네이티브 작업 재개 실패 (무시)", e)
                        }

                        // 카메라 연결 상태 재확인
                        kotlinx.coroutines.delay(200) // 더 긴 지연

                        val isStillConnected = try {
                            cameraRepository.isCameraConnected().first()
                        } catch (e: Exception) {
                            Log.w(TAG, "연결 상태 확인 실패", e)
                            false
                        }

                        if (isStillConnected) {
                            Log.d(TAG, "🔄 카메라 여전히 연결됨, 이벤트 리스너 재시작 시도")

                            try {
                                cameraRepository.startCameraEventListener()
                                Log.d(TAG, "✅ 이벤트 리스너 재시작 성공")
                            } catch (e: Exception) {
                                Log.e(TAG, "이벤트 리스너 재시작 실패", e)

                                // 재시도 1번 더
                                kotlinx.coroutines.delay(500)
                                try {
                                    cameraRepository.startCameraEventListener()
                                    Log.d(TAG, "✅ 이벤트 리스너 재시작 성공 (재시도)")
                                } catch (e2: Exception) {
                                    Log.e(TAG, "이벤트 리스너 재시작 최종 실패", e2)
                                }
                            }
                        } else {
                            Log.w(TAG, "카메라 연결 해제됨, 이벤트 리스너 재시작 건너뛰기")
                        }
                    }
                } else {
                    Log.d(TAG, "카메라 연결되지 않음, 이벤트 리스너 작업 건너뛰기")
                }
            } catch (e: Exception) {
                Log.e(TAG, "PhotoPreview 정리 중 예외 발생", e)
            }
        }

        // 매니저들 정리
        try {
            photoListManager.cleanup()
            photoImageManager.cleanup()
            photoSelectionManager.clearSelection()
            Log.d(TAG, "매니저들 정리 완료")
        } catch (e: Exception) {
            Log.w(TAG, "매니저 정리 중 예외", e)
        }

        Log.d(TAG, "=== PhotoPreviewViewModel 정리 완료 ===")
    }

    // MARK: - 멀티 선택 관련 메서드들 (PhotoSelectionManager에 위임)

    /**
     * 멀티 선택 모드 시작
     */
    fun startMultiSelectMode(initialPhotoPath: String) {
        photoSelectionManager.startMultiSelectMode(initialPhotoPath)
        _uiState.update { it.copy(selectedPhoto = null) }
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
}