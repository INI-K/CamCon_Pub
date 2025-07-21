package com.inik.camcon.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.ColorTransferLivePreview
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.ColorTransferViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class ColorTransferSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CamConTheme {
                ColorTransferSettingsScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun ColorTransferSettingsScreen(
    onBackClick: () -> Unit,
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel(),
    colorTransferViewModel: ColorTransferViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        android.util.Log.d("ColorTransferSettings", "🎮 GPU 초기화 시작...")
        try {
            android.util.Log.d("ColorTransferSettings", "✅ GPU 초기화 완료")
        } catch (e: Exception) {
            android.util.Log.e("ColorTransferSettings", "❌ GPU 초기화 실패: ${e.message}")
        }
    }
    
    val isColorTransferEnabled by appSettingsViewModel.isColorTransferEnabled.collectAsState()
    val colorTransferReferenceImagePath by appSettingsViewModel.colorTransferReferenceImagePath.collectAsState()
    val colorTransferTargetImagePath by appSettingsViewModel.colorTransferTargetImagePath.collectAsState()
    val colorTransferIntensity by appSettingsViewModel.colorTransferIntensity.collectAsState()
    
    // ColorTransferViewModel 상태
    val isLoading by colorTransferViewModel.isLoading.collectAsState()
    val processingProgress by colorTransferViewModel.processingProgress.collectAsState()
    val processingStatus by colorTransferViewModel.processingStatus.collectAsState()
    val errorMessage by colorTransferViewModel.errorMessage.collectAsState()
    val performanceInfo by colorTransferViewModel.performanceInfo.collectAsState()

    // 이미지 선택 런처들
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

                // 설정에 파일 경로 저장
                appSettingsViewModel.setColorTransferReferenceImagePath(targetFile.absolutePath)

                // 캐시 초기화
                colorTransferViewModel.clearPerformanceInfo()
                colorTransferViewModel.clearProcessingStatus()

            } catch (e: Exception) {
                e.printStackTrace()
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

                // 설정에 파일 경로 저장
                appSettingsViewModel.setColorTransferTargetImagePath(targetFile.absolutePath)

                // 캐시 초기화
                colorTransferViewModel.clearPerformanceInfo()
                colorTransferViewModel.clearProcessingStatus()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("색감 전송 상세 설정") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
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
            // 색감 전송 활성화 상태 표시
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                backgroundColor = if (isColorTransferEnabled) {
                    MaterialTheme.colors.primary.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colors.surface
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "색감 전송 기능",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isColorTransferEnabled) {
                            "✅ 활성화됨 - 촬영된 사진에 참조 이미지의 색감이 자동으로 적용됩니다"
                        } else {
                            "❌ 비활성화됨 - 설정에서 색감 전송 기능을 먼저 활성화해주세요"
                        },
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            if (isColorTransferEnabled) {
                // 실시간 미리보기 영역
                ColorTransferLivePreview(
                    referenceImagePath = colorTransferReferenceImagePath,
                    targetImagePath = colorTransferTargetImagePath,
                    intensity = colorTransferIntensity,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 색감 전송 강도 설정
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "색감 전송 강도",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "현재 강도: ${(colorTransferIntensity * 100).toInt()}%",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary
                        )

                        // 디버깅용 현재 슬라이더 값 표시
                        Text(
                            text = "슬라이더 값: ${(colorTransferIntensity * 100).toInt()}%",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "슬라이더를 움직여서 실시간으로 색감 전송 결과를 확인해보세요",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = (colorTransferIntensity * 100).toInt().toFloat(), // 정수 값으로 변환
                            onValueChange = { newValue ->
                                val intValue = newValue.toInt()
                                android.util.Log.d(
                                    "SliderDebug",
                                    "Slider percentage: $intValue%, Converted: ${intValue / 100f}"
                                )
                                appSettingsViewModel.setColorTransferIntensity(intValue / 100f)
                            },
                            valueRange = 1f..50f, // 1%~50%로 범위 확장
                            // steps 제거하여 부드러운 터치 감도 제공
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colors.primary,
                                activeTrackColor = MaterialTheme.colors.primary
                            )
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "최소 (1%)",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "최대 (50%)",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "강도가 높을수록 참조 이미지의 색감이 강하게 적용됩니다. 자연스러운 결과를 위해 2-5% 권장",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // 고급 설정
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "고급 설정",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 참조 이미지 관리 버튼
                        OutlinedButton(
                            onClick = {
                                referenceImagePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Photo, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("참조 이미지 다시 선택")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 캐시 초기화 버튼
                        OutlinedButton(
                            onClick = {
                                colorTransferViewModel.clearPerformanceInfo()
                                colorTransferViewModel.clearProcessingStatus()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("캐시 초기화")
                        }
                    }
                }

                // 성능 정보 표시
                performanceInfo?.let { info ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        backgroundColor = MaterialTheme.colors.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "성능 정보",
                                style = MaterialTheme.typography.subtitle2,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = info,
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // 에러 메시지 표시
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "오류",
                                style = MaterialTheme.typography.subtitle2,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { colorTransferViewModel.clearError() }
                            ) {
                                Text("확인", color = MaterialTheme.colors.error)
                            }
                        }
                    }
                }
            }
        }
    }
}