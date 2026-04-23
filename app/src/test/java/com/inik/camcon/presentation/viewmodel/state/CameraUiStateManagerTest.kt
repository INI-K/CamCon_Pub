package com.inik.camcon.presentation.viewmodel.state

import android.util.Log
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSupports
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
