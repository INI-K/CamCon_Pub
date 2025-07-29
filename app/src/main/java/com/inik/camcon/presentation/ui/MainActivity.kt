package com.inik.camcon.presentation.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.CameraControlScreen
import com.inik.camcon.presentation.ui.screens.PhotoPreviewScreen
import com.inik.camcon.presentation.ui.screens.ServerPhotosScreen
import com.inik.camcon.presentation.ui.screens.components.PtpTimeoutDialog
import com.inik.camcon.presentation.ui.screens.components.UsbInitializationOverlay
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
            androidx.compose.material.AlertDialog(
                onDismissRequest = { showRestartDialog = false },
                title = { Text("앱을 재시작할까요?") },
                text = { Text("연결 문제로 인해 앱을 재시작해야 할 수 있습니다. 계속하시겠습니까?") },
                confirmButton = {
                    androidx.compose.material.TextButton(
                        onClick = {
                            showRestartDialog = false
                            cameraViewModel.clearPtpTimeout()
                            // 실제 앱 재시작 로직: MainActivity 재시작 (context 사용)
                            val activity = context as? ComponentActivity
                            activity?.let { act ->
                                val packageManager = act.packageManager
                                val intent =
                                    packageManager.getLaunchIntentForPackage(act.packageName)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                act.startActivity(intent)
                                act.finish()
                                Runtime.getRuntime().exit(0)
                            }
                        }
                    ) { Text("재시작") }
                },
                dismissButton = {
                    androidx.compose.material.TextButton(
                        onClick = {
                            showRestartDialog = false
                        }
                    ) { Text("취소") }
                }
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
                        com.inik.camcon.presentation.ui.screens.ApModePhotoReceiveScreen()
                    } else {
                        CameraControlScreen(
                            onFullscreenChange = { isFullscreen = it }
                        )
                    }
                }
                composable(BottomNavItem.ServerPhotos.route) { ServerPhotosScreen() }
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

    @Inject
    lateinit var usbCameraManager: UsbCameraManager

    @Inject
    lateinit var globalManager: CameraConnectionGlobalManager

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 저장소 권한 요청
        requestStoragePermissions()

        // USB 디바이스 연결 Intent 처리를 비동기로 수행
        lifecycleScope.launch(Dispatchers.IO) {
            handleUsbIntent(intent)
        }

        setContent {
            CamConTheme {
                MainScreen(
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    globalManager = globalManager
                )
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
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
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
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
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
            // 명시적으로 카메라 세션 종료
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    com.inik.camcon.CameraNative.closeCamera()
                    Log.d(TAG, "카메라 세션 명시적 종료 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "카메라 세션 종료 중 오류", e)
                }
            }

            usbCameraManager.cleanup()
            globalManager.cleanup()

            // libgphoto2 로그 파일 닫기
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    com.inik.camcon.CameraNative.closeLogFile()
                    Log.d(TAG, "libgphoto2 로그 파일 닫기 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "로그 파일 닫기 중 오류", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "매니저 정리 중 오류", e)
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
