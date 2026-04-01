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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.inik.camcon.domain.model.SavedWifiCredential
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import com.inik.camcon.data.datasource.local.ThemeMode

/**
 * AP 모드 화면 컴포넌트 (WiFi Specification 기반)
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
    // 전역 상태 수집
    val globalConnectionState by ptpipViewModel.globalConnectionState.collectAsStateWithLifecycle()
    val activeConnectionType by ptpipViewModel.activeConnectionType.collectAsStateWithLifecycle()
    val connectionStatusMessage by ptpipViewModel.connectionStatusMessage.collectAsStateWithLifecycle()
    val autoDownloadEnabled by ptpipViewModel.autoDownloadEnabled.collectAsStateWithLifecycle()
    val lastDownloadedFile by ptpipViewModel.lastDownloadedFile.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 실시간 네트워크 상태 카드
        item {
            NetworkStatusCard(
                wifiNetworkState = wifiNetworkState,
                ptpipViewModel = ptpipViewModel
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Wi-Fi 기능 정보 카드
        item {
            WifiCapabilitiesCard(
                wifiCapabilities = wifiCapabilities,
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = onRequestPermission
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 공통 Wi-Fi 상태 카드
        item {
            WifiStatusCard(
                isWifiConnected = isWifiConnected,
                isPtpipEnabled = isPtpipEnabled,
                onEnablePtpip = { ptpipViewModel.setPtpipEnabled(true) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 주변 Wi‑Fi 스캔 결과 표시
        item {
            if (nearbyWifiSSIDs.isNotEmpty()) {
                WifiScanResultsCard(
                    ssids = nearbyWifiSSIDs,
                    onConnectToWifi = onConnectToWifi,
                    savedSsids = savedWifiSsids
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 저장된 Wi-Fi 네트워크 관리
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
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 공통 카메라 연결 및 검색 UI
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
                isWifiConnected = isWifiConnected,
                isApMode = true,
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = onRequestPermission,
                nearbyWifiSSIDs = nearbyWifiSSIDs
            )
        }
    }
}



/**
 * AP 모드 설명 카드
 */
@Composable
private fun ApModeDescriptionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(
                alpha = 0.1f
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📡 AP 모드 (액세스 포인트)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "카메라가 Wi-Fi 핫스팟을 생성하여 스마트폰이 직접 연결하는 방식입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            val apModeSteps = listOf(
                "1. 카메라에서 Wi-Fi 기능을 켜세요",
                "2. 카메라 메뉴에서 'AP 모드' 또는 '액세스 포인트 모드'를 선택하세요",
                "3. 카메라가 Wi-Fi 핫스팟을 생성합니다",
                "4. 아래 '주변 Wi-Fi 스캔' 버튼을 눌러 카메라 네트워크를 찾으세요",
                "5. 검색된 카메라 네트워크를 선택하고 비밀번호를 입력하세요"
            )

            apModeSteps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 장점: 설정이 간단하고 빠른 연결이 가능합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "⚠️ 단점: 스마트폰이 인터넷에 연결되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 실시간 네트워크 상태 카드 (AP 모드용)
 */
@Composable
private fun NetworkStatusCard(
    wifiNetworkState: WifiNetworkState,
    ptpipViewModel: PtpipViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (wifiNetworkState.isConnected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📶 실시간 네트워크 상태",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = ptpipViewModel.getNetworkStatusMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (wifiNetworkState.isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Medium
            )

            if (wifiNetworkState.isConnectedToCameraAP && wifiNetworkState.detectedCameraIP != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "감지된 카메라 IP: ${wifiNetworkState.detectedCameraIP}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (wifiNetworkState.isConnected && wifiNetworkState.isConnectedToCameraAP) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "카메라 AP에 직접 연결 (Direct Connection)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = ptpipViewModel.getComprehensiveStatusMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 자동 재연결 설정 카드
 */
@Composable
private fun AutoReconnectCard(
    isAutoReconnectEnabled: Boolean,
    onToggleAutoReconnect: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🔄 자동 재연결",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isAutoReconnectEnabled) {
                            "Wi-Fi 연결 변화 시 자동으로 카메라 재연결 시도"
                        } else {
                            "수동으로 카메라 연결 관리"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = isAutoReconnectEnabled,
                    onCheckedChange = onToggleAutoReconnect
                )
            }
        }
    }
}

/**
 * 카메라 AP 연결 상태 카드
 */
@Composable
private fun CameraAPConnectionCard(
    wifiCapabilities: WifiCapabilities
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (wifiCapabilities.isConnectedToCameraAP) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = if (wifiCapabilities.isConnectedToCameraAP) {
                    "✅ 카메라 AP에 연결됨"
                } else {
                    "❌ 카메라 AP에 연결되지 않음"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (wifiCapabilities.isConnectedToCameraAP) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (wifiCapabilities.isConnectedToCameraAP) {
                wifiCapabilities.networkName?.let { networkName ->
                    Text(
                        text = "연결된 네트워크: $networkName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                wifiCapabilities.detectedCameraIP?.let { cameraIP ->
                    Text(
                        text = "감지된 카메라 IP: $cameraIP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = "카메라 Wi-Fi 핫스팟에 연결 후 카메라 검색을 시작할 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Preview(name = "AP Mode Description Card", showBackground = true)
@Composable
private fun ApModeDescriptionCardPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        ApModeDescriptionCard()
    }
}

@Preview(name = "AP Mode Content", showBackground = true)
@Composable
private fun ApModeContentPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        // 미리보기용 더미 데이터
        val dummyWifiCapabilities = WifiCapabilities(
            isConnected = true,
            isStaConcurrencySupported = true,
            isConnectedToCameraAP = true,
            networkName = "Canon_EOS_R5_AP",
            linkSpeed = 100,
            frequency = 2400,
            ipAddress = 0xC0A80101.toInt(), // 192.168.1.1 in hex as Int
            macAddress = "00:11:22:33:44:55",
            detectedCameraIP = "192.168.1.1"
        )

        val dummyCamera = PtpipCamera(
            ipAddress = "192.168.1.100",
            port = 15740,
            name = "Canon EOS R5",
            isOnline = true
        )

        // 프리뷰는 단순화된 형태로 표시
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                ApModeDescriptionCard()
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text(
                    text = "카메라 연결 UI 영역",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🔑 저장된 Wi-Fi 네트워크 (${credentials.size}개)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "저장된 비밀번호로 빠르게 연결할 수 있습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            credentials.forEach { credential ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = credential.ssid,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${credential.security} · ${
                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(credential.lastConnectedAt))
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = { ssidToDelete = credential.ssid }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // 삭제 확인 다이얼로그
    ssidToDelete?.let { ssid ->
        AlertDialog(
            onDismissRequest = { ssidToDelete = null },
            title = { Text("저장된 네트워크 삭제") },
            text = { Text("'$ssid'의 저장된 비밀번호를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(ssid)
                    ssidToDelete = null
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { ssidToDelete = null }) {
                    Text("취소")
                }
            }
        )
    }
}