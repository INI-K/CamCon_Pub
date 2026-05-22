package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ConnectionMethod 회귀 테스트.
 *
 * 폰 핫스팟 STA(STA_PHONE_HOTSPOT) 모드를 1급 시나리오로 노출하는 enum.
 * NikonConnectionMode(AP/STA/UNKNOWN)와 직교축이며, "사용자 시나리오"를 표현한다.
 */
class ConnectionMethodTest {

    @Test
    fun `AP is not STA`() {
        assertFalse(ConnectionMethod.AP.isSta)
    }

    @Test
    fun `STA_ROUTER is STA`() {
        assertTrue(ConnectionMethod.STA_ROUTER.isSta)
    }

    @Test
    fun `STA_PHONE_HOTSPOT is STA`() {
        assertTrue(ConnectionMethod.STA_PHONE_HOTSPOT.isSta)
    }

    @Test
    fun `enum values cover three methods`() {
        assertEquals(3, ConnectionMethod.values().size)
    }
}
