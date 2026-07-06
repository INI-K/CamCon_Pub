package com.inik.camcon.presentation.ui.screens.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.inik.camcon.R
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.presentation.theme.Body
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.HeadingXL
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TouchTarget
import com.inik.camcon.presentation.ui.components.v2.DividerLineV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.viewmodel.PtpipViewModel

/**
 * 폰 핫스팟 STA 모드 화면 (히어로형).
 *
 * 폰이 핫스팟 역할을 하고 카메라가 폰의 핫스팟에 STA로 접속하는 시나리오.
 * 구성: 핫스팟 상태 히어로(꺼짐이면 설정 열기 버튼) → 카메라 연결 섹션(mDNS 검색 + 수동 IP)
 * → 접이식 연결 방법 안내. 연결 중/연결됨이면 히어로 자리에 [ConnectionStatusCard]를 렌더한다.
 */
@Composable
fun HotspotStaModeContent(
    ptpipViewModel: PtpipViewModel,
    connectionState: PtpipConnectionState,
    discoveredCameras: List<PtpipCamera>,
    isDiscovering: Boolean,
    isConnecting: Boolean,
    selectedCamera: PtpipCamera?,
    cameraInfo: PtpipCameraInfo?,
    isPtpipEnabled: Boolean,
    isWifiConnected: Boolean,
    wifiCapabilities: WifiCapabilities,
    wifiNetworkState: WifiNetworkState,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = HotspotStaContentState.fromWifiState(wifiNetworkState)
    val manualIp by ptpipViewModel.manualIp.collectAsState()
    val context = LocalContext.current

    // 핫스팟 설정을 켜고 화면으로 돌아오면 상태를 다시 읽어 히어로/검색 활성을 갱신한다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ptpipViewModel.refreshHotspotState()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.base, vertical = Spacing.md)
    ) {
        val isActiveConnection = connectionState == PtpipConnectionState.CONNECTING ||
            connectionState == PtpipConnectionState.CONNECTED
        if (isActiveConnection) {
            item {
                ConnectionStatusCard(
                    connectionState = connectionState,
                    selectedCamera = selectedCamera,
                    cameraInfo = cameraInfo,
                    onDisconnect = { ptpipViewModel.disconnect() },
                    onCapture = { ptpipViewModel.capturePhoto() }
                )
            }
        } else {
            // DISCONNECTED·ERROR 모두 히어로 유지 — ERROR는 자동으로 DISCONNECTED로
            // 복귀하지 않으므로(PtpipDataSource) 히어로를 가리면 복구 액션이 사라진다.
            item {
                HotspotStatusHero(
                    state = state,
                    onOpenSettings = {
                        openHotspotSettings(context)
                        ptpipViewModel.refreshHotspotState()
                    }
                )
            }
            if (connectionState == PtpipConnectionState.ERROR) {
                item {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    ConnectionStatusCard(
                        connectionState = connectionState,
                        selectedCamera = selectedCamera,
                        cameraInfo = cameraInfo,
                        onDisconnect = { ptpipViewModel.disconnect() },
                        onCapture = { ptpipViewModel.capturePhoto() }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(Spacing.xl))
            DividerLineV2()
            Spacer(modifier = Modifier.height(Spacing.lg))
        }

        item {
            CameraConnectSection(
                manualIp = manualIp,
                isDiscovering = isDiscovering,
                enabled = !isConnecting,
                onSearchClick = { ptpipViewModel.discoverCamerasHotspot() },
                onIpChange = { ptpipViewModel.setManualIp(it) },
                onConnect = { ptpipViewModel.connectManualCamera() },
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
        }

        item {
            ConnectionHelpExpander()
        }
    }
}

/**
 * 핫스팟 상태 히어로 — 카드 테두리 없이 중앙 정렬 대형 아이콘 + 상태 텍스트.
 * 꺼짐: 안내 문구 + 테더링 설정 열기 버튼. 켜짐: SSID/Gateway 보조 표시.
 *
 * Android 정책상 일반 앱은 표준 모바일 핫스팟을 코드로 직접 켤 수 없으므로
 * (TETHER_PRIVILEGED = signature 권한) 설정 화면으로 사용자를 안내한다.
 */
@Composable
private fun HotspotStatusHero(
    state: HotspotStaContentState,
    onOpenSettings: () -> Unit
) {
    val enabled = state.status == HotspotStaContentState.HotspotStatus.ENABLED
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xl, bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 히어로 대형 아이콘 — 기존 히어로 선례(Onboarding 72dp 등) 관례상 치수 하드코딩
        Icon(
            imageVector = if (enabled) Icons.Filled.Wifi else Icons.Filled.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = stringResource(
                if (enabled) R.string.ptpip_hotspot_enabled
                else R.string.ptpip_hotspot_disabled
            ),
            style = HeadingXL,
            color = TextPrimaryV2,
            textAlign = TextAlign.Center
        )
        if (enabled) {
            state.ssidLabel?.let {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "SSID: $it",
                    style = BodySmall,
                    color = TextSecondaryV2
                )
            }
            state.gatewayLabel?.let {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Gateway: $it",
                    style = BodySmall,
                    color = TextSecondaryV2
                )
            }
        } else {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.ptpip_hotspot_hero_hint),
                style = Body,
                color = TextSecondaryV2,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            PrimaryButton(
                text = stringResource(R.string.ptpip_hotspot_open_settings),
                onClick = onOpenSettings,
                leadingIcon = Icons.Filled.Wifi,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 테더링 설정 화면으로 이동한다.
 * 공개·보장된 단일 API가 없어 [후보 인텐트들]을 순서대로 시도하고 실패 시 다음으로 폴백한다.
 * 1) OS가 쓰는 정식 컴포넌트(OEM 파편화 위험) → 2) 액션 문자열 → 3) 무선 설정 → 4) 일반 설정.
 */
private fun openHotspotSettings(context: Context) {
    val candidates = listOf(
        Intent().setClassName(
            "com.android.settings",
            "com.android.settings.TetherSettings"
        ),
        Intent("com.android.settings.TETHER_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

/**
 * 카메라 연결 섹션 — mDNS 검색(주 액션)과 수동 IP 입력(보조)을 한 영역으로 통합.
 */
@Composable
private fun CameraConnectSection(
    manualIp: String,
    isDiscovering: Boolean,
    enabled: Boolean,
    onSearchClick: () -> Unit,
    onIpChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.sta_mode_mdns_search),
            style = HeadingM,
            color = TextPrimaryV2
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        PrimaryButton(
            text = if (isDiscovering) stringResource(R.string.sta_mode_searching)
            else stringResource(R.string.sta_mode_search_camera),
            onClick = onSearchClick,
            leadingIcon = Icons.Filled.Search,
            enabled = enabled && !isDiscovering,
            isLoading = isDiscovering,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = stringResource(R.string.ptpip_manual_ip_input),
            style = BodySmall,
            color = TextSecondaryV2
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        // 긴 로케일 라벨(fr/de)에서 IP 필드가 좁아지지 않도록 버튼은 아래 별도 행 전폭.
        OutlinedTextField(
            value = manualIp,
            onValueChange = onIpChange,
            enabled = enabled,
            singleLine = true,
            placeholder = { Text("192.168.49.137") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        SecondaryButton(
            text = stringResource(R.string.ptpip_connect_with_ip),
            onClick = onConnect,
            enabled = enabled && manualIp.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 접이식 연결 방법 안내 — 기본 접힘, 헤더 탭으로 펼침/접힘 전환.
 */
@Composable
private fun ConnectionHelpExpander() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val stateExpanded = stringResource(R.string.cd_expanded)
    val stateCollapsed = stringResource(R.string.cd_collapsed)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TouchTarget.min)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics {
                    stateDescription = if (expanded) stateExpanded else stateCollapsed
                }
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = TextSecondaryV2,
                modifier = Modifier.size(IconSize.md)
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = stringResource(R.string.ptpip_hotspot_help_title),
                style = HeadingM,
                color = TextSecondaryV2
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = stringResource(R.string.ptpip_hotspot_help_body),
                style = BodySmall,
                color = TextSecondaryV2,
                modifier = Modifier.padding(
                    start = IconSize.md + Spacing.sm,
                    top = Spacing.xs,
                    bottom = Spacing.sm
                )
            )
        }
    }
}
