package com.inik.camcon.data.repository.managers

import android.app.Application
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `CameraEventManager.createCameraCaptureListener(...)` 익명 리스너의 `onPhotoCaptured(...)` 분기 단위 테스트.
 * (CameraEventManager.kt:525~647 — 티어 감사가 확인한 물리셔터 RAW 게이팅 3겹 fail-closed 중 한 겹)
 *
 * 고정하는 불변식:
 *  1. 게이팅 분기 — RAW 파일은 `ValidateImageFormatUseCase` 단일 지점을 거쳐, 접근 차단 시
 *     onPhotoCaptured 콜백으로 넘어가지 않고 `onRawFileRestricted` 로 라우팅된다. 접근 허용 시에만 콜백 발화.
 *  2. markDone 누락 방지 — 미지원 확장자/차단된 RAW/예외 등 다운로드로 넘어가지 않는 모든 종단 경로에서
 *     `transferProgressTracker.markDone(fileName)` 을 호출해 전송 배지 고착을 막는다.
 *  3. 예외 경로 fail-closed — 검증/처리 중 예외가 나도 catch(#642-645)가 onPhotoCaptured 를 **재호출하지 않는다**
 *     (재호출 시 RAW 게이팅 우회 + 사진 이중 처리). 재호출 대신 markDone 만 수행.
 *
 * 접근 방식: 발화 경로(`CameraNative.listenCameraEvents`)는 JNI 라 호스트 JVM 실행 불가지만, 검증 대상인
 * override 분기는 순수 Kotlin 이므로 private `createCameraCaptureListener` 를 리플렉션으로 얻어 직접 구동한다
 * (선례: [CameraEventManagerPtpipConnectionLostTest]).
 *
 * `Dispatchers.Unconfined`: RAW 검증은 `scope.launch(ioDispatcher){ ... }` 내부에서 일어나므로,
 * scope/io/main 을 모두 Unconfined 로 두어 콜백까지 인라인 동기 실행시킨다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CameraEventManagerPhotoCapturedGatingTest {

    private val validateImageFormatUseCase = mockk<ValidateImageFormatUseCase>()
    private val transferProgressTracker = mockk<TransferProgressTracker>(relaxed = true)

    private var capturedCount = 0
    private var lastCapturedPath: String? = null
    private var lastCapturedName: String? = null
    private var restrictedCount = 0

    private fun createManager(): CameraEventManager {
        val context: android.content.Context =
            org.robolectric.RuntimeEnvironment.getApplication()
        return CameraEventManager(
            context = context,
            nativeDataSource = mockk(relaxed = true),
            usbCameraManager = mockk(relaxed = true),
            validateImageFormatUseCase = validateImageFormatUseCase,
            photoDownloadManager = mockk(relaxed = true),
            transferProgressTracker = transferProgressTracker,
            errorHandlingManager = mockk(relaxed = true),
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined
        )
    }

    private fun buildListener(
        manager: CameraEventManager,
        connectionType: CameraEventManager.ConnectionType = CameraEventManager.ConnectionType.USB
    ): CameraCaptureListener {
        val method = CameraEventManager::class.java.getDeclaredMethod(
            "createCameraCaptureListener",
            CameraEventManager.ConnectionType::class.java,
            Function2::class.java,   // onPhotoCaptured: (String, String) -> Unit
            Function3::class.java,   // onPhotoDownloaded: ((String, String, ByteArray) -> Unit)?
            Function0::class.java,   // onFlushComplete: () -> Unit
            Function1::class.java    // onCaptureFailed: (Int) -> Unit
        )
        method.isAccessible = true
        val onPhotoCaptured: (String, String) -> Unit = { path, name ->
            capturedCount++
            lastCapturedPath = path
            lastCapturedName = name
        }
        val onPhotoDownloaded: ((String, String, ByteArray) -> Unit)? = null
        val onFlushComplete: () -> Unit = {}
        val onCaptureFailed: (Int) -> Unit = {}
        val listener = method.invoke(
            manager,
            connectionType,
            onPhotoCaptured,
            onPhotoDownloaded,
            onFlushComplete,
            onCaptureFailed
        ) as CameraCaptureListener
        manager.onRawFileRestricted = { _, _ -> restrictedCount++ }
        return listener
    }

    // ── 1. 게이팅 분기 ──

    @Test
    fun `일반 JPG는 게이팅 통과하여 onPhotoCaptured 콜백을 호출한다`() {
        val manager = createManager()
        every { validateImageFormatUseCase.isRawFile(any()) } returns false
        val listener = buildListener(manager)

        listener.onPhotoCaptured("/tmp/KAY_1000.jpg", "KAY_1000.jpg")

        assertEquals("일반 파일은 콜백 1회", 1, capturedCount)
        assertEquals("/tmp/KAY_1000.jpg", lastCapturedPath)
        assertEquals("KAY_1000.jpg", lastCapturedName)
        assertEquals("일반 파일은 차단 콜백 0회", 0, restrictedCount)
        // 촬영 감지 = 다운로드 시작 마킹.
        verify(exactly = 1) { transferProgressTracker.markDownloading("KAY_1000.jpg") }
    }

    @Test
    fun `RAW 접근 허용(PRO)이면 onPhotoCaptured 콜백을 호출한다`() {
        val manager = createManager()
        every { validateImageFormatUseCase.isRawFile("KAY_1000.NEF") } returns true
        coEvery { validateImageFormatUseCase.validateRawFileAccess("KAY_1000.NEF") } returns
            ValidateImageFormatUseCase.ValidationResult(isSupported = true, isRawFile = true)
        val listener = buildListener(manager)

        listener.onPhotoCaptured("/tmp/KAY_1000.NEF", "KAY_1000.NEF")

        assertEquals("허용된 RAW 는 콜백 1회", 1, capturedCount)
        assertEquals("허용된 RAW 는 차단 콜백 0회", 0, restrictedCount)
    }

    @Test
    fun `RAW 접근 차단(FREE)이면 콜백 대신 onRawFileRestricted로 라우팅하고 markDone`() {
        val manager = createManager()
        every { validateImageFormatUseCase.isRawFile("KAY_1000.NEF") } returns true
        coEvery { validateImageFormatUseCase.validateRawFileAccess("KAY_1000.NEF") } returns
            ValidateImageFormatUseCase.ValidationResult(
                isSupported = false,
                restrictionMessage = UiText.Raw("RAW는 PRO 전용"),
                needsUpgrade = true,
                isRawFile = true
            )
        val listener = buildListener(manager)

        listener.onPhotoCaptured("/tmp/KAY_1000.NEF", "KAY_1000.NEF")

        assertEquals("차단된 RAW 는 onPhotoCaptured 콜백 0회(우회 차단)", 0, capturedCount)
        assertEquals("차단된 RAW 는 제한 콜백 1회", 1, restrictedCount)
        // 다운로드로 넘어가지 않으므로 진행 큐에서 제거(배지 고착 방지).
        verify(exactly = 1) { transferProgressTracker.markDone("KAY_1000.NEF") }
    }

    // ── 2. markDone 누락 방지 ──

    @Test
    fun `지원하지 않는 확장자는 콜백 없이 markDone으로 진행 큐를 정리한다`() {
        val manager = createManager()
        // mp4 는 SUPPORTED_IMAGE_EXTENSIONS 밖 → isRawFile 도달 전 종단.
        val listener = buildListener(manager)

        listener.onPhotoCaptured("/tmp/clip.mp4", "clip.mp4")

        assertEquals("미지원 확장자는 콜백 0회", 0, capturedCount)
        verify(exactly = 1) { transferProgressTracker.markDownloading("clip.mp4") }
        verify(exactly = 1) { transferProgressTracker.markDone("clip.mp4") }
    }

    // ── 3. 예외 경로 fail-closed ──

    @Test
    fun `isRawFile 검증이 예외를 던지면 onPhotoCaptured를 재호출하지 않고 markDone만 한다`() {
        val manager = createManager()
        // 확장자는 지원 목록(nef)이라 isRawFile 까지 진입 → 여기서 예외 발생.
        every { validateImageFormatUseCase.isRawFile("KAY_1000.nef") } throws
            RuntimeException("format probe boom")
        val listener = buildListener(manager)

        listener.onPhotoCaptured("/tmp/KAY_1000.nef", "KAY_1000.nef")

        // 재호출 시 RAW 게이팅 우회 + 이중 처리 — 반드시 0회여야 한다.
        assertEquals("예외 경로에서 콜백 재호출 없음(fail-closed)", 0, capturedCount)
        assertEquals("예외 경로는 제한 콜백도 호출하지 않음", 0, restrictedCount)
        // 배지 고착 방지용 markDone 은 수행.
        verify(exactly = 1) { transferProgressTracker.markDone("KAY_1000.nef") }
    }
}
