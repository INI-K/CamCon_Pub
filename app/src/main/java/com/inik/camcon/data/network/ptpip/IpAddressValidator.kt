package com.inik.camcon.data.network.ptpip

import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * PTP/IP 카메라 연결 대상 IP 주소 검증.
 *
 * DSLR/미러리스 카메라는 항상 사설망(폰 핫스팟, 카메라 AP, 같은 라우터) 내에 존재하므로,
 * 임의의 공인 IP/도메인을 받아 SSRF 류 공격에 노출되지 않도록 화이트리스트 정책을 적용한다.
 *
 * 허용 범위:
 *  - 10.0.0.0/8
 *  - 172.16.0.0/12
 *  - 192.168.0.0/16
 *  - 169.254.0.0/16 (link-local, 카메라 AP가 흔히 사용)
 *  - IPv6 link-local (fe80::/10) 및 unique-local (fc00::/7)
 *  - 127.0.0.0/8 (테스트 / mock 카메라용)
 */
object IpAddressValidator {

    /**
     * 입력값이 IPv4/IPv6 숫자 표기인지 검사한다.
     * 도메인(`example.com`)이나 빈 문자열은 거부한다.
     */
    fun isNumericIp(value: String): Boolean {
        if (value.isBlank()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            InetAddresses.isNumericAddress(value)
        } else {
            // minSdk 29이므로 보통 위 분기만 사용되지만 안전을 위해 폴백 유지.
            Patterns.IP_ADDRESS.matcher(value).matches()
        }
    }

    /**
     * 사설망 / link-local / loopback 범위에 속하는지 검사.
     */
    fun isPrivateOrLinkLocal(value: String): Boolean {
        if (!isNumericIp(value)) return false
        return try {
            val addr = InetAddress.getByName(value)
            when (addr) {
                is Inet4Address -> {
                    val bytes = addr.address
                    val b0 = bytes[0].toInt() and 0xFF
                    val b1 = bytes[1].toInt() and 0xFF
                    when {
                        // 10.0.0.0/8
                        b0 == 10 -> true
                        // 172.16.0.0/12
                        b0 == 172 && b1 in 16..31 -> true
                        // 192.168.0.0/16
                        b0 == 192 && b1 == 168 -> true
                        // 169.254.0.0/16 link-local
                        b0 == 169 && b1 == 254 -> true
                        // 127.0.0.0/8 loopback (mock/테스트)
                        b0 == 127 -> true
                        else -> false
                    }
                }
                is Inet6Address -> addr.isLinkLocalAddress ||
                        addr.isSiteLocalAddress ||
                        addr.isLoopbackAddress ||
                        addr.isAnyLocalAddress.not() && isUniqueLocalIpv6(addr)
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isUniqueLocalIpv6(addr: Inet6Address): Boolean {
        val first = addr.address[0].toInt() and 0xFE
        return first == 0xFC // fc00::/7
    }

    /**
     * `isNumericIp` + `isPrivateOrLinkLocal` 동시 만족 여부.
     * 외부 호출자는 이 메서드 하나만 사용하면 된다.
     */
    fun isAllowedCameraIp(value: String): Boolean {
        return isNumericIp(value) && isPrivateOrLinkLocal(value)
    }
}
