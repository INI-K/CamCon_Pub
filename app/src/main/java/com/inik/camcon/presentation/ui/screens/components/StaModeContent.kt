package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * STA 모드 화면 컴포넌트 (WiFi Specification 기반)
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

        // 자동 재연결 설정 카드
        item {
            AutoReconnectCard(
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                onToggleAutoReconnect = { ptpipViewModel.setAutoReconnectEnabled(it) }
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
                    isStaMode = true
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // mDNS 검색 카드 추가
        item {
            MdnsSearchCard(
                isDiscovering = isDiscovering,
                onSearchClick = { ptpipViewModel.discoverCamerasSta() }
            )
            Spacer(modifier = Modifier.height(16.dp))
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
                isApMode = false,
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = onRequestPermission,
                nearbyWifiSSIDs = nearbyWifiSSIDs
            )
        }
    }
}

/**
 * STA 모드 설명 카드
 */
@Composable
private fun StaModeDescriptionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary.copy(
                alpha = 0.1f
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🏠 STA 모드 (스테이션)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "카메라와 스마트폰이 동일한 Wi-Fi 네트워크에 연결하는 방식입니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            val staModeSteps = listOf(
                "1. 카메라에서 Wi-Fi 기능을 켜고 'STA 모드'를 선택하세요",
                "2. 카메라 메뉴에서 집 또는 사무실 Wi-Fi 네트워크를 선택하세요",
                "3. 카메라에 네트워크 비밀번호를 입력하여 연결하세요",
                "4. 아래 '주변 Wi-Fi 스캔' 버튼을 눌러 동일한 네트워크를 찾으세요",
                "5. 검색된 네트워크를 선택하고 비밀번호를 입력하세요"
            )

            staModeSteps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 장점: 카메라와 스마트폰 모두 인터넷에 연결된 상태를 유지할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "⚠️ 단점: 네트워크 설정이 복잡할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 실시간 네트워크 상태 카드 (STA 모드용)
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
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
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
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = ptpipViewModel.getNetworkStatusMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (wifiNetworkState.isConnected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Medium
            )

            if (wifiNetworkState.isConnected && !wifiNetworkState.isConnectedToCameraAP) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "일반 Wi-Fi 네트워크에 연결되어 mDNS 검색 가능",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
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
 * 자동 재연결 설정 카드 (STA 모드용)
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
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isAutoReconnectEnabled) {
                            "Wi-Fi 네트워크 변화 시 자동으로 카메라 재연결 시도"
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
 * mDNS 검색 카드 (STA 모드 전용)
 */
@Composable
private fun MdnsSearchCard(
    isDiscovering: Boolean,
    onSearchClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🔍 mDNS 카메라 검색",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "같은 네트워크에 연결된 카메라를 자동으로 검색합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "📋 검색 순서:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            val searchSteps = listOf(
                "1. 캐시된 IP 확인 (0.5초)",
                "2. mDNS 검색 (2-5초)",
                "3. 기본 IP 시도 (192.168.1.1)"
            )

            searchSteps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSearchClick,
                enabled = !isDiscovering,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(24.dp)
                            .width(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("검색 중...")
                } else {
                    Text("카메라 검색 시작")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 팁: 카메라가 같은 Wi-Fi 네트워크에 연결되어 있는지 확인하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(name = "STA Mode Description Card", showBackground = true)
@Composable
private fun StaModeDescriptionCardPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        StaModeDescriptionCard()
    }
}

@Preview(name = "STA Mode Content", showBackground = true)
@Composable
private fun StaModeContentPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        // 프리뷰는 단순화된 형태로 표시
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                StaModeDescriptionCard()
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Text(
                    text = "Wi-Fi 기능 정보 카드 영역",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
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