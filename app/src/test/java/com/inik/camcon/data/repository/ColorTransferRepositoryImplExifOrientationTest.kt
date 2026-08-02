package com.inik.camcon.data.repository

import android.app.Application
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.data.processor.ColorTransferProcessor
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
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
 * 색감전송 미리보기 EXIF orientation 회귀 테스트.
 *
 * 회귀 대상: 미리보기 경로([ColorTransferRepositoryImpl.applyColorTransfer],
 * [ColorTransferRepositoryImpl.applyColorTransferWithGPUCached])가 대상 이미지를 EXIF 방향
 * 보정 없이 디코딩해, 세로컷이 90도 누운 채 화면에 그려지던 버그.
 *
 * 왜 "프로세서에 넘어가는 비트맵"을 보는가:
 * 하류(BitmapDecodeUtils·Compose Image)는 EXIF 태그를 읽지 않는다. 따라서 태그가 아니라
 * **픽셀 자체**가 회전돼 있어야 한다. 이 계약이 깨지는 지점이 프로세서 입력이므로 거기서 검증한다.
 *
 * 저장 경로([ColorTransferRepositoryImpl.applyColorTransferAndSave])는 의도적으로 제외한다.
 * 그 경로는 BitmapIoUtils.copyExifMetadata 가 TAG_ORIENTATION 을 보존하므로 픽셀까지 회전하면
 * 이중 회전이 된다. 회전하지 않는 것이 정상이다.
 *
 * Robolectric 을 쓰는 이유: Bitmap/Matrix/BitmapFactory 실동작이 필요하다.
 * (기존 ExifRotationTest 는 분기 로직을 테스트에 복제한 미러라 실코드 회귀를 잡지 못한다.)
 * sdk=34 고정·스텁 Application 은 PhotoDownloadManagerExifOrientationTest 관례를 따른다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ColorTransferRepositoryImplExifOrientationTest {

    private lateinit var processor: ColorTransferProcessor
    private lateinit var repository: ColorTransferRepositoryImpl
    private lateinit var tmpDir: File

    /** 프로세서에 실제로 넘어온 입력 비트맵의 크기. 회수(recycle) 전에 즉시 읽는다. */
    private var inputWidth = -1
    private var inputHeight = -1

    @Before
    fun setUp() {
        processor = mockk(relaxed = true)
        repository = ColorTransferRepositoryImpl(
            colorTransferProcessor = processor,
            ioDispatcher = StandardTestDispatcher()
        )
        tmpDir = File(System.getProperty("java.io.tmpdir"), "ct_exif_${System.nanoTime()}")
            .apply { mkdirs() }
        inputWidth = -1
        inputHeight = -1

        // 두 미리보기 경로 모두 입력 크기를 기록하고, 원본과 무관한 새 비트맵을 돌려준다.
        // (같은 인스턴스를 돌려주면 saveBitmapToTempFile 과 finally 에서 이중 recycle 이 난다.)
        coEvery { processor.applyColorTransferOptimized(any(), any(), any()) } answers {
            recordInput(firstArg())
        }
        coEvery { processor.applyColorTransferWithGPUCached(any(), any(), any()) } answers {
            recordInput(firstArg())
        }
        coEvery { processor.getCachedReferenceStats(any()) } returns
            arrayOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f))
    }

    @Test
    fun `applyColorTransfer 는 대상의 EXIF 회전을 픽셀에 반영한다`() = runTest {
        // Arrange: 픽셀은 가로 4x2, EXIF 는 90도 회전 지시 — 카메라가 세로컷을 저장하는 형태
        val target = writeJpeg("target.jpg", ExifInterface.ORIENTATION_ROTATE_90)
        val reference = writeJpeg("reference.jpg", ExifInterface.ORIENTATION_NORMAL)

        // Act
        repository.applyColorTransfer(target.absolutePath, reference.absolutePath, 0.5f, 0)

        // Assert: 4x2(가로) 가 2x4(세로) 로 뒤집혀 넘어가야 한다
        assertEquals("EXIF 90도가 반영되면 폭은 원본 높이(2)가 된다", 2, inputWidth)
        assertEquals("EXIF 90도가 반영되면 높이는 원본 폭(4)이 된다", 4, inputHeight)
        assertTrue("세로컷은 높이가 폭보다 커야 한다", inputHeight > inputWidth)
    }

    @Test
    fun `applyColorTransferWithGPUCached 는 대상의 EXIF 회전을 픽셀에 반영한다`() = runTest {
        val target = writeJpeg("target.jpg", ExifInterface.ORIENTATION_ROTATE_90)
        val reference = writeJpeg("reference.jpg", ExifInterface.ORIENTATION_NORMAL)

        repository.applyColorTransferWithGPUCached(
            target.absolutePath, reference.absolutePath, 0.5f, 0
        )

        assertEquals(2, inputWidth)
        assertEquals(4, inputHeight)
    }

    @Test
    fun `orientation 이 NORMAL 이면 회전하지 않는다`() = runTest {
        // 과잉 회전 방지 — 이미 정방향인 사진을 돌리면 반대 방향으로 깨진다
        val target = writeJpeg("target.jpg", ExifInterface.ORIENTATION_NORMAL)
        val reference = writeJpeg("reference.jpg", ExifInterface.ORIENTATION_NORMAL)

        repository.applyColorTransfer(target.absolutePath, reference.absolutePath, 0.5f, 0)

        assertEquals("정방향 사진은 원본 폭(4) 유지", 4, inputWidth)
        assertEquals("정방향 사진은 원본 높이(2) 유지", 2, inputHeight)
    }

    @Test
    fun `orientation 이 180 이면 크기는 그대로다`() = runTest {
        // 180도는 회전해도 가로세로가 바뀌지 않는다. 크기만으로는 구분되지 않으므로
        // "차원 교환이 일어나지 않는다"만 보장한다.
        val target = writeJpeg("target.jpg", ExifInterface.ORIENTATION_ROTATE_180)
        val reference = writeJpeg("reference.jpg", ExifInterface.ORIENTATION_NORMAL)

        repository.applyColorTransfer(target.absolutePath, reference.absolutePath, 0.5f, 0)

        assertEquals(4, inputWidth)
        assertEquals(2, inputHeight)
    }

    // --- Helpers ---

    private fun recordInput(bitmap: Bitmap): Bitmap {
        inputWidth = bitmap.width
        inputHeight = bitmap.height
        return Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    }

    /** 4x2 가로 JPEG 를 쓰고 지정한 orientation 태그를 박는다. */
    private fun writeJpeg(name: String, orientation: Int): File {
        val file = File(tmpDir, name).apply {
            writeBytes(Base64.getDecoder().decode(LANDSCAPE_4X2_JPEG))
        }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }

        // fail-fast: 픽스처 전제가 깨지면 본 검증이 무의미해진다.
        val readback = ExifInterface(file.absolutePath)
        assertEquals(
            "테스트 전제: orientation 이 파일에 기록되어야 함",
            orientation,
            readback.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)
        )
        return file
    }

    companion object {
        /** 4(가로) x 2(세로) baseline JPEG, EXIF 세그먼트 없음. 정사각형이면 회전을 검출할 수 없다. */
        private const val LANDSCAPE_4X2_JPEG =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsL" +
                "EBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQU" +
                "FBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAACAAQDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcI" +
                "CQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRol" +
                "JicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ip" +
                "qrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAA" +
                "AAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLR" +
                "ChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaX" +
                "mJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEA" +
                "PwD4H/4SHVP+glef9/3/AMaKKK/02/sHJ/8AoCo/+CqX/wAqPA/trNf+gur/AODan/yw/9k="
    }
}
