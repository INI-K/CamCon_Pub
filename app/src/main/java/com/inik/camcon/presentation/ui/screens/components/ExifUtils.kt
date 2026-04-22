package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.util.Log
import com.google.gson.Gson

/**
 * EXIF 메타데이터 파싱 및 포맷팅 유틸리티 함수들
 * 여러 Composable에서 공유하는 유틸리티를 중앙화
 */

/**
 * JSON EXIF 문자열을 Map으로 파싱
 * Gson 사용하며 파싱 에러 처리
 */
fun parseExifInfo(exifJson: String): Map<String, String> {
    return try {
        Gson().fromJson(exifJson, Map::class.java) as? Map<String, String>
            ?: emptyMap()
    } catch (e: Exception) {
        Log.e("parseExifInfo", "Failed to parse EXIF JSON", e)
        emptyMap()
    }
}

/**
 * 노출 시간을 읽기 쉬운 셔터 스피드 표기로 변환 (예: "1/1000s")
 */
fun formatShutterSpeed(exposureTime: String): String {
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

/**
 * F-number를 표준 조리개 표기로 변환 (예: "f/2.8")
 */
fun formatAperture(fNumber: String): String {
    return try {
        val value = fNumber.toDoubleOrNull() ?: return fNumber
        "f/${String.format("%.1f", value)}"
    } catch (e: Exception) {
        fNumber
    }
}

/**
 * 초점거리를 읽기 쉬운 포맷으로 변환 (예: "50mm")
 */
fun formatFocalLength(focalLength: String): String {
    return try {
        val value = focalLength.toDoubleOrNull() ?: return focalLength
        "${value.toInt()}mm"
    } catch (e: Exception) {
        focalLength
    }
}

/**
 * EXIF 화이트밸런스 코드를 읽기 쉬운 문자열로 변환
 * 인식되지 않는 코드면 원본 값 반환
 */
fun formatWhiteBalance(whiteBalance: String): String {
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

/**
 * EXIF 플래시 코드를 읽기 쉬운 문자열로 변환
 * "1"은 Flash On, "0"은 Flash Off
 */
fun formatFlash(flash: String): String {
    return when {
        flash.contains("1") -> "Flash On"
        flash.contains("0") -> "Flash Off"
        else -> flash
    }
}

/**
 * Single EXIF field row with label and value.
 * Stateless, receives both label and formatted value.
 */
@Composable
fun ExifField(
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
