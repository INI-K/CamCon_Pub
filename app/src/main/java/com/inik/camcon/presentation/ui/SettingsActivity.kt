package com.inik.camcon.presentation.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.inik.camcon.BuildConfig
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.domain.model.User
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.AccentMuted
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.OnAccent
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.DividerLineV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.ProgressBarV2
import com.inik.camcon.presentation.ui.components.v2.RowItem
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.viewmodel.AdminReferralCodeViewModel
import com.inik.camcon.presentation.viewmodel.AdminReferralUiEvent
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.AuthUiEvent
import com.inik.camcon.presentation.viewmodel.AuthViewModel
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()
            val authViewModel: AuthViewModel = hiltViewModel()

            CamConTheme() {
                SettingsScreen(
                    onBackClick = { finish() },
                    authViewModel = authViewModel
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "SettingsScreen Preview")
@Composable
fun SettingsScreenPreview() {
    CamConTheme() {
        SettingsScreen(
            onBackClick = {},
            authViewModel = null
        )
    }
}

@Preview(showBackground = true, name = "SettingsActivity Preview")
@Composable
fun SettingsActivityPreview() {
    SettingsScreenPreview()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    ptpipViewModel: PtpipViewModel = hiltViewModel(),
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel? = hiltViewModel(),
    adminReferralCodeViewModel: AdminReferralCodeViewModel = hiltViewModel(),
    cameraViewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val authUiState by authViewModel?.uiState?.collectAsStateWithLifecycle() ?: remember {
        mutableStateOf(com.inik.camcon.presentation.viewmodel.AuthUiState())
    }

    val adminReferralState by adminReferralCodeViewModel.uiState.collectAsStateWithLifecycle()

    val cameraUiState by cameraViewModel.uiState.collectAsStateWithLifecycle()
    val isUsbConnected = cameraUiState.isNativeCameraConnected
    val isPtpipConnected = cameraUiState.isPtpipConnected

    LaunchedEffect(Unit) {
        authViewModel?.uiEvent?.collect { event ->
            when (event) {
                is AuthUiEvent.ShowError -> {
                    android.widget.Toast.makeText(
                        context,
                        "로그아웃 실패: ${event.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                is AuthUiEvent.SignOutSuccess -> {
                    // NavigateToLogin에서 처리
                }
                is AuthUiEvent.NavigateToLogin -> {
                    val intent = Intent(context, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        adminReferralCodeViewModel.uiEvent.collect { event ->
            when (event) {
                is AdminReferralUiEvent.ShowError -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_LONG).show()
                }
                is AdminReferralUiEvent.ShowSuccess -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val isPtpipEnabled by ptpipViewModel.isPtpipEnabled.collectAsStateWithLifecycle(initialValue = false)
    val isWifiConnectionModeEnabled by ptpipViewModel.isWifiConnectionModeEnabled.collectAsStateWithLifecycle(initialValue = true)
    val isAutoDiscoveryEnabled by ptpipViewModel.isAutoDiscoveryEnabled.collectAsStateWithLifecycle(initialValue = true)
    val isAutoConnectEnabled by ptpipViewModel.isAutoConnectEnabled.collectAsStateWithLifecycle(initialValue = false)
    val lastConnectedName by ptpipViewModel.lastConnectedName.collectAsStateWithLifecycle(initialValue = null)

    val isCameraControlsEnabled by appSettingsViewModel.isCameraControlsEnabled.collectAsStateWithLifecycle()
    val isLiveViewEnabled by appSettingsViewModel.isLiveViewEnabled.collectAsStateWithLifecycle()
    val isAdminTier by appSettingsViewModel.isAdminTier.collectAsStateWithLifecycle()
    val currentThemeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()
    val isAutoStartEventListener by appSettingsViewModel.isAutoStartEventListenerEnabled.collectAsStateWithLifecycle()
    val isShowLatestPhotoWhenDisabled by appSettingsViewModel.isShowLatestPhotoWhenDisabled.collectAsStateWithLifecycle()

    val isColorTransferEnabled by appSettingsViewModel.isColorTransferEnabled.collectAsStateWithLifecycle()
    val colorTransferReferenceImagePath by appSettingsViewModel.colorTransferReferenceImagePath.collectAsStateWithLifecycle()
    val isRawFileDownloadEnabled by appSettingsViewModel.isRawFileDownloadEnabled.collectAsStateWithLifecycle()

    val subscriptionTier by appSettingsViewModel.subscriptionTier.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val message = if (isGranted) "알림 권한이 허용되었습니다." else "알림 권한이 거부되었습니다."
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }

    val referenceImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val imageDir = File(context.filesDir, "color_transfer_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }
                val fileName = "color_ref_${System.currentTimeMillis()}.jpg"
                val targetFile = File(imageDir, fileName)
                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                appSettingsViewModel.setColorTransferReferenceImagePath(targetFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    var logDialogContent by remember { mutableStateOf<String?>(null) }

    fun getThemeDisplayName(themeMode: ThemeMode): String = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> "시스템 설정 따름"
        ThemeMode.LIGHT -> "라이트 모드"
        ThemeMode.DARK -> "다크 모드"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("설정", style = HeadingL, color = TextPrimaryV2) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimaryV2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface0,
                    titleContentColor = TextPrimaryV2,
                    navigationIconContentColor = TextPrimaryV2
                )
            )
        },
        containerColor = Surface0,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface0)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 카메라 제어 설정
            if (BuildConfig.SHOW_DEVELOPER_FEATURES) {
                SettingsSection(title = "카메라 제어 설정 (개발 버전)") {
                    SwitchRowV2(
                        icon = Icons.Default.CameraAlt,
                        title = "카메라 컨트롤 표시",
                        subtitle = if (isCameraControlsEnabled) {
                            "라이브뷰 및 카메라 컨트롤 UI 표시"
                        } else {
                            "비활성화 - 최신 촬영 사진이 표시됩니다"
                        },
                        checked = isCameraControlsEnabled,
                        onCheckedChange = { appSettingsViewModel.setCameraControlsEnabled(it) }
                    )

                    if (isCameraControlsEnabled && isAdminTier) {
                        SwitchRowV2(
                            icon = Icons.Default.Visibility,
                            title = "라이브뷰 활성화",
                            subtitle = "실시간 카메라 화면 표시 (ADMIN 전용)",
                            checked = isLiveViewEnabled,
                            onCheckedChange = { appSettingsViewModel.setLiveViewEnabled(it) }
                        )
                        SwitchRowV2(
                            icon = Icons.Default.Settings,
                            title = "자동 이벤트 수신",
                            subtitle = "카메라 제어 탭 진입 시 자동으로 이벤트 리스너 시작",
                            checked = isAutoStartEventListener,
                            onCheckedChange = { appSettingsViewModel.setAutoStartEventListenerEnabled(it) }
                        )
                    } else if (isCameraControlsEnabled && !isAdminTier) {
                        ClickableRowV2(
                            icon = Icons.Default.Visibility,
                            title = "라이브뷰 기능",
                            subtitle = "ADMIN 티어에서만 사용 가능한 기능입니다",
                            onClick = { }
                        )
                        SwitchRowV2(
                            icon = Icons.Default.Settings,
                            title = "자동 이벤트 수신",
                            subtitle = "카메라 제어 탭 진입 시 자동으로 이벤트 리스너 시작",
                            checked = isAutoStartEventListener,
                            onCheckedChange = { appSettingsViewModel.setAutoStartEventListenerEnabled(it) }
                        )
                    }

                    if (!isCameraControlsEnabled) {
                        SwitchRowV2(
                            icon = Icons.Default.Photo,
                            title = "최신 사진 표시",
                            subtitle = "카메라 컨트롤 비활성화 시 최근 촬영한 사진 표시",
                            checked = isShowLatestPhotoWhenDisabled,
                            onCheckedChange = { appSettingsViewModel.setShowLatestPhotoWhenDisabled(it) }
                        )
                    }
                }

                SettingsSection(title = "Wi-Fi 카메라 연결 (PTPIP) - 개발 버전") {
                    SwitchRowV2(
                        icon = Icons.Default.Wifi,
                        title = "Wi-Fi 카메라 연결",
                        subtitle = if (isPtpipEnabled) {
                            if (lastConnectedName != null) {
                                "활성화됨 - 마지막 연결: $lastConnectedName"
                            } else {
                                "활성화됨 - 연결된 카메라 없음"
                            }
                        } else {
                            "Wi-Fi를 통한 카메라 원격 제어"
                        },
                        checked = isPtpipEnabled,
                        onCheckedChange = { ptpipViewModel.setPtpipEnabled(it) }
                    )

                    if (isPtpipEnabled) {
                        SwitchRowV2(
                            icon = Icons.Default.NetworkWifi,
                            title = "WIFI 연결 하기",
                            subtitle = "카메라와 동일한 Wi-Fi 네트워크에서 연결 (권장)",
                            checked = isWifiConnectionModeEnabled,
                            onCheckedChange = { ptpipViewModel.setWifiConnectionModeEnabled(it) }
                        )
                        SwitchRowV2(
                            icon = Icons.Default.Settings,
                            title = "자동 카메라 검색",
                            subtitle = "네트워크에서 PTPIP 카메라 자동 찾기",
                            checked = isAutoDiscoveryEnabled,
                            onCheckedChange = { ptpipViewModel.setAutoDiscoveryEnabled(it) }
                        )
                        SwitchRowV2(
                            icon = Icons.Default.CameraAlt,
                            title = "자동 연결",
                            subtitle = "마지막 연결된 카메라에 자동 연결",
                            checked = isAutoConnectEnabled,
                            onCheckedChange = { enabled ->
                                ptpipViewModel.updateAutoConnectEnabled(
                                    enabled = enabled,
                                    onResult = { _, message ->
                                        android.widget.Toast.makeText(
                                            context, message, android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onRequestNotificationPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                "현재 기기에서 알림 권한 요청이 필요하지 않습니다.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        )
                        NavigationRowV2(
                            icon = Icons.Default.Info,
                            title = "카메라 연결 관리",
                            subtitle = "${ptpipViewModel.getConnectionStatusText()} - 탭하여 자세히 보기",
                            onClick = {
                                val intent = Intent(context, PtpipConnectionActivity::class.java)
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }

            // 색감 전송 설정
            SettingsSection(title = "색감 전송 설정") {
                SwitchRowV2(
                    icon = Icons.Default.Photo,
                    title = "색감 전송 기능",
                    subtitle = if (isColorTransferEnabled) {
                        if (colorTransferReferenceImagePath != null) {
                            "활성화됨 - 참조 이미지 설정됨"
                        } else {
                            "활성화됨 - 참조 이미지 없음"
                        }
                    } else {
                        "촬영된 사진에 참조 이미지의 색감을 자동 적용"
                    },
                    checked = isColorTransferEnabled,
                    onCheckedChange = { appSettingsViewModel.setColorTransferEnabled(it) }
                )

                if (isColorTransferEnabled) {
                    NavigationRowV2(
                        icon = Icons.Default.Settings,
                        title = "상세 설정",
                        subtitle = "색감 전송 알고리즘 및 고급 옵션 설정",
                        onClick = {
                            val intent = Intent(context, ColorTransferSettingsActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // RAW 파일 다운로드 설정
            SettingsSection(title = "RAW 파일 다운로드 설정") {
                if (subscriptionTier == SubscriptionTier.PRO ||
                    subscriptionTier == SubscriptionTier.ADMIN ||
                    subscriptionTier == SubscriptionTier.REFERRER
                ) {
                    SwitchRowV2(
                        icon = Icons.Default.Photo,
                        title = "RAW 파일 다운로드",
                        subtitle = if (isRawFileDownloadEnabled) "활성화됨" else "비활성화됨",
                        checked = isRawFileDownloadEnabled,
                        onCheckedChange = { appSettingsViewModel.setRawFileDownloadEnabled(it) }
                    )
                } else {
                    ClickableRowV2(
                        icon = Icons.Default.Photo,
                        title = "RAW 파일 다운로드",
                        subtitle = "PRO, ADMIN, REFERRER 티어에서만 사용 가능한 기능입니다",
                        onClick = { }
                    )
                }
            }

            // 연결된 카메라 정보
            SettingsSection(title = "연결된 카메라 정보") {
                val connectionType = when {
                    isUsbConnected -> "USB 연결"
                    isPtpipConnected -> "Wi-Fi 연결 (PTPIP)"
                    else -> "연결 안됨"
                }

                val cameraName = when {
                    cameraUiState.connectedCameraModel != null && cameraUiState.connectedCameraManufacturer != null ->
                        "${cameraUiState.connectedCameraManufacturer} ${cameraUiState.connectedCameraModel}"
                    cameraUiState.connectedCameraModel != null ->
                        cameraUiState.connectedCameraModel
                    else -> "정보 없음"
                }

                ClickableRowV2(
                    icon = Icons.Default.CameraAlt,
                    title = "연결 상태",
                    subtitle = connectionType,
                    onClick = { }
                )

                if (isUsbConnected || isPtpipConnected) {
                    ClickableRowV2(
                        icon = Icons.Default.Info,
                        title = "카메라 모델",
                        subtitle = cameraName ?: "정보 없음",
                        onClick = { }
                    )
                    cameraUiState.cameraFunctionLimitation?.let { limitation ->
                        ClickableRowV2(
                            icon = Icons.Default.Info,
                            title = "기능 제한 안내",
                            subtitle = limitation,
                            onClick = { }
                        )
                    }
                }
            }

            // 사용자 정보
            SettingsSection(title = "사용자 정보") {
                val currentUser = authUiState.currentUser
                UserProfileItem(user = currentUser, onClick = { })
                ClickableRowV2(
                    icon = Icons.Default.Logout,
                    title = if (authUiState.isLoading) "로그아웃 중..." else "로그아웃",
                    subtitle = if (authUiState.isLoading) {
                        "잠시만 기다려주세요..."
                    } else {
                        "현재 계정에서 로그아웃"
                    },
                    onClick = {
                        if (!authUiState.isLoading) {
                            authViewModel?.signOut()
                        }
                    }
                )
            }

            // 서버 설정
            if (BuildConfig.SHOW_DEVELOPER_FEATURES) {
                SettingsSection(title = "서버 설정 (개발 버전)") {
                    ClickableRowV2(
                        icon = Icons.Default.Storage,
                        title = "저장 공간",
                        subtitle = "사용 중: 2.3GB / 10GB",
                        onClick = { }
                    )
                    ClickableRowV2(
                        icon = Icons.Default.Security,
                        title = "권한 관리",
                        subtitle = "서버 접근 권한 설정",
                        onClick = { }
                    )
                }
            }

            // 앱 설정
            SettingsSection(title = "앱 설정") {
                ClickableRowV2(
                    icon = Icons.Default.Notifications,
                    title = "알림 설정",
                    subtitle = "푸시 알림 및 소리 설정",
                    onClick = { }
                )
                ClickableRowV2(
                    icon = Icons.Default.DarkMode,
                    title = "테마 설정",
                    subtitle = "현재: ${getThemeDisplayName(currentThemeMode)}",
                    onClick = { showThemeDialog = true }
                )

                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = context.packageName
                val isIgnoringOptimizations = pm.isIgnoringBatteryOptimizations(packageName)
                ClickableRowV2(
                    icon = Icons.Default.Settings,
                    title = "배터리 최적화 예외 설정",
                    subtitle = if (isIgnoringOptimizations) {
                        "배터리 사용량 최적화 예외 처리됨"
                    } else {
                        "백그라운드 연결 및 알림을 위해 예외로 설정 필요"
                    },
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:$packageName")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "설정 화면을 열 수 없습니다: ${e.localizedMessage}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }

            if (isAdminTier) {
                val isNativeLogCaptureEnabled by appSettingsViewModel.isNativeLogCaptureEnabled.collectAsStateWithLifecycle()

                SettingsSection(title = "ADMIN 전용 설정") {
                    ClickableRowV2(
                        icon = Icons.Default.CameraAlt,
                        title = "🧪 가상 카메라 (Mock Camera)",
                        subtitle = "개발/테스트용 가상 카메라 기능",
                        onClick = {
                            context.startActivity(Intent(context, MockCameraActivity::class.java))
                        }
                    )
                    ClickableRowV2(
                        icon = Icons.Default.Info,
                        title = "🔍 카메라 기능 정보",
                        subtitle = "libgphoto2 API 기반 카메라 지원 기능 조회",
                        onClick = {
                            context.startActivity(Intent(context, CameraAbilitiesActivity::class.java))
                        }
                    )
                    SwitchRowV2(
                        icon = Icons.Default.Info,
                        title = "📝 네이티브 로그 캡처",
                        subtitle = if (isNativeLogCaptureEnabled) {
                            "활성화됨 - libgphoto2 로그를 TXT 파일로 저장 중"
                        } else {
                            "PTP/IP 디버깅을 위한 상세 로그 기록"
                        },
                        checked = isNativeLogCaptureEnabled,
                        onCheckedChange = {
                            appSettingsViewModel.setNativeLogCaptureEnabled(it)
                            val message = if (it) {
                                "네이티브 로그 캡처를 시작합니다.\n카메라 초기화 시 상세 로그가 기록됩니다."
                            } else {
                                "네이티브 로그 캡처를 중지했습니다."
                            }
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    )

                    if (isNativeLogCaptureEnabled) {
                        ClickableRowV2(
                            icon = Icons.Default.Storage,
                            title = "로그 파일 보기",
                            subtitle = "저장된 네이티브 로그 파일 확인 및보내기",
                            onClick = {
                                val logFiles = appSettingsViewModel.getLogFiles()
                                if (logFiles.isEmpty()) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "저장된 로그 파일이 없습니다.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    coroutineScope.launch {
                                        logDialogContent = appSettingsViewModel.getLogFileContent()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // 테마 다이얼로그 — V2 SecondaryButton
            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text("테마 설정") },
                    text = {
                        val options = listOf(
                            ThemeMode.FOLLOW_SYSTEM to "시스템 설정 따름",
                            ThemeMode.LIGHT to "라이트 모드",
                            ThemeMode.DARK to "다크 모드"
                        )
                        Column {
                            options.forEach { (mode, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            appSettingsViewModel.setThemeMode(mode)
                                            showThemeDialog = false
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = currentThemeMode == mode,
                                        onClick = {
                                            appSettingsViewModel.setThemeMode(mode)
                                            showThemeDialog = false
                                        }
                                    )
                                    Text(label, color = TextPrimaryV2)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        SecondaryButton(text = "취소", onClick = { showThemeDialog = false })
                    }
                )
            }

            // 네이티브 로그 다이얼로그 — V2 PrimaryButton/SecondaryButton
            logDialogContent?.let { logContent ->
                AlertDialog(
                    onDismissRequest = { logDialogContent = null },
                    title = { Text("네이티브 로그") },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = logContent.takeLast(3000),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        PrimaryButton(text = "확인", onClick = { logDialogContent = null })
                    },
                    dismissButton = {
                        SecondaryButton(text = "전체 복사", onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("native_log", logContent))
                            android.widget.Toast.makeText(context, "로그가 클립보드에 복사되었습니다", android.widget.Toast.LENGTH_SHORT).show()
                        })
                    }
                )
            }

            // 정보
            SettingsSection(title = "정보") {
                ClickableRowV2(
                    icon = Icons.Default.Info,
                    title = "오픈소스 라이선스",
                    subtitle = "사용된 오픈소스 라이브러리",
                    onClick = {
                        val intent = Intent(context, OpenSourceLicensesActivity::class.java)
                        context.startActivity(intent)
                    }
                )
                ClickableRowV2(
                    icon = Icons.Default.Update,
                    title = "앱 버전",
                    subtitle = BuildConfig.VERSION_NAME,
                    onClick = { }
                )
            }

            // Mock Camera 섹션
            if (BuildConfig.SHOW_DEVELOPER_FEATURES) {
                SettingsSection(title = "🧪 가상 카메라 (개발 전용)") {
                    NavigationRowV2(
                        icon = Icons.Default.CameraAlt,
                        title = "Mock Camera 설정",
                        subtitle = "실제 카메라 없이 테스트할 수 있는 가상 카메라",
                        onClick = {
                            val intent = Intent(context, MockCameraActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                }

                SettingsSection(title = "관리자 레퍼럴 코드 관리") {
                    val totalCodes = adminReferralState.statistics["totalCodes"] as? Int ?: 0
                    val availableCodes = adminReferralState.statistics["availableCodes"] as? Int ?: 0
                    val usedCodes = adminReferralState.statistics["usedCodes"] as? Int ?: 0
                    val usageRate = adminReferralState.statistics["usageRate"] as? Int ?: 0

                    ClickableRowV2(
                        icon = Icons.Default.Info,
                        title = "레퍼럴 코드 사용량",
                        subtitle = "전체: ${totalCodes}개 | 사용 가능: ${availableCodes}개 | 사용됨: ${usedCodes}개 (${usageRate}%)",
                        onClick = { adminReferralCodeViewModel.refreshData() }
                    )
                    ClickableRowV2(
                        icon = Icons.Default.Settings,
                        title = "레퍼럴 코드 30개 생성",
                        subtitle = if (adminReferralState.isLoading) "생성 중..." else "새로운 레퍼럴 코드 30개를 생성합니다",
                        onClick = {
                            if (!adminReferralState.isLoading) {
                                adminReferralCodeViewModel.generateReferralCodes(30)
                            }
                        }
                    )
                    ClickableRowV2(
                        icon = Icons.Default.ContentCopy,
                        title = "사용 가능한 코드 하나 추출",
                        subtitle = "사용하지 않은 레퍼럴 코드 하나를 클립보드에 복사",
                        onClick = {
                            val code = adminReferralCodeViewModel.extractOneAvailableCode()
                            if (code != null) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("referral_code", code)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(
                                    context,
                                    "레퍼럴 코드 '$code'가 클립보드에 복사되었습니다",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )

                    if (adminReferralState.isLoading) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.base, vertical = Spacing.md)) {
                            ProgressBarV2(progress = null)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

/**
 * V2 섹션 컨테이너 — Lightroom 환경설정 톤.
 * 헤더(HeadingM, TextSecondaryV2 uppercase 톤) + SurfaceV2 tier=1 패널 + RowItem 리스트.
 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.base)) {
        Text(
            text = title.uppercase(),
            style = HeadingM,
            color = TextTertiary,
            modifier = Modifier.padding(
                start = Spacing.xs,
                top = Spacing.xl,
                bottom = Spacing.sm
            )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface1)
        ) {
            content()
        }
    }
}

/**
 * V2 RowItem 기반 — 스위치 trailing.
 * Row 자체를 클릭해도 토글되도록 한다.
 */
@Composable
private fun SwitchRowV2(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column {
        RowItem(
            label = title,
            description = subtitle.takeIf { it.isNotEmpty() },
            leadingIcon = icon,
            trailing = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAccent,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = TextSecondaryV2,
                        uncheckedTrackColor = Surface3
                    )
                )
            },
            onClick = { onCheckedChange(!checked) }
        )
        DividerLineV2()
    }
}

/**
 * V2 RowItem 기반 — 클릭 가능 행 (chevron 없음).
 */
@Composable
private fun ClickableRowV2(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column {
        RowItem(
            label = title,
            description = subtitle.takeIf { it.isNotEmpty() },
            leadingIcon = icon,
            onClick = onClick
        )
        DividerLineV2()
    }
}

/**
 * V2 RowItem 기반 — 네비게이션 행 (chevron trailing).
 */
@Composable
private fun NavigationRowV2(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column {
        RowItem(
            label = title,
            description = subtitle.takeIf { it.isNotEmpty() },
            leadingIcon = icon,
            trailing = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondaryV2,
                    modifier = Modifier.size(IconSize.md)
                )
            },
            onClick = onClick
        )
        DividerLineV2()
    }
}

/**
 * 사용자 프로필 표시 — RowItem 패턴.
 */
@Composable
fun UserProfileItem(
    user: User?,
    onClick: () -> Unit
) {
    Column {
        RowItem(
            label = user?.displayName ?: "사용자",
            description = user?.email ?: "로그인이 필요합니다",
            leadingIcon = null,
            trailing = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondaryV2,
                    modifier = Modifier.size(IconSize.md)
                )
            },
            onClick = onClick,
            modifier = Modifier
        )
        // 프로필 이미지는 RowItem의 leadingIcon이 ImageVector만 받기 때문에
        // 별도 오버레이 처리는 생략하고 RowItem 위에 별도 행으로 표시한다.
    }
}

// 기존 호출처 호환용 별칭 — 외부에서 SettingsItem*을 참조할 수 있어 유지.
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ClickableRowV2(icon, title, subtitle, onClick)
}

@Composable
fun SettingsItemWithSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchRowV2(icon, title, subtitle, checked, onCheckedChange)
}

@Composable
fun SettingsItemWithNavigation(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    NavigationRowV2(icon, title, subtitle, onClick)
}
