package com.inik.camcon.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.inik.camcon.R

/**
 * 메인 하단/사이드 내비게이션 항목.
 *
 * MainActivity 의 하단 NavigationBar / 측면 NavigationRail 양쪽에서 동일하게 사용된다.
 * 항목을 추가/제거할 때는 MainScreen.items 목록도 함께 갱신할 것.
 */
sealed class BottomNavItem(val route: String, val titleRes: Int, val icon: ImageVector) {
    object PhotoPreview :
        BottomNavItem("photo_preview", R.string.photo_preview, Icons.Default.Photo)

    object CameraControl :
        BottomNavItem("camera_control", R.string.camera_control, Icons.Default.CameraAlt)

    object ServerPhotos :
        BottomNavItem("server_photos", R.string.server_photos, Icons.Default.CloudDownload)

    object Settings :
        BottomNavItem("settings", R.string.settings, Icons.Default.Settings)
}
