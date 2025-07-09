package com.inik.camcon.presentation.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CamConTheme {
                SettingsScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    ptpipViewModel: PtpipViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    
    // PTPIP 설정 상태
    val isPtpipEnabled by ptpipViewModel.isPtpipEnabled.collectAsState(initial = false)
    val isWifiConnectionModeEnabled by ptpipViewModel.isWifiConnectionModeEnabled.collectAsState(
        initial = true
    )
    val isAutoDiscoveryEnabled by ptpipViewModel.isAutoDiscoveryEnabled.collectAsState(initial = true)
    val isAutoConnectEnabled by ptpipViewModel.isAutoConnectEnabled.collectAsState(initial = false)
    val lastConnectedName by ptpipViewModel.lastConnectedName.collectAsState(initial = null)
    val connectionState by ptpipViewModel.connectionState.collectAsState()

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
            // PTPIP Wi-Fi 카메라 설정 섹션
            SettingsSection(title = "📷 Wi-Fi 카메라 연결 (PTPIP)") {
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

            // User Info Section
            SettingsSection(title = "사용자 정보") {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "프로필",
                    subtitle = "사용자 정보 확인 및 수정",
                    onClick = { /* TODO */ }
                )
                SettingsItem(
                    icon = Icons.Default.Logout,
                    title = "로그아웃",
                    subtitle = "현재 계정에서 로그아웃",
                    onClick = { /* TODO */ }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Server Section
            SettingsSection(title = "서버 설정") {
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

            // App Settings Section
            SettingsSection(title = "앱 설정") {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "알림 설정",
                    subtitle = "푸시 알림 및 소리 설정",
                    onClick = { /* TODO */ }
                )
                var isDarkMode by remember { mutableStateOf(false) }
                SettingsItemWithSwitch(
                    icon = Icons.Default.DarkMode,
                    title = "다크 모드",
                    subtitle = "어두운 테마 사용",
                    checked = isDarkMode,
                    onCheckedChange = { isDarkMode = it }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // About Section
            SettingsSection(title = "정보") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "오픈소스 라이선스",
                    subtitle = "사용된 오픈소스 라이브러리",
                    onClick = { /* TODO */ }
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