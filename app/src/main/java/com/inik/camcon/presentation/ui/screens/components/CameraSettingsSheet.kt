package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.presentation.theme.CamConTheme

/**
 * 카메라 설정 바텀시트 컴포넌트
 * ModalBottomSheet 내에서 호출되는 Stateless Composable
 */
@Composable
fun CameraSettingsSheet(
    settings: CameraSettings?,
    onSettingChange: (String, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.camera_settings),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Settings would be dynamically loaded based on camera capabilities
        // 향후 구현 예정: settings?.let { ... } 패턴
    }
}

@Preview
@Composable
private fun CameraSettingsSheetPreview() {
    CamConTheme {
        CameraSettingsSheet(
            settings = null,
            onSettingChange = { _, _ -> },
            onClose = {}
        )
    }
}
