package com.inik.camcon.presentation.ui.screens.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import com.inik.camcon.R
import com.inik.camcon.domain.model.LiveViewQuality

/**
 * 라이브뷰 인-LV 화질 순환 컨트롤(도크/칩)용 공용 헬퍼.
 *
 * SettingsActivity의 라벨 매핑/다이얼로그는 private 이라 재사용 불가 → 인-LV 전용 공용 헬퍼를 둔다.
 * 단계 라벨 string은 설정 화면과 동일한 `settings_v2_liveview_quality_*` 를 재사용한다(중복 string 0).
 */

/** SPEED → BALANCED → QUALITY → SPEED 순환. */
fun LiveViewQuality.next(): LiveViewQuality = when (this) {
    LiveViewQuality.SPEED -> LiveViewQuality.BALANCED
    LiveViewQuality.BALANCED -> LiveViewQuality.QUALITY
    LiveViewQuality.QUALITY -> LiveViewQuality.SPEED
}

/** 단계별 구분 아이콘(Material filled). 속도=Speed, 균형=Balance, 품질=HighQuality. */
fun LiveViewQuality.icon(): ImageVector = when (this) {
    LiveViewQuality.SPEED -> Icons.Default.Speed
    LiveViewQuality.BALANCED -> Icons.Default.Balance
    LiveViewQuality.QUALITY -> Icons.Default.HighQuality
}

/** 접근성/배지용 짧은 라벨. 설정 화면과 동일 string 재사용. */
@StringRes
fun LiveViewQuality.shortLabelRes(): Int = when (this) {
    LiveViewQuality.SPEED -> R.string.settings_v2_liveview_quality_speed
    LiveViewQuality.BALANCED -> R.string.settings_v2_liveview_quality_balanced
    LiveViewQuality.QUALITY -> R.string.settings_v2_liveview_quality_quality
}
