package com.inik.camcon.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ServerPhotosScreen() {
    // TODO: 실제 서버 데이터로 대체
    val serverPhotos = listOf<ServerPhoto>() // 빈 리스트로 시작
    var isLoading by remember { mutableStateOf(false) }

    if (serverPhotos.isEmpty() && !isLoading) {
        EmptyServerState()
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(serverPhotos) { photo ->
                ServerPhotoItem(photo)
            }
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
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "서버에 저장된 사진이 없습니다",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "촬영한 사진을 서버에 업로드해보세요",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ServerPhotoItem(photo: ServerPhoto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Photo Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = photo.name,
                    style = MaterialTheme.typography.body1
                )
                Text(
                    text = "${photo.size} • ${photo.date}",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            // Download Button
            IconButton(onClick = { /* TODO: Download logic */ }) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colors.primary
                )
            }
        }
    }
}

data class ServerPhoto(
    val id: String,
    val name: String,
    val size: String,
    val date: String,
    val url: String
)
