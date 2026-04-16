package com.inik.camcon.presentation.ui.screens.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoInfoBottomSheetContent(
    photo: CameraPhoto,
    viewModel: PhotoPreviewViewModel?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exifInfo = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(true) }

    LaunchedEffect(photo.path) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("PhotoInfoDialog", "EXIF 정보 가져오기 시작: ${photo.path}")

                val info = if (viewModel != null) {
                    viewModel.getCameraPhotoExif(photo.path)
                } else {
                    readExifFromFile(photo.path)
                }

                Log.d("PhotoInfoDialog", "EXIF 정보 가져오기 완료: $info")
                exifInfo.value = info
            } catch (e: Exception) {
                Log.e("PhotoInfoDialog", "EXIF 정보 로드 실패", e)
                exifInfo.value = null
            } finally {
                isLoading.value = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp)
    ) {
        PhotoInfoBottomSheetHandle()

        PhotoInfoBottomSheetHeader()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            PhotoInfoDateRow(photo = photo, exifInfo = exifInfo.value, isLoading = isLoading.value)

            PhotoInfoFileRow(photo = photo, exifInfo = exifInfo.value, isLoading = isLoading.value)

            PhotoInfoExifRow(exifInfo = exifInfo.value, isLoading = isLoading.value)
        }
    }
}

@Composable
private fun PhotoInfoBottomSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    RoundedCornerShape(2.dp)
                )
        )
    }
}

@Composable
private fun PhotoInfoBottomSheetHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.fullscreen_viewer_detail_info),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PhotoInfoDateRow(
    photo: CameraPhoto,
    exifInfo: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val formattedDate by remember(exifInfo, isLoading) {
        derivedStateOf {
            formatPhotoDate(photo, exifInfo, isLoading)
        }
    }

    InfoRow(
        modifier = modifier,
        icon = {
            Icon(
                Icons.Outlined.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        content = {
            Text(
                text = formattedDate,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    )
}

@Composable
private fun PhotoInfoFileRow(
    photo: CameraPhoto,
    exifInfo: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    InfoRow(
        modifier = modifier,
        icon = {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        content = {
            Column {
                Text(
                    text = photo.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                val fileInfo = buildString {
                    append("${String.format("%.2f", photo.size / 1024.0 / 1024.0)}MB")

                    if (!isLoading && !exifInfo.isNullOrEmpty() && exifInfo != "{}") {
                        try {
                            val exifEntries = parseExifInfo(exifInfo)
                            val width = exifEntries["width"]
                            val height = exifEntries["height"]
                            if (width != null && height != null) {
                                append("    ${width}x${height}")
                            }
                        } catch (e: Exception) {
                            Log.d("PhotoInfoFileRow", "Failed to parse EXIF dimensions", e)
                        }
                    }
                }

                Text(
                    text = fileInfo,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val folderPath = photo.path.substringBeforeLast("/")
                    .replace("/storage/emulated/0", "/내장 메모리")

                Text(
                    text = folderPath,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun PhotoInfoExifRow(
    exifInfo: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    InfoRow(
        modifier = modifier,
        icon = {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        content = {
            if (isLoading) {
                Text(
                    text = stringResource(R.string.fullscreen_viewer_exif_loading),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ExifInfoContent(exifInfo = exifInfo)
            }
        }
    )
}

@Composable
fun InfoRow(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        icon()
        content()
    }
}

@Composable
fun ExifInfoContent(
    exifInfo: String?,
    modifier: Modifier = Modifier
) {
    if (exifInfo.isNullOrEmpty() || exifInfo == "{}") {
        Text(
            text = stringResource(R.string.fullscreen_viewer_exif_loading),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        val exifEntries = remember(exifInfo) {
            runCatching { parseExifInfo(exifInfo) }.getOrNull()
        }

        if (exifEntries == null) {
            Log.e("ExifInfoContent", "Failed to parse EXIF info")
            Text(
                text = stringResource(R.string.fullscreen_viewer_exif_parse_failed),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Column(
                modifier = modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val cameraModel = exifEntries["camera_model"]
                if (!cameraModel.isNullOrBlank()) {
                    ExifField("Camera", cameraModel)
                }

                val iso = exifEntries["iso"]
                if (!iso.isNullOrBlank()) {
                    ExifField("ISO", iso)
                }

                val exposureTime = exifEntries["exposure_time"]
                if (!exposureTime.isNullOrBlank()) {
                    ExifField("Shutter Speed", formatShutterSpeed(exposureTime))
                }

                val fNumber = exifEntries["f_number"]
                if (!fNumber.isNullOrBlank()) {
                    ExifField("Aperture", formatAperture(fNumber))
                }

                val focalLength = exifEntries["focal_length"]
                if (!focalLength.isNullOrBlank()) {
                    ExifField("Focal Length", formatFocalLength(focalLength))
                }

                val whiteBalance = exifEntries["white_balance"]
                if (!whiteBalance.isNullOrBlank()) {
                    ExifField("White Balance", formatWhiteBalance(whiteBalance))
                }

                val flash = exifEntries["flash"]
                if (!flash.isNullOrBlank()) {
                    ExifField("Flash", formatFlash(flash))
                }

                val dateTimeOriginal = exifEntries["date_time_original"]
                if (!dateTimeOriginal.isNullOrBlank()) {
                    ExifField("Date", dateTimeOriginal)
                }
            }
        }
    }
}

@Composable
private fun ExifField(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun readExifFromFile(filePath: String): String? {
    return try {
        val file = java.io.File(filePath)
        if (!file.exists()) return null

        val exif = androidx.exifinterface.media.ExifInterface(filePath)
        val exifMap = mutableMapOf<String, Any>()

        exifMap["width"] = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, 0
        )
        exifMap["height"] = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, 0
        )
        exifMap["camera_model"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_MODEL
        ) ?: ""
        exifMap["iso"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY
        ) ?: ""
        exifMap["exposure_time"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME
        ) ?: ""
        exifMap["f_number"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER
        ) ?: ""
        exifMap["focal_length"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH
        ) ?: ""
        exifMap["white_balance"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE
        ) ?: ""
        exifMap["flash"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_FLASH
        ) ?: ""
        exifMap["date_time_original"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL
        ) ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME) ?: ""

        com.google.gson.Gson().toJson(exifMap)
    } catch (e: Exception) {
        Log.e("readExifFromFile", "Failed to read EXIF from file: $filePath", e)
        null
    }
}

private fun formatPhotoDate(
    photo: CameraPhoto,
    exifInfo: String?,
    isLoading: Boolean
): String {
    return if (!isLoading && !exifInfo.isNullOrEmpty() && exifInfo != "{}") {
        try {
            val exifEntries = parseExifInfo(exifInfo)
            val dateTimeOriginal = exifEntries["date_time_original"]

            if (dateTimeOriginal != null) {
                Log.d("PhotoInfoDialog", "EXIF 날짜 원본: $dateTimeOriginal")
                val exifFormat =
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                val displayFormat =
                    SimpleDateFormat("yyyy년 M월 d일 a h:mm", Locale.KOREAN)

                try {
                    val parsedDate = exifFormat.parse(dateTimeOriginal)
                    if (parsedDate != null) {
                        val result = displayFormat.format(parsedDate)
                        Log.d("PhotoInfoDialog", "EXIF 날짜 파싱 성공: $result")
                        result
                    } else {
                        Log.w("PhotoInfoDialog", "EXIF 날짜 파싱 실패, 기본값 사용")
                        "Unknown date"
                    }
                } catch (e: Exception) {
                    Log.e(
                        "PhotoInfoDialog",
                        "EXIF 날짜 파싱 예외: $dateTimeOriginal",
                        e
                    )
                    "Unknown date"
                }
            } else {
                Log.d("PhotoInfoDialog", "EXIF에 date_time_original 없음, 기본값 사용")
                SimpleDateFormat("yyyy년 M월 d일 a h:mm", Locale.KOREAN)
                    .format(Date(photo.date))
            }
        } catch (e: Exception) {
            Log.w("PhotoInfoDialog", "EXIF 정보 파싱 실패", e)
            SimpleDateFormat("yyyy년 M월 d일 a h:mm", Locale.KOREAN)
                .format(Date(photo.date))
        }
    } else {
        if (isLoading) {
            "Loading date..."
        } else {
            SimpleDateFormat("yyyy년 M월 d일 a h:mm", Locale.KOREAN)
                .format(Date(photo.date))
        }
    }
}

private fun parseExifInfo(exifJson: String): Map<String, String> {
    return try {
        com.google.gson.Gson().fromJson(exifJson, Map::class.java) as? Map<String, String>
            ?: emptyMap()
    } catch (e: Exception) {
        Log.e("parseExifInfo", "Failed to parse EXIF JSON", e)
        emptyMap()
    }
}

private fun formatShutterSpeed(exposureTime: String): String {
    return try {
        val value = exposureTime.toDoubleOrNull() ?: return exposureTime
        when {
            value >= 1.0 -> "${value.toInt()}s"
            value >= 0.5 -> "1/${(1 / value).toInt()}s"
            else -> "1/${(1 / value).toInt()}s"
        }
    } catch (e: Exception) {
        exposureTime
    }
}

private fun formatAperture(fNumber: String): String {
    return try {
        val value = fNumber.toDoubleOrNull() ?: return fNumber
        "f/${String.format("%.1f", value)}"
    } catch (e: Exception) {
        fNumber
    }
}

private fun formatFocalLength(focalLength: String): String {
    return try {
        val value = focalLength.toDoubleOrNull() ?: return focalLength
        "${value.toInt()}mm"
    } catch (e: Exception) {
        focalLength
    }
}

private fun formatWhiteBalance(whiteBalance: String): String {
    return when (whiteBalance) {
        "0" -> "Auto"
        "1" -> "Manual"
        "2" -> "Daylight"
        "3" -> "Cloudy"
        "4" -> "Tungsten"
        "5" -> "Fluorescent"
        else -> whiteBalance
    }
}

private fun formatFlash(flash: String): String {
    return when {
        flash.contains("1") -> "Flash On"
        flash.contains("0") -> "Flash Off"
        else -> flash
    }
}
