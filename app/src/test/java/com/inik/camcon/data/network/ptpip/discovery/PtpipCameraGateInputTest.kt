package com.inik.camcon.data.network.ptpip.discovery

import com.inik.camcon.domain.model.CameraDiscoverySource
import com.inik.camcon.domain.model.CameraVendor
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.VendorConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nikon STA 인증 게이트 입력 보존 회귀 테스트 (**최우선 가드**).
 *
 * 배경: 후보 목록 UI를 붙이면서 표시명을 IP 기반으로 바꾸려는 시도가 반복적으로 등장한다.
 * 그런데 `PtpipCamera.name`은 `CameraVendorClassifier.isLikelyNikon`의 유일한 입력이고,
 * 그 결과가 `PtpipDataSource`의 GUID 주입 + `performStaAuthentication` + 승인 데드라인(60s/20s)을
 * 결정한다. 표시용 문자열을 `name`에 대입하면 니콘 판별이 false로 뒤집혀 STA 인증이 생략되고
 * 첫 페어링이 InitFail 0x1로 파손된다(실기 없이는 재현 불가 → 정적 가드로 막는다).
 *
 * 그래서 표시명은 `displayName`(표시 전용)으로 분리했다. 본 테스트는 그 분리가 유지되는지 감시한다.
 */
class PtpipCameraGateInputTest {

    @Test
    fun `displayName을 IP 라벨로 채워도 니콘 게이트는 유지된다`() {
        val camera = PtpipCamera(
            ipAddress = "192.168.49.137",
            port = 15740,
            name = "Z_8_5003869",
            displayName = "카메라 (192.168.49.137)",
            discoverySource = CameraDiscoverySource.MDNS
        )

        assertTrue(
            "displayName 변경이 게이트 판정을 바꾸면 안 된다",
            CameraVendorClassifier.isLikelyNikon(camera)
        )
    }

    @Test
    fun `name 자체를 표시 라벨로 덮으면 게이트가 false로 뒤집힌다(회귀 감시)`() {
        val poisoned = PtpipCamera(
            ipAddress = "192.168.49.137",
            port = 15740,
            // ⚠️ 절대 하지 말아야 할 변경. 이 assert가 깨지면 게이트 입력 규약이 흔들린 것이다.
            name = "카메라 (192.168.49.137)",
            displayName = "카메라 (192.168.49.137)"
        )

        assertFalse(
            "표시명을 name에 넣으면 니콘 판별이 사라진다 — 이 사실을 명시적으로 고정한다",
            CameraVendorClassifier.isLikelyNikon(poisoned)
        )
    }

    @Test
    fun `수동 입력 후보의 name은 IP라 니콘 경로를 타지 않는다(현행 한계 고정)`() {
        val manual = PtpipCamera(
            ipAddress = "192.168.49.137",
            port = 15740,
            name = "192.168.49.137",
            displayName = null,
            discoverySource = CameraDiscoverySource.MANUAL_INPUT
        )

        assertFalse(
            "수동 IP 단독 후보는 벤더 신호가 없다 — 문구로만 안내하는 현행 한계",
            CameraVendorClassifier.isLikelyNikon(manual)
        )
    }

    @Test
    fun `복원 경로 재현 - 저장된 원본 이름으로 verdict가 채워지고 RESTORED로 표시된다`() {
        // PtpipDataSource.restoreLastConnectedCamera 가 만드는 후보와 동일한 형태.
        val storedName = "Z_6_5000784"
        val restored = PtpipCamera(
            ipAddress = "192.168.49.20",
            port = 15740,
            name = storedName,
            isOnline = false,
            vendorVerdict = CameraVendorClassifier.classifyMdns(storedName, null),
            displayName = storedName,
            discoverySource = CameraDiscoverySource.RESTORED
        )

        assertEquals(CameraVendor.NIKON, restored.vendorVerdict.vendor)
        assertEquals(VendorConfidence.LIKELY, restored.vendorVerdict.confidence)
        assertEquals(CameraDiscoverySource.RESTORED, restored.discoverySource)
        assertEquals(storedName, restored.displayName)
        assertTrue(CameraVendorClassifier.isLikelyNikon(restored))
    }

    @Test
    fun `캐시 경로 재현 - 캐시 접미사를 name에 붙이지 않는다`() {
        // 과거 tryCachedIP는 name을 "$cachedName (캐시)"로 접합했다. 접미사가 붙어도 현행
        // 정규식(^[ZD]_)은 통과하지만, 게이트 입력을 문자열 가공하는 관행 자체가 위험하다.
        val original = "Z_8_5003869"
        val cached = PtpipCamera(
            ipAddress = "192.168.49.137",
            port = 15740,
            name = original,
            displayName = original,
            vendorVerdict = CameraVendorClassifier.classifyMdns(original, null),
            discoverySource = CameraDiscoverySource.CACHED_IP
        )

        assertEquals("캐시 후보의 name은 저장된 원본과 바이트 동일해야 한다", original, cached.name)
        assertTrue(CameraVendorClassifier.isLikelyNikon(cached))
    }
}
