package com.inik.camcon.presentation.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.ApModeContent
import com.inik.camcon.presentation.ui.screens.components.DarkTabRow
import com.inik.camcon.presentation.ui.screens.components.DarkTopBar
import com.inik.camcon.presentation.ui.screens.components.StaModeContent
import com.inik.camcon.presentation.ui.screens.components.darkScreenBackground
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import kotlinx.coroutines.launch

/**
 * PTP/IP 연결 화면
 * 
 * AP 모드와 STA 모드를 탭으로 구분하여 표시
 */
@Composable
fun PtpipConnectionScreen(
    onBackClick: () -> Unit,
    ptpipViewModel: PtpipViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // 탭 상태 - AP 모드를 먼저 표시 (index 0)
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val tabTitles = remember { listOf("AP 모드", "STA 모드") }

    // 위치 권한 상태
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showPermissionDialog by remember { mutableStateOf(false) }

    // 권한 요청 launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (!isGranted) {
            showPermissionDialog = true
        }
    }
    val onRequestLocationPermission by rememberUpdatedState(
        newValue = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    )

    // 상태 수집
    val connectionState by ptpipViewModel.connectionState.collectAsState()
    val discoveredCameras by ptpipViewModel.discoveredCameras.collectAsState()
    val isDiscovering by ptpipViewModel.isDiscovering.collectAsState()
    val isConnecting by ptpipViewModel.isConnecting.collectAsState()
    val errorMessage by ptpipViewModel.errorMessage.collectAsState()
    val selectedCamera by ptpipViewModel.selectedCamera.collectAsState()
    val cameraInfo by ptpipViewModel.cameraInfo.collectAsState()
    val isPtpipEnabled by ptpipViewModel.isPtpipEnabled.collectAsState(initial = false)
    val wifiNetworkState by ptpipViewModel.wifiNetworkState.collectAsState()
    val isAutoReconnectEnabled by ptpipViewModel.isAutoReconnectEnabled.collectAsState(initial = false)
    val isWifiConnected = ptpipViewModel.isWifiConnected()
    val wifiCapabilities = ptpipViewModel.getWifiCapabilities()

    // 에러 메시지 표시
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            ptpipViewModel.clearError()
        }
    }

    // 권한 다이얼로그
    PermissionRequiredDialog(
        visible = showPermissionDialog,
        onDismiss = { showPermissionDialog = false }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .darkScreenBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            DarkTopBar(
                title = "카메라 연결",
                subtitle = "PTP/IP 연결 모드 선택",
                onBack = onBackClick,
                actions = {
                    TopBarActionButtons(
                        isDiscovering = isDiscovering,
                        onRefresh = { ptpipViewModel.discoverCameras() },
                        onOpenWifiSettings = { openWifiSettings(context) }
                    )
                }
            )
            DarkTabRow(
                tabs = tabTitles,
                selectedIndex = pagerState.currentPage,
                onTabSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
            )

            // 탭 내용
            PtpipModePager(
                pagerState = pagerState,
                ptpipViewModel = ptpipViewModel,
                connectionState = connectionState,
                discoveredCameras = discoveredCameras,
                isDiscovering = isDiscovering,
                isConnecting = isConnecting,
                selectedCamera = selectedCamera,
                cameraInfo = cameraInfo,
                isPtpipEnabled = isPtpipEnabled,
                isWifiConnected = isWifiConnected,
                wifiCapabilities = wifiCapabilities,
                wifiNetworkState = wifiNetworkState,
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = onRequestLocationPermission
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}
@Composable
private fun PermissionRequiredDialog(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("위치 권한 필요") },
        text = {
            Text("Wi-Fi 네트워크 이름을 표시하려면 위치 권한이 필요합니다.\n설정에서 직접 권한을 허용해주세요.")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}
@Composable
private fun TopBarActionButtons(
    isDiscovering: Boolean,
    onRefresh: () -> Unit,
    onOpenWifiSettings: () -> Unit
) {
    IconButton(
        onClick = onRefresh,
        enabled = !isDiscovering
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = "새로고침",
            tint = Color(0xFFFFD6AE)
        )
    }
    IconButton(onClick = onOpenWifiSettings) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Wi-Fi 설정",
            tint = Color(0xFFFFD6AE)
        )
    }
}
private fun openWifiSettings(context: android.content.Context) {
    val intent = try {
        Intent(Settings.Panel.ACTION_WIFI)
    } catch (_: Exception) {
        Intent(Settings.ACTION_WIFI_SETTINGS)
    }
    context.startActivity(intent)
}
@Composable
private fun PtpipModePager(
    pagerState: androidx.compose.foundation.pager.PagerState,
    ptpipViewModel: PtpipViewModel,
    connectionState: com.inik.camcon.domain.model.PtpipConnectionState,
    discoveredCameras: List<com.inik.camcon.domain.model.PtpipCamera>,
    isDiscovering: Boolean,
    isConnecting: Boolean,
    selectedCamera: com.inik.camcon.domain.model.PtpipCamera?,
    cameraInfo: com.inik.camcon.domain.model.PtpipCameraInfo?,
    isPtpipEnabled: Boolean,
    isWifiConnected: Boolean,
    wifiCapabilities: com.inik.camcon.domain.model.WifiCapabilities,
    wifiNetworkState: com.inik.camcon.domain.model.WifiNetworkState,
    isAutoReconnectEnabled: Boolean,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> ApModeContent(
                ptpipViewModel = ptpipViewModel,
                connectionState = connectionState,
                discoveredCameras = discoveredCameras,
                isDiscovering = isDiscovering,
                isConnecting = isConnecting,
                selectedCamera = selectedCamera,
                cameraInfo = cameraInfo,
                isPtpipEnabled = isPtpipEnabled,
                isWifiConnected = isWifiConnected,
                wifiCapabilities = wifiCapabilities,
                wifiNetworkState = wifiNetworkState,
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = onRequestPermission
            )
            else -> StaModeContent(
                ptpipViewModel = ptpipViewModel,
                connectionState = connectionState,
                discoveredCameras = discoveredCameras,
                isDiscovering = isDiscovering,
                isConnecting = isConnecting,
                selectedCamera = selectedCamera,
                cameraInfo = cameraInfo,
                isPtpipEnabled = isPtpipEnabled,
                isWifiConnected = isWifiConnected,
                wifiCapabilities = wifiCapabilities,
                wifiNetworkState = wifiNetworkState,
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = onRequestPermission
            )
        }
    }
}

@Preview(name = "PTPIP Connection Screen", showBackground = true)
@Composable
private fun PtpipConnectionScreenPreview() {
    CamConTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .darkScreenBackground()
                .padding(16.dp)
        ) {
            DarkTopBar(
                title = "카메라 연결",
                subtitle = "PTP/IP 연결 모드 선택",
                onBack = {}
            )
            DarkTabRow(
                tabs = listOf("AP 모드", "STA 모드"),
                selectedIndex = 0,
                onTabSelected = {},
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                "카메라 연결 화면 프리뷰",
                style = MaterialTheme.typography.h6,
                color = Color(0xFFFFD7B1),
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}
