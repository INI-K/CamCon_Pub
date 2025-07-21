package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.inik.camcon.presentation.viewmodel.ColorTransferViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 실시간 색감 전송 미리보기 컴포넌트
 */
@Composable
fun ColorTransferLivePreview(
    referenceImagePath: String?,
    targetImagePath: String?,
    intensity: Float,
    modifier: Modifier = Modifier,
    colorTransferViewModel: ColorTransferViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var fullSizeProcessedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isProcessingFullSize by remember { mutableStateOf(false) }
    var lastProcessedIntensity by remember { mutableStateOf(intensity) }
    var isSliderActive by remember { mutableStateOf(false) }
    var showFullSizeImage by remember { mutableStateOf(false) }

    // 디바운싱을 위한 LaunchedEffect
    LaunchedEffect(referenceImagePath, targetImagePath, intensity) {
        if (referenceImagePath != null && targetImagePath != null &&
            File(referenceImagePath).exists() && File(targetImagePath).exists()
        ) {

            // 슬라이더가 활발하게 움직이고 있음을 표시
            isSliderActive = true

            // 500ms 디바운싱 - 0.5초로 변경
            delay(500)

            // 강도가 실제로 변경되었을 때만 처리
            if (kotlin.math.abs(intensity - lastProcessedIntensity) >= 0.0005f) {
                isProcessing = true
                isSliderActive = false

                // 이전 비트맵 캐시 초기화
                processedBitmap?.recycle()
                processedBitmap = null

                try {
                    withContext(Dispatchers.IO) {
                        val processed = processColorTransferPreview(
                            referenceImagePath,
                            targetImagePath,
                            intensity,
                            colorTransferViewModel
                        )
                        processedBitmap = processed
                        lastProcessedIntensity = intensity
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isProcessing = false
                }
            } else {
                isSliderActive = false
            }
        } else {
            // 이미지가 없을 때 캐시 초기화
            processedBitmap?.recycle()
            processedBitmap = null
            fullSizeProcessedBitmap?.recycle()
            fullSizeProcessedBitmap = null
            isSliderActive = false
        }
    }

    // 이미지 경로가 변경될 때 전체 캐시 초기화
    LaunchedEffect(referenceImagePath, targetImagePath) {
        processedBitmap?.recycle()
        processedBitmap = null
        fullSizeProcessedBitmap?.recycle()
        fullSizeProcessedBitmap = null
        lastProcessedIntensity = 0f

        // ColorTransferUseCase 캐시도 초기화
        colorTransferViewModel.clearPerformanceInfo()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                referenceImagePath == null || targetImagePath == null -> {
                    // 이미지가 없을 때
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "실시간 미리보기",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "참조 이미지와 대상 이미지를\n선택하면 미리보기가 표시됩니다",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                isSliderActive -> {
                    // 슬라이더 조작 중일 때
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colors.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "강도: ${(intensity * 100).toInt()}%",
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "슬라이더 조작을 완료하면\n미리보기가 업데이트됩니다",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                isProcessing -> {
                    // 처리 중일 때
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "색감 전송 처리 중...",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            text = "강도: ${(intensity * 100).toInt()}%",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary.copy(alpha = 0.7f)
                        )
                    }
                }

                processedBitmap != null -> {
                    // 처리된 결과만 크게 표시
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "색감 적용 결과 (${(lastProcessedIntensity * 100).toInt()}%)",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 처리된 이미지를 크게 표시 (클릭 가능)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    2.dp,
                                    MaterialTheme.colors.primary,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    // 원본 크기로 색감 처리 시작
                                    if (referenceImagePath != null && targetImagePath != null) {
                                        isProcessingFullSize = true
                                        // 백그라운드에서 원본 크기 처리
                                        coroutineScope.launch {
                                            try {
                                                val fullSize = processColorTransferFullSize(
                                                    referenceImagePath,
                                                    targetImagePath,
                                                    lastProcessedIntensity,
                                                    colorTransferViewModel
                                                )
                                                fullSizeProcessedBitmap = fullSize
                                                showFullSizeImage = true
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            } finally {
                                                isProcessingFullSize = false
                                            }
                                        }
                                    }
                                }
                        ) {
                            Image(
                                bitmap = processedBitmap!!.asImageBitmap(),
                                contentDescription = "색감 적용된 이미지",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // 클릭 힌트 오버레이
                            if (!isProcessingFullSize) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colors.primary.copy(alpha = 0.1f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "👆 탭하여 원본 크기로 보기",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                // 원본 크기 처리 중 표시
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colors.primary.copy(alpha = 0.3f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colors.onPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "원본 크기 처리 중...",
                                            style = MaterialTheme.typography.caption,
                                            color = MaterialTheme.colors.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "참조 이미지의 색감이 적용된 결과입니다",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                else -> {
                    // 대상 이미지만 표시 (참조 이미지 없음)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (targetImagePath != null && File(targetImagePath).exists()) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = ImageRequest.Builder(context)
                                        .data(targetImagePath)
                                        .crossfade(true)
                                        .build()
                                ),
                                contentDescription = "대상 이미지",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "참조 이미지를 선택하면\n색감 전송 미리보기가 표시됩니다",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }

    // 전체 크기 이미지 다이얼로그
    if (showFullSizeImage && fullSizeProcessedBitmap != null) {
        Dialog(
            onDismissRequest = {
                showFullSizeImage = false
                fullSizeProcessedBitmap?.recycle()
                fullSizeProcessedBitmap = null
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = MaterialTheme.colors.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 헤더
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "색감 적용 결과 (원본 크기)",
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.primary
                        )
                        IconButton(
                            onClick = {
                                showFullSizeImage = false
                                fullSizeProcessedBitmap?.recycle()
                                fullSizeProcessedBitmap = null
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "닫기",
                                tint = MaterialTheme.colors.onSurface
                            )
                        }
                    }

                    // 전체 크기 이미지 표시
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        ZoomableImageWithDoubleTap(
                            bitmap = fullSizeProcessedBitmap!!,
                            contentDescription = "원본 크기 색감 적용된 이미지",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

/**
 * 색감 전송 미리보기를 위한 간단한 처리 함수
 * (실제 구현에서는 네이티브 라이브러리나 더 복잡한 알고리즘 사용)
 */
private suspend fun processColorTransferPreview(
    referenceImagePath: String,
    targetImagePath: String,
    intensity: Float,
    colorTransferViewModel: ColorTransferViewModel
): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            // ColorTransferUseCase를 사용하여 색감 전송 처리
            val result = colorTransferViewModel.processColorTransfer(
                referenceImagePath,
                targetImagePath,
                intensity
            )
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/**
 * 원본 크기 색감 전송 처리 함수
 * (실제 구현에서는 네이티브 라이브러리나 더 복잡한 알고리즘 사용)
 */
private suspend fun processColorTransferFullSize(
    referenceImagePath: String,
    targetImagePath: String,
    intensity: Float,
    colorTransferViewModel: ColorTransferViewModel
): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            // ColorTransferUseCase를 사용하여 색감 전송 처리
            val result = colorTransferViewModel.processColorTransferFullSize(
                referenceImagePath,
                targetImagePath,
                intensity
            )
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}