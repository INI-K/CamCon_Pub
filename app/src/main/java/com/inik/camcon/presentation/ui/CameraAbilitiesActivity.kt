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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inik.camcon.R
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.Body
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.DisplayL
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.HeadingS
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.MonoMicro
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.SuccessV2
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextDisabled
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.theme.WarningV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.ui.components.v2.SkeletonLoader
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
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
            val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()

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
    val abilities by viewModel.abilities.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.base)
                ) {
                    SkeletonLoader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                    SkeletonLoader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        announceLoading = false
                    )
                    SkeletonLoader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        announceLoading = false
                    )
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.base),
                        modifier = Modifier.padding(Spacing.xl)
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
                        PrimaryButton(
                            text = stringResource(R.string.diag_abilities_retry),
                            onClick = { viewModel.refresh() }
                        )
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.base)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(stringResource(R.string.diag_abilities_not_connected))
                        SecondaryButton(
                            text = stringResource(R.string.diag_abilities_refresh),
                            onClick = { viewModel.refresh() }
                        )
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
            .padding(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 히어로 — 이 화면의 존재 이유(어떤 카메라이고 원격제어가 되는가)
        item {
            AbilitiesHero(abilities = abilities, deviceInfo = deviceInfo)
        }

        // 카메라 기본 정보
        item {
            SurfaceV2(tier = 2, border = true, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.diag_abilities_section_camera),
                        style = HeadingM,
                        color = TextPrimaryV2
                    )

                    if (deviceInfo != null) {
                        InfoRow(
                            stringResource(R.string.diag_abilities_label_version),
                            deviceInfo.version,
                            numeric = true
                        )
                        if (deviceInfo.hasValidSerialNumber()) {
                            InfoRow(
                                stringResource(R.string.diag_abilities_label_serial),
                                deviceInfo.serialNumber,
                                numeric = true
                            )
                        }
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
            SurfaceV2(tier = 2, border = true, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.diag_abilities_section_connection),
                        style = HeadingM,
                        color = TextPrimaryV2
                    )

                    val portType = when {
                        abilities.isUsbConnection() -> stringResource(R.string.diag_abilities_value_usb)
                        abilities.isPtpipConnection() -> stringResource(R.string.diag_abilities_value_wifi_ptpip)
                        else -> stringResource(R.string.diag_abilities_value_unknown)
                    }
                    InfoRow(stringResource(R.string.diag_abilities_label_connection_type), portType)
                    InfoRow(
                        stringResource(R.string.diag_abilities_label_usb_vendor),
                        abilities.usbVendor,
                        numeric = true
                    )
                    InfoRow(
                        stringResource(R.string.diag_abilities_label_usb_product),
                        abilities.usbProduct,
                        numeric = true
                    )
                    InfoRow(
                        stringResource(R.string.diag_abilities_label_usb_class),
                        abilities.usbClass.toString(),
                        numeric = true
                    )
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

        // 비트마스크 원본 값 (개발자용) — 폭을 끊어 전폭 카드 나열의 리듬을 깬다
        item {
            SurfaceV2(
                tier = 2,
                border = true,
                modifier = Modifier.wrapContentWidth(Alignment.Start)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.diag_abilities_section_raw),
                        style = HeadingM,
                        color = TextPrimaryV2
                    )

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
            SurfaceV2(tier = 2, border = true, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            imageVector = when {
                                abilities.supports.isFullyControllable() -> Icons.Default.CheckCircle
                                abilities.supports.isDownloadOnly() -> Icons.Default.Warning
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.md),
                            tint = when {
                                abilities.supports.isFullyControllable() -> SuccessV2
                                abilities.supports.isDownloadOnly() -> WarningV2
                                else -> TextSecondaryV2
                            }
                        )
                        Text(
                            text = stringResource(R.string.diag_abilities_section_summary),
                            style = HeadingM,
                            color = TextPrimaryV2
                        )
                    }

                    Text(
                        text = when {
                            abilities.supports.isFullyControllable() ->
                                stringResource(R.string.v3_abilities_summary_full)

                            abilities.supports.isDownloadOnly() ->
                                stringResource(
                                    R.string.v3_abilities_summary_download,
                                    abilities.getManufacturer()
                                )

                            !abilities.supports.capturePreview ->
                                stringResource(R.string.v3_abilities_summary_partial)

                            else ->
                                stringResource(R.string.v3_abilities_summary_some)
                        },
                        style = Body,
                        color = TextSecondaryV2,
                        modifier = Modifier.widthIn(max = READING_WIDTH_MAX)
                    )

                    if (abilities.supports.isFullyControllable()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            listOf(
                                R.string.v3_abilities_cap_capture,
                                R.string.v3_abilities_cap_liveview,
                                R.string.v3_abilities_cap_config,
                                R.string.v3_abilities_cap_files
                            ).forEach { capRes ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = SuccessV2,
                                        modifier = Modifier.size(IconSize.sm)
                                    )
                                    Text(
                                        text = stringResource(capRes),
                                        style = Body,
                                        color = TextPrimaryV2
                                    )
                                }
                            }
                        }
                    }
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
            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun DiagnosticsSection(
    diagnosticsManager: CameraDiagnosticsManager
) {
    val scope = rememberCoroutineScope()
    val report by diagnosticsManager.diagnosticsReport.collectAsStateWithLifecycle()
    val memoryStatus by diagnosticsManager.memoryPoolStatus.collectAsStateWithLifecycle()
    var errorHistory by remember { mutableStateOf<String?>(null) }

    SurfaceV2(tier = 2, border = true, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.v3_diag_section_title),
                style = HeadingM,
                color = TextPrimaryV2
            )

            // 진단 실행
            PrimaryButton(
                text = stringResource(R.string.diag_run_button),
                onClick = { diagnosticsManager.runFullDiagnostics() },
                leadingIcon = Icons.Default.PlayArrow,
                modifier = Modifier.fillMaxWidth()
            )

            // 진단 리포트
            val currentReport = report
            if (currentReport == null) {
                Text(
                    text = stringResource(R.string.diag_report_empty),
                    style = Body,
                    color = TextTertiary,
                    modifier = Modifier.widthIn(max = READING_WIDTH_MAX)
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

            // 에러 히스토리
            Text(
                text = stringResource(R.string.diag_error_history_title),
                style = HeadingS,
                color = TextSecondaryV2
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SecondaryButton(
                    text = stringResource(R.string.diag_error_history_load),
                    onClick = {
                        scope.launch {
                            errorHistory = diagnosticsManager.getErrorHistory(50).getOrDefault("")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = stringResource(R.string.diag_error_history_clear),
                    onClick = {
                        diagnosticsManager.clearErrorHistory()
                        errorHistory = ""
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            errorHistory?.let { history ->
                Text(
                    text = history.ifBlank { stringResource(R.string.diag_error_history_empty) },
                    style = MonoMicro,
                    color = TextTertiary
                )
            }

            // 메모리 풀 상태
            Text(
                text = stringResource(R.string.diag_memory_title),
                style = HeadingS,
                color = TextSecondaryV2
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SecondaryButton(
                    text = stringResource(R.string.diag_memory_refresh),
                    onClick = { diagnosticsManager.refreshMemoryPoolStatus() },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = stringResource(R.string.diag_memory_clear_pool),
                    onClick = { diagnosticsManager.clearCameraFilePool() },
                    modifier = Modifier.weight(1f)
                )
            }
            memoryStatus?.let { status ->
                InfoRow(
                    stringResource(R.string.diag_memory_active_count),
                    status.activeCount.toString(),
                    numeric = true
                )
                InfoRow(
                    stringResource(R.string.diag_memory_total_allocated),
                    status.totalAllocated.toString(),
                    numeric = true
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
    SurfaceV2(tier = 2, border = true, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = title,
                style = HeadingM,
                color = TextPrimaryV2
            )

            features.forEach { feature ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = feature.icon,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.md),
                            tint = if (feature.supported) TextSecondaryV2 else TextDisabled
                        )
                        Text(
                            text = feature.name,
                            style = Body,
                            color = if (feature.supported) TextPrimaryV2 else TextTertiary
                        )
                    }

                    Icon(
                        imageVector = if (feature.supported) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (feature.supported)
                            stringResource(R.string.diag_abilities_feature_supported)
                        else
                            stringResource(R.string.diag_abilities_feature_unsupported),
                        tint = if (feature.supported) SuccessV2 else TextTertiary,
                        modifier = Modifier.size(IconSize.md)
                    )
                }
            }
        }
    }
}

/**
 * 히어로 — 제조사(eyebrow) / 모델명(34sp) / 원격제어 등급(semantic).
 * 화면에서 단 하나의 히어로 슬롯이다.
 */
@Composable
private fun AbilitiesHero(
    abilities: com.inik.camcon.domain.model.CameraAbilitiesInfo,
    deviceInfo: com.inik.camcon.domain.model.PtpDeviceInfo?
) {
    val manufacturer = deviceInfo?.manufacturer?.takeIf { it.isNotBlank() }
        ?: abilities.getManufacturer()
    val model = deviceInfo?.model?.takeIf { it.isNotBlank() } ?: abilities.model

    val gradeRes = when {
        abilities.supports.isFullyControllable() -> R.string.v3_abilities_grade_full
        abilities.supports.isDownloadOnly() -> R.string.v3_abilities_grade_download
        else -> R.string.v3_abilities_grade_partial
    }
    val gradeColor = when {
        abilities.supports.isFullyControllable() -> SuccessV2
        abilities.supports.isDownloadOnly() -> WarningV2
        else -> TextSecondaryV2
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // 제조사는 라틴 문자 전제라 트래킹 라벨을 그대로 쓴다.
        Text(
            text = manufacturer,
            style = MicroLabel,
            color = TextTertiary
        )
        Text(
            text = model,
            style = DisplayL,
            color = TextPrimaryV2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(gradeRes),
            style = Caption,
            color = gradeColor
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, numeric: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = Caption,
            color = TextTertiary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = if (numeric) MonoNumeric else Body,
            color = TextPrimaryV2,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CodeRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MonoMicro,
            color = TextTertiary,
            // 카드가 wrapContentWidth라 weight를 못 쓴다. 최소폭으로 hex 열을 정렬한다.
            modifier = Modifier.widthIn(min = CODE_LABEL_MIN_WIDTH)
        )
        Text(
            text = value,
            style = MonoNumeric,
            color = TextPrimaryV2,
            modifier = Modifier
                .background(
                    color = Surface3,
                    shape = RoundedCornerShape(Radius.sm)
                )
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

private data class FeatureItem(
    val name: String,
    val supported: Boolean,
    val icon: ImageVector
)

/** 장문 설명 최대 폭 — 전폭 문단이 계속 이어지는 것을 끊는다. */
private val READING_WIDTH_MAX = 420.dp

/** 비트마스크 카드의 라벨 열 최소 폭 — hex 값 열을 수직 정렬한다. */
private val CODE_LABEL_MIN_WIDTH = 132.dp

@Preview(showBackground = true, name = "Camera Abilities")
@Composable
private fun Preview_CameraAbilitiesScreen() {
    // 실제 개체에서 읽히는 값에 가깝게 — 미지원 플래그가 섞여야 Close 경로도 프리뷰에서 검증된다.
    val dummyAbilities = com.inik.camcon.domain.model.CameraAbilitiesInfo(
        model = "Canon EOS R5",
        portType = 1,
        usbVendor = "0x04A9",
        usbProduct = "0x3229",
        usbClass = 6,
        operations = 0x000004D3,
        fileOperations = 0x000002F1,
        folderOperations = 0x00000002,
        status = "PRODUCTION",
        supports = com.inik.camcon.domain.model.CameraSupports(
            captureImage = true,
            captureVideo = false,
            captureAudio = false,
            capturePreview = true,
            triggerCapture = true,
            delete = true,
            preview = true,
            raw = true,
            audio = false,
            exif = true,
            deleteAll = false,
            putFile = false,
            makeDir = false,
            removeDir = false,
            config = true
        )
    )
    val dummyDeviceInfo = com.inik.camcon.domain.model.PtpDeviceInfo(
        manufacturer = "Canon Inc.",
        model = "EOS R5",
        version = "1.8.1",
        serialNumber = "043027000418",
    )

    CamConTheme() {
        CameraAbilitiesContent(
            abilities = dummyAbilities,
            deviceInfo = dummyDeviceInfo
        )
    }
}
