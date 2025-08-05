package com.inik.camcon.presentation.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.CameraControlScreen
import com.inik.camcon.presentation.ui.screens.MyPhotosScreen
import com.inik.camcon.presentation.ui.screens.PhotoPreviewScreen
import com.inik.camcon.presentation.ui.screens.components.PtpTimeoutDialog
import com.inik.camcon.presentation.ui.screens.components.UsbInitializationOverlay
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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

@Composable
fun CameraConnectionOptimizationDialog(
    onDismissRequest: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = null) },
        title = {
            androidx.compose.material3.Text(
                "카메라 연결 최적화 설정",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Text(
                    text = "카메라와의 안정적인 연결을 위해 배터리 최적화 예외 설정을 권장합니다.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
                androidx.compose.material3.Text(
                    text = "• 실시간 카메라 제어 유지\n" +
                            "• 사진 자동 전송 안정성 향상\n" +
                            "• 백그라운드 연결 끊김 방지\n\n" +
                            "※ 카메라 주변기기 연결 앱으로서 Google Play 정책에 따라 허용됩니다.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onGoToSettings) {
                androidx.compose.material3.Text("설정하기")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismissRequest) {
                androidx.compose.material3.Text("나중에")
            }
        }
    )
}

@Composable
fun MainScreen(
    onSettingsClick: () -> Unit,
    globalManager: CameraConnectionGlobalManager,
    cameraViewModel: CameraViewModel = hiltViewModel()
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

    // 전역 연결 상태 모니터링
    val globalConnectionState by globalManager.globalConnectionState.collectAsState()
    val activeConnectionType by globalManager.activeConnectionType.collectAsState()
    val connectionStatusMessage by globalManager.connectionStatusMessage.collectAsState()

    // CameraViewModel의 USB 초기화 상태 모니터링
    val cameraUiState by cameraViewModel.uiState.collectAsState()

    // LocalContext를 @Composable 내에서 미리 가져오기
    val context = LocalContext.current

    // 전역 상태 변화 시 로그 출력
    LaunchedEffect(globalConnectionState) {
        Log.d("MainScreen", "전역 연결 상태 변화: $connectionStatusMessage")
    }

    // 테마 모드 상태
    val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
    val themeMode by appSettingsViewModel.themeMode.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

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

            androidx.compose.material.AlertDialog(
                onDismissRequest = { /* 다이얼로그 닫기 방지 */ },
                title = { Text("앱 재시작 필요") },
                text = {
                androidx.compose.foundation.layout.Column {
                    if (isRestarting) {
                        Text("재시작 준비 중입니다...")
                        androidx.compose.foundation.layout.Spacer(
                            modifier = androidx.compose.ui.Modifier.height(8.dp)
                        )
                        Text(
                            "잠시만 기다려주세요. 카메라 연결을 정리하고 있습니다.",
                            style = androidx.compose.material.MaterialTheme.typography.caption
                        )
                    } else {
                        Text("카메라 연결 문제로 인해 앱을 완전히 재시작해야 합니다.")
                        androidx.compose.foundation.layout.Spacer(
                            modifier = androidx.compose.ui.Modifier.height(8.dp)
                        )
                        Text(
                            "• '즉시 재시작': 버튼 클릭 즉시 재시작\n• '종료': 앱만 종료 (수동 재실행 필요)",
                            style = androidx.compose.material.MaterialTheme.typography.caption
                        )
                    }
                    }
                },
                confirmButton = {
                    if (!isRestarting) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                                8.dp
                            )
                        ) {
                            androidx.compose.material.TextButton(
                                onClick = {
                                    isRestarting = true
                                    // 모든 상태 정리
                                    cameraViewModel.clearPtpTimeout()

                                    // 시스템 재시작 메커니즘을 사용한 재시작
                                    val activity = context as? ComponentActivity
                                    activity?.let { act ->
                                        MainActivity.restartAppAfterCameraCleanup(act)
                                    }
                                }
                            ) { Text("즉시 재시작") }

                            androidx.compose.material.TextButton(
                                onClick = {
                                    isRestarting = true
                                    // 모든 상태 정리
                                    cameraViewModel.clearPtpTimeout()

                                    // 간단한 재시작 (앱 종료만)
                                    val activity = context as? ComponentActivity
                                    activity?.let { act ->
                                        MainActivity.systemRestartApp(act)
                                    }
                                }
                            ) { Text("종료") }
                        }
                    }
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            )
        }

        // USB 분리 다이얼로그 표시
        if (cameraUiState.isUsbDisconnected == true) {
            androidx.compose.material.AlertDialog(
                onDismissRequest = { cameraViewModel.clearUsbDisconnection() },
                title = {
                    Text(
                        "USB 디바이스 분리",
                        style = androidx.compose.material.MaterialTheme.typography.h6,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = androidx.compose.material.MaterialTheme.colors.error
                    )
                },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            "카메라 USB 연결이 끊어졌습니다.",
                            style = androidx.compose.material.MaterialTheme.typography.body1
                        )
                        androidx.compose.foundation.layout.Spacer(
                            modifier = androidx.compose.ui.Modifier.height(
                                8.dp
                            )
                        )
                        Text(
                            "• USB 케이블 연결을 확인해주세요\n• 카메라 전원을 확인해주세요\n• 카메라를 PC 모드로 설정해주세요",
                            style = androidx.compose.material.MaterialTheme.typography.caption,
                            color = androidx.compose.material.MaterialTheme.colors.onSurface.copy(
                                alpha = 0.7f
                            )
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material.TextButton(
                        onClick = { cameraViewModel.clearUsbDisconnection() }
                    ) {
                        Text("확인")
                    }
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            )
        }

        // 카메라 상태 점검 다이얼로그 표시 (초기화가 완료된 후에만 표시)
        if (cameraUiState.showCameraStatusCheckDialog == true &&
            !cameraUiState.isUsbInitializing &&
            !cameraUiState.isCameraInitializing
        ) {
            androidx.compose.material.AlertDialog(
                onDismissRequest = { cameraViewModel.dismissCameraStatusCheckDialog() },
                title = {
                    Text(
                        "카메라 상태 점검 필요",
                        style = androidx.compose.material.MaterialTheme.typography.h6,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = androidx.compose.material.MaterialTheme.colors.error
                    )
                },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            "카메라가 정상적으로 동작하지 않습니다.",
                            style = androidx.compose.material.MaterialTheme.typography.body1
                        )
                        androidx.compose.foundation.layout.Spacer(
                            modifier = androidx.compose.ui.Modifier.height(12.dp)
                        )
                        Text(
                            "다음 사항을 확인해주세요:",
                            style = androidx.compose.material.MaterialTheme.typography.body2,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        androidx.compose.foundation.layout.Spacer(
                            modifier = androidx.compose.ui.Modifier.height(8.dp)
                        )
                        Text(
                            "• 카메라 전원이 켜져 있는지 확인\n" +
                                    "• 카메라 배터리가 충분한지 확인\n" +
                                    "• USB 케이블 연결 상태 확인\n" +
                                    "• 카메라가 PC 연결 모드로 설정되어 있는지 확인\n" +
                                    "• 카메라를 껐다가 다시 켜보세요",
                            style = androidx.compose.material.MaterialTheme.typography.caption,
                            color = androidx.compose.material.MaterialTheme.colors.onSurface.copy(
                                alpha = 0.8f
                            )
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material.TextButton(
                        onClick = { cameraViewModel.dismissCameraStatusCheckDialog() }
                    ) {
                        Text("확인")
                    }
                },
                dismissButton = {
                    androidx.compose.material.TextButton(
                        onClick = {
                            cameraViewModel.dismissCameraStatusCheckDialog()
                            cameraViewModel.refreshUsbDevices()
                        }
                    ) {
                        Text("다시 연결")
                    }
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false // 사용자가 명시적으로 확인하도록
                )
            )
        }

        Scaffold(
            bottomBar = {
                // 전체화면 모드가 아닐 때만 하단 탭 표시
                if (!isFullscreen) {
                    BottomNavigation(
                        backgroundColor = MaterialTheme.colors.surface,
                        contentColor = MaterialTheme.colors.onSurface
                    ) {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination

                        items.forEach { screen ->
                            BottomNavigationItem(
                                icon = {
                                    Icon(
                                        screen.icon,
                                        contentDescription = stringResource(screen.titleRes)
                                    )
                                },
                                label = { Text(stringResource(screen.titleRes)) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
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
                                },
                                selectedContentColor = MaterialTheme.colors.primary,
                                unselectedContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController,
                startDestination = BottomNavItem.CameraControl.route,
                Modifier.padding(
                    if (isFullscreen) PaddingValues(0.dp) else innerPadding
                )
            ) {
                composable(BottomNavItem.PhotoPreview.route) { PhotoPreviewScreen() }
                composable(BottomNavItem.CameraControl.route) {
                    // AP 모드일 때는 사진 수신 대기 화면, 아니면 카메라 컨트롤 화면
                    if (activeConnectionType == com.inik.camcon.domain.model.CameraConnectionType.AP_MODE) {
                        com.inik.camcon.presentation.ui.screens.ApModePhotoReceiveScreen(
                            viewModel = cameraViewModel // 전역 ViewModel 전달
                        )
                    } else {
                        CameraControlScreen(
                            viewModel = cameraViewModel, // 전역 ViewModel 전달
                            onFullscreenChange = { isFullscreen = it }
                        )
                    }
                }
                composable(BottomNavItem.ServerPhotos.route) { MyPhotosScreen() }
                // 설정은 별도 액티비티로 처리하므로 여기서 제외
            }
        }

        // USB 연결 및 초기화 상태에 따른 UI 블로킹 오버레이
        if (globalConnectionState.ptpipConnectionState == PtpipConnectionState.CONNECTING ||
            connectionStatusMessage.contains("초기화 중") ||
            cameraUiState.isUsbInitializing ||
            cameraUiState.isCameraInitializing  // 카메라 이벤트 리스너 초기화 상태 추가
        ) {
            UsbInitializationOverlay(
                message = when {
                    cameraUiState.isCameraInitializing -> "카메라 이벤트 초기화 중..."
                    cameraUiState.isUsbInitializing -> cameraUiState.usbInitializationMessage
                        ?: "USB 카메라 초기화 중..."
                    else -> connectionStatusMessage
                }
            )
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var batteryDialogShown = false

    @Inject
    lateinit var usbCameraManager: UsbCameraManager

    @Inject
    lateinit var globalManager: CameraConnectionGlobalManager

    @Inject
    lateinit var getSubscriptionUseCase: GetSubscriptionUseCase

    // 권한 요청 런처
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.d("MainActivity", "모든 저장소 권한이 승인됨")
        } else {
            Log.w("MainActivity", "일부 저장소 권한이 거부됨: $permissions")
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        /**
         * 앱을 완전히 재시작하는 함수
         */
        fun forceRestartApp(activity: ComponentActivity) {
            try {
                Log.d(TAG, "앱 강제 재시작 시작")

                // 1. 먼저 Activity 상태 정리
                activity.finishAffinity()

                // 2. 네이티브 리소스 정리를 백그라운드 스레드에서 수행
                Thread {
                    try {
                        Log.d(TAG, "closeCamera 호출")
                        com.inik.camcon.CameraNative.closeCamera()
                        com.inik.camcon.CameraNative.closeLogFile()
                        Log.d(TAG, "네이티브 리소스 정리 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "네이티브 리소스 정리 중 오류", e)
                    }
                }.start()

                // 3. 더 긴 지연 후 재시작 실행 (네이티브 정리 완료 대기)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        Log.d(TAG, "앱 재시작 실행")

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
                            Log.d(TAG, "프로세스 종료 실행")
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }, 1000) // 1초 후 프로세스 종료

                    } catch (e: Exception) {
                        Log.e(TAG, "재시작 실행 중 오류", e)
                        // Fallback: PackageManager 사용
                        restartWithPackageManager(activity)
                    }
                }, 2000) // 2초 지연으로 네이티브 정리 완료 대기

                Log.d(TAG, "재시작 예약 완료")

            } catch (e: Exception) {
                Log.e(TAG, "앱 재시작 중 오류", e)
                // 오류 발생 시 PackageManager 재시작 시도
                restartWithPackageManager(activity)
            }
        }

        /**
         * PackageManager를 사용한 재시작 (Fallback)
         */
        private fun restartWithPackageManager(activity: ComponentActivity) {
            try {
                Log.d(TAG, "PackageManager 재시작 시도")
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
                    Log.e(TAG, "PackageManager 재시작 실패 - Intent 없음")
                    activity.finishAffinity()
                    System.exit(0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "PackageManager 재시작 중 오류", e)
                activity.finishAffinity()
                System.exit(0)
            }
        }

        /**
         * 간단한 앱 재시작 (사용자 수동 재시작 안내)
         */
        fun simpleRestartApp(activity: ComponentActivity) {
            try {
                Log.d(TAG, "간단한 앱 재시작 시작")

                // 네이티브 리소스 정리
                Thread {
                    try {
                        com.inik.camcon.CameraNative.closeCamera()
                        com.inik.camcon.CameraNative.closeLogFile()
                        Log.d(TAG, "간단 재시작: 네이티브 리소스 정리 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "간단 재시작: 네이티브 리소스 정리 중 오류", e)
                    }
                }.start()

                // 0.5초 후 앱 종료 (사용자가 수동으로 재시작해야 함)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "앱 종료 - 사용자 수동 재시작 필요")
                    activity.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }, 500)

            } catch (e: Exception) {
                Log.e(TAG, "간단한 앱 재시작 중 오류", e)
                activity.finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }

        /**
         * 시스템 재시작 메커니즘을 사용한 안전한 재시작
         */
        fun systemRestartApp(activity: ComponentActivity) {
            try {
                Log.d(TAG, "시스템 재시작 시작")

                // 1. makeRestartActivityTask를 사용한 즉시 재시작
                val packageManager = activity.packageManager
                val intent = packageManager.getLaunchIntentForPackage(activity.packageName)
                val componentName = intent?.component

                if (componentName != null) {
                    val mainIntent = Intent.makeRestartActivityTask(componentName)
                    activity.startActivity(mainIntent)
                    Log.d(TAG, "makeRestartActivityTask 실행 완료")
                } else {
                    Log.e(TAG, "ComponentName을 찾을 수 없음")
                    // Fallback: 기존 방식
                    restartWithPackageManager(activity)
                    return
                }

                // 2. 네이티브 리소스 정리 (백그라운드에서)
                Thread {
                    try {
                        com.inik.camcon.CameraNative.closeCamera()
                        com.inik.camcon.CameraNative.closeLogFile()
                        Log.d(TAG, "시스템 재시작: 네이티브 리소스 정리 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "시스템 재시작: 네이티브 리소스 정리 중 오류", e)
                    }
                }.start()

                // 3. 프로세스 종료
                kotlin.system.exitProcess(0)

            } catch (e: Exception) {
                Log.e(TAG, "시스템 재시작 중 오류", e)
                // Fallback: 기존 방식
                restartWithPackageManager(activity)
            }
        }

        /**
         * 즉시 재시작 (가장 빠른 방법)
         */
        fun instantRestartApp(activity: ComponentActivity) {
            try {
                Log.d(TAG, "즉시 재시작 시작")

                // 1. makeRestartActivityTask를 사용한 즉시 재시작
                val packageManager = activity.packageManager
                val intent = packageManager.getLaunchIntentForPackage(activity.packageName)
                val componentName = intent?.component

                if (componentName != null) {
                    val mainIntent = Intent.makeRestartActivityTask(componentName)
                    activity.startActivity(mainIntent)
                    Log.d(TAG, "즉시 재시작: makeRestartActivityTask 실행 완료")
                } else {
                    Log.e(TAG, "즉시 재시작: ComponentName을 찾을 수 없음")
                    // Fallback: 기존 방식
                    val restartIntent = Intent(activity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    activity.startActivity(restartIntent)
                }

                // 2. 네이티브 리소스 정리 (백그라운드에서)
                Thread {
                    try {
                        com.inik.camcon.CameraNative.closeCamera()
                        com.inik.camcon.CameraNative.closeLogFile()
                        Log.d(TAG, "즉시 재시작: 네이티브 리소스 정리 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "즉시 재시작: 네이티브 리소스 정리 중 오류", e)
                    }
                }.start()

                // 3. 프로세스 종료
                kotlin.system.exitProcess(0)

            } catch (e: Exception) {
                Log.e(TAG, "즉시 재시작 중 오류", e)
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
                Log.d(TAG, "카메라 정리 후 앱 재시작 시작")

                // 카메라 정리 완료 콜백을 사용한 안전한 재시작
                com.inik.camcon.CameraNative.closeCameraAsync(
                    object : com.inik.camcon.CameraCleanupCallback {
                        override fun onCleanupComplete(success: Boolean, message: String) {
                            Log.d(TAG, "카메라 정리 완료: success=$success, message=$message")
                            
                            // 메인 스레드에서 재시작 실행
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try {
                                    // 로그 파일도 닫기
                                    com.inik.camcon.CameraNative.closeLogFile()
                                    
                                    // 시스템 재시작 메커니즘 사용
                                    val packageManager = activity.packageManager
                                    val intent = packageManager.getLaunchIntentForPackage(activity.packageName)
                                    val componentName = intent?.component

                                    if (componentName != null) {
                                        val mainIntent = Intent.makeRestartActivityTask(componentName)
                                        activity.startActivity(mainIntent)
                                        Log.d(TAG, "카메라 정리 후 재시작 실행 완료")
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
                                    Log.e(TAG, "카메라 정리 후 재시작 실행 중 오류", e)
                                    // Fallback: 기존 방식
                                    systemRestartApp(activity)
                                }
                            }
                        }
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "카메라 정리 후 재시작 중 오류", e)
                // Fallback: 기존 방식 사용
                systemRestartApp(activity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 사용자 구독 티어 로그 출력
        lifecycleScope.launch {
            try {
                // 상세한 티어 정보를 한 번만 로그에 출력
                getSubscriptionUseCase.logCurrentTier()
            } catch (e: Exception) {
                Log.e(TAG, "사용자 티어 정보 로드 실패", e)
            }
        }

        // 저장소 권한 요청
        requestStoragePermissions()

        // USB 디바이스 연결 Intent 처리를 비동기로 수행
        lifecycleScope.launch(Dispatchers.IO) {
            handleUsbIntent(intent)
        }

        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsState()

            var showBatteryDialog by remember { mutableStateOf(false) }

            // 한번만 다이얼로그를 띄움
            LaunchedEffect(Unit) {
                // check immediately after Compose composition
                if (shouldShowCameraConnectionOptimizationDialog() && !batteryDialogShown) {
                    showBatteryDialog = true
                    batteryDialogShown = true
                }
            }

            CamConTheme(themeMode = themeMode) {
                Surface {
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
                        globalManager = globalManager
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // USB Intent 처리를 비동기로 수행
        lifecycleScope.launch(Dispatchers.IO) {
            handleUsbIntent(intent)
        }
    }

    private suspend fun handleUsbIntent(intent: Intent) = withContext(Dispatchers.IO) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device: UsbDevice? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                device?.let {
                    Log.d(TAG, "USB 카메라 디바이스가 연결됨: ${it.deviceName}")
                    Log.d(
                        TAG,
                        "제조사ID: 0x${it.vendorId.toString(16)}, 제품ID: 0x${it.productId.toString(16)}"
                    )

                    // 즉시 권한 요청
                    if (!isUsbCameraDevice(it)) {
                        Log.d(TAG, "카메라 디바이스가 아님")
                        return@withContext
                    }

                    Log.d(TAG, "카메라 디바이스 확인됨, 권한 요청")

                    withContext(Dispatchers.Main) {
                        usbCameraManager.requestPermission(it)
                    }
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device: UsbDevice? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                device?.let {
                    Log.d(TAG, "USB 디바이스가 분리됨: ${it.deviceName}")
                }
            }
        }
    }

    private fun isUsbCameraDevice(device: UsbDevice): Boolean {
        // PTP 클래스 확인
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == 6) { // Still Image Capture Device
                return true
            }
        }

        // 알려진 카메라 제조사 확인
        val knownCameraVendors =
            listOf(0x04A9, 0x04B0, 0x054C, 0x04CB) // Canon, Nikon, Sony, Fujifilm
        return device.vendorId in knownCameraVendors
    }

    override fun onResume() {
        super.onResume()
        // 앱이 다시 활성화될 때 USB 상태만 확인 (디바이스 재검색은 하지 않음)
        lifecycleScope.launch(Dispatchers.IO) {
            checkUsbPermissionStatus()
        }
    }

    private suspend fun checkUsbPermissionStatus() = withContext(Dispatchers.IO) {
        try {
            // 이미 연결된 디바이스가 있는지 확인 (새로 검색하지 않음)
            val currentDevice = usbCameraManager.getCurrentDevice()

            if (currentDevice != null) {
                Log.d(TAG, "앱 재개 시 기존 연결된 디바이스 확인: ${currentDevice.deviceName}")

                // 권한 상태만 확인
                if (!usbCameraManager.hasUsbPermission.value) {
                    Log.d(TAG, "기존 디바이스의 권한이 없음, 권한 요청: ${currentDevice.deviceName}")
                    withContext(Dispatchers.Main) {
                        usbCameraManager.requestPermission(currentDevice)
                    }
                } else {
                    Log.d(TAG, "기존 디바이스에 권한이 있음: ${currentDevice.deviceName}")
                }
            } else {
                // 연결된 디바이스가 없으면 StateFlow를 통해 확인
                // 캐시된 목록이 있을 것이므로 빠르게 처리됨
                val devices = usbCameraManager.getCameraDevices()
                if (devices.isNotEmpty()) {
                    Log.d(TAG, "앱 재개 시 캐시된 디바이스 목록 확인: ${devices.size}개")

                    val device = devices.first()
                    if (!usbCameraManager.hasUsbPermission.value) {
                        Log.d(TAG, "권한이 없는 디바이스 발견, 권한 요청: ${device.deviceName}")
                        withContext(Dispatchers.Main) {
                            usbCameraManager.requestPermission(device)
                        }
                    } else {
                        Log.d(TAG, "카메라 디바이스 연결됨")
                    }
                } else {
                    Log.d(TAG, "앱 재개 시 USB 카메라 디바이스 없음")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "USB 권한 상태 확인 중 오류", e)
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
            Log.d("MainActivity", "저장소 권한 요청: $permissionsToRequest")
            storagePermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d("MainActivity", "저장소 권한이 이미 승인됨")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Activity가 종료될 때 USB 매니저 정리
        try {
            // 명시적으로 카메라 세션 종료 - 백그라운드 스레드에서 안전하게 수행
            Thread {
                try {
                    Log.d(TAG, "onDestroy - closeCamera 호출")
                    com.inik.camcon.CameraNative.closeCamera()
                    Log.d(TAG, "카메라 세션 명시적 종료 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "카메라 세션 종료 중 오류", e)
                }
            }.start()

            usbCameraManager.cleanup()
            globalManager.cleanup()

            // libgphoto2 로그 파일 닫기도 백그라운드에서 수행
            Thread {
                try {
                    com.inik.camcon.CameraNative.closeLogFile()
                    Log.d(TAG, "libgphoto2 로그 파일 닫기 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "로그 파일 닫기 중 오류", e)
                }
            }.start()
        } catch (e: Exception) {
            Log.w(TAG, "매니저 정리 중 오류", e)
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
            Log.e(TAG, "배터리 최적화 예외 설정 화면 이동 실패", e)
            // 대체 절차: 앱 상세 정보 화면
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "APP 상세 정보 화면도 이동 실패", ex)
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
            backgroundColor = MaterialTheme.colors.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "CamCon - 메인 화면",
                    style = MaterialTheme.typography.h6
                )
            }
        }
    }
}
