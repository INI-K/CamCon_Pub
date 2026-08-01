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

        if (isolated.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.ptpip_manual_source_hint),
                style = BodySmall,
                color = TextSecondaryV2
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
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

/** "192.168.49.137:15740 · 자동 발견 · Nikon 확정" 형태의 보조 설명. */
@Composable
private fun PtpipCamera.buildSubtext(): String {
    val parts = mutableListOf("$ipAddress:$port")
    sourceLabel()?.let(parts::add)
    parts.add(vendorLabel())
    return parts.joinToString(" · ")
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
