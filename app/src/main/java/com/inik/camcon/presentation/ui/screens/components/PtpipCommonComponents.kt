package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.SuccessV2
import com.inik.camcon.presentation.theme.WarningV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.domain.model.ThemeMode

/**
 * 카메라 연결 상태 카드
 * AP/STA 모드 공용 — 연결 중이거나 연결됨 상태일 때만 표시
 */
@Composable
fun ConnectionStatusCard(
    connectionState: PtpipConnectionState,
    selectedCamera: PtpipCamera?,
    cameraInfo: PtpipCameraInfo?,
    onDisconnect: () -> Unit,
    onCapture: () -> Unit
) {
    val cardShape = RoundedCornerShape(Radius.md)
    SurfaceV2(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = StrokeWidth.hairline,
                color = when (connectionState) {
                    PtpipConnectionState.CONNECTED -> SuccessV2.copy(alpha = 0.3f)
                    PtpipConnectionState.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    else -> DividerLine
                },
                shape = cardShape
            ),
        tier = 2,
        shape = cardShape
    ) {
        Column(
            modifier = Modifier.padding(Spacing.base)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = when (connectionState) {
                        PtpipConnectionState.CONNECTED -> SuccessV2
                        PtpipConnectionState.CONNECTING -> WarningV2
                        PtpipConnectionState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedCamera?.name ?: stringResource(R.string.ptpip_camera),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when (connectionState) {
                            PtpipConnectionState.CONNECTED -> stringResource(R.string.ptpip_connected_ip, selectedCamera?.ipAddress ?: "")
                            PtpipConnectionState.CONNECTING -> stringResource(R.string.ptpip_connecting_status)
                            PtpipConnectionState.ERROR -> stringResource(R.string.ptpip_connection_error)
                            else -> stringResource(R.string.ptpip_not_connected)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (cameraInfo != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "${cameraInfo.manufacturer} ${cameraInfo.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (connectionState == PtpipConnectionState.CONNECTED) {
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.ptpip_capture),
                        onClick = onCapture,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = stringResource(R.string.ptpip_disconnect),
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// 프리뷰
@Preview(name = "Connection Status - Connected", showBackground = true)
@Composable
private fun ConnectionStatusConnectedPreview() {
    CamConTheme() {
        ConnectionStatusCard(
            connectionState = PtpipConnectionState.CONNECTED,
            selectedCamera = PtpipCamera(
                ipAddress = "192.168.1.100",
                port = 15740,
                name = "Canon EOS R5",
                isOnline = true
            ),
            cameraInfo = null,
            onDisconnect = {},
            onCapture = {}
        )
    }
}

@Preview(name = "Connection Status - Disconnected", showBackground = true)
@Composable
private fun ConnectionStatusDisconnectedPreview() {
    CamConTheme() {
        ConnectionStatusCard(
            connectionState = PtpipConnectionState.DISCONNECTED,
            selectedCamera = null,
            cameraInfo = null,
            onDisconnect = {},
            onCapture = {}
        )
    }
}
