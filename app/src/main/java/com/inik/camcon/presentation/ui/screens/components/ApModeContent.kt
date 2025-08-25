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
import androidx.compose.material.Badge
import androidx.compose.material.BadgedBox
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
 * AP 모드 화면 컴포넌트 (심플화)
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
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 주변 Wi‑Fi 스캔 결과만 표시
        item {
            if (nearbyWifiSSIDs.isNotEmpty()) {
                WifiScanResultsCard(
                    ssids = nearbyWifiSSIDs,
                    onConnectToWifi = onConnectToWifi
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 카메라 연결 및 검색 UI (버튼 통합: 주변 Wi‑Fi 스캔)
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
                onRequestPermission = onRequestPermission
            )
        }
    }
}

/**
 * 주변 Wi‑Fi 스캔 결과 카드 (전체 목록 표시)
 */
@Composable
private fun WifiScanResultsCard(
    ssids: List<String>,
    onConnectToWifi: (String) -> Unit
) {
    // 카메라 제조사 패턴 목록
    val cameraManufacturers = listOf(
        "CANON",
        "NIKON",
        "SONY",
        "FUJIFILM",
        "OLYMPUS",
        "PANASONIC",
        "PENTAX",
        "LEICA",
        "LUMIX"
    )

    // SSID를 카메라 제조사 포함 여부로 분류
    val (cameraSsids, otherSsids) = ssids.partition { ssid ->
        cameraManufacturers.any { manufacturer ->
            ssid.contains(manufacturer, ignoreCase = true)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🔎 주변 카메라 Wi‑Fi (${ssids.size}개)",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )

            if (cameraSsids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📷 카메라 네트워크 (${cameraSsids.size}개)",
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 카메라 제조사가 포함된 SSID 먼저 표시
            cameraSsids.forEach { ssid ->
                val detectedManufacturer = cameraManufacturers.find { manufacturer ->
                    ssid.contains(manufacturer, ignoreCase = true)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    elevation = 2.dp,
                    backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = ssid,
                                style = MaterialTheme.typography.body1,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colors.primary
                            )
                            if (detectedManufacturer != null) {
                                Text(
                                    text = "📷 $detectedManufacturer 카메라",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.primary.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onConnectToWifi(ssid) },
                            modifier = Modifier.size(width = 60.dp, height = 36.dp)
                        ) {
                            Text(
                                "연결",
                                style = MaterialTheme.typography.caption
                            )
                        }
                    }
                }
            }

            // 일반 Wi-Fi 네트워크
            if (otherSsids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📡 기타 네트워크 (${otherSsids.size}개)",
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))

                otherSsids.forEach { ssid ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = ssid,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { onConnectToWifi(ssid) }) {
                            Text("연결")
                        }
                    }
                }
            }
        }
    }
}

// ... 기존 보조 컴포넌트 정의는 유지 (미사용)

/**
 * AP 모드 설명 카드
 */
@Composable
private fun ApModeDescriptionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📡 AP 모드 (액세스 포인트)",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "카메라가 Wi-Fi 핫스팟을 생성하여 스마트폰이 직접 연결하는 방식입니다.",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            val apModeSteps = listOf(
                "1. 카메라에서 Wi-Fi 기능을 켜세요",
                "2. 카메라 메뉴에서 'AP 모드' 또는 '액세스 포인트 모드'를 선택하세요",
                "3. 카메라가 Wi-Fi 핫스팟을 생성합니다",
                "4. 스마트폰 Wi-Fi 설정에서 카메라 네트워크를 선택하세요",
                "5. 연결 후 아래 'Wi‑Fi 스캔' 버튼을 눌러 검색하세요"
            )

            apModeSteps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 장점: 설정이 간단하고 빠른 연결이 가능합니다.",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "⚠️ 단점: 스마트폰이 인터넷에 연결되지 않습니다.",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 실시간 네트워크 상태 카드
 */
@Composable
private fun NetworkStatusCard(
    wifiNetworkState: WifiNetworkState,
    ptpipViewModel: PtpipViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        backgroundColor = if (wifiNetworkState.isConnected) {
            MaterialTheme.colors.primary.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colors.error.copy(alpha = 0.1f)
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📶 실시간 네트워크 상태",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = ptpipViewModel.getNetworkStatusMessage(),
                style = MaterialTheme.typography.body2,
                color = if (wifiNetworkState.isConnected) {
                    MaterialTheme.colors.primary
                } else {
                    MaterialTheme.colors.error
                },
                fontWeight = FontWeight.Medium
            )

            if (wifiNetworkState.isConnectedToCameraAP && wifiNetworkState.detectedCameraIP != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "감지된 카메라 IP: ${wifiNetworkState.detectedCameraIP}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = ptpipViewModel.getComprehensiveStatusMessage(),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
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
        elevation = 4.dp
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
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isAutoReconnectEnabled) {
                            "Wi-Fi 연결 변화 시 자동으로 카메라 재연결 시도"
                        } else {
                            "수동으로 카메라 연결 관리"
                        },
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
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
        elevation = 4.dp,
        backgroundColor = if (wifiCapabilities.isConnectedToCameraAP) {
            MaterialTheme.colors.primary.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colors.error.copy(alpha = 0.1f)
        }
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
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = if (wifiCapabilities.isConnectedToCameraAP) {
                    MaterialTheme.colors.primary
                } else {
                    MaterialTheme.colors.error
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (wifiCapabilities.isConnectedToCameraAP) {
                wifiCapabilities.networkName?.let { networkName ->
                    Text(
                        text = "연결된 네트워크: $networkName",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
                    )
                }

                wifiCapabilities.detectedCameraIP?.let { cameraIP ->
                    Text(
                        text = "감지된 카메라 IP: $cameraIP",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = "카메라 Wi-Fi 핫스팟에 연결 후 카메라 검색을 시작할 수 있습니다.",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
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
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}