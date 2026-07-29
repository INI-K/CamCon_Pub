package com.inik.camcon.domain.model

/**
 * 카메라 후보가 "왜 목록에 있는지"를 나타내는 발견 출처.
 *
 * UI 배지와 자동 연결 정책이 동일한 근거를 읽도록 문자열/불린 플래그 대신 enum으로 고정한다.
 * 각 값의 생산 지점은 아래 KDoc에 못박혀 있으며, 새 값을 추가할 때는 반드시 생산 지점을 함께 명시한다.
 */
enum class CameraDiscoverySource {
    /** `PtpipDiscoveryService` mDNS resolve 결과. */
    MDNS,

    /** `SsdpDiscoveryService.handleResponse` 생성분(UPnP M-SEARCH 응답). */
    SSDP,

    /**
     * 카메라 AP 게이트웨이 추정분.
     * `PtpipDiscoveryCoordinator` AP 경로(findAvailableCameraIP) +
     * `PtpipDiscoveryService` AP 게이트웨이/`DEFAULT_CAMERA_IPS` 경로.
     */
    AP_GATEWAY,

    /** `PtpipDiscoveryService.tryCachedIP` 후보(SharedPreferences 빠른 경로 캐시). */
    CACHED_IP,

    /** `PtpipDiscoveryCoordinator.addManualCamera` 신규 생성분(사용자 IP 직접 입력). */
    MANUAL_INPUT,

    /**
     * `SubnetSweepDiscoverySource` 서브넷 TCP 스윕분(최후 폴백, 사용자 명시 트리거).
     *
     * 이름·제조사 신호가 전혀 없어 Nikon STA 인증 게이트를 통과시킬 수 없다 →
     * **자동 연결 대상에서 영구 제외**하고 UI에서도 격리 섹션에 둔다.
     */
    SUBNET_SCAN,

    /** `PtpipDataSource.restoreLastConnectedCamera` 복원분. 발견 목록에는 올리지 않는다. */
    RESTORED,

    /** default(테스트/레거시 호출부). */
    UNKNOWN;

    /**
     * 같은 IP:port 후보 병합·목록 정렬의 tie-break 우선순위(높을수록 우선).
     *
     * 라이브 신호(MDNS/SSDP)가 추정·캐시 신호보다 정확하므로 우선한다.
     * 데이터 레이어 병합(`PtpipDiscoveryService.mergeCandidates`)과
     * 선택 정책(`CameraSelectionPolicy.buildCandidates`)이 같은 순서를 공유해야
     * "목록에 보이는 순서"와 "자동 연결 대상"이 어긋나지 않는다.
     */
    val priority: Int
        get() = when (this) {
            MDNS -> 7
            SSDP -> 6
            CACHED_IP -> 5
            AP_GATEWAY -> 4
            MANUAL_INPUT -> 3
            SUBNET_SCAN -> 2
            RESTORED -> 1
            UNKNOWN -> 0
        }
}
