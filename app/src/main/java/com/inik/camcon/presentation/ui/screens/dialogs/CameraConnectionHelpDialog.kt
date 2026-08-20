package com.inik.camcon.presentation.ui.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Body
import com.inik.camcon.presentation.theme.BodyLarge
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2

/**
 * 카메라 연결 문제 해결 도움말 다이얼로그.
 *
 * 타이포 위계: 타이틀 20sp → 리드 16sp → 체크리스트 14sp → 브랜드 주석 12sp.
 * 체크리스트는 다이얼로그 컨테이너(Surface2)보다 한 단 위인 tier 3 패널에 얹어
 * alpha 감광이 아니라 표면 단차로 깊이를 만든다.
 */
@Composable
fun CameraConnectionHelpDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = context.getString(R.string.camera_connection_help_title),
                style = HeadingL,
                color = TextPrimaryV2
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = context.getString(R.string.camera_connection_help_message),
                    style = BodyLarge,
                    color = TextSecondaryV2
                )

                SurfaceV2(
                    modifier = Modifier.fillMaxWidth(),
                    tier = 3
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        HelpRow("01", context.getString(R.string.check_camera_pc_mode))
                        HelpRow("02", context.getString(R.string.check_usb_cable))
                        HelpRow("03", context.getString(R.string.check_camera_power))
                        HelpRow(
                            "04",
                            context.getString(R.string.check_other_apps_not_using_camera)
                        )
                    }
                }

                // 섹션 분리 — 위 블록 간격(12) + 8 = 20dp 브레이크
                Spacer(modifier = Modifier.height(Spacing.sm))

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = context.getString(R.string.camera_specific_settings),
                        style = HeadingM,
                        color = TextPrimaryV2
                    )

                    CameraBrandInstructions()
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = context.getString(R.string.retry),
                onClick = onRetry
            )
        },
        dismissButton = {
            SecondaryButton(
                text = context.getString(R.string.close),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun HelpRow(
    number: String,
    instruction: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = number,
            style = MonoNumeric,
            color = TextTertiary,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            text = instruction,
            style = Body,
            color = TextPrimaryV2,
            modifier = Modifier.alignByBaseline()
        )
    }
}

@Composable
private fun CameraBrandInstructions() {
    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = context.getString(R.string.canon_camera_settings),
            style = Caption,
            color = TextTertiary
        )
        Text(
            text = context.getString(R.string.nikon_camera_settings),
            style = Caption,
            color = TextTertiary
        )
        Text(
            text = context.getString(R.string.sony_camera_settings),
            style = Caption,
            color = TextTertiary
        )
    }
}

@Preview(name = "Camera Connection Help Dialog", showBackground = true)
@Composable
private fun CameraConnectionHelpDialogPreview() {
    CamConTheme() {
        CameraConnectionHelpDialog(
            onDismiss = { },
            onRetry = { }
        )
    }
}
