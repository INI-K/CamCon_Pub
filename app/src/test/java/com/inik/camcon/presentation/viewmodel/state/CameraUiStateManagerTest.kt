package com.inik.camcon.presentation.viewmodel.state

import android.util.Log
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSupports
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.TransferQueueState
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CameraUiStateManager가 CameraStateObserver 인터페이스를 올바르게 구현하는지 확인.
 * CameraStateObserver 인터페이스 계약 테스트 (C1 검증)
 */
class CameraUiStateManagerTest {

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

    // --- CameraStateObserver 인터페이스 계약 ---

    @Test
    fun `CameraUiStateManager는 CameraStateObserver를 구현`() {
        // CameraUiStateManager가 CameraStateObserver 인터페이스를 구현하는지 타입 체크
        assertTrue(manager is CameraStateObserver)
    }

    // --- updateCameraInitialization ---

    @Test
    fun `updateCameraInitialization true시 isCameraInitializing true`() {
        // Given: 초기 상태
        assertFalse(manager.uiState.value.isCameraInitializing)

        // When
        manager.updateCameraInitialization(true)

        // Then
        assertTrue(manager.uiState.value.isCameraInitializing)
    }

    @Test
    fun `updateCameraInitialization false시 isCameraInitializing false`() {
        // Given: 초기화 중 상태
        manager.updateCameraInitialization(true)
        assertTrue(manager.uiState.value.isCameraInitializing)

        // When
        manager.updateCameraInitialization(false)

        // Then
        assertFalse(manager.uiState.value.isCameraInitializing)
    }

    // --- updateCameraCapabilities ---

    @Test
    fun `updateCameraCapabilities로 카메라 기능 정보 업데이트`() {
        // Given
        val capabilities = createCapabilities("Canon EOS R5")

        // When
        manager.updateCameraCapabilities(capabilities)

        // Then
        assertEquals(capabilities, manager.uiState.value.cameraCapabilities)
    }

    @Test
    fun `updateCameraCapabilities null시 에러 메시지 설정`() {
        // Given: 연결 상태에서
        manager.updateConnectionState(true)

        // When
        manager.updateCameraCapabilities(null)

        // Then
        assertNull(manager.uiState.value.cameraCapabilities)
        assertEquals("카메라 기능 정보를 가져올 수 없음", manager.uiState.value.error)
    }

    // --- showCameraStatusCheckDialog ---

    @Test
    fun `showCameraStatusCheckDialog true시 다이얼로그 표시`() {
        manager.showCameraStatusCheckDialog(true)
        assertTrue(manager.uiState.value.showCameraStatusCheckDialog)
    }

    @Test
    fun `showCameraStatusCheckDialog false시 다이얼로그 숨김`() {
        manager.showCameraStatusCheckDialog(true)
        manager.showCameraStatusCheckDialog(false)
        assertFalse(manager.uiState.value.showCameraStatusCheckDialog)
    }

    // --- updateCameraAbilities ---

    @Test
    fun `updateCameraAbilities로 동적 UI 제어 - 완전 제어 가능 카메라`() {
        // Given: 완전한 기능을 지원하는 카메라
        val abilities = createAbilities(
            model = "Nikon Z6",
            captureImage = true,
            capturePreview = true,
            config = true,
            captureVideo = true,
            delete = true,
            putFile = false
        )

        // When
        manager.updateCameraAbilities(abilities)

        // Then
        val state = manager.uiState.value
        assertTrue(state.showCaptureButton)
        assertTrue(state.showLiveViewTab)
        assertTrue(state.showVideoButton)
        assertTrue(state.showConfigTab)
        assertTrue(state.showDeleteButton)
        assertFalse(state.showUploadButton)
        assertNull(state.cameraFunctionLimitation)
        assertTrue(state.showNikonStaWarning) // Nikon이므로 STA 경고
        assertEquals("Nikon Z6", state.connectedCameraModel)
        assertEquals("Nikon", state.connectedCameraManufacturer)
    }

    @Test
    fun `updateCameraAbilities - 다운로드 전용 카메라시 제한 메시지`() {
        // Given: 다운로드만 지원하는 카메라
        val abilities = createAbilities(
            model = "Generic Camera",
            captureImage = false,
            capturePreview = false,
            config = false,
            captureVideo = false,
            delete = false,
            putFile = false
        )

        // When
        manager.updateCameraAbilities(abilities)

        // Then
        val state = manager.uiState.value
        assertFalse(state.showCaptureButton)
        assertFalse(state.showLiveViewTab)
        assertFalse(state.showVideoButton)
        assertFalse(state.showConfigTab)
        assertTrue(state.cameraFunctionLimitation!!.contains("다운로드만"))
    }

    // --- 연결 상태 관리 ---

    @Test
    fun `updateConnectionState true시 에러 초기화`() {
        // Given: 에러 상태
        manager.setError("이전 에러")

        // When: 연결 성공
        manager.updateConnectionState(true)

        // Then
        assertTrue(manager.uiState.value.isConnected)
        assertNull(manager.uiState.value.error)
    }

    @Test
    fun `updateConnectionState false + errorMessage시 에러 설정`() {
        manager.updateConnectionState(false, "연결 실패")

        assertFalse(manager.uiState.value.isConnected)
        assertEquals("연결 실패", manager.uiState.value.error)
    }

    /**
     * 회귀: 라이브뷰 중 끊김→재연결 시 "라이브뷰 시작 중..." 로딩 오버레이가 안 사라지던 버그.
     * 라이브뷰 Job이 끊김으로 CancellationException 취소되면 startLiveView의 isLoading=false 경로가
     * 실행되지 않아 isLiveViewLoading=true가 고착된다. UI가 실제로 관찰하는 유일 chokepoint인
     * updateConnectionState(false)에서 라이브뷰 플래그/프레임을 함께 정리해 해소한다.
     */
    @Test
    fun `updateConnectionState false시 고착된 라이브뷰 로딩-활성-프레임 정리`() {
        // Given: 라이브뷰 시작 중(로딩) + 프레임 보유 상태에서 (Job 취소로 loading이 true로 고착됐다고 가정)
        manager.updateConnectionState(true)
        manager.updateLiveViewState(
            isActive = true,
            isLoading = true,
            frame = LiveViewFrame(ByteArray(4), 2, 2, 0L)
        )
        assertTrue(manager.uiState.value.isLiveViewLoading)
        assertTrue(manager.uiState.value.isLiveViewActive)

        // When: 끊김(연결 false) 전이
        manager.updateConnectionState(false)

        // Then: 로딩 오버레이가 풀리고(고착 해제) 활성/프레임도 정리됨
        assertFalse(manager.uiState.value.isLiveViewLoading)
        assertFalse(manager.uiState.value.isLiveViewActive)
        assertNull(manager.liveViewFrame.value)
        assertFalse(manager.uiState.value.isConnected)
    }

    /**
     * 회귀 가드: 정상 연결(true) 전이에서는 라이브뷰 상태를 건드리지 않아야 한다
     * (활성 라이브뷰 중 오발 리셋 방지).
     */
    @Test
    fun `updateConnectionState true시 활성 라이브뷰 상태 보존`() {
        // Given: 라이브뷰 활성
        manager.updateConnectionState(true)
        manager.updateLiveViewState(isActive = true, isLoading = false)

        // When: 연결 true 재방출
        manager.updateConnectionState(true)

        // Then: 라이브뷰 활성 유지
        assertTrue(manager.uiState.value.isLiveViewActive)
        assertFalse(manager.uiState.value.isLiveViewLoading)
    }

    // --- updateTransferQueue (요구 E6) ---

    @Test
    fun `updateTransferQueue 후 capture transferQueue 가 전달한 큐와 동일`() {
        // Given
        val queue = TransferQueueState(
            downloading = 2,
            processing = 1,
            currentFileName = "KAY_1200.NEF"
        )

        // When
        manager.updateTransferQueue(queue)

        // Then
        assertEquals(queue, manager.uiState.value.capture.transferQueue)
        assertEquals(2, manager.uiState.value.capture.transferQueue.downloading)
        assertEquals(1, manager.uiState.value.capture.transferQueue.processing)
        assertTrue(manager.uiState.value.capture.transferQueue.isActive)
    }

    @Test
    fun `updateTransferQueue 에 빈 큐 전달 시 isActive false`() {
        // Given: 활성 큐로 먼저 채운 뒤
        manager.updateTransferQueue(TransferQueueState(downloading = 3))
        assertTrue(manager.uiState.value.capture.transferQueue.isActive)

        // When: 빈 큐로 초기화(전송 완료/연결 해제)
        manager.updateTransferQueue(TransferQueueState())

        // Then
        assertEquals(TransferQueueState(), manager.uiState.value.capture.transferQueue)
        assertFalse(manager.uiState.value.capture.transferQueue.isActive)
    }

    // --- 재연결 후 라이브뷰 자동 재개 예약 (요구 2) ---

    /**
     * 비자발적 끊김(PTP/IP true→false) 시점에 라이브뷰가 활성이었으면
     * 재개 예약이 set 되고, consume 은 한 번 true 를 준 뒤 클리어된다(중복 재개 방지).
     */
    @Test
    fun `PTPIP 끊김 시 라이브뷰 활성이면 재개 예약 set되고 consume은 1회만 true`() {
        // Given: PTP/IP 연결 + 라이브뷰 활성
        manager.updatePtpipConnectionState(true)
        manager.updateLiveViewState(isActive = true)

        // When: 비자발적 끊김 전이
        manager.updatePtpipConnectionState(false)

        // Then: 예약이 걸려 첫 consume 은 true, 두 번째는 false(소비됨)
        assertTrue(manager.consumeResumeLiveViewAfterReconnect())
        assertFalse(manager.consumeResumeLiveViewAfterReconnect())
    }

    /**
     * 회귀 가드(a): 사용자가 수동으로 라이브뷰를 끈 뒤 끊긴 경우엔
     * 끊김 시점 isLiveViewActive=false 라 재개 예약이 걸리지 않아야 한다.
     */
    @Test
    fun `PTPIP 끊김 시 라이브뷰 비활성이면 재개 예약 안 걸림`() {
        // Given: PTP/IP 연결 + 라이브뷰 꺼진 상태(수동 OFF 가정)
        manager.updatePtpipConnectionState(true)
        manager.updateLiveViewState(isActive = false)

        // When: 끊김 전이
        manager.updatePtpipConnectionState(false)

        // Then: 예약 없음
        assertFalse(manager.consumeResumeLiveViewAfterReconnect())
    }

    /**
     * clearResumeLiveViewAfterReconnect 로 예약을 강제 클리어하면 consume 이 false 를 준다.
     */
    @Test
    fun `clearResumeLiveViewAfterReconnect가 예약을 클리어`() {
        // Given: 예약이 걸린 상태
        manager.updatePtpipConnectionState(true)
        manager.updateLiveViewState(isActive = true)
        manager.updatePtpipConnectionState(false)

        // When
        manager.clearResumeLiveViewAfterReconnect()

        // Then
        assertFalse(manager.consumeResumeLiveViewAfterReconnect())
    }

    // --- USB 분리 처리 ---

    @Test
    fun `handleUsbDisconnection시 모든 연결 상태 초기화`() {
        // Given: 연결 상태
        manager.updateConnectionState(true)
        manager.updateNativeCameraConnection(true)
        manager.updateLiveViewState(isActive = true)
        manager.updateCapturingState(true)

        // When
        manager.handleUsbDisconnection()

        // Then
        val state = manager.uiState.value
        assertTrue(state.isUsbDisconnected)
        assertFalse(state.isConnected)
        assertFalse(state.isNativeCameraConnected)
        assertFalse(state.isLiveViewActive)
        assertFalse(state.isCapturing)
        assertNull(state.cameraCapabilities)
    }

    // --- Helper ---

    private fun createCapabilities(model: String): CameraCapabilities {
        return CameraCapabilities(
            model = model,
            canCapturePhoto = true,
            canCaptureVideo = false,
            canLiveView = true,
            canTriggerCapture = true,
            supportsBurstMode = false,
            supportsTimelapse = false,
            supportsBracketing = false,
            supportsBulbMode = false,
            supportsAutofocus = true,
            supportsManualFocus = false,
            supportsFocusPoint = false,
            canDownloadFiles = true,
            canDeleteFiles = true,
            canPreviewFiles = true,
            availableIsoSettings = emptyList(),
            availableShutterSpeeds = emptyList(),
            availableApertures = emptyList(),
            availableWhiteBalanceSettings = emptyList(),
            supportsRemoteControl = true,
            supportsConfigChange = true
        )
    }

    private fun createAbilities(
        model: String,
        captureImage: Boolean,
        capturePreview: Boolean,
        config: Boolean,
        captureVideo: Boolean = false,
        delete: Boolean = false,
        putFile: Boolean = false
    ): CameraAbilitiesInfo {
        return CameraAbilitiesInfo(
            model = model,
            status = "PRODUCTION",
            portType = 1,
            usbVendor = "0x0000",
            usbProduct = "0x0000",
            usbClass = 6,
            operations = 0,
            fileOperations = 0,
            folderOperations = 0,
            supports = CameraSupports(
                captureImage = captureImage,
                captureVideo = captureVideo,
                captureAudio = false,
                capturePreview = capturePreview,
                triggerCapture = captureImage,
                config = config,
                delete = delete,
                preview = true,
                raw = false,
                audio = false,
                exif = true,
                deleteAll = false,
                putFile = putFile,
                makeDir = false,
                removeDir = false
            )
        )
    }
}
