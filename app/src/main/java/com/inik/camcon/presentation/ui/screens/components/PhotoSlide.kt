package com.inik.camcon.presentation.ui.screens.components

import android.graphics.ColorSpace
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
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

        // Coil을 사용하여 이미지 로딩 - ByteArray와 파일 경로 모두 지원
        val painter = rememberAsyncImagePainter(
            ImageRequest.Builder(LocalContext.current)
                .data(imageData)
                .crossfade(true)
                .size(
                    // 화면 크기의 2배 정도로 제한 (고해상도 디스플레이 고려, 하지만 메모리 안전)
                    if (fullImageData != null) {
                        Size(
                            width = (screenWidth * 2).coerceAtMost(3840),  // 최대 4K 너비
                            height = (screenHeight * 2).coerceAtMost(2160) // 최대 4K 높이
                        )
                    } else {
                        // 썸네일의 경우 화면 크기 정도로 제한
                        Size(screenWidth, screenHeight)
                    }
                )
                .memoryCachePolicy(CachePolicy.ENABLED) // 메모리 캐시 활성화
                .diskCachePolicy(CachePolicy.ENABLED) // 디스크 캐시 활성화
                .apply {
                    // sRGB 색공간 설정
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    }
                }
                .build()
        )

        Image(
            painter = painter,
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