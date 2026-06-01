package com.inik.camcon.presentation.ui.screens.components

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.viewmodel.CameraOperationsManager

/**
 * 지원하지 않는 촬영 모드를 사용자에게 알리는 Snackbar 효과
 *
 * shootingModeError가 [CameraOperationsManager.UNSUPPORTED_MODE_PREFIX] 마커로 시작하면
 * 뒤따르는 [ShootingMode] 이름을 stringResource로 매핑해 현지화된 메시지를 표시한다.
 * 마커가 없으면(레거시/기타 경로) 받은 문자열을 그대로 표시한다.
 *
 * @param shootingModeError 촬영 모드 에러 메시지/마커 (null이면 동작 없음)
 * @param snackbarHostState Snackbar를 표시하는 상태 관리자
 * @param modifier 선택사항 Modifier
 */
@Composable
fun UnsupportedShootingModeSnackbar(
    shootingModeError: String?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(shootingModeError) {
        if (!shootingModeError.isNullOrEmpty()) {
            val message = resolveUnsupportedModeMessage(context, shootingModeError)
                ?: shootingModeError
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }
}

/**
 * 미지원 모드 마커를 현지화 메시지로 변환한다. 마커가 아니면 null 반환(원본 사용).
 */
private fun resolveUnsupportedModeMessage(context: Context, raw: String): String? {
    if (!raw.startsWith(CameraOperationsManager.UNSUPPORTED_MODE_PREFIX)) return null
    val modeName = raw.removePrefix(CameraOperationsManager.UNSUPPORTED_MODE_PREFIX)
    val mode = runCatching { ShootingMode.valueOf(modeName) }.getOrNull()
    val modeLabelRes = when (mode) {
        ShootingMode.SINGLE -> R.string.shooting_mode_single
        ShootingMode.BURST -> R.string.shooting_mode_burst
        ShootingMode.TIMELAPSE -> R.string.shooting_mode_timelapse
        ShootingMode.BULB -> R.string.shooting_mode_bulb
        ShootingMode.HDR_BRACKET -> R.string.shooting_mode_hdr_bracket
        null -> null
    }
    val modeLabel = modeLabelRes?.let { context.getString(it) } ?: modeName
    return context.getString(R.string.unsupported_shooting_mode_message, modeLabel)
}
