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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.R
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.CameraAbilitiesViewModel
import com.inik.camcon.presentation.viewmodel.CameraDiagnosticsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    // CameraDiagnosticsManager는 @Singleton 이므로 Activity 필드 주입으로 직접 접근 가능
    // (CameraAbilitiesViewModel은 소유 밖이라 거기에 주입하지 않고 매니저를 직접 전달)
    @Inject
    lateinit var diagnosticsManager: CameraDiagnosticsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsState()

            CamConTheme() {
                CameraAbilitiesScreen(
                    diagnosticsManager = diagnosticsManager,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAbilitiesScreen(
    onBackClick: () -> Unit,
    diagnosticsManager: CameraDiagnosticsManager? = null,
    viewModel: CameraAbilitiesViewModel = hiltViewModel()
) {
    val abilities by viewModel.abilities.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.diag_abilities_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.diag_abilities_title)
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
                        Text(stringResource(R.string.diag_abilities_loading))
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
                            Text(stringResource(R.string.diag_abilities_retry))
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
                        Text(stringResource(R.string.diag_abilities_not_connected))
                        OutlinedButton(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.diag_abilities_refresh))
                        }
                    }
                }
            }

            else -> {
                CameraAbilitiesContent(
                    abilities = abilities!!,
                    deviceInfo = deviceInfo,
                    diagnosticsManager = diagnosticsManager,
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
    modifier: Modifier = Modifier,
    diagnosticsManager: CameraDiagnosticsManager? = null
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
                        text = stringResource(R.string.diag_abilities_section_camera),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()

                    if (deviceInfo != null) {
                        InfoRow(stringResource(R.string.diag_abilities_label_manufacturer), deviceInfo.manufacturer)
                        InfoRow(stringResource(R.string.diag_abilities_label_model), deviceInfo.model)
                        InfoRow(stringResource(R.string.diag_abilities_label_version), deviceInfo.version)
                        if (deviceInfo.hasValidSerialNumber()) {
                            InfoRow(stringResource(R.string.diag_abilities_label_serial), deviceInfo.serialNumber)
                        }
                    } else {
                        InfoRow(stringResource(R.string.diag_abilities_label_model), abilities.model)
                    }

                    InfoRow(
                        stringResource(R.string.diag_abilities_label_manufacturer_detected),
                        abilities.getManufacturer()
                    )
                    InfoRow(stringResource(R.string.diag_abilities_label_driver_status), abilities.status)
                    InfoRow(
                        stringResource(R.string.diag_abilities_label_port_type),
                        when (abilities.portType) {
                            1 -> stringResource(R.string.diag_abilities_value_usb)
                            else -> stringResource(R.string.diag_abilities_value_unknown)
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
                        text = stringResource(R.string.diag_abilities_section_connection),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()

                    val portType = when {
                        abilities.isUsbConnection() -> stringResource(R.string.diag_abilities_value_usb)
                        abilities.isPtpipConnection() -> stringResource(R.string.diag_abilities_value_wifi_ptpip)
                        else -> stringResource(R.string.diag_abilities_value_unknown)
                    }
                    InfoRow(stringResource(R.string.diag_abilities_label_connection_type), portType)
                    InfoRow(stringResource(R.string.diag_abilities_label_usb_vendor), abilities.usbVendor)
                    InfoRow(stringResource(R.string.diag_abilities_label_usb_product), abilities.usbProduct)
                    InfoRow(stringResource(R.string.diag_abilities_label_usb_class), abilities.usbClass.toString())
                }
            }
        }

        // 지원 기능 (촬영)
        item {
            FeatureCard(
                title = stringResource(R.string.diag_abilities_section_capture),
                features = listOf(
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_capture_image),
                        abilities.supports.captureImage,
                        Icons.Default.CameraAlt
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_capture_video),
                        abilities.supports.captureVideo,
                        Icons.Default.Videocam
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_capture_audio),
                        abilities.supports.captureAudio,
                        Icons.Default.Mic
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_capture_preview),
                        abilities.supports.capturePreview,
                        Icons.Default.Preview
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_trigger_capture),
                        abilities.supports.triggerCapture,
                        Icons.Default.FlashOn
                    )
                )
            )
        }

        // 지원 기능 (파일)
        item {
            FeatureCard(
                title = stringResource(R.string.diag_abilities_section_file),
                features = listOf(
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_delete),
                        abilities.supports.delete,
                        Icons.Default.Delete
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_preview),
                        abilities.supports.preview,
                        Icons.Default.Visibility
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_raw),
                        abilities.supports.raw,
                        Icons.Default.PhotoLibrary
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_audio),
                        abilities.supports.audio,
                        Icons.Default.AudioFile
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_exif),
                        abilities.supports.exif,
                        Icons.Default.Info
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_delete_all),
                        abilities.supports.deleteAll,
                        Icons.Default.DeleteSweep
                    )
                )
            )
        }

        // 지원 기능 (폴더)
        item {
            FeatureCard(
                title = stringResource(R.string.diag_abilities_section_folder),
                features = listOf(
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_put_file),
                        abilities.supports.putFile,
                        Icons.Default.Upload
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_make_dir),
                        abilities.supports.makeDir,
                        Icons.Default.CreateNewFolder
                    ),
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_remove_dir),
                        abilities.supports.removeDir,
                        Icons.Default.FolderDelete
                    )
                )
            )
        }

        // 설정 기능
        item {
            FeatureCard(
                title = stringResource(R.string.diag_abilities_section_config),
                features = listOf(
                    FeatureItem(
                        stringResource(R.string.diag_abilities_feature_config),
                        abilities.supports.config,
                        Icons.Default.Settings
                    )
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
                        text = stringResource(R.string.diag_abilities_section_raw),
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
                            text = stringResource(R.string.diag_abilities_section_summary),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = when {
                            abilities.supports.isFullyControllable() ->
                                stringResource(R.string.diag_abilities_summary_full)

                            abilities.supports.isDownloadOnly() ->
                                stringResource(
                                    R.string.diag_abilities_summary_download_only,
                                    abilities.getManufacturer(),
                                    abilities.getManufacturer()
                                )

                            !abilities.supports.capturePreview ->
                                stringResource(R.string.diag_abilities_summary_partial)

                            else ->
                                stringResource(R.string.diag_abilities_summary_some)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // 진단 섹션 (ADMIN) — CameraDiagnosticsManager가 주입된 경우에만 노출
        if (diagnosticsManager != null) {
            item {
                DiagnosticsSection(diagnosticsManager = diagnosticsManager)
            }
        }

        // Spacer
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DiagnosticsSection(
    diagnosticsManager: CameraDiagnosticsManager
) {
    val scope = rememberCoroutineScope()
    val report by diagnosticsManager.diagnosticsReport.collectAsState()
    val memoryStatus by diagnosticsManager.memoryPoolStatus.collectAsState()
    var errorHistory by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.diag_section_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()

            // 진단 실행
            Button(
                onClick = { diagnosticsManager.runFullDiagnostics() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.diag_run_button))
            }

            // 진단 리포트
            val currentReport = report
            if (currentReport == null) {
                Text(
                    text = stringResource(R.string.diag_report_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                InfoRow(
                    stringResource(R.string.diag_report_camera_issues),
                    currentReport.cameraIssues
                )
                InfoRow(
                    stringResource(R.string.diag_report_usb),
                    currentReport.usbDiagnostics
                )
            }

            HorizontalDivider()

            // 에러 히스토리
            Text(
                text = stringResource(R.string.diag_error_history_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            errorHistory = diagnosticsManager.getErrorHistory(50).getOrDefault("")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.diag_error_history_load))
                }
                OutlinedButton(
                    onClick = {
                        diagnosticsManager.clearErrorHistory()
                        errorHistory = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.diag_error_history_clear))
                }
            }
            errorHistory?.let { history ->
                Text(
                    text = history.ifBlank { stringResource(R.string.diag_error_history_empty) },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // 메모리 풀 상태
            Text(
                text = stringResource(R.string.diag_memory_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { diagnosticsManager.refreshMemoryPoolStatus() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.diag_memory_refresh))
                }
                OutlinedButton(
                    onClick = { diagnosticsManager.clearCameraFilePool() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.diag_memory_clear_pool))
                }
            }
            memoryStatus?.let { status ->
                InfoRow(
                    stringResource(R.string.diag_memory_active_count),
                    status.activeCount.toString()
                )
                InfoRow(
                    stringResource(R.string.diag_memory_total_allocated),
                    status.totalAllocated.toString()
                )
                if (status.details.isNotBlank()) {
                    InfoRow(stringResource(R.string.diag_memory_details), status.details)
                }
            }
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
                        contentDescription = if (feature.supported)
                            stringResource(R.string.diag_abilities_feature_supported)
                        else
                            stringResource(R.string.diag_abilities_feature_unsupported),
                        tint = if (feature.supported)
                            com.inik.camcon.presentation.theme.Success
                        else
                            com.inik.camcon.presentation.theme.Error,
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

    CamConTheme() {
        CameraAbilitiesContent(
            abilities = dummyAbilities,
            deviceInfo = dummyDeviceInfo
        )
    }
}
