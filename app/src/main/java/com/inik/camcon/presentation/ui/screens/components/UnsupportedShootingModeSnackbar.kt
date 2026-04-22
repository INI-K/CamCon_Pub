package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/**
 * 지원하지 않는 촬영 모드를 사용자에게 알리는 Snackbar 효과
 *
 * @param shootingModeError 촬영 모드 에러 메시지 (null이면 동작 없음)
 * @param snackbarHostState Snackbar를 표시하는 상태 관리자
 * @param modifier 선택사항 Modifier
 */
@Composable
fun UnsupportedShootingModeSnackbar(
    shootingModeError: String?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(shootingModeError) {
        if (!shootingModeError.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(
                message = shootingModeError,
                duration = SnackbarDuration.Short
            )
        }
    }
}
