package com.inik.camcon.presentation.ui.screens.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.presentation.theme.SurfaceElevated
import com.inik.camcon.presentation.viewmodel.PtpipViewModel

/**
 * 폰 핫스팟 STA 모드 화면.
 *
 * 폰이 핫스팟 역할을 하고 카메라가 폰의 핫스팟에 STA로 접속하는 시나리오.
 * mDNS 광고가 도달하지 않을 수 있으므로 사용자 수동 IP 입력 카드를 함께 노출한다.
 */
@Composable
fun HotspotStaModeContent(
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
    modifier: Modifier = Modifier,
) {
    val state = HotspotStaContentState.fromWifiState(wifiNetworkState)
    val manualIp by ptpipViewModel.manualIp.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            HotspotStatusCard(state = state)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            HotspotGuideCard()
            Spacer(modifier = Modifier.height(12.dp))
        }

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

        item {
            HotspotAutoReconnectCard(
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                onToggleAutoReconnect = { ptpipViewModel.setAutoReconnectEnabled(it) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            ManualIpInputCard(
                manualIp = manualIp,
                onIpChange = { ptpipViewModel.setManualIp(it) },
                onConnect = { ptpipViewModel.connectManualCamera() },
                enabled = !isConnecting,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            HotspotMdnsSearchCard(
                isDiscovering = isDiscovering,
                onSearchClick = { ptpipViewModel.discoverCamerasHotspot() }
            )
        }
    }
}

@Composable
private fun HotspotAutoReconnectCard(
    isAutoReconnectEnabled: Boolean,
    onToggleAutoReconnect: (Boolean) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
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
                    text = if (isAutoReconnectEnabled)
                        stringResource(R.string.sta_mode_auto_reconnect_on)
                    else stringResource(R.string.sta_mode_auto_reconnect_off),
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

@Composable
private fun HotspotMdnsSearchCard(
    isDiscovering: Boolean,
    onSearchClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
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

@Composable
private fun HotspotStatusCard(state: HotspotStaContentState) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val enabled = state.status == HotspotStaContentState.HotspotStatus.ENABLED
                Icon(
                    imageVector = if (enabled) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(
                        if (enabled) R.string.ptpip_hotspot_enabled
                        else R.string.ptpip_hotspot_disabled
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            state.ssidLabel?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SSID: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.gatewayLabel?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gateway: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HotspotGuideCard() {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ptpip_hotspot_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                R.string.ptpip_hotspot_step_1,
                R.string.ptpip_hotspot_step_2,
                R.string.ptpip_hotspot_step_3,
                R.string.ptpip_hotspot_step_4,
                R.string.ptpip_hotspot_step_5,
            ).forEach { res ->
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ManualIpInputCard(
    manualIp: String,
    onIpChange: (String) -> Unit,
    onConnect: () -> Unit,
    enabled: Boolean,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.ptpip_manual_ip_input),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = manualIp,
                onValueChange = onIpChange,
                enabled = enabled,
                singleLine = true,
                placeholder = { Text("192.168.49.137") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onConnect,
                enabled = enabled && manualIp.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null
                )
                Text(
                    text = stringResource(R.string.ptpip_connect_with_ip),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
