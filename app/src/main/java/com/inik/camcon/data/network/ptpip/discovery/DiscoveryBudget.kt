package com.inik.camcon.data.network.ptpip.discovery

/**
 * 카메라 검색 1회의 시간 예산.
 *
 * 이 값이 mDNS 검색 전체 예산(과거 서비스 타입별 5000ms 하드코딩)과
 * `SsdpDiscoveryService.discover(timeoutMs)` 인자를 **동시에** 지배한다.
 * `PtpipConstants.DISCOVERY_TIMEOUT`·`CACHED_IP_TIMEOUT`은 건드리지 않는다(다른 소비처 보호).
 *
 * 예산이 2종인 이유:
 * - 사용자 주도 검색은 후보를 다 모아야 하므로 누적형(조기 종료 금지)이다.
 * - 백그라운드 4초 폴링(`WifiMonitoringService`)은 현행 응답성을 잃으면 자동 재연결 체감이
 *   붕괴하므로 예산을 낮추고 기지 IP 조기 확정을 허용한다.
 *
 * enum으로 못박아 호출부가 임의 숫자를 넣지 못하게 한다.
 *
 * @param totalMs mDNS+SSDP 검색 전체 상한
 * @param resolveTimeoutMs mDNS resolve 1건 상한(직렬 큐에서 소비)
 * @param allowEarlyConfirmOnKnownIp 캐시 IP가 DataStore 기지 IP와 같으면 즉시 반환 허용
 */
enum class DiscoveryBudget(
    val totalMs: Long,
    val resolveTimeoutMs: Long,
    val allowEarlyConfirmOnKnownIp: Boolean
) {
    UserInitiated(totalMs = 3_000L, resolveTimeoutMs = 1_000L, allowEarlyConfirmOnKnownIp = false),
    BackgroundReconnect(totalMs = 1_500L, resolveTimeoutMs = 700L, allowEarlyConfirmOnKnownIp = true)
}
