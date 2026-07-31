package com.inik.camcon.domain.model

/**
 * 현재 네트워크 링크의 신뢰도.
 *
 * "폰과 카메라만 있는 단독 세그먼트"인지 여부가 자동 연결 허용의 1차 조건이다.
 * 공유기/공용 Wi-Fi(클라이언트로 붙은 상태)에서는 mDNS로 타인 카메라가 잡히므로
 * 후보가 1개여도 자동 연결하지 않는다.
 */
enum class NetworkTrust {
    /** 카메라 자체 AP에 직결, 또는 폰이 SoftAP(게이트웨이) — 단독 링크. */
    TRUSTED_DIRECT_LINK,

    /** 공유기/공용 Wi-Fi에 클라이언트로 붙은 상태(STA 동시연결 포함). */
    UNTRUSTED_SHARED,

    /** 클라이언트 연결도 없고 핫스팟도 꺼짐. */
    NO_NETWORK
}

/**
 * 후보 0건 상태의 사유. UI EmptyState 분기의 단일 근거.
 */
enum class DiscoveryEmptyReason {
    /** 후보가 1개 이상 — EmptyState 렌더 안 함. */
    NONE,

    /** 아직 검색을 실행하지 않음(초기 진입) — 아무것도 렌더하지 않는다. */
    NOT_SEARCHED,

    /** 핫스팟 꺼짐 AND Wi-Fi 미연결. */
    NO_NETWORK,

    /** 카메라 AP에 붙어 있으나 카메라를 찾지 못함. */
    CAMERA_AP_EMPTY,

    /** 네트워크 정상 + 0건. */
    NOT_FOUND,

    /** 세션 점유(CONNECTING/CONNECTED/무선수신)로 검색을 스킵함. */
    BLOCKED_BUSY
}

/**
 * 목록에 렌더할 후보 1건 + 정책 판정 결과.
 *
 * @param isKnown 기억된 IP(lastConnectedIp)와 일치하는 후보
 * @param requiresConfirm 탭 시 확인 다이얼로그가 필요한 후보(비신뢰 링크 + 기지 아님)
 */
data class CameraCandidate(
    val camera: PtpipCamera,
    val isKnown: Boolean,
    val requiresConfirm: Boolean
)

/** 후보 목록에 대한 정책 판정 결과. */
sealed interface SelectionOutcome {
    /** 후보 0건 — 자동 연결 없음, EmptyState 렌더. */
    data object Empty : SelectionOutcome

    /** 무탭 자동 연결 허용(신뢰 링크 + 기지 기기 정확히 1개 + 세션 미점유). */
    data class AutoConnect(val camera: PtpipCamera) : SelectionOutcome

    /** 사용자 탭 1회 필요. */
    data class RequireSelection(val candidates: List<CameraCandidate>) : SelectionOutcome
}

/**
 * 검색 1회의 결과 + 0건 사유 + (예외 시) 표시 메시지.
 *
 * presentation 헬퍼가 하드코딩 문자열 대신 이 값을 반환하고, ViewModel이 [error]를 resolve한다.
 */
data class DiscoveryAttemptResult(
    val cameras: List<PtpipCamera>,
    val reason: DiscoveryEmptyReason,
    val error: UiText? = null
)

/**
 * 후보 0/1/2+ 분기와 자동 연결 허용 판정의 **단일 지점**(순수 함수).
 *
 * Android/Hilt/Context를 참조하지 않으므로 presentation(ViewModel)에서 직접 호출 가능하고
 * (presentation → domain 합법) 단위 테스트가 가능하다. Hilt UseCase로 만들지 않는 이유는
 * 자동연결 UseCase 단일 지점 이관(Wave 3)과 파일이 충돌하기 때문이다.
 *
 * 자동 연결(무탭) 허용 = 아래 3조건 AND (하나라도 어긋나면 사용자 탭 1회 필수):
 * 1. 신뢰 링크([trustOf] == [NetworkTrust.TRUSTED_DIRECT_LINK])
 * 2. 기지 기기(lastConnectedIp 일치)가 **정확히 1개** — 첫 페어링은 항상 사용자 탭
 *    (Nikon 승인 60초 락을 사용자 의도 없이 시작하지 않는다)
 * 3. 세션 점유 아님(호출부가 `autoConnectBlocked`로 전달)
 */
object CameraSelectionPolicy {

    /**
     * 자동 연결(무탭) 대상에서 영구 제외하는 출처.
     *
     * - MANUAL_INPUT: 사용자가 IP 버튼으로 명시 연결하는 경로가 이미 있다.
     * - SUBNET_SCAN: 이름·제조사 신호가 없어 Nikon STA 인증 게이트가 false로 떨어지고,
     *   공용망에서는 타인 기기일 수 있다.
     */
    val AUTO_CONNECT_EXCLUDED_SOURCES = setOf(
        CameraDiscoverySource.MANUAL_INPUT,
        CameraDiscoverySource.SUBNET_SCAN
    )

    /**
     * 실제 가용 API만 사용한 링크 신뢰도 판정.
     *
     * - [WifiNetworkState.isConnectedToCameraAP] → 카메라 자체 AP 직결(폰↔카메라 단독 세그먼트)
     * - [WifiNetworkState.isHotspotEnabled] && ![WifiNetworkState.isConnected] → 폰이 게이트웨이(SoftAP)
     * - 셋 다 false → 네트워크 없음
     * - 그 외 → 공유 네트워크
     *
     * NetworkInterface 프리픽스 계산·게이트웨이 IP 대조는 Wave 2 범위이므로 사용하지 않는다.
     */
    fun trustOf(state: WifiNetworkState): NetworkTrust = when {
        state.isConnectedToCameraAP -> NetworkTrust.TRUSTED_DIRECT_LINK
        state.isHotspotEnabled && !state.isConnected -> NetworkTrust.TRUSTED_DIRECT_LINK
        !state.isConnected && !state.isHotspotEnabled -> NetworkTrust.NO_NETWORK
        else -> NetworkTrust.UNTRUSTED_SHARED
    }

    /**
     * 후보 목록 → 정렬된 [CameraCandidate] 목록.
     *
     * 정렬(결정적): isKnown desc → 제조사 판별 신뢰도 desc → 출처 우선순위 desc → ipAddress asc.
     * 입력 목록의 중복(IP:port)은 이미 데이터 레이어에서 제거된 상태를 전제한다(여기서 재병합하지 않는다).
     */
    fun buildCandidates(
        cameras: List<PtpipCamera>,
        knownIp: String?,
        trust: NetworkTrust
    ): List<CameraCandidate> = buildCandidates(
        cameras = cameras,
        known = KnownCameraRef(ipHint = knownIp),
        trust = trust
    )

    /**
     * 기억된 카메라 참조 기반 후보 생성.
     *
     * IP 단독 판정보다 강하다 — [KnownCameraRef.matches]가 mDNS 인스턴스명을 먼저 보므로
     * DHCP로 IP가 바뀌어도 같은 본체를 기지 기기로 인식한다.
     */
    fun buildCandidates(
        cameras: List<PtpipCamera>,
        known: KnownCameraRef,
        trust: NetworkTrust
    ): List<CameraCandidate> {
        return cameras
            .map { camera ->
                val isKnown = !known.isEmpty() && known.matches(camera)
                CameraCandidate(
                    camera = camera,
                    isKnown = isKnown,
                    // 스윕 후보는 신뢰 링크·기지 여부와 무관하게 항상 확인을 받는다 — 제조사 신호가
                    // 없어 남의 카메라일 수 있고, 연결은 대상 기기 화면에 승인 요청을 띄운다.
                    requiresConfirm = camera.discoverySource == CameraDiscoverySource.SUBNET_SCAN ||
                        (trust == NetworkTrust.UNTRUSTED_SHARED && !isKnown)
                )
            }
            .sortedWith(
                compareByDescending<CameraCandidate> { it.isKnown }
                    .thenByDescending { confidenceRank(it.camera.vendorVerdict) }
                    .thenByDescending { it.camera.discoverySource.priority }
                    .thenBy { it.camera.ipAddress }
            )
    }

    /**
     * 0/1/2+ 분기 판정.
     *
     * 후보가 2개 이상이어도 "신뢰 링크 + 기지 기기 정확히 1개"면 그 후보에 자동 연결한다
     * (배경 폴링 WifiMonitoringService가 이미 '기억된 IP 일치 후보'에만 붙으므로,
     * 전경/배경 정책을 어긋나게 두면 같은 상황에서 결과가 달라진다).
     *
     * 출처 제약: MANUAL_INPUT 후보는 기지 기기여도 자동 연결 대상에서 제외한다
     * (사용자가 IP 버튼으로 명시 연결하는 경로가 이미 있다). SUBNET_SCAN도 제외한다 —
     * 이름·제조사 신호가 없어 Nikon STA 인증 게이트를 통과시킬 수 없고, 공용망에서는 타인 기기다.
     */
    fun decide(
        candidates: List<CameraCandidate>,
        trust: NetworkTrust,
        autoConnectBlocked: Boolean,
        autoConnectApproved: Boolean = true
    ): SelectionOutcome {
        if (candidates.isEmpty()) return SelectionOutcome.Empty
        if (autoConnectBlocked) return SelectionOutcome.RequireSelection(candidates)
        // 지문 불일치로 승인이 회수된 상태 — 다른 본체일 수 있으므로 사용자 확인을 받는다.
        if (!autoConnectApproved) return SelectionOutcome.RequireSelection(candidates)
        if (trust != NetworkTrust.TRUSTED_DIRECT_LINK) {
            return SelectionOutcome.RequireSelection(candidates)
        }
        val autoTargets = candidates.filter {
            it.isKnown &&
                it.camera.discoverySource !in AUTO_CONNECT_EXCLUDED_SOURCES &&
                // 연결 불가 프로토콜(후지 55740 포크 등)은 자동 연결 대상이 아니다 —
                // 목록에는 뜨지만 연결을 시도하면 실패만 반복한다.
                CameraProtocol.ofPort(it.camera.port).isConnectable
        }
        // ⚠️ **IP 단위로 접는다.** 후보 키가 IP:port라 같은 본체가 두 포트를 광고하면 2건이 되고,
        // 그대로 singleOrNull()에 넣으면 null → "기억한 카메라에 자동 연결"이 원인 불명으로
        // 수동 선택으로 강등된다. IP는 곧 기기 1대이므로 같은 IP를 두고 사용자에게 묻는 것은
        // 의미가 없다(목록에 같은 카메라가 두 번 보일 뿐). 서로 다른 **기기**가 2대일 때만 묻는다.
        //
        // 접을 때는 표준 PTP/IP 포트를 우선해 결정적으로 고른다(입력 순서에 의존하지 않는다).
        val target = autoTargets
            .groupBy { it.camera.ipAddress }
            .values
            .map { sameIp ->
                sameIp.firstOrNull { it.camera.port == CameraProtocol.PTPIP_STANDARD.port }
                    ?: sameIp.first()
            }
            .singleOrNull()
            ?: return SelectionOutcome.RequireSelection(candidates)
        return SelectionOutcome.AutoConnect(target.camera)
    }

    /**
     * 제조사 판별 신뢰도 랭크.
     *
     * `CameraVendorClassifier.confidenceRank`(data 레이어)와 동일 의미를 domain 안에서 재정의한다.
     * domain → data 역방향 의존을 만들지 않기 위한 것이며, 입력은 domain 열거형
     * [VendorConfidence] 하나뿐이라 값이 어긋날 여지가 없다.
     */
    internal fun confidenceRank(verdict: VendorVerdict): Int = when (verdict.confidence) {
        VendorConfidence.CONFIRMED -> 2
        VendorConfidence.LIKELY -> 1
        VendorConfidence.UNKNOWN -> 0
    }
}
