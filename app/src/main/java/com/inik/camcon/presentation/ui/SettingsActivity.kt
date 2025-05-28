package com.inik.camcon.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.CamConTheme
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
fun SettingsScreen(onBackClick: () -> Unit) {
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
        ) {
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
