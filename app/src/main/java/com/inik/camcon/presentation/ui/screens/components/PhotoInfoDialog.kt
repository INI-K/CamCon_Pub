package com.inik.camcon.presentation.ui.screens.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compose 기반 사진 정보 바텀 다이얼로그
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PhotoInfoBottomSheet(
    photo: CameraPhoto,
    viewModel: PhotoPreviewViewModel?,
    bottomSheetState: ModalBottomSheetState,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var exifInfo by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // EXIF 정보 로드
    LaunchedEffect(photo.path) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("PhotoInfoDialog", "EXIF 정보 가져오기 시작: ${photo.path}")
                val info = viewModel?.getCameraPhotoExif(photo.path)
                Log.d("PhotoInfoDialog", "EXIF 정보 가져오기 완료: $info")
                exifInfo = info
            } catch (e: Exception) {
                Log.e("PhotoInfoDialog", "EXIF 정보 로드 실패", e)
                exifInfo = null
            } finally {
                isLoading = false
            }
        }
    }

    ModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp)
            ) {
                // 핸들 바
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(
                                Color.Gray.copy(alpha = 0.3f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }

                // 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    bottomSheetState.hide()
                                    onDismiss()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "뒤로가기",
                                tint = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "상세정보",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    }

                    Text(
                        text = "편집",
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }

                // 스크롤 가능한 컨텐츠
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 날짜/시간 정보
                    InfoRow(
                        icon = {
                            Icon(
                                Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.Black
                            )
                        },
                        content = {
                            val dateFormat = SimpleDateFormat("yyyy년 M월 d일 오후 h:mm", Locale.KOREAN)
                            val formattedDate = try {
                                dateFormat.format(Date(photo.date * 1000L))
                            } catch (e: Exception) {
                                "알 수 없음"
                            }

                            Text(
                                text = formattedDate,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                        }
                    )

                    // 파일 정보
                    InfoRow(
                        icon = {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.Black
                            )
                        },
                        content = {
                            Column {
                                Text(
                                    text = photo.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // 파일 크기와 해상도
                                val fileInfo = buildString {
                                    append(
                                        "${
                                            String.format(
                                                "%.2f",
                                                photo.size / 1024.0 / 1024.0
                                            )
                                        }MB"
                                    )

                                    if (!isLoading && !exifInfo.isNullOrEmpty() && exifInfo != "{}") {
                                        try {
                                            val exifEntries = parseExifInfo(exifInfo!!)
                                            val width = exifEntries["width"]
                                            val height = exifEntries["height"]
                                            if (width != null && height != null) {
                                                append("    ${width}x${height}")
                                            }
                                        } catch (e: Exception) {
                                            // 무시
                                        }
                                    }
                                }

                                Text(
                                    text = fileInfo,
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )

                                // 폴더 경로
                                val folderPath = photo.path.substringBeforeLast("/")
                                    .replace("/storage/emulated/0", "/내장 메모리")

                                Text(
                                    text = folderPath,
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    )

                    // EXIF 정보
                    InfoRow(
                        icon = {
                            Icon(
                                Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.Black
                            )
                        },
                        content = {
                            if (isLoading) {
                                Text(
                                    text = "EXIF 정보 불러오는 중...",
                                    fontSize = 16.sp,
                                    color = Color.Gray
                                )
                            } else {
                                ExifInfoContent(exifInfo = exifInfo)
                            }
                        }
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
private fun InfoRow(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.padding(top = 2.dp)
        ) {
            icon()
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            modifier = Modifier.weight(1f)
        ) {
            content()
        }
    }
}

@Composable
private fun ExifInfoContent(exifInfo: String?) {
    if (exifInfo.isNullOrEmpty() || exifInfo == "{}") {
        Text(
            text = "EXIF 정보가 없습니다",
            fontSize = 16.sp,
            color = Color.Gray
        )
    } else {
        val exifEntries = remember(exifInfo) {
            try {
                parseExifInfo(exifInfo)
            } catch (e: Exception) {
                Log.e("PhotoInfoDialog", "EXIF 파싱 오류", e)
                emptyMap()
            }
        }

        if (exifEntries.isNotEmpty()) {
            Column {
                // 카메라 모델
                val cameraModel = buildString {
                    val make = exifEntries["make"]
                    val model = exifEntries["model"]
                    when {
                        make != null && model != null -> {
                            append("$make $model")
                        }

                        make != null -> append(make)
                        model != null -> append(model)
                        else -> append("알 수 없는 카메라")
                    }
                }

                Text(
                    text = cameraModel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 촬영 설정
                val settings = mutableListOf<String>()
                exifEntries["f_number"]?.let { fNumber ->
                    settings.add(formatAperture(fNumber))
                }
                exifEntries["exposure_time"]?.let { exposureTime ->
                    settings.add(formatShutterSpeed(exposureTime))
                }
                exifEntries["focal_length"]?.let { focalLength ->
                    settings.add(formatFocalLength(focalLength))
                }
                exifEntries["iso"]?.let { iso ->
                    val isoValue = try {
                        val isoNumber = iso.toIntOrNull()
                        if (isoNumber != null) "ISO $isoNumber" else "ISO $iso"
                    } catch (e: Exception) {
                        "ISO $iso"
                    }
                    settings.add(isoValue)
                }

                if (settings.isNotEmpty()) {
                    Text(
                        text = settings.joinToString("    "),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                // 추가 정보 (화이트밸런스, 플래시)
                val additionalInfo = mutableListOf<String>()
                exifEntries["white_balance"]?.let { whiteBalance ->
                    additionalInfo.add("화이트밸런스 ${formatWhiteBalance(whiteBalance)}")
                }
                exifEntries["flash"]?.let { flash ->
                    additionalInfo.add("${formatFlash(flash)}")
                }

                if (additionalInfo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = additionalInfo.joinToString("    "),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            Text(
                text = "EXIF 정보를 파싱할 수 없습니다",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
    }
}

// EXIF 파싱 및 포맷팅 함수들 (기존과 동일)
private fun parseExifInfo(exifJson: String): Map<String, String> {
    val entries = mutableMapOf<String, String>()
    val cleanJson = exifJson.trim().removePrefix("{").removeSuffix("}")
    
    if (cleanJson.isNotEmpty()) {
        val pairs = cleanJson.split(",")
        for (pair in pairs) {
            val keyValue = pair.split(":")
            if (keyValue.size == 2) {
                val key = keyValue[0].trim().removeSurrounding("\"")
                val value = keyValue[1].trim().removeSurrounding("\"")
                entries[key] = value
            }
        }
    }
    return entries
}

private fun formatShutterSpeed(exposureTime: String): String {
    return try {
        val time = exposureTime.toDoubleOrNull()
        when {
            time == null -> exposureTime
            time >= 1.0 -> "${time.toInt()} s"
            time > 0 -> {
                val fraction = 1.0 / time
                "1/${String.format("%.0f", fraction)} s"
            }
            else -> exposureTime
        }
    } catch (e: Exception) {
        exposureTime
    }
}

private fun formatAperture(fNumber: String): String {
    return try {
        val aperture = fNumber.toDoubleOrNull()
        if (aperture != null) {
            "F${String.format("%.1f", aperture)}"
        } else {
            "F$fNumber"
        }
    } catch (e: Exception) {
        "F$fNumber"
    }
}

private fun formatFocalLength(focalLength: String): String {
    return try {
        val focal = focalLength.toDoubleOrNull()
        if (focal != null) {
            "${String.format("%.2f", focal)}mm"
        } else {
            "${focalLength}mm"
        }
    } catch (e: Exception) {
        "${focalLength}mm"
    }
}

private fun formatWhiteBalance(whiteBalance: String): String {
    return when (whiteBalance) {
        "0" -> "자동"
        "1" -> "수동"
        else -> whiteBalance
    }
}

private fun formatFlash(flash: String): String {
    return try {
        val flashValue = flash.toIntOrNull() ?: return flash
        when {
            flashValue and 0x01 == 0 -> "플래시 사용 안 함"
            flashValue and 0x01 == 1 -> "플래시 사용함"
            else -> flash
        }
    } catch (e: Exception) {
        flash
    }
}

/**
 * 사진 정보 다이얼로그를 표시하는 함수 (기존 호환성을 위한 래퍼)
 */
object PhotoInfoDialog {
    @OptIn(ExperimentalMaterialApi::class)
    fun showPhotoInfoDialog(
        context: Context,
        photo: CameraPhoto,
        viewModel: PhotoPreviewViewModel?
    ) {
        Log.d("PhotoInfoDialog", "showPhotoInfoDialog 호출됨: ${photo.name}")
        
        // Compose 다이얼로그를 위한 ComposeView 생성
        val composeView = ComposeView(context)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(composeView)
            .create()

        composeView.setContent {
            val bottomSheetState = rememberModalBottomSheetState(
                initialValue = ModalBottomSheetValue.Expanded
            )

            PhotoInfoBottomSheet(
                photo = photo,
                viewModel = viewModel,
                bottomSheetState = bottomSheetState,
                onDismiss = { dialog.dismiss() }
            ) {
                // 빈 컨텐츠 (바텀시트만 표시)
            }
        }

        dialog.show()
    }
}