package com.inik.camcon.presentation.viewmodel.photo

import com.inik.camcon.domain.model.PaginatedCameraPhotos
import com.inik.camcon.domain.model.PtpTimeoutException
import com.inik.camcon.domain.usecase.camera.GetCameraPhotosPagedUseCase
import com.inik.camcon.domain.usecase.file.InvalidateFileCacheUseCase
import com.inik.camcon.domain.usecase.file.SetSonyContentsTransferModeUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 카드 보기(소니 콘텐츠 전송 모드) 전환 테스트.
 *
 * 검증하는 계약은 세 가지다.
 *
 * 1. 상태 전이가 설계대로 흐르는가. 특히 이탈에 실패하면 [CardBrowseState.STUCK] 으로 남아야
 *    한다. 조용히 IDLE 로 돌아가면 카메라는 갇혀 있는데 앱은 촬영이 되는 줄 알게 된다.
 * 2. 진입에 성공한 뒤 파일 캐시를 반드시 무효화하는가. 네이티브가 "저장소 미지원" 판정을
 *    세션 캐시에 박아 두기 때문에, 이걸 빠뜨리면 카드가 실제로 드러나도 앱이 계속 미지원을
 *    반환해 패치 전체가 무력화된다.
 * 3. 전환에 실패했을 때 원래 상태로 돌아오는가.
 */
class PhotoListManagerCardBrowseTest {

    private lateinit var setMode: SetSonyContentsTransferModeUseCase
    private lateinit var invalidateCache: InvalidateFileCacheUseCase
    private lateinit var getPhotos: GetCameraPhotosPagedUseCase

    private val emptyPage = PaginatedCameraPhotos(
        photos = emptyList(),
        currentPage = 0,
        pageSize = 50,
        totalItems = 0,
        totalPages = 0,
        hasNext = false
    )

    @Before
    fun setUp() {
        setMode = mockk()
        invalidateCache = mockk()
        getPhotos = mockk()
        coEvery { invalidateCache() } returns Result.success(true)
        coEvery { getPhotos(any(), any()) } returns Result.success(emptyPage)
    }

    // Unconfined 로 두어 전환 코루틴이 호출 즉시 끝나게 한다. 상태 전이를 동기적으로 볼 수 있다.
    private fun manager() = PhotoListManager(
        context = mockk(relaxed = true),
        getCameraPhotosPagedUseCase = getPhotos,
        validateImageFormatUseCase = mockk(relaxed = true),
        setSonyContentsTransferModeUseCase = setMode,
        invalidateFileCacheUseCase = invalidateCache,
        errorHandlingManager = mockk(relaxed = true),
        appScope = CoroutineScope(Dispatchers.Unconfined)
    )

    // MARK: - 진입

    @Test
    fun `진입에 성공하면 ACTIVE 가 되고 미지원 표시가 풀린다`() {
        coEvery { setMode(true) } returns Result.success(true)
        val m = manager()

        m.enterCardBrowse()

        assertEquals(CardBrowseState.ACTIVE, m.cardBrowseState.value)
        assertEquals(false, m.isStorageUnsupported.value)
        assertNull(m.cardBrowseError.value)
    }

    @Test
    fun `진입에 성공하면 파일 캐시를 무효화한다`() {
        coEvery { setMode(true) } returns Result.success(true)

        manager().enterCardBrowse()

        // 이 호출이 빠지면 네이티브의 "미지원" 세션 캐시가 남아 카드가 끝내 보이지 않는다.
        coVerify(exactly = 1) { invalidateCache() }
    }

    @Test
    fun `진입에 성공하면 목록을 다시 조회한다`() {
        coEvery { setMode(true) } returns Result.success(true)

        manager().enterCardBrowse()

        coVerify { getPhotos(any(), any()) }
    }

    @Test
    fun `진입에 실패하면 IDLE 로 돌아가고 실패 사유를 남긴다`() {
        coEvery { setMode(true) } returns Result.failure(IllegalStateException("지원하지 않음"))
        val m = manager()

        m.enterCardBrowse()

        assertEquals(CardBrowseState.IDLE, m.cardBrowseState.value)
        assertEquals(CardBrowseError.ENTER_FAILED, m.cardBrowseError.value)
    }

    @Test
    fun `진입에 실패하면 캐시를 건드리지 않는다`() {
        coEvery { setMode(true) } returns Result.failure(IllegalStateException("지원하지 않음"))

        manager().enterCardBrowse()

        coVerify(exactly = 0) { invalidateCache() }
    }

    @Test
    fun `이미 ACTIVE 면 다시 진입하지 않는다`() {
        coEvery { setMode(true) } returns Result.success(true)
        val m = manager()
        m.enterCardBrowse()

        m.enterCardBrowse()

        // 두 번째 호출은 상태 가드에 걸려 네이티브까지 가지 않는다.
        coVerify(exactly = 1) { setMode(true) }
    }

    // MARK: - 이탈

    @Test
    fun `이탈에 성공하면 IDLE 로 돌아가고 미지원 표시가 되살아난다`() {
        coEvery { setMode(true) } returns Result.success(true)
        coEvery { setMode(false) } returns Result.success(true)
        val m = manager()
        m.enterCardBrowse()

        m.exitCardBrowse()

        assertEquals(CardBrowseState.IDLE, m.cardBrowseState.value)
        // 이탈하면 카드가 다시 보이지 않으므로, 화면이 카드 보기 안내를 다시 그려야 한다.
        assertTrue(m.isStorageUnsupported.value)
        assertNull(m.cardBrowseError.value)
    }

    @Test
    fun `이탈에 실패하면 STUCK 으로 남는다`() {
        coEvery { setMode(true) } returns Result.success(true)
        coEvery { setMode(false) } returns Result.failure(IllegalStateException("전환 실패"))
        val m = manager()
        m.enterCardBrowse()

        m.exitCardBrowse()

        // 조용히 IDLE 로 돌아가면 카메라는 갇혀 있는데 앱은 촬영이 되는 줄 알게 된다.
        assertEquals(CardBrowseState.STUCK, m.cardBrowseState.value)
        assertEquals(CardBrowseError.EXIT_FAILED, m.cardBrowseError.value)
    }

    @Test
    fun `STUCK 상태에서 이탈을 재시도할 수 있다`() {
        coEvery { setMode(true) } returns Result.success(true)
        coEvery { setMode(false) } returns Result.failure(IllegalStateException("전환 실패"))
        val m = manager()
        m.enterCardBrowse()
        m.exitCardBrowse()
        assertEquals(CardBrowseState.STUCK, m.cardBrowseState.value)

        coEvery { setMode(false) } returns Result.success(true)
        m.exitCardBrowse()

        assertEquals(CardBrowseState.IDLE, m.cardBrowseState.value)
    }

    @Test
    fun `IDLE 에서 이탈을 부르면 아무 일도 하지 않는다`() {
        val m = manager()

        m.exitCardBrowse()

        assertEquals(CardBrowseState.IDLE, m.cardBrowseState.value)
        coVerify(exactly = 0) { setMode(false) }
    }

    // MARK: - 정리와 오류 해제

    @Test
    fun `카드 보기가 켜진 채 정리되면 촬영 모드로 되돌린다`() {
        coEvery { setMode(true) } returns Result.success(true)
        coEvery { setMode(false) } returns Result.success(true)
        val m = manager()
        m.enterCardBrowse()

        m.cleanup()

        // 되돌리지 않으면 다음 세션이 카드 보기에 갇힌 채 시작해 촬영이 막힌다.
        coVerify(exactly = 1) { setMode(false) }
        assertEquals(CardBrowseState.IDLE, m.cardBrowseState.value)
    }

    @Test
    fun `카드 보기가 꺼져 있으면 정리할 때 전환을 시도하지 않는다`() {
        val m = manager()

        m.cleanup()

        coVerify(exactly = 0) { setMode(any()) }
    }

    // MARK: - 타임아웃 재시도

    @Test
    fun `이탈이 타임아웃이면 한 번 더 시도해 복귀한다`() {
        coEvery { setMode(true) } returns Result.success(true)
        val m = manager()
        m.enterCardBrowse()
        // 첫 시도는 타임아웃, 두 번째는 성공
        coEvery { setMode(false) } returnsMany listOf(
            Result.failure(PtpTimeoutException("응답 시간 초과")),
            Result.success(true)
        )

        m.exitCardBrowse()

        // 타임아웃은 실패가 확정된 것이 아니다. 곧바로 STUCK 으로 두면 되돌릴 수 있는
        // 상황에서도 사용자에게 카메라 전원을 껐다 켜라고 안내하게 된다.
        coVerify(exactly = 2) { setMode(false) }
        assertEquals(CardBrowseState.IDLE, m.cardBrowseState.value)
        assertNull(m.cardBrowseError.value)
    }

    @Test
    fun `타임아웃 재시도까지 실패하면 STUCK 이 된다`() {
        coEvery { setMode(true) } returns Result.success(true)
        val m = manager()
        m.enterCardBrowse()
        coEvery { setMode(false) } returns Result.failure(PtpTimeoutException("응답 시간 초과"))

        m.exitCardBrowse()

        coVerify(exactly = 2) { setMode(false) }
        assertEquals(CardBrowseState.STUCK, m.cardBrowseState.value)
        assertEquals(CardBrowseError.EXIT_FAILED, m.cardBrowseError.value)
    }

    @Test
    fun `타임아웃이 아닌 실패는 재시도하지 않는다`() {
        coEvery { setMode(true) } returns Result.success(true)
        val m = manager()
        m.enterCardBrowse()
        coEvery { setMode(false) } returns Result.failure(IllegalStateException("거부됨"))

        m.exitCardBrowse()

        // 카메라가 명확히 거부한 경우까지 재시도하면 왕복만 늘어난다.
        coVerify(exactly = 1) { setMode(false) }
        assertEquals(CardBrowseState.STUCK, m.cardBrowseState.value)
    }

    // MARK: - 연결 해제

    @Test
    fun `연결이 끊기면 카드 보기 상태가 초기화된다`() {
        coEvery { setMode(true) } returns Result.success(true)
        val m = manager()
        m.enterCardBrowse()
        assertEquals(CardBrowseState.ACTIVE, m.cardBrowseState.value)

        m.resetCardBrowseOnDisconnect()

        // 카메라가 사라진 뒤라 전환 명령은 보낼 수 없다. 다시 연결했을 때 "카드 보기가
        // 켜져 있다"고 잘못 알고 있지 않도록 앱 상태만 되돌린다.
        assertEquals(CardBrowseState.IDLE, m.cardBrowseState.value)
        assertNull(m.cardBrowseError.value)
        coVerify(exactly = 0) { setMode(false) }
    }

    @Test
    fun `지원하지 않는 카메라에서는 진입이 실패로 끝난다`() {
        // 앱은 제조사를 따로 판정하지 않는다. 전환에 쓰는 위젯이 소니이면서 해당 오퍼레이션을
        // 광고하는 세션에만 존재하므로, 그 판정을 libgphoto2 한 곳에만 두는 편이 규칙이 두
        // 군데로 갈라지지 않는다. 다른 제조사에서는 여기처럼 실패가 돌아온다.
        coEvery { setMode(true) } returns Result.failure(IllegalStateException("위젯 없음"))
        val m = manager()

        m.enterCardBrowse()

        assertEquals(CardBrowseState.IDLE, m.cardBrowseState.value)
        assertEquals(CardBrowseError.ENTER_FAILED, m.cardBrowseError.value)
        coVerify(exactly = 0) { invalidateCache() }
    }

    @Test
    fun `실패 사유를 사용자가 확인하면 지워진다`() {
        coEvery { setMode(true) } returns Result.failure(IllegalStateException("지원하지 않음"))
        val m = manager()
        m.enterCardBrowse()
        assertEquals(CardBrowseError.ENTER_FAILED, m.cardBrowseError.value)

        m.clearCardBrowseError()

        assertNull(m.cardBrowseError.value)
    }
}
