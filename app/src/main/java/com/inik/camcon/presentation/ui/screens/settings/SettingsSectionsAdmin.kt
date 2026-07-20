package com.inik.camcon.presentation.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.ui.components.v2.ProgressBarV2

/**
 * 서버 설정 섹션 — 개발 빌드 또는 ADMIN 티어에 노출(게이팅은 호출자 책임). placeholder 행.
 */
@Composable
internal fun ServerSection() {
    SettingsSection(title = stringResource(R.string.settings_v2_section_server)) {
        ClickableRowV2(
            icon = Icons.Default.Storage,
            title = stringResource(R.string.settings_v2_storage_title),
            // TBD: 실제 quota API 연결 전까지 placeholder. 하드코딩 mock 표기 금지.
            subtitle = stringResource(R.string.settings_v2_storage_subtitle),
            onClick = { }
        )
        ClickableRowV2(
            icon = Icons.Default.Security,
            title = stringResource(R.string.settings_v2_permissions_title),
            subtitle = stringResource(R.string.settings_v2_permissions_subtitle),
            onClick = { }
        )
    }
}

/**
 * 관리자 섹션 — Mock 카메라(debug 전용), 카메라 능력, 네이티브 로그 캡처/보기.
 * ADMIN 티어 게이팅은 호출자 책임. Mock 카메라 행은 [showDeveloperFeatures] 에서만 노출.
 */
@Composable
internal fun AdminSection(
    isNativeLogCaptureEnabled: Boolean,
    showDeveloperFeatures: Boolean,
    onMockCameraClick: () -> Unit,
    onCameraAbilitiesClick: () -> Unit,
    onNativeLogCaptureChange: (Boolean) -> Unit,
    onViewLogClick: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_v2_section_admin)) {
        // MockCameraActivity 는 src/debug 전용(릴리스 바이너리 제외) → 정적 참조 금지.
        // debug 빌드에서만 행을 노출하고 리플렉션으로 진입한다.
        if (showDeveloperFeatures) {
            ClickableRowV2(
                icon = Icons.Default.CameraAlt,
                title = stringResource(R.string.settings_v2_mock_camera_title),
                subtitle = stringResource(R.string.settings_v2_mock_camera_subtitle),
                onClick = onMockCameraClick
            )
        }
        ClickableRowV2(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_v2_camera_abilities_title),
            subtitle = stringResource(R.string.settings_v2_camera_abilities_subtitle),
            onClick = onCameraAbilitiesClick
        )
        SwitchRowV2(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_v2_native_log_title),
            subtitle = if (isNativeLogCaptureEnabled) {
                stringResource(R.string.settings_v2_native_log_on)
            } else {
                stringResource(R.string.settings_v2_native_log_off)
            },
            checked = isNativeLogCaptureEnabled,
            onCheckedChange = onNativeLogCaptureChange
        )

        if (isNativeLogCaptureEnabled) {
            ClickableRowV2(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_v2_native_log_view_title),
                subtitle = stringResource(R.string.settings_v2_native_log_view_subtitle),
                onClick = onViewLogClick
            )
        }
    }
}

/**
 * 관리자 레퍼럴 코드 관리 섹션 — 사용 현황/코드 생성/추출.
 * 개발 빌드 또는 ADMIN 티어 게이팅은 호출자 책임.
 */
@Composable
internal fun AdminReferralSection(
    statistics: Map<String, Any>,
    isLoading: Boolean,
    onRefreshClick: () -> Unit,
    onGenerateClick: () -> Unit,
    onExtractClick: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_v2_section_referral)) {
        val totalCodes = statistics["totalCodes"] as? Int ?: 0
        val availableCodes = statistics["availableCodes"] as? Int ?: 0
        val usedCodes = statistics["usedCodes"] as? Int ?: 0
        val usageRate = statistics["usageRate"] as? Int ?: 0

        ClickableRowV2(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_v2_referral_usage_title),
            subtitle = stringResource(
                R.string.settings_v2_referral_usage_subtitle,
                totalCodes,
                availableCodes,
                usedCodes,
                usageRate
            ),
            onClick = onRefreshClick
        )
        ClickableRowV2(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.settings_v2_referral_generate_title),
            subtitle = if (isLoading) {
                stringResource(R.string.settings_v2_referral_generate_in_progress)
            } else {
                stringResource(R.string.settings_v2_referral_generate_subtitle)
            },
            onClick = onGenerateClick
        )
        ClickableRowV2(
            icon = Icons.Default.ContentCopy,
            title = stringResource(R.string.settings_v2_referral_extract_title),
            subtitle = stringResource(R.string.settings_v2_referral_extract_subtitle),
            onClick = onExtractClick
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.base, vertical = Spacing.md)
            ) {
                ProgressBarV2(progress = null)
            }
        }
    }
}
