package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.inik.camcon.data.datasource.local.ThemeMode
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.Border
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Primary
import com.inik.camcon.presentation.theme.Surface
import com.inik.camcon.presentation.theme.SurfaceElevated
import com.inik.camcon.presentation.theme.TextMuted
import com.inik.camcon.presentation.theme.TextPrimary
import com.inik.camcon.presentation.theme.TextSecondary

/**
 * ISO/셔터스피드/조리개 조절 컨트롤
 */
@Composable
fun CameraSettingsControls(
    currentSettings: CameraSettings?,
    capabilities: CameraCapabilities?,
    onSettingChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp)
) {
    val hasAnySettings = capabilities?.let {
        it.availableIsoSettings.isNotEmpty() ||
        it.availableShutterSpeeds.isNotEmpty() ||
        it.availableApertures.isNotEmpty()
    } ?: false

    if (!hasAnySettings) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ISO
        if (capabilities?.availableIsoSettings?.isNotEmpty() == true) {
            SettingDropdown(
                label = "ISO",
                currentValue = currentSettings?.iso ?: "AUTO",
                options = capabilities.availableIsoSettings,
                onValueChange = { onSettingChange("iso", it) },
                isEnabled = isEnabled && capabilities.supportsConfigChange,
                modifier = Modifier.weight(1f)
            )
        }

        // 셔터스피드
        if (capabilities?.availableShutterSpeeds?.isNotEmpty() == true) {
            SettingDropdown(
                label = "SS",
                currentValue = currentSettings?.shutterSpeed ?: "AUTO",
                options = capabilities.availableShutterSpeeds,
                onValueChange = { onSettingChange("shutterspeed", it) },
                isEnabled = isEnabled && capabilities.supportsConfigChange,
                modifier = Modifier.weight(1f)
            )
        }

        // 조리개
        if (capabilities?.availableApertures?.isNotEmpty() == true) {
            SettingDropdown(
                label = "F",
                currentValue = currentSettings?.aperture ?: "AUTO",
                options = capabilities.availableApertures,
                onValueChange = { onSettingChange("aperture", it) },
                isEnabled = isEnabled && capabilities.supportsConfigChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingDropdown(
    label: String,
    currentValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isManuallySet = currentValue.isNotEmpty() && !currentValue.equals("auto", ignoreCase = true)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 레이블
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (isEnabled) TextSecondary else TextMuted,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // 드롭다운 버튼
        Box {
            Surface(
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(min = 70.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .clickable(enabled = isEnabled && options.isNotEmpty()) {
                        expanded = true
                    }
                    .then(
                        if (isEnabled) Modifier.border(
                            width = if (isManuallySet) 1.5.dp else 1.dp,
                            color = if (isManuallySet) Primary.copy(alpha = 0.5f) else Border,
                            shape = RoundedCornerShape(50.dp)
                        )
                        else Modifier.border(
                            1.dp,
                            TextMuted.copy(alpha = 0.3f),
                            RoundedCornerShape(50.dp)
                        )
                    ),
                color = if (isEnabled) SurfaceElevated else SurfaceElevated.copy(alpha = 0.5f),
                shape = RoundedCornerShape(50.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .height(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formatDisplayValue(currentValue),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isEnabled) TextPrimary else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        textAlign = TextAlign.Center
                    )
                    if (isEnabled && options.isNotEmpty()) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 드롭다운 메뉴
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(SurfaceElevated)
                    .height(250.dp),
                properties = PopupProperties(focusable = true)
            ) {
                LazyColumn {
                    items(
                        items = options,
                        key = { option -> option }
                    ) { option ->
                        val isSelected = option == currentValue
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatDisplayValue(option),
                                        fontSize = 13.sp,
                                        color = if (isSelected) Primary else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            },
                            modifier = Modifier.background(
                                if (isSelected) Primary.copy(alpha = 0.1f)
                                else SurfaceElevated
                            )
                        )
                    }
                }
            }
        }
    }
}

// 표시값 포맷팅
private fun formatDisplayValue(value: String): String {
    return when {
        value.isEmpty() -> "AUTO"
        value.equals("auto", ignoreCase = true) -> "AUTO"
        else -> value
    }
}

@Preview(name = "Camera Settings Controls", showBackground = true)
@Composable
private fun CameraSettingsControlsPreview() {
    CamConTheme(themeMode = ThemeMode.DARK) {
        Column(
            modifier = Modifier
                .background(Background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 활성화된 상태
            CameraSettingsControls(
                currentSettings = CameraSettings(
                    iso = "400",
                    shutterSpeed = "1/125",
                    aperture = "f/2.8",
                    whiteBalance = "Auto",
                    focusMode = "Auto",
                    exposureCompensation = "0"
                ),
                capabilities = CameraCapabilities(
                    model = "Canon EOS R5",
                    canCapturePhoto = true,
                    canCaptureVideo = true,
                    canLiveView = true,
                    canTriggerCapture = true,
                    supportsAutofocus = true,
                    supportsManualFocus = true,
                    supportsFocusPoint = true,
                    supportsBurstMode = true,
                    supportsTimelapse = true,
                    supportsBracketing = true,
                    supportsBulbMode = true,
                    canDownloadFiles = true,
                    canDeleteFiles = true,
                    canPreviewFiles = true,
                    availableIsoSettings = listOf("AUTO", "100", "200", "400", "800", "1600", "3200"),
                    availableShutterSpeeds = listOf("AUTO", "1/4000", "1/2000", "1/1000", "1/500", "1/250", "1/125"),
                    availableApertures = listOf("AUTO", "f/1.4", "f/2", "f/2.8", "f/4", "f/5.6", "f/8"),
                    availableWhiteBalanceSettings = emptyList(),
                    supportsRemoteControl = true,
                    supportsConfigChange = true,
                    batteryLevel = 85
                ),
                onSettingChange = { _, _ -> },
                isEnabled = true
            )

            // 비활성화된 상태
            CameraSettingsControls(
                currentSettings = null,
                capabilities = CameraCapabilities(
                    model = "Canon EOS R5",
                    canCapturePhoto = true,
                    canCaptureVideo = false,
                    canLiveView = false,
                    canTriggerCapture = true,
                    supportsAutofocus = true,
                    supportsManualFocus = false,
                    supportsFocusPoint = false,
                    supportsBurstMode = false,
                    supportsTimelapse = false,
                    supportsBracketing = false,
                    supportsBulbMode = false,
                    canDownloadFiles = true,
                    canDeleteFiles = false,
                    canPreviewFiles = false,
                    availableIsoSettings = listOf("AUTO", "100", "200"),
                    availableShutterSpeeds = listOf("AUTO", "1/125"),
                    availableApertures = emptyList(),
                    availableWhiteBalanceSettings = emptyList(),
                    supportsRemoteControl = true,
                    supportsConfigChange = false,
                    batteryLevel = 60
                ),
                onSettingChange = { _, _ -> },
                isEnabled = false
            )
        }
    }
}
