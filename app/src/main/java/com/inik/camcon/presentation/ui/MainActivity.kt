package com.inik.camcon.presentation.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inik.camcon.R
import com.inik.camcon.data.service.BackgroundSyncService
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.onboarding.OnboardingScreen
import com.inik.camcon.presentation.navigation.BottomNavItem
import com.inik.camcon.presentation.theme.LocalWindowSizeClass
import com.inik.camcon.presentation.theme.isMediumOrWider
import com.inik.camcon.presentation.ui.screens.CameraControlScreen
import com.inik.camcon.presentation.ui.screens.MyPhotosScreen
import com.inik.camcon.presentation.ui.screens.PhotoPreviewScreen
import com.inik.camcon.presentation.ui.screens.components.PtpTimeoutDialog
import com.inik.camcon.presentation.ui.screens.components.UsbInitializationOverlay
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import com.inik.camcon.presentation.viewmodel.MainActivityViewModel
import com.inik.camcon.utils.LogcatManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.inik.camcon.di.IoDispatcher

/** Material 3 Emphasized Decelerate 이징 — 진입 모션(요소가 화면으로 들어올 때) 표준. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/**
 * 바텀 내비 아이콘 — 활성 시 아이콘 위에 2dp 앰버 인디케이터를 얹는다.
 * 비활성 시에도 동일 높이의 투명 스페이서를 두어 아이콘 정렬을 고정한다.
 */
@Composable
private fun NavBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(IconSize.lg)
                .height(2.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(if (selected) Accent else Color.Transparent)
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
fun CameraConnectionOptimizationDialog(
    onDismissRequest: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismissRequest,
        icon = { androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = null) },
        title = {
            androidx.compose.material3.Text(
                stringResource(R.string.camera_connection_optimization_title),
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.camera_connection_optimization_message),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
                androidx.compose.material3.Text(
                    text = stringResource(R.string.camera_connection_optimization_details),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.camera_connection_optimization_confirm),
                onClick = onGoToSettings
            )
        },
        dismissButton = {
            SecondaryButton(
                text = stringResource(R.string.camera_connection_optimization_dismiss),
                onClick = onDismissRequest
            )
        }
    )
}

@Composable
fun MainScreen(
    onSettingsClick: () -> Unit,
    globalManager: CameraConnectionGlobalManager,
    cameraViewModel: CameraViewModel = hiltViewModel(),
    navigateToCameraControl: Boolean = false
) {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.PhotoPreview,
        BottomNavItem.CameraControl,
        BottomNavItem.ServerPhotos,
        BottomNavItem.Settings
    )

    // 전체화면 상태 관리
    var isFullscreen by remember { mutableStateOf(false) }

    // PTPIP 경고 다이얼로그 상태
    // H3 — 다이얼로그 차단 제거 (1회 Toast로 대체).
    var showPtpipWarning by remember { mutableStateOf(false) }

    // 전역 연결 상태 모니터링
    val globalConnectionState by globalManager.globalConnectionState.collectAsStateWithLifecycle()
    val activeConnectionType by globalManager.activeConnectionType.collectAsStateWithLifecycle()
    val connectionStatusMessage by globalManager.connectionStatusMessage.collectAsStateWithLifecycle()

    // CameraViewModel의 USB 초기화 상태 모니터링
    val cameraUiState by cameraViewModel.uiState.collectAsStateWithLifecycle()

    // LocalContext를 @Composable 내에서 미리 가져오기
    val context = LocalContext.current

    // 전역 상태 변화 시 로그 출력
    LaunchedEffect(globalConnectionState) {
        LogcatManager.d("MainScreen", "전역 연결 상태 변화: $connectionStatusMessage")
    }

    // navigateToCameraControl 플래그가 true이면 카메라 컨트롤 탭으로 자동 이동
    LaunchedEffect(navigateToCameraControl) {
        if (navigateToCameraControl) {
            LogcatManager.d("MainScreen", "카메라 컨트롤 탭으로 자동 이동")
            navController.navigate(BottomNavItem.CameraControl.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // 테마 모드 상태
    val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
    val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()

    // 시스템 바 인셋 계산 - 기종별 대응
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    // WindowSizeClass: Compact → BottomNavigation, Medium/Expanded → NavigationRail
    val windowSizeClass = LocalWindowSizeClass.current
    val useNavigationRail = windowSizeClass.isMediumOrWider

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {

        // --- PTP 타임아웃 다이얼로그 모니터링 및 표시 ---
        var showRestartDialog by remember { mutableStateOf(false) }

        if (cameraUiState.isPtpTimeout == true && !showRestartDialog) {
            PtpTimeoutDialog(
                onDismissRequest = { cameraViewModel.clearPtpTimeout() },
                onRestartRequest = {
                    showRestartDialog = true
                }
            )
        }

        // 앱 재시작 다이얼로그 표시
        if (showRestartDialog) {
            var isRestarting by remember { mutableStateOf(false) }

            AppDialog(
                onDismissRequest = {
                    if (!isRestarting) {
                        showRestartDialog = false
                    }
                },
                icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(stringResource(R.string.dialog_restart_required_title), style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (isRestarting) {
                            Text(stringResource(R.string.dialog_restart_preparing_title))
                            Text(
                                stringResource(R.string.dialog_restart_preparing_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(stringResource(R.string.dialog_restart_body))
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Text(
                                stringResource(R.string.dialog_restart_options),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!isRestarting) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SecondaryButton(
                                text = stringResource(R.string.dialog_later),
                                onClick = { showRestartDialog = false }
                            )
                            PrimaryButton(
                                text = stringResource(R.string.dialog_restart_now),
                                onClick = {
                                    isRestarting = true
                                    cameraViewModel.clearPtpTimeout()
                                    val activity = context as? ComponentActivity
                                    activity?.let { act ->
                                        MainActivity.restartAppAfterCameraCleanup(act)
                                    }
                                }
                            )
                            SecondaryButton(
                                text = stringResource(R.string.dialog_restart_quit),
                                onClick = {
                                    isRestarting = true
                                    cameraViewModel.clearPtpTimeout()
                                    val activity = context as? ComponentActivity
                                    activity?.let { act ->
                                        MainActivity.systemRestartApp(act)
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }

        // USB 분리 다이얼로그 표시
        if (cameraUiState.isUsbDisconnected == true) {
            AppDialog(
                onDismissRequest = { cameraViewModel.clearUsbDisconnection() },
                icon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        stringResource(R.string.dialog_usb_disconnected_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            stringResource(R.string.dialog_usb_disconnected_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.dialog_usb_disconnected_hints),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    PrimaryButton(
                        text = stringResource(R.string.dialog_confirm),
                        onClick = { cameraViewModel.clearUsbDisconnection() }
                    )
                }
            )
        }

        // 카메라 상태 점검 다이얼로그 표시 (초기화가 완료된 후에만 표시)
        if (cameraUiState.showCameraStatusCheckDialog == true &&
            !cameraUiState.isUsbInitializing &&
            !cameraUiState.isCameraInitializing
        ) {
            AppDialog(
                onDismissRequest = { cameraViewModel.dismissCameraStatusCheckDialog() },
                icon = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        stringResource(R.string.dialog_camera_check_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            stringResource(R.string.dialog_camera_check_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.dialog_camera_check_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.dialog_camera_check_hints),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    PrimaryButton(
                        text = stringResource(R.string.dialog_confirm),
                        onClick = { cameraViewModel.dismissCameraStatusCheckDialog() }
                    )
                },
                dismissButton = {
                    SecondaryButton(
                        text = stringResource(R.string.dialog_reconnect),
                        onClick = {
                            cameraViewModel.dismissCameraStatusCheckDialog()
                            cameraViewModel.refreshUsbDevices()
                        }
                    )
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false // 사용자가 명시적으로 확인하도록
                )
            )
        }

        // 탭 클릭 핸들러 — NavigationBar / NavigationRail에서 공유
        val onTabClick: (BottomNavItem) -> Unit = { screen ->
            if (screen.route == "settings") {
                onSettingsClick()
            } else {
                navController.navigate(screen.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                // Compact 너비 + 전체화면 아님 → 기존 NavigationBar
                if (!isFullscreen && !useNavigationRail) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    // CINE 정합: Surface0 컨테이너 + 상단 헤어라인, 활성 탭 상단 2dp 앰버 인디케이터.
                    HorizontalDivider(color = DividerLine, thickness = StrokeWidth.hairline)
                    NavigationBar(
                        containerColor = Surface0,
                        contentColor = TextTertiary,
                        tonalElevation = 0.dp,
                    ) {
                        items.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItem(
                                icon = {
                                    NavBarIcon(
                                        icon = screen.icon,
                                        contentDescription = stringResource(screen.titleRes),
                                        selected = selected
                                    )
                                },
                                label = {
                                    Text(stringResource(screen.titleRes))
                                },
                                selected = selected,
                                onClick = { onTabClick(screen) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent,
                                    selectedTextColor = Accent,
                                    unselectedIconColor = TextTertiary,
                                    unselectedTextColor = TextTertiary,
                                    // 상단 앰버 인디케이터로 활성 표현 — 기본 pill 인디케이터는 제거.
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val navHostContent: @Composable () -> Unit = {
                NavHost(
                    navController,
                    startDestination = BottomNavItem.CameraControl.route,
                    modifier = Modifier.fillMaxSize(),
                    // M3 fadeThrough 탭 전환 — 진입 fadeIn 210ms(EmphasizedDecelerate),
                    // 이탈 fadeOut 90ms. 겹침을 최소화한 페이드 스루.
                    enterTransition = {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = 210,
                                easing = EmphasizedDecelerate
                            )
                        )
                    },
                    exitTransition = { fadeOut(animationSpec = tween(90)) },
                    popEnterTransition = {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = 210,
                                easing = EmphasizedDecelerate
                            )
                        )
                    },
                    popExitTransition = { fadeOut(animationSpec = tween(90)) }
                ) {
                    composable(BottomNavItem.PhotoPreview.route) { PhotoPreviewScreen() }
                    composable(BottomNavItem.CameraControl.route) {
                        // AP 모드와 USB 모드 모두 동일한 CameraControlScreen 사용
                        CameraControlScreen(
                            viewModel = cameraViewModel, // 전역 ViewModel 전달
                            onFullscreenChange = { isFullscreen = it },
                            onGalleryClick = {
                                navController.navigate(BottomNavItem.ServerPhotos.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable(BottomNavItem.ServerPhotos.route) { MyPhotosScreen() }
                    // 설정은 별도 액티비티로 처리하므로 여기서 제외
                }
            }

            // 전체화면에서도 인셋을 상태가 아닌 실제 시스템바 기준으로 소비한다:
            // 단독 창에선 hide() 성공 → 인셋 0 = 기존 몰입 UX 그대로, 멀티윈도우처럼
            // 시스템이 hide를 무시해 바가 남으면 비영 인셋이 적용돼 상하 겹침을 막는다.
            val containerModifier = Modifier
                .fillMaxSize()
                .padding(
                    if (isFullscreen) WindowInsets.systemBars.asPaddingValues() else innerPadding
                )

            if (useNavigationRail && !isFullscreen) {
                // Medium / Expanded: NavigationRail 좌측 + content 우측
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                Row(modifier = containerModifier) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        items.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationRailItem(
                                icon = {
                                    Icon(
                                        screen.icon,
                                        contentDescription = stringResource(screen.titleRes)
                                    )
                                },
                                label = {
                                    Text(stringResource(screen.titleRes))
                                },
                                selected = selected,
                                onClick = { onTabClick(screen) },
                                alwaysShowLabel = true,
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        navHostContent()
                    }
                }
            } else {
                // Compact 또는 전체화면: 기존과 동일한 단일 NavHost
                Box(modifier = containerModifier) {
                    navHostContent()
                }
            }
        }

        // PTPIP 경고 다이얼로그
        if (showPtpipWarning) {
            AppDialog(
                onDismissRequest = { showPtpipWarning = false },
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = {
                    Text(
                        stringResource(R.string.dialog_wifi_active_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            stringResource(R.string.dialog_wifi_active_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            stringResource(R.string.dialog_wifi_active_steps_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.dialog_wifi_active_steps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            stringResource(R.string.dialog_wifi_active_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    PrimaryButton(
                        text = stringResource(R.string.dialog_understood),
                        onClick = { showPtpipWarning = false }
                    )
                }
            )
        }

        // Wi-Fi 연결 끊김 알림 다이얼로그
        var showWifiDisconnectedDialog by remember { mutableStateOf(false) }
        val ptpipConnectionState = globalConnectionState.ptpipConnectionState
        val wasConnected = remember { mutableStateOf(false) }

        LaunchedEffect(ptpipConnectionState) {
            when (ptpipConnectionState) {
                PtpipConnectionState.CONNECTED -> {
                    wasConnected.value = true
                }

                PtpipConnectionState.DISCONNECTED -> {
                    // 이전에 연결되어 있었다면 끊김 알림 표시
                    if (wasConnected.value && (activeConnectionType == CameraConnectionType.AP_MODE || activeConnectionType == CameraConnectionType.STA_MODE)) {
                        showWifiDisconnectedDialog = true
                        wasConnected.value = false
                    }
                }

                PtpipConnectionState.ERROR -> {
                    if (wasConnected.value) {
                        showWifiDisconnectedDialog = true
                        wasConnected.value = false
                    }
                }

                else -> { /* 다른 상태는 무시 */
                }
            }
        }

        if (showWifiDisconnectedDialog) {
            AppDialog(
                onDismissRequest = { showWifiDisconnectedDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        stringResource(R.string.dialog_wifi_disconnected_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        stringResource(R.string.dialog_wifi_disconnected_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    PrimaryButton(
                        text = stringResource(R.string.dialog_confirm),
                        onClick = { showWifiDisconnectedDialog = false }
                    )
                }
            )
        }

        // USB 연결 및 초기화 상태에 따른 UI 블로킹 오버레이
        val shouldShowOverlay =
            globalConnectionState.ptpipConnectionState == PtpipConnectionState.CONNECTING ||
                    connectionStatusMessage.contains("초기화 중") ||
                    cameraUiState.isUsbInitializing ||
                    cameraUiState.isCameraInitializing

        if (shouldShowOverlay) {
            val overlayMessage = when {
                cameraUiState.isCameraInitializing -> "카메라 이벤트 초기화 중..."
                cameraUiState.isUsbInitializing -> cameraUiState.usbInitializationMessage
                    ?: "USB 카메라 초기화 중..."

                else -> connectionStatusMessage
            }

            LogcatManager.d("MainActivity", " UI 블로킹 오버레이 표시: $overlayMessage")
            LogcatManager.d(
                "MainActivity", "블로킹 조건 - PTP연결:${globalConnectionState.ptpipConnectionState}, " +
                        "메시지초기화:${connectionStatusMessage.contains("초기화 중")}, " +
                        "USB초기화:${cameraUiState.isUsbInitializing}, " +
                        "카메라초기화:${cameraUiState.isCameraInitializing}"
            )

            UsbInitializationOverlay(message = overlayMessage)
        } else {
            // 오버레이가 사라질 때도 로그 출력
            LaunchedEffect(Unit) {
                LogcatManager.d("MainActivity", " UI 블로킹 오버레이 해제됨")
                LogcatManager.d(
                    "MainActivity",
                    "해제 조건 - PTP연결:${globalConnectionState.ptpipConnectionState}, " +
                            "메시지초기화:${connectionStatusMessage.contains("초기화 중")}, " +
                            "USB초기화:${cameraUiState.isUsbInitializing}, " +
                            "카메라초기화:${cameraUiState.isCameraInitializing}"
                )
            }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var batteryDialogShown = false

    private val viewModel: MainActivityViewModel by viewModels()

    @Inject
    lateinit var globalManager: CameraConnectionGlobalManager

    @Inject
    lateinit var getSubscriptionUseCase: GetSubscriptionUseCase

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    // 권한 요청 런처
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            LogcatManager.d("MainActivity", "모든 저장소 권한이 승인됨")
        } else {
            LogcatManager.w("MainActivity", "일부 저장소 권한이 거부됨: $permissions")
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        /**
         * 네이티브 리소스 정리를 별도 스레드에서 fire-and-forget으로 수행.
         * 프로세스 종료 직전에 호출되므로 managed scope 불필요.
         */
        private fun cleanupNativeResources(label: String) {
            Thread {
                try {
                    com.inik.camcon.NativeLifecycle.closeCameraAndLog()
                    LogcatManager.d(TAG, "$label: 네이티브 리소스 정리 완료")
                } catch (e: Exception) {
                    LogcatManager.w(TAG, "$label: 네이티브 리소스 정리 중 오류", e)
                }
            }.start()
        }

        /**
         * 앱을 완전히 재시작하는 함수
         */
        fun forceRestartApp(activity: ComponentActivity) {
            try {
                LogcatManager.d(TAG, "앱 강제 재시작 시작")

                // 1. 먼저 Activity 상태 정리
                activity.finishAffinity()

                // 2. 네이티브 리소스 정리를 백그라운드에서 수행
                cleanupNativeResources("forceRestart")

                // 3. 더 긴 지연 후 재시작 실행 (네이티브 정리 완료 대기)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        LogcatManager.d(TAG, "앱 재시작 실행")

                        // 재시작 Intent 생성
                        val restartIntent = Intent(activity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) // 추가
                        }

                        activity.startActivity(restartIntent)

                        // 프로세스 종료는 더 긴 지연 후 실행
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            LogcatManager.d(TAG, "프로세스 종료 실행")
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }, 1000) // 1초 후 프로세스 종료

                    } catch (e: Exception) {
                        LogcatManager.e(TAG, "재시작 실행 중 오류", e)
                        // Fallback: PackageManager 사용
                        restartWithPackageManager(activity)
                    }
                }, 2000) // 2초 지연으로 네이티브 정리 완료 대기

                LogcatManager.d(TAG, "재시작 예약 완료")

            } catch (e: Exception) {
                LogcatManager.e(TAG, "앱 재시작 중 오류", e)
                // 오류 발생 시 PackageManager 재시작 시도
                restartWithPackageManager(activity)
            }
        }

        /**
         * PackageManager를 사용한 재시작 (Fallback)
         */
        private fun restartWithPackageManager(activity: ComponentActivity) {
            try {
                LogcatManager.d(TAG, "PackageManager 재시작 시도")
                val packageManager = activity.packageManager
                val restartIntent = packageManager.getLaunchIntentForPackage(activity.packageName)

                if (restartIntent != null) {
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                    activity.startActivity(restartIntent)
                    activity.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                } else {
                    LogcatManager.e(TAG, "PackageManager 재시작 실패 - Intent 없음")
                    activity.finishAffinity()
                    System.exit(0)
                }
            } catch (e: Exception) {
                LogcatManager.e(TAG, "PackageManager 재시작 중 오류", e)
                activity.finishAffinity()
                System.exit(0)
            }
        }

        /**
         * 간단한 앱 재시작 (사용자 수동 재시작 안내)
         */
        fun simpleRestartApp(activity: ComponentActivity) {
            try {
                LogcatManager.d(TAG, "간단한 앱 재시작 시작")

                // 네이티브 리소스 정리
                cleanupNativeResources("simpleRestart")

                // 0.5초 후 앱 종료 (사용자가 수동으로 재시작해야 함)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    LogcatManager.d(TAG, "앱 종료 - 사용자 수동 재시작 필요")
                    activity.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }, 500)

            } catch (e: Exception) {
                LogcatManager.e(TAG, "간단한 앱 재시작 중 오류", e)
                activity.finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }

        /**
         * 시스템 재시작 메커니즘을 사용한 안전한 재시작
         */
        fun systemRestartApp(activity: ComponentActivity) {
            try {
                LogcatManager.d(TAG, "시스템 재시작 시작")

                // 1. makeRestartActivityTask를 사용한 즉시 재시작
                val packageManager = activity.packageManager
                val intent = packageManager.getLaunchIntentForPackage(activity.packageName)
                val componentName = intent?.component

                if (componentName != null) {
                    val mainIntent = Intent.makeRestartActivityTask(componentName)
                    activity.startActivity(mainIntent)
                    LogcatManager.d(TAG, "makeRestartActivityTask 실행 완료")
                } else {
                    LogcatManager.e(TAG, "ComponentName을 찾을 수 없음")
                    // Fallback: 기존 방식
                    restartWithPackageManager(activity)
                    return
                }

                // 2. 네이티브 리소스 정리 (백그라운드에서)
                cleanupNativeResources("systemRestart")

                // 3. 프로세스 종료
                kotlin.system.exitProcess(0)

            } catch (e: Exception) {
                LogcatManager.e(TAG, "시스템 재시작 중 오류", e)
                // Fallback: 기존 방식
                restartWithPackageManager(activity)
            }
        }

        /**
         * 즉시 재시작 (가장 빠른 방법)
         */
        fun instantRestartApp(activity: ComponentActivity) {
            try {
                LogcatManager.d(TAG, "즉시 재시작 시작")

                // 1. makeRestartActivityTask를 사용한 즉시 재시작
                val packageManager = activity.packageManager
                val intent = packageManager.getLaunchIntentForPackage(activity.packageName)
                val componentName = intent?.component

                if (componentName != null) {
                    val mainIntent = Intent.makeRestartActivityTask(componentName)
                    activity.startActivity(mainIntent)
                    LogcatManager.d(TAG, "즉시 재시작: makeRestartActivityTask 실행 완료")
                } else {
                    LogcatManager.e(TAG, "즉시 재시작: ComponentName을 찾을 수 없음")
                    // Fallback: 기존 방식
                    val restartIntent = Intent(activity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    activity.startActivity(restartIntent)
                }

                // 2. 네이티브 리소스 정리 (백그라운드에서)
                cleanupNativeResources("instantRestart")

                // 3. 프로세스 종료
                kotlin.system.exitProcess(0)

            } catch (e: Exception) {
                LogcatManager.e(TAG, "즉시 재시작 중 오류", e)
                // Fallback: 기존 방식
                try {
                    activity.finishAffinity()
                    kotlin.system.exitProcess(0)
                } catch (ex: Exception) {
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }

        /**
         * 카메라 정리 완료 후 앱을 재시작하는 안전한 방법
         */
        fun restartAppAfterCameraCleanup(activity: ComponentActivity) {
            try {
                LogcatManager.d(TAG, "카메라 정리 후 앱 재시작 시작")

                // 카메라 정리 완료 콜백을 사용한 안전한 재시작
                com.inik.camcon.NativeLifecycle.closeCameraAsync(
                    object : com.inik.camcon.CameraCleanupCallback {
                        override fun onCleanupComplete(success: Boolean, message: String) {
                            LogcatManager.d(TAG, "카메라 정리 완료: success=$success, message=$message")

                            // 메인 스레드에서 재시작 실행
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try {
                                    // 로그 파일도 닫기
                                    com.inik.camcon.NativeLifecycle.closeLogFile()

                                    // 시스템 재시작 메커니즘 사용
                                    val packageManager = activity.packageManager
                                    val intent = packageManager.getLaunchIntentForPackage(activity.packageName)
                                    val componentName = intent?.component

                                    if (componentName != null) {
                                        val mainIntent = Intent.makeRestartActivityTask(componentName)
                                        activity.startActivity(mainIntent)
                                        LogcatManager.d(TAG, "카메라 정리 후 재시작 실행 완료")
                                    } else {
                                        // Fallback
                                        val restartIntent = Intent(activity, MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        }
                                        activity.startActivity(restartIntent)
                                    }

                                    // 프로세스 정리
                                    activity.finishAffinity()
                                    kotlin.system.exitProcess(0)

                                } catch (e: Exception) {
                                    LogcatManager.e(TAG, "카메라 정리 후 재시작 실행 중 오류", e)
                                    // Fallback: 기존 방식 사용
                                    systemRestartApp(activity)
                                }
                            }
                        }
                    }
                )

            } catch (e: Exception) {
                LogcatManager.e(TAG, "카메라 정리 후 재시작 중 오류", e)
                // Fallback: 기존 방식 사용
                systemRestartApp(activity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val navigateToCameraControl = intent.getBooleanExtra("navigate_to_camera_control", false)

        // C6: connectedDevice FGS는 카메라가 실제로 연결됐을 때만 기동한다.
        // (연결도 없는데 onCreate에서 무조건 startForegroundService → Play 정책 위반·상시 알림·전력 점유)
        // 연결이 성립/해제되는 것을 lifecycle 동안 관찰하여, 활성 연결이 생기면 시작·끊기면 시작 안 함.
        observeConnectionForBackgroundService()

        // 사용자 구독 티어 로그 출력
        lifecycleScope.launch {
            try {
                // 상세한 티어 정보를 한 번만 로그에 출력
                getSubscriptionUseCase.logCurrentTier()
            } catch (e: Exception) {
                LogcatManager.e(TAG, "사용자 티어 정보 로드 실패", e)
            }
        }

        // 저장소 권한 요청
        requestStoragePermissions()

        // USB 디바이스 연결 Intent 처리 + 기존 연결 디바이스 검색을 ViewModel에 위임
        viewModel.initializeUsbState(intent)

        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()

            var showBatteryDialog by remember { mutableStateOf(false) }

            // 한번만 다이얼로그를 띄움
            LaunchedEffect(Unit) {
                // Compose 컴포지션 직후 즉시 확인
                if (shouldShowCameraConnectionOptimizationDialog() && !batteryDialogShown) {
                    showBatteryDialog = true
                    batteryDialogShown = true
                }
            }

            // 첫 사용자 온보딩 표시 여부 — DataStore 초깃값(null) 동안 깜빡임을 막기 위해 null 상태 유지.
            val onboardingCompleted by appSettingsViewModel.isOnboardingCompleted
                .collectAsStateWithLifecycle()

            // WindowSizeClass 계산 후 CompositionLocal로 전파.
            // 다크 테마 고정이므로 CamConTheme은 변경하지 않는다.
            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSizeClass: WindowSizeClass = calculateWindowSizeClass(this)

            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                CamConTheme() {
                    Surface {
                        when (onboardingCompleted) {
                            null -> {
                                // 첫 emit 대기 — 빈 Surface 만 표시. (깜빡임/잘못된 분기 방지)
                            }

                            false -> {
                                OnboardingScreen(
                                    onFinish = {
                                        appSettingsViewModel.setOnboardingCompleted(true)
                                    }
                                )
                            }

                            true -> {
                                if (showBatteryDialog) {
                                    CameraConnectionOptimizationDialog(
                                        onDismissRequest = {
                                            showBatteryDialog = false
                                            markCameraConnectionOptimizationDialogShown()
                                        },
                                        onGoToSettings = {
                                            // 배터리 최적화 예외 설정 화면으로 이동
                                            openBatteryOptimizationSettings()
                                            showBatteryDialog = false
                                            markCameraConnectionOptimizationDialogShown()
                                        }
                                    )
                                }
                                MainScreen(
                                    onSettingsClick = {
                                        startActivity(Intent(this, SettingsActivity::class.java))
                                    },
                                    globalManager = globalManager,
                                    navigateToCameraControl = navigateToCameraControl
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // USB Intent 처리를 ViewModel에 위임
        viewModel.handleUsbIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        LogcatManager.d(TAG, " 앱 포그라운드 진입 - 백그라운드 서비스 상태 확인")

        // 앱이 다시 활성화될 때 USB 상태만 확인 (디바이스 재검색은 하지 않음)
        viewModel.checkUsbPermissionStatus()

        // 포그라운드 진입 시점은 FGS 시작이 OS 정책상 허용되므로,
        // onPause 백그라운드 시점에 재시작하지 못한 서비스를 여기서 보장한다.
        // C6: 단, 카메라가 실제로 연결돼 있을 때만 재시작한다(미연결 idle FGS 방지).
        lifecycleScope.launch(ioDispatcher) {
            try {
                if (!globalManager.globalConnectionState.value.isAnyConnectionActive) {
                    return@launch
                }
                val isServiceRunning = isServiceRunning(BackgroundSyncService::class.java)
                if (!isServiceRunning) {
                    LogcatManager.d(TAG, " 포그라운드 진입 - 백그라운드 서비스 재시작")
                    withContext(Dispatchers.Main) {
                        BackgroundSyncService.startService(this@MainActivity)
                    }
                }
            } catch (e: Exception) {
                LogcatManager.w(TAG, "포그라운드 백그라운드 서비스 재시작 실패", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        LogcatManager.d(TAG, " 앱 백그라운드 진입 - 백그라운드 서비스 확인")

        // 앱이 백그라운드로 이동할 때 백그라운드 서비스가 실행 중인지 확인
        lifecycleScope.launch(ioDispatcher) {
            try {
                // C6: 카메라가 실제로 연결돼 있을 때만 백그라운드 수신 서비스를 유지/재시작한다.
                // (미연결 상태에서 FGS를 살려두면 Play 정책 위반·상시 알림·전력 점유)
                if (!globalManager.globalConnectionState.value.isAnyConnectionActive) {
                    return@launch
                }
                // 백그라운드 서비스가 실행 중인지 확인
                val isServiceRunning = isServiceRunning(BackgroundSyncService::class.java)
                if (!isServiceRunning) {
                    LogcatManager.d(TAG, " 백그라운드 서비스 재시작 필요")
                    withContext(Dispatchers.Main) {
                        try {
                            BackgroundSyncService.startService(this@MainActivity)
                        } catch (e: Exception) {
                            // Android 12+에서 앱이 완전히 백그라운드 상태이면
                            // startForegroundService가 ForegroundServiceStartNotAllowedException을
                            // 던진다. onPause 시점에 FGS 재시작은 OS 정책상 보장되지 않으므로
                            // 실패를 명시적으로 기록하고 다음 포그라운드 진입(onResume) 시 재시도에 맡긴다.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                e is android.app.ForegroundServiceStartNotAllowedException
                            ) {
                                LogcatManager.w(
                                    TAG,
                                    "백그라운드 상태에서 포그라운드 서비스 시작 불가 - 다음 포그라운드 진입 시 재시도",
                                    e
                                )
                            } else {
                                throw e
                            }
                        }
                    }
                } else {
                    LogcatManager.d(TAG, " 백그라운드 서비스 이미 실행 중")
                }
            } catch (e: Exception) {
                LogcatManager.w(TAG, "백그라운드 서비스 상태 확인 실패", e)
            }
        }
    }

    /**
     * C6: 활성 카메라 연결이 생겼을 때만 BackgroundSyncService(connectedDevice FGS)를 기동한다.
     * 연결 상태를 lifecycle 동안 관찰하여 false→true 전이에서만 startForegroundService를 호출하고,
     * 미연결 상태에서는 시작하지 않는다. (연결 해제 시 서비스의 self-stop 로직이 정리를 담당)
     * 앱이 이미 활성 연결 상태로 진입(USB attach intent 등)했다면 즉시 시작된다.
     */
    private fun observeConnectionForBackgroundService() {
        lifecycleScope.launch {
            // STARTED 동안만 수집 — 백그라운드에서 startForegroundService를 호출하면
            // Android 12+에서 ForegroundServiceStartNotAllowedException 위험이 있다.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                globalManager.globalConnectionState
                    .map { it.isAnyConnectionActive }
                    .distinctUntilChanged()
                    .collect { isConnected ->
                        if (!isConnected) return@collect
                        try {
                            if (!isServiceRunning(BackgroundSyncService::class.java)) {
                                BackgroundSyncService.startService(this@MainActivity)
                                LogcatManager.d(TAG, "카메라 연결 감지 - BackgroundSyncService 시작 요청됨")
                            }
                        } catch (e: Exception) {
                            LogcatManager.w(TAG, "BackgroundSyncService 시작 실패", e)
                        }
                    }
            }
        }
    }

    /**
     * 서비스 실행 상태 확인
     */
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        return try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    LogcatManager.d(TAG, "서비스 실행 중: ${serviceClass.simpleName}")
                    return true
                }
            }
            LogcatManager.d(TAG, "서비스 실행되지 않음: ${serviceClass.simpleName}")
            false
        } catch (e: Exception) {
            LogcatManager.w(TAG, "서비스 상태 확인 실패", e)
            false
        }
    }

    /**
     * 저장소 권한 요청
     */
    private fun requestStoragePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: 세분화된 미디어 권한
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            // Android 13+: 알림 권한 (자동 연결 FGS 알림 표시용, 매니페스트에 이미 선언됨)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Android 12 이하: 기존 저장소 권한
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            LogcatManager.d("MainActivity", "저장소 권한 요청: $permissionsToRequest")
            storagePermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            LogcatManager.d("MainActivity", "저장소 권한이 이미 승인됨")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 구성 변경(언어/다크모드/글꼴/밀도/회전 등)에 의한 재생성에서는 네이티브/USB 세션을 정리하지 않는다.
        // 정리하면 촬영 도중 USB 카메라 연결이 끊기고 USB attach/detach 브로드캐스트 수신까지 해제된다.
        // (manifest configChanges 로 대부분 재생성을 막지만, 목록 밖 구성 변경에 대한 방어선)
        if (isChangingConfigurations) {
            LogcatManager.d(TAG, "구성 변경에 의한 재생성 - 네이티브/USB 정리 건너뜀")
            return
        }
        // Activity가 종료될 때 매니저 정리
        try {
            // 명시적으로 카메라 세션 종료 + 로그 파일 닫기 - 백그라운드에서 안전하게 수행.
            // ViewModel.cleanup 에는 없는 네이티브 정리이므로 Activity 에 유지한다.
            cleanupNativeResources("onDestroy")

            // USB 매니저 + 전역 연결 매니저 정리를 ViewModel 에 위임
            viewModel.cleanup()
        } catch (e: Exception) {
            LogcatManager.w(TAG, "매니저 정리 중 오류", e)
        }
    }

    /**
     * 카메라 연결 최적화 다이얼로그 표시 여부 확인
     */
    private fun shouldShowCameraConnectionOptimizationDialog(): Boolean {
        // 앱 설치 후 처음 실행 시에만 표시 + 아직 배터리 최적화 예외 미적용 시 노출
        val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val dialogShown = prefs.getBoolean("camera_connection_optimization_dialog_shown", false)
        return !dialogShown && !isIgnoringBatteryOptimizations()
    }

    /**
     * 카메라 연결 최적화 다이얼로그 표시 완료 기록
     */
    private fun markCameraConnectionOptimizationDialogShown() {
        val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("camera_connection_optimization_dialog_shown", true).apply()
    }

    /**
     * 배터리 최적화(Doze 모드) 무시 상태인지 확인
     */
    private fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 배터리 최적화 예외 설정 화면으로 이동
     */
    private fun openBatteryOptimizationSettings() {
        try {
            val intent =
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            LogcatManager.e(TAG, "배터리 최적화 예외 설정 화면 이동 실패", e)
            // 대체 절차: 앱 상세 정보 화면
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (ex: Exception) {
                LogcatManager.e(TAG, "APP 상세 정보 화면도 이동 실패", ex)
            }
        }
    }

}

/**
 * 메인 액티비티 프리뷰
 */
@Composable
fun MainActivityPreview() {
    CamConTheme {
        // 프리뷰용 간단한 컴포넌트
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "CamCon - 메인 화면",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}
