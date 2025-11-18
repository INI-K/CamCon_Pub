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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
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
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.data.datasource.local.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PTP/IP 연결 화면
 * 
 * AP 모드와 STA 모드를 탭으로 구분하여 표시
 */
@Composable
fun PtpipConnectionScreen(
    onBackClick: () -> Unit,
    ptpipViewModel: PtpipViewModel = hiltViewModel(),
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 구독 티어 체크 (STA 모드는 ADMIN만 보임)
    val isAdmin by appSettingsViewModel.isAdminTier.collectAsState()
    // 탭 제목과 탭 수를 동적으로 구성
    val tabTitles = if (isAdmin) listOf("AP 모드", "STA 모드") else listOf("AP 모드")
    val pagerState = rememberPagerState(initialPage = 0) { tabTitles.size }

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
    val connectionLostMessage by ptpipViewModel.connectionLostMessage.collectAsState()

    // PTPIP 연결 진행 상황을 위한 상태
    var showConnectionProgressDialog by remember { mutableStateOf(false) }
    val connectionProgressMessage by ptpipViewModel.connectionProgressMessage.collectAsState()

    // === 추가: Wi-Fi 패스워드 입력 다이얼로그 & 상태 ===
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordForSsid by remember { mutableStateOf("") }
    var currentWifiSsid by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    // 패스워드 입력 콜백
    val onConnectToWifiWithPassword: (ssid: String) -> Unit = { ssid ->
        currentWifiSsid = ssid
        showPasswordDialog = true
        passwordForSsid = ""
        passwordVisible = false
    }

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

    // Wi-Fi 패스워드 입력 다이얼로그 표시
    if (showPasswordDialog && currentWifiSsid != null) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                passwordForSsid = ""
                currentWifiSsid = null
            },
            title = {
                Text(
                    text = "${currentWifiSsid ?: ""}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Wi-Fi 비밀번호를 입력하세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = passwordForSsid,
                        onValueChange = { passwordForSsid = it },
                        label = { Text("비밀번호", style = MaterialTheme.typography.bodySmall) },
                        visualTransformation =
                            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.size(40.dp)
                            ) {
                                if (passwordVisible) {
                                    Icon(
                                        Icons.Filled.Visibility,
                                        contentDescription = "숨기기",
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.VisibilityOff,
                                        contentDescription = "보이기",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Log.d("PtpipConnectionScreen", "🚀 Wi-Fi 연결 버튼 클릭 - 즉시 로딩 다이얼로그 표시")

                        // 1. 즉시 로딩 다이얼로그 표시
                        showConnectionProgressDialog = true
                        Log.d(
                            "PtpipConnectionScreen",
                            "   ✅ showConnectionProgressDialog = true 설정 완료"
                        )

                        // 2. 다이얼로그 닫기
                        showPasswordDialog = false
                        Log.d("PtpipConnectionScreen", "   ✅ 비밀번호 다이얼로그 닫힘")

                        // 3. 실제 Wi-Fi 연결 시작
                        currentWifiSsid?.let { ssid ->
                            Log.d(
                                "PtpipConnectionScreen",
                                "   🌐 ViewModel.connectToWifiSsidWithPassword 호출: $ssid"
                            )
                            ptpipViewModel.connectToWifiSsidWithPassword(ssid, passwordForSsid)
                        }

                        // 4. 상태 초기화
                        passwordForSsid = ""
                        currentWifiSsid = null
                        Log.d("PtpipConnectionScreen", "   ✅ 상태 초기화 완료")
                    },
                    enabled = passwordForSsid.isNotEmpty(),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("연결", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        passwordForSsid = ""
                        currentWifiSsid = null
                    },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("취소", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    // 연결 진행 상황 업데이트 (isConnecting, connectionState, connectionProgressMessage로 제어)
    LaunchedEffect(isConnecting, connectionState, connectionProgressMessage) {
        Log.d("PtpipConnectionScreen", "🔍 다이얼로그 상태 체크:")
        Log.d("PtpipConnectionScreen", "   - isConnecting: $isConnecting")
        Log.d("PtpipConnectionScreen", "   - connectionState: $connectionState")
        Log.d("PtpipConnectionScreen", "   - connectionProgressMessage: $connectionProgressMessage")
        Log.d("PtpipConnectionScreen", "   - 현재 다이얼로그 상태: $showConnectionProgressDialog")

        // isConnecting이 false면 무조건 다이얼로그 닫기 (Wi-Fi 연결 실패 포함)
        if (!isConnecting) {
            if (connectionState != com.inik.camcon.domain.model.PtpipConnectionState.CONNECTED) {
                Log.d(
                    "PtpipConnectionScreen",
                    "   ❌ isConnecting=false & NOT CONNECTED - 다이얼로그 즉시 닫기"
                )
                showConnectionProgressDialog = false
                return@LaunchedEffect
            }
        }

        when (connectionState) {
            com.inik.camcon.domain.model.PtpipConnectionState.CONNECTING -> {
                Log.d("PtpipConnectionScreen", "   ✅ CONNECTING 상태 - 다이얼로그 열기")
                showConnectionProgressDialog = true
            }
            com.inik.camcon.domain.model.PtpipConnectionState.CONNECTED -> {
                Log.d("PtpipConnectionScreen", "   ⏸️ CONNECTED 상태 - 메시지 확인 중")
                // "연결 완료!" 메시지일 때만 다이얼로그 닫기
                if (connectionProgressMessage == "연결 완료!") {
                    Log.d("PtpipConnectionScreen", "   🎉 '연결 완료!' 메시지 확인 - 2초 후 다이얼로그 닫기")
                    kotlinx.coroutines.delay(2000) // 2초 대기
                    showConnectionProgressDialog = false
                    Log.d("PtpipConnectionScreen", "   ✅ 다이얼로그 닫힘")

                    // 1초 더 대기 후 카메라 컨트롤 화면으로 이동
                    Log.d("PtpipConnectionScreen", "   🚀 1초 후 카메라 컨트롤 화면으로 이동")
                    kotlinx.coroutines.delay(1000)
                    Log.d("PtpipConnectionScreen", "   ✅ 카메라 컨트롤 화면으로 이동")
                    onBackClick()
                } else {
                    Log.d("PtpipConnectionScreen", "   ⏳ 아직 '연결 완료!' 아님 - 다이얼로그 유지")
                }
            }
            com.inik.camcon.domain.model.PtpipConnectionState.DISCONNECTED,
            com.inik.camcon.domain.model.PtpipConnectionState.ERROR -> {
                // Wi-Fi 연결 직후 짧은 DISCONNECTED 상태 방지 - isConnecting 체크
                if (isConnecting) {
                    Log.d(
                        "PtpipConnectionScreen",
                        "   ⏳ DISCONNECTED/ERROR 상태지만 isConnecting=true - 다이얼로그 유지 (Wi-Fi 연결 중)"
                    )
                    // 다이얼로그 유지 - 아무것도 하지 않음
                } else {
                    Log.d(
                        "PtpipConnectionScreen",
                        "   ❌ DISCONNECTED/ERROR 상태 & isConnecting=false - 0.5초 후 다이얼로그 닫기"
                    )
                    kotlinx.coroutines.delay(500) // 짧은 대기로 순간적인 상태 변화 방지
                    // 다시 한번 체크
                    if (!isConnecting && connectionState != com.inik.camcon.domain.model.PtpipConnectionState.CONNECTING) {
                        Log.d("PtpipConnectionScreen", "   ✅ 진짜 DISCONNECTED/ERROR 확인 - 다이얼로그 닫기")
                        showConnectionProgressDialog = false
                    } else {
                        Log.d("PtpipConnectionScreen", "   ⏳ 상태가 변경됨 - 다이얼로그 유지")
                    }
                }
            }
        }
    }


    // Wi-Fi 연결 끊어짐 알림 표시
    connectionLostMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { ptpipViewModel.clearConnectionLostMessage() },
            title = { Text("카메라 연결 끊어짐") },
            text = {
                Column {
                    Text(message)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "다음 단계를 수행해주세요:",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("1. Wi-Fi 연결 상태를 확인하세요")
                    Text("2. 카메라 Wi-Fi가 켜져있는지 확인하세요")
                    Text("3. 카메라를 다시 검색하고 연결하세요")
                }
            },
            confirmButton = {
                Button(onClick = {
                    ptpipViewModel.clearConnectionLostMessage()
                    // 자동으로 카메라 검색 시작
                    when (pagerState.currentPage) {
                        0 -> ptpipViewModel.discoverCamerasAp()
                        1 -> ptpipViewModel.discoverCamerasSta()
                    }
                }) {
                    Text("카메라 재검색")
                }
            },
            dismissButton = {
                TextButton(onClick = { ptpipViewModel.clearConnectionLostMessage() }) {
                    Text("확인")
                }
            }
        )
    }

    // 테마 모드 가져오기
    val themeMode by appSettingsViewModel.themeMode.collectAsState()

    CamConTheme(themeMode = themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
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

                                    1 -> if (isAdmin) {
                                        ptpipViewModel.discoverCamerasSta()
                                    }
                                    else -> {}
                                }
                            },
                            enabled = !isDiscovering && (pagerState.currentPage == 0 || isAdmin)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                        }
                        IconButton(onClick = {
                            if (pagerState.currentPage == 0 || isAdmin) {
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
                            }
                        },
                            enabled = pagerState.currentPage == 0 || isAdmin
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Wi-Fi 설정")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.systemBars
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 탭 행
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
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
                            onConnectToWifi = { ssid -> onConnectToWifiWithPassword(ssid) }
                        )

                        1 -> if (isAdmin) {
                            StaModeContent(
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
                                onConnectToWifi = { ssid -> onConnectToWifiWithPassword(ssid) }
                            )
                        }
                    }
                }
            }
        }
    }

    // PTPIP 연결 진행 상황 다이얼로그
    if (showConnectionProgressDialog) {
        Dialog(
            onDismissRequest = { /* 연결 중에는 닫을 수 없음 */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = connectionProgressMessage.ifEmpty { "카메라에 연결 중..." },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "잠시만 기다려주세요...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Preview(name = "PTPIP Connection Screen", showBackground = true)
@Composable
private fun PtpipConnectionScreenPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        // 프리뷰용 더미 구현
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("카메라 연결 화면 프리뷰", style = MaterialTheme.typography.titleLarge)

            // 탭 영역 표시
            TabRow(
                selectedTabIndex = 0,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
