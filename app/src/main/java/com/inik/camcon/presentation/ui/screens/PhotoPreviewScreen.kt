package com.inik.camcon.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Snackbar
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import java.io.File

@Composable
fun PhotoPreviewScreen(
    viewModel: PhotoPreviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 헤더
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.recent_captures),
                    color = MaterialTheme.colors.onPrimary
                )
            },
            backgroundColor = MaterialTheme.colors.primary,
            elevation = 4.dp,
            actions = {
                if (uiState.photos.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.loadCameraPhotos() }
                    ) {
                        Text(
                            text = "새로고침",
                            color = MaterialTheme.colors.onPrimary
                        )
                    }
                }
            }
        )

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            uiState.photos.isEmpty() -> {
                EmptyPhotoState()
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.photos) { photo ->
                        PhotoThumbnail(
                            photo = photo,
                            onClick = { viewModel.selectPhoto(photo) }
                        )
                    }
                }
            }
        }
    }

    // 선택된 사진 상세 보기
    uiState.selectedPhoto?.let { photo ->
        PhotoDetailDialog(
            photo = photo,
            onDismiss = { viewModel.selectPhoto(null) },
            onDownload = { viewModel.downloadPhoto(photo) }
        )
    }

    // 에러 표시
    uiState.error?.let { error ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                backgroundColor = MaterialTheme.colors.error
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colors.onError
                )
            }
        }
    }
}

@Composable
fun EmptyPhotoState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "카메라에 저장된 사진이 없습니다",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "USB로 카메라를 연결해 주세요",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun PhotoThumbnail(
    photo: CameraPhoto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        elevation = 4.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Box {
            // 실제 이미지 로딩
            if (!photo.thumbnailPath.isNullOrEmpty() && File(photo.thumbnailPath).exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo.thumbnailPath)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (!photo.path.isNullOrEmpty() && File(photo.path).exists()) {
                // 썸네일이 없으면 원본 이미지 사용
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo.path)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 파일이 없을 때 기본 플레이스홀더
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "이미지",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // 파일 이름 오버레이
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = photo.name,
                    color = Color.White,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PhotoDetailDialog(
    photo: CameraPhoto,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = photo.name,
                style = MaterialTheme.typography.h6,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                // 사진 미리보기
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    elevation = 4.dp,
                    backgroundColor = MaterialTheme.colors.surface
                ) {
                    if (!photo.path.isNullOrEmpty() && File(photo.path).exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photo.path)
                                .crossfade(true)
                                .build(),
                            contentDescription = photo.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "이미지 로딩 오류",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 파일 정보
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InfoRow(
                        label = "경로",
                        value = photo.path ?: "알 수 없음"
                    )
                    InfoRow(
                        label = "크기",
                        value = formatFileSize(photo.size)
                    )
                    if (photo.width > 0 && photo.height > 0) {
                        InfoRow(
                            label = "해상도",
                            value = "${photo.width} x ${photo.height}"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text("다운로드")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
        else -> "${size / (1024 * 1024 * 1024)} GB"
    }
}

// Preview composables
@Preview(showBackground = true)
@Composable
fun EmptyPhotoStatePreview() {
    MaterialTheme {
        EmptyPhotoState()
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoThumbnailPreview() {
    MaterialTheme {
        val samplePhoto = CameraPhoto(
            name = "IMG_001.jpg",
            path = "/storage/camera/IMG_001.jpg",
            size = 2048576, // 2MB
            date = System.currentTimeMillis(),
            width = 1920,
            height = 1080,
            thumbnailPath = null
        )
        PhotoThumbnail(
            photo = samplePhoto,
            onClick = { }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoDetailDialogPreview() {
    MaterialTheme {
        val samplePhoto = CameraPhoto(
            name = "IMG_001.jpg",
            path = "/storage/camera/IMG_001.jpg",
            size = 2048576, // 2MB
            date = System.currentTimeMillis(),
            width = 1920,
            height = 1080,
            thumbnailPath = null
        )
        PhotoDetailDialog(
            photo = samplePhoto,
            onDismiss = { },
            onDownload = { }
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PhotoPreviewScreenWithPhotosPreview() {
    MaterialTheme {
        // 샘플 사진들이 있는 상태의 화면
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items((1..9).map { index ->
                CameraPhoto(
                    name = "IMG_${String.format("%03d", index)}.jpg",
                    path = "/storage/camera/IMG_${String.format("%03d", index)}.jpg",
                    size = (1024 * 1024 * (1..5).random()).toLong(),
                    date = System.currentTimeMillis() - (index * 3600000), // 각각 1시간씩 차이
                    width = 1920,
                    height = 1080,
                    thumbnailPath = null
                )
            }) { photo ->
                PhotoThumbnail(
                    photo = photo,
                    onClick = { }
                )
            }
        }
    }
}
