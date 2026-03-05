package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
private val ApModeSteps = listOf(
    "1. 카메라에서 Wi-Fi 기능을 켜세요",
    "2. 카메라 메뉴에서 'AP 모드' 또는 '액세스 포인트 모드'를 선택하세요",
    "3. 카메라가 Wi-Fi 핫스팟을 생성합니다",
    "4. 스마트폰 Wi-Fi 설정에서 카메라 네트워크를 선택하세요",
    "5. 연결 후 아래 '카메라 찾기' 버튼을 눌러 검색하세요"
)

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
    modifier: Modifier = Modifier
) {
    // 전역 상태 수집
    val globalConnectionState by ptpipViewModel.globalConnectionState.collectAsState()
    val activeConnectionType by ptpipViewModel.activeConnectionType.collectAsState()
    val connectionStatusMessage by ptpipViewModel.connectionStatusMessage.collectAsState()
    val autoDownloadEnabled by ptpipViewModel.autoDownloadEnabled.collectAsState()
    val lastDownloadedFile by ptpipViewModel.lastDownloadedFile.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ApModeDescriptionCard()
        }

        // 전역 연결 상태 카드 (새로 추가)
        item {
            GlobalConnectionStatusCard(
                connectionStatusMessage = connectionStatusMessage,
                activeConnectionType = activeConnectionType,
                globalConnectionState = globalConnectionState
            )
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

        // 자동 파일 다운로드 설정 카드
        item {
            if (autoDownloadEnabled) {
                AutoDownloadStatusCard(lastDownloadedFile = lastDownloadedFile)
            }
        }

        item {
            WifiStatusCard(
                isWifiConnected = isWifiConnected,
                isPtpipEnabled = isPtpipEnabled,
                onEnablePtpip = { ptpipViewModel.setPtpipEnabled(true) }
            )
        }

        item {
            CameraAPConnectionCard(
                wifiCapabilities = wifiCapabilities
            )
        }

        // 공통 카메라 연결 및 검색 UI
        item {
            BadgedBox(
                badge = {
                    if (autoDownloadEnabled) {
                        Badge(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = MaterialTheme.colors.onPrimary
                        ) {
                            Text("AUTO")
                        }
                    }
                }
            ) {
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
}

@Composable
private fun AutoDownloadStatusCard(lastDownloadedFile: String?) {
    DarkInfoCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "자동 다운로드",
                tint = MaterialTheme.colors.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "자동 파일 다운로드 활성화",
                style = MaterialTheme.typography.h6,
                color = DarkTitleTextColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "카메라에서 촬영한 사진이 자동으로 스마트폰에 저장됩니다.",
            style = MaterialTheme.typography.body2,
            color = DarkBodyTextColor
        )
        lastDownloadedFile?.let { fileName ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 2.dp,
                backgroundColor = Color(0xFF111827),
                border = BorderStroke(1.dp, Color(0x338D99AD))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GetApp,
                        contentDescription = "다운로드 완료",
                        tint = MaterialTheme.colors.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "최근 다운로드: $fileName",
                        style = MaterialTheme.typography.caption,
                        color = DarkBodyTextColor
                    )
                }
            }
        }
    }
}

/**
 * 전역 연결 상태 카드 (새로 추가)
 */
@Composable
private fun GlobalConnectionStatusCard(
    connectionStatusMessage: String,
    activeConnectionType: com.inik.camcon.domain.model.CameraConnectionType?,
    globalConnectionState: com.inik.camcon.domain.model.GlobalCameraConnectionState
) {
    DarkInfoCard {
            Text(
                text = "🌐 전역 연결 상태",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = DarkTitleTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = connectionStatusMessage,
                style = MaterialTheme.typography.body1,
                color = DarkBodyTextColor,
                fontWeight = FontWeight.Medium
            )

            activeConnectionType?.let { type ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "활성 연결: ${getConnectionTypeText(type)}",
                    style = MaterialTheme.typography.caption,
                    color = DarkBodyTextColor
                )
            }

            if (globalConnectionState.discoveredCameras.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "발견된 카메라: ${globalConnectionState.discoveredCameras.size}개",
                    style = MaterialTheme.typography.caption,
                    color = DarkTitleTextColor
                )
            }
    }
}

/**
 * 연결 타입을 한국어로 변환
 */
private fun getConnectionTypeText(type: com.inik.camcon.domain.model.CameraConnectionType): String {
    return when (type) {
        com.inik.camcon.domain.model.CameraConnectionType.USB -> "USB 연결"
        com.inik.camcon.domain.model.CameraConnectionType.AP_MODE -> "AP 모드"
        com.inik.camcon.domain.model.CameraConnectionType.STA_MODE -> "STA 모드"
    }
}

/**
 * AP 모드 설명 카드
 */
@Composable
private fun ApModeDescriptionCard() {
    DarkInfoCard {
            Text(
                text = "📡 AP 모드 (액세스 포인트)",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = DarkTitleTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "카메라가 Wi-Fi 핫스팟을 생성하여 스마트폰이 직접 연결하는 방식입니다.",
                style = MaterialTheme.typography.body2,
                color = DarkBodyTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            ApModeSteps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.caption,
                    color = DarkBodyTextColor,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "💡 장점: 설정이 간단하고 빠른 연결이 가능합니다.",
                style = MaterialTheme.typography.caption,
                color = DarkTitleTextColor,
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

/**
 * 실시간 네트워크 상태 카드
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
                color = if (wifiNetworkState.isConnected) DarkTitleTextColor else Color(0xFFFFC3C3),
                fontWeight = FontWeight.Medium
            )

            if (wifiNetworkState.isConnectedToCameraAP && wifiNetworkState.detectedCameraIP != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "감지된 카메라 IP: ${wifiNetworkState.detectedCameraIP}",
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
 * 자동 재연결 설정 카드
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
                            "Wi-Fi 연결 변화 시 자동으로 카메라 재연결 시도"
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

/**
 * 카메라 AP 연결 상태 카드
 */
@Composable
private fun CameraAPConnectionCard(
    wifiCapabilities: WifiCapabilities
) {
    DarkInfoCard {
            Text(
                text = if (wifiCapabilities.isConnectedToCameraAP) {
                    "✅ 카메라 AP에 연결됨"
                } else {
                    "❌ 카메라 AP에 연결되지 않음"
                },
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = if (wifiCapabilities.isConnectedToCameraAP) {
                    DarkTitleTextColor
                } else {
                    Color(0xFFFFC3C3)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (wifiCapabilities.isConnectedToCameraAP) {
                wifiCapabilities.networkName?.let { networkName ->
                    Text(
                        text = "연결된 네트워크: $networkName",
                        style = MaterialTheme.typography.body2,
                        color = DarkBodyTextColor
                    )
                }

                wifiCapabilities.detectedCameraIP?.let { cameraIP ->
                    Text(
                        text = "감지된 카메라 IP: $cameraIP",
                        style = MaterialTheme.typography.body2,
                        color = DarkTitleTextColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Text(
                    text = "카메라 Wi-Fi 핫스팟에 연결 후 카메라 검색을 시작할 수 있습니다.",
                    style = MaterialTheme.typography.body2,
                    color = DarkBodyTextColor
                )
            }
    }
}

@Preview(name = "AP Mode Description Card", showBackground = true)
@Composable
private fun ApModeDescriptionCardPreview() {
    CamConTheme {
        ApModeDescriptionCard()
    }
}

@Preview(name = "AP Mode Content", showBackground = true)
@Composable
private fun ApModeContentPreview() {
    CamConTheme {
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