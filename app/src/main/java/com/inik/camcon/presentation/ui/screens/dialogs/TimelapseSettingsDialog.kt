package com.inik.camcon.presentation.ui.screens.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme

/**
 * 타임랩스 촬영 설정을 위한 다이얼로그
 */
@Composable
fun TimelapseSettingsDialog(
    onConfirm: (interval: Int, shots: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var interval by remember { mutableStateOf("5") }
    var totalShots by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timelapse_settings)) },
        text = {
            Column {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text(stringResource(R.string.interval_seconds)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = totalShots,
                    onValueChange = { totalShots = it },
                    label = { Text(stringResource(R.string.total_shots)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        interval.toIntOrNull() ?: 5,
                        totalShots.toIntOrNull() ?: 100
                    )
                }
            ) {
                Text(stringResource(R.string.start_timelapse))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(name = "Timelapse Settings Dialog", showBackground = true)
@Composable
private fun TimelapseSettingsDialogPreview() {
    CamConTheme {
        TimelapseSettingsDialog(
            onConfirm = { _, _ -> },
            onDismiss = { }
        )
    }
}