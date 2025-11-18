package com.inik.camcon.presentation.ui.screens.components

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import com.inik.camcon.data.datasource.local.ThemeMode

/**
 * Wi-Fi 상태 카드
 */
@Composable
fun WifiStatusCard(
    isWifiConnected: Boolean,
    isPtpipEnabled: Boolean,
    onEnablePtpip: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isWifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = if (isWifiConnected) Color.Green else Color.Red,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isWifiConnected) "Wi-Fi 연결됨" else "Wi-Fi 연결 안됨",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isPtpipEnabled) {
                            if (isWifiConnected) {
                                "PTPIP 기능 활성화됨 - 네트워크에서 카메라 검색"
                            } else {
                                "Wi-Fi 네트워크에 연결하세요"
                            }
                        } else {
                            "PTPIP 기능을 활성화하세요"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // 버튼 표시 우선순위: PTPIP 활성화 > Wi-Fi 설정
                if (!isPtpipEnabled) {
                    Button(onClick = onEnablePtpip) {
                        Text("활성화")
                    }
                } else if (!isWifiConnected) {
                    OutlinedButton(
                        onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                try {
                                    Intent(Settings.Panel.ACTION_WIFI)
                                } catch (e: Exception) {
                                    Intent(Settings.ACTION_WIFI_SETTINGS)
                                }
                            } else {
                                Intent(Settings.ACTION_WIFI_SETTINGS)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Wi-Fi 연결")
                    }
                }
            }
        }
    }
}

/**
 * Wi-Fi 기능 정보 카드
 */
@Composable
fun WifiCapabilitiesCard(
    wifiCapabilities: WifiCapabilities,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val wifiManager = remember {
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    val supported = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported
        } else {
            false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Wi-Fi 기능 정보",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 네트워크 정보
            if (wifiCapabilities.isConnected) {
                wifiCapabilities.networkName?.let { name ->
                    if (hasLocationPermission) {
                        InfoRow(label = "연결된 네트워크", value = name)
                    } else {
                        InfoRow(
                            label = "연결된 네트워크",
                            value = "권한 필요",
                            valueColor = Color.Red
                        )
                    }
                } ?: run {
                    if (hasLocationPermission) {
                        InfoRow(
                            label = "연결된 네트워크",
                            value = "이름 없음",
                            valueColor = Color.Gray
                        )
                    } else {
                        InfoRow(
                            label = "연결된 네트워크",
                            value = "권한 필요",
                            valueColor = Color.Red
                        )
                    }
                }

                wifiCapabilities.linkSpeed?.let { speed ->
                    InfoRow(label = "링크 속도", value = "${speed}Mbps")
                }
                wifiCapabilities.frequency?.let { freq ->
                    InfoRow(label = "주파수", value = "${freq}MHz")
                }
            }

            // STA 동시 연결 지원 여부 (핵심 정보)
            InfoRow(
                label = "STA 동시 연결 지원",
                value = if (supported) "✅ 지원됨" else "❌ 지원되지 않음",
                valueColor = if (supported) Color.Green else Color.Red
            )

            // 안드로이드 버전 정보
            InfoRow(
                label = "Android 버전",
                value = "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"
            )

            // 권한 관련 안내
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasLocationPermission) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "💡 Wi-Fi 네트워크 이름을 확인하려면 위치 권한이 필요합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("위치 권한 허용")
                }
            }

            if (!wifiCapabilities.isStaConcurrencySupported && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 STA 동시 연결 기능은 Android 10 (API 29) 이상에서 지원됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 연결 상태 카드
 */
@Composable
fun ConnectionStatusCard(
    connectionState: PtpipConnectionState,
    selectedCamera: PtpipCamera?,
    cameraInfo: PtpipCameraInfo?,
    onDisconnect: () -> Unit,
    onCapture: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = when (connectionState) {
                        PtpipConnectionState.CONNECTED -> Color.Green
                        PtpipConnectionState.CONNECTING -> Color(0xFFFF9800)
                        PtpipConnectionState.ERROR -> Color.Red
                        else -> Color.Gray
                    },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedCamera?.name ?: "카메라",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (connectionState) {
                            PtpipConnectionState.CONNECTED -> "연결됨 - ${selectedCamera?.ipAddress}"
                            PtpipConnectionState.CONNECTING -> "연결 중..."
                            PtpipConnectionState.ERROR -> "연결 오류"
                            else -> "연결 안됨"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            if (cameraInfo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${cameraInfo.manufacturer} ${cameraInfo.model}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (connectionState == PtpipConnectionState.CONNECTED) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCapture,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("촬영")
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("연결 해제")
                    }
                }
            }
        }
    }
}

/**
 * 카메라 연결 및 검색 UI (WiFi Specification 기반)
 */
@Composable
fun CameraConnectionContent(
    ptpipViewModel: PtpipViewModel,
    connectionState: PtpipConnectionState,
    discoveredCameras: List<PtpipCamera>,
    isDiscovering: Boolean,
    isConnecting: Boolean,
    selectedCamera: PtpipCamera?,
    cameraInfo: PtpipCameraInfo?,
    isPtpipEnabled: Boolean,
    isWifiConnected: Boolean,
    isApMode: Boolean = false,
    hasLocationPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    nearbyWifiSSIDs: List<String> = emptyList()
) {
    Column {
        // 연결 상태 카드
        if (connectionState != PtpipConnectionState.DISCONNECTED) {
            ConnectionStatusCard(
                connectionState = connectionState,
                selectedCamera = selectedCamera,
                cameraInfo = cameraInfo,
                onDisconnect = { ptpipViewModel.disconnect() },
                onCapture = { ptpipViewModel.capturePhoto() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 카메라 목록 섹션 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isDiscovering) {
                    "Wi-Fi 스캔 중..."
                } else if (nearbyWifiSSIDs.isEmpty()) {
                    "Wi-Fi 네트워크"
                } else {
                    "Wi-Fi 네트워크 (${nearbyWifiSSIDs.size}개)"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = {
                    Log.d(
                        "PtpipCommonComponents",
                        "버튼 클릭 - isApMode: $isApMode, hasLocationPermission: $hasLocationPermission"
                    )
                    if (hasLocationPermission) {
                        Log.d("PtpipCommonComponents", "권한 있음 - Wi-Fi 스캔 실행")
                        ptpipViewModel.scanNearbyWifiNetworks()
                    } else {
                        Log.d("PtpipCommonComponents", "권한 없음 - 권한 요청 콜백 호출")
                        onRequestPermission()
                    }
                },
                enabled = !isDiscovering && isPtpipEnabled
            ) {
                Text(
                    when {
                        isDiscovering -> "스캔 중..."
                        nearbyWifiSSIDs.isEmpty() -> "주변 Wi-Fi 스캔"
                        else -> "다시 스캔"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 카메라 목록 내용
        CameraListContent(
            discoveredCameras = discoveredCameras,
            isDiscovering = isDiscovering,
            isConnecting = isConnecting,
            selectedCamera = selectedCamera,
            isPtpipEnabled = isPtpipEnabled,
            isWifiConnected = isWifiConnected,
            ptpipViewModel = ptpipViewModel,
            isApMode = isApMode,
            nearbyWifiSSIDs = nearbyWifiSSIDs
        )
    }
}

/**
 * 카메라 목록 내용 (WiFi Specification 기반)
 */
@Composable
fun CameraListContent(
    discoveredCameras: List<PtpipCamera>,
    isDiscovering: Boolean,
    isConnecting: Boolean,
    selectedCamera: PtpipCamera?,
    isPtpipEnabled: Boolean,
    isWifiConnected: Boolean,
    ptpipViewModel: PtpipViewModel,
    isApMode: Boolean = false,
    nearbyWifiSSIDs: List<String> = emptyList()
) {
    when {
        !isPtpipEnabled -> {
            EmptyStateCard(
                icon = Icons.Filled.Settings,
                message = "PTPIP 기능을 먼저 활성화하세요."
            )
        }

        isDiscovering -> {
            LoadingStateCard(message = "주변 Wi-Fi 네트워크를 찾고 있습니다...")
        }

        nearbyWifiSSIDs.isEmpty() -> {
            EmptyStateCard(
                icon = Icons.Filled.WifiOff,
                message = "주변 Wi-Fi 스캔 버튼을 눌러 카메라 네트워크를 찾으세요."
            )
        }

        else -> {
            // WiFi 스캔 결과가 있으면 메시지만 표시 (실제 목록은 WifiScanResultsCard에서 표시)
            EmptyStateCard(
                icon = Icons.Filled.Wifi,
                message = "위의 Wi-Fi 목록에서 카메라 네트워크를 선택하고 연결하세요."
            )
        }
    }
}

/**
 * 카메라 아이템
 */
@Composable
fun CameraItem(
    camera: PtpipCamera,
    isSelected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    isApMode: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = if (camera.isOnline) Color.Green else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${camera.ipAddress}:${camera.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = if (camera.isOnline) "온라인" else "오프라인",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (camera.isOnline) Color.Green else Color.Red
                )
            }
            Button(
                onClick = onConnect,
                enabled = camera.isOnline && !isConnecting
            ) {
                if (isConnecting && isSelected) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        if (isApMode) "AP 연결" else "STA 연결"
                    )
                }
            }
        }
    }
}

/**
 * 정보 행 컴포넌트
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * 빈 상태 카드
 */
@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 로딩 상태 카드
 */
@Composable
private fun LoadingStateCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(message)
        }
    }
}

// 프리뷰들
@Preview(name = "Wi-Fi Status Card - Connected", showBackground = true)
@Composable
private fun WifiStatusCardConnectedPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        WifiStatusCard(
            isWifiConnected = true,
            isPtpipEnabled = true,
            onEnablePtpip = { }
        )
    }
}

@Preview(name = "Wi-Fi Status Card - Disconnected", showBackground = true)
@Composable
private fun WifiStatusCardDisconnectedPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        WifiStatusCard(
            isWifiConnected = false,
            isPtpipEnabled = false,
            onEnablePtpip = { }
        )
    }
}

@Preview(name = "Camera Item - Online", showBackground = true)
@Composable
private fun CameraItemOnlinePreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        CameraItem(
            camera = PtpipCamera(
                ipAddress = "192.168.1.100",
                port = 15740,
                name = "Canon EOS R5",
                isOnline = true
            ),
            isSelected = false,
            isConnecting = false,
            onConnect = { },
            isApMode = false
        )
    }
}

@Preview(name = "Camera Item - Selected", showBackground = true)
@Composable
private fun CameraItemSelectedPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        CameraItem(
            camera = PtpipCamera(
                ipAddress = "192.168.1.100",
                port = 15740,
                name = "Canon EOS R5",
                isOnline = true
            ),
            isSelected = true,
            isConnecting = false,
            onConnect = { },
            isApMode = false
        )
    }
}