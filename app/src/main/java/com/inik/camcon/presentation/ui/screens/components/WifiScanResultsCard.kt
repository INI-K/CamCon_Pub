package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.data.datasource.local.ThemeMode
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.CamConTheme

/**
 * 주변 Wi‑Fi 스캔 결과 카드 (AP/STA 모드 공용)
 *
 * @param ssids 검색된 SSID 목록
 * @param onConnectToWifi WiFi 연결 콜백 (비밀번호 입력 다이얼로그 표시)
 * @param isStaMode STA 모드 여부 (UI 텍스트 변경용)
 */
@Composable
fun WifiScanResultsCard(
    ssids: List<String>,
    onConnectToWifi: (String) -> Unit,
    isStaMode: Boolean = false,
    savedSsids: Set<String> = emptySet()
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
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = if (isStaMode) {
                    "🔎 주변 Wi‑Fi 네트워크 (${ssids.size}개)"
                } else {
                    "🔎 주변 카메라 Wi‑Fi (${ssids.size}개)"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isStaMode) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            if (cameraSsids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📷 카메라 네트워크 (${cameraSsids.size}개)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isStaMode) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isStaMode) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        }
                    )
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
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (isStaMode) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            val isSaved = savedSsids.contains(ssid)
                            val manufacturerLabel = buildString {
                                if (detectedManufacturer != null) append("📷 $detectedManufacturer 카메라")
                                if (isSaved) {
                                    if (isNotEmpty()) append(" · ")
                                    append("🔑 저장됨")
                                }
                            }
                            if (manufacturerLabel.isNotEmpty()) {
                                Text(
                                    text = manufacturerLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isStaMode) {
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    }
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
                                style = MaterialTheme.typography.bodySmall
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))

                otherSsids.forEach { ssid ->
                    val isSaved = savedSsids.contains(ssid)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ssid,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                            if (isSaved) {
                                Text(
                                    text = "🔑 저장됨",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                        }
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

@Preview(name = "WifiScanResultsCard - AP Mode", showBackground = true)
@Composable
private fun WifiScanResultsCardApPreview() {
    CamConTheme(themeMode = ThemeMode.DARK) {
        Column(modifier = Modifier.background(Background).padding(16.dp)) {
            WifiScanResultsCard(
                ssids = listOf("NIKON_Z6III_12345", "CANON_EOS_R5", "MyHomeWifi_5G", "iPhone"),
                onConnectToWifi = {},
                isStaMode = false
            )
        }
    }
}

@Preview(name = "WifiScanResultsCard - STA Mode", showBackground = true)
@Composable
private fun WifiScanResultsCardStaPreview() {
    CamConTheme(themeMode = ThemeMode.DARK) {
        Column(modifier = Modifier.background(Background).padding(16.dp)) {
            WifiScanResultsCard(
                ssids = listOf("CameraNetwork_SONY", "HomeRouter_2G"),
                onConnectToWifi = {},
                isStaMode = true
            )
        }
    }
}
