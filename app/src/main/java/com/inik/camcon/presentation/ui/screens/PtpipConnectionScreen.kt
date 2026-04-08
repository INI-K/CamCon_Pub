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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.ApModeContent
import com.inik.camcon.presentation.ui.screens.components.StaModeContent
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.SurfaceElevated
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
    val isAdmin by appSettingsViewModel.isAdminTier.collectAsStateWithLifecycle()
    // 탭 제목과 탭 수를 동적으로 구성
    val tabTitles = if (isAdmin) listOf(stringResource(R.string.ptpip_ap_mode), stringResource(R.string.ptpip_sta_mode)) else listOf(stringResource(R.string.ptpip_ap_mode))
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
    val connectionState by ptpipViewModel.connectionState.collectAsStateWithLifecycle()
    val discoveredCameras by ptpipViewModel.discoveredCameras.collectAsStateWithLifecycle()
    val isDiscovering by ptpipViewModel.isDiscovering.collectAsStateWithLifecycle()
    val isConnecting by ptpipViewModel.isConnecting.collectAsStateWithLifecycle()
    val errorMessage by ptpipViewModel.errorMessage.collectAsStateWithLifecycle()
    val selectedCamera by ptpipViewModel.selectedCamera.collectAsStateWithLifecycle()
    val cameraInfo by ptpipViewModel.cameraInfo.collectAsStateWithLifecycle()
    val isPtpipEnabled by ptpipViewModel.isPtpipEnabled.collectAsStateWithLifecycle(initialValue = false)
    val wifiNetworkState by ptpipViewModel.wifiNetworkState.collectAsStateWithLifecycle()
    val isAutoReconnectEnabled by ptpipViewModel.isAutoReconnectEnabled.collectAsStateWithLifecycle(initialValue = false)
    val isWifiConnected = ptpipViewModel.isWifiConnected()
    val wifiCapabilities = ptpipViewModel.getWifiCapabilities()
    val nearbyWifiSSIDs by ptpipViewModel.nearbyWifiSSIDs.collectAsStateWithLifecycle()
    val needLocationSettings by ptpipViewModel.needLocationSettings.collectAsStateWithLifecycle()
    val needWifiSettings by ptpipViewModel.needWifiSettings.collectAsStateWithLifecycle()
    val connectionLostMessage by ptpipViewModel.connectionLostMessage.collectAsStateWithLifecycle()
    val savedWifiSsids by ptpipViewModel.savedWifiSsids.collectAsStateWithLifecycle()

    // PTPIP 연결 진행 상황을 위한 상태
    var showConnectionProgressDialog by remember { mutableStateOf(false) }
    val connectionProgressMessage by ptpipViewModel.connectionProgressMessage.collectAsStateWithLifecycle()

    // === 추가: Wi-Fi 패스워드 입력 다이얼로그 & 상태 ===
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordForSsid by remember { mutableStateOf("") }
    var currentWifiSsid by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    // 패스워드 입력 콜백 (저장된 비밀번호가 있으면 바로 연결)
    val onConnectToWifiWithPassword: (ssid: String) -> Unit = { ssid ->
        if (savedWifiSsids.contains(ssid)) {
            // 저장된 비밀번호로 바로 연결
            showConnectionProgressDialog = true
            ptpipViewModel.connectToWifiSsidWithSavedCredential(ssid)
        } else {
            currentWifiSsid = ssid
            showPasswordDialog = true
            passwordForSsid = ""
            passwordVisible = false
        }
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
            title = { Text(stringResource(R.string.ptpip_permission_needed)) },
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
                    Text(stringResource(R.string.ptpip_go_to_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 위치 설정 다이얼로그
    if (needLocationSettings) {
        AlertDialog(
            onDismissRequest = { ptpipViewModel.dismissLocationSettingsDialog() },
            title = { Text(stringResource(R.string.ptpip_location_service_needed)) },
            text = {
                Text(stringResource(R.string.ptpip_location_service_message))
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
                    Text(stringResource(R.string.ptpip_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { ptpipViewModel.dismissLocationSettingsDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Wi-Fi 설정 다이얼로그 (새로 추가)
    if (needWifiSettings) {
        AlertDialog(
            onDismissRequest = { ptpipViewModel.dismissWifiSettingsDialog() },
            title = { Text(stringResource(R.string.ptpip_wifi_scan_restriction)) },
            text = {
                Column {
                    Text(stringResource(R.string.ptpip_wifi_scan_restriction_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.ptpip_wifi_scan_steps))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.ptpip_wifi_scan_step1))
                    Text(stringResource(R.string.ptpip_wifi_scan_step2))
                    Text(stringResource(R.string.ptpip_wifi_scan_step3))
                    Text(stringResource(R.string.ptpip_wifi_scan_step4))
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
                    Text(stringResource(R.string.ptpip_open_wifi_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { ptpipViewModel.dismissWifiSettingsDialog() }) {
                    Text(stringResource(R.string.close))
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
                        text = stringResource(R.string.ptpip_enter_wifi_password),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = passwordForSsid,
                        onValueChange = { passwordForSsid = it },
                        label = { Text(stringResource(R.string.ptpip_password), style = MaterialTheme.typography.bodySmall) },
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
                                        contentDescription = stringResource(R.string.ptpip_hide_password),
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.VisibilityOff,
                                        contentDescription = stringResource(R.string.ptpip_show_password),
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
                    Text(stringResource(R.string.ptpip_connect), style = MaterialTheme.typography.labelLarge)
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
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
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
                    Log.d("PtpipConnectionScreen", "   🎉 '연결 완료!' 메시지 확인 - 다이얼로그 닫기")
                    kotlinx.coroutines.delay(500) // 연결 완료 메시지 확인용 최소 대기
                    showConnectionProgressDialog = false
                    Log.d("PtpipConnectionScreen", "   ✅ 다이얼로그 닫힘")

                    // 카메라 컨트롤 화면으로 이동
                    Log.d("PtpipConnectionScreen", "   🚀 카메라 컨트롤 화면으로 이동")
                    kotlinx.coroutines.delay(300)
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
            title = { Text(stringResource(R.string.ptpip_camera_disconnected)) },
            text = {
                Column {
                    Text(message)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ptpip_next_steps),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.ptpip_check_wifi))
                    Text(stringResource(R.string.ptpip_check_camera_wifi))
                    Text(stringResource(R.string.ptpip_reconnect_camera))
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
                    Text(stringResource(R.string.ptpip_search_camera))
                }
            },
            dismissButton = {
                TextButton(onClick = { ptpipViewModel.clearConnectionLostMessage() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // 테마 모드 가져오기
    val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()

    CamConTheme(themeMode = themeMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text(stringResource(R.string.ptpip_camera_connection)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh))
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
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_wifi_settings))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.primary
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
                    containerColor = SurfaceElevated,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    title,
                                    color = if (pagerState.currentPage == index)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
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
                            onConnectToWifi = { ssid -> onConnectToWifiWithPassword(ssid) },
                            savedWifiSsids = savedWifiSsids
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
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SurfaceElevated
                ),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = connectionProgressMessage.ifEmpty { stringResource(R.string.ptpip_connecting_to_camera) },
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.ptpip_please_wait),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    TextButton(
                        onClick = {
                            ptpipViewModel.disconnect()
                            showConnectionProgressDialog = false
                        }
                    ) {
                        Text(
                            stringResource(R.string.cancel),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
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
                containerColor = SurfaceElevated,
                contentColor = MaterialTheme.colorScheme.onSurface
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
