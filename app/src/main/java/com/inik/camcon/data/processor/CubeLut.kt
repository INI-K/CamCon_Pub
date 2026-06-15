package com.inik.camcon.data.processor

import kotlin.math.floor

/**
 * 파싱된 3D LUT.
 *
 * @param size LUT_3D_SIZE (한 축의 격자 점 개수). 예: 13 → 13³ 엔트리.
 * @param data RGB 인터리브 FloatArray. 길이 = size³ * 3. .cube 파일 순서(R이 가장 빠르게,
 *   그다음 G, 그다음 B)를 그대로 보존한다. 따라서 격자점 (r,g,b)의 시작 인덱스는
 *   `((b * size + g) * size + r) * 3`.
 */
data class Lut3D(val size: Int, val data: FloatArray) {
    override fun equals(other: Any?): Boolean =
        other is Lut3D && other.size == size && other.data.contentEquals(data)

    override fun hashCode(): Int = 31 * size + data.contentHashCode()
}

/**
 * Adobe `.cube` 3D LUT 파서.
 *
 * 참조 구현(YahiaAngelo/Film-Simulator `SkiaImageProcessor.parseCubeLut`)과 동일한 규칙:
 * 주석(#)·`TITLE`·`DOMAIN_*`·`LUT_1D_*` 줄은 건너뛰고, `LUT_3D_SIZE` 이후 데이터 트리플만 읽는다.
 * 1D LUT(`LUT_1D_SIZE`)나 데이터가 부족한 파일은 null 을 반환한다.
 */
object CubeLutParser {

    private val WHITESPACE = Regex("\\s+")

    fun parse(text: String): Lut3D? {
        var size = 0
        val data = ArrayList<Float>()

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.startsWith("LUT_3D_SIZE", ignoreCase = true)) {
                size = line.substringAfter("LUT_3D_SIZE").trim().toIntOrNull() ?: return null
                if (size <= 1 || size > 128) return null
                data.ensureCapacity(size * size * size * 3)
                continue
            }
            // DOMAIN_MIN/MAX 가 단위 입방체(0..1)가 아니면 거부한다. sampleTrilinear 는 입력을
            // 0..1 로 가정하므로, 비단위 도메인(log/HDR LUT 등)을 그대로 적용하면 무음 오발색이 된다.
            // 현재 번들된 296개는 모두 0..1 이므로 영향 없음. fail-fast 로 잘못된 LUT 를 가시화한다.
            if (line.startsWith("DOMAIN_MIN", ignoreCase = true)) {
                val nonZero = line.substringAfter("DOMAIN_MIN").trim().split(WHITESPACE)
                    .any { kotlin.math.abs(it.toFloatOrNull() ?: 0f) > 1e-4f }
                if (nonZero) return null
                continue
            }
            if (line.startsWith("DOMAIN_MAX", ignoreCase = true)) {
                val nonUnit = line.substringAfter("DOMAIN_MAX").trim().split(WHITESPACE)
                    .any { kotlin.math.abs((it.toFloatOrNull() ?: 1f) - 1f) > 1e-4f }
                if (nonUnit) return null
                continue
            }
            if (line.startsWith("TITLE", ignoreCase = true) ||
                line.startsWith("DOMAIN_", ignoreCase = true) ||
                line.startsWith("LUT_1D_", ignoreCase = true)
            ) continue

            if (size == 0) continue
            val parts = line.split(WHITESPACE)
            if (parts.size < 3) continue
            val r = parts[0].toFloatOrNull() ?: continue
            val g = parts[1].toFloatOrNull() ?: continue
            val b = parts[2].toFloatOrNull() ?: continue
            data.add(r); data.add(g); data.add(b)
        }

        if (size == 0) return null
        val expected = size * size * size * 3
        if (data.size < expected) return null
        return Lut3D(size, FloatArray(expected) { data[it] })
    }
}

/**
 * 정규화된 입력 색 (r,g,b ∈ [0,1])을 LUT 격자에서 삼선형(trilinear) 보간해 [out] 에 채운다.
 * GPU 경로(셰이더)와 동일한 보간 결과를 CPU 에서 재현하기 위한 단일 진리값.
 */
fun Lut3D.sampleTrilinear(r: Float, g: Float, b: Float, out: FloatArray) {
    val scale = size - 1
    val rs = r.coerceIn(0f, 1f) * scale
    val gs = g.coerceIn(0f, 1f) * scale
    val bs = b.coerceIn(0f, 1f) * scale

    val r0 = floor(rs).toInt()
    val g0 = floor(gs).toInt()
    val b0 = floor(bs).toInt()
    val r1 = minOf(r0 + 1, scale)
    val g1 = minOf(g0 + 1, scale)
    val b1 = minOf(b0 + 1, scale)

    val rd = rs - r0
    val gd = gs - g0
    val bd = bs - b0

    // 8개 코너의 시작 인덱스
    fun idx(rr: Int, gg: Int, bb: Int) = ((bb * size + gg) * size + rr) * 3

    var oc = 0
    while (oc < 3) {
        val c000 = data[idx(r0, g0, b0) + oc]
        val c100 = data[idx(r1, g0, b0) + oc]
        val c010 = data[idx(r0, g1, b0) + oc]
        val c110 = data[idx(r1, g1, b0) + oc]
        val c001 = data[idx(r0, g0, b1) + oc]
        val c101 = data[idx(r1, g0, b1) + oc]
        val c011 = data[idx(r0, g1, b1) + oc]
        val c111 = data[idx(r1, g1, b1) + oc]

        val c00 = c000 + (c100 - c000) * rd
        val c10 = c010 + (c110 - c010) * rd
        val c01 = c001 + (c101 - c001) * rd
        val c11 = c011 + (c111 - c011) * rd

        val c0 = c00 + (c10 - c00) * gd
        val c1 = c01 + (c11 - c01) * gd

        out[oc] = c0 + (c1 - c0) * bd
        oc++
    }
}
