package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.inik.camcon.R
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.Body
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.HeadingS
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2

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

    SurfaceV2(
        tier = 2,
        border = true,
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.base)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WifiFind,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.md)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = if (isStaMode) {
                        stringResource(R.string.v3_ptpip_scan_nearby_wifi_label)
                    } else {
                        stringResource(R.string.v3_ptpip_scan_nearby_camera_wifi_label)
                    },
                    style = HeadingM,
                    color = TextPrimaryV2,
                    modifier = Modifier.weight(1f)
                )
                // 스캔 개수는 변동 수치 → 라벨과 분리해 탭형 모노로 자릿수를 고정한다.
                Text(
                    text = "${ssids.size}",
                    style = MonoNumeric,
                    color = TextSecondaryV2
                )
            }

            // 카메라 제조사 네트워크 섹션
            if (cameraSsids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(IconSize.sm)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.wifi_scan_camera_network, cameraSsids.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                cameraSsids.forEach { ssid ->
                    val detectedManufacturer = cameraManufacturers.find { manufacturer ->
                        ssid.contains(manufacturer, ignoreCase = true)
                    }

                    SurfaceV2(
                        tier = 3,
                        border = true,
                        shape = RoundedCornerShape(Radius.sm),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = ssid,
                                    style = HeadingS,
                                    color = TextPrimaryV2
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
                                            modifier = Modifier.size(IconSize.xs)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.xs))
                                        Text(
                                            text = stringResource(R.string.wifi_scan_saved),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            PrimaryButton(
                                text = stringResource(R.string.wifi_scan_connect),
                                onClick = { onConnectToWifi(ssid) }
                            )
                        }
                    }
                }
            }

            // 일반 Wi-Fi 네트워크
            if (otherSsids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(IconSize.sm)
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.wifi_scan_other_network, otherSsids.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))

                otherSsids.forEach { ssid ->
                    val isSaved = savedSsids.contains(ssid)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // 카메라 AP는 SemiBold, 일반 네트워크는 Regular — 같은 14sp에서 무게로 갈린다.
                            Text(
                                text = ssid,
                                style = Body,
                                color = TextSecondaryV2
                            )
                            if (isSaved) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.VpnKey,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(IconSize.xs)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = stringResource(R.string.wifi_scan_saved),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        SecondaryButton(
                            text = stringResource(R.string.wifi_scan_connect),
                            onClick = { onConnectToWifi(ssid) }
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "WifiScanResultsCard - AP Mode", showBackground = true)
@Composable
private fun WifiScanResultsCardApPreview() {
    CamConTheme() {
        Column(modifier = Modifier.padding(Spacing.base)) {
            // 실제 카메라 AP가 뿌리는 형식(모델 약칭 + hex 시리얼)으로 둔다.
            WifiScanResultsCard(
                ssids = listOf("NIKON_Z8-A47C1E", "CANON_EOSR5_3f9a21", "KT_GiGA_5G_A2F1", "iPhone_15Pro_hs"),
                onConnectToWifi = {},
                isStaMode = false,
                savedSsids = setOf("KT_GiGA_5G_A2F1")
            )
        }
    }
}

@Preview(name = "WifiScanResultsCard - STA Mode", showBackground = true)
@Composable
private fun WifiScanResultsCardStaPreview() {
    CamConTheme() {
        Column(modifier = Modifier.padding(Spacing.base)) {
            WifiScanResultsCard(
                ssids = listOf("DIRECT-a7-SONY_ILCE7M4", "iptime_5G_7c2b"),
                onConnectToWifi = {},
                isStaMode = true
            )
        }
    }
}
