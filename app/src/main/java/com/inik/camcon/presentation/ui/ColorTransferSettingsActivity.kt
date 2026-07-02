package com.inik.camcon.presentation.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.MonoReadout
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.ui.screens.ColorTransferImagePickerScreen
import com.inik.camcon.presentation.ui.screens.components.ColorTransferLivePreview
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.ColorTransferStage
import com.inik.camcon.presentation.viewmodel.ColorTransferViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.delay

@AndroidEntryPoint
class ColorTransferSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()

            CamConTheme() {
                ColorTransferSettingsScreen(
                    onBackClick = { finish() },
                    appSettingsViewModel = appSettingsViewModel,
                    colorTransferViewModel = hiltViewModel()
                )
            }
        }
    }
}

/**
 * 색감 전송 상세 설정 화면 내부 라우팅 상태.
 * 새 Activity/매니페스트 등록 없이 Composable 토글로 참조 이미지 라이브러리를 노출한다.
 */
private enum class ColorTransferRoute {
    SETTINGS,
    REFERENCE_PICKER
}

@Composable
fun ColorTransferSettingsScreen(
    onBackClick: () -> Unit,
    appSettingsViewModel: AppSettingsViewModel,
    colorTransferViewModel: ColorTransferViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var route by rememberSaveable { mutableStateOf(ColorTransferRoute.SETTINGS) }

    val isColorTransferEnabled by appSettingsViewModel.isColorTransferEnabled.collectAsStateWithLifecycle()
    val colorTransferReferenceImagePath by appSettingsViewModel.colorTransferReferenceImagePath.collectAsStateWithLifecycle()
    val colorTransferTargetImagePath by appSettingsViewModel.colorTransferTargetImagePath.collectAsStateWithLifecycle()
    val colorTransferIntensity by appSettingsViewModel.colorTransferIntensity.collectAsStateWithLifecycle()

    // ColorTransferViewModel 상태
    val isLoading by colorTransferViewModel.isLoading.collectAsStateWithLifecycle()
    val processingProgress by colorTransferViewModel.processingProgress.collectAsStateWithLifecycle()
    val processingStage by colorTransferViewModel.processingStage.collectAsStateWithLifecycle()
    val errorMessage by colorTransferViewModel.errorMessage.collectAsStateWithLifecycle()
    val performanceInfo by colorTransferViewModel.performanceInfo.collectAsStateWithLifecycle()

    // [권장3] 참조 이미지 라이브러리 화면을 내부 토글로 노출.
    if (route == ColorTransferRoute.REFERENCE_PICKER) {
        ColorTransferImagePickerScreen(
            onBackClick = { route = ColorTransferRoute.SETTINGS },
            onImageSelected = { selectedPath ->
                // 이미 앱 내부에 저장된 라이브러리 이미지이므로 중복 복사 없이 경로만 지정한다.
                appSettingsViewModel.setColorTransferReferenceImagePath(selectedPath)
                colorTransferViewModel.clearPerformanceInfo()
                colorTransferViewModel.clearProcessingStatus()
                route = ColorTransferRoute.SETTINGS
            },
            viewModel = colorTransferViewModel
        )
        return
    }

    // 시스템 갤러리에서 새 이미지를 가져오는 런처들
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

                colorTransferViewModel.clearPerformanceInfo()
                colorTransferViewModel.clearProcessingStatus()
            } catch (e: Exception) {
                Log.e("ColorTransferSettings", "참조 이미지 복사 실패", e)
            }
        }
    }

    val targetImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
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

                appSettingsViewModel.setColorTransferTargetImagePath(targetFile.absolutePath)

                colorTransferViewModel.clearPerformanceInfo()
                colorTransferViewModel.clearProcessingStatus()
            } catch (e: Exception) {
                Log.e("ColorTransferSettings", "대상 이미지 복사 실패", e)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text(stringResource(R.string.ct_settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Surface0,
                        titleContentColor = TextPrimaryV2,
                        navigationIconContentColor = TextPrimaryV2
                    )
                )
                // 앱바-컨텐츠 경계 헤어라인(순흑끼리 경계 소실 방지, CINE 계기판 언어).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StrokeWidth.hairline)
                        .background(DividerLine)
                )
            }
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
            // 색감 전송 비활성화 안내 — flat SurfaceV2 + 헤어라인(Elevation 0 정책).
            if (!isColorTransferEnabled) {
                SurfaceV2(
                    tier = 1,
                    border = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.base)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.base)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_disabled_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryV2
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.ct_disabled_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    }
                }
            }

            if (isColorTransferEnabled) {
                // [필수2] 처리 중 진행률 인디케이터 + 상태 텍스트 — flat SurfaceV2(tier 2) + 헤어라인.
                val isProcessingInProgress = isLoading &&
                        processingStage != null &&
                        processingStage != ColorTransferStage.DONE
                AnimatedVisibility(visible = isProcessingInProgress) {
                    val stageText = when (processingStage) {
                        ColorTransferStage.PREVIEW_PROCESSING ->
                            stringResource(R.string.ct_processing_title)
                        ColorTransferStage.FULL_SIZE_PREPARING ->
                            stringResource(R.string.ct_lp_processing_full)
                        ColorTransferStage.FULL_SIZE_APPLYING ->
                            stringResource(R.string.ct_processing_title)
                        else -> stringResource(R.string.ct_processing_title)
                    }
                    SurfaceV2(
                        tier = 2,
                        border = true,
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.base, vertical = Spacing.sm)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.base)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stageText,
                                    style = MicroLabel,
                                    color = Accent
                                )
                                Text(
                                    text = stringResource(
                                        R.string.ct_intensity_percent,
                                        (processingProgress * 100).toInt()
                                    ),
                                    style = MonoReadout,
                                    color = Accent
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.md))
                            LinearProgressIndicator(
                                progress = { processingProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = Accent,
                                trackColor = DividerLine
                            )
                        }
                    }
                }

                // 실시간 미리보기 영역 — flat SurfaceV2 + 헤어라인. 프리뷰 기능 불변, 스타일만.
                SurfaceV2(
                    tier = 1,
                    border = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.base)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.base)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_live_preview_title),
                            style = MicroLabel,
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        ColorTransferLivePreview(
                            referenceImagePath = colorTransferReferenceImagePath,
                            targetImagePath = colorTransferTargetImagePath,
                            intensity = colorTransferIntensity,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 색감 전송 강도 설정 — flat SurfaceV2 + 헤어라인. 강도 슬라이더 기능/디바운스 불변.
                SurfaceV2(
                    tier = 1,
                    border = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.base, vertical = Spacing.sm)
                ) {
                    // 로컬 상태로 즉시 반응
                    var localIntensity by remember { mutableStateOf(colorTransferIntensity) }

                    // ViewModel 값이 변경되면 로컬 상태도 업데이트
                    LaunchedEffect(colorTransferIntensity) {
                        if ((colorTransferIntensity - localIntensity).let { kotlin.math.abs(it) } > 0.001f) {
                            localIntensity = colorTransferIntensity
                        }
                    }

                    // 디바운싱: 로컬 상태 변경 후 300ms 후에 ViewModel 업데이트
                    LaunchedEffect(localIntensity) {
                        delay(300)
                        if ((localIntensity - colorTransferIntensity).let { kotlin.math.abs(it) } > 0.001f) {
                            appSettingsViewModel.setColorTransferIntensity(localIntensity)
                        }
                    }

                    Column(
                        modifier = Modifier.padding(Spacing.lg)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.ct_intensity_title),
                                style = MicroLabel,
                                color = TextTertiary
                            )
                            // 전송 강도 판독 = MonoReadout, 앰버.
                            Text(
                                text = stringResource(
                                    R.string.ct_intensity_percent,
                                    (localIntensity * 100).toInt()
                                ),
                                style = MonoReadout,
                                color = Accent
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.lg))

                        // 강도 슬라이더 (얇은 트랙 + 앰버 필)
                        val intensitySliderColors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            activeTickColor = Accent,
                            inactiveTrackColor = DividerLine
                        )
                        Slider(
                            value = localIntensity,
                            onValueChange = { newValue ->
                                localIntensity = newValue
                            },
                            valueRange = 0.01f..1.0f,
                            colors = intensitySliderColors,
                            // 비활성 트랙 끝 stop indicator(앰버 점) 제거 — 썸으로 오인 방지.
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    colors = intensitySliderColors,
                                    drawStopIndicator = null
                                )
                            },
                            modifier = Modifier.height(48.dp)
                        )

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.ct_intensity_weak),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                            Text(
                                text = stringResource(R.string.ct_intensity_strong),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        // 추천 범위 표시
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = null,
                                tint = Accent.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = Spacing.xs)
                            )
                            Text(
                                text = stringResource(R.string.ct_intensity_recommended),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Accent.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 이미지 선택 버튼들 — flat SurfaceV2 + 헤어라인, V2 SecondaryButton.
                SurfaceV2(
                    tier = 1,
                    border = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.base, vertical = Spacing.sm)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.base)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_image_management),
                            style = MicroLabel,
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // 참조 이미지 선택 (시스템 갤러리에서 새로 추가)
                            SecondaryButton(
                                text = stringResource(R.string.ct_reference_image),
                                onClick = { referenceImagePickerLauncher.launch("image/*") },
                                leadingIcon = Icons.Default.Photo,
                                modifier = Modifier.weight(1f)
                            )

                            // 대상 이미지 선택
                            SecondaryButton(
                                text = stringResource(R.string.ct_target_image),
                                onClick = { targetImagePickerLauncher.launch("image/*") },
                                leadingIcon = Icons.Default.Photo,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // [권장3] 등록된 참조 이미지 라이브러리에서 다시 고르기
                        SecondaryButton(
                            text = stringResource(R.string.ct_manage_library),
                            onClick = { route = ColorTransferRoute.REFERENCE_PICKER },
                            leadingIcon = Icons.Default.PhotoLibrary,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // 캐시 초기화
                        SecondaryButton(
                            text = stringResource(R.string.ct_clear_cache),
                            onClick = {
                                colorTransferViewModel.clearPerformanceInfo()
                                colorTransferViewModel.clearProcessingStatus()
                            },
                            leadingIcon = Icons.Default.Clear,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 성능 정보 표시 — flat SurfaceV2 + 헤어라인.
                performanceInfo?.let { info ->
                    SurfaceV2(
                        tier = 1,
                        border = true,
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.base, vertical = Spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md)
                        ) {
                            Text(
                                text = "⚡",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(end = Spacing.sm)
                            )
                            Text(
                                text = info,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        }
                    }
                }

                // 에러 메시지 표시 — flat SurfaceV2 + 헤어라인.
                errorMessage?.let { error ->
                    SurfaceV2(
                        tier = 1,
                        border = true,
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.base, vertical = Spacing.sm)
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.base)
                        ) {
                            Row {
                                Text(
                                    text = "⚠️",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(end = Spacing.sm)
                                )
                                Text(
                                    text = stringResource(R.string.ct_error_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorV2
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ErrorV2
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            TextButton(
                                onClick = { colorTransferViewModel.clearError() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.ct_confirm),
                                    style = MicroLabel,
                                    color = Accent
                                )
                            }
                        }
                    }
                }

                // 하단 여백
                Spacer(modifier = Modifier.height(Spacing.base))
            }
        }
    }
}

/**
 * 프리뷰용 간단한 화면 구성
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorTransferSettingsScreenPreview(
    isColorTransferEnabled: Boolean,
    colorTransferIntensity: Float,
    errorMessage: String? = null,
    performanceInfo: String? = null
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text(stringResource(R.string.ct_settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Surface0,
                        titleContentColor = TextPrimaryV2,
                        navigationIconContentColor = TextPrimaryV2
                    )
                )
                // 앱바-컨텐츠 경계 헤어라인(순흑끼리 경계 소실 방지, CINE 계기판 언어).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StrokeWidth.hairline)
                        .background(DividerLine)
                )
            }
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
            if (!isColorTransferEnabled) {
                SurfaceV2(
                    tier = 1,
                    border = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.base)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.base)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_disabled_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryV2
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.ct_disabled_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    }
                }
            }

            if (isColorTransferEnabled) {
                // 미리보기 플레이스홀더
                SurfaceV2(
                    tier = 1,
                    border = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.base)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.base)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_live_preview_title),
                            style = MicroLabel,
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .background(
                                    Surface0,
                                    RoundedCornerShape(Radius.sm)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = TextTertiary
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = stringResource(R.string.ct_live_preview_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextTertiary
                                )
                                Text(
                                    text = stringResource(
                                        R.string.ct_lp_intensity_label,
                                        (colorTransferIntensity * 100).toInt()
                                    ),
                                    style = MonoReadout,
                                    color = Accent
                                )
                            }
                        }
                    }
                }

                // 슬라이더
                SurfaceV2(
                    tier = 1,
                    border = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.base, vertical = Spacing.sm)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.ct_intensity_title),
                                style = MicroLabel,
                                color = TextTertiary
                            )
                            Text(
                                text = stringResource(
                                    R.string.ct_intensity_percent,
                                    (colorTransferIntensity * 100).toInt()
                                ),
                                style = MonoReadout,
                                color = Accent
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.lg))

                        val previewSliderColors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            activeTickColor = Accent,
                            inactiveTrackColor = DividerLine
                        )
                        Slider(
                            value = colorTransferIntensity,
                            onValueChange = {},
                            valueRange = 0.01f..1.0f,
                            colors = previewSliderColors,
                            // 비활성 트랙 끝 stop indicator(앰버 점) 제거 — 썸으로 오인 방지.
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    colors = previewSliderColors,
                                    drawStopIndicator = null
                                )
                            },
                            modifier = Modifier.height(48.dp)
                        )

                        Spacer(modifier = Modifier.height(Spacing.xs))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.ct_intensity_weak),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                            Text(
                                text = stringResource(R.string.ct_intensity_strong),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = null,
                                tint = Accent.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = Spacing.xs)
                            )
                            Text(
                                text = stringResource(R.string.ct_intensity_recommended),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Accent.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 버튼들
                SurfaceV2(
                    tier = 1,
                    border = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.base, vertical = Spacing.sm)
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.base)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_image_management),
                            style = MicroLabel,
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            SecondaryButton(
                                text = stringResource(R.string.ct_reference_image),
                                onClick = {},
                                leadingIcon = Icons.Default.Photo,
                                modifier = Modifier.weight(1f)
                            )

                            SecondaryButton(
                                text = stringResource(R.string.ct_target_image),
                                onClick = {},
                                leadingIcon = Icons.Default.Photo,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        SecondaryButton(
                            text = stringResource(R.string.ct_manage_library),
                            onClick = {},
                            leadingIcon = Icons.Default.PhotoLibrary,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        SecondaryButton(
                            text = stringResource(R.string.ct_clear_cache),
                            onClick = {},
                            leadingIcon = Icons.Default.Clear,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 성능 정보
                performanceInfo?.let { info ->
                    SurfaceV2(
                        tier = 1,
                        border = true,
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.base, vertical = Spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md)
                        ) {
                            Text(
                                text = "⚡",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(end = Spacing.sm)
                            )
                            Text(
                                text = info,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        }
                    }
                }

                // 에러 메시지
                errorMessage?.let { error ->
                    SurfaceV2(
                        tier = 1,
                        border = true,
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.base, vertical = Spacing.sm)
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.base)
                        ) {
                            Row {
                                Text(
                                    text = "⚠️",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(end = Spacing.sm)
                                )
                                Text(
                                    text = stringResource(R.string.ct_error_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorV2
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ErrorV2
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            TextButton(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.ct_confirm),
                                    style = MicroLabel,
                                    color = Accent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.base))
            }
        }
    }
}

/**
 * 비활성화 상태 프리뷰
 */
@Preview(name = "비활성화 상태", showSystemUi = true)
@Composable
fun PreviewDisabled() {
    CamConTheme {
        ColorTransferSettingsScreenPreview(
            isColorTransferEnabled = false,
            colorTransferIntensity = 0.05f
        )
    }
}

/**
 * 낮은 강도 프리뷰
 */
@Preview(name = "낮은 강도 (5%)", showSystemUi = true)
@Composable
fun PreviewLowIntensity() {
    CamConTheme {
        ColorTransferSettingsScreenPreview(
            isColorTransferEnabled = true,
            colorTransferIntensity = 0.05f
        )
    }
}

/**
 * 중간 강도 프리뷰
 */
@Preview(name = "중간 강도 (25%)", showSystemUi = true)
@Composable
fun PreviewMediumIntensity() {
    CamConTheme {
        ColorTransferSettingsScreenPreview(
            isColorTransferEnabled = true,
            colorTransferIntensity = 0.25f,
            performanceInfo = "처리 시간: 45ms\nGPU 사용: 65%\n메모리: 128MB"
        )
    }
}

/**
 * 높은 강도 프리뷰
 */
@Preview(name = "높은 강도 (75%)", showSystemUi = true)
@Composable
fun PreviewHighIntensity() {
    CamConTheme {
        ColorTransferSettingsScreenPreview(
            isColorTransferEnabled = true,
            colorTransferIntensity = 0.75f,
            performanceInfo = "처리 시간: 52ms\nGPU 사용: 78%"
        )
    }
}

/**
 * 에러 상태 프리뷰
 */
@Preview(name = "에러 상태", showSystemUi = true)
@Composable
fun PreviewError() {
    CamConTheme {
        ColorTransferSettingsScreenPreview(
            isColorTransferEnabled = true,
            colorTransferIntensity = 0.15f,
            errorMessage = "GPU 메모리 부족: 이미지 크기를 줄여주세요"
        )
    }
}

/**
 * 다크 모드 프리뷰
 */
@Preview(
    name = "다크 모드",
    showSystemUi = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewDarkMode() {
    CamConTheme {
        ColorTransferSettingsScreenPreview(
            isColorTransferEnabled = true,
            colorTransferIntensity = 0.10f,
            performanceInfo = "처리 시간: 38ms\nGPU 사용: 58%"
        )
    }
}
