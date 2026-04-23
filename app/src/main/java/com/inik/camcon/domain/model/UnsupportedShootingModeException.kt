package com.inik.camcon.domain.model

/**
 * 카메라가 지원하지 않는 촬영 모드를 요청했을 때 발생하는 예외
 */
class UnsupportedShootingModeException(
    val mode: ShootingMode,
    val supportedModes: List<ShootingMode> = listOf(ShootingMode.SINGLE)
) : Exception(
    "Unsupported shooting mode: ${mode.name}. Supported: ${supportedModes.joinToString { it.name }}"
)
