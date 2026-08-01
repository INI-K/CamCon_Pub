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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import com.inik.camcon.R
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.model.User
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DisplayL
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.MonoReadout
import com.inik.camcon.presentation.theme.OnAccent
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.DividerLineV2
import com.inik.camcon.presentation.ui.components.v2.RowItem
import com.inik.camcon.presentation.ui.components.v2.StatusIndicator
import com.inik.camcon.presentation.ui.components.v2.StatusKind
import kotlin.math.roundToInt

/**
 * 설정 화면 Hero — "지금 무엇이 붙어 있는가".
 *
 * 원격 제어 앱 설정 화면의 존재 이유는 연결 상태 확인이므로, 카메라 모델명을
 * [DisplayL](34sp Bold)로 승격해 화면 유일한 Hero 슬롯으로 둔다. 연결 종류는
 * MicroLabel eyebrow(라틴 대문자 계측기 라벨) + [StatusIndicator] 도트로 계측기 톤을 유지한다.
 * 미연결이면 이름을 [TextTertiary]로 감광해 "값 없음"을 색으로도 말한다.
 */
@Composable
internal fun CameraStatusHero(
    isUsbConnected: Boolean,
    isPtpipConnected: Boolean,
    connectedCameraModel: String?,
    connectedCameraManufacturer: String?,
    modifier: Modifier = Modifier
) {
    val isConnected = isUsbConnected || isPtpipConnected
    val connectionLabel = when {
        isUsbConnected -> stringResource(R.string.settings_v2_connection_type_usb)
        isPtpipConnected -> stringResource(R.string.settings_v2_connection_type_wifi)
        else -> stringResource(R.string.settings_v2_connection_type_none)
    }
    val cameraName = when {
        connectedCameraModel != null && connectedCameraManufacturer != null ->
            "$connectedCameraManufacturer $connectedCameraModel"

        connectedCameraModel != null -> connectedCameraModel
        else -> stringResource(R.string.settings_v3_hero_camera_none)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .padding(top = Spacing.md, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(
            text = stringResource(R.string.settings_v3_hero_eyebrow),
            style = MicroLabel,
            color = TextTertiary
        )
        Text(
            text = cameraName,
            style = DisplayL,
            color = if (isConnected) TextPrimaryV2 else TextTertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        StatusIndicator(
            kind = if (isConnected) StatusKind.Connected else StatusKind.Idle,
            label = connectionLabel
        )
    }
}

/**
 * V2 섹션 컨테이너 — Lightroom 환경설정 톤.
 * 헤더(MicroLabel, TextTertiary 계측기 라벨) + SurfaceV2 tier=1 패널 + RowItem 리스트.
 * 좌우 앵커는 Dense 등급(12dp = [Spacing.md], DESIGN_SYSTEM_V2 §8.3).
 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
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
                // 드래그 중 자릿수가 바뀌어도 폭이 흔들리지 않도록 tnum 모노 판독값으로 둔다.
                // 활성 조작값이므로 라벨(HeadingM)보다 크고 Accent 로 강조.
                Text(
                    text = "${(intensity * 100f).roundToInt()}%",
                    style = MonoReadout,
                    color = Accent
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
 * 수치를 곁들일 때만 [trailing] 에 Mono 계열 readout 을 넘긴다.
 */
@Composable
internal fun ClickableRowV2(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Column {
        RowItem(
            label = title,
            description = subtitle.takeIf { it.isNotEmpty() },
            leadingIcon = icon,
            trailing = trailing,
            onClick = onClick
        )
        DividerLineV2()
    }
}

/**
 * 읽기 전용 정보 행 — [RowItem] 에 onClick 을 넘기지 않아 ripple·Role.Button 이 붙지 않는다.
 * 값이 수치면 [trailing] 에 Mono 계열 readout 을 넣어 자릿수를 고정한다.
 */
@Composable
internal fun StaticRowV2(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Column {
        RowItem(
            label = title,
            description = subtitle.takeIf { it.isNotEmpty() },
            leadingIcon = icon,
            trailing = trailing,
            onClick = null
        )
        DividerLineV2()
    }
}

/**
 * 구독/권한으로 잠긴 행 — 자물쇠 아이콘으로 활성 행과 구분한다.
 * [onUpgradeClick] 이 있으면 페이월로 진입하고, null(구매 불가한 ADMIN 전용 기능 등)이면
 * 정적 행으로 렌더해 눌러도 반응 없는 ripple 을 없앤다.
 */
@Composable
internal fun LockedRowV2(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onUpgradeClick: (() -> Unit)? = null
) {
    Column {
        RowItem(
            label = title,
            description = subtitle.takeIf { it.isNotEmpty() },
            leadingIcon = icon,
            trailing = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(IconSize.sm)
                )
            },
            onClick = onUpgradeClick
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
 * [onClick] 이 null 이면 이동할 곳이 없다는 뜻이므로 chevron·ripple 을 모두 붙이지 않는다.
 */
@Composable
fun UserProfileItem(
    user: User?,
    onClick: (() -> Unit)? = null
) {
    // 이동 대상이 있을 때만 chevron 을 붙인다(정적 행에 방향 지시자를 남기지 않는다).
    val chevron: @Composable (() -> Unit)? = if (onClick == null) {
        null
    } else {
        {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondaryV2,
                modifier = Modifier.size(IconSize.md)
            )
        }
    }
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
            trailing = chevron,
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

@Preview(name = "Settings Hero / USB 연결됨", showBackground = true, backgroundColor = 0xFF050607)
@Composable
private fun CameraStatusHeroConnectedPreview() {
    CamConTheme {
        Column(modifier = Modifier.background(Surface0)) {
            CameraStatusHero(
                isUsbConnected = true,
                isPtpipConnected = false,
                connectedCameraModel = "Z 8",
                connectedCameraManufacturer = "Nikon Corporation"
            )
        }
    }
}

@Preview(name = "Settings Hero / 미연결", showBackground = true, backgroundColor = 0xFF050607)
@Composable
private fun CameraStatusHeroIdlePreview() {
    CamConTheme {
        Column(modifier = Modifier.background(Surface0)) {
            CameraStatusHero(
                isUsbConnected = false,
                isPtpipConnected = false,
                connectedCameraModel = null,
                connectedCameraManufacturer = null
            )
        }
    }
}

@Preview(name = "Settings 필름 강도 행", showBackground = true, backgroundColor = 0xFF0C0E11)
@Composable
private fun FilmIntensityRowPreview() {
    CamConTheme {
        Column(modifier = Modifier.background(Surface1)) {
            FilmIntensityRow(intensity = 0.68f, onChange = {})
        }
    }
}
