package com.inik.camcon.data.repository.managers

import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.ConnectionReportMethod
import com.inik.camcon.domain.usecase.camera.ReportCameraConnectionUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [ConnectionReportObserver] StateFlow 방출 → UseCase 위임 검증.
 *
 * 검증(방출·위임만, 내부 구현 미검증):
 *  - USB + caps null → 위임 0회
 *  - caps 완성 → 1회(USB)
 *  - 동일 (기종, 방식) 재emit → distinctUntilChanged 로 미증가
 *  - STA_MODE → WIFI 로 매핑
 *  - abilitiesModel 우선 / null 이면 표시용 model 폴백
 *
 * 방식: `activeConnectionType`·`cameraCapabilities` 를 실제 MutableStateFlow 로 stub,
 * `appScope` 는 UnconfinedTestDispatcher scope 로 주입해 launchIn 컬렉트가 즉시 동작.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionReportObserverTest {

    private val typeFlow = MutableStateFlow<CameraConnectionType?>(null)
    private val capsFlow = MutableStateFlow<CameraCapabilities?>(null)
    private val useCase = mockk<ReportCameraConnectionUseCase>(relaxed = true)

    private fun startObserver(scope: CoroutineScope): ConnectionReportObserver {
        val globalManager = mockk<CameraConnectionGlobalManager> {
            every { activeConnectionType } returns typeFlow
        }
        val connManager = mockk<CameraConnectionManager>(relaxed = true) {
            every { cameraCapabilities } returns capsFlow
        }
        return ConnectionReportObserver(
            globalManager = globalManager,
            connManager = connManager,
            useCase = useCase,
            appScope = scope
        ).also { it.start() }
    }

    private fun caps(model: String, abilitiesModel: String? = null) = CameraCapabilities(
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
        supportsManualFocus = true,
        supportsFocusPoint = false,
        canDownloadFiles = true,
        canDeleteFiles = false,
        canPreviewFiles = false,
        availableIsoSettings = emptyList(),
        availableShutterSpeeds = emptyList(),
        availableApertures = emptyList(),
        availableWhiteBalanceSettings = emptyList(),
        supportsRemoteControl = true,
        supportsConfigChange = true,
        abilitiesModel = abilitiesModel
    )

    @Test
    fun `USB 연결이라도 capabilities 가 없으면 보고하지 않는다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        typeFlow.value = CameraConnectionType.USB
        capsFlow.value = null

        startObserver(scope)

        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `USB + capabilities 완성 시 USB 방식으로 1회 보고한다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        typeFlow.value = CameraConnectionType.USB
        startObserver(scope)

        capsFlow.value = caps(model = "Nikon Z 8")

        coVerify(exactly = 1) { useCase("Nikon Z 8", ConnectionReportMethod.USB) }
    }

    @Test
    fun `동일 기종 방식 재emit 은 distinct 로 중복 보고되지 않는다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        typeFlow.value = CameraConnectionType.USB
        startObserver(scope)

        val first = caps(model = "Nikon Z 8")
        capsFlow.value = first
        // 다른 필드(배터리)만 바뀐 새 객체 → StateFlow 는 방출하지만 (기종,방식) 페어는 동일.
        capsFlow.value = first.copy(batteryLevel = 50)

        coVerify(exactly = 1) { useCase("Nikon Z 8", ConnectionReportMethod.USB) }
    }

    @Test
    fun `STA_MODE 는 WIFI 방식으로 보고한다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        typeFlow.value = CameraConnectionType.STA_MODE
        startObserver(scope)

        capsFlow.value = caps(model = "Nikon Z 8")

        coVerify(exactly = 1) { useCase("Nikon Z 8", ConnectionReportMethod.WIFI) }
    }

    @Test
    fun `AP_MODE 도 WIFI 방식으로 보고한다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        typeFlow.value = CameraConnectionType.AP_MODE
        startObserver(scope)

        capsFlow.value = caps(model = "Nikon Z 8")

        coVerify(exactly = 1) { useCase("Nikon Z 8", ConnectionReportMethod.WIFI) }
    }

    @Test
    fun `abilitiesModel 이 있으면 표시용 model 보다 우선한다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        typeFlow.value = CameraConnectionType.USB
        startObserver(scope)

        // model(표시용, DeviceInfo)=ILCE-7M3 이지만 abilitiesModel(libgphoto2 원본)로 보고돼야 한다.
        capsFlow.value = caps(model = "ILCE-7M3", abilitiesModel = "Sony Alpha A7 III")

        coVerify(exactly = 1) { useCase("Sony Alpha A7 III", ConnectionReportMethod.USB) }
        coVerify(exactly = 0) { useCase("ILCE-7M3", any()) }
    }

    @Test
    fun `abilitiesModel 이 null 이면 표시용 model 로 폴백한다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        typeFlow.value = CameraConnectionType.USB
        startObserver(scope)

        capsFlow.value = caps(model = "Nikon Z 8", abilitiesModel = null)

        coVerify(exactly = 1) { useCase("Nikon Z 8", ConnectionReportMethod.USB) }
    }

    @Test
    fun `abilitiesModel 이 공백이면 표시용 model 로 폴백한다`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        typeFlow.value = CameraConnectionType.USB
        startObserver(scope)

        capsFlow.value = caps(model = "Nikon Z 8", abilitiesModel = "   ")

        coVerify(exactly = 1) { useCase("Nikon Z 8", ConnectionReportMethod.USB) }
    }
}
