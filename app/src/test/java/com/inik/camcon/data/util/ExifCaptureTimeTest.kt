package com.inik.camcon.data.util

import android.app.Application
import android.util.Base64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * ExifCaptureTime 단위 테스트 (사진 정렬/중복 fix).
 *
 * [ExifCaptureTime.parseMillis] 는 androidx ExifInterface 로 EXIF 를 읽으므로 Android 클래스가
 * 필요해 Robolectric 으로 실행한다(compileSdk 36 이지만 Robolectric 4.14 지원 한계로 sdk=34 고정).
 *
 * EXIF write(saveAttributes)는 androidx ExifInterface 의 JPEG writer 가 마커 구조에 엄격해
 * 합성 JPEG 에서 "Invalid marker" 로 실패한다. 따라서 write 대신, DateTimeOriginal 이 이미
 * 박힌 실제 1x1 JPEG 픽스처([JPEG_1430_25] / [JPEG_1430_26])를 디코드해 read 경로만 검증한다.
 *
 * application=Application::class: 실제 앱 CamCon(@HiltAndroidApp)은 onCreate 에서 libgphoto2
 * 네이티브를 로딩해 호스트 JVM 에서 UnsatisfiedLinkError 가 난다. 빈 스텁 Application 으로 대체.
 *
 * EXIF DateTime 은 타임존 정보가 없어 ExifInterface 가 기기 기본 TZ 로 해석한다. 결정성을 위해
 * 기본 TZ 를 UTC 로 고정하고 기대 millis 도 UTC 로 산출한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExifCaptureTimeTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    // --- 정상 경로: EXIF DateTimeOriginal 추출 ---

    @Test
    fun `EXIF DateTimeOriginal 이 있으면 그 시각의 millis 를 반환`() {
        // Arrange: DateTimeOriginal=2026:06:09 14:30:25 가 박힌 실제 JPEG
        val imageData = decode(JPEG_1430_25)
        val expectedMillis = utcMillis("2026:06:09 14:30:25")

        // Act
        val result = ExifCaptureTime.parseMillis(imageData)

        // Assert
        assertNotNull("EXIF DateTimeOriginal 이 있으면 null 이 아니어야 함", result)
        assertEquals(expectedMillis, result)
    }

    @Test
    fun `서로 다른 EXIF 촬영 시각은 단조 증가하는 millis 로 매핑`() {
        // Arrange: 1초 차이 두 촬영 시각
        val earlier = ExifCaptureTime.parseMillis(decode(JPEG_1430_25))
        val later = ExifCaptureTime.parseMillis(decode(JPEG_1430_26))

        // Assert: 정렬 기준으로 쓰이므로 더 늦은 촬영이 더 큰 millis
        assertNotNull(earlier)
        assertNotNull(later)
        assertTrue("늦은 촬영 시각이 더 큰 millis 여야 함", later!! > earlier!!)
        assertEquals(1000L, later - earlier)
    }

    // --- 실패/폴백 경로: EXIF 없음 → null ---

    @Test
    fun `EXIF 없는 JPEG 바이트는 null 반환`() {
        // Arrange: EXIF 세그먼트가 없는 최소 baseline JPEG
        val imageData = decode(JPEG_NO_EXIF)

        // Act
        val result = ExifCaptureTime.parseMillis(imageData)

        // Assert: 추출 실패 시 null (호출측이 currentTimeMillis 로 폴백)
        assertNull("EXIF 가 없으면 null 이어야 함", result)
    }

    @Test
    fun `빈 바이트 배열은 예외 없이 null 반환`() {
        // Arrange & Act
        val result = ExifCaptureTime.parseMillis(ByteArray(0))

        // Assert
        assertNull(result)
    }

    @Test
    fun `JPEG 가 아닌 잘못된 바이트는 예외 없이 null 반환`() {
        // Arrange: 유효한 이미지 컨테이너가 아닌 임의 바이트
        val garbage = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05)

        // Act
        val result = ExifCaptureTime.parseMillis(garbage)

        // Assert: try/catch 로 흡수되어 null
        assertNull(result)
    }

    // --- Helpers ---

    private fun decode(base64: String): ByteArray = Base64.decode(base64, Base64.DEFAULT)

    /** EXIF DateTime 문자열을 UTC 기준 millis 로 환산(테스트에서 기본 TZ=UTC 고정). */
    private fun utcMillis(exifDateTime: String): Long {
        val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.parse(exifDateTime)!!.time
    }

    companion object {
        // 1x1 JPEG, Exif.DateTimeOriginal = "2026:06:09 14:30:25" (piexif 로 생성).
        private const val JPEG_1430_25 =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/4QBERXhpZgAATU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAAZADAAIAAAAUAAAAKDIwMjY6MDY6MDkgMTQ6MzA6MjUA/9sAQwAIBgYHBgUIBwcHCQkICgwUDQwLCwwZEhMPFB0aHx4dGhwcICQuJyAiLCMcHCg3KSwwMTQ0NB8nOT04MjwuMzQy/9sAQwEJCQkMCwwYDQ0YMiEcITIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIy/8AAEQgAAQABAwEiAAIRAQMRAf/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//EALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkjM1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/aAAwDAQACEQMRAD8A+f6KKKAP/9k="

        // 1x1 JPEG, Exif.DateTimeOriginal = "2026:06:09 14:30:26" (위에서 1초 늦음).
        private const val JPEG_1430_26 =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/4QBERXhpZgAATU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAAZADAAIAAAAUAAAAKDIwMjY6MDY6MDkgMTQ6MzA6MjYA/9sAQwAIBgYHBgUIBwcHCQkICgwUDQwLCwwZEhMPFB0aHx4dGhwcICQuJyAiLCMcHCg3KSwwMTQ0NB8nOT04MjwuMzQy/9sAQwEJCQkMCwwYDQ0YMiEcITIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIy/8AAEQgAAQABAwEiAAIRAQMRAf/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//EALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkjM1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/aAAwDAQACEQMRAD8A+f6KKKAP/9k="

        // 1x1 JPEG, EXIF 세그먼트 없음(순수 베이스). decode → ExifInterface read → null 검증용.
        private const val JPEG_NO_EXIF =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5/ooooA//2Q=="
    }
}
