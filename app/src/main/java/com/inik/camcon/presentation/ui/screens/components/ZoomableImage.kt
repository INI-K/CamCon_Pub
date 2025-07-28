package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
 * 더블탭으로 줌 리셋이 가능한 확장 버전 (싱글탭 지원)
 */
@Composable
fun ZoomableImageWithDoubleTap(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onSingleTap: (() -> Unit)? = null
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
                // 탭 제스처 처리 (더블탭으로 줌 리셋, 싱글탭으로 콜백)
                detectTapGestures(
                    onTap = { onSingleTap?.invoke() },
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

/**
 * 페이저와 호환되는 고급 줌 이미지 컴포넌트
 * - 단순한 제스처 처리로 핀치 줌과 스와이프가 모두 작동
 */
@Composable
fun PagerCompatibleZoomableImage(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onSingleTap: (() -> Unit)? = null,
    maxScale: Float = 5f,
    minScale: Float = 0.5f
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RectangleShape)
            .pointerInput(Unit) {
                detectTransformGestures(
                    panZoomLock = false
                ) { centroid, pan, zoom, _ ->
                    // 줌 처리
                    if (zoom != 1f) {
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)

                        if (newScale != scale) {
                            // 줌 중심점 계산
                            val centroidOffset = Offset(
                                x = centroid.x - size.width / 2f,
                                y = centroid.y - size.height / 2f
                            )

                            val scaleFactor = newScale / scale
                            offset = Offset(
                                x = (offset.x + centroidOffset.x) * scaleFactor - centroidOffset.x,
                                y = (offset.y + centroidOffset.y) * scaleFactor - centroidOffset.y
                            )

                            scale = newScale
                        }
                    }

                    // 팬 처리 (줌된 상태에서만)
                    if (scale > 1f && pan != Offset.Zero) {
                        val newOffset = offset + pan

                        // 경계 제한
                        val maxX = (size.width * (scale - 1f)) / 2f
                        val maxY = (size.height * (scale - 1f)) / 2f

                        offset = Offset(
                            x = if (maxX > 0) newOffset.x.coerceIn(-maxX, maxX) else 0f,
                            y = if (maxY > 0) newOffset.y.coerceIn(-maxY, maxY) else 0f
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (scale <= 1f) {
                            onSingleTap?.invoke()
                        }
                    },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            // 줌 아웃
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            // 줌 인 (2.5배)
                            val targetScale = 2.5f
                            scale = targetScale

                            // 더블탭 위치 중심으로 줌
                            val centroidOffset = Offset(
                                x = tapOffset.x - size.width / 2f,
                                y = tapOffset.y - size.height / 2f
                            )

                            offset = Offset(
                                x = -centroidOffset.x * (targetScale - 1f) / targetScale,
                                y = -centroidOffset.y * (targetScale - 1f) / targetScale
                            )
                        }
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

/**
 * ByteArray 데이터를 받아서 줌 가능한 이미지로 표시하는 컴포넌트
 */
@Composable
fun ZoomableImageFromByteArray(
    imageData: ByteArray,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onSingleTap: (() -> Unit)? = null,
    maxScale: Float = 5f,
    minScale: Float = 1f
) {
    val bitmap = remember(imageData) {
        BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
    }

    if (bitmap != null) {
        // 간단한 줌 이미지 사용 (테스트용)
        SimpleZoomableImage(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            onSingleTap = onSingleTap
        )
    } else {
        // 비트맵 디코딩 실패 시 기본 이미지 표시
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "이미지를 불러올 수 없습니다",
                color = Color.White,
                style = MaterialTheme.typography.body2
            )
        }
    }
}

/**
 * 간단하고 확실한 줌 이미지 컴포넌트 (테스트용)
 */
@Composable
fun SimpleZoomableImage(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onSingleTap: (() -> Unit)? = null
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RectangleShape)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // 기본적인 줌 처리
                    scale = (scale * zoom).coerceIn(1f, 5f)

                    // 줌된 상태에서만 팬 허용
                    if (scale > 1f) {
                        offset += pan

                        // 간단한 경계 제한
                        val maxOffset = size.width * (scale - 1f) * 0.5f
                        offset = Offset(
                            x = offset.x.coerceIn(-maxOffset, maxOffset),
                            y = offset.y.coerceIn(-maxOffset, maxOffset)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (scale <= 1f) {
                            onSingleTap?.invoke()
                        }
                    },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                        }
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