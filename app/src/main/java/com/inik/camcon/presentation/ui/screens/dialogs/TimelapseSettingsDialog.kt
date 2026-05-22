package com.inik.camcon.presentation.ui.screens.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.domain.model.ThemeMode

/**
 * 타임랩스 촬영 설정을 위한 다이얼로그.
 *
 * @param initialInterval prefill용 초기 간격(초). 기본 5.
 * @param initialCount prefill용 초기 총 컷 수. 기본 100.
 * @param onConfirm (간격, 컷수) 콜백 — 호출측에서 저장과 시작 모두 수행한다.
 */
@Composable
fun TimelapseSettingsDialog(
    onConfirm: (interval: Int, shots: Int) -> Unit,
    onDismiss: () -> Unit,
    initialInterval: Int = 5,
    initialCount: Int = 100
) {
    var interval by remember { mutableStateOf(initialInterval.toString()) }
    var totalShots by remember { mutableStateOf(initialCount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timelapse_settings), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text(stringResource(R.string.timelapse_interval_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = totalShots,
                    onValueChange = { totalShots = it },
                    label = { Text(stringResource(R.string.timelapse_count_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        interval.toIntOrNull() ?: initialInterval,
                        totalShots.toIntOrNull() ?: initialCount
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
    CamConTheme() {
        TimelapseSettingsDialog(
            onConfirm = { _, _ -> },
            onDismiss = { }
        )
    }
}
