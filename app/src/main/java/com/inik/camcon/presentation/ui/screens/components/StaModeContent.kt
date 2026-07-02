package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
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
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.SuccessV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import com.inik.camcon.domain.model.ThemeMode

/**
 * STA 모드 화면 컴포넌트
 */
@Composable
fun StaModeContent(
    ptpipViewModel: PtpipViewModel,
    connectionState: PtpipConnectionState,
    discoveredCameras: List<PtpipCamera>,
    isDiscovering: Boolean,
    isConnecting: Boolean,
    selectedCamera: PtpipCamera?,
    cameraInfo: PtpipCameraInfo?,
    isPtpipEnabled: Boolean,
    isWifiConnected: Boolean,
    wifiCapabilities: WifiCapabilities,
    wifiNetworkState: WifiNetworkState,
    isAutoReconnectEnabled: Boolean,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    nearbyWifiSSIDs: List<String>,
    onConnectToWifi: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.base, vertical = Spacing.md)
    ) {
        // 통합 네트워크 상태 카드
        item {
            StaNetworkStatusCard(
                wifiNetworkState = wifiNetworkState,
                isPtpipEnabled = isPtpipEnabled,
                isWifiConnected = isWifiConnected,
                ptpipViewModel = ptpipViewModel
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        // 자동 재연결 토글
        item {
            AutoReconnectCard(
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                onToggleAutoReconnect = { ptpipViewModel.setAutoReconnectEnabled(it) }
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        // 카메라 연결 상태 (연결 중이거나 연결됨일 때만)
        if (connectionState != PtpipConnectionState.DISCONNECTED) {
            item {
                ConnectionStatusCard(
                    connectionState = connectionState,
                    selectedCamera = selectedCamera,
                    cameraInfo = cameraInfo,
                    onDisconnect = { ptpipViewModel.disconnect() },
                    onCapture = { ptpipViewModel.capturePhoto() }
                )
                Spacer(modifier = Modifier.height(Spacing.md))
            }
        }

        // 주변 Wi‑Fi 스캔 결과
        if (nearbyWifiSSIDs.isNotEmpty()) {
            item {
                WifiScanResultsCard(
                    ssids = nearbyWifiSSIDs,
                    onConnectToWifi = onConnectToWifi,
                    isStaMode = true
                )
                Spacer(modifier = Modifier.height(Spacing.md))
            }
        }

        // mDNS 검색 카드
        item {
            MdnsSearchCard(
                isDiscovering = isDiscovering,
                onSearchClick = { ptpipViewModel.discoverCamerasSta() }
            )
        }
    }
}

/**
 * 통합 네트워크 상태 카드 (STA 모드)
 */
@Composable
private fun StaNetworkStatusCard(
    wifiNetworkState: WifiNetworkState,
    isPtpipEnabled: Boolean,
    isWifiConnected: Boolean,
    ptpipViewModel: PtpipViewModel
) {
    val statusShape = RoundedCornerShape(Radius.md)
    SurfaceV2(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    StrokeWidth.hairline,
                    if (wifiNetworkState.isConnected)
                        SuccessV2.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                ),
                shape = statusShape
            ),
        tier = 2,
        shape = statusShape
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = if (wifiNetworkState.isConnected) SuccessV2 else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(IconSize.md)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = if (isWifiConnected) stringResource(R.string.ap_mode_wifi_connected) else stringResource(R.string.ap_mode_wifi_disconnected),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                if (!isPtpipEnabled) {
                    PrimaryButton(
                        text = stringResource(R.string.ap_mode_ptpip_enable),
                        onClick = { ptpipViewModel.setPtpipEnabled(true) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = ptpipViewModel.getNetworkStatusMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = if (wifiNetworkState.isConnected) SuccessV2 else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )

            if (wifiNetworkState.isConnected && !wifiNetworkState.isConnectedToCameraAP) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.sta_mode_general_wifi),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val comprehensiveMsg = ptpipViewModel.getComprehensiveStatusMessage()
            if (comprehensiveMsg.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = comprehensiveMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 자동 재연결 토글 카드
 */
@Composable
private fun AutoReconnectCard(
    isAutoReconnectEnabled: Boolean,
    onToggleAutoReconnect: (Boolean) -> Unit
) {
    SurfaceV2(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(StrokeWidth.hairline, DividerLine),
                shape = RoundedCornerShape(Radius.md)
            ),
        tier = 2,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.md)
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sta_mode_auto_reconnect),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isAutoReconnectEnabled) stringResource(R.string.sta_mode_auto_reconnect_on) else stringResource(R.string.sta_mode_auto_reconnect_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isAutoReconnectEnabled,
                onCheckedChange = onToggleAutoReconnect
            )
        }
    }
}

/**
 * mDNS 카메라 검색 카드
 */
@Composable
private fun MdnsSearchCard(
    isDiscovering: Boolean,
    onSearchClick: () -> Unit
) {
    val searchShape = RoundedCornerShape(Radius.md)
    SurfaceV2(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    StrokeWidth.hairline,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ),
                shape = searchShape
            ),
        tier = 2,
        shape = searchShape
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.md)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.sta_mode_mdns_search),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = stringResource(R.string.sta_mode_mdns_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            PrimaryButton(
                text = if (isDiscovering) stringResource(R.string.sta_mode_searching) else stringResource(R.string.sta_mode_search_camera),
                onClick = onSearchClick,
                enabled = !isDiscovering,
                isLoading = isDiscovering,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "STA Mode Content", showBackground = true)
@Composable
private fun StaModeContentPreview() {
    CamConTheme() {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = "STA 모드 프리뷰",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
