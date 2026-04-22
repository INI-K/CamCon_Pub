package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.inik.camcon.R

/**
 * Stateless Composable for displaying EXIF metadata information.
 * Receives parsed EXIF JSON and renders formatted camera/exposure settings.
 */
@Composable
fun PhotoExifPanel(
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
            Log.e("PhotoExifPanel", "Failed to parse EXIF info")
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

// ==================== EXIF Formatting Utilities ====================

// EXIF 파싱 및 포맷팅 함수들은 ExifUtils.kt로 중앙화됨 (공유 유틸리티)
// ExifField Composable도 ExifUtils.kt의 공개 버전 사용
