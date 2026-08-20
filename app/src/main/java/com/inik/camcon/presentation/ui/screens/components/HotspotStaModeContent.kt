package com.inik.camcon.presentation.ui.screens.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.inik.camcon.R
import com.inik.camcon.domain.model.NikonConnectionProfile
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.PtpipCameraInfo
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import com.inik.camcon.presentation.theme.Body
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.DisplayL
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.MonoReadout
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.SuccessV2
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TouchTarget
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
    // 검증된 manualIp가 아니라 자유 타이핑 원문(manualIpInput)을 바인딩한다 — 검증된 값만 바인딩하면
    // 부분 입력이 매 키 입력마다 거부·리셋돼 타이핑으로 완전한 IP를 만들 수 없었다(붙여넣기만 동작).
    val manualIp by ptpipViewModel.manualIpInput.collectAsStateWithLifecycle()
    // 후보 목록·0건 사유·링크 신뢰도는 정책(CameraSelectionPolicy)이 계산한 결과를 그대로 받는다.
    // 화면에서 재정렬·재판정하면 "보이는 순서"와 "자동 연결 대상"이 어긋난다.
    val candidates by ptpipViewModel.cameraCandidates.collectAsStateWithLifecycle()
    val emptyReason by ptpipViewModel.discoveryEmptyReason.collectAsStateWithLifecycle()
    val networkTrust by ptpipViewModel.networkTrust.collectAsStateWithLifecycle()
    val sweepAvailable by ptpipViewModel.isSubnetSweepAvailable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 확인 다이얼로그 상태는 LazyColumn 밖에서 소유한다 — item 안에 두면 스크롤로 item이
    // 컴포지션에서 이탈할 때 다이얼로그가 상태와 함께 사라진다.
    var pendingConfirm by remember { mutableStateOf<PtpipCamera?>(null) }

    // 니콘 [사진 전송] 프로파일 후보를 탭했을 때의 안내 대상. 기능 제약(라이브뷰 중 실시간 수신·
    // 갤러리 불가)을 연결 전에 알리고 [카메라 컨트롤]로의 전환을 권한다. 상세는
    // ImageTransferProfileDialog 문서 참조.
    var pendingTransferProfile by remember { mutableStateOf<PtpipCamera?>(null) }

    val connect: (PtpipCamera) -> Unit = { camera ->
        ptpipViewModel.selectCamera(camera)
        ptpipViewModel.connectToCameraSta(camera)
    }

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

        // 히어로와 연결 섹션은 같은 surface tier 위에 있어 헤어라인이 경계를 만들지 못한다.
        // 정본 규칙상 헤어라인은 tier가 바뀌는 자리 전용 → 여백만으로 구획한다.
        item {
            Spacer(modifier = Modifier.height(Spacing.xl))
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
            DiscoveredCameraList(
                candidates = candidates,
                emptyReason = emptyReason,
                trust = networkTrust,
                // 연결 중에는 목록을 비활성화한다 — 다른 항목 탭을 큐잉하면 connectionStateMutex
                // 뒤에서 순차 연결이 일어나 최대 60초(니콘 승인 데드라인) 무반응이 된다.
                // ⚠️ isConnecting은 ViewModel 로컬 플래그라 배경 폴링이 시작한 연결을 못 잡는다.
                // 그 경우 진행 다이얼로그도 뜨지 않아 화면상 아무 일도 없는 것처럼 보인다
                // → connectionState를 함께 본다.
                enabled = !isConnecting && !isDiscovering &&
                    connectionState != PtpipConnectionState.CONNECTING,
                sweepAvailable = sweepAvailable,
                onCandidateTap = { camera, needsConfirm ->
                    when {
                        // 링크 신뢰 확인이 먼저다 — 오탭 시 대상 카메라 세션을 최대 60초 점유하므로
                        // 기능 안내보다 우선한다. 확인 통과 후 연결 시 프로파일 안내는 생략된다.
                        needsConfirm -> pendingConfirm = camera
                        camera.connectionProfile == NikonConnectionProfile.IMAGE_TRANSFER ->
                            pendingTransferProfile = camera
                        else -> connect(camera)
                    }
                },
                onRetrySearch = { ptpipViewModel.discoverCamerasHotspot() },
                onSweepSubnet = { ptpipViewModel.sweepSubnet() }
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
        }

        item {
            ConnectionHelpExpander()
        }
    }

    pendingConfirm?.let { target ->
        CameraConnectConfirmDialog(
            onDismiss = { pendingConfirm = null },
            onConfirm = {
                pendingConfirm = null
                connect(target)
            }
        )
    }

    pendingTransferProfile?.let { target ->
        ImageTransferProfileDialog(
            onDismiss = { pendingTransferProfile = null },
            onConnectAnyway = {
                pendingTransferProfile = null
                connect(target)
            },
            onRetrySearch = {
                pendingTransferProfile = null
                ptpipViewModel.discoverCamerasHotspot()
            }
        )
    }
}

/**
 * 핫스팟 상태 표시 (전면 정리 2026-08-18, 사용자 결정).
 *
 * 켜짐: 컴팩트 상태 밴드(틴트 사각 아이콘 + 상태 텍스트 + 성공 점) 한 줄 — 상태는 확인만
 * 하면 되는 정보라 화면 절반을 차지하던 대형 히어로(64dp 아이콘 + 34sp + SSID/GATEWAY
 * 판독)를 걷어냈다. SSID는 위치권한 없으면 "<unknown ssid>" 원시값이 노출되고 GATEWAY는
 * 사용자에게 무의미한 디버그 정보라 함께 제거. 주인공은 아래 [카메라 검색] 액션이다.
 *
 * 꺼짐: 이 화면의 유일한 선행 조건이므로 대형 히어로 유지(아이콘 + 안내 + 설정 열기 버튼).
 * Android 정책상 일반 앱은 표준 모바일 핫스팟을 코드로 직접 켤 수 없으므로
 * (TETHER_PRIVILEGED = signature 권한) 설정 화면으로 사용자를 안내한다.
 */
@Composable
private fun HotspotStatusHero(
    state: HotspotStaContentState,
    onOpenSettings: () -> Unit
) {
    val enabled = state.status == HotspotStaContentState.HotspotStatus.ENABLED
    if (enabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(Radius.md)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.md)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Text(
                text = stringResource(R.string.ptpip_hotspot_enabled),
                style = HeadingM,
                color = TextPrimaryV2,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(SuccessV2, CircleShape)
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xl, bottom = Spacing.sm),
            horizontalAlignment = Alignment.Start
        ) {
            // 히어로 대형 아이콘 — 기존 히어로 선례(Onboarding 72dp 등) 관례상 치수 하드코딩
            Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.ptpip_hotspot_disabled),
                style = DisplayL,
                color = TextSecondaryV2,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.ptpip_hotspot_hero_hint),
                style = Body,
                color = TextSecondaryV2,
                modifier = Modifier.widthIn(max = 420.dp)
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
 * 카메라 연결 섹션 (전면 정리 2026-08-18, 사용자 결정).
 *
 * 주 액션은 [카메라 검색] 버튼 하나 — 자동 폴백 스윕까지 검색 버튼이 다 하므로 "mDNS" 같은
 * 프로토콜 용어 섹션 헤더는 제거(버튼 라벨이 곧 설명). 수동 IP 입력은 고급 사용자 전용이라
 * 접이식 뒤로 내려 첫 화면 밀도를 낮춘다(기본 접힘).
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
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        PrimaryButton(
            text = if (isDiscovering) stringResource(R.string.sta_mode_searching)
            else stringResource(R.string.sta_mode_search_camera),
            onClick = onSearchClick,
            leadingIcon = Icons.Filled.Search,
            enabled = enabled && !isDiscovering,
            isLoading = isDiscovering,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TouchTarget.min)
                .clickable(role = Role.Button) { advancedOpen = !advancedOpen }
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (advancedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = TextSecondaryV2,
                modifier = Modifier.size(IconSize.md)
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = stringResource(R.string.ptpip_manual_ip_input),
                style = BodySmall,
                color = TextSecondaryV2
            )
        }
        AnimatedVisibility(visible = advancedOpen) {
            Column(
                modifier = Modifier.padding(
                    start = IconSize.md + Spacing.sm,
                    top = Spacing.xs
                )
            ) {
                // 긴 로케일 라벨(fr/de)에서 IP 필드가 좁아지지 않도록 버튼은 아래 별도 행 전폭.
                // 최대 15자 IPv4라 전폭이 낭비 — 내용 길이에 맞춰 폭을 끊어 전폭 스택에 리듬을 준다.
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = onIpChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MonoReadout,
                    placeholder = {
                        Text("192.168.49.137", style = MonoReadout, color = TextSecondaryV2)
                    },
                    modifier = Modifier.widthIn(max = 220.dp)
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
