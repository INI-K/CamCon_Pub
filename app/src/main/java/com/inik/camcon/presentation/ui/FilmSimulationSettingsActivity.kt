package com.inik.camcon.presentation.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.components.v2.FilterChipV2
import com.inik.camcon.presentation.ui.screens.components.FilmSimulationLivePreview
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.FilmSimulationViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class FilmSimulationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CamConTheme {
                FilmSimulationSettingsScreen(
                    onBackClick = { finish() },
                    appSettingsViewModel = hiltViewModel(),
                    filmSimulationViewModel = hiltViewModel()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilmSimulationSettingsScreen(
    onBackClick: () -> Unit,
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel(),
    filmSimulationViewModel: FilmSimulationViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val isFilmSimulationEnabled by appSettingsViewModel.isFilmSimulationEnabled.collectAsStateWithLifecycle()
    val selectedFilmLutId by appSettingsViewModel.selectedFilmLutId.collectAsStateWithLifecycle()
    val filmSimulationIntensity by appSettingsViewModel.filmSimulationIntensity.collectAsStateWithLifecycle()

    val availableLuts by filmSimulationViewModel.availableLuts.collectAsStateWithLifecycle()
    val targetBitmap by filmSimulationViewModel.targetBitmap.collectAsStateWithLifecycle()
    val lookupBitmap by filmSimulationViewModel.lookupBitmap.collectAsStateWithLifecycle()
    val isExporting by filmSimulationViewModel.isExporting.collectAsStateWithLifecycle()
    val message by filmSimulationViewModel.message.collectAsStateWithLifecycle()

    // 내보내기 경로는 ViewModel 이 소유한다(targetBitmap 과 동일 생명주기 → 회전·재생성 후에도 일치).
    val currentTargetPath by filmSimulationViewModel.targetPath.collectAsStateWithLifecycle()

    // 선택된 LUT 가 바뀌면 룩업 비트맵을 다시 로드한다.
    LaunchedEffect(selectedFilmLutId) {
        filmSimulationViewModel.loadLookup(selectedFilmLutId)
    }

    // 내보내기 결과 토스트
    val exportSuccessText = stringResource(R.string.fs_export_success)
    val exportFailedText = stringResource(R.string.fs_export_failed)
    LaunchedEffect(message) {
        message?.let { token ->
            val text = if (token == FilmSimulationViewModel.MESSAGE_OK) {
                exportSuccessText
            } else {
                exportFailedText
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            filmSimulationViewModel.clearMessage()
        }
    }

    // 시스템 갤러리에서 미리보기 대상 이미지를 가져온다.
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val imageDir = File(context.cacheDir, "film_simulation_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }
                val targetFile = File(imageDir, "film_target_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                filmSimulationViewModel.setTargetImage(targetFile.absolutePath)
            } catch (e: Exception) {
                Log.e("FilmSimulationSettings", "대상 이미지 복사 실패", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.fs_settings_title)) },
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
            if (!isFilmSimulationEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.fs_disabled_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.fs_disabled_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                return@Column
            }

            // a) 안내 카드 — 선택한 LUT 가 촬영 사진에 자동 적용됨
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Text(
                    text = stringResource(R.string.fs_applied_default),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(16.dp)
                )
            }

            // b) 실시간 미리보기 카드
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.fs_preview_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { pickImageLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(stringResource(R.string.fs_pick_target))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FilmSimulationLivePreview(
                        targetBitmap = targetBitmap,
                        lookupBitmap = lookupBitmap,
                        intensity = filmSimulationIntensity,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    )
                }
            }

            // c) 강도 카드 — 로컬 상태 + 300ms 디바운스
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                var localIntensity by remember { mutableStateOf(filmSimulationIntensity) }

                LaunchedEffect(filmSimulationIntensity) {
                    if (kotlin.math.abs(filmSimulationIntensity - localIntensity) > 0.001f) {
                        localIntensity = filmSimulationIntensity
                    }
                }

                LaunchedEffect(localIntensity) {
                    delay(300)
                    if (kotlin.math.abs(localIntensity - filmSimulationIntensity) > 0.001f) {
                        appSettingsViewModel.setFilmSimulationIntensity(localIntensity)
                    }
                }

                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.fs_intensity_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                R.string.fs_intensity_percent,
                                (localIntensity * 100).toInt()
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Slider(
                        value = localIntensity,
                        onValueChange = { localIntensity = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            activeTickColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                        ),
                        modifier = Modifier.height(48.dp)
                    )
                }
            }

            // d) LUT 선택 카드 — 카테고리별 칩
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.fs_select_lut_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // None (원본) 선택지
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChipV2(
                            text = stringResource(R.string.fs_lut_none),
                            selected = selectedFilmLutId.isBlank(),
                            onClick = { appSettingsViewModel.setSelectedFilmLutId("") }
                        )
                    }

                    availableLuts
                        .groupBy { it.category }
                        .toSortedMap()
                        .forEach { (category, luts) ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                luts.forEach { lut ->
                                    FilterChipV2(
                                        text = lut.name,
                                        selected = lut.id == selectedFilmLutId,
                                        onClick = {
                                            appSettingsViewModel.setSelectedFilmLutId(lut.id)
                                        }
                                    )
                                }
                            }
                        }
                }
            }

            // e) 내보내기 버튼
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val exportEnabled = targetBitmap != null &&
                            selectedFilmLutId.isNotBlank() &&
                            !isExporting
                    Button(
                        onClick = {
                            val path = currentTargetPath
                            if (path != null) {
                                filmSimulationViewModel.export(
                                    path,
                                    selectedFilmLutId,
                                    filmSimulationIntensity
                                )
                            }
                        },
                        enabled = exportEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.fs_export))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
