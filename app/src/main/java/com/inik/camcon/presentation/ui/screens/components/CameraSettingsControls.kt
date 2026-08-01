package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.ExposureCompensation
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Micro
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.theme.TouchTarget

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
    // Dense 등급(DESIGN_SYSTEM_V2 §8.3) — 노출 계측 스트립이므로 base 12dp.
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.md),
    exposureCompensation: ExposureCompensation? = null,
    onExposureCompensationChange: ((String) -> Unit)? = null
) {
    val showEvSlot = capabilities?.canExposureCompensation == true &&
            exposureCompensation != null &&
            exposureCompensation.available.isNotEmpty() &&
            onExposureCompensationChange != null

    val hasAnySettings = capabilities?.let {
        it.availableIsoSettings.isNotEmpty() ||
        it.availableShutterSpeeds.isNotEmpty() ||
        it.availableApertures.isNotEmpty()
    } ?: false

    if (!hasAnySettings && !showEvSlot) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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

        // 노출 보정(EV) — capabilities.canExposureCompensation 일 때만 렌더링.
        // 현재값 표시는 raw 문자열을 그대로 사용 (예: "0", "+1/3", "-2/3").
        if (showEvSlot && exposureCompensation != null && onExposureCompensationChange != null) {
            SettingDropdown(
                label = "EV",
                currentValue = exposureCompensation.current,
                options = exposureCompensation.available,
                onValueChange = onExposureCompensationChange,
                isEnabled = isEnabled,
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
            style = Micro,
            color = if (isEnabled) TextSecondaryV2 else TextTertiary,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // 드롭다운 버튼 (HUD: 각진 4dp 세그먼트)
        Box {
            Surface(
                modifier = Modifier
                    // 탭 가능한 세그먼트이므로 클릭 영역 하한을 WCAG 2.2 안전선(44dp)으로 올린다.
                    .defaultMinSize(minHeight = TouchTarget.min)
                    .widthIn(min = 70.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(enabled = isEnabled && options.isNotEmpty()) {
                        expanded = true
                    }
                    .then(
                        if (isEnabled) Modifier.border(
                            width = if (isManuallySet) StrokeWidth.regular else StrokeWidth.thin,
                            color = if (isManuallySet) Accent.copy(alpha = 0.5f) else DividerLine,
                            shape = RoundedCornerShape(Radius.sm)
                        )
                        else Modifier.border(
                            StrokeWidth.thin,
                            TextTertiary.copy(alpha = 0.3f),
                            RoundedCornerShape(Radius.sm)
                        )
                    ),
                color = if (isEnabled) Surface2 else Surface2.copy(alpha = 0.5f),
                shape = RoundedCornerShape(Radius.sm)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = Spacing.sm)
                        .defaultMinSize(minHeight = TouchTarget.min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formatDisplayValue(currentValue),
                        style = MonoNumeric,
                        color = if (isEnabled) TextPrimaryV2 else TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        textAlign = TextAlign.Center
                    )
                    if (isEnabled && options.isNotEmpty()) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = TextSecondaryV2,
                            modifier = Modifier.size(IconSize.sm)
                        )
                    }
                }
            }

            // 드롭다운 메뉴
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Surface2)
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
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) Accent else TextPrimaryV2
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Accent,
                                            modifier = Modifier.size(IconSize.sm)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            },
                            modifier = Modifier.background(
                                if (isSelected) Accent.copy(alpha = 0.1f)
                                else Surface2
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
    CamConTheme() {
        Column(
            modifier = Modifier
                .background(Surface0)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 활성화된 상태
            CameraSettingsControls(
                // 실촬영 판독값 — 정수 스톱이 아닌 값이라 자릿수·문자열 폭 변동이 프리뷰에서 드러난다.
                currentSettings = CameraSettings(
                    iso = "640",
                    shutterSpeed = "1/160",
                    aperture = "f/3.5",
                    whiteBalance = "5600K",
                    focusMode = "AF-C",
                    exposureCompensation = "-1/3"
                ),
                capabilities = CameraCapabilities(
                    model = "NIKON Z 8",
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
                    availableIsoSettings = listOf("AUTO", "64", "100", "200", "400", "640", "800", "1600", "3200", "6400", "12800"),
                    availableShutterSpeeds = listOf("AUTO", "1/8000", "1/4000", "1/2000", "1/1000", "1/500", "1/250", "1/200", "1/160", "1/125", "1/60"),
                    availableApertures = listOf("AUTO", "f/1.8", "f/2.8", "f/3.5", "f/4", "f/5.6", "f/8", "f/11"),
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
