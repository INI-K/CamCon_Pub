package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.domain.model.CameraPhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 이미지 처리 관련 유틸리티 함수들
 * EXIF 정보 처리, 비트맵 디코딩, 회전 처리 등을 담당합니다.
 */
object ImageProcessingUtils {

    /**
     * ByteArray에서 EXIF 방향 정보를 고려하여 비트맵을 디코딩하는 함수
     */
    fun decodeBitmapWithExifRotation(
        imageData: ByteArray,
        photo: CameraPhoto? = null
    ): Bitmap? {
        return try {
            Log.d("ImageProcessing", "=== EXIF 디코딩 시작: ${photo?.name ?: "unknown"} ===")

            // 1. 기본 비트맵 디코딩
            val originalBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                ?: return null

            Log.d("ImageProcessing", "원본 비트맵 크기: ${originalBitmap.width}x${originalBitmap.height}")

            // 2. EXIF 방향 정보 읽기
            val orientation = getExifOrientation(imageData, photo)

            Log.d(
                "ImageProcessing",
                "최종 EXIF Orientation: $orientation (${photo?.name ?: "unknown"})"
            )

            // 3. 방향에 따른 회전 적용
            applyRotationFromExif(originalBitmap, orientation, photo?.name)

        } catch (e: Exception) {
            Log.e("ImageProcessing", "EXIF 회전 처리 완전 실패: ${photo?.name}", e)
            // EXIF 처리 실패 시 원본 디코딩 시도
            try {
                BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            } catch (ex: Exception) {
                Log.e("ImageProcessing", "❌ 비트맵 디코딩 완전 실패", ex)
                null
            }
        }
    }

    /**
     * 썸네일에 고화질 이미지의 EXIF 정보를 적용하는 함수
     */
    fun decodeThumbnailWithFullImageExif(
        thumbnailData: ByteArray,
        fullImageData: ByteArray,
        photo: CameraPhoto
    ): Bitmap? {
        // 고화질 이미지에서 EXIF 읽기
        val fullExif = try {
            val exif = ExifInterface(fullImageData.inputStream())
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            Log.e("ImageProcessing", "고화질 EXIF 읽기 실패: ${photo.name}", e)
            ExifInterface.ORIENTATION_NORMAL
        }

        // 썸네일 이미지 디코딩
        val thumbnailBitmap = decodeBitmapWithExifRotation(thumbnailData, photo)
            ?: return null

        // 고화질 이미지의 EXIF 정보를 썸네일에 적용
        return applyRotationFromExif(thumbnailBitmap, fullExif, photo.name)
    }

    /**
     * ImageView에 이미지 데이터를 로드하는 함수
     */
    fun loadImageIntoView(
        imageView: ImageView,
        photo: CameraPhoto,
        fullImageData: ByteArray?,
        thumbnailData: ByteArray?,
        bitmapCache: MutableMap<String, Bitmap>,
        imageViewRefs: MutableMap<String, ImageView>,
        highQualityUpdated: MutableSet<String>
    ) {
        // ImageView 참조 저장 (실시간 고화질 업데이트용)
        imageViewRefs[photo.path] = imageView

        // 이미지 데이터가 모두 없는 경우 즉시 플레이스홀더 표시
        if (fullImageData == null && thumbnailData == null) {
            Log.w("ImageProcessing", "⚠️ 이미지 데이터 없음 - 즉시 플레이스홀더 표시: ${photo.name}")
            setPlaceholderImage(imageView)
            return
        }

        // 이미 고화질이 적용된 경우 새로 로드하지 않음
        if (highQualityUpdated.contains(photo.path) && fullImageData != null) {
            Log.d("ImageProcessing", "⚡ 고화질 이미 적용됨 - 스킵: ${photo.name}")

            // 캐시된 고화질 비트맵이 있다면 다시 적용 (뷰 재생성 대응)
            val fullCacheKey = "${photo.path}_full"
            val cachedBitmap = bitmapCache[fullCacheKey]
            if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                imageView.setImageBitmap(cachedBitmap)
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                imageView.alpha = 1.0f
                Log.d("ImageProcessing", "💫 캐시된 고화질 재적용: ${photo.name}")
            }
            return
        }

        // 고화질 이미지가 있고 아직 처리되지 않은 경우 우선 처리
        if (fullImageData != null && !highQualityUpdated.contains(photo.path)) {
            Log.d("ImageProcessing", "🎯 고화질 이미지 우선 처리: ${photo.name}")

            // 즉시 썸네일로 시작 (빠른 반응성)
            if (thumbnailData != null) {
                loadThumbnailQuickly(imageView, photo, thumbnailData, bitmapCache)
            }

            // 백그라운드에서 고화질 이미지 우선 처리
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val fullCacheKey = "${photo.path}_full"
                    var fullBitmap = bitmapCache[fullCacheKey]

                    if (fullBitmap == null || fullBitmap.isRecycled) {
                        fullBitmap = decodeBitmapWithExifRotation(fullImageData, photo)
                        if (fullBitmap != null && !fullBitmap.isRecycled) {
                            bitmapCache[fullCacheKey] = fullBitmap
                        }
                    }

                    if (fullBitmap != null && !fullBitmap.isRecycled) {
                        CoroutineScope(Dispatchers.Main).launch {
                            // 현재 ImageView가 여전히 이 사진을 표시하고 있는지 확인
                            val currentImageView = imageViewRefs[photo.path]
                            if (currentImageView == imageView) {
                                imageView.setImageBitmap(fullBitmap)
                                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                                imageView.alpha = 1.0f
                                highQualityUpdated.add(photo.path)
                                Log.d("ImageProcessing", "✅ 고화질 이미지 표시 완료: ${photo.name}")
                            } else {
                                Log.d("ImageProcessing", "ImageView 변경됨 - 고화질 적용 취소: ${photo.name}")
                            }
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("ImageProcessing", "고화질 이미지 처리 실패: ${photo.name}", e)
                }

                // 고화질 실패 시 썸네일로 fallback (이미 표시되어 있을 수 있음)
                if (thumbnailData == null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        setPlaceholderImage(imageView)
                    }
                }
            }
            return
        }

        // 고화질이 없거나 이미 처리된 경우 썸네일 처리
        loadThumbnailFallback(imageView, photo, thumbnailData, bitmapCache)
    }

    /**
     * 썸네일을 빠르게 로드하는 함수 (고화질 로딩 전 임시 표시용)
     */
    private fun loadThumbnailQuickly(
        imageView: ImageView,
        photo: CameraPhoto,
        thumbnailData: ByteArray,
        bitmapCache: MutableMap<String, Bitmap>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val thumbnailCacheKey = "${photo.path}_thumbnail"
                var thumbnailBitmap = bitmapCache[thumbnailCacheKey]

                if (thumbnailBitmap == null || thumbnailBitmap.isRecycled) {
                    thumbnailBitmap = decodeBitmapWithExifRotation(thumbnailData, photo)
                    if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                        bitmapCache[thumbnailCacheKey] = thumbnailBitmap
                    }
                }

                if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                    CoroutineScope(Dispatchers.Main).launch {
                        imageView.setImageBitmap(thumbnailBitmap)
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        imageView.alpha = 0.8f // 고화질 로딩 중임을 표시
                        Log.d("ImageProcessing", "⚡ 썸네일 빠른 표시: ${photo.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageProcessing", "썸네일 빠른 로딩 실패: ${photo.name}", e)
            }
        }
    }

    /**
     * 썸네일 fallback 처리
     */
    private fun loadThumbnailFallback(
        imageView: ImageView,
        photo: CameraPhoto,
        thumbnailData: ByteArray?,
        bitmapCache: MutableMap<String, Bitmap>
    ) {
        if (thumbnailData == null) {
            Log.w("ImageProcessing", "⚠️ 썸네일 데이터 없음 - 플레이스홀더 표시: ${photo.name}")
            setPlaceholderImage(imageView)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val thumbnailCacheKey = "${photo.path}_thumbnail"
                var thumbnailBitmap = bitmapCache[thumbnailCacheKey]

                if (thumbnailBitmap == null || thumbnailBitmap.isRecycled) {
                    thumbnailBitmap = decodeBitmapWithExifRotation(thumbnailData, photo)
                    if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                        bitmapCache[thumbnailCacheKey] = thumbnailBitmap
                    }
                }

                if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                    CoroutineScope(Dispatchers.Main).launch {
                        imageView.setImageBitmap(thumbnailBitmap)
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        imageView.alpha = 1.0f
                        Log.d("ImageProcessing", "✅ 썸네일 표시 완료: ${photo.name}")
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        Log.w("ImageProcessing", "⚠️ 썸네일 처리 실패 - 플레이스홀더 표시: ${photo.name}")
                        setPlaceholderImage(imageView)
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageProcessing", "❌ 썸네일 로딩 에러: ${photo.name}", e)
                CoroutineScope(Dispatchers.Main).launch {
                    setPlaceholderImage(imageView)
                }
            }
        }
    }

    /**
     * EXIF 방향 정보를 읽는 함수
     */
    private fun getExifOrientation(imageData: ByteArray, photo: CameraPhoto?): Int {
        return try {
            // 원본 파일이 있고 존재하는 경우 파일에서 직접 읽기
            if (!photo?.path.isNullOrEmpty() && File(photo?.path ?: "").exists()) {
                Log.d("ImageProcessing", "원본 파일에서 EXIF 읽기 시도: ${photo!!.path}")
                val exif = ExifInterface(photo.path)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                Log.d("ImageProcessing", "파일 EXIF 읽기 성공: orientation = $orientation")
                orientation
            } else {
                Log.d("ImageProcessing", "바이트 스트림에서 EXIF 읽기 시도")
                // 원본 파일이 없으면 바이트 스트림에서 읽기
                val exif = ExifInterface(imageData.inputStream())
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                Log.d("ImageProcessing", "바이트 스트림 EXIF 읽기 성공: orientation = $orientation")
                orientation
            }
        } catch (e: Exception) {
            Log.e("ImageProcessing", "EXIF 읽기 실패: ${e.message}", e)
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * EXIF 방향 정보에 따른 비트맵 회전 적용 (수정된 회전 로직)
     */
    private fun applyRotationFromExif(
        originalBitmap: Bitmap,
        orientation: Int,
        photoName: String?
    ): Bitmap {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                Log.d("ImageProcessing", "90도 회전 수정 적용: $photoName (270도로 변경)")
                // 90도가 반대로 나오면 270도로 변경
                val matrix = Matrix()
                matrix.postRotate(270f) // 90도 대신 270도 적용
                Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )
            }

            ExifInterface.ORIENTATION_ROTATE_180 -> {
                Log.d("ImageProcessing", "180도 회전 수정 적용: $photoName (이전에 거꾸로 표시되던 문제 해결)")
                // 180도 회전이 거꾸로 되어있었다면, 회전하지 않거나 반대로 회전
                originalBitmap // 일단 회전하지 않고 테스트
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                Log.d("ImageProcessing", "270도 회전 수정 적용: $photoName (90도로 변경)")
                // 270도가 잘못 표시되면 90도로 변경하여 테스트
                val matrix = Matrix()
                matrix.postRotate(90f) // 270도 대신 90도 적용
                Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )
            }

            else -> {
                Log.d("ImageProcessing", "회전 없음: $photoName (orientation: $orientation)")
                originalBitmap
            }
        }
    }

    /**
     * 플레이스홀더 이미지 설정
     */
    fun setPlaceholderImage(imageView: ImageView) {
        try {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
            imageView.scaleType = ImageView.ScaleType.CENTER
            imageView.alpha = 0.5f
            Log.d("ImageProcessing", "플레이스홀더 이미지 설정 완료")
        } catch (e: Exception) {
            Log.e("ImageProcessing", "플레이스홀더 설정 오류", e)
            // 오류 발생 시 최소한의 설정
            imageView.setImageDrawable(null)
            imageView.setBackgroundColor(0xFF444444.toInt()) // 회색 배경
        }
    }

    /**
     * 고화질 이미지를 ImageView에 로드하는 단순화된 함수
     */
    fun loadFullImageIntoView(
        imageView: ImageView,
        photo: CameraPhoto,
        fullImageData: ByteArray
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = decodeBitmapWithExifRotation(fullImageData, photo)

                if (bitmap != null && !bitmap.isRecycled) {
                    CoroutineScope(Dispatchers.Main).launch {
                        imageView.setImageBitmap(bitmap)
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        imageView.alpha = 1.0f
                        Log.d("ImageProcessing", "✅ 고화질 이미지 로드 완료: ${photo.name}")
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        setPlaceholderImage(imageView)
                        Log.w("ImageProcessing", "고화질 비트맵 생성 실패: ${photo.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageProcessing", "고화질 이미지 로드 실패: ${photo.name}", e)
                CoroutineScope(Dispatchers.Main).launch {
                    setPlaceholderImage(imageView)
                }
            }
        }
    }

    /**
     * 썸네일 이미지를 ImageView에 로드하는 단순화된 함수
     */
    fun loadThumbnailIntoView(
        imageView: ImageView,
        photo: CameraPhoto,
        thumbnailData: ByteArray
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = decodeBitmapWithExifRotation(thumbnailData, photo)

                if (bitmap != null && !bitmap.isRecycled) {
                    CoroutineScope(Dispatchers.Main).launch {
                        imageView.setImageBitmap(bitmap)
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        imageView.alpha = 1.0f
                        Log.d("ImageProcessing", "✅ 썸네일 이미지 로드 완료: ${photo.name}")
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        setPlaceholderImage(imageView)
                        Log.w("ImageProcessing", "썸네일 비트맵 생성 실패: ${photo.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageProcessing", "썸네일 이미지 로드 실패: ${photo.name}", e)
                CoroutineScope(Dispatchers.Main).launch {
                    setPlaceholderImage(imageView)
                }
            }
        }
    }

    /**
     * 인접 사진 미리 로드 함수
     */
    fun preloadAdjacentPhotosMinimal(
        currentPosition: Int,
        photos: List<CameraPhoto>,
        fullImageCache: Map<String, ByteArray>,
        viewModel: com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel?,
        loadingPhotos: MutableSet<String>
    ) {
        val preloadRange = 1 // 앞뒤 1장씩만 미리 로드

        // 현재 사진의 바로 앞뒤 사진만 체크
        val indicesToPreload = listOf(currentPosition - 1, currentPosition + 1)
            .filter { it in photos.indices }

        for (index in indicesToPreload) {
            val adjacentPhoto = photos[index]

            if (fullImageCache[adjacentPhoto.path] == null && !loadingPhotos.contains(adjacentPhoto.path)) {
                loadingPhotos.add(adjacentPhoto.path)
                viewModel?.downloadPhoto(adjacentPhoto)
            }
        }
    }
}