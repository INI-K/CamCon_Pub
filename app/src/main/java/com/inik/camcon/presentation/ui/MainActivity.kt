package com.inik.camcon.presentation.ui

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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
import com.inik.camcon.presentation.navigation.MainNavigation
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.CameraControlScreen
import com.inik.camcon.presentation.ui.screens.PhotoPreviewScreen
import com.inik.camcon.presentation.ui.screens.ServerPhotosScreen
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
}

@Composable
fun MainScreen(
    onSettingsClick: () -> Unit,
    globalManager: CameraConnectionGlobalManager
) {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.PhotoPreview,
        BottomNavItem.CameraControl,
        BottomNavItem.ServerPhotos
    )

    // 전역 연결 상태 모니터링
    val globalConnectionState by globalManager.globalConnectionState.collectAsState()
    val activeConnectionType by globalManager.activeConnectionType.collectAsState()
    val connectionStatusMessage by globalManager.connectionStatusMessage.collectAsState()

    // 전역 상태 변화 시 로그 출력
    LaunchedEffect(globalConnectionState) {
        Log.d("MainScreen", "전역 연결 상태 변화: $connectionStatusMessage")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name) +
                                if (activeConnectionType != null) " - ${connectionStatusMessage}" else ""
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                },
                backgroundColor = when (activeConnectionType) {
                    com.inik.camcon.domain.model.CameraConnectionType.AP_MODE ->
                        MaterialTheme.colors.primary

                    com.inik.camcon.domain.model.CameraConnectionType.STA_MODE ->
                        MaterialTheme.colors.secondary

                    com.inik.camcon.domain.model.CameraConnectionType.USB ->
                        MaterialTheme.colors.surface

                    else -> MaterialTheme.colors.primary
                },
                contentColor = MaterialTheme.colors.onPrimary
            )
        },
        bottomBar = {
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
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        selectedContentColor = MaterialTheme.colors.primary,
                        unselectedContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = BottomNavItem.CameraControl.route,
            Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.PhotoPreview.route) { PhotoPreviewScreen() }
            composable(BottomNavItem.CameraControl.route) {
                CameraControlScreen()
            }
            composable(BottomNavItem.ServerPhotos.route) { ServerPhotosScreen() }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var usbCameraManager: UsbCameraManager

    @Inject
    lateinit var globalManager: CameraConnectionGlobalManager

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // USB 디바이스 연결 Intent 처리를 비동기로 수행
        lifecycleScope.launch(Dispatchers.IO) {
            handleUsbIntent(intent)
        }

        setContent {
            CamConTheme {
                MainNavigation(
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onPtpipConnectionClick = {
                        startActivity(Intent(this, PtpipConnectionActivity::class.java))
                    }
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

                    // 전역 상태 업데이트
                    //globalManager.forceUpdateState() // 불필요한 호출 제거
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    Log.d(TAG, "USB 디바이스가 분리됨: ${it.deviceName}")

                    // 전역 상태 업데이트
                    //globalManager.forceUpdateState() // 불필요한 호출 제거
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

        // 전역 상태 업데이트
        //lifecycleScope.launch {
        //    globalManager.forceUpdateState() // 불필요한 호출 제거
        //}
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

    override fun onDestroy() {
        super.onDestroy()
        // Activity가 종료될 때 USB 매니저 정리
        try {
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
        MainNavigation(
            onSettingsClick = { },
            onPtpipConnectionClick = { }
        )
    }
}
