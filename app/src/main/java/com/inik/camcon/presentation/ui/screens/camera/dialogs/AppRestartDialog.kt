package com.inik.camcon.presentation.ui.screens.camera.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inik.camcon.presentation.ui.components.v2.AppDialog

@Composable
fun AppRestartDialog(
    isVisible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isVisible) return

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Restart Required",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = "App configuration has changed. Restart the app to apply changes?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text("Restart")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Later")
            }
        },
        modifier = modifier,
    )
}
