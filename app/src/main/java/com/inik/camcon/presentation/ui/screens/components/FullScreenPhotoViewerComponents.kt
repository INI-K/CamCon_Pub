package com.inik.camcon.presentation.ui.screens.components

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.TextPrimary
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import kotlinx.coroutines.launch

@Composable
fun FullScreenTopBar(
    photo: CameraPhoto,
    onClose: () -> Unit,
    onInfoClick: () -> Unit,
    onDownloadClick: (() -> Unit)?,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .background(
                    Background.copy(alpha = 0.6f),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_close),
                tint = TextPrimary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onInfoClick,
            modifier = Modifier
                .background(
                    Background.copy(alpha = 0.6f),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.cd_info),
                tint = TextPrimary
            )
        }

        if (onDownloadClick != null) {
            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier
                    .background(
                        Background.copy(alpha = 0.6f),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = stringResource(R.string.cd_download),
                    tint = TextPrimary
                )
            }
        }

        IconButton(
            onClick = onShareClick,
            modifier = Modifier
                .background(
                    Background.copy(alpha = 0.6f),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = stringResource(R.string.cd_share),
                tint = TextPrimary
            )
        }
    }
}

@Composable
fun PhotoPagerImage(
    fullImageData: ByteArray?,
    thumbnailData: ByteArray?,
    photo: CameraPhoto,
    onDismiss: () -> Unit,
    isLocalPhoto: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val imageModel: Any? = when {
        isLocalPhoto || java.io.File(photo.path).exists() -> {
            Log.d("PhotoPagerImage", "로컬 파일 사용: ${photo.path}")
            java.io.File(photo.path)
        }

        fullImageData != null -> {
            Log.d("PhotoPagerImage", "고화질 데이터 사용: ${fullImageData.size} bytes")
            fullImageData
        }

        thumbnailData != null -> {
            Log.d("PhotoPagerImage", "썸네일 데이터 사용: ${thumbnailData.size} bytes")
            thumbnailData
        }

        else -> {
            Log.d("PhotoPagerImage", "이미지 데이터 없음")
            null
        }
    }

    if (imageModel != null) {
        Log.d("PhotoPagerImage", "ZoomImage 사용: $imageModel")
        val zoomState = rememberCoilZoomState()

        val imageRequest = coil.request.ImageRequest.Builder(context)
            .data(imageModel)
            .allowHardware(false)
            .crossfade(true)
            .listener(
                onStart = { request ->
                    Log.d("PhotoPagerImage", "이미지 로딩 시작: ${photo.name}")
                },
                onSuccess = { request, result ->
                    Log.d("PhotoPagerImage", "이미지 로딩 성공: ${photo.name}")

                    try {
                        val exif = when (imageModel) {
                            is ByteArray -> {
                                androidx.exifinterface.media.ExifInterface(
                                    java.io.ByteArrayInputStream(imageModel)
                                )
                            }
                            is java.io.File -> {
                                androidx.exifinterface.media.ExifInterface(imageModel.absolutePath)
                            }
                            else -> null
                        }

                        if (exif != null) {
                            val orientation = exif.getAttributeInt(
                                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                            )

                            val rotationNeeded = when (orientation) {
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> "90도"
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> "180도 (⚠️ Coil은 이 상태에서 상하반전)"
                                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> "270도"
                                else -> "없음"
                            }

                            Log.d("EXIF_CHECK", "=== EXIF 회전 정보 확인 ===")
                            Log.d("EXIF_CHECK", "파일: ${photo.name}")
                            Log.d("EXIF_CHECK", "EXIF Orientation: $orientation")
                            Log.d("EXIF_CHECK", "필요한 회전: $rotationNeeded")
                            Log.d("EXIF_CHECK", "Coil의 자동 EXIF 회전 활성화 상태: 활성 (180도는 상하 반전됨)")
                        }
                    } catch (e: Exception) {
                        Log.e("EXIF_CHECK", "EXIF 정보 확인 실패: ${e.message}", e)
                    }
                },
                onError = { request, error ->
                    Log.e("PhotoPagerImage", "이미지 로딩 실패: ${photo.name}", error.throwable)
                }
            )
            .build()

        CoilZoomAsyncImage(
            model = imageRequest,
            contentDescription = photo.name,
            zoomState = zoomState,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    } else {
        PhotoPagerLoadingIndicator(modifier)
    }
}

@Composable
fun PhotoPagerLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = TextPrimary
        )
    }
}

@Composable
fun FullScreenBottomThumbnails(
    photos: List<CameraPhoto>,
    currentPhotoIndex: Int,
    thumbnailCache: Map<String, ByteArray>,
    viewModel: PhotoPreviewViewModel?,
    onPhotoSelected: (CameraPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    LaunchedEffect(currentPhotoIndex) {
        scope.launch {
            lazyListState.animateScrollToItem(currentPhotoIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Background.copy(alpha = 0.8f))
            .padding(8.dp)
    ) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(photos) { index, photo ->
                val isSelected = index == currentPhotoIndex

                ServerThumbnailItemWrapper(
                    photo = photo,
                    isSelected = isSelected,
                    thumbnailData = thumbnailCache[photo.path],
                    onClick = {
                        onPhotoSelected(photo)
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPhotoSelected(photo) }
                )
            }
        }
    }
}

@Composable
fun LocalBottomThumbnailStripWrapper(
    photos: List<CameraPhoto>,
    currentPhotoIndex: Int,
    onPhotoSelected: (CameraPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    LaunchedEffect(currentPhotoIndex) {
        scope.launch {
            lazyListState.animateScrollToItem(currentPhotoIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Background.copy(alpha = 0.8f))
            .padding(8.dp)
    ) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(photos) { index, photo ->
                val isSelected = index == currentPhotoIndex

                LocalThumbnailItemWrapper(
                    photo = photo,
                    isSelected = isSelected,
                    onClick = {
                        onPhotoSelected(photo)
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPhotoSelected(photo) }
                )
            }
        }
    }
}

@Composable
private fun ServerThumbnailItemWrapper(
    photo: CameraPhoto,
    isSelected: Boolean,
    thumbnailData: ByteArray?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
    ) {
        if (thumbnailData != null) {
            val bitmap = remember(thumbnailData) {
                android.graphics.BitmapFactory.decodeByteArray(
                    thumbnailData,
                    0,
                    thumbnailData.size
                )
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                PhotoThumbnailLoadingState()
            }
        } else {
            PhotoThumbnailLoadingState()
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

@Composable
private fun LocalThumbnailItemWrapper(
    photo: CameraPhoto,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
    ) {
        val bitmap = remember(photo.path) {
            android.graphics.BitmapFactory.decodeFile(photo.path)
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            PhotoThumbnailLoadingState()
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

@Composable
private fun PhotoThumbnailLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = TextPrimary
        )
    }
}
