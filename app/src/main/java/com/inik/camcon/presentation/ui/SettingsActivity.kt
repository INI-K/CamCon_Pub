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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.inik.camcon.BuildConfig
import com.inik.camcon.data.datasource.local.ThemeMode
import com.inik.camcon.domain.model.User
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.AdminReferralCodeViewModel
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.AuthViewModel
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import kotlin.jvm.java

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsState()
            val authViewModel: AuthViewModel = hiltViewModel()

            CamConTheme(themeMode = themeMode) {
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
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        // Provide a default onBackClick. ViewModel is not injected in Preview.
        SettingsScreen(
            onBackClick = {},
            authViewModel = null // Preview에서는 null로 처리
        )
    }
}

// Activity-level Preview 추가
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

    // Auth 상태 - null 체크 추가
    val authUiState by authViewModel?.uiState?.collectAsState() ?: remember {
        mutableStateOf(com.inik.camcon.presentation.viewmodel.AuthUiState())
    }

    // 관리자 레퍼럴 코드 상태
    val adminReferralState by adminReferralCodeViewModel.uiState.collectAsState()

    // 카메라 상태 정보
    val cameraUiState by cameraViewModel.uiState.collectAsState()
    val isUsbConnected = cameraUiState.isNativeCameraConnected
    val isPtpipConnected = cameraUiState.isPtpipConnected

    // 로그아웃 성공 시 LoginActivity로 이동
    LaunchedEffect(authUiState.isSignOutSuccess) {
        if (authUiState.isSignOutSuccess) {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
            (context as? ComponentActivity)?.finish()
        }
    }

    // 관리자 레퍼럴 코드 관련 메시지 처리
    adminReferralState.error?.let { error ->
        LaunchedEffect(error) {
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
            adminReferralCodeViewModel.clearError()
        }
    }

    adminReferralState.successMessage?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            adminReferralCodeViewModel.clearSuccessMessage()
        }
    }

    // PTPIP 설정 상태
    val isPtpipEnabled by ptpipViewModel.isPtpipEnabled.collectAsState(initial = false)
    val isWifiConnectionModeEnabled by ptpipViewModel.isWifiConnectionModeEnabled.collectAsState(
        initial = true
    )
    val isAutoDiscoveryEnabled by ptpipViewModel.isAutoDiscoveryEnabled.collectAsState(initial = true)
    val isAutoConnectEnabled by ptpipViewModel.isAutoConnectEnabled.collectAsState(initial = false)
    val lastConnectedName by ptpipViewModel.lastConnectedName.collectAsState(initial = null)

    // 앱 설정 상태
    val isCameraControlsEnabled by appSettingsViewModel.isCameraControlsEnabled.collectAsState()
    val isLiveViewEnabled by appSettingsViewModel.isLiveViewEnabled.collectAsState()
    val isAdminTier by appSettingsViewModel.isAdminTier.collectAsState()
    val currentThemeMode by appSettingsViewModel.themeMode.collectAsState()
    val isAutoStartEventListener by appSettingsViewModel.isAutoStartEventListenerEnabled.collectAsState()
    val isShowLatestPhotoWhenDisabled by appSettingsViewModel.isShowLatestPhotoWhenDisabled.collectAsState()

    // 색감 전송 설정 상태
    val isColorTransferEnabled by appSettingsViewModel.isColorTransferEnabled.collectAsState()
    val colorTransferReferenceImagePath by appSettingsViewModel.colorTransferReferenceImagePath.collectAsState()
    val isRawFileDownloadEnabled by appSettingsViewModel.isRawFileDownloadEnabled.collectAsState()

    val subscriptionTier by appSettingsViewModel.subscriptionTier.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val message = if (isGranted) {
            "알림 권한이 허용되었습니다."
        } else {
            "알림 권한이 거부되었습니다."
        }
        android.widget.Toast.makeText(
            context,
            message,
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    // 색감 전송 이미지 선택 런처
    val referenceImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            // URI에서 파일 경로로 변환하여 저장
            try {
                val imageDir = File(context.filesDir, "color_transfer_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }

                val fileName = "color_ref_${System.currentTimeMillis()}.jpg"
                val targetFile = File(imageDir, fileName)

                // URI에서 파일로 복사
                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 설정에 파일 경로 저장
                appSettingsViewModel.setColorTransferReferenceImagePath(targetFile.absolutePath)

            } catch (e: Exception) {
                // 오류 처리 (로그 출력 등)
                e.printStackTrace()
            }
        }
    }

    // 테마 선택 다이얼로그 상태
    var showThemeDialog by remember { mutableStateOf(false) }

    // 테마 모드를 한글로 변환하는 함수
    fun getThemeDisplayName(themeMode: ThemeMode): String {
        return when (themeMode) {
            ThemeMode.FOLLOW_SYSTEM -> "시스템 설정 따름"
            ThemeMode.LIGHT -> "라이트 모드"
            ThemeMode.DARK -> "다크 모드"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 카메라 제어 설정 섹션 - 개발자 기능이 활성화된 경우만 표시
            if (BuildConfig.SHOW_DEVELOPER_FEATURES) {
                SettingsSection(title = "카메라 제어 설정 (개발 버전)") {
                    SettingsItemWithSwitch(
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
                        SettingsItemWithSwitch(
                            icon = Icons.Default.Visibility,
                            title = "라이브뷰 활성화",
                            subtitle = "실시간 카메라 화면 표시 (ADMIN 전용)",
                            checked = isLiveViewEnabled,
                            onCheckedChange = { appSettingsViewModel.setLiveViewEnabled(it) }
                        )

                        SettingsItemWithSwitch(
                            icon = Icons.Default.Settings,
                            title = "자동 이벤트 수신",
                            subtitle = "카메라 제어 탭 진입 시 자동으로 이벤트 리스너 시작",
                            checked = isAutoStartEventListener,
                            onCheckedChange = {
                                appSettingsViewModel.setAutoStartEventListenerEnabled(
                                    it
                                )
                            }
                        )
                    } else if (isCameraControlsEnabled && !isAdminTier) {
                        // ADMIN 티어가 아닐 때 라이브뷰 제한 안내
                        SettingsItem(
                            icon = Icons.Default.Visibility,
                            title = "라이브뷰 기능",
                            subtitle = "ADMIN 티어에서만 사용 가능한 기능입니다",
                            onClick = { /* 클릭해도 아무 동작 안함 */ }
                        )

                        SettingsItemWithSwitch(
                            icon = Icons.Default.Settings,
                            title = "자동 이벤트 수신",
                            subtitle = "카메라 제어 탭 진입 시 자동으로 이벤트 리스너 시작",
                            checked = isAutoStartEventListener,
                            onCheckedChange = {
                                appSettingsViewModel.setAutoStartEventListenerEnabled(
                                    it
                                )
                            }
                        )
                    }

                    if (!isCameraControlsEnabled) {
                        SettingsItemWithSwitch(
                            icon = Icons.Default.Photo,
                            title = "최신 사진 표시",
                            subtitle = "카메라 컨트롤 비활성화 시 최근 촬영한 사진 표시",
                            checked = isShowLatestPhotoWhenDisabled,
                            onCheckedChange = {
                                appSettingsViewModel.setShowLatestPhotoWhenDisabled(
                                    it
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // PTPIP Wi-Fi 카메라 설정 섹션 - 개발자 기능이 활성화된 경우만 표시
                SettingsSection(title = "Wi-Fi 카메라 연결 (PTPIP) - 개발 버전") {
                    SettingsItemWithSwitch(
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
                        SettingsItemWithSwitch(
                            icon = Icons.Default.NetworkWifi,
                            title = "WIFI 연결 하기",
                            subtitle = "카메라와 동일한 Wi-Fi 네트워크에서 연결 (권장)",
                            checked = isWifiConnectionModeEnabled,
                            onCheckedChange = { ptpipViewModel.setWifiConnectionModeEnabled(it) }
                        )

                        SettingsItemWithSwitch(
                            icon = Icons.Default.Settings,
                            title = "자동 카메라 검색",
                            subtitle = "네트워크에서 PTPIP 카메라 자동 찾기",
                            checked = isAutoDiscoveryEnabled,
                            onCheckedChange = { ptpipViewModel.setAutoDiscoveryEnabled(it) }
                        )

                        SettingsItemWithSwitch(
                            icon = Icons.Default.CameraAlt,
                            title = "자동 연결",
                            subtitle = "마지막 연결된 카메라에 자동 연결",
                            checked = isAutoConnectEnabled,
                            onCheckedChange = { enabled ->
                                ptpipViewModel.updateAutoConnectEnabled(
                                    enabled = enabled,
                                    onResult = { _, message ->
                                        android.widget.Toast.makeText(
                                            context,
                                            message,
                                            android.widget.Toast.LENGTH_LONG
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

                        SettingsItemWithNavigation(
                            icon = Icons.Default.Info,
                            title = "카메라 연결 관리",
                            subtitle = "${ptpipViewModel.getConnectionStatusText()} - 탭하여 자세히 보기",
                            onClick = {
                                // PtpipConnectionActivity 시작
                                val intent = Intent(context, PtpipConnectionActivity::class.java)
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // 색감 전송 설정 섹션
            SettingsSection(title = "색감 전송 설정") {
                SettingsItemWithSwitch(
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
                    // 기존 참조 이미지 선택 항목을 간소화
                    SettingsItemWithNavigation(
                        icon = Icons.Default.Settings,
                        title = "상세 설정",
                        subtitle = "색감 전송 알고리즘 및 고급 옵션 설정",
                        onClick = {
                            // ColorTransferSettingsActivity로 이동
                            val intent = Intent(context, ColorTransferSettingsActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // RAW 파일 다운로드 설정 섹션
            SettingsSection(title = "RAW 파일 다운로드 설정") {
                if (subscriptionTier == com.inik.camcon.domain.model.SubscriptionTier.PRO ||
                    subscriptionTier == com.inik.camcon.domain.model.SubscriptionTier.ADMIN ||
                    subscriptionTier == com.inik.camcon.domain.model.SubscriptionTier.REFERRER
                ) {
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Photo,
                        title = "RAW 파일 다운로드",
                        subtitle = if (isRawFileDownloadEnabled) "활성화됨" else "비활성화됨",
                        checked = isRawFileDownloadEnabled,
                        onCheckedChange = { appSettingsViewModel.setRawFileDownloadEnabled(it) }
                    )
                } else {
                    SettingsItem(
                        icon = Icons.Default.Photo,
                        title = "RAW 파일 다운로드",
                        subtitle = "PRO, ADMIN, REFERRER 티어에서만 사용 가능한 기능입니다",
                        onClick = { /* 클릭해도 아무 동작 안함 */ }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 연결된 카메라 정보 섹션
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

                SettingsItem(
                    icon = Icons.Default.CameraAlt,
                    title = "연결 상태",
                    subtitle = connectionType,
                    onClick = { }
                )

                if (isUsbConnected || isPtpipConnected) {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "카메라 모델",
                        subtitle = cameraName ?: "정보 없음",
                        onClick = { }
                    )

                    // 기능 제한 안내
                    cameraUiState.cameraFunctionLimitation?.let { limitation ->
                        SettingsItem(
                            icon = Icons.Default.Info,
                            title = "기능 제한 안내",
                            subtitle = limitation,
                            onClick = { }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // User Info Section
            SettingsSection(title = "사용자 정보") {
                val currentUser = authUiState.currentUser

                UserProfileItem(
                    user = currentUser,
                    onClick = { /* TODO */ }
                )
                SettingsItem(
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

            // 로그아웃 에러 처리
            authUiState.error?.let { error ->
                LaunchedEffect(error) {
                    android.widget.Toast.makeText(context, "로그아웃 실패: $error", android.widget.Toast.LENGTH_LONG).show()
                    authViewModel?.clearError()
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Server Section
            if (BuildConfig.SHOW_DEVELOPER_FEATURES) {
                SettingsSection(title = "서버 설정 (개발 버전)") {
                    SettingsItem(
                        icon = Icons.Default.Storage,
                        title = "저장 공간",
                        subtitle = "사용 중: 2.3GB / 10GB",
                        onClick = { /* TODO */ }
                    )
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "권한 관리",
                        subtitle = "서버 접근 권한 설정",
                        onClick = { /* TODO */ }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // App Settings Section
            SettingsSection(title = "앱 설정") {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "알림 설정",
                    subtitle = "푸시 알림 및 소리 설정",
                    onClick = { /* TODO */ }
                )
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = "테마 설정",
                    subtitle = "현재: ${getThemeDisplayName(currentThemeMode)}",
                    onClick = {
                        showThemeDialog = true
                    }
                )

                // === 배터리 최적화 예외 설정 항목 추가 ===
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val packageName = context.packageName
                val isIgnoringOptimizations = pm.isIgnoringBatteryOptimizations(packageName)
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "배터리 최적화 예외 설정",
                    subtitle = if (isIgnoringOptimizations) {
                        "배터리 사용량 최적화 예외 처리됨"
                    } else {
                        "백그라운드 연결 및 알림을 위해 예외로 설정 필요"
                    },
                    onClick = {
                        try {
                            val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
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
                // === 배터리 최적화 예외 항목 끝 ===
            }

            if (isAdminTier) {
                // ADMIN 전용: 가상 카메라 설정 및 네이티브 로그
                val isNativeLogCaptureEnabled by appSettingsViewModel.isNativeLogCaptureEnabled.collectAsState()

                SettingsSection(title = "ADMIN 전용 설정") {
                    SettingsItem(
                        icon = Icons.Default.CameraAlt,
                        title = "🧪 가상 카메라 (Mock Camera)",
                        subtitle = "개발/테스트용 가상 카메라 기능",
                        onClick = {
                            context.startActivity(Intent(context, MockCameraActivity::class.java))
                        }
                    )

                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "🔍 카메라 기능 정보",
                        subtitle = "libgphoto2 API 기반 카메라 지원 기능 조회",
                        onClick = {
                            context.startActivity(
                                Intent(
                                    context,
                                    CameraAbilitiesActivity::class.java
                                )
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 네이티브 로그 캡처 설정
                    SettingsItemWithSwitch(
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
                            android.widget.Toast.makeText(
                                context,
                                message,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                    if (isNativeLogCaptureEnabled) {
                        SettingsItem(
                            icon = Icons.Default.Storage,
                            title = "로그 파일 보기",
                            subtitle = "저장된 네이티브 로그 파일 확인 및보내기",
                            onClick = {
                                // 로그 파일 목록 표시
                                val logFiles = appSettingsViewModel.getLogFiles()
                                if (logFiles.isEmpty()) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "저장된 로그 파일이 없습니다.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    // 최신 로그 파일 내용 표시
                                    val logContent = appSettingsViewModel.getLogFileContent()

                                    // 간단한 다이얼로그로 로그 표시
                                    android.app.AlertDialog.Builder(context)
                                        .setTitle("네이티브 로그")
                                        .setMessage(logContent.takeLast(3000)) // 마지막 3000자만 표시
                                        .setPositiveButton("확인", null)
                                        .setNeutralButton("전체 복사") { _, _ ->
                                            val clipboard =
                                                context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText(
                                                "native_log",
                                                logContent
                                            )
                                            clipboard.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(
                                                context,
                                                "로그가 클립보드에 복사되었습니다",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .show()
                                }
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            if (showThemeDialog) {
                android.app.AlertDialog.Builder(context)
                    .setTitle("테마 설정")
                    .setSingleChoiceItems(
                        arrayOf("시스템 설정 따름", "라이트 모드", "다크 모드").map { it }.toTypedArray(),
                        when (currentThemeMode) {
                            ThemeMode.FOLLOW_SYSTEM -> 0
                            ThemeMode.LIGHT -> 1
                            ThemeMode.DARK -> 2
                        }
                    ) { _, which ->
                        when (which) {
                            0 -> appSettingsViewModel.setThemeMode(ThemeMode.FOLLOW_SYSTEM)
                            1 -> appSettingsViewModel.setThemeMode(ThemeMode.LIGHT)
                            2 -> appSettingsViewModel.setThemeMode(ThemeMode.DARK)
                        }
                        showThemeDialog = false
                    }
                    .setNegativeButton("취소") { _, _ ->
                        showThemeDialog = false
                    }
                    .show()
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // About Section
            SettingsSection(title = "정보") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "오픈소스 라이선스",
                    subtitle = "사용된 오픈소스 라이브러리",
                    onClick = {
                        val intent = Intent(context, OpenSourceLicensesActivity::class.java)
                        context.startActivity(intent)
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Update,
                    title = "앱 버전",
                    subtitle = "1.0.0",
                    onClick = { /* TODO */ }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 🧪 Mock Camera 섹션 - 개발 버전에서 표시
            if (BuildConfig.SHOW_DEVELOPER_FEATURES) {
                SettingsSection(title = "🧪 가상 카메라 (개발 전용)") {
                    SettingsItemWithNavigation(
                        icon = Icons.Default.CameraAlt,
                        title = "Mock Camera 설정",
                        subtitle = "실제 카메라 없이 테스트할 수 있는 가상 카메라",
                        onClick = {
                            val intent = Intent(context, MockCameraActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsSection(title = "관리자 레퍼럴 코드 관리") {
                    // 통계 정보
                    val totalCodes = adminReferralState.statistics["totalCodes"] as? Int ?: 0
                    val availableCodes =
                        adminReferralState.statistics["availableCodes"] as? Int ?: 0
                    val usedCodes = adminReferralState.statistics["usedCodes"] as? Int ?: 0
                    val usageRate = adminReferralState.statistics["usageRate"] as? Int ?: 0

                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "레퍼럴 코드 사용량",
                        subtitle = "전체: ${totalCodes}개 | 사용 가능: ${availableCodes}개 | 사용됨: ${usedCodes}개 (${usageRate}%)",
                        onClick = { adminReferralCodeViewModel.refreshData() }
                    )

                    SettingsItem(
                        icon = Icons.Default.Settings,
                        title = "레퍼럴 코드 30개 생성",
                        subtitle = if (adminReferralState.isLoading) "생성 중..." else "새로운 레퍼럴 코드 30개를 생성합니다",
                        onClick = {
                            if (!adminReferralState.isLoading) {
                                adminReferralCodeViewModel.generateReferralCodes(30)
                            }
                        }
                    )

                    SettingsItem(
                        icon = Icons.Default.ContentCopy,
                        title = "사용 가능한 코드 하나 추출",
                        subtitle = "사용하지 않은 레퍼럴 코드 하나를 클립보드에 복사",
                        onClick = {
                            val code = adminReferralCodeViewModel.extractOneAvailableCode()
                            if (code != null) {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip =
                                    android.content.ClipData.newPlainText("referral_code", code)
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("처리 중...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun SettingsItemWithSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun SettingsItemWithNavigation(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "더보기",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun UserProfileItem(
    user: User?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 프로필 이미지
        if (user?.photoUrl != null) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = "프로필 이미지",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "기본 프로필",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user?.displayName ?: "사용자",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = user?.email ?: "로그인이 필요합니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "더보기",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}