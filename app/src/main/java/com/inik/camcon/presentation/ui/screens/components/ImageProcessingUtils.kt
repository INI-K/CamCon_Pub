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

        // 백그라운드에서 이미지 디코딩 처리
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var selectedBitmap: Bitmap? = null
                var isHighQuality = false

                // 1. 고화질 이미지가 있으면 우선 처리
                if (fullImageData != null) {
                    val fullCacheKey = "${photo.path}_full"
                    var fullBitmap = bitmapCache[fullCacheKey]

                    if (fullBitmap == null) {
                        fullBitmap = decodeBitmapWithExifRotation(fullImageData, photo)
                        if (fullBitmap != null && !fullBitmap.isRecycled) {
                            bitmapCache[fullCacheKey] = fullBitmap
                        }
                    }

                    if (fullBitmap != null && !fullBitmap.isRecycled) {
                        selectedBitmap = fullBitmap
                        isHighQuality = true
                        Log.d("ImageProcessing", "🖼️ 고화질 이미지 준비 완료 (회전 적용): ${photo.name}")
                    }
                }

                // 2. 고화질이 없거나 실패했으면 썸네일 처리
                if (selectedBitmap == null && thumbnailData != null) {
                    val thumbnailCacheKey = "${photo.path}_thumbnail"
                    var thumbnailBitmap = bitmapCache[thumbnailCacheKey]

                    if (thumbnailBitmap == null) {
                        thumbnailBitmap = decodeBitmapWithExifRotation(thumbnailData, photo)
                        if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                            bitmapCache[thumbnailCacheKey] = thumbnailBitmap
                        }
                    }

                    if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                        selectedBitmap = thumbnailBitmap
                        Log.d("ImageProcessing", "📱 썸네일 준비 완료 (회전 적용): ${photo.name}")
                    }
                }

                // 3. 메인 스레드에서 UI 업데이트 
                CoroutineScope(Dispatchers.Main).launch {
                    if (selectedBitmap != null && !selectedBitmap.isRecycled) {
                        imageView.setImageBitmap(selectedBitmap)
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        imageView.alpha = 1.0f // 정상 이미지는 완전 불투명

                        if (isHighQuality) {
                            highQualityUpdated.add(photo.path)
                            Log.d("ImageProcessing", "✅ 고화질 이미지 표시 완료: ${photo.name}")
                        } else {
                            Log.d("ImageProcessing", "✅ 썸네일 표시 완료: ${photo.name}")
                        }
                    } else {
                        // 모든 이미지 처리 실패 시 플레이스홀더 설정
                        Log.w("ImageProcessing", "⚠️ 모든 이미지 처리 실패, 플레이스홀더 표시: ${photo.name}")
                        setPlaceholderImage(imageView)
                    }
                }

            } catch (e: Exception) {
                Log.e("ImageProcessing", "❌ 이미지 로딩 에러: ${photo.name}", e)
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
     * EXIF 방향 정보에 따른 비트맵 회전 적용
     */
    private fun applyRotationFromExif(
        originalBitmap: Bitmap,
        orientation: Int,
        photoName: String?
    ): Bitmap {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                Log.d("ImageProcessing", "90도 회전 적용: $photoName")
                val matrix = Matrix()
                matrix.postRotate(90f)
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
                Log.d("ImageProcessing", "180도 회전 적용: $photoName")
                val matrix = Matrix()
                matrix.postRotate(180f)
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

            ExifInterface.ORIENTATION_ROTATE_270 -> {
                Log.d("ImageProcessing", "270도 회전 적용: $photoName")
                val matrix = Matrix()
                matrix.postRotate(270f)
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
                viewModel?.downloadFullImage(adjacentPhoto.path)
            }
        }
    }
}