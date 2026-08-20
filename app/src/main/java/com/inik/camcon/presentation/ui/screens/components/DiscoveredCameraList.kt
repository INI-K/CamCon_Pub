package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraCandidate
import com.inik.camcon.domain.model.CameraDiscoverySource
import com.inik.camcon.domain.model.CameraProtocol
import com.inik.camcon.domain.model.DiscoveryEmptyReason
import com.inik.camcon.domain.model.NetworkTrust
import com.inik.camcon.domain.model.NikonConnectionProfile
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.VendorConfidence
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.ChipV2
import com.inik.camcon.presentation.ui.components.v2.EmptyState
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.RowItem
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton

/**
 * 발견된 카메라 후보 목록 + 선택.
 *
 * 후보가 2개 이상일 때 `cameras.first()`로 조용히 자동 선택하던 동작을 대체한다.
 * 자동 연결 여부는 [com.inik.camcon.domain.model.CameraSelectionPolicy]가 결정하며 이 컴포저블은
 * **표시와 탭만** 담당한다(정렬도 정책이 정한 순서를 그대로 쓰고 여기서 재정렬하지 않는다).
 *
 * 격리 규칙: 출처가 사용자 입력·불명(MANUAL_INPUT/UNKNOWN, 이후 서브넷 스캔분)인 후보는 별도 섹션으로
 * 내린다. 라이브 신호(mDNS/SSDP)로 잡힌 후보와 달리 카메라라는 근거가 약하고, 공용 네트워크에서는
 * 타인 기기일 수 있기 때문이다.
 */
@Composable
fun DiscoveredCameraList(
    candidates: List<CameraCandidate>,
    emptyReason: DiscoveryEmptyReason,
    trust: NetworkTrust,
    enabled: Boolean,
    sweepAvailable: Boolean,
    onCandidateTap: (camera: PtpipCamera, needsConfirm: Boolean) -> Unit,
    onRetrySearch: () -> Unit,
    onSweepSubnet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) {
        DiscoveryEmptyBlock(
            reason = emptyReason,
            enabled = enabled,
            sweepAvailable = sweepAvailable,
            onRetrySearch = onRetrySearch,
            onSweepSubnet = onSweepSubnet,
            modifier = modifier
        )
        return
    }

    val (live, isolated) = candidates.partition { it.camera.discoverySource.isLiveSignal() }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.ptpip_discovery_found_title),
            style = HeadingM,
            color = TextPrimaryV2
        )
        if (trust == NetworkTrust.UNTRUSTED_SHARED) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.ptpip_shared_network_notice),
                style = BodySmall,
                color = TextSecondaryV2
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))

        live.forEach { candidate ->
            CandidateRow(
                candidate = candidate,
                enabled = enabled,
                // 확인이 필요한 후보(비신뢰 링크 + 기지 아님)는 탭 즉시 연결하지 않는다. 연결은 대상
                // 카메라에 승인 요청을 띄우고 최대 60초 세션을 점유하므로 오탭 비용이 크다.
                onTap = { onCandidateTap(candidate.camera, candidate.requiresConfirm) }
            )
        }

        // 사진 전송 모드 카메라가 목록에 있으면 기능 제약을 **연결 전에** 알린다.
        // 2026-08-19 Z8 전수 실측으로 모드별 능력이 확정됐다:
        //   [카메라 컨트롤] 라이브뷰 중 실시간 수신 O / 갤러리 O / 앱셔터 O
        //   [사진 전송]     라이브뷰 중 실시간 수신 X(라이브뷰를 꺼야 들어옴) / 갤러리 X(폴더 정보 잠금)
        // 즉 CamCon 에는 카메라 컨트롤이 상위 호환이다. 본체 재생(▶)은 니콘 사양상 PC 연결 중이면
        // 어느 모드든 제한되므로(공식 명세 §2.2 No.3 — 유일한 예외인 application mode 는 카드
        // 저장소를 통째로 감춰 갤러리를 죽이는 것이 실측됨) 모드 선택 기준이 되지 못한다.
        if (candidates.any { it.camera.connectionProfile == NikonConnectionProfile.IMAGE_TRANSFER }) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.ptpip_profile_transfer_notice),
                style = BodySmall,
                color = TextSecondaryV2
            )
        }

        if (isolated.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.md))
            // "공용 네트워크일 수 있습니다" 경고는 공유망에서만 성립 — 폰 핫스팟(단독 링크)에선
            // 서브넷의 모든 기기가 본인 핫스팟 접속 기기라 경고가 오히려 불안만 조성한다
            // (전면 정리 2026-08-18). 격리 후보의 탭 시 확인 다이얼로그는 신뢰망에서도 유지된다.
            if (trust != NetworkTrust.TRUSTED_DIRECT_LINK) {
                Text(
                    text = stringResource(R.string.ptpip_manual_source_hint),
                    style = BodySmall,
                    color = TextSecondaryV2
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
            isolated.forEach { candidate ->
                CandidateRow(
                    candidate = candidate,
                    enabled = enabled,
                    // 격리 섹션은 기지 기기여도 항상 확인을 받는다.
                    onTap = { onCandidateTap(candidate.camera, true) }
                )
            }
        }

        // 스윕은 0건일 때만 쓰는 보조 수단이 아니다. mDNS/SSDP로 광고하지 않는 제조사(또는 멀티캐스트
        // 차단망)에서는 **유일한 자동 발견 수단**이므로, 후보가 이미 있어도 항상 노출한다.
        // 발견만 하고 자동 연결은 하지 않으므로(정책이 SUBNET_SCAN을 영구 제외) 노출 비용이 낮다.
        if (sweepAvailable) {
            Spacer(modifier = Modifier.height(Spacing.md))
            SecondaryButton(
                text = stringResource(R.string.ptpip_sweep_action),
                onClick = onSweepSubnet,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 후보 연결 확인 다이얼로그.
 *
 * 호출부(화면)가 상태를 소유해야 한다 — `LazyColumn` item 안에 두면 그 item이 컴포지션에서
 * 이탈할 때 다이얼로그가 상태와 함께 사라진다.
 */
@Composable
fun CameraConnectConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.ptpip_connect_confirm_title),
                style = HeadingM,
                color = TextPrimaryV2
            )
        },
        text = {
            Text(
                text = stringResource(R.string.ptpip_connect_confirm_body),
                style = BodySmall,
                color = TextSecondaryV2
            )
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.ptpip_connect_confirm_action),
                onClick = onConfirm
            )
        },
        dismissButton = {
            SecondaryButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }
    )
}

/**
 * 니콘 [사진 전송] 프로파일 카메라를 탭했을 때 뜨는 안내.
 *
 * 이 모드로도 연결·촬영·수신은 되지만 (1) 라이브뷰 중 촬영물이 라이브뷰를 끌 때까지 안 들어오고
 * (2) 갤러리(카드 탐색)가 폴더 정보 잠금으로 동작하지 않는다(Z8 실측 2026-08-19). 카메라에서
 * [카메라 컨트롤]로 바꾸면 둘 다 해결되므로 재검색을 1순위로 권한다.
 *
 * 다만 **차단하지 않는다** — 카메라 컨트롤 프로파일을 무선에서 제공하지 않는 기종이 있으면
 * 막는 순간 그 기종의 무선 수신이 통째로 죽는다. EXPEED 6 세대(Z6/Z7/Z5/Z50 등)가 그렇다는
 * 정황(업스트림 이슈 #976: Z6II 무선에서 객체 접근 잠금, "smart device mode 에서만 잠금 없음")은
 * 있으나 **확정된 사실이 아니다** — 니콘 공식 명세는 §1.1 에서 스스로 USB 범위임을 밝히고
 * 있어 무선 프로파일 구성을 규정하지 않는다(다만 ConnectionPath 0xD12E 는 구세대 문서에도
 * 'Built-in Wi-Fi' 값을 정의한다 = 무선에서도 같은 PTP 방언을 쓴다는 뜻). 실기 확인 전까지
 * 보수적으로 열어 둔다.
 */
@Composable
fun ImageTransferProfileDialog(
    onDismiss: () -> Unit,
    onConnectAnyway: () -> Unit,
    onRetrySearch: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.ptpip_profile_transfer_dialog_title),
                style = HeadingM,
                color = TextPrimaryV2
            )
        },
        text = {
            Text(
                text = stringResource(R.string.ptpip_profile_transfer_dialog_body),
                style = BodySmall,
                color = TextSecondaryV2
            )
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.ptpip_profile_transfer_dialog_research),
                onClick = onRetrySearch
            )
        },
        dismissButton = {
            SecondaryButton(
                text = stringResource(R.string.ptpip_profile_transfer_dialog_connect_anyway),
                onClick = onConnectAnyway
            )
        }
    )
}

/** mDNS/SSDP 등 카메라가 스스로 광고한 라이브 신호인가. */
private fun CameraDiscoverySource.isLiveSignal(): Boolean = when (this) {
    CameraDiscoverySource.MDNS,
    CameraDiscoverySource.SSDP,
    CameraDiscoverySource.CACHED_IP,
    CameraDiscoverySource.AP_GATEWAY -> true

    CameraDiscoverySource.MANUAL_INPUT,
    CameraDiscoverySource.SUBNET_SCAN,
    CameraDiscoverySource.RESTORED,
    CameraDiscoverySource.UNKNOWN -> false
}

@Composable
private fun CandidateRow(
    candidate: CameraCandidate,
    enabled: Boolean,
    onTap: () -> Unit
) {
    val camera = candidate.camera
    // 발견은 됐지만 앱이 아직 제어할 수 없는 프로토콜(예: 후지 55740 포크)은 탭을 막고 사유를 밝힌다.
    // 목록에서 숨기면 "내 카메라가 왜 안 보이지"가 되고, 그냥 탭시키면 실패만 반복한다.
    val protocol = CameraProtocol.ofPort(camera.port)
    val connectable = protocol.isConnectable
    RowItem(
        label = camera.resolveDisplayLabel(),
        description = if (connectable) {
            camera.buildSubtext()
        } else {
            camera.buildSubtext() + " · " + stringResource(R.string.ptpip_protocol_unsupported)
        },
        leadingIcon = Icons.Filled.PhotoCamera,
        trailing = if (candidate.isKnown) {
            { ChipV2(text = stringResource(R.string.ptpip_candidate_known)) }
        } else {
            null
        },
        onClick = onTap.takeIf { connectable },
        enabled = enabled && connectable
    )
}

/**
 * 표시명 폴백 체인: displayName → name → "카메라 (IP)".
 *
 * ⚠️ [PtpipCamera.name]은 Nikon STA 인증 게이트(`CameraVendorClassifier.isLikelyNikon`)의 입력이므로
 * 표시 목적으로 가공하지 않는다. 여기서 만드는 라벨은 화면에만 쓰고 도메인으로 돌려보내지 않는다.
 */
@Composable
private fun PtpipCamera.resolveDisplayLabel(): String =
    displayName?.takeIf { it.isNotBlank() }
        ?: name.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.ptpip_candidate_unnamed_fmt, ipAddress)

/** "192.168.49.137:15740 · 자동 발견 · Nikon 확정 · 원격 제어" 형태의 보조 설명. */
@Composable
private fun PtpipCamera.buildSubtext(): String {
    val parts = mutableListOf("$ipAddress:$port")
    sourceLabel()?.let(parts::add)
    parts.add(vendorLabel())
    profileLabel()?.let(parts::add)
    return parts.joinToString(" · ")
}

/**
 * 카메라가 광고한 연결 프로파일. 판별 불가(UNKNOWN)면 아무것도 붙이지 않는다.
 *
 * 이 값은 mDNS TXT 로 읽은 것이라 연결 전에 알 수 있고, **바디 조작 가능 여부**를 좌우한다
 * (원격 제어=본체 재생 잠김 / 사진 전송=본체 자유. Z8 실측 2026-08-19).
 * 사용자가 용도에 맞는 모드를 고를 수 있게 목록에서 미리 보여준다.
 */
@Composable
private fun PtpipCamera.profileLabel(): String? = when (connectionProfile) {
    NikonConnectionProfile.CAMERA_CONTROL -> stringResource(R.string.ptpip_profile_control)
    NikonConnectionProfile.IMAGE_TRANSFER -> stringResource(R.string.ptpip_profile_transfer)
    NikonConnectionProfile.UNKNOWN -> null
}

@Composable
private fun PtpipCamera.sourceLabel(): String? = when (discoverySource) {
    CameraDiscoverySource.MDNS -> stringResource(R.string.ptpip_source_mdns)
    CameraDiscoverySource.SSDP -> stringResource(R.string.ptpip_source_ssdp)
    CameraDiscoverySource.AP_GATEWAY -> stringResource(R.string.ptpip_source_gateway)
    CameraDiscoverySource.CACHED_IP -> stringResource(R.string.ptpip_source_cached)
    CameraDiscoverySource.MANUAL_INPUT -> stringResource(R.string.ptpip_source_manual)
    CameraDiscoverySource.SUBNET_SCAN -> stringResource(R.string.ptpip_source_scan)
    CameraDiscoverySource.RESTORED, CameraDiscoverySource.UNKNOWN -> null
}

@Composable
private fun PtpipCamera.vendorLabel(): String {
    val vendorName = vendorVerdict.vendor.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }
    return when (vendorVerdict.confidence) {
        VendorConfidence.CONFIRMED ->
            stringResource(R.string.ptpip_vendor_confirmed_fmt, vendorName)

        VendorConfidence.LIKELY ->
            stringResource(R.string.ptpip_vendor_likely_fmt, vendorName)

        VendorConfidence.UNKNOWN -> stringResource(R.string.ptpip_vendor_unknown)
    }
}

/**
 * 후보 0건 안내.
 *
 * 사유 1순위는 **카메라 측 조치**다(핫스팟 합류 / 스마트기기 연결 메뉴 / 절전 해제). "로컬 네트워크 차단"
 * 안내는 targetSdk 36에서 Android의 로컬 네트워크 제한이 강제되지 않으므로 넣지 않는다 — 넣으면 오진이다.
 */
@Composable
private fun DiscoveryEmptyBlock(
    reason: DiscoveryEmptyReason,
    enabled: Boolean,
    sweepAvailable: Boolean,
    onRetrySearch: () -> Unit,
    onSweepSubnet: () -> Unit,
    modifier: Modifier = Modifier
) {
    // NONE(후보 있음)과 NOT_SEARCHED(초기 진입)는 아무것도 그리지 않는다.
    if (reason == DiscoveryEmptyReason.NONE || reason == DiscoveryEmptyReason.NOT_SEARCHED) return

    val description = when (reason) {
        DiscoveryEmptyReason.NO_NETWORK -> stringResource(R.string.ptpip_discovery_no_network)
        DiscoveryEmptyReason.CAMERA_AP_EMPTY -> stringResource(R.string.ptpip_discovery_ap_empty)
        DiscoveryEmptyReason.BLOCKED_BUSY -> stringResource(R.string.ptpip_discovery_busy)
        else -> stringResource(R.string.ptpip_discovery_empty_camera_first)
    }
    val icon = if (reason == DiscoveryEmptyReason.NO_NETWORK) {
        Icons.Filled.WifiOff
    } else {
        Icons.Filled.Search
    }

    EmptyState(
        icon = icon,
        title = stringResource(R.string.ptpip_discovery_empty_title),
        description = description,
        action = if (reason == DiscoveryEmptyReason.NOT_FOUND) {
            {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SecondaryButton(
                        text = stringResource(R.string.sta_mode_search_camera),
                        onClick = onRetrySearch,
                        enabled = enabled
                    )
                    // 서브넷 스윕은 최후 폴백이라 사용자가 명시적으로 눌러야만 실행한다.
                    // 프리픽스를 못 얻는 환경에서는 버튼 자체를 내보내지 않는다 — 누르면
                    // 아무 일도 안 하는 버튼을 주력 모드에 두면 안 된다.
                    if (sweepAvailable) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        SecondaryButton(
                            text = stringResource(R.string.ptpip_sweep_action),
                            onClick = onSweepSubnet,
                            enabled = enabled
                        )
                    }
                }
            }
        } else {
            null
        },
        modifier = modifier
    )
}
