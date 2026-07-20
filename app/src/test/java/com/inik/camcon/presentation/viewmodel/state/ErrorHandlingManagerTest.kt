package com.inik.camcon.presentation.viewmodel.state

import app.cash.turbine.test
import com.inik.camcon.domain.manager.ErrorAction
import com.inik.camcon.domain.manager.ErrorSeverity
import com.inik.camcon.domain.manager.ErrorType
import com.inik.camcon.domain.manager.fake.FakeNativeErrorCallbackRegistrar
import com.inik.camcon.domain.util.Logger
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ErrorHandlingManager] 의 JNI 에러 코드 → 이벤트 라우팅(ErrorHandlingManager.kt:91 `handleNativeError`) 단위 테스트.
 *
 * 핵심 방어선: USB 물리 분리 계열 코드(-4 `ERROR_USB_DISCONNECTED` / -52 `ERROR_USB_DETECTION_FAILED`)가
 * `usbDisconnectedEvent` 를 방출해 재연결 흐름을 트리거해야 한다. 과거 이 방출이 누락되면 카메라가
 * 조용히 죽는(silent fail) 증상으로 이어졌다. 방출은 순수 분기 로직이므로 Fake 콜백 등록기로
 * 네이티브 통지를 모사해 격리 검증한다.
 *
 * 검증 원칙(프로젝트 규약): 구현 세부가 아닌 **SharedFlow 방출**(usbDisconnectedEvent / nativeErrorEvent /
 * errorEvent)을 Turbine 으로 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ErrorHandlingManagerTest {

    private lateinit var registrar: FakeNativeErrorCallbackRegistrar
    private lateinit var manager: ErrorHandlingManager

    @Before
    fun setUp() {
        registrar = FakeNativeErrorCallbackRegistrar()
        manager = ErrorHandlingManager(
            callbackRegistrar = registrar,
            logger = mockk<Logger>(relaxed = true)
        )
        // 콜백 등록 → registrar.trigger 로 네이티브 통지를 모사할 수 있게 한다.
        manager.initialize()
    }

    // ── usbDisconnectedEvent 라우팅(핵심 방어선) ──

    @Test
    fun `code -4(USB_DISCONNECTED)는 usbDisconnectedEvent를 방출한다`() = runTest {
        manager.usbDisconnectedEvent.test {
            registrar.trigger(-4, "libusb disconnect")
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `code -52(USB_DETECTION_FAILED)도 usbDisconnectedEvent를 방출한다`() = runTest {
        manager.usbDisconnectedEvent.test {
            registrar.trigger(-52, "detection failed")
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `code -10(USB_TIMEOUT)은 usbDisconnectedEvent를 방출하지 않는다`() = runTest {
        manager.usbDisconnectedEvent.test {
            registrar.trigger(-10, "timeout")
            expectNoEvents()
        }
    }

    @Test
    fun `code -35(USB_WRITE_FAILED)는 usbDisconnectedEvent를 방출하지 않는다`() = runTest {
        manager.usbDisconnectedEvent.test {
            registrar.trigger(-35, "write failed")
            expectNoEvents()
        }
    }

    @Test
    fun `알 수 없는 코드는 usbDisconnectedEvent를 방출하지 않는다`() = runTest {
        manager.usbDisconnectedEvent.test {
            registrar.trigger(-999, "unknown")
            expectNoEvents()
        }
    }

    // ── nativeErrorEvent 매핑(심각도/액션) ──

    @Test
    fun `code -4는 nativeErrorEvent를 HIGH + RECONNECT_CAMERA로 방출한다`() = runTest {
        manager.nativeErrorEvent.test {
            registrar.trigger(-4, "disc")
            val event = awaitItem()
            assertEquals(-4, event.errorCode)
            assertEquals(ErrorSeverity.HIGH, event.severity)
            assertEquals(ErrorAction.RECONNECT_CAMERA, event.actionRequired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `code -10은 nativeErrorEvent를 WARNING + RETRY_CONNECTION으로 방출한다`() = runTest {
        manager.nativeErrorEvent.test {
            registrar.trigger(-10, "timeout")
            val event = awaitItem()
            assertEquals(-10, event.errorCode)
            assertEquals(ErrorSeverity.WARNING, event.severity)
            assertEquals(ErrorAction.RETRY_CONNECTION, event.actionRequired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `알 수 없는 코드는 nativeErrorEvent를 MEDIUM + SHOW_ERROR로 방출한다`() = runTest {
        manager.nativeErrorEvent.test {
            registrar.trigger(-999, "unknown")
            val event = awaitItem()
            assertEquals(-999, event.errorCode)
            assertEquals(ErrorSeverity.MEDIUM, event.severity)
            assertEquals(ErrorAction.SHOW_ERROR, event.actionRequired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── errorEvent(공개 ErrorNotifier 표면) ──

    @Test
    fun `emitError는 errorEvent를 type과 message 그대로 방출한다`() = runTest {
        manager.errorEvent.test {
            manager.emitError(ErrorType.OPERATION, "촬영 실패")
            val event = awaitItem()
            assertEquals(ErrorType.OPERATION, event.type)
            assertEquals("촬영 실패", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 콜백 등록 수명 ──

    @Test
    fun `initialize는 콜백을 1회만 등록한다(중복 등록 가드)`() {
        // @Before 에서 이미 1회 등록됨.
        assertEquals(1, registrar.registerCount)
        assertTrue(registrar.isRegistered)

        // 재호출해도 isCallbackRegistered 가드로 재등록하지 않는다.
        manager.initialize()
        assertEquals(1, registrar.registerCount)
    }

    @Test
    fun `cleanup은 콜백을 해제한다`() {
        assertTrue(registrar.isRegistered)

        manager.cleanup()

        assertFalse(registrar.isRegistered)
        assertEquals(1, registrar.unregisterCount)
    }
}
