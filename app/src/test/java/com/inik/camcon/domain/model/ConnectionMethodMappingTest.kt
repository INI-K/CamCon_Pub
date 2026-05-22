package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ConnectionMethod ↔ NikonConnectionMode 매핑 회귀 테스트.
 *
 * architect 설계 §1.1: STA_PHONE_HOTSPOT은 토폴로지상 STA와 동일하므로
 * Nikon 인증 분기는 STA_MODE로 흘러간다. AP만 AP_MODE.
 *
 * 매핑 함수 `ConnectionMethod.toNikonConnectionMode()`는 도메인 레이어에서
 * pairing-code 인증이 필요한지 결정하는 단일 진실 지점이 된다.
 */
class ConnectionMethodMappingTest {

    @Test
    fun `AP maps to Nikon AP_MODE`() {
        assertEquals(NikonConnectionMode.AP_MODE, ConnectionMethod.AP.toNikonConnectionMode())
    }

    @Test
    fun `STA_ROUTER maps to Nikon STA_MODE`() {
        assertEquals(
            NikonConnectionMode.STA_MODE,
            ConnectionMethod.STA_ROUTER.toNikonConnectionMode()
        )
    }

    @Test
    fun `STA_PHONE_HOTSPOT maps to Nikon STA_MODE`() {
        // 폰 핫스팟도 토폴로지상 STA — pairing-code 인증 동일하게 필요.
        assertEquals(
            NikonConnectionMode.STA_MODE,
            ConnectionMethod.STA_PHONE_HOTSPOT.toNikonConnectionMode()
        )
    }
}
