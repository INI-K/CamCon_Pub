package com.inik.camcon.domain.model

/**
 * 카메라 검색 결과의 출처. 후속 분기와 신뢰도 판단에 사용.
 */
@Suppress("unused")
enum class EndpointSource {
    /** mDNS(NsdManager)가 광고를 받아 발견. */
    MDNS,

    /** 현재 네트워크의 게이트웨이 IP를 카메라로 추정 (AP 모드 휴리스틱). */
    GATEWAY,

    /** 사용자가 직접 입력한 IP. */
    MANUAL_INPUT,

    /** DataStore에 저장된 마지막 연결 IP. */
    SAVED,
}

/**
 * 카메라 후보 엔드포인트. [PtpipCamera]의 상위 추상화이며, 검색 출처와 메타데이터를 보존한다.
 *
 * 신규 코드는 가능한 [CameraEndpoint]를 우선 사용하고, 외부 호출용으로 [PtpipCamera]로 변환한다.
 * 현재 origin 호출부 없음 — follow-up에서 PtpipDiscoveryService 마이그레이션 시드.
 */
@Suppress("unused")
data class CameraEndpoint(
    val ipAddress: String,
    val port: Int,
    val name: String,
    val source: EndpointSource,
    val manufacturer: String? = null,
    val model: String? = null,
) {
    fun toPtpipCamera(): PtpipCamera =
        PtpipCamera(ipAddress = ipAddress, port = port, name = name, isOnline = true)
}

fun PtpipCamera.toEndpoint(source: EndpointSource = EndpointSource.SAVED): CameraEndpoint =
    CameraEndpoint(ipAddress = ipAddress, port = port, name = name, source = source)
