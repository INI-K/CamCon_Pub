package com.inik.camcon.data.processor

import android.graphics.Bitmap

/**
 * [Lut3D] 를 GPUImage 의 `GPUImageLookupFilter` 가 기대하는 512×512 룩업 비트맵으로 변환한다.
 *
 * GPUImageLookupFilter 포맷: 64³ 룩업을 8×8 타일(각 64×64)로 펼친 512×512 이미지.
 * - 파란색 채널 b(0..63)가 타일을 선택: row = b/8, col = b%8
 * - 타일 내부에서 x = red(0..63), y = green(0..63)
 * - 픽셀 좌표 = (col*64 + red, row*64 + green)
 *
 * 원본 .cube 는 size 가 64 가 아닐 수 있으므로(예: 13), 각 64³ 격자점을 정규화 좌표로
 * 환산해 [sampleTrilinear] 로 보간한다. 결과 색 해상도는 원본 LUT 의 격자 밀도에 의해 제한된다.
 */
object FilmLutAtlasBuilder {

    private const val TILE = 64          // 한 축 64 단계 (0..63)
    private const val GRID = 8           // 8×8 타일
    private const val DIM = TILE * GRID  // 512
    private const val INV = 1f / (TILE - 1) // 1/63

    fun build(lut: Lut3D): Bitmap {
        val pixels = IntArray(DIM * DIM)
        val out = FloatArray(3)

        var b = 0
        while (b < TILE) {
            val tileRow = b / GRID
            val tileCol = b % GRID
            val bf = b * INV
            val baseY = tileRow * TILE
            val baseX = tileCol * TILE

            var g = 0
            while (g < TILE) {
                val gf = g * INV
                val rowOffset = (baseY + g) * DIM + baseX
                var r = 0
                while (r < TILE) {
                    lut.sampleTrilinear(r * INV, gf, bf, out)
                    val rr = (out[0].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val gg = (out[1].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val bb = (out[2].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    pixels[rowOffset + r] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
                    r++
                }
                g++
            }
            b++
        }

        return Bitmap.createBitmap(pixels, DIM, DIM, Bitmap.Config.ARGB_8888)
    }
}
