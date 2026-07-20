package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.inik.camcon.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SkeletonLoader
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0

/**
 * 간단한 로딩 오버레이
 */
@Composable
fun LoadingOverlay(
    message: String = "",
    progress: Float? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0.copy(alpha = 0.7f))
            // 블로킹 오버레이 — 스크림 아래 셔터·탭·토글로 탭이 관통해 명령이 경합하지 않도록
            // 모든 탭 제스처를 소비한다(clickable 의 role=button 부작용 없이).
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { contentDescription = message },
        contentAlignment = Alignment.Center
    ) {
        SurfaceV2(
            tier = 2,
            border = true,
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SkeletonLoader(
                    modifier = Modifier
                        .width(200.dp)
                        .height(Spacing.sm)
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * USB 초기화 과정을 표시하는 전체화면 오버레이
 */
@Composable
fun UsbInitializationOverlay(
    message: String = stringResource(R.string.camera_control_usb_initializing),
    progress: Float? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0.copy(alpha = 0.7f))
            // 블로킹 오버레이 — 초기화 중 스크림 아래 컨트롤로 탭이 관통하지 않도록 탭 제스처를 소비한다.
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { contentDescription = message },
        contentAlignment = Alignment.Center
    ) {
        SurfaceV2(
            tier = 2,
            border = true,
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier
                .padding(Spacing.xl)
                .width(300.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SkeletonLoader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.sm)
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                Text(
                    text = stringResource(R.string.usb_init_please_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * PTP 타임아웃 에러 발생 시 앱 재시작을 안내하는 다이얼로그
 */
@Composable
fun PtpTimeoutDialog(
    onDismissRequest: () -> Unit = {},
    onRestartRequest: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.usb_init_camera_connection_error),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.usb_init_timeout_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.usb_init_restart_fix),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.usb_init_check_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.camera_control_app_restart),
                onClick = onRestartRequest
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(R.string.close))
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    )
}

@Preview(name = "Loading Overlay", showBackground = true)
@Composable
private fun LoadingOverlayPreview() {
    CamConTheme() {
        Box(modifier = Modifier.background(Surface0).fillMaxSize()) {
            LoadingOverlay(message = "카메라 연결 중...")
        }
    }
}

@Preview(name = "USB Initialization Overlay", showBackground = true)
@Composable
private fun UsbInitializationOverlayPreview() {
    CamConTheme() {
        Box(modifier = Modifier.background(Surface0).fillMaxSize()) {
            UsbInitializationOverlay(message = "USB 카메라 초기화 중...", progress = 0.6f)
        }
    }
}

@Preview(name = "PTP Timeout Dialog", showBackground = true)
@Composable
private fun PtpTimeoutDialogPreview() {
    CamConTheme() {
        PtpTimeoutDialog(onRestartRequest = {})
    }
}