package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import android.util.Log
import com.google.gson.Gson
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.HeadingS
import com.inik.camcon.presentation.theme.Micro
import com.inik.camcon.presentation.theme.MonoReadout

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
 * EXIF RATIONAL 문자열("500/10")과 십진 문자열("50.0")을 모두 실수로 파싱한다.
 *
 * ExifInterface.getAttribute 는 RATIONAL 태그(초점거리·노출시간·조리개)를 계산하지 않고
 * "분자/분모" 원문 그대로 돌려준다. 이를 toDoubleOrNull 로만 처리하면 파싱이 실패해
 * 화면에 "500/10" 같은 원시값이 그대로 노출된다(A7C 실측 2026-08-18).
 */
private fun parseExifRational(raw: String): Double? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    val slash = s.indexOf('/')
    if (slash > 0) {
        val num = s.substring(0, slash).trim().toDoubleOrNull() ?: return null
        val den = s.substring(slash + 1).trim().toDoubleOrNull() ?: return null
        if (den == 0.0) return null
        return num / den
    }
    return s.toDoubleOrNull()
}

/**
 * 노출 시간을 읽기 쉬운 셔터 스피드 표기로 변환 (예: "1/1000s")
 */
fun formatShutterSpeed(exposureTime: String): String {
    return try {
        val value = parseExifRational(exposureTime) ?: return exposureTime
        when {
            value <= 0.0 -> exposureTime
            value >= 1.0 -> "${value.toInt()}s"
            else -> "1/${Math.round(1 / value)}s"
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
        val value = parseExifRational(fNumber) ?: return fNumber
        "f/${String.format("%.1f", value)}"
    } catch (e: Exception) {
        fNumber
    }
}

/**
 * 초점거리를 읽기 쉬운 포맷으로 변환 (예: "500/10" → "50mm", "16.5" → "16.5mm")
 */
fun formatFocalLength(focalLength: String): String {
    return try {
        val value = parseExifRational(focalLength) ?: return focalLength
        // 정수면 소수점을 떼고, 아니면 한 자리만 남긴다(35.5mm 같은 실제 렌즈 값 보존).
        if (value == Math.floor(value)) "${value.toInt()}mm"
        else "${String.format("%.1f", value)}mm"
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
 * EXIF Flash 태그에서 '실제 발광 여부'를 판정한다. 판정 불가면 null.
 *
 * Flash 는 단일 코드가 아니라 비트필드이며 **bit0 만** 발광 여부다.
 * 예: 0x10(16)="발광 안 함, 강제발광 모드", 0x18(24)="발광 안 함, 자동 모드",
 *     0x20(32)="플래시 기능 없음", 0x01=발광, 0x09=강제발광으로 발광.
 * 종전 구현은 문자열 contains("1") 로 판정해 16·24 같은 '발광 안 함' 코드를
 * 전부 "사용함"으로 표시했다(A7C 실측 2026-08-18: 플래시 미사용인데 사용함 표기).
 */
private fun exifFlashFired(flash: String): Boolean? {
    val code = flash.trim().toIntOrNull() ?: return null
    return (code and 0x1) != 0
}

/**
 * EXIF 플래시 코드를 읽기 쉬운 문자열로 변환 (bit0 = 발광 여부)
 */
fun formatFlash(flash: String): String {
    return when (exifFlashFired(flash)) {
        true -> "Flash On"
        false -> "Flash Off"
        null -> flash
    }
}

/**
 * EXIF 화이트밸런스 코드를 로컬라이즈된 문자열로 변환(필수3).
 * 인식되지 않는 코드면 원본 값 반환(JNI 코드 노출 방지를 위해 fallback 유지).
 */
@Composable
fun formatWhiteBalanceLabel(whiteBalance: String): String {
    return when (whiteBalance) {
        "0" -> stringResource(R.string.gallery_v2_wb_auto)
        "1" -> stringResource(R.string.gallery_v2_wb_manual)
        "2" -> stringResource(R.string.gallery_v2_wb_daylight)
        "3" -> stringResource(R.string.gallery_v2_wb_cloudy)
        "4" -> stringResource(R.string.gallery_v2_wb_tungsten)
        "5" -> stringResource(R.string.gallery_v2_wb_fluorescent)
        else -> whiteBalance
    }
}

/**
 * EXIF 플래시 코드를 로컬라이즈된 문자열로 변환(필수3).
 */
@Composable
fun formatFlashLabel(flash: String): String {
    return when (exifFlashFired(flash)) {
        true -> stringResource(R.string.gallery_v2_flash_on)
        false -> stringResource(R.string.gallery_v2_flash_off)
        null -> flash
    }
}

/**
 * 라벨과 값을 가진 단일 EXIF 필드 행.
 * Stateless이며, 라벨과 포맷된 값을 모두 전달받는다.
 *
 * 라벨은 계측기 라벨(11sp)로 내리고 값은 판독값으로 올려 크기·패밀리 대비를 만든다.
 * ISO/셔터/조리개처럼 자릿수가 변하는 값은 모노(tnum)라 세로 정렬이 유지되고,
 * 카메라 모델·WB·플래시처럼 로컬라이즈된 낱말 값은 `isNumeric = false` 로 Pretendard 를 쓴다.
 */
@Composable
fun ExifField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = Micro,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isNumeric) MonoReadout else HeadingS,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
