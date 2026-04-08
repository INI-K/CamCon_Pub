package com.inik.camcon.presentation.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inik.camcon.R
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Primary
import com.inik.camcon.presentation.theme.SurfaceElevated
import com.inik.camcon.presentation.theme.TextMuted
import com.inik.camcon.presentation.theme.TextPrimary
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
        containerColor = Background,
        contentColor = TextPrimary,
        bottomBar = {
            MainBottomNavigation(
                navController = navController,
                items = BottomNavDestinations.items
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AppNavigation(
                navController = navController,
                cameraViewModel = cameraViewModel,
                startDestination = AppDestination.CameraControl.route,
                onSettingsClick = onSettingsClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 메인 하단 네비게이션 - 전체 너비, 상단만 라운드
 */
@Composable
private fun MainBottomNavigation(
    navController: androidx.navigation.NavHostController,
    items: List<AppDestination>,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // 상단만 라운드된 배경
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { destination ->
                val isSelected =
                    currentDestination?.hierarchy?.any { it.route == destination.route } == true
                val label = getTextForDestination(destination)
                val icon = getIconForDestination(destination)

                NavItem(
                    icon = icon,
                    label = label,
                    isSelected = isSelected,
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
                    }
                )
            }
        }
    }
}

/**
 * 개별 네비게이션 아이템
 */
@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.95f,
        animationSpec = tween(150),
        label = "nav_item_scale"
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(
                color = if (isSelected) Primary.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Primary else TextMuted,
            modifier = Modifier.size(24.dp)
        )

        Text(
            text = label,
            color = if (isSelected) Primary else TextMuted,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
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

@Preview(showBackground = true, backgroundColor = 0xFF0A0F1C, widthDp = 400)
@Composable
private fun MainBottomNavigationPreview() {
    CamConTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Background),
            contentAlignment = Alignment.BottomCenter
        ) {
            val items = listOf(
                AppDestination.PhotoPreview,
                AppDestination.CameraControl,
                AppDestination.ServerPhotos
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, destination ->
                    val icon = when (destination) {
                        AppDestination.PhotoPreview -> Icons.Default.Photo
                        AppDestination.CameraControl -> Icons.Default.CameraAlt
                        AppDestination.ServerPhotos -> Icons.Default.CloudDownload
                        else -> Icons.Default.Photo
                    }
                    val label = when (destination) {
                        AppDestination.PhotoPreview -> "사진"
                        AppDestination.CameraControl -> "카메라"
                        AppDestination.ServerPhotos -> "다운로드"
                        else -> ""
                    }

                    Column(
                        modifier = Modifier
                            .background(
                                color = if (index == 1) Primary.copy(alpha = 0.15f) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (index == 1) Primary else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )

                        Text(
                            text = label,
                            color = if (index == 1) Primary else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (index == 1) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
