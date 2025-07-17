package com.inik.camcon.presentation.ui.screens.components

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inik.camcon.domain.model.CameraPhoto
import kotlinx.coroutines.launch

/**
 * 전체화면으로 사진을 볼 수 있는 뷰어 컴포넌트
 * 갤러리 앱처럼 동작: 더블탭 줌, 핀치 줌, 스와이프 전환, 가로/세로 화면 대응
 */
@Composable
fun FullScreenPhotoViewer(
    photo: CameraPhoto,
    photos: List<CameraPhoto>,
    onDismiss: () -> Unit,
    onPhotoChanged: (CameraPhoto) -> Unit,
    thumbnailData: ByteArray?,
    fullImageData: ByteArray?,
    isDownloadingFullImage: Boolean = false,
    onDownload: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val scaleAnimatable = remember { Animatable(1f) }
    val offsetXAnimatable = remember { Animatable(0f) }
    val offsetYAnimatable = remember { Animatable(0f) }

    val currentPhotoIndex = photos.indexOfFirst { it.path == photo.path }
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    Log.d("FullScreenViewer", "=== FullScreenPhotoViewer 렌더링 ===")
    Log.d("FullScreenViewer", "사진: ${photo.name}, 인덱스: $currentPhotoIndex")
    Log.d("FullScreenViewer", "화면 방향: ${configuration.orientation}")

    // 화면 회전 시 상태 초기화
    LaunchedEffect(configuration.orientation) {
        Log.d("FullScreenViewer", "🔄 화면 회전 감지 - 상태 초기화")
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        scaleAnimatable.snapTo(1f)
        offsetXAnimatable.snapTo(0f)
        offsetYAnimatable.snapTo(0f)
    }

    // 새 사진으로 변경될 때 변환 상태 초기화
    LaunchedEffect(photo.path) {
        Log.d("FullScreenViewer", "🔄 사진 변경됨 - 상태 초기화: ${photo.name}")
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        scaleAnimatable.snapTo(1f)
        offsetXAnimatable.snapTo(0f)
        offsetYAnimatable.snapTo(0f)
        Log.d("FullScreenViewer", "✅ 상태 초기화 완료")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 메인 이미지 영역
            PhotoViewerContent(
                photo = photo,
                photos = photos,
                thumbnailData = thumbnailData,
                fullImageData = fullImageData,
                isDownloadingFullImage = isDownloadingFullImage,
                currentPhotoIndex = currentPhotoIndex,
                scale = scaleAnimatable.value,
                offsetX = offsetXAnimatable.value,
                offsetY = offsetYAnimatable.value,
                onScaleChange = { newScale ->
                    Log.d("FullScreenViewer", "📊 스케일 변경 요청: $scale → $newScale")
                    scale = newScale
                    coroutineScope.launch {
                        scaleAnimatable.snapTo(newScale)
                        Log.d("FullScreenViewer", "✅ 스케일 애니메이션 적용 완료: ${scaleAnimatable.value}")
                    }
                },
                onOffsetChange = { x, y ->
                    Log.d("FullScreenViewer", "📍 오프셋 변경 요청: ($offsetX, $offsetY) → ($x, $y)")
                    offsetX = x
                    offsetY = y
                    coroutineScope.launch {
                        offsetXAnimatable.snapTo(x)
                        offsetYAnimatable.snapTo(y)
                        Log.d("FullScreenViewer", "✅ 오프셋 애니메이션 적용 완료: (${offsetXAnimatable.value}, ${offsetYAnimatable.value})")
                    }
                },
                onAnimateScale = { targetScale ->
                    Log.d("FullScreenViewer", "🎬 스케일 애니메이션 시작: $scale → $targetScale")
                    coroutineScope.launch {
                        scaleAnimatable.animateTo(targetScale, tween(300))
                        scale = targetScale
                        Log.d("FullScreenViewer", "✅ 스케일 애니메이션 완료: ${scaleAnimatable.value}")
                    }
                },
                onAnimateOffset = { targetX, targetY ->
                    Log.d("FullScreenViewer", "🎬 오프셋 애니메이션 시작: ($offsetX, $offsetY) → ($targetX, $targetY)")
                    coroutineScope.launch {
                        offsetXAnimatable.animateTo(targetX, tween(300))
                        offsetYAnimatable.animateTo(targetY, tween(300))
                        offsetX = targetX
                        offsetY = targetY
                        Log.d("FullScreenViewer", "✅ 오프셋 애니메이션 완료: (${offsetXAnimatable.value}, ${offsetYAnimatable.value})")
                    }
                },
                onPhotoChanged = { newPhoto ->
                    Log.d("FullScreenViewer", "📸 사진 변경 요청: ${photo.name} → ${newPhoto.name}")
                    onPhotoChanged(newPhoto)
                }
            )

            // 상단 파일명과 페이지 정보
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.7f),
                        RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = photo.name,
                        style = MaterialTheme.typography.h6,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (photos.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${currentPhotoIndex + 1} / ${photos.size}",
                            style = MaterialTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 상단 컨트롤 버튼들 (오른쪽 상단)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                PhotoViewerTopControls(
                    onShowDetails = { showDetails = true },
                    onDismiss = onDismiss
                )
            }
        }
    }

    // 상세 정보 다이얼로그
    if (showDetails) {
        PhotoDetailsDialog(
            photo = photo,
            onDismiss = { showDetails = false },
            onDownload = onDownload
        )
    }
}