package com.inik.camcon.data.datasource.local

import android.app.Application
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [ConnectionReportLocalDataSource] in-memory(Robolectric) DataStore 회귀 테스트.
 *
 * 검증:
 *  - 최초에는 미보고(false)
 *  - markReported 후 isReported true
 *  - 멱등(같은 키 두 번 mark 해도 안전, 여전히 true)
 *  - 다른 키는 서로 영향 없음
 *
 * 주의: `preferencesDataStore("connection_reports")` 는 프로세스 단일 인스턴스라
 * 테스트 메서드 간 상태가 누적된다 → 메서드마다 고유 키를 써서 누수를 회피한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ConnectionReportLocalDataSourceTest {

    private lateinit var dataSource: ConnectionReportLocalDataSource

    @Before
    fun setUp() {
        dataSource = ConnectionReportLocalDataSource(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `최초에는 미보고 상태다`() = runTest {
        assertFalse(dataSource.isReported("fresh:nikon z 8|usb"))
    }

    @Test
    fun `markReported 후 isReported 가 true 다`() = runTest {
        val key = "mark:nikon z 8|usb"
        assertFalse(dataSource.isReported(key))

        dataSource.markReported(key)

        assertTrue(dataSource.isReported(key))
    }

    @Test
    fun `같은 키를 두 번 mark 해도 멱등하게 true 를 유지한다`() = runTest {
        val key = "idem:canon eos r5|wifi"

        dataSource.markReported(key)
        dataSource.markReported(key)

        assertTrue(dataSource.isReported(key))
    }

    @Test
    fun `한 키를 mark 해도 다른 키에는 영향이 없다`() = runTest {
        val marked = "iso:sony a7|usb"
        val other = "iso:sony a7|wifi"

        dataSource.markReported(marked)

        assertTrue(dataSource.isReported(marked))
        assertFalse(dataSource.isReported(other))
    }
}
