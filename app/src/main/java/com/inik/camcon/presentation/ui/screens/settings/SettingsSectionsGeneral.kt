package com.inik.camcon.presentation.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.inik.camcon.R
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.User
import com.inik.camcon.presentation.ui.components.v2.DestructiveRowV2

/**
 * 사용자 정보 섹션 — 프로필, 구독 관리 진입점, 추천 코드 등록, 로그아웃, 계정 삭제.
 * 로그아웃/삭제 진행 중 가드는 호출자([onLogoutClick]/[onDeleteAccountClick])에 캡슐화한다.
 */
@Composable
internal fun UserInfoSection(
    user: User?,
    subscriptionTier: SubscriptionTier,
    isLoggingOut: Boolean,
    onProfileClick: () -> Unit,
    onSubscriptionClick: () -> Unit,
    onReferralRedeemClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_v2_section_user_info)) {
        UserProfileItem(user = user, onClick = onProfileClick)

        // 구독 관리 / 업그레이드 진입점 — 페이월(SubscriptionActivity)로 이동.
        val tierLabelRes = when (subscriptionTier) {
            SubscriptionTier.FREE -> R.string.subscription_tier_free
            SubscriptionTier.BASIC -> R.string.subscription_tier_basic
            SubscriptionTier.PRO -> R.string.subscription_tier_pro
            SubscriptionTier.REFERRER -> R.string.subscription_tier_referrer
            SubscriptionTier.ADMIN -> R.string.subscription_tier_admin
        }
        NavigationRowV2(
            icon = Icons.Default.Update,
            title = stringResource(R.string.settings_v2_subscription_title),
            subtitle = stringResource(
                R.string.settings_v2_subscription_subtitle_tier,
                stringResource(tierLabelRes)
            ),
            onClick = onSubscriptionClick
        )

        // 추천 코드 등록 — 전 티어 노출. 로그인 후 코드를 입력해 혜택(REFERRER)을 받는다.
        ClickableRowV2(
            icon = Icons.Default.Redeem,
            title = stringResource(R.string.settings_referral_redeem_title),
            subtitle = stringResource(R.string.settings_referral_redeem_subtitle),
            onClick = onReferralRedeemClick
        )

        ClickableRowV2(
            icon = Icons.Default.Logout,
            title = if (isLoggingOut) {
                stringResource(R.string.settings_v2_logout_in_progress_title)
            } else {
                stringResource(R.string.settings_v2_logout_title)
            },
            subtitle = if (isLoggingOut) {
                stringResource(R.string.settings_v2_logout_in_progress_subtitle)
            } else {
                stringResource(R.string.settings_v2_logout_subtitle)
            },
            onClick = onLogoutClick
        )
        DestructiveRowV2(
            icon = Icons.Default.DeleteForever,
            title = stringResource(R.string.account_delete_row_title),
            subtitle = stringResource(R.string.account_delete_row_subtitle),
            onClick = onDeleteAccountClick
        )
    }
}

/**
 * 앱 설정 섹션 — 셔터음, 언어, 알림 설정, 배터리 최적화 예외.
 * 시스템 인텐트 실행/토스트는 호출자 콜백에 캡슐화한다.
 */
@Composable
internal fun AppSection(
    isShutterSoundEnabled: Boolean,
    currentLanguageLabel: String,
    isIgnoringBatteryOptimizations: Boolean,
    onShutterSoundChange: (Boolean) -> Unit,
    onLanguageClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onBatteryClick: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_v2_section_app)) {
        SwitchRowV2(
            icon = Icons.Default.CameraAlt,
            title = stringResource(R.string.capture_shutter_sound_label),
            subtitle = stringResource(R.string.capture_shutter_sound_subtitle),
            checked = isShutterSoundEnabled,
            onCheckedChange = onShutterSoundChange
        )
        ClickableRowV2(
            icon = Icons.Default.Language,
            title = stringResource(R.string.language_row_title),
            subtitle = currentLanguageLabel,
            onClick = onLanguageClick
        )
        ClickableRowV2(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.notification_settings_title),
            subtitle = stringResource(R.string.notification_settings_subtitle),
            onClick = onNotificationSettingsClick
        )
        ClickableRowV2(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.settings_v2_battery_title),
            subtitle = if (isIgnoringBatteryOptimizations) {
                stringResource(R.string.settings_v2_battery_on)
            } else {
                stringResource(R.string.settings_v2_battery_off)
            },
            onClick = onBatteryClick
        )
    }
}

/**
 * 정보 섹션 — 오픈소스 라이선스, 앱 버전, 개인정보처리방침·이용약관.
 */
@Composable
internal fun InfoSection(
    appVersion: String,
    onOssLicenseClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onTermsClick: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_v2_section_info)) {
        ClickableRowV2(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_v2_oss_license_title),
            subtitle = stringResource(R.string.settings_v2_oss_license_subtitle),
            onClick = onOssLicenseClick
        )
        ClickableRowV2(
            icon = Icons.Default.Update,
            title = stringResource(R.string.settings_v2_app_version_title),
            subtitle = appVersion,
            onClick = { }
        )
        ClickableRowV2(
            icon = Icons.Default.Security,
            title = stringResource(R.string.privacy_policy),
            subtitle = "",
            onClick = onPrivacyClick
        )
        ClickableRowV2(
            icon = Icons.Default.Description,
            title = stringResource(R.string.terms_of_service),
            subtitle = "",
            onClick = onTermsClick
        )
    }
}
