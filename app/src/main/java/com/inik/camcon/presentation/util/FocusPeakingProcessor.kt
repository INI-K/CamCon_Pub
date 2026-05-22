package com.inik.camcon.presentation.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min

/**
 * Sobel 3×3 에지 검출 기반 포커스 피킹 프로세서.
 *
 * 입력 비트맵을 다운샘플 → luminance 변환 → Sobel gradient → threshold 이상 픽셀에
 * [color] 오버레이 → nearest upscale 순으로 처리한 새 비트맵을 반환한다.
 *
 * 호출자가 책임:
 * - 반드시 IO 디스패처에서 실행. 비용이 크다.
 * - 반환 비트맵 사용 후 recycle.
 *
 * @param source 원본 라이브뷰 비트맵 (recycled 이면 null 반환)
 * @param threshold Sobel magnitude 임계값. 30~100 권장. 클수록 strong edge 만 표시.
 * @param color 오버레이 색상 (ARGB)
 * @param downsample 1 이상. 클수록 빠르지만 거친 edge.
 * @return 에지 오버레이가 입혀진 새 ARGB_8888 비트맵. 입력은 변경하지 않음.
 */
fun applyFocusPeaking(
    source: Bitmap,
    threshold: Int = 60,
    color: Int = Color.RED,
    downsample: Int = 2
): Bitmap? {
    if (source.isRecycled) return null
    val ds = if (downsample <= 0) 1 else downsample
    val w = source.width
    val h = source.height
    if (w <= 2 || h <= 2) return null

    val dw = w / ds
    val dh = h / ds
    if (dw <= 2 || dh <= 2) return null

    // 1) 다운샘플 + luminance 추출
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    val lum = IntArray(dw * dh)
    var dyIdx = 0
    var sy = 0
    while (dyIdx < dh) {
        var dxIdx = 0
        var sx = 0
        val rowOffset = sy * w
        while (dxIdx < dw) {
            val p = pixels[rowOffset + sx]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            lum[dyIdx * dw + dxIdx] = (299 * r + 587 * g + 114 * b) / 1000
            dxIdx++
            sx += ds
        }
        dyIdx++
        sy += ds
    }

    // 2) Sobel 3×3 — strong edge 마스크 생성
    // Gx = [-1 0 1; -2 0 2; -1 0 1], Gy = [-1 -2 -1; 0 0 0; 1 2 1]
    val mask = BooleanArray(dw * dh)
    var y = 1
    while (y < dh - 1) {
        val rowM = y * dw
        val rowU = (y - 1) * dw
        val rowD = (y + 1) * dw
        var x = 1
        while (x < dw - 1) {
            val tl = lum[rowU + x - 1]
            val tc = lum[rowU + x]
            val tr = lum[rowU + x + 1]
            val ml = lum[rowM + x - 1]
            val mr = lum[rowM + x + 1]
            val bl = lum[rowD + x - 1]
            val bc = lum[rowD + x]
            val br = lum[rowD + x + 1]
            val gx = -tl + tr - 2 * ml + 2 * mr - bl + br
            val gy = -tl - 2 * tc - tr + bl + 2 * bc + br
            val mag = abs(gx) + abs(gy) // L1 approximation — Sobel magnitude
            if (mag >= threshold) {
                mask[rowM + x] = true
            }
            x++
        }
        y++
    }

    // 3) 입력 픽셀을 복사한 뒤 mask 영역을 nearest upscale 로 색칠.
    val result = pixels // 재사용 — 원본 source 는 건드리지 않음(별도 IntArray 였음).
    var py = 0
    while (py < h) {
        val sdy = min(py / ds, dh - 1)
        val maskRow = sdy * dw
        var px = 0
        while (px < w) {
            val sdx = min(px / ds, dw - 1)
            if (mask[maskRow + sdx]) {
                result[py * w + px] = color
            }
            px++
        }
        py++
    }

    val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    output.setPixels(result, 0, w, 0, 0, w, h)
    return output
}
