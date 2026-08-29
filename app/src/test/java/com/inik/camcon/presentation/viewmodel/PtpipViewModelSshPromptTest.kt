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

    /** 연결을 한 번 시도해 조치 대상 카메라를 확정한 뒤 실패 사유를 방출한다. */
    private fun PtpipViewModel.attemptAndFailWith(
        failure: PtpipConnectFailure,
        fingerprint: String? = null
    ) {
        connectToCamera(camera)
        hostKeyFingerprint.value = fingerprint
        connectFailure.value = failure
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

        viewModel.attemptAndFailWith(PtpipConnectFailure.SSH_CREDENTIALS_REQUIRED)
        viewModel.submitSshCredentials("sony", "pw")

        assertNull(viewModel.sshPrompt.value)
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

        viewModel.attemptAndFailWith(
            PtpipConnectFailure.SSH_HOST_KEY_UNVERIFIED,
            fingerprint = "SHA256:abc"
        )
        viewModel.trustSshHostKey()

        assertNull(viewModel.sshPrompt.value)
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

        viewModel.attemptAndFailWith(PtpipConnectFailure.SSH_TUNNEL_FAILED)
        assertEquals(SshConnectPrompt.TunnelFailed(camera), viewModel.sshPrompt.value)

        viewModel.retrySshConnect()

        assertNull(viewModel.sshPrompt.value)
        coVerify(exactly = 2) { connectionHelper.connectToCamera(camera, false) }
    }

    @Test
    fun `취소는 다이얼로그만 닫고 저장소를 건드리지 않는다`() = runTest {
        val viewModel = createViewModel()

        viewModel.attemptAndFailWith(PtpipConnectFailure.SSH_CREDENTIALS_REQUIRED)
        viewModel.dismissSshPrompt()

        assertNull(viewModel.sshPrompt.value)
        coVerify(exactly = 0) { ptpipRepository.saveSshCredentials(any(), any(), any()) }
        coVerify(exactly = 0) { ptpipRepository.trustSshHostKey(any(), any()) }
    }
}
