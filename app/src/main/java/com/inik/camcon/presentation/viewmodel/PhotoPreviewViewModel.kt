package com.inik.camcon.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.R
import com.inik.camcon.data.datasource.nativesource.NikonApplicationModeManager
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.presentation.viewmodel.state.ErrorHandlingManager
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.utils.resolve
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.usecase.camera.DeleteCameraFileUseCase
import com.inik.camcon.domain.usecase.camera.ResumeNativeOperationsUseCase
import com.inik.camcon.utils.LogMask
import com.inik.camcon.presentation.viewmodel.photo.FileTypeFilter
import com.inik.camcon.presentation.viewmodel.photo.PhotoImageManager
import com.inik.camcon.presentation.viewmodel.photo.PhotoListManager
import com.inik.camcon.presentation.viewmodel.photo.PhotoSelectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    // 미리보기 탭/RAW·전체포맷 접근 허용 여부. ValidateFeatureAccessUseCase 단일 지점 판정 결과(CLAUDE.md §2).
    val canAccessRawFormats: Boolean = false,
    val isPtpipConnected: Boolean = false
)

/**
 * 일회성 UI 이벤트 (스낵바/토스트 메시지)
 *
 * - [ShowError]    : 에러 토스트(재시도 버튼 노출 대상).
 * - [ShowInfo]     : 성공/안내 토스트(재시도 없음).
 * - [ShowFreeTierNotice] : FREE 티어 2000px 축소 사전 고지 — '업그레이드' 액션 동반(필수4).
 */
sealed class PhotoPreviewUiEvent {
    data class ShowError(val message: String) : PhotoPreviewUiEvent()
    data class ShowInfo(val message: String) : PhotoPreviewUiEvent()
    data class ShowFreeTierNotice(val message: String) : PhotoPreviewUiEvent()
}

/**
 * 다중선택 다운로드 진행 상태(필수1).
 *
 * @param inProgress 진행 중 여부. false 면 UI 에서 진행 표시 숨김.
 * @param completed  완료(성공+실패)된 항목 수.
 * @param total      이번 배치 전체 항목 수.
 * @param failed     실패한 항목 수.
 */
data class MultiDownloadProgress(
    val inProgress: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val failed: Int = 0
)

/**
 * 사진 미리보기를 위한 ViewModel - MVVM 패턴 준수
 * 단일책임: UI 상태 관리 및 매니저들 간의 조정만 담당
 * View Layer와 Domain Layer 사이의 중재자 역할
 */
@HiltViewModel
class PhotoPreviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraRepository: CameraRepository,
    private val globalManager: CameraConnectionGlobalManager,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val appSettingsRepository: AppSettingsRepository,
    private val validateImageFormatUseCase: ValidateImageFormatUseCase,
    private val validateFeatureAccessUseCase: ValidateFeatureAccessUseCase,

    // 매니저 의존성 주입 (단일책임원칙 적용)
    private val photoListManager: PhotoListManager,
    private val photoImageManager: PhotoImageManager,
    private val photoSelectionManager: PhotoSelectionManager,
    private val errorHandlingManager: ErrorHandlingManager,
    private val resumeNativeOperationsUseCase: ResumeNativeOperationsUseCase,
    private val deleteCameraFileUseCase: DeleteCameraFileUseCase,
    private val nikonApplicationModeManager: NikonApplicationModeManager
) : ViewModel() {

    companion object {
        private const val TAG = "사진미리보기뷰모델"

        // 다중선택 일괄 다운로드 동시성 상한.
        // 네이티브 커맨드 큐가 단일 워커로 직렬 처리하고 JNI 대기 타이머가 '제출 시점' 기산이라,
        // 전건을 동시에 발사하면 후순위 항목이 실행 시작 전에 60초 타임아웃으로 구조적 실패한다.
        // 유계 동시성으로 '제출 시점 ≈ 서비스 시점'을 근접시켜 각 항목에 온전한 대기 창을 준다.
        private const val MAX_CONCURRENT_DOWNLOADS = 3
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

    private fun emitInfo(message: String) {
        viewModelScope.launch {
            _uiEvent.emit(PhotoPreviewUiEvent.ShowInfo(message))
        }
    }

    private fun emitFreeTierNotice(message: String) {
        viewModelScope.launch {
            _uiEvent.emit(PhotoPreviewUiEvent.ShowFreeTierNotice(message))
        }
    }

    // 다중선택 다운로드 진행 상태(필수1).
    private val _multiDownloadProgress = MutableStateFlow(MultiDownloadProgress())
    val multiDownloadProgress: StateFlow<MultiDownloadProgress> =
        _multiDownloadProgress.asStateFlow()

    // 진행 중인 다중선택 배치 Job(유계 동시성). 재진입 방지·정리에 사용.
    private var batchDownloadJob: Job? = null

    // 단발(단일 사진) 다운로드 결과 통지를 받을 경로. 다중선택 배치와 구분.
    private var singleDownloadPath: String? = null

    // FREE 티어 축소 사전 고지는 세션당 1회만 노출(반복 방해 방지).
    private var freeTierNoticeShown = false

    // 다운로드 결과 구독 Job
    private var downloadResultObserveJob: Job? = null

    // 매니저들의 상태 노출 (읽기 전용)
    val photos = photoListManager.filteredPhotos
    val allPhotos = photoListManager.allPhotos
    val isLoadingPhotos = photoListManager.isLoading
    val isStorageUnsupported = photoListManager.isStorageUnsupported

    // 카드 보기(소니 콘텐츠 전송 모드) 전환 상태와 실패 사유.
    // ACTIVE 인 동안 촬영·라이브뷰가 카메라 쪽에서 막히므로 화면이 그 사실을 알려야 한다.
    val cardBrowseState = photoListManager.cardBrowseState
    val cardBrowseError = photoListManager.cardBrowseError

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

    // RAW 다운로드 허용 설정 (ValidateImageFormatUseCase와 동일 조건 유지)
    private val _isRawDownloadEnabled = MutableStateFlow(true)

    // M12: 마지막 다운로드 시도한 사진 (재시도용)
    private val _lastFailedDownload = MutableStateFlow<CameraPhoto?>(null)
    val lastFailedDownload: StateFlow<CameraPhoto?> = _lastFailedDownload.asStateFlow()

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
        // 초기 상태 설정
        _uiState.update { it.copy(isInitializing = true) }

        // 이벤트 리스너 중단·재개와 앱 모드 토글은 모두 onTabEnter()/onTabExit()이 담당한다.
        // 이 함수는 ViewModel 생성 시 1회만 실행되므로 여기에 두면 탭 재진입에서 토글이 걸리지
        // 않는다(실측 2026-08-20 09:00). 진입 처리를 한 곳에 모아 USB/Wi-Fi 분기 드리프트도 막는다.

        // 옵저버들 설정
        setupObservers()
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

        // RAW 다운로드 허용 설정 관찰 (ValidateImageFormatUseCase와 동일 조건 유지)
        viewModelScope.launch {
            appSettingsRepository.isRawFileDownloadEnabled.collect { enabled ->
                _isRawDownloadEnabled.value = enabled
            }
        }

        // 에러 이벤트 관찰
        observeErrorEvents()

        // 다운로드 결과 관찰 (성공/실패 피드백·다중선택 진행 집계)
        observeDownloadResults()

        // 사진 목록 변화 감지 및 썸네일 로드 (한 번만 설정)
        observePhotosAndLoadThumbnails()
    }

    /**
     * 풀이미지 다운로드 결과 관찰(필수1).
     *
     * 단일(명시적) 다운로드 결과만 처리한다 — 다중선택 배치는 [downloadSelectedPhotos] 가
     * 유계 동시성으로 직접 await 하며 집계하므로 이 SharedFlow 경유가 아니다.
     * 인접 프리로드 등 추적되지 않는 경로의 결과는 조용히 무시한다(UX 노이즈 방지).
     */
    private fun observeDownloadResults() {
        if (downloadResultObserveJob?.isActive == true) return

        downloadResultObserveJob = viewModelScope.launch {
            photoImageManager.downloadResult.collect { result ->
                if (singleDownloadPath == result.photoPath) {
                    handleSingleDownloadResult(result)
                }
            }
        }
    }

    /**
     * 단일 사진 다운로드 결과 처리.
     */
    private fun handleSingleDownloadResult(result: PhotoImageManager.DownloadResult) {
        singleDownloadPath = null
        if (result.isSuccess) {
            _lastFailedDownload.value = null
            emitInfo(context.getString(R.string.gallery_v2_download_success))
        } else {
            // 실패는 재시도 대상으로 유지(_lastFailedDownload). 친화적 메시지.
            emitError(context.getString(R.string.gallery_v2_download_failed))
        }
    }

    /**
     * 카메라 연결 상태 관찰
     */
    private fun observeCameraConnection() {
        // 이미 active인 Job이 있으면 재실행하지 않음
        if (connectionObserveJob?.isActive == true) return

        connectionObserveJob = viewModelScope.launch {
            globalManager.globalConnectionState.collect { connectionState ->
                val isConnected = connectionState.isAnyConnectionActive
                Log.d(TAG, "전역 카메라 연결 상태 변경: $isConnected")

                val previousConnected = _uiState.value.isConnected
                _uiState.update { it.copy(isConnected = isConnected) }

                if (isConnected && !previousConnected) {
                    // USB/PTPIP 공통 — 카메라 파일 목록 불러오기
                    Log.d(TAG, "카메라 연결됨 - 파일 목록 불러오기")
                    photoListManager.loadInitialPhotos(_uiState.value.isConnected)

                    // observePhotosAndLoadThumbnails()는 이미 setupObservers()에서 설정됨
                    // 여기서 별도로 호출하지 않음
                } else if (!isConnected && previousConnected) {
                    Log.d(TAG, "카메라 연결 해제됨")
                    // 카드 보기가 켜진 상태로 연결이 끊겼다면 앱 상태를 되돌린다. 카메라는 이미
                    // 사라져 전환 명령을 보낼 수 없고, 다음에 켜질 때 기본 모드로 시작한다.
                    photoListManager.resetCardBrowseOnDisconnect()
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
                val previous = _uiState.value.isPtpipConnected
                _uiState.update { it.copy(isPtpipConnected = isPtpipConnected) }
                // PTPIP 활성 전환 시 카메라 파일 목록 로드 (카메라 연결 이벤트 후행 케이스 대비).
                if (isPtpipConnected && !previous) {
                    Log.d(TAG, "PTPIP 활성 전환 감지 - 파일 목록 로드 트리거")
                    photoListManager.loadInitialPhotos(isConnected = true)
                }
            }
        }
    }

    /**
     * 사진 목록 변화를 감지하고 썸네일 로드
     */
    private fun observePhotosAndLoadThumbnails() {
        // 이미 active인 Job이 있으면 재실행하지 않음
        if (photosObserveJob?.isActive == true) return

        photosObserveJob = viewModelScope.launch {
            photoListManager.filteredPhotos.collect { photos ->
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
                _uiState.update {
                    it.copy(
                        currentTier = tier,
                        canAccessRawFormats = validateFeatureAccessUseCase.isPhotoPreviewAllowed(tier)
                    )
                }

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
                // LOW = 재시도가 무의미한 안내성 이벤트(예: 소니 PC리모트의 카드 탐색 미지원).
                // 에러 토스트로 올리면 '재시도' 버튼이 같은 실패를 반복 유도한다(실측: 연타로
                // 목록 조회 11회) → 자동 소멸 안내 토스트로 구분 표시.
                if (errorEvent.severity == com.inik.camcon.domain.manager.ErrorSeverity.LOW) {
                    emitInfo(errorEvent.message)
                    Log.i(TAG, "안내 이벤트 수신: ${errorEvent.type} - ${errorEvent.message}")
                } else {
                    emitError(errorEvent.message)
                    Log.e(TAG, "에러 이벤트 수신: ${errorEvent.type} - ${errorEvent.message}")
                }
            }
        }
    }

    // MARK: - 공개 메서드 (UI에서 호출)

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

        // 다음 페이지 로드 시에는 observePhotosAndLoadThumbnails()의 collect가
        // 자동으로 filteredPhotos 변화를 감지하여 썸네일 로딩을 처리함
        // 직접 호출하지 않음 (중복 방지)
    }

    /**
     * 사진 목록 새로고침 (PhotoListManager에 위임)
     */
    fun refreshPhotos() {
        Log.d(TAG, "사진 목록 새로고침")
        photoListManager.refreshPhotos(_uiState.value.isConnected)
    }

    /**
     * 카드 보기로 전환 (PhotoListManager에 위임).
     *
     * 성공하면 카드가 보이는 대신 촬영·라이브뷰가 멈춘다. 화면은 [cardBrowseState] 를 보고
     * 그 사실을 사용자에게 알려야 한다.
     */
    fun enterCardBrowse() {
        Log.d(TAG, "카드 보기 전환 요청")
        photoListManager.enterCardBrowse()
    }

    /**
     * 카드 보기에서 나와 촬영 모드로 복귀 (PhotoListManager에 위임).
     */
    fun exitCardBrowse() {
        Log.d(TAG, "카드 보기 이탈 요청")
        photoListManager.exitCardBrowse()
    }

    /** 카드 보기 전환 실패 안내를 사용자가 확인했을 때 호출한다. */
    fun clearCardBrowseError() {
        photoListManager.clearCardBrowseError()
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
        
        _uiState.update { it.copy(selectedPhoto = photo) }
    }

    /**
     * 사진 다운로드 (PhotoImageManager에 위임).
     *
     * 주의: 이 경로는 풀스크린 진입 자동 프리로드·인접 프리로드에서도 호출되므로
     * 성공/실패 토스트나 FREE 고지를 띄우지 않는다(노이즈 방지). 사용자가 다운로드
     * 버튼을 명시적으로 누른 경우는 [downloadPhotoExplicit] 를 사용한다(필수1/4).
     */
    fun downloadPhoto(photo: CameraPhoto) {
        if (!handleRawFileAccess(photo)) {
            return
        }
        photoImageManager.downloadFullImage(photo.path, _uiState.value.currentTier)
    }

    /**
     * 사용자가 다운로드 버튼을 명시적으로 누른 단일 다운로드(필수1/4).
     *
     * - 성공/실패 토스트를 노출하고, 실패 시 재시도 대상으로 보존.
     * - FREE 티어면 2000px 축소를 사전 고지(세션당 1회).
     */
    fun downloadPhotoExplicit(photo: CameraPhoto) {
        if (!handleRawFileAccess(photo)) {
            return
        }

        maybeNotifyFreeTierResolution()

        // M12 — 단일 사진 재시도용 추적
        _lastFailedDownload.value = photo
        // 다중선택 배치가 진행 중이 아닐 때만 단일 결과 추적(배치 경로와 충돌 방지).
        if (!_multiDownloadProgress.value.inProgress) {
            singleDownloadPath = photo.path
        }
        // 명시적 다운로드 — 기기 저장(MediaStore)까지 영속화.
        photoImageManager.downloadFullImage(
            photo.path,
            _uiState.value.currentTier,
            persistToDevice = true
        )
    }

    /**
     * 필수4 — FREE 티어 사용자에게 원본이 2000px 로 축소됨을 사전 고지.
     * 세션당 1회만 노출하여 반복 방해를 막는다. '업그레이드' CTA 는 UI 측에서 처리.
     */
    private fun maybeNotifyFreeTierResolution() {
        if (freeTierNoticeShown) return
        if (_uiState.value.currentTier == SubscriptionTier.FREE) {
            freeTierNoticeShown = true
            emitFreeTierNotice(context.getString(R.string.gallery_v2_free_resolution_notice))
        }
    }

    /**
     * H7-A — 사진 삭제. 카메라/로컬 모두에서 제거.
     *
     * 카메라 측 path는 libgphoto2 풀경로 (예: "/store_00010001/DCIM/100NIKON/DSC_0001.JPG").
     * 마지막 '/' 기준으로 folder/filename 분리 후 DeleteCameraFileUseCase 호출.
     * folder/filename 추출 실패(슬래시 없음) 시 카메라 측 삭제는 스킵하고 로컬만 정리.
     */
    fun deletePhoto(photo: CameraPhoto) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "사진 삭제 시도: ${LogMask.path(photo.name)}")

                val lastSlash = photo.path.lastIndexOf('/')
                if (lastSlash > 0 && lastSlash < photo.path.length - 1) {
                    val folder = photo.path.substring(0, lastSlash)
                    val filename = photo.path.substring(lastSlash + 1)
                    deleteCameraFileUseCase(folder, filename).fold(
                        onSuccess = {
                            Log.d(TAG, "카메라 측 사진 삭제 성공: ${LogMask.path(folder)} / ${LogMask.path(filename)}")
                        },
                        onFailure = { e ->
                            Log.w(TAG, "카메라 측 사진 삭제 실패 (로컬 정리는 계속): ${LogMask.path(photo.name)}", e)
                            // 파괴적 동작인데 무통지면 목록 갱신 후 사진이 되살아나 '삭제 안 됨'처럼 보인다.
                            // 카메라 측 삭제 실패(예: Nikon AccessDenied 0x200F)를 사용자에게 알린다.
                            emitError(context.getString(R.string.photo_delete_camera_failed))
                        }
                    )
                } else {
                    Log.w(TAG, "카메라 path 형식이 예상과 다름 — 카메라 측 삭제 스킵: ${LogMask.path(photo.path)}")
                }

                // 로컬 파일도 정리.
                runCatching {
                    val localFile = java.io.File(photo.path)
                    if (localFile.exists()) localFile.delete()
                }

                // 선택 해제 및 목록 갱신
                if (_uiState.value.selectedPhoto?.path == photo.path) {
                    _uiState.update { it.copy(selectedPhoto = null) }
                }
                photoListManager.refreshPhotos(_uiState.value.isConnected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "사진 삭제 중 예외", e)
                emitError(e.message ?: "delete failed")
            }
        }
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
     * 카메라 사진 로드 (별칭). USB/PTPIP 공통 카메라 파일 목록 경로.
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

    /**
     * M12 — 마지막 실패한 다운로드 재시도. 없으면 전체 새로고침으로 폴백.
     */
    fun retryDownload(photo: CameraPhoto?) {
        val target = photo ?: _lastFailedDownload.value
        if (target != null) {
            Log.d(TAG, "단일 사진 재시도: ${LogMask.path(target.name)}")
            // 형제 다운로드 경로(preloadAdjacentImages·quickPreloadCurrentImage)와 동일하게
            // ValidateImageFormatUseCase RAW 게이트를 경유한다(재시도 우회 방지). 차단 시 handleRawFileAccess가 통지.
            if (!handleRawFileAccess(target)) {
                return
            }
            // 재시도도 명시적 액션이므로 단일 결과 추적으로 성공/실패 토스트 노출(필수1).
            if (!_multiDownloadProgress.value.inProgress) {
                singleDownloadPath = target.path
            }
            // 명시적 재시도 — 기기 저장(MediaStore)까지 영속화.
            photoImageManager.downloadFullImage(
                target.path,
                _uiState.value.currentTier,
                persistToDevice = true
            )
        } else {
            Log.d(TAG, "마지막 실패 다운로드 없음 - 전체 새로고침 폴백")
            loadCameraPhotos()
        }
    }

    // MARK: - 비공개 헬퍼 메서드

    /**
     * RAW 파일 접근 권한 처리.
     *
     * RAW 게이팅 분기는 [ValidateImageFormatUseCase] 단일 지점에 위임한다 (CLAUDE.md §2).
     * 본 함수는 캐싱된 tier / rawDownloadEnabled 를 동기 메서드 [ValidateImageFormatUseCase.resolveRawAccess]
     * 에 넘겨 결정을 받고, 차단된 경우 SharedFlow 로 에러 메시지를 emit 한다.
     *
     * @return 접근 허용 시 true, 차단 시 false.
     */
    private fun handleRawFileAccess(photo: CameraPhoto): Boolean {
        val result = validateImageFormatUseCase.resolveRawAccess(
            filePath = photo.path,
            currentTier = _uiState.value.currentTier,
            isRawDownloadEnabled = _isRawDownloadEnabled.value
        )
        if (!result.isSupported) {
            result.restrictionMessage?.let { emitError(it.resolve(context)) }
            return false
        }
        return true
    }

    /**
     * 파일 경로가 RAW 인지 판정한다. RAW 판정은 [ValidateImageFormatUseCase] 단일 지점에 위임(CLAUDE.md §2).
     * 필름 에디터 진입점 게이팅(RAW 비노출)에 사용한다.
     */
    fun isRawFile(path: String): Boolean = validateImageFormatUseCase.isRawFile(path)

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
            - 선택된 사진: ${LogMask.path(_uiState.value.selectedPhoto?.name)}
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
    /**
     * 미리보기(카드 탐색) 탭 진입. **화면 진입마다** 호출되어야 한다 — ViewModel 초기화는
     * 1회뿐이라 거기에 두면 재진입 시 앱 모드가 켜진 채로 카드 탐색을 시도하게 된다.
     *
     * 앱 모드가 켜져 있으면 카드 저장소가 PTP 목록에서 사라지므로(저장소 제거 이벤트)
     * 반드시 끈 뒤 목록을 읽는다. 상태가 실제로 바뀐 경우에만 목록을 다시 읽어
     * 불필요한 재조회를 피한다(네이티브 폴더 캐시가 함께 무효화된 상태이므로 재조회가 필요).
     */
    fun onTabEnter() {
        viewModelScope.launch {
            // 카드 탐색 중에는 이벤트 폴을 멈춘다 — USB/Wi-Fi 공통.
            //
            // 유휴 Nikon PTP/IP 세션의 EVENT_POLL 1회는 `camera_wait_for_event` 안에서
            // 벤더 이벤트 조회와 전송 큐 확인 왕복을 돈다.
            // 전송 큐가 비어 있으면 카메라 응답이 늦어 PTPIP_DEFAULT_TIMEOUT(2.5s)이 만료되고,
            // 폴 1회가 ~5초간 단일 PTP 세션과 커맨드 큐 워커를 점유한다. 그동안 사용자가 낸
            // FILE_LIST/썸네일은 뒤에 줄을 선다(실측 2026-08-26: 파일 목록 조회가 4.80초
            // 대기 후 실제 조회는 0ms).
            //
            // 기존에는 USB 만 리스너를 껐고 Wi-Fi 는 켠 채로 뒀다. 카드 탐색 구간에는 무선
            // 수신이 필요 없으므로 두 경로의 동작을 통일한다. 탭 이탈 시 재개한다.
            try {
                cameraRepository.setPhotoPreviewMode(true)
                cameraRepository.stopCameraEventListener()
                Log.d(TAG, "✓ 카드 탐색 진입 — 이벤트 폴 중단")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "탭 진입 이벤트 리스너 중단 실패 (무시하고 계속)", e)
            }

            try {
                if (nikonApplicationModeManager.enterCardBrowsing()) {
                    Log.d(TAG, "앱 모드 해제로 저장소 구성 변경 — 사진 목록 재조회")
                    photoListManager.loadInitialPhotos(_uiState.value.isConnected)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "탭 진입 앱 모드 처리 실패 (무시하고 계속)", e)
            }
        }
    }

    fun onTabExit() {
        Log.d(TAG, "📸 사진 미리보기 탭 이탈 감지 - 연결 상태별 처리")

        // 탭을 떠나면 진행 중인 썸네일 순차 로딩을 즉시 중단(카메라 큐 점유 해제).
        photoImageManager.cancelThumbnailLoading()

        // 카드 보기(소니 콘텐츠 전송 모드)를 켠 채 탭을 떠나면 카메라가 그 모드에 갇혀 촬영과
        // 라이브뷰가 계속 막힌다. 이탈은 카메라 왕복이 필요한데 아래 viewModelScope 는 탭 전환
        // 직후 취소될 수 있으므로, 앱 scope 에서 처리하는 매니저에 위임한다. 카드 보기가 꺼져
        // 있으면 매니저가 상태를 보고 그냥 돌아간다. 아래 이벤트 리스너 재시작보다 먼저 명령을
        // 걸어야 카메라가 촬영 모드로 돌아간 뒤 리스너가 붙는다.
        photoListManager.exitCardBrowse()

        viewModelScope.launch {
            try {
                val currentConnected = _uiState.value.isConnected
                val isPtpipConnected = _uiState.value.isPtpipConnected

                Log.d(TAG, "📸 사진 미리보기 탭 종료 - 연결상태: $currentConnected, PTPIP: $isPtpipConnected")

                // 카드 탐색 구간 이탈 — 앱 모드를 켜서 본체 재생(▶)을 해방한다.
                // 니콘 사양상 PC 연결 중 본체 재생은 앱 모드에서만 허용된다(벤더 사양).
                nikonApplicationModeManager.leaveCardBrowsing()

                resumeEventListenerAfterCardBrowsing(currentConnected, isPtpipConnected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "탭 이탈 시 이벤트 리스너 관리 실패", e)
            }
        }
    }

    /**
     * 카드 탐색 이탈 시 이벤트 폴을 되살린다. [onTabExit]·[onCleared] 공통 진입점.
     *
     * ⚠️ **Wi-Fi 경로를 [CameraRepository.isCameraConnected] 로 게이팅하면 안 된다.**
     * 그 Flow 는 `connectionManager.isConnected || eventManager.isEventListenerActive` 이라
     * ([CameraLifecycleRepositoryImpl] 참조) 우리가 방금 리스너를 끈 상태에서는 false 로 떨어질
     * 수 있다. 그 값으로 재시작을 게이팅하면 리스너가 영영 살아나지 않는다 — STA 에서
     * `isPtpipConnected` 가 false 로 남아 미리보기 탭이 USB 분기로 빠지며 Wi-Fi 리스너를
     * 죽였던 회귀와 정확히 같은 함정이다. Wi-Fi 는 [isPtpipConnected] 로만 판단한다.
     */
    private suspend fun resumeEventListenerAfterCardBrowsing(
        isConnected: Boolean,
        isPtpipConnected: Boolean
    ) {
        if (!isConnected && !isPtpipConnected) {
            Log.d(TAG, "카메라 연결되지 않음, 이벤트 리스너 작업 건너뛰기")
            return
        }

        // 미리보기 모드 해제가 먼저다 — 이 플래그가 남으면 BackgroundSyncService 의 재시작
        // 감독이 영구 억제된다(USB 로 진입했다가 Wi-Fi 로 전환된 경우 포함).
        cameraRepository.setPhotoPreviewMode(false)
        Log.d(TAG, "📴 사진 미리보기 모드 비활성화 완료")

        if (!isPtpipConnected) {
            // USB 는 네이티브 작업을 재개하고, 케이블이 빠졌을 수 있으므로 연결을 재확인한다.
            try {
                resumeNativeOperationsUseCase()
                Log.d(TAG, "▶️ 네이티브 작업 재개 완료")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "네이티브 작업 재개 실패 (무시)", e)
            }

            kotlinx.coroutines.delay(200)

            val isStillConnected = try {
                cameraRepository.isCameraConnected().first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "연결 상태 확인 실패", e)
                false
            }
            if (!isStillConnected) {
                Log.w(TAG, "카메라 연결 해제됨, 이벤트 리스너 재시작 건너뛰기")
                return
            }
        }

        Log.d(TAG, "🔄 이벤트 리스너 재시작 시도 (PTPIP=$isPtpipConnected)")
        startEventListenerWithRetry()
    }

    /**
     * 이벤트 리스너 시작을 1회 재시도까지 수행한다.
     *
     * `startCameraEventListener()` 는 예외 대신 `Result.failure` 로도 실패를 알리므로 둘 다 본다 —
     * 예외만 보던 기존 경로는 조용한 실패에서 재시도가 걸리지 않았다.
     */
    private suspend fun startEventListenerWithRetry() {
        repeat(2) { attempt ->
            val started = try {
                cameraRepository.startCameraEventListener().getOrDefault(false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "이벤트 리스너 재시작 예외 (시도 ${attempt + 1})", e)
                false
            }
            if (started) {
                Log.d(TAG, "✅ 이벤트 리스너 재시작 성공 (시도 ${attempt + 1})")
                return
            }
            if (attempt == 0) kotlinx.coroutines.delay(500)
        }
        Log.e(TAG, "이벤트 리스너 재시작 최종 실패")
    }

    override fun onCleared() {
        super.onCleared()

        // 사진 미리보기 탭에서 나갈 때 이벤트 리스너 재시작.
        // onCleared 시점에는 viewModelScope 가 이미 cancel 상태이므로 일반 launch 는
        // 즉시 취소되어 cleanup 로직이 실행되지 않는다. NonCancellable 로 감싸 정리 보장.
        viewModelScope.launch(NonCancellable) {
            try {
                val currentConnected = _uiState.value.isConnected
                val isPtpipConnected = _uiState.value.isPtpipConnected

                Log.d(TAG, "📸 사진 미리보기 탭 종료 - 연결상태: $currentConnected, PTPIP: $isPtpipConnected")

                resumeEventListenerAfterCardBrowsing(currentConnected, isPtpipConnected)
            } catch (e: CancellationException) {
                // NonCancellable 컨텍스트에서는 도달 가능성이 거의 없지만 안전 차원에서 재던짐.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PhotoPreview 정리 중 예외 발생", e)
            }
        }

        // 매니저들 정리.
        // [PhotoListManager.cleanup] 은 카드 보기가 켜져 있으면 이탈 명령을 앱 scope 로 걸어
        // 카메라를 촬영 모드로 되돌린다 — onCleared 에서 카드 보기가 갇히지 않는 경로가 여기다.
        try {
            photoListManager.cleanup()
            photoImageManager.cleanup()
            photoSelectionManager.clearSelection()
            Log.d(TAG, "매니저들 정리 완료")
        } catch (e: Exception) {
            // onCleared 는 일반 함수이므로 CancellationException 재던지기 불필요.
            Log.w(TAG, "매니저 정리 중 예외", e)
        }
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
     * 선택된 사진들 다운로드(필수1).
     *
     * RAW 게이팅을 통과한 항목만 배치 대상에 포함시키고, 진행 상태(n/m)를 노출한다.
     * 유계 동시성([MAX_CONCURRENT_DOWNLOADS])으로 각 항목을 await 하며 저장까지 수행해,
     * 전건 동시 발사 시 후순위가 JNI 60초 대기(제출 시점 기산)로 구조적 타임아웃하던 문제를 없앤다.
     * 모두 끝나면 요약 토스트 + 선택 모드 자동 종료. 게이팅으로 0개면 즉시 종료.
     * FREE 티어면 축소 사전 고지(세션당 1회). 실패 항목은 개별 식별되어 첫 실패가 재시도 대상으로 보존된다.
     */
    fun downloadSelectedPhotos() {
        val selectedPaths = photoSelectionManager.getSelectedPaths()
        Log.d(TAG, "선택된 사진들 다운로드 시작: ${selectedPaths.size}개")

        // 이미 진행 중인 배치가 있으면 중복 시작 방지.
        if (_multiDownloadProgress.value.inProgress || batchDownloadJob?.isActive == true) {
            Log.d(TAG, "이미 다중 다운로드 진행 중 - 중복 요청 무시")
            return
        }

        maybeNotifyFreeTierResolution()

        // RAW 게이팅 통과 항목만 배치 대상으로 수집(차단 항목은 handleRawFileAccess 가 에러 emit).
        val eligiblePaths = selectedPaths.filter { photoPath ->
            val tempPhoto = CameraPhoto(
                path = photoPath,
                name = photoPath.substringAfterLast("/"),
                size = 0L,
                date = System.currentTimeMillis()
            )
            handleRawFileAccess(tempPhoto)
        }

        if (eligiblePaths.isEmpty()) {
            Log.d(TAG, "다운로드 가능한 선택 항목이 없음 - 선택 모드 종료")
            photoSelectionManager.exitMultiSelectMode()
            return
        }

        _multiDownloadProgress.value = MultiDownloadProgress(
            inProgress = true,
            completed = 0,
            total = eligiblePaths.size,
            failed = 0
        )

        val tier = _uiState.value.currentTier
        // 실패 경로 개별 식별용(스레드 안전 — 배치 코루틴은 여러 자식에서 갱신).
        val failedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())

        batchDownloadJob = viewModelScope.launch {
            val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
            eligiblePaths.map { photoPath ->
                launch {
                    val ok = semaphore.withPermit {
                        photoImageManager.downloadAndPersist(photoPath, tier)
                    }
                    if (!ok) {
                        failedPaths.add(photoPath)
                        Log.w(TAG, "일괄 다운로드 항목 실패: ${LogMask.path(photoPath)}")
                    }
                    // n/m 진행 집계(불변 갱신, CAS 안전).
                    _multiDownloadProgress.update {
                        it.copy(
                            completed = it.completed + 1,
                            failed = it.failed + if (ok) 0 else 1
                        )
                    }
                }
            }.joinAll()

            // 배치 완료 요약.
            val total = eligiblePaths.size
            val failed = failedPaths.size
            if (failed == 0) {
                emitInfo(context.getString(R.string.gallery_v2_download_all_done, total))
            } else {
                emitError(
                    context.getString(R.string.gallery_v2_download_partial, total - failed, total)
                )
                // 실패 항목 개별 식별 — 첫 실패를 단일 재시도 대상으로 보존.
                val firstFailed = failedPaths.first()
                _lastFailedDownload.value = CameraPhoto(
                    path = firstFailed,
                    name = firstFailed.substringAfterLast("/"),
                    size = 0L,
                    date = System.currentTimeMillis()
                )
            }
            _multiDownloadProgress.value = MultiDownloadProgress()
            photoSelectionManager.exitMultiSelectMode()
        }
    }
}