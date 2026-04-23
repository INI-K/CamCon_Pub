# CamCon 프로젝트 개선 액션 플랜

**작성일**: 2026-02-05  
**담당자**: 개발팀  
**목표 완료일**: 2026-03-05 (4주)

---

## 🔴 P1: 즉시 개선 (1-2주) - 필수 작업

### 1. SharedFlow 이벤트 분리

#### 📋 작업 항목

##### 1-1. AuthViewModel 개선
**파일**: `AuthViewModel.kt`  
**작업**: SharedFlow 기반 이벤트 추가

```kotlin
// 추가할 코드
sealed class AuthEvent {
    data class ShowLogoutSuccess(val message: String) : AuthEvent()
    data class ShowError(val message: String) : AuthEvent()
    object NavigateAfterLogout : AuthEvent()
}

// ViewModel 내부에 추가
private val _authEvent = MutableSharedFlow<AuthEvent>(replay = 0)
val authEvent: SharedFlow<AuthEvent> = _authEvent.asSharedFlow()

// signOut() 메서드 수정
fun signOut() {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            signOutUseCase().fold(
                onSuccess = {
                    signOutFromGoogle()
                    _authEvent.emit(AuthEvent.ShowLogoutSuccess("로그아웃되었습니다"))
                    _authEvent.emit(AuthEvent.NavigateAfterLogout)
                    _uiState.update { it.copy(isLoading = false, isSignOutSuccess = true) }
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "로그아웃 실패"
                    _authEvent.emit(AuthEvent.ShowError(errorMsg))
                    _uiState.update { it.copy(isLoading = false, error = errorMsg) }
                }
            )
        } catch (e: Exception) {
            val errorMsg = e.message ?: "로그아웃 실패"
            _authEvent.emit(AuthEvent.ShowError(errorMsg))
            _uiState.update { it.copy(isLoading = false, error = errorMsg) }
        }
    }
}
```

**예상 소요 시간**: 30분  
**영향 범위**: AuthViewModel 관련 UI 화면  
**테스트 필요**: AuthScreen 통합 테스트

---

##### 1-2. LoginViewModel 유지
**파일**: `LoginViewModel.kt`  
**작업**: 현재 구현 유지 (이미 완벽함)

✅ **완료됨** - 추가 작업 없음

---

##### 1-3. CameraViewModel 개선
**파일**: `CameraViewModel.kt`  
**작업**: 이벤트 기반 알림 추가

```kotlin
// CameraEvent 선언
sealed class CameraEvent {
    data class ShowError(val message: String) : CameraEvent()
    data class ShowMessage(val message: String) : CameraEvent()
    object CameraDisconnected : CameraEvent()
    object CameraConnected : CameraEvent()
}

// ViewModel 내부에 추가
private val _cameraEvent = MutableSharedFlow<CameraEvent>(replay = 0)
val cameraEvent: SharedFlow<CameraEvent> = _cameraEvent.asSharedFlow()

// 에러 핸들링 메서드 수정
private fun observeErrorEvents() {
    errorHandlingManager.errorEvent
        .onEach { errorEvent ->
            _cameraEvent.emit(CameraEvent.ShowError(errorEvent.message))
            uiStateManager.setError(errorEvent.message)
        }
        .launchIn(viewModelScope)
}
```

**예상 소요 시간**: 45분  
**영향 범위**: 카메라 컨트롤 스크린  
**테스트 필요**: 에러 표시 로직 검증

---

#### ✅ 체크리스트

- [ ] AuthViewModel.kt 수정
- [ ] AuthScreen 테스트
- [ ] CameraViewModel.kt 수정
- [ ] CameraControlScreen 테스트
- [ ] 코드 리뷰

---

### 2. 테스트 커버리지 확대

#### 📋 작업 항목

##### 2-1. CameraViewModelTest 작성
**파일**: `app/src/test/java/com/inik/camcon/presentation/viewmodel/CameraViewModelTest.kt`

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {
    
    private lateinit var viewModel: CameraViewModel
    private lateinit var cameraRepository: CameraRepository
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var uiStateManager: CameraUiStateManager
    private lateinit var connectionManager: CameraConnectionManager
    private lateinit var operationsManager: CameraOperationsManager
    private lateinit var settingsManager: CameraSettingsManager
    private lateinit var errorHandlingManager: ErrorHandlingManager
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cameraRepository = mockk()
        getSubscriptionUseCase = mockk()
        uiStateManager = mockk(relaxed = true)
        connectionManager = mockk(relaxed = true)
        operationsManager = mockk(relaxed = true)
        settingsManager = mockk(relaxed = true)
        errorHandlingManager = mockk(relaxed = true)
        
        // 기본 Mock 설정
        every { cameraRepository.getCameraFeed() } returns 
            flowOf(emptyList()).stateIn(
                scope = CoroutineScope(testDispatcher),
                started = SharingStarted.Lazily,
                initialValue = emptyList()
            )
        every { cameraRepository.isPtpipConnected() } returns 
            flowOf(false).stateIn(...)
        every { errorHandlingManager.errorEvent } returns 
            MutableSharedFlow()
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    private fun createViewModel(): CameraViewModel {
        return CameraViewModel(
            context = mockk(relaxed = true),
            cameraRepository = cameraRepository,
            getSubscriptionUseCase = getSubscriptionUseCase,
            uiStateManager = uiStateManager,
            connectionManager = connectionManager,
            operationsManager = operationsManager,
            settingsManager = settingsManager,
            errorHandlingManager = errorHandlingManager,
            appPreferencesDataSource = mockk(relaxed = true)
        )
    }
    
    @Test
    fun `카메라 연결 상태 변경시 uiState 업데이트`() = runTest {
        // Given
        val connectedState = true
        every { cameraRepository.isCameraConnected() } returns 
            flowOf(connectedState)
        
        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        verify { uiStateManager.updateConnectionState(connectedState) }
    }
    
    @Test
    fun `PTPIP 연결시 미리보기 탭 차단`() = runTest {
        // Given
        val ptpipConnected = true
        every { cameraRepository.isPtpipConnected() } returns 
            flowOf(ptpipConnected)
        
        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        verify { uiStateManager.blockPreviewTab(true) }
    }
}
```

**예상 소요 시간**: 1시간  
**테스트 케이스**: 5개  
**필수 Mock**: CameraRepository, GetSubscriptionUseCase, Managers

---

##### 2-2. PhotoPreviewViewModelTest 작성
**파일**: `app/src/test/java/com/inik/camcon/presentation/viewmodel/PhotoPreviewViewModelTest.kt`

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoPreviewViewModelTest {
    
    private lateinit var viewModel: PhotoPreviewViewModel
    private lateinit var cameraRepository: CameraRepository
    private lateinit var getSubscriptionUseCase: GetSubscriptionUseCase
    private lateinit var photoListManager: PhotoListManager
    private lateinit var photoImageManager: PhotoImageManager
    private lateinit var photoSelectionManager: PhotoSelectionManager
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cameraRepository = mockk()
        getSubscriptionUseCase = mockk()
        photoListManager = mockk(relaxed = true)
        photoImageManager = mockk(relaxed = true)
        photoSelectionManager = mockk(relaxed = true)
        
        // 기본 Mock 설정
        every { cameraRepository.isPtpipConnected() } returns 
            flowOf(false)
        every { getSubscriptionUseCase.getSubscriptionTier() } returns 
            flowOf(SubscriptionTier.FREE)
        every { photoListManager.filteredPhotos } returns 
            MutableStateFlow(emptyList())
    }
    
    @Test
    fun `초기 상태는 로딩 아님`() = runTest {
        // When
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        assertFalse(viewModel.uiState.value.isLoading)
    }
    
    @Test
    fun `PTPIP 연결시 파일 목록 로딩 차단`() = runTest {
        // Given
        every { cameraRepository.isPtpipConnected() } returns 
            flowOf(true)
        
        // When
        viewModel = createViewModel()
        viewModel.loadInitialPhotos()
        
        // Then
        verify(exactly = 0) { photoListManager.loadInitialPhotos(any(), any()) }
    }
}
```

**예상 소요 시간**: 1시간  
**테스트 케이스**: 4개

---

##### 2-3. ServerPhotosViewModelTest 작성
**파일**: `app/src/test/java/com/inik/camcon/presentation/viewmodel/ServerPhotosViewModelTest.kt`

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class ServerPhotosViewModelTest {
    
    private lateinit var viewModel: ServerPhotosViewModel
    private lateinit var context: Context
    private lateinit var cameraRepository: CameraRepository
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        cameraRepository = mockk()
    }
    
    @Test
    fun `초기 상태는 빈 사진 목록`() = runTest {
        // When
        viewModel = ServerPhotosViewModel(context, cameraRepository)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then
        assertTrue(viewModel.uiState.value.photos.isEmpty())
    }
    
    @Test
    fun `멀티 선택 모드 시작`() = runTest {
        // Given
        val photoId = "test-photo-1"
        
        // When
        viewModel = ServerPhotosViewModel(context, cameraRepository)
        viewModel.startMultiSelectMode(photoId)
        
        // Then
        assertTrue(viewModel.uiState.value.isMultiSelectMode)
        assertEquals(setOf(photoId), viewModel.uiState.value.selectedPhotos)
    }
}
```

**예상 소요 시간**: 45분  
**테스트 케이스**: 4개

---

#### ✅ 체크리스트

- [ ] CameraViewModelTest.kt 작성
- [ ] PhotoPreviewViewModelTest.kt 작성
- [ ] ServerPhotosViewModelTest.kt 작성
- [ ] 각 테스트 실행 및 검증
- [ ] 테스트 커버리지 리포트 생성

**목표**: 테스트 커버리지 12-15%로 확대

---

## 🟡 P2: 단기 개선 (2-4주)

### 3. Dispatcher 명시화

#### 📋 작업 항목

**파일**: `CameraViewModel.kt`, `PhotoPreviewViewModel.kt`

**변경 사항**:

```kotlin
// AS-IS (개선 전)
private fun loadSubscriptionTierAtStartup() {
    viewModelScope.launch {
        try {
            val tier = getSubscriptionUseCase.getSubscriptionTier().first()
            // ...
        } catch (e: Exception) {
            // ...
        }
    }
}

// TO-BE (개선 후)
private fun loadSubscriptionTierAtStartup() {
    viewModelScope.launch {
        try {
            val tier = withContext(Dispatchers.IO) {
                getSubscriptionUseCase.getSubscriptionTier().first()
            }
            // Main 컨텍스트로 자동 복귀
            val tierInt = when (tier) {
                SubscriptionTier.FREE -> 0
                SubscriptionTier.BASIC -> 1
                SubscriptionTier.PRO -> 2
                SubscriptionTier.REFERRER -> 2
                SubscriptionTier.ADMIN -> 2
            }
            CameraNative.setSubscriptionTier(tierInt)
            Log.d(TAG, "✅ 구독 티어 설정 완료: $tier")
        } catch (e: CancellationException) {
            throw e  // CancellationException은 재발생
        } catch (e: Exception) {
            Log.e(TAG, "❌ 구독 티어 로드 실패", e)
            CameraNative.setSubscriptionTier(0)
            CameraNative.setRawFileDownloadEnabled(true)
        }
    }
}
```

**예상 소요 시간**: 1시간  
**영향 파일**: 3-4개  
**테스트**: 스레드 안전성 검증

---

### 4. Compose 성능 최적화

#### 📋 작업 항목

##### 4-1. OpenSourceLicensesActivity.kt key 추가

**파일**: `OpenSourceLicensesActivity.kt`

```kotlin
// AS-IS
LazyColumn {
    items(licenses) { license ->
        LicenseItem(license)
    }
}

// TO-BE
LazyColumn {
    items(licenses, key = { license -> license.name }) { license ->
        LicenseItem(license)
    }
}
```

**예상 소요 시간**: 10분

---

##### 4-2. derivedStateOf 도입 검토

**파일들**: `PhotoPreviewScreen.kt`, `ServerPhotosScreen.kt`

```kotlin
// PhotoPreviewScreen.kt에 추가
val selectedPhotosCount = derivedStateOf {
    selectedPhotos.size
}

// UI에서 사용
Text("선택됨: ${selectedPhotosCount.value}")
```

**예상 소요 시간**: 20분  
**성능 개선**: 불필요한 리컴포지션 감소

---

#### ✅ 체크리스트

- [ ] OpenSourceLicensesActivity.kt 수정
- [ ] derivedStateOf 도입 검토
- [ ] Compose 성능 프로파일링
- [ ] 성능 개선 검증

---

### 5. 예외 처리 강화

#### 📋 작업 항목

**파일들**: `CameraViewModel.kt`, `PhotoPreviewViewModel.kt`

```kotlin
// AS-IS
viewModelScope.launch {
    errorHandlingManager.errorEvent
        .onEach { errorEvent ->
            uiStateManager.setError(errorEvent.message)
        }
        .launchIn(viewModelScope)
}

// TO-BE
viewModelScope.launch {
    try {
        errorHandlingManager.errorEvent.collect { errorEvent ->
            uiStateManager.setError(errorEvent.message)
        }
    } catch (e: CancellationException) {
        Log.d(TAG, "에러 이벤트 구독 취소됨")
        throw e  // CancellationException은 재발생
    } catch (e: Exception) {
        Log.e(TAG, "예상치 못한 예외 발생", e)
    }
}
```

**예상 소요 시간**: 45분

---

## 🟢 P3: 중기 개선 (1개월)

### 6. 추가 테스트 작성

#### 📋 작업 항목

##### 6-1. LoginViewModelTest
**파일**: `app/src/test/java/com/inik/camcon/presentation/viewmodel/LoginViewModelTest.kt`  
**테스트 케이스**: 5개  
**예상 소요 시간**: 1시간

---

##### 6-2. AppSettingsViewModelTest
**파일**: `app/src/test/java/com/inik/camcon/presentation/viewmodel/AppSettingsViewModelTest.kt`  
**테스트 케이스**: 4개  
**예상 소요 시간**: 45분

---

##### 6-3. Repository 테스트
**파일**: `app/src/test/java/com/inik/camcon/data/repository/`  
**범위**: 5개 Repository 기본 테스트  
**예상 소요 시간**: 3시간

---

### 7. 문서화

#### 📋 작업 항목

##### 7-1. ViewModel 개발 가이드 문서 작성
```markdown
# CamCon ViewModel 개발 가이드

## StateFlow 캡슐화
- Private MutableStateFlow 선언
- Public StateFlow asStateFlow() 제공

## SharedFlow 이벤트
- Sealed class로 이벤트 정의
- replay = 0으로 설정
- 일회성 이벤트 분리

## 코루틴 패턴
- viewModelScope 사용
- IO 작업에 Dispatchers.IO 명시
- CancellationException 재발생
```

**예상 소요 시간**: 1시간

---

##### 7-2. 테스트 작성 가이드
```markdown
# CamCon 테스트 작성 가이드

## 테스트 구조
- @Before: Mock 설정
- @Test: 테스트 케이스
- @After: 정리

## Mock 도구
- MockK: 객체 모킹
- Turbine: Flow 테스트
- runTest: 코루틴 테스트
```

**예상 소요 시간**: 1시간

---

## 📊 진행 상황 추적

### 주간 목표

#### 1주차 (2월 5-12일)
- [ ] SharedFlow 이벤트 분리 (AuthViewModel, CameraViewModel)
- [ ] CameraViewModelTest 작성
- [ ] 코드 리뷰 및 테스트

**예상 완료도**: 40%

#### 2주차 (2월 12-19일)
- [ ] PhotoPreviewViewModelTest, ServerPhotosViewModelTest 작성
- [ ] Dispatcher 명시화
- [ ] Compose 성능 최적화

**예상 완료도**: 70%

#### 3주차 (2월 19-26일)
- [ ] 예외 처리 강화
- [ ] LoginViewModelTest 작성
- [ ] AppSettingsViewModelTest 작성

**예상 완료도**: 85%

#### 4주차 (2월 26-3월 5일)
- [ ] Repository 테스트 시작
- [ ] 문서화 작업
- [ ] 최종 검토 및 통합

**예상 완료도**: 100%

---

## 📈 성공 기준

| 지표 | 현재 | 목표 | 달성 기준 |
|------|------|------|----------|
| 테스트 커버리지 | 3-5% | 15% | 8개 이상 테스트 파일 |
| SharedFlow 이벤트 분리 | 1/13 | 13/13 | 모든 ViewModel |
| Dispatcher 명시화 | 60% | 100% | 모든 코루틴 |
| Compose key 사용 | 80% | 100% | 모든 LazyColumn/Row |

---

## 🔄 정기 리뷰

**주간 리뷰**: 매주 목요일 4:00 PM  
**항목**: 진행 상황, 블로킹 이슈, 우선순위 조정

---

## 참고 자료

- [Kotlin 코루틴 공식 문서](https://kotlinlang.org/docs/coroutines-overview.html)
- [Android ViewModel 가이드](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Jetpack Compose 성능 가이드](https://developer.android.com/jetpack/compose/performance)
- CamCon 프로젝트 상태 리포트 (PROJECT_STATUS_REVIEW.md)

---

**최종 목표**: ⭐⭐⭐⭐⭐ (5/5) 달성  
**완료 예정일**: 2026-03-05

