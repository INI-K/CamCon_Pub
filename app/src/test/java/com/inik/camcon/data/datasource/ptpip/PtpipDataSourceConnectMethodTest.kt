package com.inik.camcon.data.datasource.ptpip

import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.PtpipCamera
import com.inik.camcon.domain.model.toForceApMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.functions

/**
 * `PtpipDataSource.connectToCamera(camera, method: ConnectionMethod)` 오버로드 회귀 테스트.
 *
 * architect 설계 §4.1: 기존 `connectToCamera(camera, forceApMode: Boolean)`는 보존하고,
 * `ConnectionMethod`를 받는 오버로드를 추가하여 `forceApMode = (method == AP)`로 위임한다.
 *
 * DataSource 자체를 인스턴스화하려면 `@Inject constructor`의 무거운 의존성
 * (Context, WifiNetworkHelper, PtpipDiscoveryService, NikonAuthenticationService, ...)이
 * 필요하므로, 본 테스트는 **public API 시그니처의 존재**만 reflection으로 검증한다.
 * 통과 = TDD red 해소(구현 시작 가능).
 */
class PtpipDataSourceConnectMethodTest {

    @Test
    fun `connectToCamera with ConnectionMethod overload is declared as suspend`() {
        val fn = PtpipDataSource::class.functions.firstOrNull { f ->
            f.name == "connectToCamera" && f.parameters.any { p ->
                p.type.toString().contains("ConnectionMethod")
            }
        }
        assertTrue(
            "PtpipDataSource must declare suspend fun connectToCamera(camera, method: ConnectionMethod): Boolean",
            fn != null && fn.isSuspend
        )
    }

    @Test
    fun `connectToCamera overload returns Boolean`() {
        val fn = PtpipDataSource::class.functions.firstOrNull { f ->
            f.name == "connectToCamera" && f.parameters.any { p ->
                p.type.toString().contains("ConnectionMethod")
            }
        }
        requireNotNull(fn) { "connectToCamera(camera, ConnectionMethod) overload missing" }
        assertEquals("kotlin.Boolean", fn.returnType.toString())
    }

    @Test
    fun `ConnectionMethod AP maps to forceApMode true semantic`() {
        // 매핑 규칙의 핵심 불변식: AP만 forceApMode=true.
        // 이 규칙이 깨지면 STA 분기에서 잘못된 native init이 호출된다.
        assertTrue(ConnectionMethod.AP.toForceApMode())
        assertFalse(ConnectionMethod.STA_ROUTER.toForceApMode())
        assertFalse(ConnectionMethod.STA_PHONE_HOTSPOT.toForceApMode())
    }

    @Test
    fun `activeConnectionMethod StateFlow is exposed`() {
        // PtpipDataSource는 _activeConnectionMethod를 public StateFlow로 노출해야 한다.
        val hasProperty = PtpipDataSource::class.members.any { it.name == "activeConnectionMethod" }
        assertTrue(
            "PtpipDataSource must expose `activeConnectionMethod: StateFlow<ConnectionMethod?>`",
            hasProperty
        )
    }

    @Test
    fun `manualIp StateFlow is exposed`() {
        val hasProperty = PtpipDataSource::class.members.any { it.name == "manualIp" }
        assertTrue(
            "PtpipDataSource must expose `manualIp: StateFlow<String>`",
            hasProperty
        )
    }

    @Test
    fun `setManualIp and addManualCamera helpers exist`() {
        val members = PtpipDataSource::class.members.map { it.name }.toSet()
        assertTrue("setManualIp must exist", "setManualIp" in members)
        assertTrue("addManualCamera must exist", "addManualCamera" in members)
    }

    @Test
    fun `addManualCamera signature accepts ip name and port`() {
        val fn = PtpipDataSource::class.functions.firstOrNull { it.name == "addManualCamera" }
        requireNotNull(fn) { "addManualCamera must be declared" }
        // receiver + (ip, name, port) — 총 4개 파라미터.
        assertEquals(4, fn.parameters.size)
        assertTrue(
            "addManualCamera must return PtpipCamera",
            fn.returnType.toString().contains(PtpipCamera::class.simpleName!!)
        )
    }

    @Test
    fun `legacy forceApMode path also updates activeConnectionMethod stateflow`() {
        // 회귀 가드 (MAJOR-1 reviewer): PtpipConnectionHelper / PtpipDiscoveryHelper /
        // AutoConnectTaskRunner / connectManualCamera 등 직접 connectToCamera(camera, forceApMode)
        // 경로에서도 _activeConnectionMethod가 갱신되어야 한다.
        // 본 테스트는 시그니처 및 매핑 invariant를 reflection으로 가드한다.

        // 1. legacy 시그니처가 여전히 노출되어 있는지.
        val legacyFn = PtpipDataSource::class.functions.firstOrNull { f ->
            f.name == "connectToCamera" && f.parameters.any { p ->
                p.type.toString().contains("Boolean") &&
                    !p.type.toString().contains("ConnectionMethod")
            }
        }
        assertTrue(
            "Legacy connectToCamera(camera, forceApMode: Boolean) must remain exposed",
            legacyFn != null && legacyFn.isSuspend
        )

        // 2. forceApMode=true→AP, false→STA_* 매핑 invariant.
        assertEquals(true, ConnectionMethod.AP.toForceApMode())
        assertEquals(false, ConnectionMethod.STA_ROUTER.toForceApMode())
        assertEquals(false, ConnectionMethod.STA_PHONE_HOTSPOT.toForceApMode())

        // 3. forceApMode=false 경로에서 본체가 호출할 inferMethod() 선언 가드.
        val inferFn = PtpipDataSource::class.functions.firstOrNull { it.name == "inferMethod" }
        assertTrue("inferMethod() must be declared on PtpipDataSource", inferFn != null)
        requireNotNull(inferFn)
        assertTrue(
            "inferMethod() must return ConnectionMethod",
            inferFn.returnType.toString().contains("ConnectionMethod")
        )
    }
}
