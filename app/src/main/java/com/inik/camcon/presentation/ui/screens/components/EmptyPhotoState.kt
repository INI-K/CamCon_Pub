package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.components.v2.EmptyState

/**
 * 사진이 없을 때 표시되는 빈 상태 — V2 EmptyState 위임 (Technical HUD 토큰).
 */
@Composable
fun EmptyPhotoState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            icon = Icons.Outlined.PhotoLibrary,
            title = stringResource(R.string.no_photos),
            description = stringResource(R.string.connect_camera_and_capture)
        )
    }
}

@Preview(name = "Empty Photo State", showBackground = true, backgroundColor = 0xFF0A0B0D)
@Composable
private fun EmptyPhotoStatePreview() {
    CamConTheme {
        EmptyPhotoState()
    }
}
