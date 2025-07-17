package com.inik.camcon.presentation.ui.screens.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inik.camcon.domain.model.CameraPhoto
import java.io.File

/**
 * 사진 썸네일을 표시하는 카드 컴포넌트
 */
@Composable
fun PhotoThumbnail(
    photo: CameraPhoto,
    onClick: () -> Unit,
    thumbnailData: ByteArray? = null
) {
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
                            .build(),
                        contentDescription = photo.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                thumbnailData != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumbnailData)
                            .crossfade(true)
                            .build(),
                        contentDescription = photo.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
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