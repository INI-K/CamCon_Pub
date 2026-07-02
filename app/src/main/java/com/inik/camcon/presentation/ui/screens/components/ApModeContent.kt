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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.inik.camcon.domain.model.SavedWifiCredential
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.SuccessV2
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import com.inik.camcon.domain.model.ThemeMode
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * AP 모드 화면 컴포넌트
 */
@Composable
fun ApModeContent(
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
    savedWifiSsids: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.base, vertical = Spacing.md)
    ) {
        // 통합 네트워크 상태 카드 (NetworkStatus + WifiStatus + PTP/IP 활성화)
        item {
            ApNetworkStatusCard(
                wifiNetworkState = wifiNetworkState,
                isPtpipEnabled = isPtpipEnabled,
                isWifiConnected = isWifiConnected,
                ptpipViewModel = ptpipViewModel
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
                    savedSsids = savedWifiSsids
                )
                Spacer(modifier = Modifier.height(Spacing.md))
            }
        } else if (isPtpipEnabled) {
            // 스캔 결과 없을 때 안내
            item {
                ScanPromptCard(
                    isDiscovering = isDiscovering,
                    hasLocationPermission = hasLocationPermission,
                    onScan = { ptpipViewModel.scanNearbyWifiNetworks() },
                    onRequestPermission = onRequestPermission
                )
                Spacer(modifier = Modifier.height(Spacing.md))
            }
        }

        // 저장된 Wi-Fi 네트워크
        item {
            val savedCredentials by ptpipViewModel.savedWifiCredentials
                .collectAsStateWithLifecycle(initialValue = emptyList())
            if (savedCredentials.isNotEmpty()) {
                SavedWifiNetworksCard(
                    credentials = savedCredentials,
                    onDelete = { ssid ->
                        ptpipViewModel.deleteSavedWifiCredential(ssid)
                    }
                )
            }
        }
    }
}

/**
 * 통합 네트워크 상태 카드 (AP 모드)
 * - Wi-Fi 연결 상태 + PTP/IP 활성화 + 카메라 AP 감지
 */
@Composable
private fun ApNetworkStatusCard(
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

                // PTP/IP 비활성일 때 활성화 버튼
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

            if (wifiNetworkState.isConnectedToCameraAP && wifiNetworkState.detectedCameraIP != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.ap_mode_camera_ip, wifiNetworkState.detectedCameraIP ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (wifiNetworkState.isConnected && wifiNetworkState.isConnectedToCameraAP) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.ap_mode_camera_ap_direct),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 상세 상태 (한 줄 요약)
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
 * 스캔 안내 카드 - 스캔 결과가 없을 때 표시
 */
@Composable
private fun ScanPromptCard(
    isDiscovering: Boolean,
    hasLocationPermission: Boolean,
    onScan: () -> Unit,
    onRequestPermission: () -> Unit
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(IconSize.xl)
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.ap_mode_search_camera_wifi),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.ap_mode_turn_on_camera_wifi),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            PrimaryButton(
                text = if (isDiscovering) stringResource(R.string.ap_mode_scanning) else stringResource(R.string.ap_mode_wifi_scan),
                onClick = {
                    if (hasLocationPermission) onScan() else onRequestPermission()
                },
                enabled = !isDiscovering
            )
        }
    }
}

/**
 * 저장된 Wi-Fi 네트워크 관리 카드
 */
@Composable
private fun SavedWifiNetworksCard(
    credentials: List<SavedWifiCredential>,
    onDelete: (String) -> Unit
) {
    var ssidToDelete by remember { mutableStateOf<String?>(null) }

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
        Column(modifier = Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.md)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.ap_mode_saved_networks, credentials.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))

            credentials.forEach { credential ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = credential.ssid,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${credential.security} · ${
                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(credential.lastConnectedAt))
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { ssidToDelete = credential.ssid }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.cd_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(IconSize.md)
                        )
                    }
                }
            }
        }
    }

    ssidToDelete?.let { ssid ->
        AppDialog(
            onDismissRequest = { ssidToDelete = null },
            title = { Text(stringResource(R.string.ap_mode_delete_saved_network)) },
            text = { Text(stringResource(R.string.ap_mode_delete_saved_network_confirm, ssid)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(ssid)
                    ssidToDelete = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { ssidToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Preview(name = "AP Mode Content", showBackground = true)
@Composable
private fun ApModeContentPreview() {
    CamConTheme() {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = "AP 모드 프리뷰",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
