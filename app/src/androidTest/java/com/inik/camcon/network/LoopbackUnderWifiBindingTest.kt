package com.inik.camcon.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 소니 SSH 터널 설계의 착수 게이트 검증(D10-i).
 *
 * SSH 로컬 포워딩은 127.0.0.1 리스너에 의존하는데, CamCon은 Wi-Fi 경로에서
 * `bindProcessToNetwork`로 프로세스를 Wi-Fi 네트워크에 바인딩한다. 일부 벤더 커널이
 * SO_MARK 정책 라우팅을 lo 인터페이스까지 적용하면 loopback이 막힐 수 있어 실기 확인이
 * 필요하다. 이 테스트가 실패하면 SSH 클라이언트 소켓만 Network.socketFactory로 만들고
 * 프로세스 전역 바인딩을 푸는 대비책(설계 D10-i)으로 전환해야 한다.
 */
@RunWith(AndroidJUnit4::class)
class LoopbackUnderWifiBindingTest {

    @Test
    fun wifi_바인딩_상태에서_loopback_왕복이_성공한다() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val wifi = cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        // Wi-Fi 미연결 기기에서는 검증 불능이므로 실패가 아니라 스킵으로 처리한다.
        assumeTrue("Wi-Fi 네트워크 없음 - 검증 불능", wifi != null)

        val bound = cm.bindProcessToNetwork(wifi)
        assertTrue("bindProcessToNetwork 실패", bound)
        try {
            val loopback = InetAddress.getByName("127.0.0.1")
            ServerSocket().use { server ->
                server.bind(InetSocketAddress(loopback, 0))
                val port = server.localPort

                val executor = Executors.newSingleThreadExecutor()
                val accepted = executor.submit<ByteArray> {
                    server.accept().use { s ->
                        val buf = ByteArray(4)
                        var read = 0
                        while (read < buf.size) {
                            val n = s.getInputStream().read(buf, read, buf.size - read)
                            check(n > 0) { "스트림 조기 종료" }
                            read += n
                        }
                        s.getOutputStream().write(byteArrayOf(9, 8, 7, 6))
                        s.getOutputStream().flush()
                        buf
                    }
                }

                Socket().use { client ->
                    client.connect(InetSocketAddress(loopback, port), 3000)
                    client.soTimeout = 3000
                    client.getOutputStream().write(byteArrayOf(1, 2, 3, 4))
                    client.getOutputStream().flush()
                    val echo = ByteArray(4)
                    var read = 0
                    while (read < echo.size) {
                        val n = client.getInputStream().read(echo, read, echo.size - read)
                        check(n > 0) { "응답 조기 종료" }
                        read += n
                    }
                    assertEquals(9, echo[0].toInt())
                }

                val received = accepted.get(5, TimeUnit.SECONDS)
                assertEquals(1, received[0].toInt())
                executor.shutdownNow()
            }
        } finally {
            cm.bindProcessToNetwork(null)
        }
    }
}
