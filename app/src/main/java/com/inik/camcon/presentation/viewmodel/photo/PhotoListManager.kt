package com.inik.camcon.presentation.viewmodel.photo

import android.content.Context
import android.util.Log
import com.inik.camcon.BuildConfig
import com.inik.camcon.R
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.domain.manager.ErrorSeverity
import com.inik.camcon.domain.manager.ErrorType
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraPhotosPagedUseCase
import com.inik.camcon.presentation.viewmodel.state.ErrorHandlingManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사진 목록 관리 및 페이징 전용 매니저
 * 단일책임: 사진 목록 로딩, 필터링, 페이징만 담당
 * Presentation Layer: 뷰모델에서 사용
 */
@Singleton
class PhotoListManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getCameraPhotosPagedUseCase: GetCameraPhotosPagedUseCase,
    private val validateImageFormatUseCase: ValidateImageFormatUseCase,
    private val errorHandlingManager: ErrorHandlingManager,
    @ApplicationScope private val appScope: CoroutineScope
) {

    companion object {
        private const val TAG = "사진목록매니저"
        private const val PREFETCH_PAGE_SIZE = 50
    }

    // 앱 scope의 자식 scope — cancelChildren해도 앱 scope에 영향 없음
    private var managerScope = createManagerScope()

    private fun createManagerScope(): CoroutineScope =
        CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext.job))

    // 전체 사진 목록 (필터링 전)
    private val _allPhotos = MutableStateFlow<List<CameraPhoto>>(emptyList())
    val allPhotos: StateFlow<List<CameraPhoto>> = _allPhotos.asStateFlow()

    // 필터링된 사진 목록
    private val _filteredPhotos = MutableStateFlow<List<CameraPhoto>>(emptyList())
    val filteredPhotos: StateFlow<List<CameraPhoto>> = _filteredPhotos.asStateFlow()

    // 로딩 상태
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 추가 로딩 상태 (페이징)
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    // 페이징 정보
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _hasNextPage = MutableStateFlow(false)
    val hasNextPage: StateFlow<Boolean> = _hasNextPage.asStateFlow()

    // 필터 상태
    private val _currentFilter = MutableStateFlow(FileTypeFilter.JPG)
    val currentFilter: StateFlow<FileTypeFilter> = _currentFilter.asStateFlow()

    // 현재 구독 티어 — 페이징/초기 로드 시 RAW 게이팅에 사용한다.
    // 티어를 명확히 아는 경로(changeFileTypeFilter / load*PhotosTier)에서 갱신하며,
    // 페이징 경로는 이 값을 사용해 FREE 하드코딩으로 인한 PRO/ADMIN RAW 누락을 방지한다.
    @Volatile
    private var currentTier: SubscriptionTier = SubscriptionTier.FREE

    // 프리로딩 상태
    private val _prefetchedPage = MutableStateFlow(0)

    // 작업 중단 플래그 — 해제 스레드와 코루틴 워커 간 가시성 보장
    @Volatile
    private var isManagerActive = true

    /**
     * 초기 사진 목록 로드
     */
    fun loadInitialPhotos(
        isConnected: Boolean,
        tier: SubscriptionTier = currentTier
    ) {
        Log.d(TAG, "loadInitialPhotos 호출 (티어=$tier)")
        currentTier = tier
        managerScope.launch {
            if (!isManagerActive) {
                Log.d(TAG, "loadInitialPhotos 작업 중단됨 (매니저 비활성)")
                return@launch
            }

            _isLoading.value = true
            // loadNextPage와 동일하게 try/finally로 로딩 상태 해제를 보장한다.
            // (과거: onSuccess 내 return@launch / 카메라 미연결 / 코루틴 취소 경로가
            //  하단 _isLoading=false 를 건너뛰어 스피너가 영구 박제되는 결함이 있었다.)
            try {
                _currentPage.value = 0
                _allPhotos.value = emptyList()

                Log.d(TAG, "현재 카메라 연결 상태: $isConnected")

                if (!isConnected) {
                    Log.w(TAG, "카메라가 연결되지 않음")
                    errorHandlingManager.emitError(
                        ErrorType.CONNECTION,
                        "카메라가 연결되지 않았습니다. 카메라를 연결해주세요.",
                        null,
                        ErrorSeverity.MEDIUM
                    )
                    return@launch
                }

                if (!isManagerActive) {
                    Log.d(TAG, "loadInitialPhotos 중단됨 (카메라 확인 후)")
                    return@launch
                }

                getCameraPhotosPagedUseCase(page = 0, pageSize = PREFETCH_PAGE_SIZE).fold(
                    onSuccess = { paginatedPhotos ->
                        if (!isManagerActive) {
                            Log.d(TAG, "loadInitialPhotos 중단됨 (사진 목록 로딩 후)")
                            return@fold
                        }

                        Log.d(TAG, "사진 목록 불러오기 성공: ${paginatedPhotos.photos.size}개")
                        _allPhotos.value = paginatedPhotos.photos
                        updateFilteredPhotos(currentTier)

                        _currentPage.value = paginatedPhotos.currentPage
                        _totalPages.value = paginatedPhotos.totalPages
                        _hasNextPage.value = paginatedPhotos.hasNext
                    },
                    onFailure = { exception ->
                        if (isManagerActive) {
                            Log.e(TAG, "사진 목록 불러오기 실패", exception)
                            val errorMessage =
                                errorHandlingManager.handleFileError(exception, "사진 목록 로딩")
                            errorHandlingManager.emitError(
                                ErrorType.FILE_SYSTEM,
                                errorMessage,
                                exception,
                                ErrorSeverity.MEDIUM
                            )
                        }
                    }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 다음 페이지 로드
     */
    fun loadNextPage(tier: SubscriptionTier = currentTier) {
        currentTier = tier
        if (!_hasNextPage.value) {
            Log.d(TAG, "loadNextPage 건너뛰기: hasNextPage=false")
            return
        }

        if (!isManagerActive) {
            Log.d(TAG, "loadNextPage 작업 중단됨 (매니저 비활성)")
            return
        }

        // 가드+잠금을 원자적으로 수행 — loadNextPage/prefetchNextPage 동시 호출 시 중복 페이지 로딩 방지.
        if (!_isLoadingMore.compareAndSet(expect = false, update = true)) {
            Log.d(TAG, "loadNextPage 건너뛰기: 이미 로딩 중")
            return
        }

        Log.d(TAG, "loadNextPage 시작")
        managerScope.launch {
            try {
                if (!isManagerActive) {
                    Log.d(TAG, "loadNextPage 중단됨 (시작 후)")
                    return@launch
                }

                val nextPage = _currentPage.value + 1
                getCameraPhotosPagedUseCase(page = nextPage, pageSize = PREFETCH_PAGE_SIZE).fold(
                    onSuccess = { paginatedPhotos ->
                        if (!isManagerActive) {
                            Log.d(TAG, "loadNextPage 중단됨 (성공 후)")
                            return@fold
                        }

                        Log.d(TAG, "loadNextPage 성공: ${paginatedPhotos.photos.size}개 추가")
                        val currentPhotos = _allPhotos.value
                        val newPhotos = currentPhotos + paginatedPhotos.photos

                        _allPhotos.value = newPhotos
                        updateFilteredPhotos(currentTier)

                        _currentPage.value = paginatedPhotos.currentPage
                        _totalPages.value = paginatedPhotos.totalPages
                        _hasNextPage.value = paginatedPhotos.hasNext
                    },
                    onFailure = { exception ->
                        if (isManagerActive) {
                            Log.e(TAG, "loadNextPage 실패", exception)
                            val errorMessage =
                                errorHandlingManager.handleFileError(exception, "추가 사진 로딩")
                            errorHandlingManager.emitError(
                                ErrorType.FILE_SYSTEM,
                                errorMessage,
                                exception,
                                ErrorSeverity.MEDIUM
                            )
                        }
                    }
                )
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    /**
     * 파일 타입 필터 변경
     */
    fun changeFileTypeFilter(filter: FileTypeFilter, currentTier: SubscriptionTier) {
        Log.d(TAG, "파일 타입 필터 변경: ${_currentFilter.value} -> $filter")

        this.currentTier = currentTier
        _currentFilter.value = filter
        updateFilteredPhotos(currentTier)

        // 프리로딩 페이지 리셋
        _prefetchedPage.value = _currentPage.value

        Log.d(TAG, "필터링 완료: 전체 ${_allPhotos.value.size}개 -> 필터링된 ${_filteredPhotos.value.size}개")
    }

    /**
     * 필터링된 사진 목록 업데이트
     */
    private fun updateFilteredPhotos(currentTier: SubscriptionTier) {
        val allPhotos = _allPhotos.value
        val filter = _currentFilter.value

        Log.d(
            TAG,
            "updateFilteredPhotos 호출: 필터=$filter, 전체사진=${allPhotos.size}개, 현재티어=$currentTier"
        )

        // 먼저 티어에 따른 접근 가능한 파일만 필터링
        val accessiblePhotos = if (validateImageFormatUseCase.isRawAllowedForTier(currentTier)) {
            allPhotos
        } else {
            allPhotos.filter { photo ->
                !validateImageFormatUseCase.isRawFile(photo.path)
            }
        }

        // 사용자가 선택한 필터 적용
        val filtered = when (filter) {
            FileTypeFilter.ALL -> accessiblePhotos
            FileTypeFilter.JPG -> accessiblePhotos.filter {
                it.path.endsWith(".jpg", true) || it.path.endsWith(".jpeg", true)
            }

            FileTypeFilter.RAW -> {
                if (!validateImageFormatUseCase.isRawAllowedForTier(currentTier)) {
                    // RAW 필터 선택했지만 권한 없는 경우 에러 메시지 표시
                    val message = when (currentTier) {
                        SubscriptionTier.FREE -> context.getString(R.string.raw_filter_restriction_free)
                        SubscriptionTier.BASIC -> context.getString(R.string.raw_filter_restriction_basic)
                        else -> context.getString(R.string.raw_filter_restriction_generic)
                    }
                    errorHandlingManager.emitError(
                        ErrorType.PERMISSION,
                        message,
                        null,
                        ErrorSeverity.MEDIUM
                    )
                    emptyList()
                } else {
                    accessiblePhotos.filter {
                        validateImageFormatUseCase.isRawFile(it.path)
                    }
                }
            }
        }

        _filteredPhotos.value = filtered
        Log.d(TAG, "필터링 결과: 접근가능=${accessiblePhotos.size}개, 최종필터링=${filtered.size}개")
    }

    /**
     * 프리로딩 체크 (사용자가 특정 인덱스에 도달했을 때)
     */
    fun onPhotoIndexReached(
        currentIndex: Int,
        tier: SubscriptionTier = currentTier
    ) {
        currentTier = tier
        val filteredPhotos = _filteredPhotos.value
        val totalFilteredPhotos = filteredPhotos.size
        val currentPage = _currentPage.value

        // 엄격한 동적 임계값 계산
        val dynamicThreshold = when {
            totalFilteredPhotos <= 20 -> totalFilteredPhotos - 3
            totalFilteredPhotos <= 50 -> (totalFilteredPhotos * 0.8).toInt()
            else -> (totalFilteredPhotos * 0.85).toInt().coerceAtLeast(40)
        }

        val shouldPrefetch = currentIndex >= dynamicThreshold &&
                !_isLoadingMore.value &&
                _hasNextPage.value &&
                _prefetchedPage.value <= currentPage &&
                currentIndex >= totalFilteredPhotos - 5 &&
                totalFilteredPhotos >= 20

        Log.d(
            TAG,
            "프리로딩 체크: 인덱스=$currentIndex, 사진수=$totalFilteredPhotos, 임계값=$dynamicThreshold, 조건만족=$shouldPrefetch"
        )

        if (shouldPrefetch) {
            Log.d(TAG, "프리로드 트리거: 현재 인덱스 $currentIndex")
            // prefetch가 실제로 잠금에 성공해 시작된 경우에만 prefetchedPage를 전진시킨다.
            if (prefetchNextPage()) {
                _prefetchedPage.value = currentPage + 1
            }
        }
    }

    /**
     * 백그라운드에서 다음 페이지를 미리 로드
     */
    private fun prefetchNextPage(): Boolean {
        if (!_hasNextPage.value) {
            Log.d(TAG, "프리로드 건너뛰기: hasNextPage=false")
            return false
        }

        // 가드+잠금을 원자적으로 수행 — loadNextPage/prefetchNextPage 동시 호출 시 중복 페이지 로딩 방지.
        if (!_isLoadingMore.compareAndSet(expect = false, update = true)) {
            Log.d(TAG, "프리로드 건너뛰기: 이미 로딩 중")
            return false
        }

        Log.d(TAG, "prefetchNextPage 시작")
        managerScope.launch {
            try {
                val nextPage = _currentPage.value + 1
                getCameraPhotosPagedUseCase(page = nextPage, pageSize = PREFETCH_PAGE_SIZE).fold(
                    onSuccess = { paginatedPhotos ->
                        val currentPhotos = _allPhotos.value
                        val newPhotos = currentPhotos + paginatedPhotos.photos

                        _allPhotos.value = newPhotos
                        updateFilteredPhotos(currentTier)

                        _currentPage.value = paginatedPhotos.currentPage
                        _totalPages.value = paginatedPhotos.totalPages
                        _hasNextPage.value = paginatedPhotos.hasNext

                        Log.d(TAG, "백그라운드 프리로드 완료: 추가된 사진 ${paginatedPhotos.photos.size}개")
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "백그라운드 프리로드 실패", exception)
                        val errorMessage = errorHandlingManager.handleFileError(exception, "백그라운드 로딩")
                        errorHandlingManager.emitError(
                            ErrorType.FILE_SYSTEM,
                            errorMessage,
                            exception,
                            ErrorSeverity.LOW
                        )
                    }
                )
            } finally {
                _isLoadingMore.value = false
            }
        }
        return true
    }

    /**
     * 사진 목록 새로고침
     */
    fun refreshPhotos(
        isConnected: Boolean,
        tier: SubscriptionTier = currentTier
    ) {
        Log.d(TAG, "사진 목록 새로고침")
        _prefetchedPage.value = 0
        loadInitialPhotos(isConnected, tier)
    }

    /**
     * 현재 상태 정보 로깅 (디버깅용)
     */
    fun logCurrentState() {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG, """
                현재 사진 목록 상태:
                - 전체 사진: ${_allPhotos.value.size}개
                - 필터링된 사진: ${_filteredPhotos.value.size}개
                - 현재 페이지: ${_currentPage.value}
                - 전체 페이지: ${_totalPages.value}
                - 다음 페이지 있음: ${_hasNextPage.value}
                - 현재 필터: ${_currentFilter.value}
                - 로딩 중: ${_isLoading.value}
                - 추가 로딩 중: ${_isLoadingMore.value}
            """.trimIndent()
            )
        }
    }

    /**
     * 매니저 정리
     */
    fun cleanup() {
        // 진행 중 작업 취소 → scope 재생성 후 즉시 재활성화하여
        // @Singleton 재진입(미리보기 재진입) 시 목록 로딩이 영구 차단되지 않도록 한다.(F20)
        managerScope.coroutineContext.job.cancel()
        managerScope = createManagerScope()
        isManagerActive = true
        _allPhotos.value = emptyList()
        _filteredPhotos.value = emptyList()
        _isLoading.value = false
        _isLoadingMore.value = false
        _currentPage.value = 0
        _totalPages.value = 0
        _hasNextPage.value = false
        _prefetchedPage.value = 0
        Log.d(TAG, "사진 목록 매니저 정리 완료")
    }
}

/**
 * 파일 타입 필터 열거형
 */
enum class FileTypeFilter {
    ALL,
    JPG,
    RAW
}