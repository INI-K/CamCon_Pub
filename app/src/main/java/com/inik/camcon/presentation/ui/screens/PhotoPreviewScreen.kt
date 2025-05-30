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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel

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
            title = { Text("카메라 사진") },
            backgroundColor = MaterialTheme.colors.surface,
            elevation = 4.dp,
            actions = {
                if (uiState.photos.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.loadCameraPhotos() }
                    ) {
                        Text("새로고침", color = MaterialTheme.colors.primary)
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
                    CircularProgressIndicator()
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
        Snackbar(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(error)
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
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "카메라에 저장된 사진이 없습니다",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "카메라로 사진을 촬영하면 여기에 표시됩니다",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
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
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() },
        elevation = 2.dp
    ) {
        Box {
            // TODO: 실제 카메라에서 썸네일 가져오기
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
            }

            // 파일 이름 표시
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = photo.name,
                    color = Color.White,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(4.dp),
                    maxLines = 1
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
        title = { Text(photo.name) },
        text = {
            Column {
                // TODO: 실제 카메라에서 이미지 가져오기
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("경로: ${photo.path}", style = MaterialTheme.typography.caption)
                Text("크기: ${formatFileSize(photo.size)}", style = MaterialTheme.typography.caption)
                if (photo.width > 0 && photo.height > 0) {
                    Text(
                        "해상도: ${photo.width} x ${photo.height}",
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
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

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024}KB"
        else -> "${size / (1024 * 1024)}MB"
    }
}
