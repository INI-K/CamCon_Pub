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
import androidx.compose.ui.tooling.preview.Preview
import com.inik.camcon.R
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DisplayM
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.SuccessV2
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
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
                        else -> TextSecondaryV2
                    },
                    modifier = Modifier.size(IconSize.lg)
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    // 연결 완료 시에는 카메라명이 이 화면의 Hero다(히어로 슬롯인 HotspotStatusHero는
                    // 연결 상태에서 렌더되지 않으므로 화면당 Hero 1개 규칙이 유지된다).
                    val isConnected = connectionState == PtpipConnectionState.CONNECTED
                    Text(
                        text = selectedCamera?.name ?: stringResource(R.string.ptpip_camera),
                        style = if (isConnected) DisplayM else HeadingM,
                        color = TextPrimaryV2
                    )
                    if (isConnected) {
                        // 상태어(프로포셔널) / IP(모노 tnum) 분리 — 자릿수가 바뀌어도 자리가 흔들리지 않는다.
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.v3_ptpip_status_connected),
                                style = BodySmall,
                                color = TextSecondaryV2
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = selectedCamera?.ipAddress ?: "",
                                style = MonoNumeric,
                                color = TextSecondaryV2
                            )
                        }
                    } else {
                        Text(
                            text = when (connectionState) {
                                PtpipConnectionState.CONNECTING -> stringResource(R.string.ptpip_connecting_status)
                                PtpipConnectionState.ERROR -> stringResource(R.string.ptpip_connection_error)
                                else -> stringResource(R.string.ptpip_not_connected)
                            },
                            style = BodySmall,
                            color = TextSecondaryV2
                        )
                    }
                }
            }

            if (cameraInfo != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "${cameraInfo.manufacturer} ${cameraInfo.model}",
                    style = BodySmall,
                    color = TextSecondaryV2
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
                ipAddress = "192.168.49.137",
                port = 15740,
                name = "NIKON Z 8",
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
