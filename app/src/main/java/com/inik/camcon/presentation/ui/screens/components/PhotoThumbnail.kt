package com.inik.camcon.presentation.ui.screens.components

// Import for FluidPhotoThumbnail and helpers

import android.graphics.ColorSpace
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoThumbnail(
    photo: CameraPhoto,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    thumbnailData: ByteArray? = null,
    fullImageCache: Map<String, ByteArray> = emptyMap(),
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false
) {
    // remember를 사용하여 photo.path가 변경될 때만 로그 출력 (중복 방지)
    remember(photo.path) {
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = 6.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Box {
            // 선택된 상태일 때 오버레이 추가
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Blue.copy(alpha = 0.3f))
                )
            }

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
                        contentScale = ContentScale.Crop
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
                        contentScale = ContentScale.Crop
                    )
                }

                thumbnailData != null && thumbnailData.isNotEmpty() -> {
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
                    // 네이티브 썸네일 데이터가 없거나 비어있는 경우
                    Log.w("PhotoThumbnail", "썸네일 데이터 없음: ${photo.name}")
                    ThumbnailPlaceholder()
                }
            }

            // 멀티 선택 모드에서 선택 상태 표시
            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            color = if (isSelected) Color.Green else Color.White.copy(alpha = 0.8f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "선택됨",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        // 선택되지 않은 상태에서도 빈 원 표시
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    color = Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
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

                // 1. 썸네일 데이터 유효성 검사
                if (thumbnailData.isEmpty()) {
                    Log.w("PhotoThumbnail", "썸네일 데이터가 비어있음: ${photo.name}")
                    return@withContext
                }

                // 2. 고화질 이미지에서 EXIF 정보 읽기 (한 번만)
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

                // 3. 안전한 비트맵 디코딩 옵션 설정
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 // sRGB 호환성
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB) // sRGB 색공간
                    inMutable = false
                    inSampleSize = 1 // 썸네일이므로 원본 크기 유지

                    // 하드웨어 가속 비활성화 (일부 디바이스에서 호환성 문제 방지)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        inPreferredColorSpace =
                            android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                    }
                }

                // 4. 썸네일 비트맵 디코딩 (안전한 방식)
                val originalBitmap = try {
                    android.graphics.BitmapFactory.decodeByteArray(
                        thumbnailData, 0, thumbnailData.size, options
                    )
                } catch (e: OutOfMemoryError) {
                    Log.e("PhotoThumbnail", "메모리 부족으로 비트맵 디코딩 실패: ${photo.name}", e)
                    // 메모리 부족 시 더 작은 샘플 사이즈로 재시도
                    options.inSampleSize = 2
                    try {
                        android.graphics.BitmapFactory.decodeByteArray(
                            thumbnailData, 0, thumbnailData.size, options
                        )
                    } catch (e2: Exception) {
                        Log.e("PhotoThumbnail", "재시도 비트맵 디코딩도 실패: ${photo.name}", e2)
                        null
                    }
                } catch (e: Exception) {
                    Log.e("PhotoThumbnail", "비트맵 디코딩 실패: ${photo.name}", e)
                    // 'unimplemented' 에러나 기타 디코딩 에러 처리
                    if (e.message?.contains("unimplemented") == true) {
                        Log.e(
                            "PhotoThumbnail",
                            "--- Failed to create image decoder with message 'unimplemented'"
                        )
                        Log.e("PhotoThumbnail", "썸네일 비트맵 디코딩 실패: ${photo.name}")
                    }
                    null
                }

                if (originalBitmap != null) {
                    Log.d(
                        "PhotoThumbnail",
                        "원본 비트맵 크기: ${originalBitmap.width}x${originalBitmap.height}"
                    )

                    // 5. 고화질 이미지의 EXIF 정보를 썸네일에 적용 (180도 회전 수정)
                    val rotatedBmp = when (fullExif) {
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> {
                            Log.d("PhotoThumbnail", "90도 회전 수정 적용: ${photo.name} (270도로 변경)")
                            try {
                                val matrix = android.graphics.Matrix()
                                matrix.postRotate(270f) // 90도 대신 270도 적용
                                android.graphics.Bitmap.createBitmap(
                                    originalBitmap, 0, 0,
                                    originalBitmap.width, originalBitmap.height,
                                    matrix, true
                                ).also {
                                    // 원본 비트맵 메모리 해제
                                    if (it != originalBitmap) {
                                        originalBitmap.recycle()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("PhotoThumbnail", "90도 회전 실패: ${photo.name}", e)
                                originalBitmap
                            }
                        }

                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> {
                            Log.d("PhotoThumbnail", "180도 회전 수정: ${photo.name} (회전하지 않음)")
                            // 180도 회전이 거꾸로 표시되는 문제 해결 - 일단 회전하지 않음
                            originalBitmap
                        }

                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> {
                            Log.d("PhotoThumbnail", "270도 회전 수정 적용: ${photo.name} (90도로 변경)")
                            try {
                                val matrix = android.graphics.Matrix()
                                matrix.postRotate(90f) // 270도 대신 90도 적용
                                android.graphics.Bitmap.createBitmap(
                                    originalBitmap, 0, 0,
                                    originalBitmap.width, originalBitmap.height,
                                    matrix, true
                                ).also {
                                    if (it != originalBitmap) {
                                        originalBitmap.recycle()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("PhotoThumbnail", "270도 회전 실패: ${photo.name}", e)
                                originalBitmap
                            }
                        }

                        else -> {
                            Log.d("PhotoThumbnail", "회전 없음: ${photo.name} (orientation: $fullExif)")
                            originalBitmap
                        }
                    }

                    // 6. ImageBitmap으로 변환
                    try {
                        rotatedBitmap = rotatedBmp.asImageBitmap()
                        Log.d("PhotoThumbnail", "비트맵 디코딩 완료: ${photo.name}, bitmap: true")
                        Log.d(
                            "PhotoThumbnail",
                            "썸네일 비트맵 적용 성공: ${photo.name} (${rotatedBmp.width}x${rotatedBmp.height})"
                        )
                    } catch (e: Exception) {
                        Log.e("PhotoThumbnail", "ImageBitmap 변환 실패: ${photo.name}", e)
                        // 변환 실패 시 비트맵 메모리 해제
                        rotatedBmp.recycle()
                    }
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
            contentScale = ContentScale.Crop
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

                // 썸네일 데이터 유효성 검사
                if (thumbnailData.isEmpty()) {
                    Log.w("PhotoThumbnail", "기본 썸네일 데이터가 비어있음: ${photo.name}")
                    return@withContext
                }

                // 안전한 비트맵 디코딩 옵션 설정
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    inMutable = false
                    inSampleSize = 1

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        inPreferredColorSpace =
                            android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                    }
                }

                val decodedBitmap = try {
                    android.graphics.BitmapFactory.decodeByteArray(
                        thumbnailData, 0, thumbnailData.size, options
                    )
                } catch (e: OutOfMemoryError) {
                    Log.e("PhotoThumbnail", "기본 썸네일 메모리 부족: ${photo.name}", e)
                    // 메모리 부족 시 샘플 사이즈 증가
                    options.inSampleSize = 2
                    try {
                        android.graphics.BitmapFactory.decodeByteArray(
                            thumbnailData, 0, thumbnailData.size, options
                        )
                    } catch (e2: Exception) {
                        Log.e("PhotoThumbnail", "기본 썸네일 재시도도 실패: ${photo.name}", e2)
                        null
                    }
                } catch (e: Exception) {
                    Log.e("PhotoThumbnail", "기본 썸네일 디코딩 실패: ${photo.name}", e)
                    if (e.message?.contains("unimplemented") == true) {
                        Log.e(
                            "PhotoThumbnail",
                            "--- Failed to create image decoder with message 'unimplemented'"
                        )
                        Log.e("PhotoThumbnail", "썸네일 비트맵 디코딩 실패: ${photo.name}")
                    }
                    null
                }

                if (decodedBitmap != null) {
                    try {
                        bitmap = decodedBitmap.asImageBitmap()
                        Log.d(
                            "PhotoThumbnail",
                            "썸네일 비트맵 적용 성공: ${photo.name} (${decodedBitmap.width}x${decodedBitmap.height})"
                        )
                    } catch (e: Exception) {
                        Log.e("PhotoThumbnail", "기본 썸네일 ImageBitmap 변환 실패: ${photo.name}", e)
                        decodedBitmap.recycle()
                    }
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
            contentScale = ContentScale.Crop
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
            androidx.compose.material.CircularProgressIndicator(
                color = MaterialTheme.colors.primary,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "로딩 중...",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
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

/**
 * 사진 크기에 따라 유동적인 비율을 가지는 썸네일 컴포넌트
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FluidPhotoThumbnail(
    photo: CameraPhoto,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    thumbnailData: ByteArray? = null,
    fullImageCache: Map<String, ByteArray> = emptyMap(),
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false
) {
    // 사진의 실제 비율을 기반으로 높이 결정
    val aspectRatio = remember(photo.path) {
        if (photo.width > 0 && photo.height > 0) {
            photo.width.toFloat() / photo.height.toFloat()
        } else {
            // 비율 정보가 없으면 랜덤한 비율 사용 (시각적 다양성을 위해)
            when ((0..3).random()) {
                0 -> 1f       // 정사각형
                1 -> 0.75f    // 세로형
                2 -> 1.33f    // 가로형
                else -> 0.6f  // 긴 세로형
            }
        }
    }

    // remember를 사용하여 photo.path가 변경될 때만 로그 출력 (중복 방지)
    remember(photo.path) {
        Log.d("FluidPhotoThumbnail", "=== 유동적 썸네일 어댑터 처리 시작: ${photo.name} ===")
        Log.d("FluidPhotoThumbnail", "photo.path: ${photo.path}")
        Log.d("FluidPhotoThumbnail", "thumbnailData size: ${thumbnailData?.size ?: 0} bytes")
        Log.d(
            "FluidPhotoThumbnail",
            "fullImageData size: ${fullImageCache[photo.path]?.size ?: 0} bytes"
        )
        Log.d(
            "FluidPhotoThumbnail",
            "파일 존재 여부: ${!photo.path.isNullOrEmpty() && File(photo.path).exists()}"
        )
        Log.d("FluidPhotoThumbnail", "계산된 비율: $aspectRatio (${photo.width}x${photo.height})")
        photo.path
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = 6.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Box {
            // 선택된 상태일 때 오버레이 추가
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Blue.copy(alpha = 0.3f))
                )
            }

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
                        contentScale = ContentScale.Crop
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
                        contentScale = ContentScale.Crop
                    )
                }

                thumbnailData != null && thumbnailData.isNotEmpty() -> {
                    val fullImageData = fullImageCache[photo.path]
                    if (fullImageData != null) {
                        FluidExifAwareThumbnail(
                            thumbnailData = thumbnailData,
                            fullImageData = fullImageData,
                            photo = photo,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // 고화질 이미지가 없을 때는 기본 썸네일만 표시 (중복 처리 방지)
                        FluidThumbnailImage(
                            thumbnailData = thumbnailData,
                            photo = photo,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                else -> {
                    // 네이티브 썸네일 데이터가 없거나 비어있는 경우
                    Log.w("FluidPhotoThumbnail", "썸네일 데이터 없음: ${photo.name}")
                    ThumbnailPlaceholder()
                }
            }

            // 멀티 선택 모드에서 선택 상태 표시
            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            color = if (isSelected) Color.Green else Color.White.copy(alpha = 0.8f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "선택됨",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        // 선택되지 않은 상태에서도 빈 원 표시
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    color = Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
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
 * 유동적 비율용 EXIF 인식 썸네일 컴포넌트
 */
@Composable
private fun FluidExifAwareThumbnail(
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
                Log.d("FluidPhotoThumbnail", "유동적 비트맵 디코딩 시작: ${photo.name}")
                Log.d("FluidPhotoThumbnail", "고화질 데이터에서 EXIF 읽어서 썸네일에 적용: ${photo.name}")

                // 1. 썸네일 데이터 유효성 검사
                if (thumbnailData.isEmpty()) {
                    Log.w("FluidPhotoThumbnail", "썸네일 데이터가 비어있음: ${photo.name}")
                    return@withContext
                }

                // 2. 고화질 이미지에서 EXIF 정보 읽기 (한 번만)
                val fullExif = try {
                    val exif = androidx.exifinterface.media.ExifInterface(
                        ByteArrayInputStream(fullImageData)
                    )
                    val orientation = exif.getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                    )

                    Log.d(
                        "FluidPhotoThumbnail",
                        "최종 EXIF Orientation: $orientation (${photo.name})"
                    )
                    orientation
                } catch (e: Exception) {
                    Log.e("FluidPhotoThumbnail", "고화질 EXIF 읽기 실패: ${photo.name}", e)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                }

                // 3. 안전한 비트맵 디코딩 옵션 설정
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    inMutable = false
                    inSampleSize = 1

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        inPreferredColorSpace =
                            android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                    }
                }

                // 4. 썸네일 비트맵 디코딩
                val originalBitmap = try {
                    android.graphics.BitmapFactory.decodeByteArray(
                        thumbnailData, 0, thumbnailData.size, options
                    )
                } catch (e: OutOfMemoryError) {
                    Log.e("FluidPhotoThumbnail", "메모리 부족으로 비트맵 디코딩 실패: ${photo.name}", e)
                    options.inSampleSize = 2
                    try {
                        android.graphics.BitmapFactory.decodeByteArray(
                            thumbnailData, 0, thumbnailData.size, options
                        )
                    } catch (e2: Exception) {
                        Log.e("FluidPhotoThumbnail", "재시도 비트맵 디코딩도 실패: ${photo.name}", e2)
                        null
                    }
                } catch (e: Exception) {
                    Log.e("FluidPhotoThumbnail", "비트맵 디코딩 실패: ${photo.name}", e)
                    null
                }

                if (originalBitmap != null) {
                    // 5. 고화질 이미지의 EXIF 정보를 썸네일에 적용 (180도 회전 수정)
                    val rotatedBmp = when (fullExif) {
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> {
                            Log.d("FluidPhotoThumbnail", "90도 회전 수정 적용: ${photo.name} (270도로 변경)")
                            try {
                                val matrix = android.graphics.Matrix()
                                matrix.postRotate(270f)
                                android.graphics.Bitmap.createBitmap(
                                    originalBitmap, 0, 0,
                                    originalBitmap.width, originalBitmap.height,
                                    matrix, true
                                ).also {
                                    if (it != originalBitmap) {
                                        originalBitmap.recycle()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("FluidPhotoThumbnail", "90도 회전 실패: ${photo.name}", e)
                                originalBitmap
                            }
                        }

                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> {
                            Log.d("FluidPhotoThumbnail", "180도 회전 수정: ${photo.name} (회전하지 않음)")
                            // 180도 회전이 거꾸로 표시되는 문제 해결 - 일단 회전하지 않음
                            originalBitmap
                        }

                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> {
                            Log.d("FluidPhotoThumbnail", "270도 회전 수정 적용: ${photo.name} (90도로 변경)")
                            try {
                                val matrix = android.graphics.Matrix()
                                matrix.postRotate(90f) // 270도 대신 90도 적용
                                android.graphics.Bitmap.createBitmap(
                                    originalBitmap, 0, 0,
                                    originalBitmap.width, originalBitmap.height,
                                    matrix, true
                                ).also {
                                    if (it != originalBitmap) {
                                        originalBitmap.recycle()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("FluidPhotoThumbnail", "270도 회전 실패: ${photo.name}", e)
                                originalBitmap
                            }
                        }

                        else -> {
                            Log.d(
                                "FluidPhotoThumbnail",
                                "회전 없음: ${photo.name} (orientation: $fullExif)"
                            )
                            originalBitmap
                        }
                    }

                    // 6. ImageBitmap으로 변환
                    try {
                        rotatedBitmap = rotatedBmp.asImageBitmap()
                        Log.d("FluidPhotoThumbnail", "유동적 비트맵 디코딩 완료: ${photo.name}")
                    } catch (e: Exception) {
                        Log.e("FluidPhotoThumbnail", "ImageBitmap 변환 실패: ${photo.name}", e)
                        rotatedBmp.recycle()
                    }
                } else {
                    Log.e("FluidPhotoThumbnail", "썸네일 비트맵 디코딩 실패: ${photo.name}")
                }
            } catch (e: Exception) {
                Log.e("FluidPhotoThumbnail", "유동적 EXIF 썸네일 처리 실패: ${photo.name}", e)
            }
        }
    }

    // 회전된 비트맵 표시
    rotatedBitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = photo.name,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } ?: run {
        // 로딩 중이거나 실패 시 플레이스홀더
        ThumbnailPlaceholder()
    }
}

/**
 * 유동적 비율용 기본 썸네일 이미지 컴포넌트
 */
@Composable
private fun FluidThumbnailImage(
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
                Log.d("FluidPhotoThumbnail", "유동적 기본 비트맵 디코딩 시작: ${photo.name}")

                if (thumbnailData.isEmpty()) {
                    Log.w("FluidPhotoThumbnail", "기본 썸네일 데이터가 비어있음: ${photo.name}")
                    return@withContext
                }

                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = false
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    inMutable = false
                    inSampleSize = 1

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        inPreferredColorSpace =
                            android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                    }
                }

                val decodedBitmap = try {
                    android.graphics.BitmapFactory.decodeByteArray(
                        thumbnailData, 0, thumbnailData.size, options
                    )
                } catch (e: OutOfMemoryError) {
                    Log.e("FluidPhotoThumbnail", "기본 썸네일 메모리 부족: ${photo.name}", e)
                    options.inSampleSize = 2
                    try {
                        android.graphics.BitmapFactory.decodeByteArray(
                            thumbnailData, 0, thumbnailData.size, options
                        )
                    } catch (e2: Exception) {
                        Log.e("FluidPhotoThumbnail", "기본 썸네일 재시도도 실패: ${photo.name}", e2)
                        null
                    }
                } catch (e: Exception) {
                    Log.e("FluidPhotoThumbnail", "기본 썸네일 디코딩 실패: ${photo.name}", e)
                    null
                }

                if (decodedBitmap != null) {
                    try {
                        bitmap = decodedBitmap.asImageBitmap()
                        Log.d("FluidPhotoThumbnail", "유동적 기본 썸네일 성공: ${photo.name}")
                    } catch (e: Exception) {
                        Log.e("FluidPhotoThumbnail", "기본 썸네일 ImageBitmap 변환 실패: ${photo.name}", e)
                        decodedBitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e("FluidPhotoThumbnail", "유동적 썸네일 이미지 처리 실패: ${photo.name}", e)
            }
        }
    }

    bitmap?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = photo.name,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } ?: ThumbnailPlaceholder()
}
