package com.inik.camcon.presentation.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.inik.camcon.R
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.model.User
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.OnAccent
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.DividerLineV2
import com.inik.camcon.presentation.ui.components.v2.RowItem
import kotlin.math.roundToInt

/**
 * V2 섹션 컨테이너 — Lightroom 환경설정 톤.
 * 헤더(MicroLabel, TextTertiary 계측기 라벨) + SurfaceV2 tier=1 패널 + RowItem 리스트.
 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.base)) {
        Text(
            // CINE 계측기 라벨 — MicroLabel(11sp Medium, ls1.4). CJK 대응으로 .uppercase() 호출 금지.
            text = title,
            style = MicroLabel,
            color = TextTertiary,
            modifier = Modifier.padding(
                start = Spacing.xs,
                top = Spacing.xl,
                bottom = Spacing.sm
            )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface1)
        ) {
            content()
        }
    }
}

/**
 * V2 RowItem 기반 — 스위치 trailing.
 * Row 자체를 클릭해도 토글되도록 한다.
 */
@Composable
internal fun SwitchRowV2(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column {
        RowItem(
            label = title,
            description = subtitle.takeIf { it.isNotEmpty() },
            leadingIcon = icon,
            trailing = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAccent,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = TextSecondaryV2,
                        uncheckedTrackColor = Surface3
                    )
                )
            },
            onClick = { onCheckedChange(!checked) }
        )
        DividerLineV2()
    }
}

/**
 * 자동적용 강도 슬라이더 행 — SettingsSection 내부 다크 V2 Slider(0..1).
 * 라벨 + 퍼센트 표기 + Material3 Slider. 값 변경은 [onChange] 로 호이스팅.
 */
@Composable
internal fun FilmIntensityRow(
    intensity: Float,
    onChange: (Float) -> Unit
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.base, vertical = Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_v2_film_intensity_title),
                    style = HeadingM,
                    color = TextPrimaryV2
                )
                Text(
                    text = "${(intensity * 100f).roundToInt()}%",
                    style = BodySmall,
                    color = TextSecondaryV2
                )
            }
            Slider(
                value = intensity.coerceIn(0f, 1f),
                onValueChange = onChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Surface3
                )
            )
        }
        DividerLineV2()
    }
}

/**
 * V2 RowItem 기반 — 클릭 가능 행 (chevron 없음).
 */
@Composable
internal fun ClickableRowV2(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column {
        RowItem(
            label = title,
            description = subtitle.takeIf { it.isNotEmpty() },
            leadingIcon = icon,
            onClick = onClick
        )
        DividerLineV2()
    }
}

/**
 * V2 RowItem 기반 — 네비게이션 행 (chevron trailing).
 */
@Composable
internal fun NavigationRowV2(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column {
        RowItem(
            label = title,
            description = subtitle.takeIf { it.isNotEmpty() },
            leadingIcon = icon,
            trailing = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondaryV2,
                    modifier = Modifier.size(IconSize.md)
                )
            },
            onClick = onClick
        )
        DividerLineV2()
    }
}

/**
 * 사용자 프로필 표시 — RowItem 패턴.
 */
@Composable
fun UserProfileItem(
    user: User?,
    onClick: () -> Unit
) {
    Column {
        RowItem(
            label = user?.displayName ?: stringResource(R.string.settings_v2_user_default_name),
            description = user?.email ?: stringResource(R.string.settings_v2_user_login_required),
            leadingContent = {
                if (user?.photoUrl != null) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(IconSize.xl)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TextSecondaryV2,
                        modifier = Modifier.size(IconSize.lg)
                    )
                }
            },
            trailing = {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondaryV2,
                    modifier = Modifier.size(IconSize.md)
                )
            },
            onClick = onClick
        )
    }
}

/**
 * 안내성(비오류) 토스트 오버레이 — CameraControlScreen 의 ToastV2 패턴을 따른다.
 * [message] 가 non-null 이면 상단에서 슬라이드 인 후 3초 뒤 [onDismiss] 로 소멸한다.
 */
@Composable
internal fun AdvisoryToastHost(
    message: String?,
    paddingValues: PaddingValues,
    onDismiss: () -> Unit
) {
    val visible = message != null
    if (message != null) {
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(3000L)
            onDismiss()
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -80 }) +
            androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(260)
            ),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -80 }) +
            androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(260)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(start = Spacing.base, end = Spacing.base, top = Spacing.sm)
        ) {
            com.inik.camcon.presentation.ui.components.v2.ToastV2(
                message = message.orEmpty(),
                kind = com.inik.camcon.presentation.ui.components.v2.StatusKind.Idle,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@StringRes
internal fun LiveViewQuality.labelRes(): Int = when (this) {
    LiveViewQuality.SPEED -> R.string.settings_v2_liveview_quality_speed
    LiveViewQuality.BALANCED -> R.string.settings_v2_liveview_quality_balanced
    LiveViewQuality.QUALITY -> R.string.settings_v2_liveview_quality_quality
}

// 기존 호출처 호환용 별칭 — 외부에서 SettingsItem*을 참조할 수 있어 유지.
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ClickableRowV2(icon, title, subtitle, onClick)
}

@Composable
fun SettingsItemWithSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchRowV2(icon, title, subtitle, checked, onCheckedChange)
}

@Composable
fun SettingsItemWithNavigation(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    NavigationRowV2(icon, title, subtitle, onClick)
}
