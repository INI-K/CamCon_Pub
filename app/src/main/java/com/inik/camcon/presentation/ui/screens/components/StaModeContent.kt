package com.inik.camcon.presentation.ui.screens.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.inik.camcon.presentation.theme.Border
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Success
import com.inik.camcon.presentation.theme.SurfaceElevated
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 통합 네트워크 상태 카드
        item {
            StaNetworkStatusCard(
                wifiNetworkState = wifiNetworkState,
                isPtpipEnabled = isPtpipEnabled,
                isWifiConnected = isWifiConnected,
                ptpipViewModel = ptpipViewModel
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 자동 재연결 토글
        item {
            AutoReconnectCard(
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                onToggleAutoReconnect = { ptpipViewModel.setAutoReconnectEnabled(it) }
            )
            Spacer(modifier = Modifier.height(12.dp))
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
                Spacer(modifier = Modifier.height(12.dp))
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
                Spacer(modifier = Modifier.height(12.dp))
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (wifiNetworkState.isConnected)
                    Success.copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.medium
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isWifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = if (wifiNetworkState.isConnected) Success else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isWifiConnected) stringResource(R.string.ap_mode_wifi_connected) else stringResource(R.string.ap_mode_wifi_disconnected),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                if (!isPtpipEnabled) {
                    Button(onClick = { ptpipViewModel.setPtpipEnabled(true) }) {
                        Text(stringResource(R.string.ap_mode_ptpip_enable))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = ptpipViewModel.getNetworkStatusMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = if (wifiNetworkState.isConnected) Success else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )

            if (wifiNetworkState.isConnected && !wifiNetworkState.isConnectedToCameraAP) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sta_mode_general_wifi),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val comprehensiveMsg = ptpipViewModel.getComprehensiveStatusMessage()
            if (comprehensiveMsg.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Border,
                shape = MaterialTheme.shapes.medium
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.medium
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.sta_mode_mdns_search),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.sta_mode_mdns_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSearchClick,
                enabled = !isDiscovering,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sta_mode_searching))
                } else {
                    Text(stringResource(R.string.sta_mode_search_camera))
                }
            }
        }
    }
}

@Preview(name = "STA Mode Content", showBackground = true)
@Composable
private fun StaModeContentPreview() {
    CamConTheme(themeMode = ThemeMode.DARK) {
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
