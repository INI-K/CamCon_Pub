package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.utils.LogMask
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
                Log.e("ImageProcessing", "비트맵 디코딩 완전 실패", ex)
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
     * EXIF 방향 정보를 읽는 함수
     */
    private fun getExifOrientation(imageData: ByteArray, photo: CameraPhoto?): Int {
        return try {
            // 원본 파일이 있고 존재하는 경우 파일에서 직접 읽기
            if (!photo?.path.isNullOrEmpty() && File(photo?.path ?: "").exists()) {
                Log.d("ImageProcessing", "원본 파일에서 EXIF 읽기 시도: ${LogMask.path(photo!!.path)}")
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
     * 파일 경로의 EXIF orientation 을 [bitmap] 에 적용해 올바로 세운 비트맵을 돌려준다.
     * 디코딩 후 비트맵에 회전을 입히는 단일 진입점([applyRotationFromExif] 재사용)으로,
     * 필름 에디터 프리뷰/썸네일 소스가 세로 사진을 눕히지 않도록 한다.
     * 회전이 일어나면 입력 비트맵은 recycle 되고 새 비트맵을 반환한다(회전 없으면 입력 그대로).
     */
    fun applyExifOrientationFromFile(bitmap: Bitmap, filePath: String): Bitmap {
        val orientation = try {
            ExifInterface(filePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            Log.w("ImageProcessing", "파일 EXIF orientation 읽기 실패: $filePath", e)
            ExifInterface.ORIENTATION_NORMAL
        }
        return applyRotationFromExif(bitmap, orientation, filePath)
    }

    /**
     * EXIF 방향 정보에 따른 비트맵 회전 적용 (표준 매핑).
     * PhotoDownloadManager.rotateImageIfRequired 와 동일한 규칙으로 통일(F30):
     * 90→90f, 180→180f, 270→270f. 90/270 회전 시 width/height 를 swap 한다.
     */
    private fun applyRotationFromExif(
        originalBitmap: Bitmap,
        orientation: Int,
        photoName: String?
    ): Bitmap {
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> {
                Log.d("ImageProcessing", "회전 없음: $photoName (orientation: $orientation)")
                return originalBitmap
            }
        }

        Log.d("ImageProcessing", "EXIF ${rotationDegrees.toInt()}도 회전 적용: $photoName")

        return try {
            // 90/270 회전 시 회전 중심을 중앙으로 두고 회전하면 createBitmap이 swap된 차원으로 생성한다.
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees, originalBitmap.width / 2f, originalBitmap.height / 2f)
            val rotated = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
            if (rotated != originalBitmap) {
                originalBitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            Log.e("ImageProcessing", "EXIF 회전 실패: $photoName", e)
            originalBitmap
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
            imageView.setBackgroundColor(Surface3.toArgb()) // 플레이스홀더 폴백 배경 (CINE Surface3 토큰)
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