package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        // 필수3 — 미다운로드/데이터 없음은 안내 상태로 표시(영구 "불러오는 중" 제거).
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
            Log.e("PhotoExifPanel", "Failed to parse EXIF info")
            Text(
                text = stringResource(R.string.fullscreen_viewer_exif_parse_failed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            // i18n 라벨 렌더링은 PhotoInfoBottomSheet.ExifEntriesList 공통 사용(중복 제거).
            ExifEntriesList(exifEntries = exifEntries, modifier = modifier)
        }
    }
}

// ==================== EXIF Formatting Utilities ====================

// EXIF 파싱 및 포맷팅 함수들은 ExifUtils.kt로 중앙화됨 (공유 유틸리티)
// ExifField Composable도 ExifUtils.kt의 공개 버전 사용
