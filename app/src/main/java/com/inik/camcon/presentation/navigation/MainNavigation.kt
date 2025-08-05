package com.inik.camcon.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.CameraViewModel

/**
 * 메인 화면의 전체 네비게이션 구조
 * 상단 바와 하단 네비게이션을 포함한 메인 스크린
 */
@Composable
fun MainNavigation(
    cameraViewModel: CameraViewModel,
    onSettingsClick: () -> Unit,
    onPtpipConnectionClick: () -> Unit
) {
    val navController = rememberNavController()

    Scaffold(
        topBar = {
            MainTopAppBar(
                onSettingsClick = onSettingsClick,
                onPtpipConnectionClick = onPtpipConnectionClick
            )
        },
        bottomBar = {
            MainBottomNavigation(
                navController = navController,
                items = BottomNavDestinations.items
            )
        }
    ) { innerPadding ->
        AppNavigation(
            navController = navController,
            cameraViewModel = cameraViewModel,
            startDestination = AppDestination.CameraControl.route,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/**
 * 메인 상단 앱바
 */
@Composable
private fun MainTopAppBar(
    onSettingsClick: () -> Unit,
    onPtpipConnectionClick: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            IconButton(onClick = onPtpipConnectionClick) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "PTPIP 연결"
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            }
        },
        backgroundColor = MaterialTheme.colors.primary,
        contentColor = MaterialTheme.colors.onPrimary
    )
}

/**
 * 메인 하단 네비게이션
 */
@Composable
private fun MainBottomNavigation(
    navController: androidx.navigation.NavHostController,
    items: List<AppDestination>
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    BottomNavigation(
        backgroundColor = MaterialTheme.colors.surface,
        contentColor = MaterialTheme.colors.onSurface
    ) {
        items.forEach { destination ->
            val isSelected =
                currentDestination?.hierarchy?.any { it.route == destination.route } == true

            BottomNavigationItem(
                icon = {
                    Icon(
                        imageVector = getIconForDestination(destination),
                        contentDescription = getContentDescriptionForDestination(destination)
                    )
                },
                label = { Text(getTextForDestination(destination)) },
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                selectedContentColor = MaterialTheme.colors.primary,
                unselectedContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 목적지에 따른 아이콘 반환
 */
private fun getIconForDestination(destination: AppDestination): ImageVector {
    return when (destination) {
        AppDestination.PhotoPreview -> Icons.Default.Photo
        AppDestination.CameraControl -> Icons.Default.CameraAlt
        AppDestination.ServerPhotos -> Icons.Default.CloudDownload
        else -> Icons.Default.Photo
    }
}

/**
 * 목적지에 따른 텍스트 반환
 */
@Composable
private fun getTextForDestination(destination: AppDestination): String {
    return when (destination) {
        AppDestination.PhotoPreview -> stringResource(R.string.photo_preview)
        AppDestination.CameraControl -> stringResource(R.string.camera_control)
        AppDestination.ServerPhotos -> stringResource(R.string.server_photos)
        else -> ""
    }
}

/**
 * 목적지에 따른 콘텐츠 설명 반환
 */
@Composable
private fun getContentDescriptionForDestination(destination: AppDestination): String {
    return when (destination) {
        AppDestination.PhotoPreview -> stringResource(R.string.photo_preview)
        AppDestination.CameraControl -> stringResource(R.string.camera_control)
        AppDestination.ServerPhotos -> stringResource(R.string.server_photos)
        else -> ""
    }
}

/**
 * 메인 네비게이션 프리뷰
 */
// @Preview(showBackground = true)
// @Composable
// fun MainNavigationPreview() {
//     CamConTheme {
//         MainNavigation(
//             cameraViewModel = // CameraViewModel 인스턴스가 필요하므로 프리뷰에서 제외
//             onSettingsClick = { },
//             onPtpipConnectionClick = { }
//         )
//     }
// }

/**
 * 메인 상단 앱바 프리뷰
 */
@Preview(showBackground = true)
@Composable
fun MainTopAppBarPreview() {
    CamConTheme {
        MainTopAppBar(
            onSettingsClick = { },
            onPtpipConnectionClick = { }
        )
    }
}