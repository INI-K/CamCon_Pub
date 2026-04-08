package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Overlay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.CamConTheme

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
            .background(Overlay),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

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
    message: String = "USB 카메라 초기화 중...",
    progress: Float? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Overlay.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .padding(32.dp)
                .width(300.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.size(60.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(60.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

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
    AlertDialog(
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.usb_init_restart_fix),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.usb_init_check_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRestartRequest
            ) {
                Text(stringResource(R.string.camera_control_app_restart))
            }
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
    CamConTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.background(Background).fillMaxSize()) {
            LoadingOverlay(message = "카메라 연결 중...")
        }
    }
}

@Preview(name = "USB Initialization Overlay", showBackground = true)
@Composable
private fun UsbInitializationOverlayPreview() {
    CamConTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.background(Background).fillMaxSize()) {
            UsbInitializationOverlay(message = "USB 카메라 초기화 중...", progress = 0.6f)
        }
    }
}

@Preview(name = "PTP Timeout Dialog", showBackground = true)
@Composable
private fun PtpTimeoutDialogPreview() {
    CamConTheme(themeMode = ThemeMode.DARK) {
        PtpTimeoutDialog(onRestartRequest = {})
    }
}