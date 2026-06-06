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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.inik.camcon.presentation.theme.CamConTheme
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
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.ct_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
            // 색감 전송 활성화 상태 표시
            if (!isColorTransferEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_disabled_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.ct_disabled_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (isColorTransferEnabled) {
                // [필수2] 처리 중 진행률 인디케이터 + 상태 텍스트
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stageText,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(
                                        R.string.ct_intensity_percent,
                                        (processingProgress * 100).toInt()
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { processingProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        }
                    }
                }

                // 실시간 미리보기 영역 - 더 크게
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_live_preview_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ColorTransferLivePreview(
                            referenceImagePath = colorTransferReferenceImagePath,
                            targetImagePath = colorTransferTargetImagePath,
                            intensity = colorTransferIntensity,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 색감 전송 강도 설정 - 더 직관적으로
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.ct_intensity_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(
                                    R.string.ct_intensity_percent,
                                    (localIntensity * 100).toInt()
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 더 큰 슬라이더
                        Slider(
                            value = localIntensity,
                            onValueChange = { newValue ->
                                localIntensity = newValue
                            },
                            valueRange = 0.01f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                activeTickColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier.height(48.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.ct_intensity_weak),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = stringResource(R.string.ct_intensity_strong),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 추천 범위 표시
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = stringResource(R.string.ct_intensity_recommended),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 이미지 선택 버튼들 - 더 간결하게
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_image_management),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 참조 이미지 선택 (시스템 갤러리에서 새로 추가)
                            OutlinedButton(
                                onClick = {
                                    referenceImagePickerLauncher.launch("image/*")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Photo,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(stringResource(R.string.ct_reference_image))
                            }

                            // 대상 이미지 선택
                            OutlinedButton(
                                onClick = {
                                    targetImagePickerLauncher.launch("image/*")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Photo,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(stringResource(R.string.ct_target_image))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // [권장3] 등록된 참조 이미지 라이브러리에서 다시 고르기
                        OutlinedButton(
                            onClick = {
                                route = ColorTransferRoute.REFERENCE_PICKER
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(stringResource(R.string.ct_manage_library))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 캐시 초기화
                        OutlinedButton(
                            onClick = {
                                colorTransferViewModel.clearPerformanceInfo()
                                colorTransferViewModel.clearProcessingStatus()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(stringResource(R.string.ct_clear_cache))
                        }
                    }
                }

                // 성능 정보 표시 - 컴팩트하게
                performanceInfo?.let { info ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "⚡",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = info,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // 에러 메시지 표시 - 더 눈에 띄게
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row {
                                Text(
                                    text = "⚠️",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ct_error_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = { colorTransferViewModel.clearError() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.ct_confirm))
                            }
                        }
                    }
                }

                // 하단 여백
                Spacer(modifier = Modifier.height(16.dp))
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
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.ct_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
            if (!isColorTransferEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_disabled_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.ct_disabled_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (isColorTransferEnabled) {
                // 미리보기 플레이스홀더
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_live_preview_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
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
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.ct_live_preview_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.ct_lp_intensity_label,
                                        (colorTransferIntensity * 100).toInt()
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 슬라이더
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.ct_intensity_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(
                                    R.string.ct_intensity_percent,
                                    (colorTransferIntensity * 100).toInt()
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Slider(
                            value = colorTransferIntensity,
                            onValueChange = {},
                            valueRange = 0.01f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                activeTickColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier.height(48.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.ct_intensity_weak),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = stringResource(R.string.ct_intensity_strong),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = stringResource(R.string.ct_intensity_recommended),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 버튼들
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ct_image_management),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {},
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Photo,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(stringResource(R.string.ct_reference_image))
                            }

                            OutlinedButton(
                                onClick = {},
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Photo,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(stringResource(R.string.ct_target_image))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(stringResource(R.string.ct_manage_library))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(stringResource(R.string.ct_clear_cache))
                        }
                    }
                }

                // 성능 정보
                performanceInfo?.let { info ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "⚡",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = info,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // 에러 메시지
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row {
                                Text(
                                    text = "⚠️",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ct_error_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.ct_confirm))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
