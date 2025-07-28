package com.inik.camcon.presentation.ui.screens.components

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inik.camcon.domain.model.CameraPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

/**
 * 사진 썸네일을 표시하는 카드 컴포넌트
 */
@Composable
fun PhotoThumbnail(
    photo: CameraPhoto,
    onClick: () -> Unit,
    thumbnailData: ByteArray? = null,
    fullImageCache: Map<String, ByteArray> = emptyMap()
) {
    // remember를 사용하여 photo.path가 변경될 때만 로그 출력 (중복 방지)
    val loggedPath = remember(photo.path) {
        Log.d("PhotoThumbnail", "=== 썸네일 어댑터 처리 시작: ${photo.name} ===")
        Log.d("PhotoThumbnail", "photo.path: ${photo.path}")
        Log.d("PhotoThumbnail", "thumbnailData size: ${thumbnailData?.size ?: 0} bytes")
        Log.d(
            "PhotoThumbnail",
            "fullImageData size: ${fullImageCache[photo.path]?.size ?: 0} bytes"
        )
        Log.d(
            "PhotoThumbnail",
            "파일 존재 여부: ${!photo.path.isNullOrEmpty() && File(photo.path).exists()}"
        )
        photo.path
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        elevation = 6.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Box {
            // 이미지 로딩 우선순위: 썸네일 경로 -> 원본 경로 -> 바이트 데이터 -> 플레이스홀더
            when {
                !photo.thumbnailPath.isNullOrEmpty() && File(photo.thumbnailPath).exists() -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo.thumbnailPath)
                            .crossfade(true)
                            .allowHardware(false) // EXIF 처리를 위해 하드웨어 가속 비활성화
                            .build(),
                        contentDescription = photo.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                !photo.path.isNullOrEmpty() && File(photo.path).exists() -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo.path)
                            .crossfade(true)
                            .allowHardware(false) // EXIF 처리를 위해 하드웨어 가속 비활성화
                            .build(),
                        contentDescription = photo.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                thumbnailData != null -> {
                    val fullImageData = fullImageCache[photo.path]
                    if (fullImageData != null) {
                        ExifAwareThumbnail(
                            thumbnailData = thumbnailData,
                            fullImageData = fullImageData,
                            photo = photo,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // 고화질 이미지가 없을 때는 기본 썸네일만 표시 (중복 처리 방지)
                        ThumbnailImage(
                            thumbnailData = thumbnailData,
                            photo = photo,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                else -> {
                    ThumbnailPlaceholder()
                }
            }

            // 하단 파일명 표시
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = photo.name,
                    color = Color.White,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp
                )
            }

            // 우상단 파일 크기 표시
            if (photo.size > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(bottomStart = 8.dp, topEnd = 12.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatFileSize(photo.size),
                        color = Color.White,
                        style = MaterialTheme.typography.caption,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

/**
 * 고화질 데이터의 EXIF 정보를 사용하여 썸네일에 올바른 회전을 적용하는 컴포넌트
 */
@Composable
private fun ExifAwareThumbnail(
    thumbnailData: ByteArray,
    fullImageData: ByteArray,
    photo: CameraPhoto,
    modifier: Modifier = Modifier
) {
    var rotatedBitmap by remember(photo.path, thumbnailData, fullImageData) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(photo.path, thumbnailData, fullImageData) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("PhotoThumbnail", "비트맵 디코딩 시작: ${photo.name}")
                Log.d("PhotoThumbnail", "고화질 데이터에서 EXIF 읽어서 썸네일에 적용: ${photo.name}")

                // 1. 고화질 이미지에서 EXIF 정보 읽기 (한 번만)
                val fullExif = try {
                    Log.d("PhotoThumbnail", "=== EXIF 디코딩 시작: ${photo.name} ===")
                    Log.d("PhotoThumbnail", "imageData size: ${fullImageData.size} bytes")
                    Log.d("PhotoThumbnail", "photo.path: ${photo.path}")

                    val exif = androidx.exifinterface.media.ExifInterface(
                        ByteArrayInputStream(fullImageData)
                    )
                    val orientation = exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )

                    Log.d("PhotoThumbnail", "원본 비트맵 크기: reading from fullImage")
                    Log.d("PhotoThumbnail", "바이트 스트림에서 EXIF 읽기 시도")
                    Log.d("PhotoThumbnail", "파일 존재 여부: false")
                    Log.d("PhotoThumbnail", "바이트 스트림 EXIF 읽기 성공: orientation = $orientation")
                    Log.d("PhotoThumbnail", "최종 EXIF Orientation: $orientation (${photo.name})")

                    orientation
                } catch (e: Exception) {
                    Log.e("PhotoThumbnail", "고화질 EXIF 읽기 실패: ${photo.name}", e)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                }

                // 2. 썸네일 비트맵 디코딩
                val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(
                    thumbnailData, 0, thumbnailData.size
                )

                if (originalBitmap != null) {
                    Log.d(
                        "PhotoThumbnail",
                        "원본 비트맵 크기: ${originalBitmap.width}x${originalBitmap.height}"
                    )

                    // 3. 고화질 이미지의 EXIF 정보를 썸네일에 적용
                    val rotatedBmp = when (fullExif) {
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> {
                            Log.d("PhotoThumbnail", "90도 회전 적용: ${photo.name}")
                            val matrix = android.graphics.Matrix()
                            matrix.postRotate(90f)
                            android.graphics.Bitmap.createBitmap(
                                originalBitmap, 0, 0,
                                originalBitmap.width, originalBitmap.height,
                                matrix, true
                            )
                        }

                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> {
                            Log.d("PhotoThumbnail", "180도 회전 적용: ${photo.name}")
                            val matrix = android.graphics.Matrix()
                            matrix.postRotate(180f)
                            android.graphics.Bitmap.createBitmap(
                                originalBitmap, 0, 0,
                                originalBitmap.width, originalBitmap.height,
                                matrix, true
                            )
                        }

                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> {
                            Log.d("PhotoThumbnail", "270도 회전 적용: ${photo.name}")
                            val matrix = android.graphics.Matrix()
                            matrix.postRotate(270f)
                            android.graphics.Bitmap.createBitmap(
                                originalBitmap, 0, 0,
                                originalBitmap.width, originalBitmap.height,
                                matrix, true
                            )
                        }

                        else -> {
                            Log.d("PhotoThumbnail", "회전 없음: ${photo.name} (orientation: $fullExif)")
                            originalBitmap
                        }
                    }

                    rotatedBitmap = rotatedBmp.asImageBitmap()
                    Log.d("PhotoThumbnail", "비트맵 디코딩 완료: ${photo.name}, bitmap: true")
                    Log.d(
                        "PhotoThumbnail",
                        "썸네일 비트맵 적용 성공: ${photo.name} (${rotatedBmp.width}x${rotatedBmp.height})"
                    )
                } else {
                    Log.e("PhotoThumbnail", "썸네일 비트맵 디코딩 실패: ${photo.name}")
                }
            } catch (e: Exception) {
                Log.e("PhotoThumbnail", "EXIF 썸네일 처리 실패: ${photo.name}", e)
            }
        }
    }

    // 회전된 비트맵 표시
    rotatedBitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = photo.name,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } ?: run {
        // 로딩 중이거나 실패 시 플레이스홀더
        ThumbnailPlaceholder()
    }
}

/**
 * 기본 썸네일 이미지 표시 컴포넌트 (EXIF 정보 없이)
 */
@Composable
private fun ThumbnailImage(
    thumbnailData: ByteArray,
    photo: CameraPhoto,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(photo.path, thumbnailData) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(photo.path, thumbnailData) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("PhotoThumbnail", "비트맵 디코딩 시작: ${photo.name}")
                Log.d("PhotoThumbnail", "고화질 데이터 없음, 기본 썸네일 디코딩: ${photo.name}")

                val decodedBitmap = android.graphics.BitmapFactory.decodeByteArray(
                    thumbnailData, 0, thumbnailData.size
                )

                if (decodedBitmap != null) {
                    bitmap = decodedBitmap.asImageBitmap()
                    Log.d(
                        "PhotoThumbnail",
                        "썸네일 비트맵 적용 성공: ${photo.name} (${decodedBitmap.width}x${decodedBitmap.height})"
                    )
                } else {
                    Log.e("PhotoThumbnail", "썸네일 비트맵 디코딩 실패: ${photo.name}")
                }
            } catch (e: Exception) {
                Log.e("PhotoThumbnail", "썸네일 이미지 처리 실패: ${photo.name}", e)
            }
        }
    }

    bitmap?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = photo.name,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } ?: ThumbnailPlaceholder()
}

/**
 * 이미지를 불러올 수 없을 때 표시되는 플레이스홀더
 */
@Composable
private fun ThumbnailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "이미지 없음",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                fontSize = 9.sp
            )
        }
    }
}

/**
 * 파일 크기를 사람이 읽기 쉬운 형태로 포맷
 */
private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }
}

