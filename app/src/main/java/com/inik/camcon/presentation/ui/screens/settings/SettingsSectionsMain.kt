package com.inik.camcon.presentation.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.inik.camcon.R
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.utils.resolve

/**
 * 카메라 제어 설정 섹션 — 노출 여부 판정은 호출자 책임.
 *
 * [isLiveViewAllowed] 가 true 면 라이브뷰/화질/자동 이벤트를, false 면 잠금 안내 + 자동
 * 이벤트를 표시한다. 예전엔 이 판정이 `isAdminTier` 였는데, 라이브뷰를 PRO 이상에 열면서
 * (2026-08-20) 판정만 바뀌고 UI 가 ADMIN 에 묶여 있어 REFERRER·PRO 가 토글을 볼 수 없었다.
 */
@Composable
internal fun CameraControlSection(
    isCameraControlsEnabled: Boolean,
    isLiveViewAllowed: Boolean,
    isLiveViewEnabled: Boolean,
    liveViewQuality: LiveViewQuality,
    isAutoStartEventListener: Boolean,
    isShowLatestPhotoWhenDisabled: Boolean,
    onCameraControlsChange: (Boolean) -> Unit,
    onLiveViewChange: (Boolean) -> Unit,
    onLiveViewQualityClick: () -> Unit,
    onAutoStartEventChange: (Boolean) -> Unit,
    onShowLatestPhotoChange: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_v2_section_camera_control)) {
        SwitchRowV2(
            icon = Icons.Default.CameraAlt,
            title = stringResource(R.string.settings_v2_camera_controls_title),
            subtitle = if (isCameraControlsEnabled) {
                stringResource(R.string.settings_v2_camera_controls_on)
            } else {
                stringResource(R.string.settings_v2_camera_controls_off)
            },
            checked = isCameraControlsEnabled,
            onCheckedChange = onCameraControlsChange
        )

        if (isCameraControlsEnabled && isLiveViewAllowed) {
            SwitchRowV2(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.settings_v2_liveview_title),
                subtitle = stringResource(R.string.settings_v2_liveview_subtitle),
                checked = isLiveViewEnabled,
                onCheckedChange = onLiveViewChange
            )
            ClickableRowV2(
                icon = Icons.Default.HighQuality,
                title = stringResource(R.string.settings_v2_liveview_quality_title),
                subtitle = stringResource(liveViewQuality.labelRes()),
                onClick = onLiveViewQualityClick
            )
            SwitchRowV2(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings_v2_auto_event_title),
                subtitle = stringResource(R.string.settings_v2_auto_event_subtitle),
                checked = isAutoStartEventListener,
                onCheckedChange = onAutoStartEventChange
            )
        } else if (isCameraControlsEnabled) {
            // 비허용 티어 → 잠금 표시. 구독으로 열리는 기능이므로 문구는 업그레이드를 안내한다.
            LockedRowV2(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.settings_v2_liveview_locked_title),
                subtitle = stringResource(R.string.settings_v2_liveview_locked_subtitle)
            )
            SwitchRowV2(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings_v2_auto_event_title),
                subtitle = stringResource(R.string.settings_v2_auto_event_subtitle),
                checked = isAutoStartEventListener,
                onCheckedChange = onAutoStartEventChange
            )
        }

        if (!isCameraControlsEnabled) {
            SwitchRowV2(
                icon = Icons.Default.Photo,
                title = stringResource(R.string.settings_v2_latest_photo_title),
                subtitle = stringResource(R.string.settings_v2_latest_photo_subtitle),
                checked = isShowLatestPhotoWhenDisabled,
                onCheckedChange = onShowLatestPhotoChange
            )
        }
    }
}

/**
 * Wi-Fi PTP/IP 연결 섹션 — 전 티어 노출(무선 연결은 구독 기능이 아니다, FREE 포함).
 * [onAutoConnectChange] 는 자동 연결 토글의 결과 토스트·알림 권한 처리를 캡슐화한다.
 */
@Composable
internal fun WifiPtpipSection(
    isPtpipEnabled: Boolean,
    lastConnectedName: String?,
    isAutoConnectEnabled: Boolean,
    connectionStatusText: String,
    onPtpipEnabledChange: (Boolean) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onManageConnectionClick: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_v2_section_wifi_ptpip)) {
        SwitchRowV2(
            icon = Icons.Default.Wifi,
            title = stringResource(R.string.settings_v2_wifi_camera_title),
            subtitle = if (isPtpipEnabled) {
                val connectedName = lastConnectedName
                if (connectedName != null) {
                    stringResource(R.string.settings_v2_wifi_camera_on_last, connectedName)
                } else {
                    stringResource(R.string.settings_v2_wifi_camera_on_none)
                }
            } else {
                stringResource(R.string.settings_v2_wifi_camera_off)
            },
            checked = isPtpipEnabled,
            onCheckedChange = onPtpipEnabledChange
        )

        if (isPtpipEnabled) {
            SwitchRowV2(
                icon = Icons.Default.CameraAlt,
                title = stringResource(R.string.settings_v2_auto_connect_title),
                subtitle = stringResource(R.string.settings_v2_auto_connect_subtitle),
                checked = isAutoConnectEnabled,
                onCheckedChange = onAutoConnectChange
            )
            NavigationRowV2(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_v2_connection_manage_title),
                subtitle = stringResource(
                    R.string.settings_v2_connection_manage_subtitle,
                    connectionStatusText
                ),
                onClick = onManageConnectionClick
            )
        }
    }
}

/**
 * 색감 전송 설정 섹션.
 */
@Composable
internal fun ColorTransferSection(
    isColorTransferEnabled: Boolean,
    hasReferenceImage: Boolean,
    isVibrateOnPhotoReceivedEnabled: Boolean,
    onColorTransferChange: (Boolean) -> Unit,
    onColorTransferDetailClick: () -> Unit,
    onVibrateChange: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_v2_section_color_transfer)) {
        SwitchRowV2(
            icon = Icons.Default.Photo,
            title = stringResource(R.string.settings_v2_color_transfer_title),
            subtitle = if (isColorTransferEnabled) {
                if (hasReferenceImage) {
                    stringResource(R.string.settings_v2_color_transfer_on_ref)
                } else {
                    stringResource(R.string.settings_v2_color_transfer_on_noref)
                }
            } else {
                stringResource(R.string.settings_v2_color_transfer_off)
            },
            checked = isColorTransferEnabled,
            onCheckedChange = onColorTransferChange
        )

        if (isColorTransferEnabled) {
            NavigationRowV2(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings_v2_color_transfer_detail_title),
                subtitle = stringResource(R.string.settings_v2_color_transfer_detail_subtitle),
                onClick = onColorTransferDetailClick
            )
        }

        SwitchRowV2(
            icon = Icons.Default.Vibration,
            title = stringResource(R.string.settings_v2_vibrate_on_photo_title),
            subtitle = if (isVibrateOnPhotoReceivedEnabled) {
                stringResource(R.string.settings_v2_vibrate_on_photo_on)
            } else {
                stringResource(R.string.settings_v2_vibrate_on_photo_off)
            },
            checked = isVibrateOnPhotoReceivedEnabled,
            onCheckedChange = onVibrateChange
        )
    }
}

/**
 * 필름 시뮬레이션 설정 섹션 — 편집기 진입(항상) + 자동적용 토글/기본 필름/강도.
 */
@Composable
internal fun FilmSimulationSection(
    isFilmSimulationEnabled: Boolean,
    selectedFilmLutId: String,
    selectedFilmLutLocked: Boolean?,
    filmSimulationIntensity: Float,
    onFilmEditorClick: () -> Unit,
    onFilmSimulationChange: (Boolean) -> Unit,
    onDefaultFilmClick: () -> Unit,
    onIntensityChange: (Float) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_v2_section_film_simulation)) {
        // 편집기 진입(항상 표시) — 소스 미지정으로 열어 컨택트 시트가 이미지 선택을 안내.
        NavigationRowV2(
            icon = Icons.Default.Photo,
            title = stringResource(R.string.settings_v2_film_editor_title),
            subtitle = stringResource(R.string.settings_v2_film_editor_subtitle),
            onClick = onFilmEditorClick
        )

        // 자동적용 블록 — 토글 + (on 일 때) 기본 필름 선택 + 강도 슬라이더.
        SwitchRowV2(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.settings_v2_film_simulation_title),
            subtitle = if (isFilmSimulationEnabled) {
                if (selectedFilmLutId.isNotEmpty()) {
                    stringResource(R.string.settings_v2_film_simulation_on)
                } else {
                    stringResource(R.string.settings_v2_film_simulation_on_nolut)
                }
            } else {
                stringResource(R.string.settings_v2_film_simulation_off)
            },
            checked = isFilmSimulationEnabled,
            onCheckedChange = onFilmSimulationChange
        )

        if (isFilmSimulationEnabled) {
            // 선택된 기본 필름이 현재 티어에서 잠겼으면(강등/무료셋 변경) 보조 문구에 잠금 힌트를 덧붙인다.
            // 자동 적용이 스킵되는데 필름심 ON 으로만 보이는 혼란을 방지한다(null=미확정 → 힌트 없음).
            val defaultFilmSubtitle = if (selectedFilmLutLocked == true) {
                stringResource(R.string.settings_v2_film_default_subtitle) +
                    " · " + stringResource(R.string.fs_selected_film_locked_hint)
            } else {
                stringResource(R.string.settings_v2_film_default_subtitle)
            }
            NavigationRowV2(
                icon = Icons.Default.Photo,
                title = stringResource(R.string.settings_v2_film_default_title),
                subtitle = defaultFilmSubtitle,
                onClick = onDefaultFilmClick
            )
            FilmIntensityRow(
                intensity = filmSimulationIntensity,
                onChange = onIntensityChange
            )
        }
    }
}

/**
 * RAW 파일 다운로드 설정 섹션 — 허용(PRO+) 시 토글, 아니면 잠금 행에서 페이월([onUpgradeClick])로.
 */
@Composable
internal fun RawDownloadSection(
    isRawDownloadAllowed: Boolean,
    isRawFileDownloadEnabled: Boolean,
    onRawDownloadChange: (Boolean) -> Unit,
    onUpgradeClick: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_v2_section_raw_download)) {
        if (isRawDownloadAllowed) {
            SwitchRowV2(
                icon = Icons.Default.Photo,
                title = stringResource(R.string.settings_v2_raw_download_title),
                subtitle = if (isRawFileDownloadEnabled) {
                    stringResource(R.string.settings_v2_raw_download_on)
                } else {
                    stringResource(R.string.settings_v2_raw_download_off)
                },
                checked = isRawFileDownloadEnabled,
                onCheckedChange = onRawDownloadChange
            )
        } else {
            LockedRowV2(
                icon = Icons.Default.Photo,
                title = stringResource(R.string.settings_v2_raw_download_title),
                subtitle = stringResource(R.string.settings_v2_raw_download_locked),
                onUpgradeClick = onUpgradeClick
            )
        }
    }
}

/**
 * 연결된 카메라 정보 섹션 — 기능 제한 고지 전용.
 *
 * 연결 종류·모델명은 화면 상단 Hero([CameraStatusHero])가 소유하므로 여기서 반복하지 않는다.
 * 고지할 제한이 없으면 섹션 자체를 렌더하지 않아 빈 패널을 남기지 않는다.
 */
@Composable
internal fun ConnectedCameraSection(
    cameraFunctionLimitation: UiText?
) {
    val limitation = cameraFunctionLimitation ?: return
    val context = LocalContext.current
    SettingsSection(title = stringResource(R.string.settings_v2_section_connected_camera)) {
        StaticRowV2(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_v2_camera_limitation_title),
            subtitle = limitation.resolve(context)
        )
    }
}
