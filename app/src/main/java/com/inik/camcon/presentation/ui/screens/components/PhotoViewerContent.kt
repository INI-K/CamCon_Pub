package com.inik.camcon.presentation.ui.screens.components

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.inik.camcon.domain.model.CameraPhoto
import kotlinx.coroutines.launch

/**
 * 사진 뷰어의 메인 이미지 콘텐츠
 * 제스처 처리 및 이미지 슬라이드 관리
 */
@Composable
fun PhotoViewerContent(
    photo: CameraPhoto,
    photos: List<CameraPhoto>,
    thumbnailData: ByteArray?,
    currentPhotoIndex: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onAnimateScale: (Float) -> Unit,
    onAnimateOffset: (Float, Float) -> Unit,
    onPhotoChanged: (CameraPhoto) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()

    var lastTapTime by remember { mutableStateOf(0L) }

    Log.d("PhotoViewer", "PhotoViewerContent - scale: $scale, offset: ($offsetX, $offsetY)")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val downTime = System.currentTimeMillis()

                    Log.d("PhotoViewer", "포인터 다운: ${down.position}")

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }

                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            Log.d(
                                "PhotoViewer",
                                "제스처 - 줌: $zoomChange, 팬: $panChange, 포인터수: ${event.changes.size}"
                            )

                            when {
                                // 핀치 줌
                                event.changes.size > 1 && zoomChange != 1f -> {
                                    Log.d("PhotoViewer", "핀치 줌!")
                                    val newScale = (scale * zoomChange).coerceIn(1f, 3f)
                                    onScaleChange(newScale)

                                    if (newScale > 1f) {
                                        onOffsetChange(offsetX + panChange.x, offsetY + panChange.y)
                                    } else {
                                        onOffsetChange(0f, 0f)
                                    }
                                    event.changes.forEach { it.consume() }
                                }

                                // 확대된 상태에서 팬
                                scale > 1.1f -> {
                                    Log.d("PhotoViewer", "확대 상태 팬")
                                    onOffsetChange(offsetX + panChange.x, offsetY + panChange.y)
                                    event.changes.forEach { it.consume() }
                                }

                                // 기본 상태에서 스와이프
                                else -> {
                                    if (kotlin.math.abs(panChange.x) > kotlin.math.abs(panChange.y)) {
                                        Log.d("PhotoViewer", "스와이프: ${panChange.x}")
                                        onOffsetChange(offsetX + panChange.x, 0f)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // 제스처 종료 처리
                    val upTime = System.currentTimeMillis()
                    val duration = upTime - downTime

                    Log.d("PhotoViewer", "제스처 종료 - 시간: ${duration}ms, 최종 오프셋: $offsetX")

                    // 더블탭 체크
                    if (duration < 200 && kotlin.math.abs(offsetX) < 50) {
                        if (upTime - lastTapTime < 500) {
                            Log.d("PhotoViewer", "더블탭 감지! 현재 스케일: $scale")
                            if (scale > 1.1f) {
                                Log.d("PhotoViewer", "축소")
                                onAnimateScale(1f)
                                onAnimateOffset(0f, 0f)
                            } else {
                                Log.d("PhotoViewer", "확대")
                                onAnimateScale(2f)
                                val centerX =
                                    context.resources.displayMetrics.widthPixels.toFloat() / 2f
                                val centerY =
                                    context.resources.displayMetrics.heightPixels.toFloat() / 2f
                                val newOffsetX = (centerX - down.position.x) * 1f
                                val newOffsetY = (centerY - down.position.y) * 1f
                                onAnimateOffset(newOffsetX, newOffsetY)
                            }
                            lastTapTime = 0L
                        } else {
                            lastTapTime = upTime
                        }
                    }
                    // 스와이프 체크
                    else if (scale <= 1.1f) {
                        val threshold = screenWidth * 0.15f
                        Log.d("PhotoViewer", "스와이프 체크 - 오프셋: $offsetX, 임계값: $threshold")

                        if (kotlin.math.abs(offsetX) > threshold) {
                            Log.d("PhotoViewer", "사진 전환!")
                            when {
                                offsetX > 0 && currentPhotoIndex > 0 -> {
                                    Log.d("PhotoViewer", "이전 사진")
                                    onPhotoChanged(photos[currentPhotoIndex - 1])
                                }

                                offsetX < 0 && currentPhotoIndex < photos.size - 1 -> {
                                    Log.d("PhotoViewer", "다음 사진")
                                    onPhotoChanged(photos[currentPhotoIndex + 1])
                                }

                                else -> {
                                    Log.d("PhotoViewer", "복원")
                                    coroutineScope.launch { onAnimateOffset(0f, 0f) }
                                }
                            }
                        } else {
                            Log.d("PhotoViewer", "복원")
                            coroutineScope.launch { onAnimateOffset(0f, 0f) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 이전 이미지
        if (currentPhotoIndex > 0 && scale <= 1f) {
            PhotoSlide(
                photo = photos[currentPhotoIndex - 1],
                modifier = Modifier.graphicsLayer(
                    translationX = offsetX - screenWidth
                )
            )
        }

        // 현재 이미지
        PhotoSlide(
            photo = photo,
            thumbnailData = thumbnailData,
            modifier = Modifier.graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )
        )

        // 다음 이미지
        if (currentPhotoIndex < photos.size - 1 && scale <= 1f) {
            PhotoSlide(
                photo = photos[currentPhotoIndex + 1],
                modifier = Modifier.graphicsLayer(
                    translationX = offsetX + screenWidth
                )
            )
        }
    }
}