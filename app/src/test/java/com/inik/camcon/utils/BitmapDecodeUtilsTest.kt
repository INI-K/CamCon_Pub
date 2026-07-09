package com.inik.camcon.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BitmapDecodeUtils.calculateInSampleSize] 순수 로직 회귀.
 *
 * 이 함수는 `PhotoDownloadManager`/`PhotoImageManager` 의 기존 다운샘플 로직과 바이트 동일해야
 * 하는 인프라 헬퍼다. 실제 파일 디코딩([decodeSampled])은 [android.graphics.BitmapFactory]
 * 네이티브 의존이라 JVM 단위 테스트로 검증 불가 — 여기서는 순수 정수 산술인
 * [calculateInSampleSize] 경계만 락인한다. Android 클래스를 만지지 않으므로 Robolectric 불필요.
 *
 * 구현 형태 메모: Google 공식 샘플과 동일하게 `halfWidth/halfHeight` 기준으로 루프를 돈다
 * (`while (halfH/s >= reqH && halfW/s >= reqW) s*=2`). 즉 결과 s 는 두 축 모두 req 이상으로
 * 남기는 최대 2의 거듭제곱이다. "full 치수(height/s)" 변형이 아니므로 6000×4000·req800 은
 * 8 이 아니라 **4** 다(inSampleSize=8 이면 500×750 이 되어 height 가 req 800 미만).
 */
class BitmapDecodeUtilsTest {

    @Test
    fun `6000x4000 을 req 800 으로 줄이면 inSampleSize 4`() {
        // half=3000x2000. s=4 → 1500x1000(둘 다 >=800). s=8 → 750x500(height<800)이라 stop.
        assertEquals(4, BitmapDecodeUtils.calculateInSampleSize(6000, 4000, 800, 800))
    }

    @Test
    fun `8000x8000 을 req 1000 으로 줄이면 inSampleSize 8`() {
        // half=4000. s=8 → 1000x1000(정확히 req). s=16 → 500<1000 stop.
        assertEquals(8, BitmapDecodeUtils.calculateInSampleSize(8000, 8000, 1000, 1000))
    }

    @Test
    fun `원본이 req 와 정확히 같으면 inSampleSize 1`() {
        // height>req·width>req 둘 다 거짓이라 if 진입 자체를 안 함.
        assertEquals(1, BitmapDecodeUtils.calculateInSampleSize(800, 800, 800, 800))
    }

    @Test
    fun `원본이 req 보다 작으면 다운샘플 없이 1`() {
        assertEquals(1, BitmapDecodeUtils.calculateInSampleSize(400, 300, 800, 800))
    }

    @Test
    fun `한 축만 req 초과여도 작은 축이 이미 req 미만이면 1`() {
        // width 6000>800 이라 진입하지만 halfHeight 250 이 첫 루프에서 req 800 미만 → 즉시 stop.
        assertEquals(1, BitmapDecodeUtils.calculateInSampleSize(6000, 500, 800, 800))
    }

    @Test
    fun `비대칭 원본은 제한 축이 결과를 결정한다`() {
        // 4000x1000, req 500. half=2000x500. s=2 → 1000x250(height<500)이라 s=2 에서 stop.
        assertEquals(2, BitmapDecodeUtils.calculateInSampleSize(4000, 1000, 500, 500))
    }

    @Test
    fun `정확한 2배수 경계 1600 을 req 800 으로 줄이면 2`() {
        // half=800. s=2 → 400<800 이라 s=2 에서 stop.
        assertEquals(2, BitmapDecodeUtils.calculateInSampleSize(1600, 1600, 800, 800))
    }

    @Test
    fun `원본 0 은 방어적으로 1 을 반환한다`() {
        // 0>reqHeight·0>reqWidth 둘 다 거짓 → if 미진입. (decodeSampled 는 outWidth<=0 를 null 로 가드)
        assertEquals(1, BitmapDecodeUtils.calculateInSampleSize(0, 0, 800, 800))
    }

    @Test
    fun `원본 음수 는 방어적으로 1 을 반환한다`() {
        assertEquals(1, BitmapDecodeUtils.calculateInSampleSize(-100, -100, 800, 800))
    }

    @Test
    fun `결과는 항상 2의 거듭제곱이다`() {
        val widths = intArrayOf(400, 1000, 1600, 3000, 6000, 8000, 12000)
        val heights = intArrayOf(300, 1000, 1600, 2000, 4000, 8000, 9000)
        for (w in widths) for (h in heights) {
            val s = BitmapDecodeUtils.calculateInSampleSize(w, h, 1000, 1000)
            assertTrue("s>=1 이어야 함 (w=$w h=$h): $s", s >= 1)
            assertTrue("s 는 2의 거듭제곱이어야 함 (w=$w h=$h): $s", (s and (s - 1)) == 0)
        }
    }
}
