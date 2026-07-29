package com.inik.camcon.data.network.ptpip.wifi

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * softAP 인터페이스 이름 판정 가드 — **앱 전체의 단일 실패점**이다.
 *
 * 이 판정이 false를 내면 연쇄적으로 전부 죽는다:
 * 1. `isHotspotEnabled()` = false
 * 2. `PtpipDiscoveryCoordinator`가 "Wi-Fi도 핫스팟도 아님"으로 보고 **검색을 시작조차 안 함**
 * 3. `localIpv4Prefix()`도 실패 → **서브넷 스윕까지 비활성**
 *
 * 사용자에게는 "핫스팟을 켰는데 카메라를 못 찾음"으로 나타난다 — 카메라 문제가 아니라 우리 문제다.
 * 그래서 OEM별 인터페이스 이름 변형을 여기서 고정한다.
 */
class SoftApInterfaceNameTest {

    private lateinit var helper: WifiNetworkHelper

    @Before
    fun setUp() {
        // 이름 판정 자체는 순수 문자열 로직이지만, 생성자가 시스템 서비스를 즉시 캐스팅하므로
        // WifiManager/ConnectivityManager 목을 정확한 타입으로 돌려줘야 한다.
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.WIFI_SERVICE) } returns
            mockk<android.net.wifi.WifiManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns
            mockk<android.net.ConnectivityManager>(relaxed = true)
        helper = WifiNetworkHelper(context, kotlinx.coroutines.Dispatchers.Unconfined)
    }

    @Test
    fun `대표적인 softAP 인터페이스 이름을 인식한다`() {
        listOf(
            "ap0",          // Pixel 등 표준
            "ap1",
            "swlan0",       // 삼성
            "softap0",
            "ap_br0",       // 브리지형
            "bridge0",
            "wlan1",        // 동시 동작 칩셋이 SoftAP를 두 번째 wlan으로 올린다
            "wlan2",
            "wl0.1"         // Broadcom 가상 AP
        ).forEach { name ->
            assertTrue("softAP 인터페이스를 놓쳤다: $name", helper.isSoftApInterfaceName(name))
        }
    }

    @Test
    fun `클라이언트 wlan0은 softAP로 보지 않는다`() {
        // wlan0을 softAP로 오인하면 공유기에 붙은 상태를 핫스팟으로 착각해
        // trustOf()가 TRUSTED_DIRECT_LINK를 내고 공용망에서 자동 연결이 열린다.
        assertFalse(helper.isSoftApInterfaceName("wlan0"))
        assertFalse(helper.isSoftApInterfaceName("wlan0.1"))
    }

    @Test
    fun `USB 테더링과 VPN 터널은 배제한다`() {
        // 이들도 사설 IPv4를 갖기 때문에 이름으로 먼저 걸러야 오탐이 없다.
        listOf("rndis0", "usb0", "tun0", "ppp0", "lo", "dummy0", "sit0", "ip6tnl0", "docker0")
            .forEach { name ->
                assertFalse("무관 인터페이스를 softAP로 오인: $name", helper.isSoftApInterfaceName(name))
            }
    }

    @Test
    fun `라우터 클라이언트 모드 apcli는 배제한다`() {
        // "apcli0"은 일부 칩셋의 AP 클라이언트 모드로 softAP가 아니다.
        // 정규식이 `^(ap|swlan|softap)\d` 이므로 ap 뒤에 숫자가 와야 매칭된다.
        assertFalse(helper.isSoftApInterfaceName("apcli0"))
    }
}
