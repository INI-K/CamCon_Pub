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
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import com.inik.camcon.utils.LogMask
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
                val info = if (viewModel != null) {
                    viewModel.getCameraPhotoExif(photo.path)
                } else {
                    readExifFromFile(photo.path)
                }

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
            style = HeadingL,
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
    // 필수3 — 날짜 fallback 문자열 i18n (비-Composable formatPhotoDate 에 주입).
    val unknownDate = stringResource(R.string.gallery_v2_date_unknown)
    val loadingDate = stringResource(R.string.fullscreen_viewer_loading_date)
    val formattedDate by remember(exifInfo, isLoading, unknownDate, loadingDate) {
        derivedStateOf {
            formatPhotoDate(photo, exifInfo, isLoading, unknownDate, loadingDate)
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
                style = MaterialTheme.typography.bodyLarge,
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
                    style = MaterialTheme.typography.titleMedium,
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val internalStorageLabel = stringResource(R.string.gallery_v2_internal_storage)
                val folderPath = photo.path.substringBeforeLast("/")
                    .replace("/storage/emulated/0", internalStorageLabel)

                Text(
                    text = folderPath,
                    style = MaterialTheme.typography.bodyMedium,
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
                    style = MaterialTheme.typography.bodyLarge,
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
        // 필수3 — 미다운로드/데이터 없음은 "불러오는 중"이 아니라 안내 상태로 구분 표시.
        Text(
            text = stringResource(R.string.gallery_v2_exif_unavailable),
            style = MaterialTheme.typography.bodyLarge,
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
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            ExifEntriesList(exifEntries = exifEntries, modifier = modifier)
        }
    }
}

/**
 * 파싱된 EXIF 항목을 i18n 라벨로 렌더링(필수3).
 * ExifInfoContent / PhotoExifPanel 공통 사용으로 중복 제거.
 */
@Composable
fun ExifEntriesList(
    exifEntries: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val cameraModel = exifEntries["camera_model"]
        if (!cameraModel.isNullOrBlank()) {
            ExifField(stringResource(R.string.gallery_v2_exif_camera), cameraModel)
        }

        val iso = exifEntries["iso"]
        if (!iso.isNullOrBlank()) {
            ExifField(stringResource(R.string.gallery_v2_exif_iso), iso)
        }

        val exposureTime = exifEntries["exposure_time"]
        if (!exposureTime.isNullOrBlank()) {
            ExifField(
                stringResource(R.string.gallery_v2_exif_shutter),
                formatShutterSpeed(exposureTime)
            )
        }

        val fNumber = exifEntries["f_number"]
        if (!fNumber.isNullOrBlank()) {
            ExifField(stringResource(R.string.gallery_v2_exif_aperture), formatAperture(fNumber))
        }

        val focalLength = exifEntries["focal_length"]
        if (!focalLength.isNullOrBlank()) {
            ExifField(
                stringResource(R.string.gallery_v2_exif_focal_length),
                formatFocalLength(focalLength)
            )
        }

        val whiteBalance = exifEntries["white_balance"]
        if (!whiteBalance.isNullOrBlank()) {
            ExifField(
                stringResource(R.string.gallery_v2_exif_white_balance),
                formatWhiteBalanceLabel(whiteBalance)
            )
        }

        val flash = exifEntries["flash"]
        if (!flash.isNullOrBlank()) {
            ExifField(stringResource(R.string.gallery_v2_exif_flash), formatFlashLabel(flash))
        }

        val dateTimeOriginal = exifEntries["date_time_original"]
        if (!dateTimeOriginal.isNullOrBlank()) {
            ExifField(stringResource(R.string.gallery_v2_exif_date), dateTimeOriginal)
        }
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
        Log.e("readExifFromFile", "Failed to read EXIF from file: ${LogMask.path(filePath)}", e)
        null
    }
}

private fun formatPhotoDate(
    photo: CameraPhoto,
    exifInfo: String?,
    isLoading: Boolean,
    unknownDate: String,
    loadingDate: String
): String {
    // 표시 포맷은 기기 로케일을 따른다(필수3: 한국어 고정 제거).
    val displayFormat = SimpleDateFormat("yyyy.MM.dd a h:mm", Locale.getDefault())
    return if (!isLoading && !exifInfo.isNullOrEmpty() && exifInfo != "{}") {
        try {
            val exifEntries = parseExifInfo(exifInfo)
            val dateTimeOriginal = exifEntries["date_time_original"]

            if (dateTimeOriginal != null) {
                val exifFormat =
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())

                try {
                    val parsedDate = exifFormat.parse(dateTimeOriginal)
                    if (parsedDate != null) {
                        displayFormat.format(parsedDate)
                    } else {
                        unknownDate
                    }
                } catch (e: Exception) {
                    Log.e("PhotoInfoDialog", "EXIF 날짜 파싱 예외", e)
                    unknownDate
                }
            } else {
                displayFormat.format(Date(photo.date))
            }
        } catch (e: Exception) {
            Log.w("PhotoInfoDialog", "EXIF 정보 파싱 실패", e)
            displayFormat.format(Date(photo.date))
        }
    } else {
        if (isLoading) {
            loadingDate
        } else {
            displayFormat.format(Date(photo.date))
        }
    }
}
