package com.inik.camcon.domain.model

/**
 * 발견된 기기가 말하는 카메라 제어 프로토콜. **포트로 역추론한다.**
 *
 * 제조사별 광고 프로토콜(mDNS/SSDP/자체 UDP)을 각각 구현하는 대신, 카메라 제어 포트가 열려 있는지만
 * 보면 제조사를 몰라도 발견이 성립한다 — 이것이 "한 번에 모든 카메라 검색"의 실질적 수단이다.
 *
 * ⚠️ **발견 가능 ≠ 연결 가능.** 앱이 실제로 제어할 수 있는지는 [isConnectable]이 정한다.
 */
enum class CameraProtocol(val port: Int, val isConnectable: Boolean) {

    /**
     * 표준 PTP/IP (ISO 15740). Nikon·Canon·Sony·Panasonic·Ricoh가 모두 이 포트를 쓴다.
     * libgphoto2 범용 `"PTP/IP Camera"` 엔트리로 연결된다.
     */
    PTPIP_STANDARD(15740, isConnectable = true),

    /**
     * 후지필름 PTP/IP 포크. 커맨드 55740 / 이벤트 55741 / JPEG 55742
     * (`camlibs/ptp2/fujiptpip.c` 주석).
     *
     * ⚠️ 현재 **발견만 된다.** libgphoto2에 전송 구현(`ptp_fujiptpip_*`)이 있고 배포 `.so`에도
     * 심볼이 있지만, 전송 선택이 모델명 문자열로 갈리고(`library.c:9661`
     * `if (strstr(a.model,"Fuji"))`) CamCon이 모델을 `"PTP/IP Camera"`로 하드코딩하기 때문에
     * (`camera_ptpip.cpp:250`) 이 경로에 도달하지 못한다. 네이티브에서 모델을 파라미터화하면 열린다.
     */
    PTPIP_FUJI(55740, isConnectable = false);

    companion object {
        /** 스윕이 훑을 포트 집합. 카메라 전용 포트만 넣는다(아래 주석 참조). */
        val SWEEP_PORTS: List<Int> = entries.map { it.port }

        /**
         * 포트 → 프로토콜. 모르는 포트는 표준 PTP/IP로 가정한다(mDNS가 알려준 SRV 포트 등).
         *
         * HTTP 계열 제어 API(Canon CCAPI, Ricoh/OM의 OSC, Sony 구형 Camera Remote API)는
         * **의도적으로 스윕 대상에 넣지 않는다** — 80/8080/443은 공유기·NAS·프린터·IoT가 전부
         * 열어두는 포트라 오탐이 카메라를 압도한다. 그 계열은 SSDP LOCATION처럼 확정 신호가
         * 있을 때만 다뤄야 한다.
         */
        fun ofPort(port: Int): CameraProtocol =
            entries.firstOrNull { it.port == port } ?: PTPIP_STANDARD
    }
}
