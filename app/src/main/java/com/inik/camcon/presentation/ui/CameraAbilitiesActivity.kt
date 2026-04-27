package com.inik.camcon.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.CameraAbilitiesViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 카메라 기능 정보 화면 (ADMIN 전용)
 *
 * libgphoto2 API로 조회한 카메라 기능을 상세하게 표시
 * - CameraAbilities (operations, file_operations, folder_operations)
 * - DeviceInfo (manufacturer, model, version, serial)
 * - 지원 기능 목록 (capture_image, liveview 등)
 */
@AndroidEntryPoint
class CameraAbilitiesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsState()

            CamConTheme(themeMode = themeMode) {
                CameraAbilitiesScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAbilitiesScreen(
    viewModel: CameraAbilitiesViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val abilities by viewModel.abilities.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("카메라 기능 정보") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("카메라 정보 조회 중...")
                    }
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.refresh() }) {
                            Text("다시 시도")
                        }
                    }
                }
            }

            abilities == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("카메라가 연결되지 않았습니다")
                        OutlinedButton(onClick = { viewModel.refresh() }) {
                            Text("새로고침")
                        }
                    }
                }
            }

            else -> {
                CameraAbilitiesContent(
                    abilities = abilities!!,
                    deviceInfo = deviceInfo,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun CameraAbilitiesContent(
    abilities: com.inik.camcon.domain.model.CameraAbilitiesInfo,
    deviceInfo: com.inik.camcon.domain.model.PtpDeviceInfo?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 카메라 기본 정보
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📸 카메라 정보",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()

                    if (deviceInfo != null) {
                        InfoRow("제조사", deviceInfo.manufacturer)
                        InfoRow("모델", deviceInfo.model)
                        InfoRow("버전", deviceInfo.version)
                        if (deviceInfo.hasValidSerialNumber()) {
                            InfoRow("시리얼", deviceInfo.serialNumber)
                        }
                    } else {
                        InfoRow("모델", abilities.model)
                    }

                    InfoRow("제조사 (감지)", abilities.getManufacturer())
                    InfoRow("드라이버 상태", abilities.status)
                    InfoRow(
                        "포트 타입", when (abilities.portType) {
                            1 -> "USB"
                            else -> "Unknown"
                        }
                    )
                }
            }
        }

        // 연결 정보
        item {
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🔌 연결 정보",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()

                    val portType = when {
                        abilities.isUsbConnection() -> "USB"
                        abilities.isPtpipConnection() -> "Wi-Fi (PTP/IP)"
                        else -> "알 수 없음"
                    }
                    InfoRow("연결 타입", portType)
                    InfoRow("USB Vendor ID", abilities.usbVendor)
                    InfoRow("USB Product ID", abilities.usbProduct)
                    InfoRow("USB Class", abilities.usbClass.toString())
                }
            }
        }

        // 지원 기능 (촬영)
        item {
            FeatureCard(
                title = "📷 촬영 기능",
                features = listOf(
                    FeatureItem(
                        "원격 사진 촬영",
                        abilities.supports.captureImage,
                        Icons.Default.CameraAlt
                    ),
                    FeatureItem(
                        "원격 비디오 촬영",
                        abilities.supports.captureVideo,
                        Icons.Default.Videocam
                    ),
                    FeatureItem("오디오 녹음", abilities.supports.captureAudio, Icons.Default.Mic),
                    FeatureItem(
                        "라이브뷰 미리보기",
                        abilities.supports.capturePreview,
                        Icons.Default.Preview
                    ),
                    FeatureItem("트리거 촬영", abilities.supports.triggerCapture, Icons.Default.FlashOn)
                )
            )
        }

        // 지원 기능 (파일)
        item {
            FeatureCard(
                title = "📁 파일 작업",
                features = listOf(
                    FeatureItem("파일 삭제", abilities.supports.delete, Icons.Default.Delete),
                    FeatureItem("파일 미리보기", abilities.supports.preview, Icons.Default.Visibility),
                    FeatureItem("RAW 파일", abilities.supports.raw, Icons.Default.PhotoLibrary),
                    FeatureItem("오디오 파일", abilities.supports.audio, Icons.Default.AudioFile),
                    FeatureItem("EXIF 정보", abilities.supports.exif, Icons.Default.Info),
                    FeatureItem("전체 삭제", abilities.supports.deleteAll, Icons.Default.DeleteSweep)
                )
            )
        }

        // 지원 기능 (폴더)
        item {
            FeatureCard(
                title = "📂 폴더 작업",
                features = listOf(
                    FeatureItem("파일 업로드", abilities.supports.putFile, Icons.Default.Upload),
                    FeatureItem(
                        "디렉토리 생성",
                        abilities.supports.makeDir,
                        Icons.Default.CreateNewFolder
                    ),
                    FeatureItem("디렉토리 삭제", abilities.supports.removeDir, Icons.Default.FolderDelete)
                )
            )
        }

        // 설정 기능
        item {
            FeatureCard(
                title = "⚙️ 설정 기능",
                features = listOf(
                    FeatureItem("카메라 설정 변경", abilities.supports.config, Icons.Default.Settings)
                )
            )
        }

        // 비트마스크 원본 값 (개발자용)
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🔧 원시 값 (개발자용)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()

                    CodeRow("operations", "0x${abilities.operations.toString(16).uppercase()}")
                    CodeRow(
                        "file_operations",
                        "0x${abilities.fileOperations.toString(16).uppercase()}"
                    )
                    CodeRow(
                        "folder_operations",
                        "0x${abilities.folderOperations.toString(16).uppercase()}"
                    )
                }
            }
        }

        // 종합 평가
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        abilities.supports.isFullyControllable() ->
                            MaterialTheme.colorScheme.primaryContainer

                        abilities.supports.isDownloadOnly() ->
                            MaterialTheme.colorScheme.errorContainer

                        else ->
                            MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                abilities.supports.isFullyControllable() -> Icons.Default.CheckCircle
                                abilities.supports.isDownloadOnly() -> Icons.Default.Warning
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = when {
                                abilities.supports.isFullyControllable() ->
                                    MaterialTheme.colorScheme.primary

                                abilities.supports.isDownloadOnly() ->
                                    MaterialTheme.colorScheme.error

                                else ->
                                    MaterialTheme.colorScheme.secondary
                            }
                        )
                        Text(
                            text = "종합 평가",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = when {
                            abilities.supports.isFullyControllable() ->
                                "✅ 완전한 원격 제어 가능\n\n" +
                                        "이 카메라는 모든 기능을 지원합니다:\n" +
                                        "• 원격 촬영\n" +
                                        "• 라이브뷰\n" +
                                        "• 설정 변경\n" +
                                        "• 파일 관리"

                            abilities.supports.isDownloadOnly() ->
                                "⚠️ 다운로드만 가능\n\n" +
                                        "이 카메라는 파일 다운로드만 지원합니다.\n" +
                                        "원격 촬영 및 라이브뷰는 사용할 수 없습니다.\n\n" +
                                        "💡 제조사: ${abilities.getManufacturer()}\n" +
                                        "일부 ${abilities.getManufacturer()} 모델은 PTP 기능이 제한적입니다."

                            !abilities.supports.capturePreview ->
                                "ℹ️ 부분 지원\n\n" +
                                        "라이브뷰는 지원되지 않지만,\n" +
                                        "원격 촬영은 가능합니다."

                            else ->
                                "ℹ️ 일부 기능 지원\n\n" +
                                        "카메라 기능이 일부 제한될 수 있습니다."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Spacer
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    features: List<FeatureItem>
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()

            features.forEach { feature ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = feature.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (feature.supported)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = feature.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Icon(
                        imageVector = if (feature.supported) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (feature.supported) "지원" else "미지원",
                        tint = if (feature.supported)
                            Color(0xFF4CAF50)
                        else
                            Color(0xFFF44336),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CodeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private data class FeatureItem(
    val name: String,
    val supported: Boolean,
    val icon: ImageVector
)

@Preview(showBackground = true, name = "Camera Abilities")
@Composable
private fun Preview_CameraAbilitiesScreen() {
    val dummyAbilities = com.inik.camcon.domain.model.CameraAbilitiesInfo(
        model = "EOS R5",
        portType = 1,
        usbVendor = "0x04A9",
        usbProduct = "0x3229",
        usbClass = 6,
        operations = 0x00000FFF,
        fileOperations = 0x000003F7,
        folderOperations = 0x00000007,
        status = "connected",
        supports = com.inik.camcon.domain.model.CameraSupports(
            captureImage = true,
            captureVideo = true,
            captureAudio = true,
            capturePreview = true,
            triggerCapture = true,
            delete = true,
            preview = true,
            raw = true,
            audio = true,
            exif = true,
            deleteAll = true,
            putFile = true,
            makeDir = true,
            removeDir = true,
            config = true
        )
    )
    val dummyDeviceInfo = com.inik.camcon.domain.model.PtpDeviceInfo(
        manufacturer = "Canon",
        model = "EOS R5",
        version = "1.3.2",
        serialNumber = "123456789012",
    )

    CamConTheme(themeMode = ThemeMode.LIGHT) {
        CameraAbilitiesContent(
            abilities = dummyAbilities,
            deviceInfo = dummyDeviceInfo
        )
    }
}
