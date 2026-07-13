package com.inik.camcon.data.repository.managers

import android.app.Application
import android.media.ExifInterface
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.domain.manager.PhotoCaptureEventManager
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import com.inik.camcon.domain.usecase.FilmLutUseCase
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

/**
 * FREE 티어 리사이즈 EXIF orientation 이중 회전 회귀 테스트 (C7 후속 fix).
 *
 * [PhotoDownloadManager.copyAllExifData] 가 픽셀 회전을 실제로 적용한 경우(rotationApplied=true)
 * TAG_ORIENTATION 을 NORMAL(1)로 재설정하고, 회전을 건너뛴 경우(false)엔 원본 orientation 을
 * 보존하는지 검증한다. 그 외 태그(MAKE/DATETIME_ORIGINAL)는 두 경우 모두 보존되어야 한다.
 *
 * PhotoDownloadManager 는 androidx 가 아닌 [android.media.ExifInterface] 를 쓰므로 Robolectric 의
 * 실제 프레임워크 구현으로 실 JPEG 파일에 read/write 한다(compileSdk 36 이나 Robolectric 4.14
 * 지원 한계로 sdk=34 고정). copyAllExifData 는 private → 리플렉션으로 호출한다.
 *
 * application=Application::class: 실제 CamCon(@HiltAndroidApp)은 onCreate 에서 libgphoto2 네이티브를
 * 로딩해 호스트 JVM 에서 UnsatisfiedLinkError → 빈 스텁 Application 으로 대체(ExifCaptureTimeTest 관례).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PhotoDownloadManagerExifOrientationTest {

    private lateinit var manager: PhotoDownloadManager
    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        manager = PhotoDownloadManager(
            context = mockk(relaxed = true),
            nativeDataSource = mockk<NativeCameraDataSource>(relaxed = true),
            appPreferencesDataSource = mockk<AppPreferencesDataSource>(relaxed = true),
            colorTransferUseCase = mockk<ColorTransferUseCase>(relaxed = true),
            filmLutUseCase = mockk<FilmLutUseCase>(relaxed = true),
            photoCaptureEventManager = mockk<PhotoCaptureEventManager>(relaxed = true),
            getSubscriptionUseCase = mockk<GetSubscriptionUseCase>(relaxed = true),
            validateImageFormatUseCase = mockk<ValidateImageFormatUseCase>(relaxed = true),
            validateFeatureAccessUseCase = mockk<ValidateFeatureAccessUseCase>(relaxed = true),
            transferProgressTracker = mockk<TransferProgressTracker>(relaxed = true),
            ioDispatcher = StandardTestDispatcher()
        )
        tmpDir = File(System.getProperty("java.io.tmpdir"), "exif_test_${System.nanoTime()}")
            .apply { mkdirs() }
    }

    @Test
    fun `회전 적용 시 orientation 은 NORMAL 로 재설정되고 다른 태그는 보존된다`() {
        // Arrange: orientation=6(90도) + MAKE/DATETIME_ORIGINAL 이 박힌 원본
        val original = makeOriginal(orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val output = newBaselineJpeg("out_rotated.jpg")

        // Act: 픽셀을 이미 회전시켰으므로 rotationApplied=true
        invokeCopyAllExifData(original, output, rotationApplied = true)

        // Assert: 이중 회전 방지를 위해 orientation=NORMAL, 나머지 태그는 그대로 복사
        val out = ExifInterface(output.absolutePath)
        assertEquals(
            "회전 적용 후 orientation 은 NORMAL(1) 이어야 이중 회전이 안 남",
            ExifInterface.ORIENTATION_NORMAL,
            out.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
        )
        assertEquals("Nikon", out.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals("2026:06:09 14:30:25", out.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
    }

    @Test
    fun `회전 미적용 시 원본 orientation 이 보존된다`() {
        // Arrange
        val original = makeOriginal(orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val output = newBaselineJpeg("out_notrotated.jpg")

        // Act: 회전을 건너뛴 경로(메모리 부족 등) — 뷰어 자동 회전에 맡겨야 하므로 원본 보존
        invokeCopyAllExifData(original, output, rotationApplied = false)

        // Assert: orientation 원본값(6) 유지, 다른 태그도 보존
        val out = ExifInterface(output.absolutePath)
        assertEquals(
            "회전 미적용 시 원본 orientation(6) 을 보존해야 뷰어가 자동 회전",
            ExifInterface.ORIENTATION_ROTATE_90,
            out.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
        )
        assertEquals("Nikon", out.getAttribute(ExifInterface.TAG_MAKE))
    }

    // --- Helpers ---

    /** 실 1x1 JPEG 에 orientation + MAKE + DATETIME_ORIGINAL 을 써 원본 픽스처를 만든다. */
    private fun makeOriginal(orientation: Int): File {
        val file = newBaselineJpeg("original.jpg")
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        exif.setAttribute(ExifInterface.TAG_MAKE, "Nikon")
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:06:09 14:30:25")
        exif.saveAttributes()

        // fail-fast: Robolectric 의 android.media.ExifInterface writer 가 실제로 persist 하는지 확인.
        val readback = ExifInterface(file.absolutePath)
        assertTrue(
            "테스트 전제: ExifInterface write 가 동작해야 함(setup persist 실패)",
            readback.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1) == orientation &&
                readback.getAttribute(ExifInterface.TAG_MAKE) == "Nikon"
        )
        return file
    }

    private fun newBaselineJpeg(name: String): File =
        File(tmpDir, name).apply { writeBytes(Base64.getDecoder().decode(JPEG_NO_EXIF)) }

    private fun invokeCopyAllExifData(original: File, output: File, rotationApplied: Boolean) {
        val method = PhotoDownloadManager::class.java.getDeclaredMethod(
            "copyAllExifData",
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(manager, original.absolutePath, output.absolutePath, 100, 200, rotationApplied)
    }

    companion object {
        // 1x1 JPEG, EXIF 세그먼트 없음(실제 baseline JPEG). ExifCaptureTimeTest 픽스처와 동일.
        private const val JPEG_NO_EXIF =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5/ooooA//2Q=="
    }
}
