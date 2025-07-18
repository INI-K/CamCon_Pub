package com.inik.camcon.domain.usecase

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.os.Build
import com.inik.camcon.data.processor.ColorTransferProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 색감 전송 기능을 위한 UseCase
 */
@Singleton
class ColorTransferUseCase @Inject constructor(
    private val colorTransferProcessor: ColorTransferProcessor
) {

    /**
     * 캐시된 참조 이미지 통계를 사용하여 색감 전송을 수행하고 EXIF 메타데이터를 보존합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceImagePath 참조할 이미지의 파일 경로
     * @param originalImagePath 원본 이미지 파일 경로 (EXIF 메타데이터 복사용)
     * @param outputPath 결과 이미지 저장 경로
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransferAndSave(
        inputBitmap: Bitmap,
        referenceImagePath: String,
        originalImagePath: String,
        outputPath: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 참조 이미지 통계를 캐시에서 가져오거나 계산
            val referenceStats = colorTransferProcessor.getCachedReferenceStats(referenceImagePath)
                ?: return@withContext null

            // 캐시된 통계를 사용하여 색감 전송 적용
            val transferredBitmap = colorTransferProcessor.applyColorTransferWithCachedStats(
                inputBitmap,
                referenceStats
            )

            // 결과 이미지를 파일로 저장
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            java.io.FileOutputStream(outputFile).use { outputStream ->
                transferredBitmap.compress(
                    android.graphics.Bitmap.CompressFormat.JPEG,
                    95,
                    outputStream
                )
            }

            // 원본 이미지의 EXIF 메타데이터를 결과 이미지로 복사
            try {
                val originalExif = androidx.exifinterface.media.ExifInterface(originalImagePath)
                val resultExif = androidx.exifinterface.media.ExifInterface(outputPath)

                // 중요한 EXIF 태그들을 복사
                val tagsToPreserve = arrayOf(
                    androidx.exifinterface.media.ExifInterface.TAG_DATETIME,
                    androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL,
                    androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED,
                    androidx.exifinterface.media.ExifInterface.TAG_MAKE,
                    androidx.exifinterface.media.ExifInterface.TAG_MODEL,
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF,
                    androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
                    androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME,
                    androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER,
                    androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS,
                    androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH,
                    androidx.exifinterface.media.ExifInterface.TAG_APERTURE_VALUE,
                    androidx.exifinterface.media.ExifInterface.TAG_SHUTTER_SPEED_VALUE,
                    androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE,
                    androidx.exifinterface.media.ExifInterface.TAG_FLASH
                )

                var copiedTags = 0
                for (tag in tagsToPreserve) {
                    originalExif.getAttribute(tag)?.let { value ->
                        resultExif.setAttribute(tag, value)
                        copiedTags++
                    }
                }

                // 처리된 이미지임을 표시
                resultExif.setAttribute(
                    androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE,
                    "CamCon - Color Transfer Applied"
                )

                // EXIF 데이터 저장
                resultExif.saveAttributes()
                android.util.Log.d("ColorTransferUseCase", "✅ EXIF 메타데이터 복사 완료: $copiedTags 개 태그")
            } catch (e: Exception) {
                android.util.Log.w("ColorTransferUseCase", "⚠️ EXIF 메타데이터 복사 실패: ${e.message}")
            }

            transferredBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 파일 경로의 참조 이미지를 사용하여 색감 전송을 수행하고 EXIF 메타데이터를 보존합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceBitmap 참조할 색감의 이미지
     * @param originalImagePath 원본 이미지 파일 경로 (EXIF 메타데이터 복사용)
     * @param outputPath 결과 이미지 저장 경로
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransferAndSave(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        originalImagePath: String,
        outputPath: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            colorTransferProcessor.applyColorTransferAndSave(
                inputBitmap,
                referenceBitmap,
                originalImagePath,
                outputPath
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 파일 경로의 참조 이미지를 사용하여 색감 전송을 수행합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceImagePath 참조할 이미지의 파일 경로
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransfer(
        inputBitmap: Bitmap,
        referenceImagePath: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 캐시된 참조 이미지 통계 사용
            val referenceStats = colorTransferProcessor.getCachedReferenceStats(referenceImagePath)
                ?: return@withContext null

            colorTransferProcessor.applyColorTransferWithCachedStats(inputBitmap, referenceStats)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 비트맵을 직접 사용하여 색감 전송을 수행합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceBitmap 참조할 색감의 이미지
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransfer(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            colorTransferProcessor.applyColorTransfer(inputBitmap, referenceBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 참조 이미지가 변경되었을 때 캐시를 초기화합니다.
     */
    fun clearReferenceCache() {
        colorTransferProcessor.clearCache()
    }

    /**
     * 이미지 파일이 유효한지 확인합니다.
     * @param imagePath 확인할 이미지 파일 경로
     * @return 유효한 이미지 파일인 경우 true
     */
    fun isValidImageFile(imagePath: String): Boolean {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return false

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            // 이미지가 정상적으로 디코딩되었는지 확인
            options.outWidth > 0 && options.outHeight > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun loadImageFromPath(imagePath: String): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB) // sRGB 색공간 설정
            }
        }
        return BitmapFactory.decodeFile(imagePath, options)
    }
}