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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
private val StaModeSteps = listOf(
    "1. 카메라와 스마트폰을 동일한 Wi-Fi 네트워크에 연결하세요",
    "2. 카메라에서 Wi-Fi 기능을 활성화하고 'STA 모드'를 선택하세요",
    "3. 카메라 메뉴에서 집 또는 사무실 Wi-Fi 네트워크를 선택하세요",
    "4. 네트워크 비밀번호를 입력하여 연결하세요",
    "5. 연결 후 아래 '카메라 찾기' 버튼을 눌러 검색하세요"
)

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
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StaModeDescriptionCard()
        }

        // 실시간 네트워크 상태 카드
        item {
            NetworkStatusCard(
                wifiNetworkState = wifiNetworkState,
                ptpipViewModel = ptpipViewModel
            )
        }

        // 자동 재연결 설정 카드
        item {
            AutoReconnectCard(
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                onToggleAutoReconnect = { ptpipViewModel.setAutoReconnectEnabled(it) }
            )
        }

        // Wi-Fi 기능 정보 카드
        item {
            WifiCapabilitiesCard(
                wifiCapabilities = wifiCapabilities,
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = onRequestPermission
            )
        }

        // 공통 Wi-Fi 상태 카드
        item {
            WifiStatusCard(
                isWifiConnected = isWifiConnected,
                isPtpipEnabled = isPtpipEnabled,
                onEnablePtpip = { ptpipViewModel.setPtpipEnabled(true) }
            )
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
                isWifiConnected = isWifiConnected
            )
        }
    }
}

/**
 * STA 모드 설명 카드
 */
@Composable
private fun StaModeDescriptionCard() {
    DarkInfoCard {
            Text(
                text = "🏠 STA 모드 (스테이션)",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = DarkTitleTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "카메라와 스마트폰이 동일한 Wi-Fi 네트워크에 연결하는 방식입니다.",
                style = MaterialTheme.typography.body2,
                color = DarkBodyTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            StaModeSteps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.caption,
                    color = DarkBodyTextColor,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 장점: 카메라와 스마트폰 모두 인터넷에 연결된 상태를 유지할 수 있습니다.",
                style = MaterialTheme.typography.caption,
                color = DarkTitleTextColor,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "⚠️ 단점: 네트워크 설정이 복잡할 수 있습니다.",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error,
                fontWeight = FontWeight.Medium
            )
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
    DarkInfoCard {
            Text(
                text = "📶 실시간 네트워크 상태",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = DarkTitleTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = ptpipViewModel.getNetworkStatusMessage(),
                style = MaterialTheme.typography.body2,
                color = if (wifiNetworkState.isConnected) {
                    DarkTitleTextColor
                } else {
                    Color(0xFFFFC3C3)
                },
                fontWeight = FontWeight.Medium
            )

            if (wifiNetworkState.isConnected && !wifiNetworkState.isConnectedToCameraAP) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "일반 Wi-Fi 네트워크에 연결되어 mDNS 검색 가능",
                    style = MaterialTheme.typography.caption,
                    color = DarkTitleTextColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = ptpipViewModel.getComprehensiveStatusMessage(),
                style = MaterialTheme.typography.caption,
                color = DarkBodyTextColor
            )
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
    DarkInfoCard {
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
                        color = DarkTitleTextColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isAutoReconnectEnabled) {
                            "Wi-Fi 네트워크 변화 시 자동으로 카메라 재연결 시도"
                        } else {
                            "수동으로 카메라 연결 관리"
                        },
                        style = MaterialTheme.typography.body2,
                        color = DarkBodyTextColor
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

@Preview(name = "STA Mode Description Card", showBackground = true)
@Composable
private fun StaModeDescriptionCardPreview() {
    CamConTheme {
        StaModeDescriptionCard()
    }
}

@Preview(name = "STA Mode Content", showBackground = true)
@Composable
private fun StaModeContentPreview() {
    CamConTheme {
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
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(16.dp)
                )
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