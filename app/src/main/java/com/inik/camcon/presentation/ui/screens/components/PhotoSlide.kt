package com.inik.camcon.presentation.ui.screens.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inik.camcon.domain.model.CameraPhoto
import java.io.File

/**
 * 개별 사진 슬라이드 컴포넌트
 * 썸네일 또는 파일 경로로부터 이미지를 표시
 */
@Composable
fun PhotoSlide(
    photo: CameraPhoto,
    modifier: Modifier = Modifier,
    thumbnailData: ByteArray? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            thumbnailData != null -> {
                val bitmap = BitmapFactory.decodeByteArray(thumbnailData, 0, thumbnailData.size)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            !photo.path.isNullOrEmpty() && File(photo.path).exists() -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo.path)
                        .crossfade(false)
                        .build(),
                    contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            else -> {
                PhotoLoadError()
            }
        }
    }
}

/**
 * 이미지 로딩 에러 표시
 */
@Composable
private fun PhotoLoadError() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.BrokenImage,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "이미지를 불러올 수 없습니다",
            style = MaterialTheme.typography.body2,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}