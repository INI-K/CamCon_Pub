package com.inik.camcon.domain.manager

import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.CameraCapabilities

/**
 * Data 레이어에서 카메라 상태를 Presentation 레이어로 전달하기 위한 인터페이스.
 * Clean Architecture 의존 방향(Data -> Domain <- Presentation)을 유지한다.
 */
interface CameraStateObserver {
    fun updateCameraAbilities(abilities: CameraAbilitiesInfo)
    fun updateCameraCapabilities(capabilities: CameraCapabilities?)
    fun updateCameraInitialization(isInitializing: Boolean)
    fun showCameraStatusCheckDialog(show: Boolean)
}
