package com.inik.camcon.data.repository.managers

import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.ConnectionReportMethod
import com.inik.camcon.domain.usecase.camera.ReportCameraConnectionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 연결 성공을 감지해 익명 보고 UseCase에 위임하는 읽기 전용 관찰자.
 *
 * activeConnectionType + cameraCapabilities를 결합해 (기종, 방식) 페어를 만든다.
 * USB는 libgphoto2 abilities 모델명([CameraCapabilities.abilitiesModel])이 실모델이므로 우선 쓴다.
 * Wi-Fi(PTP/IP)는 abilitiesModel이 "PTP/IP Camera" 같은 제네릭이라
 * 실제 DeviceInfo 모델명([CameraCapabilities.model])을 우선한다. 각 경우 값이 없으면 다른 쪽으로 폴백한다.
 *
 * ⚠️ 읽기 전용 — disconnect/close 등 연결 상태를 바꾸는 호출은 절대 하지 않는다.
 */
@Singleton
class ConnectionReportObserver @Inject constructor(
    private val globalManager: CameraConnectionGlobalManager,
    private val connManager: CameraConnectionManager,
    private val useCase: ReportCameraConnectionUseCase,
    @ApplicationScope appScope: CoroutineScope
) {
    // 앱 scope의 자식 scope — 앱 scope 수명을 따르되 취소는 격리한다.
    private val scope: CoroutineScope =
        CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext.job))

    fun start() {
        combine(
            globalManager.activeConnectionType,
            connManager.cameraCapabilities
        ) { type, caps ->
            if (type == null || caps == null) {
                null
            } else {
                val abilities = caps.abilitiesModel?.takeIf { it.isNotBlank() }
                val device = caps.model.takeIf { it.isNotBlank() }
                // USB는 abilitiesModel이 실모델이라 우선 사용(회귀 방지).
                // Wi-Fi(PTP/IP)는 abilitiesModel이 "PTP/IP Camera" 같은 제네릭이므로
                // 실제 DeviceInfo(model, 예: "Sony Corporation ILCE-7C")를 우선한다.
                val model = when (type) {
                    CameraConnectionType.USB -> abilities ?: device
                    CameraConnectionType.AP_MODE,
                    CameraConnectionType.STA_MODE -> device ?: abilities
                }
                if (model.isNullOrBlank()) {
                    null
                } else {
                    model to when (type) {
                        CameraConnectionType.USB -> ConnectionReportMethod.USB
                        CameraConnectionType.AP_MODE,
                        CameraConnectionType.STA_MODE -> ConnectionReportMethod.WIFI
                    }
                }
            }
        }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { (model, method) -> useCase(model, method) }
            .launchIn(scope)
    }
}
