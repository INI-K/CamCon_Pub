package com.inik.camcon.domain.manager

import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.GlobalCameraConnectionState
import com.inik.camcon.domain.model.UiText
import kotlinx.coroutines.flow.StateFlow

interface CameraConnectionGlobalManager {
    val globalConnectionState: StateFlow<GlobalCameraConnectionState>
    val activeConnectionType: StateFlow<CameraConnectionType?>

    /**
     * 연결 상태 메시지. 도메인/데이터 레이어는 Context가 없으므로 [UiText](리소스 ID + 인자)만
     * 전달하고, presentation 레이어가 `resolve(context)`로 실제 문자열을 얻는다.
     */
    val connectionStatusMessage: StateFlow<UiText>

    fun getCurrentActiveConnectionType(): CameraConnectionType?
    fun isConnectionTypeActive(type: CameraConnectionType): Boolean
    fun isAnyCameraConnected(): Boolean
    fun isApModeConnected(): Boolean
    fun isStaModeConnected(): Boolean
    fun isUsbConnected(): Boolean
    fun setPtpipPhotoCapturedCallback(callback: (String, String) -> Unit)
    fun cleanup()
}
