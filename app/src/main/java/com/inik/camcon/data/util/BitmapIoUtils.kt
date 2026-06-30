package com.inik.camcon.data.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * data 레이어 이미지 후처리(필름LUT·색감전송) 공통 IO/EXIF/회전 유틸 (CD-2 중복 제거).
 *
 * 모든 함수는 동기 IO이며 호출부의 `withContext(ioDispatcher)` 안에서 호출된다(순수함수, DI 없음).
 * presentation 의 `ImageProcessingUtils` 는 레이어 경계상 data 가 쓸 수 없어 data/util 에 둔다.
 */
object BitmapIoUtils {

    /** 결과물에 보존하는 EXIF 18태그(촬영 메타·GPS·노출). */
    private val PRESERVED_EXIF_TAGS = arrayOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH
    )

    /**
     * [bitmap] 을 JPEG([quality])로 임시파일(`[prefix]*.jpg`)에 저장하고 bitmap 을 회수한다.
     * 실패 시 잔존 임시파일을 삭제하고 null 을 반환한다(로깅은 호출부가 반환값으로 판별).
     */
    fun saveBitmapToTempFile(bitmap: Bitmap, prefix: String, quality: Int = 95): String? {
        var tempFile: File? = null
        return try {
            tempFile = File.createTempFile(prefix, ".jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            bitmap.recycle()
            tempFile.absolutePath
        } catch (e: Exception) {
            bitmap.recycle()
            // 압축/쓰기 실패 시 잔존 임시파일 정리 (캐시 디렉토리 누적 방지)
            tempFile?.delete()
            null
        }
    }

    /**
     * [inputImagePath] 의 보존 대상 EXIF 18태그를 [outputPath] 로 복사하고 SOFTWARE 태그를 [software] 로 설정한다.
     * @return 복사된 태그 수.
     */
    fun copyExifMetadata(inputImagePath: String, outputPath: String, software: String): Int {
        val inputExif = ExifInterface(inputImagePath)
        val outputExif = ExifInterface(outputPath)
        var copied = 0
        for (tag in PRESERVED_EXIF_TAGS) {
            inputExif.getAttribute(tag)?.let { value ->
                outputExif.setAttribute(tag, value)
                copied++
            }
        }
        outputExif.setAttribute(ExifInterface.TAG_SOFTWARE, software)
        outputExif.saveAttributes()
        return copied
    }

    /**
     * [bitmap] 을 [degrees] 만큼 회전한 사본을 반환한다. 새 인스턴스가 생기면 원본을 즉시 회수해
     * 순간 2× 메모리 피크를 줄인다. 회전 결과가 원본과 같으면 원본을 그대로 반환한다.
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}
