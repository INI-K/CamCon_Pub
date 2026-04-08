package com.inik.camcon.presentation.viewmodel.photo

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.camera.GetCameraThumbnailUseCase
import com.inik.camcon.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.inik.camcon.utils.Constants
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
    private val getCameraThumbnailUseCase: GetCameraThumbnailUseCase,
    @ApplicationScope private val appScope: CoroutineScope
) {

    companion object {
        private const val TAG = "사진이미지매니저"
    }

    // 앱 scope의 자식 scope — cancelChildren해도 앱 scope에 영향 없음
    private var managerScope = createManagerScope()

    private fun createManagerScope(): CoroutineScope =
        CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext.job))

    // 썸네일 캐시
    private val _thumbnailCache = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val thumbnailCache: StateFlow<Map<String, ByteArray>> = _thumbnailCache.asStateFlow()

    // 고해상도 이미지 캐시
    private val _fullImageCache = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val fullImageCache: StateFlow<Map<String, ByteArray>> = _fullImageCache.asStateFlow()

    // 다운로드 상태 관리
    private val _downloadingImages = MutableStateFlow<Set<String>>(emptySet())
    val downloadingImages: StateFlow<Set<String>> = _downloadingImages.asStateFlow()

    // 썸네일 로딩 상태 관리 (중복 방지용)
    private val _loadingThumbnails = MutableStateFlow<Set<String>>(emptySet())
    val loadingThumbnails: StateFlow<Set<String>> = _loadingThumbnails.asStateFlow()

    // EXIF 정보 캐시
    private val _exifCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val exifCache: StateFlow<Map<String, String>> = _exifCache.asStateFlow()

    // 작업 취소를 위한 플래그
    private var isManagerActive = true

    /**
     * 썸네일 로드
     */
    fun loadThumbnailsForPhotos(photos: List<CameraPhoto>) {
        val stackTrace = Thread.currentThread().stackTrace
            .take(5)
            .joinToString("\n") { "    at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }

        Log.d(TAG, "🔍 loadThumbnailsForPhotos 호출됨!")
        Log.d(TAG, "🔍 호출 스택:\n$stackTrace")
        Log.d(TAG, "=== 썸네일 로딩 시작: ${photos.size}개 사진 ===")

        managerScope.launch {
            if (!isManagerActive) {
                Log.d(TAG, "⛔ 썸네일 로딩 중단됨 (매니저 비활성)")
                return@launch
            }

            val currentCache = _thumbnailCache.value.toMutableMap()
            val currentlyLoading = _loadingThumbnails.value.toMutableSet()

            // 순차적으로 처리 (동시 실행 방지)
            photos.forEach { photo ->
                // 이미 캐시에 있거나 로딩 중인 경우 건너뛰기
                if (currentCache.containsKey(photo.path) || currentlyLoading.contains(photo.path)) {
                    if (currentCache.containsKey(photo.path)) {
                        Log.d(TAG, "♻️ 이미 캐시에 있음: ${photo.name}")
                    } else {
                        Log.d(TAG, "⏳ 이미 로딩 중: ${photo.name}")
                    }
                    return@forEach
                }

                // 매니저 비활성화 체크
                if (!isManagerActive) {
                    Log.d(TAG, "⛔ 썸네일 로딩 중단됨 (매니저 비활성)")
                    return@launch
                }

                // 로딩 상태에 추가
                currentlyLoading.add(photo.path)
                _loadingThumbnails.value = currentlyLoading.toSet()

                try {
                    Log.d(TAG, "📷 썸네일 로드 시작: ${photo.name}")
                    Log.d(TAG, "   - 경로: ${photo.path}")
                    Log.d(TAG, "   - 파일크기: ${photo.size} bytes")

                    // 네이티브 썸네일 호출
                    getCameraThumbnailUseCase(photo.path).fold(
                        onSuccess = { thumbnailData ->
                            if (!isManagerActive) {
                                Log.d(TAG, "매니저 비활성화됨, 결과 무시: ${photo.name}")
                                return@fold
                            }

                            Log.d(TAG, "✅ 썸네일 데이터 받음: ${photo.name}")
                            Log.d(TAG, "   - 썸네일 크기: ${thumbnailData.size} bytes")
                            Log.d(TAG, "   - 썸네일 비어있음: ${thumbnailData.isEmpty()}")

                            if (thumbnailData.isNotEmpty()) {
                                // 썸네일 데이터의 헤더 확인 (JPEG인지 등)
                                val header = thumbnailData.take(8).map { "%02X".format(it) }
                                    .joinToString(" ")
                                Log.d(TAG, "   - 썸네일 헤더: $header")

                                // JPEG 헤더 확인 (FF D8 FF로 시작해야 함)
                                if (thumbnailData.size >= 3 &&
                                    thumbnailData[0] == 0xFF.toByte() &&
                                    thumbnailData[1] == 0xD8.toByte() &&
                                    thumbnailData[2] == 0xFF.toByte()
                                ) {
                                    Log.d(TAG, "   - 유효한 JPEG 썸네일 확인됨")
                                } else {
                                    Log.w(TAG, "   - 비정상적인 썸네일 헤더 감지됨")
                                }

                                synchronized(currentCache) {
                                    // LRU 캐시 크기 제한 적용
                                    if (currentCache.size >= Constants.Cache.MAX_THUMBNAIL_CACHE_SIZE) {
                                        val oldestKey = currentCache.keys.firstOrNull()
                                        if (oldestKey != null) {
                                            currentCache.remove(oldestKey)
                                            Log.d(TAG, "캐시 크기 제한 - 가장 오래된 썸네일 제거: $oldestKey")
                                        }
                                    }
                                    currentCache[photo.path] = thumbnailData
                                    _thumbnailCache.value = currentCache.toMap()
                                }
                                Log.d(TAG, "💾 썸네일 캐시 저장 완료: ${photo.name} (캐시 크기: ${currentCache.size})")
                            } else {
                                Log.w(TAG, "⚠️ 빈 썸네일 데이터 수신: ${photo.name}")
                                // 빈 데이터도 캐시에 저장하여 재시도 방지
                                synchronized(currentCache) {
                                    currentCache[photo.path] = ByteArray(0)
                                    _thumbnailCache.value = currentCache.toMap()
                                }
                            }
                        },
                        onFailure = { exception ->
                            if (!isManagerActive) {
                                Log.d(TAG, "매니저 비활성화됨, 에러 무시: ${photo.name}")
                                return@fold
                            }

                            val errorMessage = exception.message ?: "알 수 없는 오류"
                            Log.e(TAG, "❌ 썸네일 로드 실패: ${photo.path}")
                            Log.d(TAG, "   - 에러 메시지: $errorMessage")
                            Log.d(TAG, "   - 에러 타입: ${exception.javaClass.simpleName}")

                            // 카메라 사용 중 오류에 대해 더 관대하게 처리
                            val isCameraBusyError =
                                errorMessage.contains("사용 중", ignoreCase = true) ||
                                        errorMessage.contains("초기화 중", ignoreCase = true) ||
                                        errorMessage.contains("카메라가 연결되지 않음", ignoreCase = true)

                            if (isCameraBusyError) {
                                Log.w(TAG, "⏳ 카메라 사용 중/초기화 중 오류 - 캐시에 저장하지 않고 나중에 재시도 허용")
                                // 카메라 사용 중인 경우 캐시에 저장하지 않음 (자연스러운 재시도 허용)
                                return@fold
                            }

                            // 특정 에러 타입에 따른 처리
                            when {
                                errorMessage.contains(
                                    "camera not initialized",
                                    ignoreCase = true
                                ) -> {
                                    Log.e(TAG, "   - 카메라 초기화 문제")
                                }

                                errorMessage.contains("file not found", ignoreCase = true) -> {
                                    Log.e(TAG, "   - 파일을 찾을 수 없음")
                                }

                                errorMessage.contains("timeout", ignoreCase = true) -> {
                                    Log.e(TAG, "   - 타임아웃 발생")
                                }

                                else -> {
                                    Log.e(TAG, "   - 기타 오류: $errorMessage")
                                }
                            }

                            // 일반적인 실패한 경우만 빈 데이터로 캐시하여 재시도 방지
                            synchronized(currentCache) {
                                currentCache[photo.path] = ByteArray(0)
                                _thumbnailCache.value = currentCache.toMap()
                            }
                        }
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(TAG, "💥 썸네일 로딩 중 예외: ${photo.name}", exception)
                    if (isManagerActive) {
                        synchronized(currentCache) {
                            currentCache[photo.path] = ByteArray(0)
                            _thumbnailCache.value = currentCache.toMap()
                        }
                    }
                } finally {
                    // 로딩 상태에서 제거
                    currentlyLoading.remove(photo.path)
                    _loadingThumbnails.value = currentlyLoading.toSet()
                    Log.d(TAG, "🔄 썸네일 로딩 완료 처리: ${photo.name}")
                }
            }

            Log.d(TAG, "=== 썸네일 로딩 완료: ${photos.size}개 사진 ===")
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

        managerScope.launch {
            try {
                Log.d(TAG, "실제 파일 다운로드 시작: $photoPath")

                val imageData = withContext(Dispatchers.IO) {
                    Log.d(TAG, "downloadCameraPhoto 호출")
                    CameraNative.downloadCameraPhoto(photoPath)
                }

                if (imageData != null && imageData.isNotEmpty()) {
                    Log.d(TAG, "이미지 데이터 확인: 유효함 (${imageData.size} bytes)")

                    val currentCache = _fullImageCache.value.toMutableMap()
                    if (!currentCache.containsKey(photoPath)) {
                        // LRU 캐시 크기 제한 적용
                        while (currentCache.size >= Constants.Cache.MAX_FULL_IMAGE_CACHE_SIZE) {
                            val oldestKey = currentCache.keys.firstOrNull()
                            if (oldestKey != null) {
                                currentCache.remove(oldestKey)
                                Log.d(TAG, "캐시 크기 제한 - 가장 오래된 이미지 제거: $oldestKey")
                            } else {
                                break
                            }
                        }
                        currentCache[photoPath] = imageData
                        _fullImageCache.value = currentCache.toMap()

                        Log.d(TAG, "실제 파일 다운로드 성공: ${imageData.size} bytes (캐시 크기: ${currentCache.size})")

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
            } catch (e: CancellationException) {
                throw e
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

            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
        managerScope.launch {
            try {
                Log.d(TAG, "EXIF 파싱 시작: $photoPath")

                val tempFile = File.createTempFile("temp_exif", ".jpg")
                tempFile.writeBytes(imageData)

                try {
                    val exif = ExifInterface(tempFile.absolutePath)
                    val exifMap = mutableMapOf<String, Any>()

                    // 기본 정보
                    val basicInfo = CameraNative.getCameraPhotoExif(photoPath)
                    basicInfo?.let { basic ->
                        try {
                            val basicJson = JSONObject(basic)
                            if (basicJson.has("width")) {
                                exifMap["width"] = basicJson.getInt("width")
                            }
                            if (basicJson.has("height")) {
                                exifMap["height"] = basicJson.getInt("height")
                            }else{

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
            } catch (e: CancellationException) {
                throw e
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
        managerScope.coroutineContext.job.cancel()
        managerScope = createManagerScope()
        isManagerActive = false
        _thumbnailCache.value = emptyMap()
        _fullImageCache.value = emptyMap()
        _downloadingImages.value = emptySet()
        _loadingThumbnails.value = emptySet()
        _exifCache.value = emptyMap()
    }
}