package com.inik.camcon.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.inik.camcon.data.datasource.local.ConnectionReportLocalDataSource
import com.inik.camcon.domain.model.ConnectionReportMethod
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ConnectionReportRepositoryImpl] 단위 테스트.
 *
 * MockK FirebaseFunctions/FirebaseAuth + 상태를 가진 fake local(mockk answers):
 *  (a) 기보고 키 → CF 미호출
 *  (b) 신규 → CF 호출 + 로컬 mark (payload wire 값 포함 검증)
 *  (c) currentUser == null → CF·isReported 미호출
 *  (d) FirebaseFunctionsException(UNAUTHENTICATED/UNAVAILABLE) → 미mark
 *  (e) 성공한 경우에만 mark (일반 예외 실패 시 미mark)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionReportRepositoryImplTest {

    private lateinit var functions: FirebaseFunctions
    private lateinit var callableRef: HttpsCallableReference
    private lateinit var auth: FirebaseAuth
    private lateinit var local: ConnectionReportLocalDataSource

    /** fake local 의 백킹 상태 — markReported 성공 여부를 그대로 반영한다. */
    private val reported = mutableSetOf<String>()

    private fun repo() = ConnectionReportRepositoryImpl(
        functions = functions,
        auth = auth,
        local = local,
        ioDispatcher = UnconfinedTestDispatcher()
    )

    @Before
    fun setUp() {
        reported.clear()

        callableRef = mockk()
        functions = mockk {
            every { getHttpsCallable("reportCameraConnection") } returns callableRef
        }

        auth = mockk()
        // 기본: 로그인 상태
        every { auth.currentUser } returns mockk<FirebaseUser>()

        local = mockk {
            coEvery { isReported(any()) } answers { reported.contains(firstArg()) }
            coEvery { markReported(any()) } answers { reported.add(firstArg()); Unit }
        }
    }

    private fun stubCallSuccess() {
        every { callableRef.call(any()) } returns
            Tasks.forResult(mockk<HttpsCallableResult>(relaxed = true))
    }

    @Test
    fun `이미 보고한 키는 CF 를 호출하지 않는다`() = runTest {
        reported.add("Nikon Z 8|usb")
        stubCallSuccess()

        repo().reportConnection("Nikon Z 8", ConnectionReportMethod.USB)

        verify(exactly = 0) { functions.getHttpsCallable(any()) }
    }

    @Test
    fun `신규 연결은 CF 를 호출하고 로컬에 mark 한다`() = runTest {
        stubCallSuccess()

        repo().reportConnection("Nikon Z 8", ConnectionReportMethod.USB)

        // wire payload 검증: method.wire == "usb"
        verify(exactly = 1) {
            callableRef.call(mapOf("model" to "Nikon Z 8", "method" to "usb"))
        }
        assertTrue("성공 시 로컬 mark 되어야 한다", reported.contains("Nikon Z 8|usb"))
    }

    @Test
    fun `WIFI 신규 연결의 wire 는 wifi 로 전달된다`() = runTest {
        stubCallSuccess()

        repo().reportConnection("Canon EOS R5", ConnectionReportMethod.WIFI)

        verify(exactly = 1) {
            callableRef.call(mapOf("model" to "Canon EOS R5", "method" to "wifi"))
        }
        assertTrue(reported.contains("Canon EOS R5|wifi"))
    }

    @Test
    fun `미로그인 상태면 CF 도 isReported 도 호출하지 않는다`() = runTest {
        every { auth.currentUser } returns null

        repo().reportConnection("Nikon Z 8", ConnectionReportMethod.USB)

        verify(exactly = 0) { functions.getHttpsCallable(any()) }
        coVerifyLocalNeverTouched()
    }

    @Test
    fun `UNAUTHENTICATED 예외면 mark 하지 않는다`() = runTest {
        val ffe = mockk<FirebaseFunctionsException>(relaxed = true)
        every { ffe.code } returns FirebaseFunctionsException.Code.UNAUTHENTICATED
        every { callableRef.call(any()) } returns Tasks.forException(ffe)

        repo().reportConnection("Nikon Z 8", ConnectionReportMethod.USB)

        assertFalse("실패 시 mark 되면 안 된다", reported.contains("Nikon Z 8|usb"))
    }

    @Test
    fun `UNAVAILABLE 예외면 mark 하지 않는다`() = runTest {
        val ffe = mockk<FirebaseFunctionsException>(relaxed = true)
        every { ffe.code } returns FirebaseFunctionsException.Code.UNAVAILABLE
        every { callableRef.call(any()) } returns Tasks.forException(ffe)

        repo().reportConnection("Nikon Z 8", ConnectionReportMethod.USB)

        assertFalse(reported.contains("Nikon Z 8|usb"))
    }

    @Test
    fun `일반 예외로 실패해도 조용히 삼키고 mark 하지 않는다`() = runTest {
        every { callableRef.call(any()) } returns Tasks.forException(RuntimeException("network"))

        // 예외가 밖으로 새지 않아야 한다(UX 영향 0).
        repo().reportConnection("Nikon Z 8", ConnectionReportMethod.USB)

        assertFalse(reported.contains("Nikon Z 8|usb"))
    }

    @Test
    fun `실패 후 재시도하면 다시 CF 를 호출한다`() = runTest {
        // 1차: 일반 예외 실패 → 미mark
        every { callableRef.call(any()) } returns Tasks.forException(RuntimeException("network"))
        repo().reportConnection("Nikon Z 8", ConnectionReportMethod.USB)
        assertFalse(reported.contains("Nikon Z 8|usb"))

        // 2차: 성공 → 호출 + mark (미mark 상태라 재시도 성립)
        stubCallSuccess()
        repo().reportConnection("Nikon Z 8", ConnectionReportMethod.USB)

        verify { callableRef.call(mapOf("model" to "Nikon Z 8", "method" to "usb")) }
        assertTrue(reported.contains("Nikon Z 8|usb"))
    }

    private fun coVerifyLocalNeverTouched() {
        io.mockk.coVerify(exactly = 0) { local.isReported(any()) }
        io.mockk.coVerify(exactly = 0) { local.markReported(any()) }
    }
}
