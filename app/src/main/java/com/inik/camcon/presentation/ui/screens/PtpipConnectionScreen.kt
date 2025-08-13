package com.inik.camcon.presentation.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
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

    // Wi‑Fi 스캔 권한 상태 (WifiNetworkHelper 사용)
    var wifiScanPermissionStatus by remember {
        mutableStateOf(ptpipViewModel.getWifiHelper().analyzeWifiScanPermissionStatus())
    }

    var showPermissionDialog by remember { mutableStateOf(false) }

    // Wi-Fi 스캔 권한 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Log.d("PtpipConnectionScreen", "권한 요청 결과: $results")
        // 권한 상태 업데이트
        wifiScanPermissionStatus = ptpipViewModel.getWifiHelper().analyzeWifiScanPermissionStatus()

        if (!wifiScanPermissionStatus.canScan) {
            if (wifiScanPermissionStatus.missingPermissions.isNotEmpty()) {
                showPermissionDialog = true
            } else {
                // 권한은 있지만 Wi-Fi나 위치 서비스가 꺼져있음 - 스낵바로 안내
                scope.launch {
                    val message = if (!wifiScanPermissionStatus.isWifiEnabled) {
                        "Wi-Fi를 켜주세요"
                    } else if (!wifiScanPermissionStatus.isLocationEnabled) {
                        "위치 서비스를 켜주세요"
                    } else {
                        "스캔 조건을 확인해주세요"
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        } else {
            // 모든 조건 만족 - 스캔 실행
            ptpipViewModel.scanNearbyWifiNetworks()
        }
    }

    fun requestWifiScanPermissions() {
        Log.d("PtpipConnectionScreen", "Wi-Fi 스캔 권한 요청 시작")
        val wifiHelper = ptpipViewModel.getWifiHelper()
        val requiredPermissions = wifiHelper.getRequiredWifiScanPermissions()

        Log.d("PtpipConnectionScreen", "필요한 권한: $requiredPermissions")
        permissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    // Activity Result 런처 추가 (위치 설정용)
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.d("PtpipConnectionScreen", "Google Play Services 위치 설정 결과: ${result.resultCode}")
        ptpipViewModel.dismissLocationSettingsDialog()
        // 설정 후 즉시 Wi-Fi 스캔 재시도
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d("PtpipConnectionScreen", "위치 설정 성공 - Wi-Fi 스캔 재시도")
            ptpipViewModel.scanNearbyWifiNetworks()
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
    val nearbyWifiSSIDs by ptpipViewModel.nearbyWifiSSIDs.collectAsState()
    val needLocationSettings by ptpipViewModel.needLocationSettings.collectAsState()
    val needWifiSettings by ptpipViewModel.needWifiSettings.collectAsState()

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
            title = { Text("권한 필요") },
            text = {
                Text(ptpipViewModel.getWifiHelper().getPermissionRationaleMessage())
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    // 설정으로 이동
                    val intent = ptpipViewModel.getWifiHelper().createAppSettingsIntent()
                    context.startActivity(intent)
                }) {
                    Text("설정으로 이동")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 위치 설정 다이얼로그
    if (needLocationSettings) {
        AlertDialog(
            onDismissRequest = { ptpipViewModel.dismissLocationSettingsDialog() },
            title = { Text("위치 서비스 필요") },
            text = {
                Text("Wi-Fi 스캔을 위해 위치 서비스가 필요합니다.\n'허용'을 누르면 설정을 바로 변경할 수 있습니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    // Google Play Services를 통한 위치 설정 확인 및 요청
                    ptpipViewModel.checkLocationSettings()

                    // WifiNetworkHelper를 통해 위치 설정 요청
                    val wifiHelper = ptpipViewModel.getWifiHelper()
                    wifiHelper.checkLocationSettingsForScan()
                        .addOnSuccessListener {
                            // 이미 설정되어 있음
                            Log.d("PtpipConnectionScreen", "위치 설정이 이미 활성화됨")
                            ptpipViewModel.dismissLocationSettingsDialog()
                            ptpipViewModel.scanNearbyWifiNetworks()
                        }
                        .addOnFailureListener { exception: Exception ->
                            if (exception is ResolvableApiException) {
                                try {
                                    Log.d(
                                        "PtpipConnectionScreen",
                                        "Google Play Services 위치 설정 다이얼로그 표시"
                                    )
                                    locationSettingsLauncher.launch(
                                        androidx.activity.result.IntentSenderRequest.Builder(
                                            exception.resolution
                                        ).build()
                                    )
                                } catch (e: Exception) {
                                    Log.e("PtpipConnectionScreen", "위치 설정 다이얼로그 표시 실패", e)
                                    // 폴백: 시스템 설정으로 이동
                                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    context.startActivity(intent)
                                    ptpipViewModel.dismissLocationSettingsDialog()
                                }
                            } else {
                                Log.w("PtpipConnectionScreen", "위치 설정 확인 실패: ${exception.message}")
                                // 폴백: 시스템 설정으로 이동
                                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                                ptpipViewModel.dismissLocationSettingsDialog()
                            }
                        }
                }) {
                    Text("허용")
                }
            },
            dismissButton = {
                TextButton(onClick = { ptpipViewModel.dismissLocationSettingsDialog() }) {
                    Text("취소")
                }
            }
        )
    }

    // Wi-Fi 설정 다이얼로그 (새로 추가)
    if (needWifiSettings) {
        AlertDialog(
            onDismissRequest = { ptpipViewModel.dismissWifiSettingsDialog() },
            title = { Text("Wi-Fi 스캔 제한") },
            text = {
                Column {
                    Text("Android의 보안 정책으로 인해 앱에서 직접 Wi-Fi 스캔이 제한됩니다.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("다음 단계를 따라주세요:")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("1. 'Wi-Fi 설정 열기' 버튼을 누르세요")
                    Text("2. 시스템 Wi-Fi 설정에서 주변 네트워크 목록을 확인하세요")
                    Text("3. 카메라 Wi-Fi(CANON, NIKON 등)를 찾으세요")
                    Text("4. 설정을 닫고 다시 스캔해보세요")
                }
            },
            confirmButton = {
                TextButton(onClick = {
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
                    ptpipViewModel.dismissWifiSettingsDialog()
                }) {
                    Text("Wi-Fi 설정 열기")
                }
            },
            dismissButton = {
                TextButton(onClick = { ptpipViewModel.dismissWifiSettingsDialog() }) {
                    Text("닫기")
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
                        onClick = {
                            when (pagerState.currentPage) {
                                0 -> if (ptpipViewModel.getWifiHelper()
                                        .analyzeWifiScanPermissionStatus().canScan
                                ) {
                                    Log.d("PtpipConnectionScreen", "Wi-Fi 스캔 실행")
                                    ptpipViewModel.scanNearbyWifiNetworks()
                                } else {
                                    Log.d("PtpipConnectionScreen", "권한 부족으로 권한 요청 호출")
                                    requestWifiScanPermissions()
                                }
                                1 -> ptpipViewModel.discoverCamerasSta()
                                else -> {}
                            }
                        },
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
                        hasLocationPermission = ptpipViewModel.getWifiHelper()
                            .analyzeWifiScanPermissionStatus().canScan,
                        onRequestPermission = { requestWifiScanPermissions() },
                        nearbyWifiSSIDs = nearbyWifiSSIDs,
                        onConnectToWifi = { ssid -> ptpipViewModel.connectToWifiSsid(ssid) }
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
                        hasLocationPermission = ptpipViewModel.getWifiHelper()
                            .analyzeWifiScanPermissionStatus().canScan,
                        onRequestPermission = { requestWifiScanPermissions() }
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
