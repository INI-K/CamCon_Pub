package com.inik.camcon.data.util

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 원본 이미지 바이트에서 촬영 시각(EXIF)을 millis로 추출한다.
 *
 * 사진 정렬/중복 처리에서 안정적인 기준 시각을 얻기 위해 사용한다.
 * JPG/NEF 등 EXIF 를 포함한 포맷 모두에서 동작하며, 추출 실패 시 null 을 반환해
 * 호출측이 System.currentTimeMillis() 등으로 폴백할 수 있게 한다.
 *
 * 주의: 호출측(PhotoDownloadManager 등)은 android.media.ExifInterface 를 쓰므로
 * FQN 충돌을 막기 위해 이 유틸은 androidx ExifInterface 만 자체 import 해 격리한다.
 */
object ExifCaptureTime {

    // EXIF DateTime 포맷 (yyyy:MM:dd HH:mm:ss). 폴백 파싱용.
    private const val EXIF_DATETIME_PATTERN = "yyyy:MM:dd HH:mm:ss"

    /**
     * @param imageData 원본 이미지 바이트(재인코딩 이전).
     * @return 촬영 시각 epoch millis, 추출 불가 시 null.
     */
    fun parseMillis(imageData: ByteArray): Long? {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(imageData))

            // 1) SubSec + Offset 까지 반영한 millis (가장 정확).
            exif.dateTimeOriginal?.let { return it }

            // 2) 폴백: TAG_DATETIME 문자열을 직접 파싱.
            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME) ?: return null
            parseDateTimeString(raw)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDateTimeString(raw: String): Long? {
        return try {
            SimpleDateFormat(EXIF_DATETIME_PATTERN, Locale.US).parse(raw)?.time
        } catch (e: Exception) {
            null
        }
    }
}
