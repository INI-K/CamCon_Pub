package com.inik.camcon.presentation.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min

/**
 * 로컬 대비 정규화 Modified-Laplacian 기반 포커스 피킹 프로세서.
 *
 * 표준 포커스 피킹은 1차 미분(Sobel) 엣지가 아니라 **2차 미분(Laplacian)** 으로 "선명도(고주파)"를
 * 측정하고, **로컬 대비로 정규화**해 엣지의 절대 대비(밝기 차)를 제거한다. 이렇게 해야 흐릿하지만
 * 대비가 큰 경계가 더는 피킹되지 않고, *실제로 초점이 맞은 미세 디테일*만 강조된다.
 * (근거: Nayar Sum-Modified-Laplacian, Magic Lantern peak_d2xy(1차→2차미분 교체), darktable 이중거리 gradient)
 *
 * 파이프라인: 다운샘플 → 휘도 → 3-tap 이항 사전 스무딩 → Modified-Laplacian(축별 abs 합) →
 * 로컬평균으로 정규화 → 정규화 비율 임계 → [color] 오버레이 → nearest upscale.
 *
 * 호출자가 책임:
 * - 반드시 IO 디스패처에서 실행. 비용이 크다.
 * - 반환 비트맵 사용 후 recycle.
 *
 * @param source 원본 라이브뷰 비트맵 (recycled 이면 null 반환)
 * @param threshold **정규화된 2차미분 비율**(무차원) 임계값. 8~20 권장, 기본 12. 클수록 더 선명한 곳만 표시.
 *                  ⚠️ 구버전 Sobel magnitude 임계(30~100, 옛 기본 60)와 의미가 다르다 — 옛 값을 넘기면
 *                  거의 전부 칠해지거나 텅 빈다.
 * @param color 오버레이 색상 (ARGB)
 * @param downsample 1 이상. 기본 2. 키우지 말 것 — 미세 고주파가 사라져 초점 선택성이 떨어진다.
 * @return 초점 맞은 디테일에 [color] 오버레이가 입혀진 새 ARGB_8888 비트맵. 입력은 변경하지 않음.
 */
fun applyFocusPeaking(
    source: Bitmap,
    threshold: Int = 12,
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

    // 1) 다운샘플 + 휘도(BT.601, 고정소수점 77/150/29>>8) 추출
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    val lum = IntArray(dw * dh)
    var dyIdx = 0
    var sy = 0
    while (dyIdx < dh) {
        var dxIdx = 0
        var sx = 0
        val rowOffset = sy * w
        val dRow = dyIdx * dw
        while (dxIdx < dw) {
            val p = pixels[rowOffset + sx]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            lum[dRow + dxIdx] = (77 * r + 150 * g + 29 * b) shr 8
            dxIdx++
            sx += ds
        }
        dyIdx++
        sy += ds
    }

    // 2) 사전 스무딩 — separable 3-tap 이항 [1,2,1]/4 (가로→세로). 가장자리는 clamp.
    // 2차 미분이 노이즈를 증폭하고 정규화가 암부에서 그걸 또 키우므로, 이 단계는 필수다.
    val tmp = IntArray(dw * dh)
    run {
        var ty = 0
        while (ty < dh) {
            val row = ty * dw
            var tx = 0
            while (tx < dw) {
                val l = lum[row + (if (tx > 0) tx - 1 else tx)]
                val c = lum[row + tx]
                val r = lum[row + (if (tx < dw - 1) tx + 1 else tx)]
                tmp[row + tx] = (l + 2 * c + r + 2) shr 2
                tx++
            }
            ty++
        }
    }
    val lumS = IntArray(dw * dh)
    run {
        var ty = 0
        while (ty < dh) {
            val row = ty * dw
            val up = (if (ty > 0) ty - 1 else ty) * dw
            val dn = (if (ty < dh - 1) ty + 1 else ty) * dw
            var tx = 0
            while (tx < dw) {
                lumS[row + tx] = (tmp[up + tx] + 2 * tmp[row + tx] + tmp[dn + tx] + 2) shr 2
                tx++
            }
            ty++
        }
    }

    // 3) Modified-Laplacian(축별 abs 합) + 로컬 대비 정규화 + 임계 — 한 패스에 융합.
    //   ml   = |2c - L - R| + |2c - U - D|   (선명할수록 큼; 흐릿한 고대비 경계는 곡률이 작아 작음)
    //   norm = ml*256 / (localAvg + EPS)     (대비 무차원화 → 고대비 흐림 경계가 임계 아래로 떨어짐)
    //   raw ml 바닥(ML_FLOOR)으로 암부/평탄부 노이즈 폭주를 차단.
    val mask = BooleanArray(dw * dh)
    var y = 1
    while (y < dh - 1) {
        val rowM = y * dw
        val rowU = (y - 1) * dw
        val rowD = (y + 1) * dw
        var x = 1
        while (x < dw - 1) {
            val c = lumS[rowM + x]
            val left = lumS[rowM + x - 1]
            val right = lumS[rowM + x + 1]
            val up = lumS[rowU + x]
            val down = lumS[rowD + x]
            val lapX = abs(2 * c - left - right)
            val lapY = abs(2 * c - up - down)
            val ml = lapX + lapY
            if (ml >= ML_FLOOR) {
                val localAvg = (c + left + right + up + down) / 5
                val norm = (ml * NORM_SCALE) / (localAvg + EPS)
                if (norm >= threshold) {
                    mask[rowM + x] = true
                }
            }
            x++
        }
        y++
    }

    // 4) 입력 픽셀을 복사한 뒤 mask 영역을 nearest upscale 로 색칠. (원본 source 는 건드리지 않음)
    val result = pixels
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

/** 분모 가드(암부 divide-by-tiny 폭주 방지). ~255의 6%. */
private const val EPS = 16

/** raw Modified-Laplacian 바닥값 — 암부/평탄부 노이즈를 정규화가 빨갛게 키우는 것을 차단. */
private const val ML_FLOOR = 24

/** 정규화 고정소수점 배율(비율을 정수 범위로 유지). */
private const val NORM_SCALE = 256
