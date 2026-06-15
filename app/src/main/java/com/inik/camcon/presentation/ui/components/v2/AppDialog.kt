package com.inik.camcon.presentation.ui.components.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.Elevation
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2

/**
 * CamCon V2 — 브랜드 다이얼로그.
 *
 * Material3 [AlertDialog] 의 드롭인 래퍼. Technical HUD 톤을 강제 적용:
 * Surface2 컨테이너 / 각진 8dp([Radius.lg]) 코너(M3 기본 28dp 폐기) / 앰버 아이콘 /
 * TextPrimaryV2·TextSecondaryV2 텍스트 / [Elevation.high] tonal.
 *
 * 호출부는 기존 `AlertDialog(...)` 를 `AppDialog(...)` 로 바꾸고 색/모양 지정을 제거하면 된다.
 * 버튼은 v2 [PrimaryButton]/[SecondaryButton] 사용을 권장한다.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        properties = properties,
        containerColor = Surface2,
        iconContentColor = Accent,
        titleContentColor = TextPrimaryV2,
        textContentColor = TextSecondaryV2,
        shape = RoundedCornerShape(Radius.lg),
        tonalElevation = Elevation.high
    )
}
