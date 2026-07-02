package com.inik.camcon.presentation.ui.screens.camera.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.inik.camcon.R
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton

@Composable
fun CameraRestartDialog(
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.diag_close),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(stringResource(R.string.diag_restart_needed))
        },
        text = {
            Text(
                stringResource(R.string.diag_restart_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.diag_restart_confirm),
                onClick = onRestart
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.diag_restart_cancel))
            }
        },
        modifier = modifier
    )
}
