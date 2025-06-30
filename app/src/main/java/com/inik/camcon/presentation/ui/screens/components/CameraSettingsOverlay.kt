package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.presentation.theme.CamConTheme

/**
 * 카메라 설정 정보를 오버레이로 표시하는 컴포넌트
 */
@Composable
fun CameraSettingsOverlay(
    settings: CameraSettings?,
    modifier: Modifier = Modifier
) {
    settings?.let { settings ->
        Row(
            modifier = modifier
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            CameraSettingChip("ISO ${settings.iso}")
            Spacer(modifier = Modifier.width(8.dp))
            CameraSettingChip(settings.shutterSpeed)
            Spacer(modifier = Modifier.width(8.dp))
            CameraSettingChip("f/${settings.aperture}")
        }
    }
}

/**
 * 개별 카메라 설정 칩
 */
@Composable
fun CameraSettingChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}

@Preview(name = "Camera Settings Overlay", showBackground = true)
@Composable
private fun CameraSettingsOverlayPreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.Black)
        ) {
            CameraSettingsOverlay(
                settings = CameraSettings(
                    iso = "1600",
                    shutterSpeed = "1/250",
                    aperture = "4.0",
                    whiteBalance = "Daylight",
                    focusMode = "AF-S",
                    exposureCompensation = "-0.7"
                )
            )
        }
    }
}