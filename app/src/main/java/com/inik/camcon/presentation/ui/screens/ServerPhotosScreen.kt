package com.inik.camcon.presentation.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Snackbar
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.ServerPhotosViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ServerPhotosScreen(
    viewModel: ServerPhotosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 모던한 헤더
        ModernServerHeader(
            photoCount = uiState.photos.size,
            onRefresh = { viewModel.refreshPhotos() }
        )

        when {
            uiState.isLoading -> {
                LoadingIndicator()
            }

            uiState.photos.isEmpty() -> {
                EmptyServerState()
            }

            else -> {
                PhotoGrid(
                    photos = uiState.photos,
                    onPhotoClick = { /* TODO: 사진 상세 보기 */ },
                    onDeleteClick = { photo -> viewModel.deletePhoto(photo.id) }
                )
            }
        }
    }

    // 에러 표시
    uiState.error?.let { error ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                backgroundColor = MaterialTheme.colors.error,
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(
                            text = "확인",
                            color = MaterialTheme.colors.onError
                        )
                    }
                }
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
private fun ModernServerHeader(
    photoCount: Int,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "내 사진",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            if (photoCount > 0) {
                Text(
                    text = "${photoCount}장의 사진",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "새로고침",
                tint = MaterialTheme.colors.onSurface
            )
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<CapturedPhoto>,
    onPhotoClick: (CapturedPhoto) -> Unit,
    onDeleteClick: (CapturedPhoto) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(photos) { photo ->
            PhotoGridItem(
                photo = photo,
                onClick = { onPhotoClick(photo) },
                onDelete = { onDeleteClick(photo) }
            )
        }
    }
}

@Composable
private fun PhotoGridItem(
    photo: CapturedPhoto,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            // 사진 이미지
            val painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(File(photo.filePath))
                    .crossfade(true)
                    .build()
            )

            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // 삭제 버튼 (우상단)
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 사진 정보 오버레이 (하단)
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                backgroundColor = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = File(photo.filePath).name,
                        color = Color.White,
                        style = MaterialTheme.typography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val dateFormat = SimpleDateFormat("MM.dd", Locale.getDefault())
                    Text(
                        text = dateFormat.format(Date(photo.captureTime)),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.caption,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colors.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "내 사진을 불러오는 중...",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun EmptyServerState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "저장된 사진이 없습니다",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "카메라에서 촬영하면\n이곳에 저장됩니다",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CapturedPhotoItem(
    photo: com.inik.camcon.domain.model.CapturedPhoto,
    onDelete: () -> Unit
) {
    // 이 함수는 더 이상 사용되지 않음 (그리드뷰로 변경)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: 사진 상세 보기 */ },
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 썸네일
            val painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(File(photo.filePath))
                    .crossfade(true)
                    .build()
            )

            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 사진 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = File(photo.filePath).name,
                    style = MaterialTheme.typography.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
                Text(
                    text = dateFormat.format(Date(photo.captureTime)),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )

                // 파일 크기 표시
                val sizeText = when {
                    photo.size > 1024 * 1024 -> "${photo.size / (1024 * 1024)}MB"
                    photo.size > 1024 -> "${photo.size / 1024}KB"
                    else -> "${photo.size}B"
                }
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            // 삭제 버튼
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = Color.Red.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyServerStatePreview() {
    CamConTheme {
        EmptyServerState()
    }
}

@Preview(showBackground = true)
@Composable
fun CapturedPhotoItemPreview() {
    CamConTheme {
        CapturedPhotoItem(
            photo = CapturedPhoto(
                id = "1",
                filePath = "/storage/emulated/0/Pictures/IMG_001.jpg",
                thumbnailPath = "/storage/emulated/0/Pictures/thumb_IMG_001.jpg",
                captureTime = System.currentTimeMillis(),
                cameraModel = "Canon EOS R6",
                settings = null,
                size = 1024 * 1024 * 5,
                width = 1920,
                height = 1080
            ),
            onDelete = {}
        )
    }
}
