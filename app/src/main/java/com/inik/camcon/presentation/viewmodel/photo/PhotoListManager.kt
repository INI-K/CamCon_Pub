package com.inik.camcon.presentation.viewmodel.photo

import android.util.Log
import com.inik.camcon.domain.manager.ErrorSeverity
import com.inik.camcon.domain.manager.ErrorType
import com.inik.camcon.presentation.viewmodel.state.ErrorHandlingManager
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.usecase.camera.GetCameraPhotosPagedUseCase
import com.inik.camcon.utils.SubscriptionUtils
import com.inik.camcon.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val getCameraPhotosPagedUseCase: GetCameraPhotosPagedUseCase,
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

    // 프리로딩 상태
    private val _prefetchedPage = MutableStateFlow(0)

    // 작업 중단 플래그
    private var isManagerActive = true

    /**
     * 초기 사진 목록 로드
     */
    fun loadInitialPhotos(isConnected: Boolean, isPtpipConnected: Boolean = false) {
        Log.d(TAG, "=== loadInitialPhotos 호출 ===")
        managerScope.launch {
            Log.d(TAG, "loadInitialPhotos 코루틴 시작")

            if (!isManagerActive) {
                Log.d(TAG, "⛔ loadInitialPhotos 작업 중단됨 (매니저 비활성)")
                return@launch
            }

            _isLoading.value = true
            _currentPage.value = 0
            _allPhotos.value = emptyList()

            Log.d(TAG, "현재 카메라 연결 상태: $isConnected")

            if (!isConnected) {
                Log.w(TAG, "카메라가 연결되지 않음")
                _isLoading.value = false
                errorHandlingManager.emitError(
                    ErrorType.CONNECTION,
                    "카메라가 연결되지 않았습니다. 카메라를 연결해주세요.",
                    null,
                    ErrorSeverity.MEDIUM
                )
                return@launch
            }

            // PTPIP 연결 상태 확인
            if (isPtpipConnected) {
                Log.w(TAG, "PTPIP 연결 상태: 파일 목록 로딩 차단")
                _isLoading.value = false
                errorHandlingManager.emitError(
                    ErrorType.OPERATION,
                    "PTPIP 연결 시 사진 미리보기는 지원되지 않습니다.\nUSB 케이블 연결을 사용해주세요.",
                    null,
                    ErrorSeverity.MEDIUM
                )
                return@launch
            }

            if (!isManagerActive) {
                Log.d(TAG, "⛔ loadInitialPhotos 중단됨 (카메라 확인 후)")
                return@launch
            }

            Log.d(TAG, "getCameraPhotosPagedUseCase 호출 시작")
            getCameraPhotosPagedUseCase(page = 0, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    if (!isManagerActive) {
                        Log.d(TAG, "⛔ loadInitialPhotos 중단됨 (사진 목록 로딩 후)")
                        return@launch
                    }

                    Log.d(TAG, "사진 목록 불러오기 성공: ${paginatedPhotos.photos.size}개")
                    _allPhotos.value = paginatedPhotos.photos
                    updateFilteredPhotos(SubscriptionTier.FREE) // 기본값으로 FREE 티어 사용

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

            _isLoading.value = false
            Log.d(TAG, "loadInitialPhotos 코루틴 완료")
        }
    }

    /**
     * 다음 페이지 로드
     */
    fun loadNextPage(isPtpipConnected: Boolean = false) {
        if (_isLoadingMore.value || !_hasNextPage.value) {
            Log.d(
                TAG,
                "loadNextPage 건너뛰기: isLoadingMore=${_isLoadingMore.value}, hasNextPage=${_hasNextPage.value}"
            )
            return
        }

        if (!isManagerActive) {
            Log.d(TAG, "⛔ loadNextPage 작업 중단됨 (매니저 비활성)")
            return
        }

        // PTPIP 연결 상태 체크 (페이징도 차단)
        if (isPtpipConnected) {
            Log.w(TAG, "PTPIP 연결 상태: 파일 목록 로딩 차단")
            errorHandlingManager.emitError(
                ErrorType.OPERATION,
                "PTPIP 연결 시 사진 미리보기는 지원되지 않습니다.\nUSB 케이블 연결을 사용해주세요.",
                null,
                ErrorSeverity.MEDIUM
            )
            return
        }

        Log.d(TAG, "=== loadNextPage 시작 ===")
        managerScope.launch {
            _isLoadingMore.value = true
            Log.d(TAG, "isLoadingMore = true 설정됨")

            if (!isManagerActive) {
                Log.d(TAG, "⛔ loadNextPage 중단됨 (시작 후)")
                return@launch
            }

            val nextPage = _currentPage.value + 1
            getCameraPhotosPagedUseCase(page = nextPage, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    if (!isManagerActive) {
                        Log.d(TAG, "⛔ loadNextPage 중단됨 (성공 후)")
                        return@launch
                    }

                    Log.d(TAG, "loadNextPage 성공: ${paginatedPhotos.photos.size}개 추가")
                    val currentPhotos = _allPhotos.value
                    val newPhotos = currentPhotos + paginatedPhotos.photos

                    _allPhotos.value = newPhotos
                    updateFilteredPhotos(SubscriptionTier.FREE) // 기본값으로 FREE 티어 사용

                    _currentPage.value = paginatedPhotos.currentPage
                    _totalPages.value = paginatedPhotos.totalPages
                    _hasNextPage.value = paginatedPhotos.hasNext

                    Log.d(TAG, "isLoadingMore = false 설정됨")
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

            _isLoadingMore.value = false
        }
    }

    /**
     * 파일 타입 필터 변경
     */
    fun changeFileTypeFilter(filter: FileTypeFilter, currentTier: SubscriptionTier) {
        Log.d(TAG, "파일 타입 필터 변경: ${_currentFilter.value} -> $filter")

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
        val accessiblePhotos = if (canAccessRawFiles(currentTier)) {
            allPhotos
        } else {
            allPhotos.filter { photo ->
                val isRaw = SubscriptionUtils.isRawFile(photo.path)
                if (isRaw) {
                    Log.v(TAG, "RAW 파일 숨김 (권한없음): ${photo.path}")
                }
                !isRaw
            }
        }

        // 사용자가 선택한 필터 적용
        val filtered = when (filter) {
            FileTypeFilter.ALL -> accessiblePhotos
            FileTypeFilter.JPG -> accessiblePhotos.filter {
                val isJpg = it.path.endsWith(".jpg", true) || it.path.endsWith(".jpeg", true)
                Log.v(TAG, "JPG 필터 확인: ${it.path} -> $isJpg")
                isJpg
            }

            FileTypeFilter.RAW -> {
                if (!canAccessRawFiles(currentTier)) {
                    // RAW 필터 선택했지만 권한 없는 경우 에러 메시지 표시
                    val message = when (currentTier) {
                        SubscriptionTier.FREE -> "RAW 파일 보기는 준비중입니다.\nJPG 파일만 확인하실 수 있습니다."
                        SubscriptionTier.BASIC -> "RAW 파일은 PRO 구독에서만 볼 수 있습니다.\nPRO로 업그레이드해주세요!"
                        else -> "RAW 파일에 접근할 수 없습니다."
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
                        val isRaw = SubscriptionUtils.isRawFile(it.path)
                        Log.v(TAG, "RAW 필터 확인: ${it.path} -> $isRaw")
                        isRaw
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
    fun onPhotoIndexReached(currentIndex: Int, isPtpipConnected: Boolean = false) {
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
            TAG, """
            프리로딩 체크:
            - 현재 인덱스: $currentIndex
            - 필터링된 사진 수: $totalFilteredPhotos
            - 동적 임계값: $dynamicThreshold
            - 프리로드 조건 만족: $shouldPrefetch
        """.trimIndent()
        )

        if (shouldPrefetch) {
            Log.d(TAG, "🚀 프리로드 트리거: 현재 인덱스 $currentIndex")
            prefetchNextPage(isPtpipConnected)
            _prefetchedPage.value = currentPage + 1
        }
    }

    /**
     * 백그라운드에서 다음 페이지를 미리 로드
     */
    private fun prefetchNextPage(isPtpipConnected: Boolean = false) {
        if (_isLoadingMore.value || !_hasNextPage.value) {
            Log.d(TAG, "프리로드 건너뛰기")
            return
        }

        // PTPIP 연결 상태 체크 (프리로딩도 차단)
        if (isPtpipConnected) {
            Log.w(TAG, "PTPIP 연결 상태: 파일 목록 프리로드 차단")
            errorHandlingManager.emitError(
                ErrorType.OPERATION,
                "PTPIP 연결 시 사진 미리보기는 지원되지 않습니다.\nUSB 케이블 연결을 사용해주세요.",
                null,
                ErrorSeverity.LOW
            )
            return
        }

        Log.d(TAG, "=== prefetchNextPage 시작 ===")
        managerScope.launch {
            _isLoadingMore.value = true

            val nextPage = _currentPage.value + 1
            getCameraPhotosPagedUseCase(page = nextPage, pageSize = PREFETCH_PAGE_SIZE).fold(
                onSuccess = { paginatedPhotos ->
                    val currentPhotos = _allPhotos.value
                    val newPhotos = currentPhotos + paginatedPhotos.photos

                    _allPhotos.value = newPhotos
                    updateFilteredPhotos(SubscriptionTier.FREE) // 기본값 사용

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

            _isLoadingMore.value = false
        }
    }

    /**
     * RAW 파일 접근 권한 확인
     */
    private fun canAccessRawFiles(currentTier: SubscriptionTier): Boolean {
        return currentTier == SubscriptionTier.PRO ||
                currentTier == SubscriptionTier.REFERRER ||
                currentTier == SubscriptionTier.ADMIN
    }

    /**
     * 사진 목록 새로고침
     */
    fun refreshPhotos(isConnected: Boolean, isPtpipConnected: Boolean = false) {
        Log.d(TAG, "사진 목록 새로고침")
        _prefetchedPage.value = 0
        loadInitialPhotos(isConnected, isPtpipConnected)
    }

    /**
     * 현재 상태 정보 로깅 (디버깅용)
     */
    fun logCurrentState() {
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

    /**
     * 매니저 정리
     */
    fun cleanup() {
        managerScope.coroutineContext.job.cancel()
        managerScope = createManagerScope()
        isManagerActive = false
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