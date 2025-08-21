package com.inik.camcon.presentation.ui.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.data.datasource.local.ThemeMode

/**
 * 카메라 연결 문제 해결 도움말 다이얼로그
 */
@Composable
fun CameraConnectionHelpDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.camera_connection_help_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    context.getString(R.string.camera_connection_help_message),
                    style = MaterialTheme.typography.body1
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF2A2A2A)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HelpRow("1. ", context.getString(R.string.check_camera_pc_mode))
                        HelpRow("2. ", context.getString(R.string.check_usb_cable))
                        HelpRow("3. ", context.getString(R.string.check_camera_power))
                        HelpRow(
                            "4. ",
                            context.getString(R.string.check_other_apps_not_using_camera)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    context.getString(R.string.camera_specific_settings),
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Bold
                )

                CameraBrandInstructions()
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(context.getString(R.string.retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.close))
            }
        }
    )
}

@Composable
private fun HelpRow(
    number: String,
    instruction: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(number, fontWeight = FontWeight.Bold)
        Text(instruction)
    }
}

@Composable
private fun CameraBrandInstructions() {
    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            context.getString(R.string.canon_camera_settings),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        Text(
            context.getString(R.string.nikon_camera_settings),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        Text(
            context.getString(R.string.sony_camera_settings),
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Preview(name = "Camera Connection Help Dialog", showBackground = true)
@Composable
private fun CameraConnectionHelpDialogPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        CameraConnectionHelpDialog(
            onDismiss = { },
            onRetry = { }
        )
    }
}