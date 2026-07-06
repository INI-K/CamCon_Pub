package com.inik.camcon.presentation.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.ApModeContent
import com.inik.camcon.presentation.ui.screens.components.HotspotStaModeContent
import com.inik.camcon.presentation.ui.screens.components.StaModeContent
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.CameraSpec
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.IconButtonV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.ui.components.v2.SkeletonLoader
import com.inik.camcon.presentation.ui.components.v2.StatusIndicator
import com.inik.camcon.presentation.ui.components.v2.StatusKind
import com.inik.camcon.utils.LogMask
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
    // [임시] Wi-Fi 핫스팟 STA(폰 핫스팟에 카메라 합류) 집중 개발용 플래그.
    // true 면 AP/STA(공유기) 탭을 숨기고 핫스팟 STA 모드만 노출한다.
    // 개발 완료 후 false 로 되돌리면 기존 탭 구성(AP/STA/핫스팟)이 복원된다.
    val staOnly = true
    // 탭 제목과 탭 수를 동적으로 구성
    val tabTitles = if (staOnly) listOf(
        stringResource(R.string.ptpip_sta_hotspot_mode),
    ) else if (isAdmin) listOf(
        stringResource(R.string.ptpip_ap_mode),
        stringResource(R.string.ptpip_sta_mode),
        stringResource(R.string.ptpip_sta_hotspot_mode),
    ) else listOf(
        stringResource(R.string.ptpip_ap_mode),
        stringResource(R.string.ptpip_sta_hotspot_mode),
    )
    val pagerState = rememberPagerState(initialPage = 0) { tabTitles.size }

    // Wi‑Fi 스캔 권한 상태 (ViewModel 위임)
    var wifiScanPermissionStatus by remember {
        mutableStateOf(ptpipViewModel.analyzeWifiScanPermissionStatus())
    }

    var showPermissionDialog by remember { mutableStateOf(false) }

    // Wi-Fi 스캔 권한 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Log.d("PtpipConnectionScreen", "권한 요청 결과: $results")
        // 권한 상태 업데이트
        wifiScanPermissionStatus = ptpipViewModel.analyzeWifiScanPermissionStatus()

        if (!wifiScanPermissionStatus.canScan) {
            if (wifiScanPermissionStatus.missingPermissions.isNotEmpty()) {
                showPermissionDialog = true
            } else {
                // 권한은 있지만 Wi-Fi나 위치 서비스가 꺼져있음 - 스낵바로 안내
                val enableWifiMessage = context.getString(R.string.ptpip_please_enable_wifi)
                val enableLocationMessage = context.getString(R.string.ptpip_please_enable_location)
                val checkScanMessage = context.getString(R.string.ptpip_check_scan_conditions)
                scope.launch {
                    val message = if (!wifiScanPermissionStatus.isWifiEnabled) {
                        enableWifiMessage
                    } else if (!wifiScanPermissionStatus.isLocationEnabled) {
                        enableLocationMessage
                    } else {
                        checkScanMessage
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
        val requiredPermissions = ptpipViewModel.getRequiredWifiScanPermissions()

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
    // DataStore 기본값(true)과 일치시켜 첫 방출 전 OFF 깜빡임을 막는다.
    val isAutoReconnectEnabled by ptpipViewModel.isAutoReconnectEnabled.collectAsStateWithLifecycle(initialValue = true)
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

    // 신규 연결(CONNECTING→CONNECTED) 시에만 자동으로 카메라 컨트롤 화면으로 이동한다.
    // 이미 연결된 상태로 이 화면에 재진입하면(예: ADMIN 전송목록 테스트 버튼 사용) 자동 이동을 막아
    // 화면이 즉시 닫혀버리지 않도록 한다.
    var sawConnectingThisSession by remember { mutableStateOf(false) }

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

    // 📡 물리 셔터 무선 수신 모드 상태 + 안내(Toast)
    val isShutterListening by ptpipViewModel.isShutterListening.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        // 다른 화면(CameraControl/PhotoPreview)과 동일하게 lifecycle-aware 수집:
        // 비포그라운드에서 셔터 Toast가 뜨지 않도록 STARTED 구간에서만 collect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            ptpipViewModel.shutterListenMessage.collect { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // 권한 다이얼로그
    if (showPermissionDialog) {
        AppDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.ptpip_permission_needed)) },
            text = {
                Text(ptpipViewModel.getPermissionRationaleMessage())
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(R.string.ptpip_go_to_settings),
                    onClick = {
                        showPermissionDialog = false
                        // 설정으로 이동 (Intent 생성은 Screen에서 Context로 직접 처리)
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showPermissionDialog = false }
                )
            }
        )
    }

    // 위치 설정 다이얼로그
    if (needLocationSettings) {
        AppDialog(
            onDismissRequest = { ptpipViewModel.dismissLocationSettingsDialog() },
            title = { Text(stringResource(R.string.ptpip_location_service_needed)) },
            text = {
                Text(stringResource(R.string.ptpip_location_service_message))
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(R.string.ptpip_allow),
                    onClick = {
                        // Google Play Services를 통한 위치 설정 확인 및 요청
                        ptpipViewModel.checkLocationSettings()

                        // 위치 설정 확인은 Screen에서 LocationServices로 직접 처리
                        val locationSettingsRequest = LocationSettingsRequest.Builder()
                            .addLocationRequest(
                                LocationRequest.create().apply {
                                    interval = 0L
                                    fastestInterval = 0L
                                    priority = LocationRequest.PRIORITY_LOW_POWER
                                }
                            )
                            .build()
                        LocationServices.getSettingsClient(context)
                            .checkLocationSettings(locationSettingsRequest)
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
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = { ptpipViewModel.dismissLocationSettingsDialog() }
                )
            }
        )
    }

    // Wi-Fi 설정 다이얼로그 (새로 추가)
    if (needWifiSettings) {
        AppDialog(
            onDismissRequest = { ptpipViewModel.dismissWifiSettingsDialog() },
            title = { Text(stringResource(R.string.ptpip_wifi_scan_restriction)) },
            text = {
                Column {
                    Text(stringResource(R.string.ptpip_wifi_scan_restriction_message))
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(stringResource(R.string.ptpip_wifi_scan_steps))
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(stringResource(R.string.ptpip_wifi_scan_step1))
                    Text(stringResource(R.string.ptpip_wifi_scan_step2))
                    Text(stringResource(R.string.ptpip_wifi_scan_step3))
                    Text(stringResource(R.string.ptpip_wifi_scan_step4))
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(R.string.ptpip_open_wifi_settings),
                    onClick = {
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
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = stringResource(R.string.close),
                    onClick = { ptpipViewModel.dismissWifiSettingsDialog() }
                )
            }
        )
    }

    // Wi-Fi 패스워드 입력 다이얼로그 표시
    if (showPasswordDialog && currentWifiSsid != null) {
        AppDialog(
            onDismissRequest = {
                showPasswordDialog = false
                passwordForSsid = ""
                currentWifiSsid = null
            },
            title = {
                Text(
                    text = "${currentWifiSsid ?: ""}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.ptpip_enter_wifi_password),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = Spacing.md)
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
                                        modifier = Modifier.size(IconSize.md)
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.VisibilityOff,
                                        contentDescription = stringResource(R.string.ptpip_show_password),
                                        modifier = Modifier.size(IconSize.md)
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
                PrimaryButton(
                    text = stringResource(R.string.ptpip_connect),
                    onClick = {
                        // 1. 즉시 로딩 다이얼로그 표시
                        showConnectionProgressDialog = true

                        // 2. 다이얼로그 닫기
                        showPasswordDialog = false

                        // 3. 실제 Wi-Fi 연결 시작
                        currentWifiSsid?.let { ssid ->
                            Log.d(
                                "PtpipConnectionScreen",
                                "Wi-Fi 연결 시작: ${LogMask.ssid(ssid)}"
                            )
                            ptpipViewModel.connectToWifiSsidWithPassword(ssid, passwordForSsid)
                        }

                        // 4. 상태 초기화
                        passwordForSsid = ""
                        currentWifiSsid = null
                    },
                    enabled = passwordForSsid.isNotEmpty()
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        showPasswordDialog = false
                        passwordForSsid = ""
                        currentWifiSsid = null
                    }
                )
            }
        )
    }

    // 연결 완료 메시지는 locale별로 다르므로 하드코딩 대신 현 locale의 문자열 리소스로 비교한다.
    val connectCompleteMessage = stringResource(R.string.progress_ptpip_complete)
    val connectCompleteLimitedMessage = stringResource(R.string.progress_ptpip_complete_limited)

    // 연결 진행 상황 업데이트 (isConnecting, connectionState, connectionProgressMessage로 제어)
    LaunchedEffect(isConnecting, connectionState, connectionProgressMessage) {
        // isConnecting이 false면 무조건 다이얼로그 닫기 (Wi-Fi 연결 실패 포함)
        if (!isConnecting) {
            if (connectionState != com.inik.camcon.domain.model.PtpipConnectionState.CONNECTED) {
                showConnectionProgressDialog = false
                return@LaunchedEffect
            }
        }

        when (connectionState) {
            com.inik.camcon.domain.model.PtpipConnectionState.CONNECTING -> {
                sawConnectingThisSession = true
                showConnectionProgressDialog = true
            }
            com.inik.camcon.domain.model.PtpipConnectionState.CONNECTED -> {
                // 연결 완료 메시지(완전/제한)일 때만 다이얼로그 닫기 — locale별 문자열 리소스로 비교
                if (connectionProgressMessage == connectCompleteMessage ||
                    connectionProgressMessage == connectCompleteLimitedMessage
                ) {
                    kotlinx.coroutines.delay(500) // 연결 완료 메시지 확인용 최소 대기
                    showConnectionProgressDialog = false

                    if (sawConnectingThisSession) {
                        // 신규 연결 완료 → 카메라 컨트롤 화면으로 이동
                        kotlinx.coroutines.delay(300)
                        // 핸드오프 표시 — Activity finish 시 onCleared가 연결을 끊지 않도록 한다.
                        ptpipViewModel.markConnectionHandoff()
                        onBackClick()
                    }
                    // else: 이미 연결된 상태로 재진입(예: ADMIN 전송목록 테스트 버튼) — 자동 이동 생략, 화면 유지
                }
            }
            com.inik.camcon.domain.model.PtpipConnectionState.DISCONNECTED,
            com.inik.camcon.domain.model.PtpipConnectionState.ERROR -> {
                // Wi-Fi 연결 직후 짧은 DISCONNECTED 상태 방지 - isConnecting 체크
                if (!isConnecting) {
                    kotlinx.coroutines.delay(500) // 짧은 대기로 순간적인 상태 변화 방지
                    // 다시 한번 체크
                    if (!isConnecting && connectionState != com.inik.camcon.domain.model.PtpipConnectionState.CONNECTING) {
                        showConnectionProgressDialog = false
                    }
                }
            }
        }
    }


    // Wi-Fi 연결 끊어짐 알림 표시
    connectionLostMessage?.let { message ->
        AppDialog(
            onDismissRequest = { ptpipViewModel.clearConnectionLostMessage() },
            title = { Text(stringResource(R.string.ptpip_camera_disconnected)) },
            text = {
                Column {
                    Text(message)
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        stringResource(R.string.ptpip_next_steps),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(stringResource(R.string.ptpip_check_wifi))
                    Text(stringResource(R.string.ptpip_check_camera_wifi))
                    Text(stringResource(R.string.ptpip_reconnect_camera))
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(R.string.ptpip_search_camera),
                    onClick = {
                        ptpipViewModel.clearConnectionLostMessage()
                        // 자동으로 카메라 검색 시작 (admin: 0=AP, 1=STA, 2=Hotspot / non-admin: 0=AP, 1=Hotspot)
                        val page = pagerState.currentPage
                        when {
                            staOnly -> ptpipViewModel.discoverCamerasHotspot()
                            page == 0 -> ptpipViewModel.discoverCamerasAp()
                            isAdmin && page == 1 -> ptpipViewModel.discoverCamerasSta()
                            else -> ptpipViewModel.discoverCamerasHotspot()
                        }
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = stringResource(R.string.ok),
                    onClick = { ptpipViewModel.clearConnectionLostMessage() }
                )
            }
        )
    }

    // 테마 모드 가져오기
    val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()

    CamConTheme() {
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
                        // 📡 물리 셔터 무선 수신 토글 (니콘 STA vendor 풀해상도)
                        if (isAdmin) {
                            IconButtonV2(
                                icon = if (isShutterListening) Icons.Filled.CloudDone else Icons.Filled.CloudDownload,
                                contentDescription = if (isShutterListening)
                                    stringResource(R.string.ptpip_shutter_listen_stop)
                                else
                                    stringResource(R.string.ptpip_shutter_listen_start),
                                tint = if (isShutterListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    val target = selectedCamera ?: discoveredCameras.firstOrNull()
                                    if (target == null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.ptpip_no_camera_search_first),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        ptpipViewModel.toggleShutterListening(target)
                                    }
                                }
                            )
                        }
                        IconButtonV2(
                            icon = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                            tint = MaterialTheme.colorScheme.primary,
                            enabled = !isDiscovering,
                            onClick = {
                                val page = pagerState.currentPage
                                when {
                                    staOnly -> ptpipViewModel.discoverCamerasHotspot()
                                    page == 0 -> if (ptpipViewModel
                                            .analyzeWifiScanPermissionStatus().canScan
                                    ) {
                                        Log.d("PtpipConnectionScreen", "Wi-Fi 스캔 실행")
                                        ptpipViewModel.scanNearbyWifiNetworks()
                                    } else {
                                        Log.d("PtpipConnectionScreen", "권한 부족으로 권한 요청 호출")
                                        requestWifiScanPermissions()
                                    }

                                    isAdmin && page == 1 -> ptpipViewModel.discoverCamerasSta()
                                    else -> ptpipViewModel.discoverCamerasHotspot()
                                }
                            }
                        )
                        IconButtonV2(
                            icon = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.cd_wifi_settings),
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = {
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
                        )
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
            // 태블릿/Expanded 너비에서 컨텐츠가 너무 늘어지지 않도록 600.dp로 캡 + 중앙 정렬.
            // Compact 폭에서는 widthIn(max=600.dp)이 영향을 주지 않으므로 동작 보존.
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopCenter
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
            ) {
                // V2 StatusBar — TopAppBar 아래 PTP 연결 상태 표시 (32dp)
                val ptpStatusKind = when (connectionState) {
                    com.inik.camcon.domain.model.PtpipConnectionState.CONNECTED -> StatusKind.Connected
                    com.inik.camcon.domain.model.PtpipConnectionState.CONNECTING -> StatusKind.Connecting
                    com.inik.camcon.domain.model.PtpipConnectionState.ERROR -> StatusKind.Error
                    com.inik.camcon.domain.model.PtpipConnectionState.DISCONNECTED -> if (isConnecting || isDiscovering) StatusKind.Connecting else StatusKind.Idle
                }
                val ptpStatusLabel = when (ptpStatusKind) {
                    StatusKind.Connected -> selectedCamera?.name ?: stringResource(R.string.ptpip_camera_connection)
                    StatusKind.Connecting -> connectionProgressMessage.ifEmpty { stringResource(R.string.ptpip_connecting_to_camera) }
                    StatusKind.Error -> errorMessage ?: stringResource(R.string.ptpip_camera_disconnected)
                    // ptpStatusKind 는 PtpipConnectionState 매핑이라 Searching 은 생산되지 않지만 when 완전성을 위해 Idle 과 동일 처리.
                    StatusKind.Searching, StatusKind.Idle -> stringResource(R.string.ptpip_camera_connection)
                }
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CameraSpec.statusBarHeight)
                        .padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        StatusIndicator(kind = ptpStatusKind, label = ptpStatusLabel)
                    }

                    // 자동 재연결 토글 — TopAppBar 아래 항상 노출.
                    // 인프로세스 재연결(AUTO_RECONNECT) 전용. 알림 권한 불필요.
                    // 백그라운드 자동연결(AUTO_CONNECT)은 설정 화면이 단일 관리처.
                    Text(
                        text = stringResource(R.string.connect_auto_reconnect_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = Spacing.sm)
                    )
                    Switch(
                        checked = isAutoReconnectEnabled,
                        onCheckedChange = { enabled ->
                            ptpipViewModel.setAutoReconnectEnabled(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // 탭 행
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Surface2,
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
                    // 탭 인덱스 → 컨텐츠 매핑:
                    //   admin     : 0=AP, 1=STA_ROUTER, 2=STA_PHONE_HOTSPOT
                    //   non-admin : 0=AP, 1=STA_PHONE_HOTSPOT
                    val hotspotTabIndex = if (isAdmin) 2 else 1
                    when {
                        staOnly -> HotspotStaModeContent(
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
                            hasLocationPermission = ptpipViewModel
                                .analyzeWifiScanPermissionStatus().canScan,
                            onRequestPermission = { requestWifiScanPermissions() },
                        )

                        page == 0 -> ApModeContent(
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
                            hasLocationPermission = ptpipViewModel
                                .analyzeWifiScanPermissionStatus().canScan,
                            onRequestPermission = { requestWifiScanPermissions() },
                            nearbyWifiSSIDs = nearbyWifiSSIDs,
                            onConnectToWifi = { ssid -> onConnectToWifiWithPassword(ssid) },
                            savedWifiSsids = savedWifiSsids
                        )

                        isAdmin && page == 1 -> StaModeContent(
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
                            hasLocationPermission = ptpipViewModel
                                .analyzeWifiScanPermissionStatus().canScan,
                            onRequestPermission = { requestWifiScanPermissions() },
                            nearbyWifiSSIDs = nearbyWifiSSIDs,
                            onConnectToWifi = { ssid -> onConnectToWifiWithPassword(ssid) }
                        )

                        page == hotspotTabIndex -> HotspotStaModeContent(
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
                            hasLocationPermission = ptpipViewModel
                                .analyzeWifiScanPermissionStatus().canScan,
                            onRequestPermission = { requestWifiScanPermissions() },
                        )
                    }
                }
            }
            } // end widthIn Box
        }
    }

    // PTPIP 연결 진행 상황 다이얼로그 (v2 AppDialog — 연결 중에는 닫을 수 없음)
    if (showConnectionProgressDialog) {
        AppDialog(
            onDismissRequest = { /* 연결 중에는 닫을 수 없음 */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = {
                Text(
                    text = connectionProgressMessage.ifEmpty { stringResource(R.string.ptpip_connecting_to_camera) },
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // V8: 스피너 대신 v2 SkeletonLoader shimmer 로딩 표시
                    SkeletonLoader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(StrokeWidth.thick)
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = stringResource(R.string.ptpip_please_wait),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                SecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        ptpipViewModel.disconnect()
                        showConnectionProgressDialog = false
                    }
                )
            }
        )
    }
}

@Preview(name = "PTPIP Connection Screen", showBackground = true)
@Composable
private fun PtpipConnectionScreenPreview() {
    CamConTheme() {
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
                containerColor = Surface2,
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
