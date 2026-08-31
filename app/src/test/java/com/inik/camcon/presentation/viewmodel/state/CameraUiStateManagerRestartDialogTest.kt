package com.inik.camcon.presentation.viewmodel.state

import android.util.Log
import com.inik.camcon.domain.model.PtpTimeoutException
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 재시작 다이얼로그가 연결 성공 뒤에도 남지 않는지 검증한다.
 *
 * 실기(2026-08-31)에서 이런 일이 있었다. 초기화 대기 타이머가 먼저 울려 "앱을 재시작하세요"
 * 다이얼로그를 띄웠는데, 그 5초 뒤 네이티브가 유령 세션을 리셋으로 풀어 연결에 성공했다.
 * 그런데도 다이얼로그는 화면에 그대로 남았다 — 연결 성공 처리가 `isPtpTimeout` 만 내리고
 * 다이얼로그 플래그는 건드리지 않았기 때문이다.
 *
 * **어떤 경로로 성공하든 좀비 다이얼로그가 남지 않는다**는 것이 여기서 지키는 계약이다.
 */
class CameraUiStateManagerRestartDialogTest {

    private lateinit var manager: CameraUiStateManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any(), any()) } returns 0
        manager = CameraUiStateManager()
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `연결에 성공하면 떠 있던 재시작 다이얼로그가 닫힌다`() {
        // 앞선 시도가 실패해 다이얼로그가 떠 있는 상태를 만든다.
        manager.handlePtpTimeout(PtpTimeoutException("PTP timeout"))
        manager.showRestartDialog(true)
        assertTrue(manager.uiState.value.showRestartDialog)

        manager.onConnectionSuccess()

        // 연결됐는데 "재시작하세요"가 떠 있으면 사용자는 멀쩡한 세션을 스스로 끊는다.
        assertFalse(manager.uiState.value.showRestartDialog)
    }

    @Test
    fun `연결에 성공하면 PTP 타임아웃 표시도 함께 내려간다`() {
        manager.handlePtpTimeout(PtpTimeoutException("PTP timeout"))
        assertTrue(manager.uiState.value.isPtpTimeout)

        manager.onConnectionSuccess()

        assertFalse(manager.uiState.value.isPtpTimeout)
        assertTrue(manager.uiState.value.isConnected)
    }

    @Test
    fun `연결 실패는 재시작 다이얼로그를 닫지 않는다`() {
        manager.showRestartDialog(true)

        manager.onConnectionFailure(PtpTimeoutException("PTP timeout"))

        // 실패한 채로 다이얼로그가 사라지면 사용자에게 남는 안내가 없다.
        assertTrue(manager.uiState.value.showRestartDialog)
        assertTrue(manager.uiState.value.isPtpTimeout)
    }
}
