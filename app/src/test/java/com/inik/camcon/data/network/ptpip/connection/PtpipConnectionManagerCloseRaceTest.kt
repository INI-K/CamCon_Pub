package com.inik.camcon.data.network.ptpip.connection

import com.inik.camcon.domain.model.PtpSessionState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Issue idx2: closeConnections()가 ptpTransactionMutex 없이 소켓/상태를 변경해
 * openSession()과 race를 일으키던 문제 회귀 테스트.
 *
 * closeConnections()의 상태/소켓 정리 구간을 ptpTransactionMutex.withLock으로 보호한 뒤,
 * (1) 호출 후 세션 상태가 항상 DISCONNECTED로 수렴하고,
 * (2) closeSession 재진입으로 인한 데드락 없이 완료되며,
 * (3) 동시 다중 호출에서도 상태가 손상되지 않음을 검증한다.
 *
 * 소켓이 null인 초기 상태이므로 실 네트워크 없이 상태머신 경로만 단위 검증한다.
 */
class PtpipConnectionManagerCloseRaceTest {

    @Test
    fun `closeConnections는 세션 상태를 DISCONNECTED로 리셋한다`() = runTest {
        val manager = PtpipConnectionManager(StandardTestDispatcher(testScheduler))

        manager.closeConnections()

        assertEquals(PtpSessionState.DISCONNECTED, manager.getSessionState())
        assertFalse(manager.isConnected())
    }

    @Test
    fun `closeConnections는 mutex 재진입 데드락 없이 완료된다`() = runTest {
        val manager = PtpipConnectionManager(StandardTestDispatcher(testScheduler))

        // closeSession=true 경로(내부에서 closeSession→ptpTransactionMutex 사용)도
        // 락 밖에서 호출되므로 정상 완료되어야 한다. (소켓 null이라 closeSession은 스킵)
        manager.closeConnections(closeSession = true)

        assertEquals(PtpSessionState.DISCONNECTED, manager.getSessionState())
    }

    @Test
    fun `동시 closeConnections 호출은 직렬화되어 상태가 손상되지 않는다`() = runTest {
        val manager = PtpipConnectionManager(StandardTestDispatcher(testScheduler))

        val jobs = (1..20).map {
            async { manager.closeConnections() }
        }
        jobs.awaitAll()

        assertEquals(PtpSessionState.DISCONNECTED, manager.getSessionState())
        assertFalse(manager.isSessionReady())
    }
}
