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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.SkeletonLoader
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.launch

@Composable
fun FullScreenTopBar(
    photo: CameraPhoto,
    onClose: () -> Unit,
    onInfoClick: () -> Unit,
    onDownloadClick: (() -> Unit)?,
    onShareClick: () -> Unit,
    onFilmEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog && onDeleteClick != null) {
        AppDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.preview_delete_title)) },
            text = { Text(stringResource(R.string.preview_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteClick()
                }) {
                    Text(stringResource(R.string.preview_delete_title))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.base),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .background(
                    Surface0.copy(alpha = 0.6f),
                    RoundedCornerShape(Radius.sm)
                )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_close),
                tint = TextPrimaryV2
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onInfoClick,
            modifier = Modifier
                .background(
                    Surface0.copy(alpha = 0.6f),
                    RoundedCornerShape(Radius.sm)
                )
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(R.string.cd_info),
                tint = TextPrimaryV2
            )
        }

        if (onDownloadClick != null) {
            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier
                    .background(
                        Surface0.copy(alpha = 0.6f),
                        RoundedCornerShape(Radius.sm)
                    )
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = stringResource(R.string.cd_download),
                    tint = TextPrimaryV2
                )
            }
        }

        IconButton(
            onClick = onShareClick,
            modifier = Modifier
                .background(
                    Surface0.copy(alpha = 0.6f),
                    RoundedCornerShape(Radius.sm)
                )
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = stringResource(R.string.cd_share),
                tint = TextPrimaryV2
            )
        }

        // 필름 편집 아이콘 — 로컬 디코딩 가능 파일이며 RAW 아닐 때만 호출자가 전달(Phase 4 진입점).
        if (onFilmEditClick != null) {
            IconButton(
                onClick = onFilmEditClick,
                modifier = Modifier
                    .background(
                        Surface0.copy(alpha = 0.6f),
                        RoundedCornerShape(Radius.sm)
                    )
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = stringResource(R.string.cd_film_edit),
                    tint = TextPrimaryV2
                )
            }
        }

        // H7-A — 삭제 아이콘 (구독·기능 게이팅 통과한 경우만 호출자가 전달)
        if (onDeleteClick != null) {
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .background(
                        Surface0.copy(alpha = 0.6f),
                        RoundedCornerShape(Radius.sm)
                    )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.preview_delete_title),
                    tint = TextPrimaryV2
                )
            }
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
            Log.d("PhotoPagerImage", "로컬 파일 사용: ${LogMask.path(photo.path)}")
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
            // RAW(NEF 등)는 Coil 이 EXIF orientation 을 안 씌워 세로컷이 눕는다 → RAW 만 방향 보정(전체화면도 목록과 동일).
            .apply {
                com.inik.camcon.presentation.ui.util.RawExifRotationTransformation
                    .forPathOrNull(photo.path)?.let { transformations(it) }
            }
            .listener(
                onStart = { request ->
                    Log.d("PhotoPagerImage", "이미지 로딩 시작: ${photo.name}")
                },
                onSuccess = { request, result ->
                    Log.d("PhotoPagerImage", "이미지 로딩 성공: ${photo.name}")

                    // LOW: EXIF 디버그 로깅은 DEBUG 빌드에서만 수행 (메인 스레드 I/O 회피)
                    if (com.inik.camcon.BuildConfig.DEBUG) {
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

                                Log.d(
                                    "EXIF_CHECK",
                                    "EXIF 회전: ${photo.name} orientation=$orientation 필요회전=$rotationNeeded (Coil 자동회전 활성, 180도는 상하반전)"
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("EXIF_CHECK", "EXIF 정보 확인 실패: ${e.message}", e)
                        }
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
    SkeletonLoader(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(Radius.sm)
    )
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
            .background(Surface0.copy(alpha = 0.8f))
            .padding(Spacing.sm)
    ) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            contentPadding = PaddingValues(horizontal = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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
                        .clip(RoundedCornerShape(Radius.sm))
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
            .background(Surface0.copy(alpha = 0.8f))
            .padding(Spacing.sm)
    ) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            contentPadding = PaddingValues(horizontal = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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
                        .clip(RoundedCornerShape(Radius.sm))
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
                RoundedCornerShape(Radius.sm)
            )
            .clip(RoundedCornerShape(Radius.sm))
    ) {
        if (thumbnailData != null) {
            val bitmap = remember(thumbnailData) {
                android.graphics.BitmapFactory.decodeByteArray(
                    thumbnailData,
                    0,
                    thumbnailData.size
                )
            }

            // F28: 수동 디코딩한 비트맵을 key 변경/컴포지션 이탈 시 명시적으로 recycle
            DisposableEffect(thumbnailData) {
                onDispose {
                    try {
                        bitmap?.let { if (!it.isRecycled) it.recycle() }
                    } catch (e: Exception) {
                        Log.w("FullScreenThumbnail", "썸네일 Bitmap recycle 실패: ${photo.name}", e)
                    }
                }
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
                        RoundedCornerShape(Radius.sm)
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
                RoundedCornerShape(Radius.sm)
            )
            .clip(RoundedCornerShape(Radius.sm))
    ) {
        val bitmap = remember(photo.path) {
            android.graphics.BitmapFactory.decodeFile(photo.path)
        }

        // F28: 수동 디코딩한 비트맵을 key 변경/컴포지션 이탈 시 명시적으로 recycle
        DisposableEffect(photo.path) {
            onDispose {
                try {
                    bitmap?.let { if (!it.isRecycled) it.recycle() }
                } catch (e: Exception) {
                    Log.w("FullScreenThumbnail", "썸네일 Bitmap recycle 실패: ${photo.name}", e)
                }
            }
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
                        RoundedCornerShape(Radius.sm)
                    )
            )
        }
    }
}

@Composable
private fun PhotoThumbnailLoadingState(modifier: Modifier = Modifier) {
    SkeletonLoader(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(Radius.sm),
        announceLoading = false
    )
}
