package com.inik.camcon.presentation.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
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
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.AuthViewModel
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream

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
    CamConTheme {
        // Provide a default onBackClick. ViewModel is not injected in Preview.
        SettingsScreen(
            onBackClick = {},
            authViewModel = null // Preview에서는 null로 처리
        )
    }
}

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    ptpipViewModel: PtpipViewModel = hiltViewModel(),
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel? = hiltViewModel()
) {
    val context = LocalContext.current

    // Auth 상태 - null 체크 추가
    val authUiState by authViewModel?.uiState?.collectAsState() ?: remember {
        mutableStateOf(com.inik.camcon.presentation.viewmodel.AuthUiState())
    }

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

    // PTPIP 설정 상태
    val isPtpipEnabled by ptpipViewModel.isPtpipEnabled.collectAsState(initial = false)
    val isWifiConnectionModeEnabled by ptpipViewModel.isWifiConnectionModeEnabled.collectAsState(
        initial = true
    )
    val isAutoDiscoveryEnabled by ptpipViewModel.isAutoDiscoveryEnabled.collectAsState(initial = true)
    val isAutoConnectEnabled by ptpipViewModel.isAutoConnectEnabled.collectAsState(initial = false)
    val lastConnectedName by ptpipViewModel.lastConnectedName.collectAsState(initial = null)
    val connectionState by ptpipViewModel.connectionState.collectAsState()

    // 앱 설정 상태
    val isCameraControlsEnabled by appSettingsViewModel.isCameraControlsEnabled.collectAsState()
    val isLiveViewEnabled by appSettingsViewModel.isLiveViewEnabled.collectAsState()
    val currentThemeMode by appSettingsViewModel.themeMode.collectAsState()
    val isAutoStartEventListener by appSettingsViewModel.isAutoStartEventListenerEnabled.collectAsState()
    val isShowLatestPhotoWhenDisabled by appSettingsViewModel.isShowLatestPhotoWhenDisabled.collectAsState()

    // 색감 전송 설정 상태
    val isColorTransferEnabled by appSettingsViewModel.isColorTransferEnabled.collectAsState()
    val colorTransferReferenceImagePath by appSettingsViewModel.colorTransferReferenceImagePath.collectAsState()
    val colorTransferTargetImagePath by appSettingsViewModel.colorTransferTargetImagePath.collectAsState()

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

    // 대상 이미지 선택 런처
    val targetImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            // 임시로 대상 이미지 설정 (실제 구현에서는 서버에서 받은 최신 사진을 사용)
            try {
                val imageDir = File(context.filesDir, "color_transfer_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }

                val fileName = "color_target_${System.currentTimeMillis()}.jpg"
                val targetFile = File(imageDir, fileName)

                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // ViewModel을 통해 대상 이미지 경로 저장
                appSettingsViewModel.setColorTransferTargetImagePath(targetFile.absolutePath)

            } catch (e: Exception) {
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
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            )
        }
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

                    if (isCameraControlsEnabled) {
                        SettingsItemWithSwitch(
                            icon = Icons.Default.Visibility,
                            title = "라이브뷰 활성화",
                            subtitle = "실시간 카메라 화면 표시",
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

                Divider(modifier = Modifier.padding(vertical = 8.dp))

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
                            onCheckedChange = { ptpipViewModel.setAutoConnectEnabled(it) }
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

                Divider(modifier = Modifier.padding(vertical = 8.dp))
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

            Divider(modifier = Modifier.padding(vertical = 8.dp))

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
                    Toast.makeText(context, "로그아웃 실패: $error", Toast.LENGTH_LONG).show()
                    authViewModel?.clearError()
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

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

                Divider(modifier = Modifier.padding(vertical = 8.dp))
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
            }

            if (showThemeDialog) {
                AlertDialog.Builder(context)
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

            Divider(modifier = Modifier.padding(vertical = 8.dp))

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
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.primary,
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
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
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
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colors.primary
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
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "더보기",
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
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
                    tint = MaterialTheme.colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user?.displayName ?: "사용자",
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = user?.email ?: "로그인이 필요합니다",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "더보기",
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}