package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.PtpipViewModel

private val HotspotStaSteps = listOf(
    "1. 폰의 모바일 핫스팟(테더링)을 켜세요",
    "2. 카메라 Wi-Fi 설정에서 'STA 모드'를 선택하세요",
    "3. 카메라에서 폰 핫스팟의 SSID/비밀번호를 입력해 접속하세요",
    "4. 카메라가 접속되면 아래에서 '카메라 찾기'를 누르거나 카메라 IP를 직접 입력하세요",
    "5. 검색은 mDNS 광고를 듣습니다. 카메라가 광고하지 않으면 IP를 직접 입력하세요"
)

/**
 * STA 모드 - 폰 핫스팟 변형.
 *
 * 폰이 AP 역할을 하고 카메라가 STA 클라이언트로 접속하는 시나리오.
 * 토폴로지상 STA_ROUTER와 동일하지만 게이트웨이가 폰 자신이라 인터넷(모바일 데이터)도 유지된다.
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
    modifier: Modifier = Modifier
) {
    val manualIp by ptpipViewModel.manualIp.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { HotspotStaDescriptionCard() }
        item { HotspotStatusCard(wifiNetworkState = wifiNetworkState) }
        item {
            ManualIpInputCard(
                ip = manualIp,
                onIpChange = { ptpipViewModel.setManualIp(it) },
                onConnect = { ptpipViewModel.connectManualCamera() },
                enabled = !isConnecting
            )
        }
        item {
            AutoReconnectToggleCard(
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                onToggle = { ptpipViewModel.setAutoReconnectEnabled(it) }
            )
        }
        item {
            WifiStatusCard(
                isWifiConnected = isWifiConnected || wifiNetworkState.isHotspotEnabled,
                isPtpipEnabled = isPtpipEnabled,
                onEnablePtpip = { ptpipViewModel.setPtpipEnabled(true) }
            )
        }
        item {
            CameraConnectionContent(
                ptpipViewModel = ptpipViewModel,
                connectionState = connectionState,
                discoveredCameras = discoveredCameras,
                isDiscovering = isDiscovering,
                isConnecting = isConnecting,
                selectedCamera = selectedCamera,
                cameraInfo = cameraInfo,
                isPtpipEnabled = isPtpipEnabled,
                isWifiConnected = isWifiConnected || wifiNetworkState.isHotspotEnabled
            )
        }
    }
}

@Composable
private fun HotspotStaDescriptionCard() {
    DarkInfoCard {
        Text(
            text = "📲 STA 모드 (폰 핫스팟)",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            color = DarkTitleTextColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "공유기 없이, 폰이 핫스팟 역할을 하고 카메라가 폰에 클라이언트로 접속합니다. 야외 촬영에 적합합니다.",
            style = MaterialTheme.typography.body2,
            color = DarkBodyTextColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        HotspotStaSteps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.caption,
                color = DarkBodyTextColor,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "💡 장점: 공유기 불필요, 모바일 데이터로 인터넷 유지 가능",
            style = MaterialTheme.typography.caption,
            color = DarkTitleTextColor,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "⚠️ 단점: 카메라가 mDNS를 광고하지 않으면 IP 직접 입력이 필요할 수 있음",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.error,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HotspotStatusCard(wifiNetworkState: WifiNetworkState) {
    DarkInfoCard {
        val enabled = wifiNetworkState.isHotspotEnabled
        Text(
            text = if (enabled) "✅ 폰 핫스팟 활성화됨" else "❌ 폰 핫스팟 꺼져 있음",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            color = if (enabled) DarkTitleTextColor else Color(0xFFFFC3C3)
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (enabled) {
            Text(
                text = "카메라가 폰에 접속한 뒤 아래에서 검색하거나 IP를 입력하세요.",
                style = MaterialTheme.typography.body2,
                color = DarkBodyTextColor
            )
            wifiNetworkState.gatewayIp?.let { gw ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "폰(게이트웨이) IP: $gw",
                    style = MaterialTheme.typography.caption,
                    color = DarkBodyTextColor
                )
            }
        } else {
            Text(
                text = "안드로이드 설정 → 모바일 핫스팟 및 테더링에서 핫스팟을 활성화해주세요.",
                style = MaterialTheme.typography.body2,
                color = DarkBodyTextColor
            )
        }
    }
}

@Composable
private fun ManualIpInputCard(
    ip: String,
    onIpChange: (String) -> Unit,
    onConnect: () -> Unit,
    enabled: Boolean
) {
    DarkInfoCard {
        Text(
            text = "🔢 카메라 IP 직접 입력",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            color = DarkTitleTextColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "카메라가 mDNS를 광고하지 않으면 IP를 직접 입력하여 연결할 수 있습니다.",
            style = MaterialTheme.typography.body2,
            color = DarkBodyTextColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = ip,
            onValueChange = onIpChange,
            label = { Text("예: 192.168.49.137") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = DarkTitleTextColor,
                cursorColor = MaterialTheme.colors.primary,
                focusedBorderColor = MaterialTheme.colors.primary,
                unfocusedBorderColor = Color(0x668D99AD),
                focusedLabelColor = MaterialTheme.colors.primary,
                unfocusedLabelColor = DarkBodyTextColor
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onConnect,
            enabled = enabled && ip.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
        ) {
            Text("이 IP로 연결")
        }
    }
}

@Composable
private fun AutoReconnectToggleCard(
    isAutoReconnectEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    DarkInfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🔄 자동 재연결",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold,
                    color = DarkTitleTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isAutoReconnectEnabled) {
                        "핫스팟 재연결 시 카메라 자동 재접속"
                    } else {
                        "수동 연결 관리"
                    },
                    style = MaterialTheme.typography.body2,
                    color = DarkBodyTextColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(checked = isAutoReconnectEnabled, onCheckedChange = onToggle)
        }
    }
}

@Preview(name = "Hotspot STA - Description", showBackground = true)
@Composable
private fun HotspotStaDescriptionPreview() {
    CamConTheme { HotspotStaDescriptionCard() }
}

@Preview(name = "Hotspot STA - Manual IP", showBackground = true)
@Composable
private fun ManualIpInputPreview() {
    CamConTheme {
        ManualIpInputCard(
            ip = "192.168.49.137",
            onIpChange = {},
            onConnect = {},
            enabled = true
        )
    }
}
