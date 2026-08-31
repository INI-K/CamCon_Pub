package com.inik.camcon.presentation.viewmodel.state

import com.inik.camcon.domain.manager.ErrorSeverity
import com.inik.camcon.domain.manager.ErrorType
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.ExposureCompensation
import com.inik.camcon.domain.model.StorageInfo
import com.inik.camcon.domain.usecase.camera.GetCameraCapabilitiesUseCase
import com.inik.camcon.domain.usecase.camera.GetCameraSettingsUseCase
import com.inik.camcon.domain.usecase.camera.GetExposureCompensationUseCase
import com.inik.camcon.domain.usecase.camera.GetStorageInfoUseCase
import com.inik.camcon.domain.usecase.camera.SetExposureCompensationUseCase
import com.inik.camcon.domain.usecase.camera.UpdateCameraSettingUseCase
import com.inik.camcon.domain.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CameraSettingsManager] 단위 테스트 — ISO/SS/조리개/WB 등 설정 조회·변경 및 EV/스토리지 로드의
 * **StateFlow 방출**을 검증한다(구현 세부 검증 금지, 프로젝트 규약).
 *
 * 협력자:
 *  - UseCase 6종: mockk. 성공/실패 `Result` 로 시나리오 표현.
 *  - errorHandlingManager: relaxed mockk — 실패 시 `emitError` 라우팅 여부만 확인.
 *  - logger: relaxed mockk(도메인 인터페이스, android Log 비의존).
 *  - ioDispatcher: [UnconfinedTestDispatcher] — `withContext(io)` 를 인라인 실행해 suspend 호출을 즉시 완결.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraSettingsManagerTest {

    private val getCameraSettingsUseCase = mockk<GetCameraSettingsUseCase>()
    private val updateCameraSettingUseCase = mockk<UpdateCameraSettingUseCase>()
    private val getCameraCapabilitiesUseCase = mockk<GetCameraCapabilitiesUseCase>()
    private val getExposureCompensationUseCase = mockk<GetExposureCompensationUseCase>()
    private val setExposureCompensationUseCase = mockk<SetExposureCompensationUseCase>()
    private val getStorageInfoUseCase = mockk<GetStorageInfoUseCase>()
    private val errorHandlingManager = mockk<ErrorHandlingManager>(relaxed = true)

    private fun createManager() = CameraSettingsManager(
        getCameraSettingsUseCase = getCameraSettingsUseCase,
        updateCameraSettingUseCase = updateCameraSettingUseCase,
        getCameraCapabilitiesUseCase = getCameraCapabilitiesUseCase,
        getExposureCompensationUseCase = getExposureCompensationUseCase,
        setExposureCompensationUseCase = setExposureCompensationUseCase,
        getStorageInfoUseCase = getStorageInfoUseCase,
        errorHandlingManager = errorHandlingManager,
        logger = mockk<Logger>(relaxed = true),
        ioDispatcher = UnconfinedTestDispatcher()
    )

    // ── loadCameraSettings ──

    @Test
    fun `loadCameraSettings 성공 시 cameraSettings 방출하고 로딩 플래그를 내린다`() = runTest {
        val settings = sampleSettings(mapOf("iso" to listOf("100", "200")))
        coEvery { getCameraSettingsUseCase() } returns Result.success(settings)
        val manager = createManager()

        manager.loadCameraSettings()

        assertEquals(settings, manager.cameraSettings.value)
        assertFalse(manager.isLoadingSettings.value)
    }

    @Test
    fun `loadCameraSettings 실패 시 emitError 라우팅 후 로딩 플래그를 내린다`() = runTest {
        coEvery { getCameraSettingsUseCase() } returns Result.failure(RuntimeException("boom"))
        val manager = createManager()

        manager.loadCameraSettings()

        assertNull(manager.cameraSettings.value)
        assertFalse(manager.isLoadingSettings.value)
        coVerify {
            errorHandlingManager.emitError(ErrorType.OPERATION, any(), any(), ErrorSeverity.MEDIUM)
        }
    }

    // ── loadCameraCapabilities ──

    @Test
    fun `loadCameraCapabilities 성공 시 cameraCapabilities 방출`() = runTest {
        val caps = sampleCapabilities(liveView = true, timelapse = true, autofocus = true)
        coEvery { getCameraCapabilitiesUseCase() } returns Result.success(caps)
        val manager = createManager()

        manager.loadCameraCapabilities()

        assertEquals(caps, manager.cameraCapabilities.value)
        assertTrue(manager.canLiveView())
        assertTrue(manager.canTimelapse())
        assertTrue(manager.canAutoFocus())
    }

    @Test
    fun `loadCameraCapabilities 성공했지만 null이면 capabilities는 유지되고 emitError`() = runTest {
        coEvery { getCameraCapabilitiesUseCase() } returns Result.success(null)
        val manager = createManager()

        manager.loadCameraCapabilities()

        assertNull(manager.cameraCapabilities.value)
        coVerify {
            errorHandlingManager.emitError(ErrorType.OPERATION, any(), any(), ErrorSeverity.LOW)
        }
    }

    // ── updateCameraSetting ──

    @Test
    fun `updateCameraSetting 성공 시 캐시에 반영하고 설정을 재조회한다`() = runTest {
        coEvery { updateCameraSettingUseCase("iso", "400") } returns Result.success(true)
        // 재조회에서 사용될 설정 스텁.
        coEvery { getCameraSettingsUseCase() } returns
            Result.success(sampleSettings(mapOf("iso" to listOf("400"))))
        val manager = createManager()

        manager.updateCameraSetting("iso", "400")

        // 캐시 우선 조회 — 방금 넣은 값이 나와야 한다.
        assertEquals("400", manager.getSettingValue("iso"))
        assertFalse(manager.isUpdatingSettings.value)
        coVerify { updateCameraSettingUseCase("iso", "400") }
        // 동기화를 위한 loadCameraSettings 재조회.
        coVerify { getCameraSettingsUseCase() }
    }

    @Test
    fun `updateCameraSetting 실패 시 emitError 후 업데이트 플래그를 내린다`() = runTest {
        coEvery { updateCameraSettingUseCase("iso", "400") } returns
            Result.failure(RuntimeException("nope"))
        val manager = createManager()

        manager.updateCameraSetting("iso", "400")

        assertFalse(manager.isUpdatingSettings.value)
        coVerify {
            errorHandlingManager.emitError(ErrorType.OPERATION, any(), any(), ErrorSeverity.MEDIUM)
        }
    }

    // ── 노출 보정(EV) ──

    @Test
    fun `loadExposureCompensation 성공 시 exposureCompensation 방출`() = runTest {
        val ev = ExposureCompensation(current = "0", available = listOf("-1/3", "0", "+1/3"))
        coEvery { getExposureCompensationUseCase() } returns Result.success(ev)
        val manager = createManager()

        manager.loadExposureCompensation()

        assertEquals(ev, manager.exposureCompensation.value)
    }

    @Test
    fun `setExposureCompensation 성공 시 재조회로 값을 동기화하고 플래그를 내린다`() = runTest {
        coEvery { setExposureCompensationUseCase("+1/3") } returns Result.success(Unit)
        coEvery { getExposureCompensationUseCase() } returns
            Result.success(ExposureCompensation(current = "+1/3", available = listOf("0", "+1/3")))
        val manager = createManager()

        manager.setExposureCompensation("+1/3")

        assertEquals("+1/3", manager.exposureCompensation.value?.current)
        assertFalse(manager.isUpdatingSettings.value)
        coVerify { setExposureCompensationUseCase("+1/3") }
        coVerify { getExposureCompensationUseCase() }
    }

    @Test
    fun `setExposureCompensation 실패 시 emitError`() = runTest {
        coEvery { setExposureCompensationUseCase("+1/3") } returns
            Result.failure(RuntimeException("ev fail"))
        val manager = createManager()

        manager.setExposureCompensation("+1/3")

        assertFalse(manager.isUpdatingSettings.value)
        coVerify {
            errorHandlingManager.emitError(ErrorType.OPERATION, any(), any(), ErrorSeverity.MEDIUM)
        }
    }

    // ── 스토리지 ──

    @Test
    fun `loadStorageInfo 성공 시 storageInfo 방출`() = runTest {
        val info = StorageInfo(totalBytes = 64_000_000_000L, freeBytes = 32_000_000_000L, imagesFree = 1200)
        coEvery { getStorageInfoUseCase() } returns Result.success(info)
        val manager = createManager()

        manager.loadStorageInfo()

        assertEquals(info, manager.storageInfo.value)
    }

    // ── 순수 조회 헬퍼 ──

    @Test
    fun `getSettingValue-isSettingSupported-getAvailableValues는 로딩된 설정을 반영`() = runTest {
        coEvery { getCameraSettingsUseCase() } returns
            Result.success(sampleSettings(mapOf("iso" to listOf("100", "200", "400"))))
        val manager = createManager()
        manager.loadCameraSettings()

        assertEquals("100", manager.getSettingValue("iso"))
        assertTrue(manager.isSettingSupported("iso"))
        assertFalse(manager.isSettingSupported("shutterspeed"))
        assertEquals(listOf("100", "200", "400"), manager.getAvailableValues("iso"))
        assertTrue(manager.getAvailableValues("nope").isEmpty())
    }

    // ── cleanup ──

    @Test
    fun `cleanup은 모든 상태 StateFlow를 초기화한다`() = runTest {
        coEvery { getCameraSettingsUseCase() } returns
            Result.success(sampleSettings(mapOf("iso" to listOf("100"))))
        coEvery { getCameraCapabilitiesUseCase() } returns
            Result.success(sampleCapabilities(liveView = true, timelapse = true, autofocus = true))
        coEvery { getExposureCompensationUseCase() } returns
            Result.success(ExposureCompensation("0", listOf("0")))
        coEvery { getStorageInfoUseCase() } returns
            Result.success(StorageInfo(1L, 1L, 1))
        val manager = createManager()
        manager.loadCameraSettings()
        manager.loadCameraCapabilities()
        manager.loadExposureCompensation()
        manager.loadStorageInfo()

        manager.cleanup()

        assertNull(manager.cameraSettings.value)
        assertNull(manager.cameraCapabilities.value)
        assertNull(manager.exposureCompensation.value)
        assertNull(manager.storageInfo.value)
        assertFalse(manager.isLoadingSettings.value)
        assertFalse(manager.isUpdatingSettings.value)
        // 캐시도 비워져 이전 값이 조회되지 않아야 한다.
        assertNull(manager.getSettingValue("iso"))
    }

    // ── 세션 캐시 (탭 재진입이 왕복을 다시 만들지 않는다) ──

    @Test
    fun `기능 정보는 세션당 한 번만 조회한다`() = runTest {
        coEvery { getCameraCapabilitiesUseCase() } returns
            Result.success(sampleCapabilities(liveView = true, timelapse = true, autofocus = true))
        val manager = createManager()

        manager.loadCameraCapabilities()
        manager.loadCameraCapabilities()   // 탭 재진입

        coVerify(exactly = 1) { getCameraCapabilitiesUseCase() }
        assertTrue(manager.cameraCapabilities.value!!.canLiveView)
    }

    @Test
    fun `EV 와 스토리지도 세션당 한 번만 조회한다`() = runTest {
        coEvery { getExposureCompensationUseCase() } returns
            Result.success(ExposureCompensation("0", listOf("0")))
        coEvery { getStorageInfoUseCase() } returns Result.success(StorageInfo(1L, 1L, 1))
        val manager = createManager()

        manager.loadExposureCompensation()
        manager.loadStorageInfo()
        manager.loadExposureCompensation()
        manager.loadStorageInfo()

        coVerify(exactly = 1) { getExposureCompensationUseCase() }
        coVerify(exactly = 1) { getStorageInfoUseCase() }
    }

    @Test
    fun `조회에 실패하면 캐시하지 않고 다음에 다시 시도한다`() = runTest {
        // 실패를 "받았다"로 굳히면 그 세션 내내 값이 비어 있게 된다.
        coEvery { getCameraCapabilitiesUseCase() } returns Result.failure(RuntimeException("실패"))
        val manager = createManager()

        manager.loadCameraCapabilities()
        manager.loadCameraCapabilities()

        coVerify(exactly = 2) { getCameraCapabilitiesUseCase() }
    }

    @Test
    fun `세션이 바뀌면 다시 조회한다`() = runTest {
        coEvery { getCameraCapabilitiesUseCase() } returns
            Result.success(sampleCapabilities(liveView = true, timelapse = true, autofocus = true))
        val manager = createManager()
        manager.loadCameraCapabilities()

        manager.onCameraSessionChanged()
        manager.loadCameraCapabilities()

        // 카메라가 바뀌면 값도 달라지므로 반드시 다시 물어봐야 한다.
        coVerify(exactly = 2) { getCameraCapabilitiesUseCase() }
    }

    @Test
    fun `EV 를 바꾸면 EV 만 다시 조회하고 스토리지는 그대로 둔다`() = runTest {
        coEvery { getExposureCompensationUseCase() } returns
            Result.success(ExposureCompensation("0", listOf("0", "+1/3")))
        coEvery { getStorageInfoUseCase() } returns Result.success(StorageInfo(1L, 1L, 1))
        coEvery { setExposureCompensationUseCase("+1/3") } returns Result.success(Unit)
        val manager = createManager()
        manager.loadExposureCompensation()
        manager.loadStorageInfo()

        manager.setExposureCompensation("+1/3")
        manager.loadStorageInfo()

        // 최초 1회 + 변경 후 재조회 1회
        coVerify(exactly = 2) { getExposureCompensationUseCase() }
        // 스토리지는 건드리지 않았으므로 그대로 캐시가 유지된다.
        coVerify(exactly = 1) { getStorageInfoUseCase() }
    }

    // ── 헬퍼 ──

    private fun sampleSettings(available: Map<String, List<String>>) = CameraSettings(
        iso = "100",
        shutterSpeed = "1/125",
        aperture = "f/2.8",
        whiteBalance = "auto",
        focusMode = "AF-S",
        exposureCompensation = "0",
        availableSettings = available
    )

    private fun sampleCapabilities(
        liveView: Boolean,
        timelapse: Boolean,
        autofocus: Boolean
    ) = CameraCapabilities(
        model = "Nikon Z8",
        canCapturePhoto = true,
        canCaptureVideo = false,
        canLiveView = liveView,
        canTriggerCapture = true,
        supportsBurstMode = false,
        supportsTimelapse = timelapse,
        supportsBracketing = false,
        supportsBulbMode = false,
        supportsAutofocus = autofocus,
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
