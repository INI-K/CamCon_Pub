package com.inik.camcon.domain.manager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class ErrorHandlingManagerTest {
    @Test
    fun `native error constants keep compatibility`() {
        assertEquals(-10, ErrorHandlingManager.ERROR_USB_TIMEOUT)
        assertEquals(-52, ErrorHandlingManager.ERROR_USB_DETECTION_FAILED)
        assertEquals(-35, ErrorHandlingManager.ERROR_USB_WRITE_FAILED)
        assertEquals(-1000, ErrorHandlingManager.ERROR_APP_RESTART_REQUIRED)
        assertEquals(-2000, ErrorHandlingManager.ERROR_PTP_TIMEOUT_PERSISTENT)
    }
    @Test
    fun `native error event holds expected action`() {
        val event = NativeErrorEvent(
            errorCode = ErrorHandlingManager.ERROR_APP_RESTART_REQUIRED,
            originalMessage = "fatal",
            userFriendlyMessage = "앱 재시작 필요",
            severity = ErrorSeverity.CRITICAL,
            actionRequired = ErrorAction.RESTART_APP
        )
        assertEquals(ErrorAction.RESTART_APP, event.actionRequired)
        assertTrue(event.userFriendlyMessage.contains("재시작"))
    }
}