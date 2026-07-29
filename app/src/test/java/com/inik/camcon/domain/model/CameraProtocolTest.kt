package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 프로토콜 역추론 가드.
 *
 * "한 번에 모든 카메라 검색"은 제조사별 광고 프로토콜을 각각 구현하는 대신 **카메라 전용 포트 집합**을
 * 훑는 것으로 성립한다. 그래서 이 파일이 지키는 두 계약:
 *
 * 1. 스윕 포트 집합에 **HTTP 계열(80/8080/443)이 절대 들어가지 않는다** — 공유기·NAS·프린터·IoT가
 *    전부 열어두는 포트라 오탐이 카메라를 압도한다.
 * 2. **발견 가능 ≠ 연결 가능**이 코드로 구분된다 — 후지 포크는 발견만 되고, UI가 탭을 막아야 한다.
 */
class CameraProtocolTest {

    @Test
    fun `스윕 포트는 표준 PTP-IP와 후지 포크 두 종뿐이다`() {
        assertEquals(listOf(15740, 55740), CameraProtocol.SWEEP_PORTS)
    }

    @Test
    fun `스윕 포트에 HTTP 계열이 없다`() {
        // 80/8080/443을 넣으면 공유기·NAS·프린터가 전부 "카메라"로 잡힌다.
        listOf(80, 443, 8080, 8000, 631).forEach { noisy ->
            assertFalse("노이즈 포트가 스윕에 포함됨: $noisy", CameraProtocol.SWEEP_PORTS.contains(noisy))
        }
    }

    @Test
    fun `표준 포트는 연결 가능하다`() {
        val protocol = CameraProtocol.ofPort(15740)

        assertEquals(CameraProtocol.PTPIP_STANDARD, protocol)
        assertTrue(protocol.isConnectable)
    }

    @Test
    fun `후지 포크 포트는 발견되지만 연결 불가로 표시된다`() {
        // libgphoto2에 전송 구현(ptp_fujiptpip_*)이 있고 .so에도 심볼이 있으나, 전송 선택이
        // 모델명 문자열로 갈리고(library.c: strstr(a.model,"Fuji")) CamCon이 모델을
        // "PTP/IP Camera"로 하드코딩하므로 도달하지 못한다. 네이티브 수정 시 true로 바뀐다.
        val protocol = CameraProtocol.ofPort(55740)

        assertEquals(CameraProtocol.PTPIP_FUJI, protocol)
        assertFalse("후지 연결이 열렸다면 이 테스트와 UI 게이트를 함께 갱신해야 한다", protocol.isConnectable)
    }

    @Test
    fun `모르는 포트는 표준 PTP-IP로 가정한다`() {
        // mDNS SRV가 알려준 비표준 포트를 배제하면 실제 카메라를 놓친다.
        assertEquals(CameraProtocol.PTPIP_STANDARD, CameraProtocol.ofPort(15741))
        assertEquals(CameraProtocol.PTPIP_STANDARD, CameraProtocol.ofPort(0))
    }
}
