package com.inik.camcon.presentation.viewmodel

import android.content.Context
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectFailure
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.domain.repository.WifiCapabilityProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.verify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `PtpipViewModel`의 SSH 조치 요청(sshPrompt) 방출 테스트.
 *
 * 원칙(ViewModel 테스트) = 구현 세부가 아니라 StateFlow 방출을 검증한다. 여기서는
 * "데이터소스가 방출한 실패 사유가 어떤 다이얼로그 요청으로 바뀌는가"와
 * "사용자 조치가 저장·재연결로 이어지는가"를 본다.
 *
 * 보안 계약도 함께 고정한다 — 지문 불일치에는 신뢰 경로가 없다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PtpipViewModelSshPromptTest {

    private lateinit var ptpipRepository: PtpipRepository
    private lateinit var preferencesRepository: PtpipPreferencesRepository
    private lateinit var globalManager: CameraConnectionGlobalManager
    private lateinit var handoffTracker: ConnectionHandoffTracker
    private lateinit var connectionHelper: PtpipConnectionHelper
    private lateinit var discoveryHelper: PtpipDiscoveryHelper
    private lateinit var debugHelper: PtpipDebugHelper
    private lateinit var wifiCapabilityProvider: WifiCapabilityProvider
    private lateinit var appContext: Context

    private lateinit var connectFailure: MutableStateFlow<PtpipConnectFailure?>
    private lateinit var hostKeyFingerprint: MutableStateFlow<String?>
    private lateinit var connectingCamera: MutableStateFlow<PtpipCamera?>

    private val testDispatcher = UnconfinedTestDispatcher()

    private val camera = PtpipCamera(
        ipAddress = "192.168.49.10",
        port = 15740,
        name = "ILCE-7M5"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        ptpipRepository = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        globalManager = mockk(relaxed = true)
        handoffTracker = mockk(relaxed = true)
        connectionHelper = mockk(relaxed = true)
        discoveryHelper = mockk(relaxed = true)
        debugHelper = mockk(relaxed = true)
        wifiCapabilityProvider = mockk(relaxed = true)
        appContext = mockk(relaxed = true)

        connectFailure = MutableStateFlow(null)
        hostKeyFingerprint = MutableStateFlow(null)
        connectingCamera = MutableStateFlow(null)

        every { ptpipRepository.connectionState } returns
            MutableStateFlow(PtpipConnectionState.DISCONNECTED)
        every { ptpipRepository.connectionProgressMessage } returns MutableStateFlow("")
        every { ptpipRepository.discoveredCameras } returns MutableStateFlow(emptyList())
        every { ptpipRepository.cameraInfo } returns MutableStateFlow<PtpipCameraInfo?>(null)
        every { ptpipRepository.wifiNetworkState } returns
            MutableStateFlow(WifiNetworkState(false, false, null, null))
        every { ptpipRepository.connectionLostMessage } returns MutableStateFlow<String?>(null)
        every { ptpipRepository.activeConnectionMethod } returns
            MutableStateFlow<ConnectionMethod?>(null)
        every { ptpipRepository.manualIp } returns MutableStateFlow("")
        every { ptpipRepository.connectFailure } returns connectFailure
        every { ptpipRepository.sshHostKeyFingerprint } returns hostKeyFingerprint
        every { ptpipRepository.connectingCamera } returns connectingCamera

        every { globalManager.globalConnectionState } returns
            MutableStateFlow(GlobalCameraConnectionState())
        every { globalManager.activeConnectionType } returns
            MutableStateFlow<CameraConnectionType?>(null)

        every { preferencesRepository.isPtpipEnabled } returns flowOf(true)
        every { preferencesRepository.isAutoReconnectEnabled } returns flowOf(false)

        // 연결은 실패로 끝나야 실패 사유 방출 경로가 성립한다(SSH 실패는 false 반환 규약).
        coEvery { connectionHelper.connectToCamera(any(), any()) } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): PtpipViewModel = PtpipViewModel(
        appContext = appContext,
        ptpipRepository = ptpipRepository,
        preferencesRepository = preferencesRepository,
        globalManager = globalManager,
        handoffTracker = handoffTracker,
        connectionHelper = connectionHelper,
        discoveryHelper = discoveryHelper,
        debugHelper = debugHelper,
        wifiCapabilityProvider = wifiCapabilityProvider,
        ioDispatcher = testDispatcher,
    )

    /**
     * 재연결이 시작되는 순간의 관찰값.
     *
     * 데이터소스는 새 시도를 시작할 때 실패 사유를 null로 되돌리고, 그 null 방출만으로도
     * `sshPrompt`가 비워진다. 그래서 조치 뒤에 `sshPrompt`가 null인 것만 보면 **조치 함수가
     * 다이얼로그를 닫았는지**를 확인할 수 없다(시도 시작의 초기화가 대신 지워 줘도 통과한다).
     * 재연결 진입 시점의 값을 따로 붙잡아 두 원인을 갈라낸다.
     */
    private class RetryProbe {
        var reconnected = false
        var promptAtReconnect: SshConnectPrompt? = null
    }

    /**
     * 연결을 한 번 시도해 대상 카메라를 확정하고, 그 시도가 [failure] 로 끝나게 한다.
     *
     * 데이터소스의 실제 순서를 그대로 흉내 낸다: 시도 시작에 사유를 null로 되돌리고, 실패 사유를
     * 세팅한 **뒤에** false를 돌려준다. 이 순서가 어긋나면 "일반 실패 스낵바를 억제할지"를
     * 판정하는 지점이 실기와 다른 조건에서 검증되어 회귀를 놓친다.
     *
     * 두 번째 시도부터는 성공시킨다. 사용자 조치 뒤의 재연결까지 실패하면 조치 자체를 검증할 수 없다.
     */
    private fun PtpipViewModel.attemptAndFailWith(
        failure: PtpipConnectFailure,
        fingerprint: String? = null
    ): RetryProbe {
        val viewModel = this
        val probe = RetryProbe()
        var firstAttempt = true
        coEvery { connectionHelper.connectToCamera(camera, any()) } coAnswers {
            if (firstAttempt) {
                firstAttempt = false
                connectFailure.value = null
                hostKeyFingerprint.value = null
                hostKeyFingerprint.value = fingerprint
                connectFailure.value = failure
                false
            } else {
                // 사유를 초기화하기 **전에** 붙잡아야 조치 함수의 효과와 구분된다.
                probe.reconnected = true
                probe.promptAtReconnect = viewModel.sshPrompt.value
                connectFailure.value = null
                hostKeyFingerprint.value = null
                true
            }
        }
        connectToCamera(camera)
        return probe
    }

    @Test
    fun `자격증명 미보유 실패는 입력 다이얼로그 요청으로 바뀐다`() = runTest {
        val viewModel = createViewModel()
        assertNull(viewModel.sshPrompt.value)

        viewModel.attemptAndFailWith(PtpipConnectFailure.SSH_CREDENTIALS_REQUIRED)

        val prompt = viewModel.sshPrompt.value
        assertTrue(prompt is SshConnectPrompt.Credentials)
        prompt as SshConnectPrompt.Credentials
        assertEquals(SshCredentialsPromptReason.REQUIRED, prompt.reason)
        assertEquals(camera, prompt.camera)
    }

    @Test
    fun `인증 거부 실패는 재입력 사유로 구분된다`() = runTest {
        val viewModel = createViewModel()

        viewModel.attemptAndFailWith(PtpipConnectFailure.SSH_AUTH_FAILED)

        val prompt = viewModel.sshPrompt.value as SshConnectPrompt.Credentials
        assertEquals(SshCredentialsPromptReason.AUTH_FAILED, prompt.reason)
    }

    @Test
    fun `자격증명 저장에 성공하면 다이얼로그를 닫고 같은 카메라로 다시 연결한다`() = runTest {
        val viewModel = createViewModel()
        coEvery { ptpipRepository.saveSshCredentials(camera, "sony", "pw") } returns true

        val probe = viewModel.attemptAndFailWith(PtpipConnectFailure.SSH_CREDENTIALS_REQUIRED)
        viewModel.submitSshCredentials("sony", "pw")

        assertNull(viewModel.sshPrompt.value)
        // 재연결 진입 시점에 이미 비어 있어야 한다. 시도 시작의 사유 초기화가 대신 지워 준 것이
        // 아니라 submitSshCredentials가 직접 닫았다는 증거다.
        assertTrue(probe.reconnected)
        assertNull(probe.promptAtReconnect)
        coVerify(exactly = 1) { ptpipRepository.saveSshCredentials(camera, "sony", "pw") }
        // 최초 시도 1회 + 저장 후 재연결 1회.
        coVerify(exactly = 2) { connectionHelper.connectToCamera(camera, false) }
    }

    @Test
    fun `자격증명을 안전하게 저장할 수 없으면 안내 상태로 남고 재연결하지 않는다`() = runTest {
        val viewModel = createViewModel()
        coEvery { ptpipRepository.saveSshCredentials(camera, "sony", "pw") } returns false

        viewModel.attemptAndFailWith(PtpipConnectFailure.SSH_CREDENTIALS_REQUIRED)
        viewModel.submitSshCredentials("sony", "pw")

        val prompt = viewModel.sshPrompt.value as SshConnectPrompt.Credentials
        assertEquals(SshCredentialsPromptReason.STORE_FAILED, prompt.reason)
        // 재연결은 일어나지 않는다 — 저장이 안 됐으니 결과가 같다.
        coVerify(exactly = 1) { connectionHelper.connectToCamera(camera, false) }
    }

    @Test
    fun `호스트키 미검증은 지문과 함께 대조 요청으로 바뀐다`() = runTest {
        val viewModel = createViewModel()

        viewModel.attemptAndFailWith(
            PtpipConnectFailure.SSH_HOST_KEY_UNVERIFIED,
            fingerprint = "SHA256:abc"
        )

        val prompt = viewModel.sshPrompt.value as SshConnectPrompt.HostKeyTrust
        assertEquals("SHA256:abc", prompt.fingerprint)
        assertEquals(camera, prompt.camera)
    }

    @Test
    fun `사용자가 지문을 신뢰하면 저장 후 다시 연결한다`() = runTest {
        val viewModel = createViewModel()

        val probe = viewModel.attemptAndFailWith(
            PtpipConnectFailure.SSH_HOST_KEY_UNVERIFIED,
            fingerprint = "SHA256:abc"
        )
        viewModel.trustSshHostKey()

        assertNull(viewModel.sshPrompt.value)
        assertTrue(probe.reconnected)
        assertNull(probe.promptAtReconnect)
        coVerify(exactly = 1) { ptpipRepository.trustSshHostKey(camera, "SHA256:abc") }
        coVerify(exactly = 2) { connectionHelper.connectToCamera(camera, false) }
    }

    @Test
    fun `지문 불일치에는 신뢰 경로가 없다`() = runTest {
        val viewModel = createViewModel()

        viewModel.attemptAndFailWith(
            PtpipConnectFailure.SSH_HOST_KEY_MISMATCH,
            fingerprint = "SHA256:changed"
        )
        // 불일치 상태에서 신뢰를 호출해도 저장되지 않아야 한다(TOFU 보호의 핵심).
        viewModel.trustSshHostKey()

        val prompt = viewModel.sshPrompt.value as SshConnectPrompt.HostKeyMismatch
        assertEquals("SHA256:changed", prompt.fingerprint)
        coVerify(exactly = 0) { ptpipRepository.trustSshHostKey(any(), any()) }
        coVerify(exactly = 1) { connectionHelper.connectToCamera(camera, false) }
    }

    @Test
    fun `지문을 얻지 못한 미검증 실패는 대조 다이얼로그를 띄우지 않는다`() = runTest {
        val viewModel = createViewModel()

        viewModel.attemptAndFailWith(
            PtpipConnectFailure.SSH_HOST_KEY_UNVERIFIED,
            fingerprint = null
        )

        // 보여 줄 지문이 없으면 사용자가 대조할 수 없다. 대조 없는 신뢰 경로를 만들지 않는다.
        assertNull(viewModel.sshPrompt.value)
    }

    @Test
    fun `페어링 대기는 SSH 다이얼로그를 띄우지 않는다`() = runTest {
        val viewModel = createViewModel()

        viewModel.attemptAndFailWith(PtpipConnectFailure.PAIRING_PENDING)

        assertNull(viewModel.sshPrompt.value)
    }

    @Test
    fun `터널 실패는 재시도 안내로 바뀌고 재시도가 같은 카메라를 다시 연결한다`() = runTest {
        val viewModel = createViewModel()

        val probe = viewModel.attemptAndFailWith(PtpipConnectFailure.SSH_TUNNEL_FAILED)
        assertEquals(SshConnectPrompt.TunnelFailed(camera), viewModel.sshPrompt.value)

        viewModel.retrySshConnect()

        assertNull(viewModel.sshPrompt.value)
        assertTrue(probe.reconnected)
        assertNull(probe.promptAtReconnect)
        coVerify(exactly = 2) { connectionHelper.connectToCamera(camera, false) }
    }

    /**
     * 실기 회귀 (2026-08-29) — 다이얼로그를 못 띄우는 SSH 실패가 스낵바까지 삼켜 연결이 조용히
     * 실패했다. 로그: "SSH 실패(SSH_HOST_KEY_UNVERIFIED)를 받았으나 연결 대상 카메라가 없어…".
     *
     * 안내 채널이 둘 다 비는 상태를 금지한다 — 다이얼로그가 뜨거나, 최소한 실패 메시지는 남아야 한다.
     */
    @Test
    fun `다이얼로그를 못 띄우는 SSH 실패는 일반 실패 안내로 대체된다`() = runTest {
        val viewModel = createViewModel()
        every { appContext.getString(any<Int>()) } returns "카메라 연결에 실패했습니다"

        // 지문이 없어 대조 다이얼로그를 만들 수 없는 호스트키 미검증 실패.
        viewModel.attemptAndFailWith(
            PtpipConnectFailure.SSH_HOST_KEY_UNVERIFIED,
            fingerprint = null
        )

        assertNull(viewModel.sshPrompt.value)
        // 다이얼로그가 없으면 스낵바라도 떠야 한다. 둘 다 비면 사용자는 아무것도 보지 못한다.
        assertEquals("카메라 연결에 실패했습니다", viewModel.errorMessage.value)
    }

    @Test
    fun `다이얼로그를 띄운 SSH 실패는 일반 실패 안내를 겹치지 않는다`() = runTest {
        val viewModel = createViewModel()
        every { appContext.getString(any<Int>()) } returns "카메라 연결에 실패했습니다"

        viewModel.attemptAndFailWith(
            PtpipConnectFailure.SSH_HOST_KEY_UNVERIFIED,
            fingerprint = "SHA256:abc"
        )

        assertTrue(viewModel.sshPrompt.value is SshConnectPrompt.HostKeyTrust)
        assertNull(viewModel.errorMessage.value)
    }

    /**
     * 실기 회귀 (2026-08-29) — 자동 재연결·Wi-Fi 폴링·자동 연결은 ViewModel을 거치지 않고 연결을
     * 시작하므로 ViewModel이 추적하는 대상은 비어 있다. 그 경로의 실패에서 다이얼로그가 뜨지 않아
     * 사용자가 아무 안내도 받지 못했다.
     *
     * 여기서는 `connectToCamera`를 **한 번도 부르지 않고** 데이터 레이어만 실패를 방출한다.
     */
    @Test
    fun `ViewModel이 시작하지 않은 연결의 실패에도 다이얼로그가 뜬다`() = runTest {
        val viewModel = createViewModel()

        // 배경 경로가 연결을 시작했다 — 데이터 레이어만 대상을 안다.
        connectingCamera.value = camera
        hostKeyFingerprint.value = "SHA256:abc"
        connectFailure.value = PtpipConnectFailure.SSH_HOST_KEY_UNVERIFIED

        val prompt = viewModel.sshPrompt.value as SshConnectPrompt.HostKeyTrust
        assertEquals(camera, prompt.camera)
        assertEquals("SHA256:abc", prompt.fingerprint)
        // 화면이 시작한 연결이 아니어도 조치는 그 카메라로 이어져야 한다.
        coVerify(exactly = 0) { connectionHelper.connectToCamera(any(), any()) }
    }

    /**
     * 데이터 레이어는 실패한 SSH 시도를 일정 시간 억제한다(자동 경로의 4초 폭주가 카메라를 세션
     * 잠금으로 모는 것을 막는다). 사용자 조치 뒤의 재연결이 그 억제를 먼저 풀지 않으면, 방금 저장한
     * 자격증명으로 시도한 연결이 같은 사유로 되돌아와 입력이 무시된 것처럼 보인다.
     */
    @Test
    fun `사용자 조치는 재연결 전에 SSH 시도 억제를 푼다`() = runTest {
        val viewModel = createViewModel()
        coEvery { ptpipRepository.saveSshCredentials(camera, "sony", "pw") } returns true

        viewModel.attemptAndFailWith(PtpipConnectFailure.SSH_CREDENTIALS_REQUIRED)
        viewModel.submitSshCredentials("sony", "pw")

        coVerifyOrder {
            ptpipRepository.saveSshCredentials(camera, "sony", "pw")
            ptpipRepository.clearSshAttemptThrottle()
            connectionHelper.connectToCamera(camera, false)
        }
    }

    @Test
    fun `지문 신뢰와 터널 재시도도 억제를 푼다`() = runTest {
        val trustVm = createViewModel()
        trustVm.attemptAndFailWith(
            PtpipConnectFailure.SSH_HOST_KEY_UNVERIFIED,
            fingerprint = "SHA256:abc"
        )
        trustVm.trustSshHostKey()
        verify(atLeast = 1) { ptpipRepository.clearSshAttemptThrottle() }

        val retryVm = createViewModel()
        retryVm.attemptAndFailWith(PtpipConnectFailure.SSH_TUNNEL_FAILED)
        retryVm.retrySshConnect()
        verify(atLeast = 2) { ptpipRepository.clearSshAttemptThrottle() }
    }

    @Test
    fun `취소는 다이얼로그만 닫고 저장소를 건드리지 않는다`() = runTest {
        val viewModel = createViewModel()

        viewModel.attemptAndFailWith(PtpipConnectFailure.SSH_CREDENTIALS_REQUIRED)
        viewModel.dismissSshPrompt()

        assertNull(viewModel.sshPrompt.value)
        coVerify(exactly = 0) { ptpipRepository.saveSshCredentials(any(), any(), any()) }
        coVerify(exactly = 0) { ptpipRepository.trustSshHostKey(any(), any()) }
        // 취소는 조치가 아니므로 억제를 풀지 않는다 — 풀면 자동 경로의 폭주 차단이 무너진다.
        verify(exactly = 0) { ptpipRepository.clearSshAttemptThrottle() }
    }
}
