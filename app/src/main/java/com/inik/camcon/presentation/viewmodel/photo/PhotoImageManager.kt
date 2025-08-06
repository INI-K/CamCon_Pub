package com.inik.camcon.presentation.viewmodel.photo

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.camera.GetCameraThumbnailUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사진 이미지 관리 전용 매니저
 * 단일책임: 이미지 다운로드, 캐싱, 리사이징 작업만 담당
 */
@Singleton
class PhotoImageManager @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val getCameraThumbnailUseCase: GetCameraThumbnailUseCase
) {

    companion object {
        private const val TAG = "사진이미지매니저"
    }

    // 썸네일 캐시
    private val _thumbnailCache = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val thumbnailCache: StateFlow<Map<String, ByteArray>> = _thumbnailCache.asStateFlow()

    // 고해상도 이미지 캐시
    private val _fullImageCache = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val fullImageCache: StateFlow<Map<String, ByteArray>> = _fullImageCache.asStateFlow()

    // 다운로드 상태 관리
    private val _downloadingImages = MutableStateFlow<Set<String>>(emptySet())
    val downloadingImages: StateFlow<Set<String>> = _downloadingImages.asStateFlow()

    // EXIF 정보 캐시
    private val _exifCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val exifCache: StateFlow<Map<String, String>> = _exifCache.asStateFlow()

    // 작업 취소를 위한 플래그
    private var isManagerActive = true

    /**
     * 썸네일 로드
     */
    fun loadThumbnailsForPhotos(photos: List<CameraPhoto>) {
        Log.d(TAG, "=== 썸네일 로딩 시작: ${photos.size}개 사진 ===")

        CoroutineScope(Dispatchers.IO).launch {
            if (!isManagerActive) {
                Log.d(TAG, "⛔ 썸네일 로딩 중단됨 (매니저 비활성)")
                return@launch
            }

            val currentCache = _thumbnailCache.value.toMutableMap()

            photos.forEach { photo ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (!isManagerActive) {
                            Log.d(TAG, "⛔ 개별 썸네일 로딩 중단됨: ${photo.name}")
                            return@launch
                        }

                        if (!currentCache.containsKey(photo.path)) {
                            Log.d(TAG, "📷 썸네일 로드 시작: ${photo.name}")
                            Log.d(TAG, "   - 경로: ${photo.path}")
                            Log.d(TAG, "   - 파일크기: ${photo.size} bytes")

                            getCameraThumbnailUseCase(photo.path).fold(
                                onSuccess = { thumbnailData ->
                                    if (!isManagerActive) {
                                        Log.d(TAG, "⛔ 썸네일 성공 처리 중단됨: ${photo.name}")
                                        return@launch
                                    }

                                    Log.d(TAG, "✅ 썸네일 데이터 받음: ${photo.name}")
                                    Log.d(TAG, "   - 썸네일 크기: ${thumbnailData.size} bytes")
                                    Log.d(TAG, "   - 썸네일 비어있음: ${thumbnailData.isEmpty()}")

                                    if (thumbnailData.isNotEmpty()) {
                                        // 썸네일 데이터의 헤더 확인 (JPEG인지 등)
                                        val header = thumbnailData.take(8).map { "%02X".format(it) }
                                            .joinToString(" ")
                                        Log.d(TAG, "   - 썸네일 헤더: $header")
                                    }

                                    synchronized(currentCache) {
                                        currentCache[photo.path] = thumbnailData
                                        _thumbnailCache.value = currentCache.toMap()
                                    }
                                    Log.d(TAG, "💾 썸네일 캐시 저장 완료: ${photo.name}")
                                },
                                onFailure = { exception ->
                                    if (!isManagerActive) {
                                        Log.d(TAG, "⛔ 썸네일 실패 처리 중단됨: ${photo.name}")
                                        return@launch
                                    }

                                    Log.e(TAG, "❌ 썸네일 로드 실패: ${photo.path}", exception)
                                    Log.d(TAG, "   - 에러 메시지: ${exception.message}")
                                    Log.d(TAG, "   - 에러 타입: ${exception.javaClass.simpleName}")

                                    // 재시도 로직
                                    Log.d(TAG, "🔄 썸네일 재시도 시작: ${photo.name}")
                                    retryThumbnailLoad(photo, currentCache, maxRetries = 2)
                                }
                            )
                        } else {
                            Log.d(TAG, "♻️ 이미 캐시에 있음: ${photo.name}")
                        }
                    } catch (exception: Exception) {
                        Log.e(TAG, "💥 썸네일 로딩 중 예외: ${photo.name}", exception)
                        if (isManagerActive) {
                            synchronized(currentCache) {
                                currentCache[photo.path] = ByteArray(0)
                                _thumbnailCache.value = currentCache.toMap()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 썸네일 재시도 로직
     */
    private fun retryThumbnailLoad(
        photo: CameraPhoto,
        currentCache: MutableMap<String, ByteArray>,
        maxRetries: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            repeat(maxRetries) { retryIndex ->
                try {
                    if (!isManagerActive) {
                        Log.d(TAG, "⛔ 썸네일 재시도 중단됨: ${photo.name}")
                        return@launch
                    }

                    kotlinx.coroutines.delay(200L * (retryIndex + 1))
                    Log.d(TAG, "썸네일 재시도 ${retryIndex + 1}/${maxRetries}: ${photo.name}")

                    getCameraThumbnailUseCase(photo.path).fold(
                        onSuccess = { retryThumbnailData ->
                            if (!isManagerActive) {
                                Log.d(TAG, "⛔ 썸네일 재시도 성공 처리 중단됨: ${photo.name}")
                                return@launch
                            }

                            synchronized(currentCache) {
                                currentCache[photo.path] = retryThumbnailData
                                _thumbnailCache.value = currentCache.toMap()
                            }
                            Log.d(
                                TAG,
                                "썸네일 재시도 성공: ${photo.name} (${retryThumbnailData.size} bytes)"
                            )
                            return@repeat
                        },
                        onFailure = { retryException ->
                            Log.e(
                                TAG,
                                "썸네일 재시도 ${retryIndex + 1} 실패: ${photo.path}",
                                retryException
                            )

                            if (retryIndex == maxRetries - 1 && isManagerActive) {
                                synchronized(currentCache) {
                                    currentCache[photo.path] = ByteArray(0)
                                    _thumbnailCache.value = currentCache.toMap()
                                }
                            }
                        }
                    )
                } catch (retryException: Exception) {
                    Log.e(TAG, "썸네일 재시도 중 예외: ${photo.name}", retryException)
                    if (retryIndex == maxRetries - 1 && isManagerActive) {
                        synchronized(currentCache) {
                            currentCache[photo.path] = ByteArray(0)
                            _thumbnailCache.value = currentCache.toMap()
                        }
                    }
                }
            }
        }
    }

    /**
     * 고해상도 이미지 다운로드
     */
    fun downloadFullImage(photoPath: String, currentTier: SubscriptionTier) {
        Log.d(TAG, "=== downloadFullImage 호출: $photoPath ===")

        if (_fullImageCache.value.containsKey(photoPath)) {
            Log.d(TAG, "이미 캐시에 있음, 다운로드 생략")
            return
        }

        synchronized(this) {
            if (_downloadingImages.value.contains(photoPath)) {
                Log.d(TAG, "이미 다운로드 중, 중복 요청 무시")
                return
            }
            _downloadingImages.value = _downloadingImages.value + photoPath
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "실제 파일 다운로드 시작: $photoPath")

                val imageData = withContext(Dispatchers.IO) {
                    Log.d(TAG, "downloadCameraPhoto 호출")
                    NativeCall(photoPath)
                }

                if (imageData != null && imageData.isNotEmpty()) {
                    Log.d(TAG, "이미지 데이터 확인: 유효함 (${imageData.size} bytes)")

                    val currentCache = _fullImageCache.value
                    if (!currentCache.containsKey(photoPath)) {
                        val newCache = currentCache + (photoPath to imageData)
                        _fullImageCache.value = newCache

                        Log.d(TAG, "실제 파일 다운로드 성공: ${imageData.size} bytes")

                        // EXIF 파싱
                        if (!_exifCache.value.containsKey(photoPath)) {
                            parseExifFromImageData(photoPath, imageData)
                        }

                        // Free 티어 사용자인 경우 리사이징 처리
                        if (currentTier == SubscriptionTier.FREE) {
                            processImageForFreeTier(photoPath, imageData)
                        }
                    }
                } else {
                    Log.e(TAG, "실제 파일 다운로드 실패: 데이터가 비어있음")
                }
            } catch (e: Exception) {
                Log.e(TAG, "실제 파일 다운로드 중 예외", e)
            } finally {
                _downloadingImages.value = _downloadingImages.value - photoPath
                Log.d(TAG, "다운로드 상태 정리 완료: $photoPath")
            }
        }
    }

    /**
     * Free 티어 사용자를 위한 이미지 처리
     */
    private suspend fun processImageForFreeTier(photoPath: String, imageData: ByteArray) {
        if (!photoPath.endsWith(".jpg", true)) return

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🎯 Free 티어 사용자 - 리사이징 처리 시작")

                val tempFile = File.createTempFile("temp_resize", ".jpg")
                tempFile.writeBytes(imageData)

                val resizedFile = File.createTempFile("temp_resized", ".jpg")

                val resizeSuccess = resizeImageForFreeTier(
                    tempFile.absolutePath,
                    resizedFile.absolutePath
                )

                if (resizeSuccess && resizedFile.exists()) {
                    val resizedData = resizedFile.readBytes()

                    // 캐시 업데이트 (리사이즈된 이미지로 교체)
                    val currentCache = _fullImageCache.value.toMutableMap()
                    currentCache[photoPath] = resizedData
                    _fullImageCache.value = currentCache

                    Log.d(TAG, "✅ Free 티어 리사이징 완료: ${resizedData.size} bytes")
                } else {
                    Log.w(TAG, "⚠️ Free 티어 리사이징 실패, 원본 유지")
                }

                // 임시 파일 정리
                tempFile.delete()
                resizedFile.delete()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Free 티어 리사이징 처리 중 오류", e)
            }
        }
    }

    /**
     * Free 티어 이미지 리사이징
     */
    private suspend fun resizeImageForFreeTier(inputPath: String, outputPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔧 Free 티어 이미지 리사이즈 시작: $inputPath")

                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(inputPath, options)

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight
                val maxDimension = kotlin.math.max(originalWidth, originalHeight)

                if (maxDimension <= 2000) {
                    Log.d(TAG, "이미 작은 이미지 - 리사이즈 불필요")
                    return@withContext File(inputPath).copyTo(File(outputPath), overwrite = true)
                        .exists()
                }

                val scale = 2000.toFloat() / maxDimension.toFloat()
                val newWidth = (originalWidth * scale).toInt()
                val newHeight = (originalHeight * scale).toInt()

                val sampleSize =
                    calculateInSampleSize(originalWidth, originalHeight, newWidth, newHeight)

                options.apply {
                    inJustDecodeBounds = false
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }

                val bitmap = android.graphics.BitmapFactory.decodeFile(inputPath, options)
                    ?: return@withContext false

                try {
                    val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        newWidth,
                        newHeight,
                        true
                    )

                    val originalExif = ExifInterface(inputPath)
                    val orientation = originalExif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )

                    val rotatedBitmap = rotateImageIfRequired(resizedBitmap, orientation)

                    java.io.FileOutputStream(outputPath).use { out ->
                        rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    if (resizedBitmap != rotatedBitmap) {
                        resizedBitmap.recycle()
                    }
                    rotatedBitmap.recycle()

                    copyAllExifData(inputPath, outputPath, newWidth, newHeight)

                    Log.d(TAG, "✅ Free 티어 리사이즈 완료 (EXIF 보존)")
                    true
                } finally {
                    bitmap.recycle()
                }

            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "❌ 메모리 부족으로 리사이즈 실패", e)
                System.gc()
                false
            } catch (e: Exception) {
                Log.e(TAG, "❌ 이미지 리사이즈 실패", e)
                false
            }
        }
    }

    /**
     * EXIF 정보 파싱
     */
    private fun parseExifFromImageData(photoPath: String, imageData: ByteArray) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "EXIF 파싱 시작: $photoPath")

                val tempFile = File.createTempFile("temp_exif", ".jpg")
                tempFile.writeBytes(imageData)

                try {
                    val exif = ExifInterface(tempFile.absolutePath)
                    val exifMap = mutableMapOf<String, Any>()

                    // 기본 정보
                    val basicInfo = NativeCall(photoPath)
                    basicInfo?.let { basic ->
                        try {
                            val basicJson = JSONObject(basic)
                            if (basicJson.has("width")) {
                                exifMap["width"] = basicJson.getInt("width")
                            }
                            if (basicJson.has("height")) {
                                exifMap["height"] = basicJson.getInt("height")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "기본 정보 파싱 실패", e)
                        }
                    }

                    // 카메라 정보
                    exif.getAttribute(ExifInterface.TAG_MAKE)?.let { exifMap["make"] = it }
                    exif.getAttribute(ExifInterface.TAG_MODEL)?.let { exifMap["model"] = it }
                    exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { exifMap["f_number"] = it }
                    exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                        ?.let { exifMap["exposure_time"] = it }
                    exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                        ?.let { exifMap["focal_length"] = it }
                    exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                        ?.let { exifMap["iso"] = it }
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?.let { exifMap["date_time_original"] = it }

                    // GPS 정보
                    val latLong = FloatArray(2)
                    if (exif.getLatLong(latLong)) {
                        exifMap["gps_latitude"] = latLong[0]
                        exifMap["gps_longitude"] = latLong[1]
                    }

                    val jsonObject = JSONObject()
                    exifMap.forEach { (key, value) ->
                        jsonObject.put(key, value)
                    }

                    val exifJson = jsonObject.toString()
                    Log.d(TAG, "EXIF 파싱 완료: $exifJson")

                    _exifCache.value = _exifCache.value + (photoPath to exifJson)

                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "EXIF 파싱 실패", e)
            }
        }
    }

    /**
     * 이미지에서 썸네일 가져오기
     */
    fun getThumbnail(photoPath: String): ByteArray? {
        return _thumbnailCache.value[photoPath]
    }

    /**
     * 고해상도 이미지 가져오기
     */
    fun getFullImage(photoPath: String): ByteArray? {
        return _fullImageCache.value[photoPath]
    }

    /**
     * 다운로드 상태 확인
     */
    fun isDownloadingFullImage(photoPath: String): Boolean {
        return _downloadingImages.value.contains(photoPath)
    }

    /**
     * EXIF 정보 가져오기
     */
    fun getCameraPhotoExif(photoPath: String): String? {
        return _exifCache.value[photoPath]
    }

    /**
     * 유틸리티 메서드들
     */
    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rotateImageIfRequired(
        bitmap: android.graphics.Bitmap,
        orientation: Int
    ): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }

        return try {
            val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "이미지 회전 중 메모리 부족", e)
            bitmap
        }
    }

    private fun copyAllExifData(
        originalPath: String,
        newPath: String,
        newWidth: Int,
        newHeight: Int
    ) {
        try {
            val originalExif = ExifInterface(originalPath)
            val newExif = ExifInterface(newPath)

            val tagsToPreserve = arrayOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_ORIENTATION
            )

            var copiedCount = 0
            for (tag in tagsToPreserve) {
                val value = originalExif.getAttribute(tag)
                if (value != null) {
                    newExif.setAttribute(tag, value)
                    copiedCount++
                }
            }

            newExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, newWidth.toString())
            newExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, newHeight.toString())
            newExif.setAttribute(ExifInterface.TAG_SOFTWARE, "CamCon (Free Tier Resize)")

            newExif.saveAttributes()
            Log.d(TAG, "✅ EXIF 정보 복사 완료: ${copiedCount}개 태그 복사됨")

        } catch (e: Exception) {
            Log.e(TAG, "❌ EXIF 정보 복사 실패", e)
        }
    }

    /**
     * 정리
     */
    fun cleanup() {
        isManagerActive = false
        _thumbnailCache.value = emptyMap()
        _fullImageCache.value = emptyMap()
        _downloadingImages.value = emptySet()
        _exifCache.value = emptyMap()
    }
}