package com.inik.camcon.presentation.util

import android.graphics.Bitmap

/**
 * RGB + Luminance 256-bin 히스토그램.
 *
 * 각 채널은 0..255 인덱스 별 픽셀 카운트를 담는다.
 * Luminance 는 Rec.601 가중치 (0.299·R + 0.587·G + 0.114·B) 로 계산한다.
 */
data class HistogramData(
    val r: IntArray,
    val g: IntArray,
    val b: IntArray,
    val lum: IntArray
) {
    init {
        require(r.size == 256 && g.size == 256 && b.size == 256 && lum.size == 256) {
            "Histogram bins must be 256"
        }
    }

    /** 모든 채널 최대 카운트 — Y축 정규화에 사용. */
    val maxCount: Int
        get() {
            var m = 0
            for (i in 0 until 256) {
                if (r[i] > m) m = r[i]
                if (g[i] > m) m = g[i]
                if (b[i] > m) m = b[i]
                if (lum[i] > m) m = lum[i]
            }
            return m
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HistogramData) return false
        return r.contentEquals(other.r) &&
                g.contentEquals(other.g) &&
                b.contentEquals(other.b) &&
                lum.contentEquals(other.lum)
    }

    override fun hashCode(): Int {
        var result = r.contentHashCode()
        result = 31 * result + g.contentHashCode()
        result = 31 * result + b.contentHashCode()
        result = 31 * result + lum.contentHashCode()
        return result
    }
}

/**
 * Bitmap 의 RGB/luminance 256-bin 히스토그램을 계산한다.
 *
 * 비용을 줄이기 위해 [downsample] 픽셀 간격으로 샘플링한다.
 * 기본값 4 는 1/16 픽셀만 읽어 라이브뷰 30FPS 에서도 메인스레드 외에서 처리 가능한 수준.
 *
 * Pure function — 호출자는 반드시 IO/Default 디스패처에서 실행한다.
 * 반환된 IntArray 는 호출자가 자유롭게 보유 가능.
 *
 * @param bitmap 입력 비트맵. recycled 상태면 null 을 반환.
 * @param downsample 1 이상의 샘플링 간격. 0 이하면 1 로 보정.
 */
fun computeHistogram(bitmap: Bitmap, downsample: Int = 4): HistogramData? {
    if (bitmap.isRecycled) return null
    val step = if (downsample <= 0) 1 else downsample

    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return null

    val r = IntArray(256)
    val g = IntArray(256)
    val b = IntArray(256)
    val lum = IntArray(256)

    // getPixels 로 한 행씩 읽어 step 간격 샘플링.
    // 전체를 한 번에 읽는 것보다 메모리 사용이 훨씬 적다 (large frame 대비).
    val rowBuffer = IntArray(w)
    var y = 0
    while (y < h) {
        bitmap.getPixels(rowBuffer, 0, w, 0, y, w, 1)
        var x = 0
        while (x < w) {
            val pixel = rowBuffer[x]
            val rv = (pixel shr 16) and 0xFF
            val gv = (pixel shr 8) and 0xFF
            val bv = pixel and 0xFF
            r[rv]++
            g[gv]++
            b[bv]++
            // Rec.601 luminance — 정수 근사 (299·R + 587·G + 114·B) / 1000.
            val l = (299 * rv + 587 * gv + 114 * bv) / 1000
            lum[l.coerceIn(0, 255)]++
            x += step
        }
        y += step
    }
    return HistogramData(r, g, b, lum)
}
