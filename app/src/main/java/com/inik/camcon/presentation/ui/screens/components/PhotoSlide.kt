package com.inik.camcon.presentation.ui.screens.components

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto

/**
 * 개별 사진 슬라이드 컴포넌트
 * 썸네일 또는 파일 경로로부터 이미지를 표시
 */
@Composable
fun PhotoSlide(
    photo: CameraPhoto,
    modifier: Modifier = Modifier,
    thumbnailData: ByteArray? = null,
    fullImageData: ByteArray? = null,
    isDownloadingFullImage: Boolean = false
) {
    val context = LocalContext.current

    // 화면 크기 가져오기
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    // 이미지 데이터 상태 로깅
    android.util.Log.d("PhotoSlide", "=== PhotoSlide 렌더링: ${photo.name} ===")
    android.util.Log.d("PhotoSlide", "썸네일 데이터: ${thumbnailData?.size ?: 0} bytes")
    android.util.Log.d("PhotoSlide", "실제 파일 데이터: ${fullImageData?.size ?: 0} bytes")
    android.util.Log.d("PhotoSlide", "다운로드 중: $isDownloadingFullImage")
    android.util.Log.d("PhotoSlide", "화면 크기: ${screenWidth}x${screenHeight}")

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 실제 파일 데이터가 있으면 우선 사용, 없으면 썸네일, 그것도 없으면 파일 경로 사용
        val imageData = fullImageData ?: thumbnailData ?: photo.path
        val isFullQuality = fullImageData != null

        android.util.Log.d(
            "PhotoSlide",
            "사용할 이미지 데이터: ${if (isFullQuality) "고화질" else if (thumbnailData != null) "썸네일" else "파일 경로"}"
        )

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageData)
                .crossfade(true)
                .allowHardware(false)
                .build(),
            contentDescription = photo.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 이미지 로딩 에러 표시
 */
@Composable
private fun PhotoLoadError() {
    val context = LocalContext.current

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
            text = context.getString(R.string.image_load_failed),
            style = MaterialTheme.typography.body2,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}