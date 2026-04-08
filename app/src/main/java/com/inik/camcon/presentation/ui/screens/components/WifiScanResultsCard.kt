package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.Border
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.SurfaceElevated

/**
 * 주변 Wi‑Fi 스캔 결과 카드 (AP/STA 모드 공용)
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
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Border,
                shape = MaterialTheme.shapes.medium
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WifiFind,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isStaMode) {
                        stringResource(R.string.wifi_scan_nearby_wifi, ssids.size)
                    } else {
                        stringResource(R.string.wifi_scan_nearby_camera_wifi, ssids.size)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 카메라 제조사 네트워크 섹션
            if (cameraSsids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.wifi_scan_camera_network, cameraSsids.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                cameraSsids.forEach { ssid ->
                    val detectedManufacturer = cameraManufacturers.find { manufacturer ->
                        ssid.contains(manufacturer, ignoreCase = true)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.small
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
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
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                val isSaved = savedSsids.contains(ssid)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (detectedManufacturer != null) {
                                        Text(
                                            text = detectedManufacturer,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        )
                                    }
                                    if (isSaved) {
                                        if (detectedManufacturer != null) {
                                            Text(
                                                text = " · ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.VpnKey,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = stringResource(R.string.wifi_scan_saved),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onConnectToWifi(ssid) },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    stringResource(R.string.wifi_scan_connect),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // 일반 Wi-Fi 네트워크
            if (otherSsids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.wifi_scan_other_network, otherSsids.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            if (isSaved) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.VpnKey,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = stringResource(R.string.wifi_scan_saved),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { onConnectToWifi(ssid) }) {
                            Text(
                                stringResource(R.string.wifi_scan_connect),
                                style = MaterialTheme.typography.labelMedium
                            )
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
        Column(modifier = Modifier.padding(16.dp)) {
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
        Column(modifier = Modifier.padding(16.dp)) {
            WifiScanResultsCard(
                ssids = listOf("CameraNetwork_SONY", "HomeRouter_2G"),
                onConnectToWifi = {},
                isStaMode = true
            )
        }
    }
}
