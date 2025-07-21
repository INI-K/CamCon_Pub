package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

/**
 * 핀치줌과 드래그가 가능한 이미지 컴포넌트
 */
@Composable
fun ZoomableImage(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RectangleShape)
            .pointerInput(Unit) {
                detectTransformGestures(
                    panZoomLock = false
                ) { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 5f)

                    // 줌 변경 시 오프셋 조정
                    val newOffset = if (newScale != scale) {
                        val scaleDifference = newScale / scale
                        Offset(
                            x = offset.x * scaleDifference,
                            y = offset.y * scaleDifference
                        )
                    } else {
                        offset
                    }

                    // 팬 제스처 적용
                    val adjustedOffset = newOffset + pan

                    // 경계 제한 계산
                    val maxX = (size.width * (newScale - 1f)) / 2f
                    val maxY = (size.height * (newScale - 1f)) / 2f

                    scale = newScale
                    offset = Offset(
                        x = if (maxX > 0) adjustedOffset.x.coerceIn(-maxX, maxX) else 0f,
                        y = if (maxY > 0) adjustedOffset.y.coerceIn(-maxY, maxY) else 0f
                    )
                }
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

/**
 * 더블탭으로 줌 리셋이 가능한 확장 버전
 */
@Composable
fun ZoomableImageWithDoubleTap(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RectangleShape)
            .pointerInput(Unit) {
                detectTransformGestures(
                    panZoomLock = false
                ) { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 5f)

                    // 줌 변경 시 오프셋 조정
                    val newOffset = if (newScale != scale) {
                        val scaleDifference = newScale / scale
                        Offset(
                            x = offset.x * scaleDifference,
                            y = offset.y * scaleDifference
                        )
                    } else {
                        offset
                    }

                    // 팬 제스처 적용
                    val adjustedOffset = newOffset + pan

                    // 경계 제한 계산
                    val maxX = (size.width * (newScale - 1f)) / 2f
                    val maxY = (size.height * (newScale - 1f)) / 2f

                    scale = newScale
                    offset = Offset(
                        x = if (maxX > 0) adjustedOffset.x.coerceIn(-maxX, maxX) else 0f,
                        y = if (maxY > 0) adjustedOffset.y.coerceIn(-maxY, maxY) else 0f
                    )
                }
            }
            .pointerInput(Unit) {
                // 더블탭으로 줌 리셋
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    }
                )
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}