package com.inik.camcon.presentation.ui.screens.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.inik.camcon.R
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
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
    val context = LocalContext.current

    // 핫스팟 설정을 켜고 화면으로 돌아오면 상태를 다시 읽어 카드/검색 활성을 갱신한다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ptpipViewModel.refreshHotspotState()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.base, vertical = Spacing.md)
    ) {
        item {
            HotspotStatusCard(state = state)
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        if (state.status == HotspotStaContentState.HotspotStatus.DISABLED) {
            item {
                HotspotEnableCard(
                    onOpenSettings = {
                        openHotspotSettings(context)
                        ptpipViewModel.refreshHotspotState()
                    }
                )
                Spacer(modifier = Modifier.height(Spacing.md))
            }
        }

        item {
            HotspotGuideCard()
            Spacer(modifier = Modifier.height(Spacing.md))
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
                Spacer(modifier = Modifier.height(Spacing.md))
            }
        }

        item {
            HotspotAutoReconnectCard(
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                onToggleAutoReconnect = { ptpipViewModel.setAutoReconnectEnabled(it) }
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        item {
            ManualIpInputCard(
                manualIp = manualIp,
                onIpChange = { ptpipViewModel.setManualIp(it) },
                onConnect = { ptpipViewModel.connectManualCamera() },
                enabled = !isConnecting,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        item {
            HotspotMdnsSearchCard(
                isDiscovering = isDiscovering,
                onSearchClick = { ptpipViewModel.discoverCamerasHotspot() }
            )
        }
    }
}

/**
 * 폰 핫스팟이 꺼져 있을 때 노출되는 카드.
 *
 * Android 정책상 일반 앱은 표준 모바일 핫스팟을 코드로 직접 켤 수 없으므로
 * (TETHER_PRIVILEGED = signature 권한), 테더링 설정 화면으로 사용자를 안내한다.
 */
@Composable
private fun HotspotEnableCard(onOpenSettings: () -> Unit) {
    SurfaceV2(
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(StrokeWidth.hairline, DividerLine),
                shape = RoundedCornerShape(Radius.md)
            ),
        tier = 2
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.md)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.ptpip_hotspot_enable_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.ptpip_hotspot_enable_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            PrimaryButton(
                text = stringResource(R.string.ptpip_hotspot_open_settings),
                onClick = onOpenSettings,
                leadingIcon = Icons.Filled.Wifi,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 테더링 설정 화면으로 이동한다.
 * 공개·보장된 단일 API가 없어 [후보 인텐트들]을 순서대로 시도하고 실패 시 다음으로 폴백한다.
 * 1) OS가 쓰는 정식 컴포넌트(OEM 파편화 위험) → 2) 액션 문자열 → 3) 무선 설정 → 4) 일반 설정.
 */
private fun openHotspotSettings(context: Context) {
    val candidates = listOf(
        Intent().setClassName(
            "com.android.settings",
            "com.android.settings.TetherSettings"
        ),
        Intent("com.android.settings.TETHER_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

@Composable
private fun HotspotAutoReconnectCard(
    isAutoReconnectEnabled: Boolean,
    onToggleAutoReconnect: (Boolean) -> Unit
) {
    SurfaceV2(
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(StrokeWidth.hairline, DividerLine),
                shape = RoundedCornerShape(Radius.md)
            ),
        tier = 2
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
    SurfaceV2(
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(StrokeWidth.hairline, DividerLine),
                shape = RoundedCornerShape(Radius.md)
            ),
        tier = 2
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

@Composable
private fun HotspotStatusCard(state: HotspotStaContentState) {
    SurfaceV2(
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(StrokeWidth.hairline, DividerLine),
                shape = RoundedCornerShape(Radius.md)
            ),
        tier = 2
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
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
                    modifier = Modifier.padding(start = Spacing.sm)
                )
            }

            state.ssidLabel?.let {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "SSID: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.gatewayLabel?.let {
                Spacer(modifier = Modifier.height(Spacing.xs))
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
    SurfaceV2(
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(StrokeWidth.hairline, DividerLine),
                shape = RoundedCornerShape(Radius.md)
            ),
        tier = 2
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Text(
                text = stringResource(R.string.ptpip_hotspot_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
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
                    modifier = Modifier.padding(vertical = Spacing.xs)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.ptpip_hotspot_compat_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    SurfaceV2(
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(StrokeWidth.hairline, DividerLine),
                shape = RoundedCornerShape(Radius.md)
            ),
        tier = 2
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Text(
                text = stringResource(R.string.ptpip_manual_ip_input),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = manualIp,
                onValueChange = onIpChange,
                enabled = enabled,
                singleLine = true,
                placeholder = { Text("192.168.49.137") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            PrimaryButton(
                text = stringResource(R.string.ptpip_connect_with_ip),
                onClick = onConnect,
                leadingIcon = Icons.Filled.Search,
                enabled = enabled && manualIp.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
