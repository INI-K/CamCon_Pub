package com.inik.camcon.presentation.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.ApModeContent
import com.inik.camcon.presentation.ui.screens.components.StaModeContent
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
    val tabTitles = listOf("AP 모드", "STA 모드")

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
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("위치 권한 필요") },
            text = {
                Text("Wi-Fi 네트워크 이름을 표시하려면 위치 권한이 필요합니다.\n설정에서 직접 권한을 허용해주세요.")
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("확인")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카메라 연결") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { ptpipViewModel.discoverCameras() },
                        enabled = !isDiscovering
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                    }
                    IconButton(onClick = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                Intent(Settings.Panel.ACTION_WIFI)
                            } catch (e: Exception) {
                                Intent(Settings.ACTION_WIFI_SETTINGS)
                            }
                        } else {
                            Intent(Settings.ACTION_WIFI_SETTINGS)
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Wi-Fi 설정")
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 탭 행
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(title) }
                    )
                }
            }

            // 탭 내용
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
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
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }
                    )
                    1 -> StaModeContent(
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
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Preview(name = "PTPIP Connection Screen", showBackground = true)
@Composable
private fun PtpipConnectionScreenPreview() {
    CamConTheme {
        // 프리뷰용 더미 구현
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("카메라 연결 화면 프리뷰", style = MaterialTheme.typography.h6)

            // 탭 영역 표시
            TabRow(
                selectedTabIndex = 0,
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            ) {
                Tab(
                    selected = true,
                    onClick = { },
                    text = { Text("AP 모드") }
                )
                Tab(
                    selected = false,
                    onClick = { },
                    text = { Text("STA 모드") }
                )
            }

            Text(
                "탭 컨텐츠 영역",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

