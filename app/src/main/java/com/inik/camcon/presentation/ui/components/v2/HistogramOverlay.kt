package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.util.HistogramData

/**
 * 라이브뷰용 RGB + Luminance 히스토그램 오버레이.
 *
 * 4 채널을 동일 Canvas 에 stacked line 으로 그린다.
 * 색상은 R/G/B/White (alpha 0.6) — 다크 테마 기준으로 가독성 우선.
 *
 * @param data null 이면 자리만 차지하고 그리지 않는다(토글 OFF 직후 깜빡임 방지).
 */
@Composable
fun HistogramOverlay(
    data: HistogramData?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = HISTOGRAM_WIDTH, height = HISTOGRAM_HEIGHT)
            .clip(RoundedCornerShape(Radius.md))
            .background(Surface1.copy(alpha = 0.7f))
            .padding(Spacing.xs)
    ) {
        if (data == null) return@Box

        val maxCount = data.maxCount.coerceAtLeast(1)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val binW = canvasW / 256f

            drawChannel(data.lum, maxCount, canvasH, binW, COLOR_LUM)
            drawChannel(data.r, maxCount, canvasH, binW, COLOR_R)
            drawChannel(data.g, maxCount, canvasH, binW, COLOR_G)
            drawChannel(data.b, maxCount, canvasH, binW, COLOR_B)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChannel(
    bins: IntArray,
    maxCount: Int,
    canvasH: Float,
    binW: Float,
    color: Color
) {
    for (i in 0 until 256) {
        val h = (bins[i].toFloat() / maxCount) * canvasH
        if (h <= 0f) continue
        val x = i * binW
        drawLine(
            color = color,
            start = Offset(x, canvasH),
            end = Offset(x, canvasH - h),
            strokeWidth = binW.coerceAtLeast(1f)
        )
    }
}

private val HISTOGRAM_WIDTH = 128.dp
private val HISTOGRAM_HEIGHT = 80.dp

private val COLOR_R = Color(0xFFE53935).copy(alpha = 0.6f)
private val COLOR_G = Color(0xFF43A047).copy(alpha = 0.6f)
private val COLOR_B = Color(0xFF1E88E5).copy(alpha = 0.6f)
private val COLOR_LUM = Color.White.copy(alpha = 0.6f)
