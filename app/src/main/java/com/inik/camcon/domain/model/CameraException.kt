package com.inik.camcon.domain.model

/**
 * 카메라 관련 예외들을 정의하는 sealed class
 */
sealed class CameraException(message: String) : Exception(message) {
    /**
     * 지원하지 않는 촬영 모드 (BURST, TIMELAPSE, BRACKETING, BULB)
     */
    data class UnsupportedShootingMode(
        val mode: ShootingMode,
        val supportedModes: List<ShootingMode> = listOf(ShootingMode.SINGLE)
    ) : CameraException(
        "Unsupported shooting mode: ${mode.name}. Supported: ${supportedModes.joinToString { it.name }}"
    )
}
