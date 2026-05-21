package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
