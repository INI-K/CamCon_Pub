package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import java.text.DateFormat
import java.util.Date

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

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 실제 파일 데이터가 있으면 우선 사용, 없으면 썸네일, 그것도 없으면 파일 경로 사용
        val imageData = fullImageData ?: thumbnailData ?: photo.path

        // WCAG 2.2 SC 1.1.1 — 사진은 의미를 가진 콘텐츠이므로 파일명 + 촬영 일시를
        // 결합한 의미 있는 contentDescription 을 제공한다.
        val a11yDescription = buildPhotoContentDescription(photo)

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageData)
                .crossfade(true)
                .allowHardware(false)
                .build(),
            contentDescription = a11yDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * 사진의 의미 있는 contentDescription 생성 — 파일명 + 촬영 일시.
 * date<=0 이면 날짜를 생략한 짧은 형식을 사용한다.
 */
@Composable
internal fun buildPhotoContentDescription(photo: CameraPhoto): String {
    return if (photo.date > 0L) {
        val dateText = DateFormat
            .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(photo.date * 1000L))
        stringResource(R.string.cd_photo_preview, photo.name, dateText)
    } else {
        stringResource(R.string.cd_photo_preview_no_date, photo.name)
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
            tint = com.inik.camcon.presentation.theme.TextPrimary.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = context.getString(R.string.image_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = com.inik.camcon.presentation.theme.TextPrimary.copy(alpha = 0.6f)
        )
    }
}

/**
 * Photo Slide 프리뷰
 */
@Preview(name = "Photo Slide - Placeholder", showBackground = true)
@Composable
private fun PhotoSlidePreview() {
    MaterialTheme {
        PhotoSlide(
            photo = CameraPhoto(
                name = "IMG_0001.JPG",
                path = "/sdcard/DCIM/Camera/IMG_0001.JPG",
                size = 2048576,
                date = System.currentTimeMillis() / 1000,
                width = 4032,
                height = 3024
            )
        )
    }
}