package com.inik.camcon.presentation.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.MockCameraViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class MockCameraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContent {
                val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
                val themeMode by appSettingsViewModel.themeMode.collectAsState()

                CamConTheme(themeMode = themeMode) {
                    MockCameraScreen(
                        onBackClick = { finish() }
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MockCameraActivity", "onCreate 실패", e)
            // 크래시를 방지하고 사용자에게 에러 메시지 표시
            android.widget.Toast.makeText(
                this,
                "화면 초기화 실패: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
}

@Composable
fun MockCameraScreen(
    onBackClick: () -> Unit,
    mockCameraViewModel: MockCameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by mockCameraViewModel.uiState.collectAsState()

    // 이미지 선택 런처
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            // URI에서 실제 파일 경로로 변환
            val paths = uris.mapNotNull { uri ->
                try {
                    val imageDir = File(context.filesDir, "mock_camera_images")
                    if (!imageDir.exists()) {
                        imageDir.mkdirs()
                    }

                    val fileName = "mock_${System.currentTimeMillis()}_${uris.indexOf(uri)}.jpg"
                    val targetFile = File(imageDir, fileName)

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(targetFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    targetFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (paths.isNotEmpty()) {
                mockCameraViewModel.addMockImages(paths)
            }
        }
    }

    // 에러 및 성공 메시지 표시
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            mockCameraViewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            mockCameraViewModel.clearSuccessMessage()
        }
    }

    // 딜레이 슬라이더 상태
    var delaySliderValue by remember { mutableStateOf(uiState.delayMs.toFloat()) }

    // 자동 캡처 간격 슬라이더 상태
    var autoIntervalSliderValue by remember { mutableStateOf(uiState.autoCaptureInterval.toFloat()) }

    // 카메라 모델 다이얼로그 상태
    var showCameraModelDialog by remember { mutableStateOf(false) }

    // 예시: 제조사 및 모델 목록
    val cameraManufacturers = listOf("Canon", "Nikon", "Sony", "Fujifilm", "Samsung")
    val cameraModels = mapOf(
        "Canon" to listOf("EOS R5", "EOS 5D Mark IV", "EOS M50"),
        "Nikon" to listOf("D850", "Z7 II", "D3500"),
        "Sony" to listOf("Alpha 7 IV", "Alpha 6400", "RX100 VII"),
        "Fujifilm" to listOf("X-T4", "X-E4", "GFX100S"),
        "Samsung" to listOf("NX500", "NX1", "WB250F")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🧪 Mock Camera 설정") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
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
                .padding(16.dp)
        ) {
            // 활성화 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mock Camera 활성화",
                                style = MaterialTheme.typography.h6
                            )
                            Text(
                                text = if (uiState.isEnabled) {
                                    "가상 카메라가 활성화되었습니다"
                                } else {
                                    "실제 카메라 대신 미리 설정된 이미지 사용"
                                },
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = uiState.isEnabled,
                            onCheckedChange = { mockCameraViewModel.enableMockCamera(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colors.primary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 카메라 모델 선택 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "카메라 모델 선택",
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 제조사 및 모델 선택 UI
                    Button(
                        onClick = { showCameraModelDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Camera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        val selectedText =
                            if (uiState.manufacturer.isNotEmpty() && uiState.cameraModel.isNotEmpty()) {
                                "${uiState.manufacturer} - ${uiState.cameraModel}"
                            } else {
                                "카메라 모델을 선택하세요"
                            }
                        Text(selectedText)
                    }

                    if (uiState.manufacturer.isNotEmpty() && uiState.cameraModel.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "선택된 모델: ${uiState.manufacturer} - ${uiState.cameraModel}",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary
                        )
                    }

                    // 다이얼로그
                    if (showCameraModelDialog) {
                        AlertDialog(
                            onDismissRequest = { showCameraModelDialog = false },
                            title = { Text("카메라 모델 선택") },
                            text = {
                                Column {
                                    Text("제조사 선택", style = MaterialTheme.typography.subtitle1)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    cameraManufacturers.forEach { manufacturer ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = uiState.manufacturer == manufacturer,
                                                onClick = {
                                                    mockCameraViewModel.setMockCameraModel(
                                                        manufacturer,
                                                        cameraModels[manufacturer]?.firstOrNull()
                                                            ?: ""
                                                    )
                                                    showCameraModelDialog = false
                                                }
                                            )
                                            Text(manufacturer)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (uiState.manufacturer.isNotEmpty()) {
                                        Text("모델 선택", style = MaterialTheme.typography.subtitle1)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val models =
                                            cameraModels[uiState.manufacturer].orEmpty()
                                        models.forEach { model ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                RadioButton(
                                                    selected = uiState.cameraModel == model,
                                                    onClick = {
                                                        mockCameraViewModel.setMockCameraModel(
                                                            uiState.manufacturer,
                                                            model
                                                        )
                                                        showCameraModelDialog = false
                                                    }
                                                )
                                                Text(model)
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    enabled = uiState.manufacturer.isNotEmpty() && uiState.cameraModel.isNotEmpty(),
                                    onClick = {
                                        showCameraModelDialog = false
                                    }
                                ) {
                                    Text("선택 완료")
                                }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { showCameraModelDialog = false }) {
                                    Text("취소")
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 이미지 관리 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mock 이미지 관리",
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "등록된 이미지: ${uiState.imageCount}개",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 이미지 추가 버튼
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("이미지 추가")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 모두 삭제 버튼
                    OutlinedButton(
                        onClick = { mockCameraViewModel.clearMockImages() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.imageCount > 0
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("모든 이미지 삭제")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 딜레이 설정 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "캡처 딜레이 설정",
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "현재 딜레이: ${uiState.delayMs}ms",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Slider(
                        value = delaySliderValue,
                        onValueChange = { delaySliderValue = it },
                        onValueChangeFinished = {
                            mockCameraViewModel.setDelay(delaySliderValue.toInt())
                        },
                        valueRange = 0f..5000f,
                        steps = 49, // 100ms 단위
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colors.primary,
                            activeTrackColor = MaterialTheme.colors.primary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0ms", style = MaterialTheme.typography.caption)
                        Text("5000ms", style = MaterialTheme.typography.caption)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 자동 캡처 설정 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "자동 캡처",
                                style = MaterialTheme.typography.h6
                            )
                            Text(
                                text = if (uiState.autoCapture) {
                                    "자동으로 ${uiState.autoCaptureInterval}ms마다 캡처"
                                } else {
                                    "정기적으로 자동 캡처 실행"
                                },
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = uiState.autoCapture,
                            onCheckedChange = {
                                mockCameraViewModel.setAutoCapture(
                                    it,
                                    uiState.autoCaptureInterval
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colors.primary
                            )
                        )
                    }

                    if (uiState.autoCapture) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "캡처 간격: ${uiState.autoCaptureInterval}ms",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = autoIntervalSliderValue,
                            onValueChange = { autoIntervalSliderValue = it },
                            onValueChangeFinished = {
                                mockCameraViewModel.setAutoCapture(
                                    true,
                                    autoIntervalSliderValue.toInt()
                                )
                            },
                            valueRange = 1000f..10000f,
                            steps = 17, // 500ms 단위
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colors.primary,
                                activeTrackColor = MaterialTheme.colors.primary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1초", style = MaterialTheme.typography.caption)
                            Text("10초", style = MaterialTheme.typography.caption)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 에러 시뮬레이션 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "에러 시뮬레이션",
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "테스트용 에러를 시뮬레이션합니다",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            mockCameraViewModel.simulateError(-1, "카메라 초기화 실패")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.error
                        )
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("초기화 에러 시뮬레이션")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            mockCameraViewModel.simulateError(-2, "캡처 타임아웃")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.error
                        )
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("캡처 에러 시뮬레이션")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 새로고침 버튼
            OutlinedButton(
                onClick = { mockCameraViewModel.refreshState() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("상태 새로고침")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 샘플 이미지 자동 생성 버튼
            OutlinedButton(
                onClick = {
                    // assets에서 샘플 이미지 복사
                    try {
                        val imageDir = File(context.filesDir, "mock_camera_images")
                        if (!imageDir.exists()) {
                            imageDir.mkdirs()
                        }

                        val samplePaths = mutableListOf<String>()

                        // drawable 리소스에서 샘플 이미지 생성 (임시)
                        for (i in 1..3) {
                            val fileName = "sample_$i.jpg"
                            val targetFile = File(imageDir, fileName)

                            // 기본 샘플 이미지가 없으면 더미 파일 생성
                            if (!targetFile.exists()) {
                                // 간단한 JPEG 헤더와 데이터 생성 (실제로는 리소스에서 복사)
                                targetFile.createNewFile()
                            }

                            samplePaths.add(targetFile.absolutePath)
                        }

                        if (samplePaths.isNotEmpty()) {
                            mockCameraViewModel.addMockImages(samplePaths)
                            Toast.makeText(
                                context,
                                "샘플 이미지 ${samplePaths.size}개 추가됨",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "샘플 이미지 생성 실패: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("샘플 이미지 생성")
            }

            // 로딩 표시
            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}