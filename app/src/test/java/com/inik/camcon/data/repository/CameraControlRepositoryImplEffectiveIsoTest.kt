package com.inik.camcon.data.repository

import android.app.Application
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 실효 ISO(Auto ISO) 라이브 표시 게이팅 단위 테스트.
 *
 * 설계서: `.claude/_workspace/eff_iso_design.md`
 * 대상: [CameraControlRepositoryImpl.parseWidgetJsonToSettings] 의 ISO 표시 게이팅(L141~150).
 *
 * 게이팅 규칙:
 *  - d054(autoiso) == "1"            → settings.iso = d0b5(실효 ISO) 우선
 *  - d054 존재 && != "1" (수동)       → manual iso(0x500F, "iso") 우선, d0b5 무시
 *  - d054 부재(Z6/Z7류·미연결)        → 기존 우선순위 value("d0b5","iso","isospeed")  (회귀 0)
 *
 * 검증 방식:
 *  - [parseWidgetJsonToSettings] 는 private 이므로 **public 진입점 [getCameraSettings] 경유**로
 *    실제 게이팅 코드를 그대로 통과시킨다(로직 재현이 아닌 실구현 검증 → 회귀 방어선).
 *  - 게이팅에 무관한 6개 협력자는 relaxed mock. 위젯 JSON 만 [getLiveExposureJson] stub 으로 주입.
 *  - org.json.JSONObject 가 Android 클래스라 Robolectric(sdk=34) 사용. (ExifCaptureTimeTest 선례)
 *  - application = Application::class: 실제 @HiltAndroidApp 은 onCreate 에서 libgphoto2 를 로딩해
 *    호스트 JVM 에서 UnsatisfiedLinkError. 빈 스텁 Application 으로 대체.
 *  - getCameraSettings() 가 내부에서 withContext(Dispatchers.Main) 으로 캐시를 갱신하므로
 *    Dispatchers.setMain + runTest 로 Main 디스패처를 제공한다.
 *
 * 단위 테스트 불가 경로(명시): d0b5/d054 prop 을 카메라에서 실제로 읽어오는 경로
 * (JNI → libgphoto2 generic fallback → camcon_synth_valueread_dpd, 0x1015 GetDevicePropValue)는
 * 실기기·camlib 영역이라 호스트 JVM 단위 테스트로 검증 불가. 본 테스트는 위젯 JSON 이 주어진 뒤의
 * **순수 Kotlin 게이팅 분기**만 검증한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CameraControlRepositoryImplEffectiveIsoTest {

    private lateinit var nativeDataSource: NativeCameraDataSource
    private lateinit var repository: CameraControlRepositoryImpl

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // getCameraSettings() 내부의 withContext(Dispatchers.Main) 용.
        Dispatchers.setMain(mainDispatcher)

        nativeDataSource = mockk(relaxed = true)
        val ptpipDataSource = mockk<PtpipDataSource>(relaxed = true)
        val usbCameraManager = mockk<UsbCameraManager>(relaxed = true)
        val connectionManager = mockk<CameraConnectionManager>(relaxed = true)
        val eventManager = mockk<CameraEventManager>(relaxed = true)
        val downloadManager = mockk<PhotoDownloadManager>(relaxed = true)

        repository = CameraControlRepositoryImpl(
            nativeDataSource = nativeDataSource,
            ptpipDataSource = ptpipDataSource,
            usbCameraManager = usbCameraManager,
            connectionManager = connectionManager,
            eventManager = eventManager,
            downloadManager = downloadManager,
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 위젯 노드 1개 JSON 조각. parseWidgetJsonToSettings.walk() 는 name/value/children 만 본다.
     */
    private fun node(name: String, value: String): String =
        """{"name":"$name","value":"$value"}"""

    /**
     * getWidgetJsonFromSource() 의 경량 getter 필터를 통과하는 루트 JSON 을 만든다.
     * (light.isNotBlank() && !"error" && contains "name") — 루트에도 name 을 둬 보장.
     */
    private fun widgetJson(vararg children: String): String =
        """{"name":"main","children":[${children.joinToString(",")}]}"""

    /** getLiveExposureJson() 가 주어진 JSON 을 반환하도록 stub. */
    private fun stubLiveExposure(json: String) {
        every { nativeDataSource.getLiveExposureJson() } returns json
    }

    // ──────────────────────────────────────────────────────────────────────
    // (a) Auto ISO ON: d054="1" → 실효 ISO(d0b5) 우선
    // ──────────────────────────────────────────────────────────────────────
    @Test
    fun autoIsoOn_usesEffectiveIso_d0b5() = runTest {
        stubLiveExposure(
            widgetJson(
                node("d054", "1"),
                node("d0b5", "900"),
                node("iso", "64")
            )
        )

        val settings = repository.getCameraSettings().getOrThrow()

        assertEquals("Auto ISO ON 이면 실효 ISO(d0b5)=900 을 써야 한다", "900", settings.iso)
    }

    // ──────────────────────────────────────────────────────────────────────
    // (b) Auto ISO OFF: d054="0" → manual iso 우선 (d0b5 무시)
    // ──────────────────────────────────────────────────────────────────────
    @Test
    fun autoIsoOff_usesManualIso() = runTest {
        stubLiveExposure(
            widgetJson(
                node("d054", "0"),
                node("d0b5", "900"),
                node("iso", "64")
            )
        )

        val settings = repository.getCameraSettings().getOrThrow()

        assertEquals("Auto ISO OFF 면 manual iso=64 를 써야 한다(d0b5 무시)", "64", settings.iso)
    }

    // ──────────────────────────────────────────────────────────────────────
    // (c) 회귀: 게이팅 정보(d054/d0b5) 부재 → manual iso 그대로
    // ──────────────────────────────────────────────────────────────────────
    @Test
    fun noGatingInfo_fallsBackToManualIso() = runTest {
        stubLiveExposure(
            widgetJson(
                node("iso", "100")
            )
        )

        val settings = repository.getCameraSettings().getOrThrow()

        assertEquals("게이팅 정보 없으면 기존 동작(iso=100) 유지 — 회귀 0", "100", settings.iso)
    }

    // ──────────────────────────────────────────────────────────────────────
    // (d) 회귀: d0b5 만 있고 d054 부재(Z6/Z7류) → 기존 우선순위대로 d0b5 우선
    // ──────────────────────────────────────────────────────────────────────
    @Test
    fun d0b5WithoutD054_keepsLegacyPriority_d0b5First() = runTest {
        stubLiveExposure(
            widgetJson(
                node("d0b5", "800"),
                node("iso", "200")
            )
        )

        val settings = repository.getCameraSettings().getOrThrow()

        // 기존 우선순위 value("d0b5","iso","isospeed") → d0b5 가 맨 앞.
        assertEquals("d054 부재 시 기존 우선순위로 d0b5=800 이 먼저", "800", settings.iso)
    }

    // ──────────────────────────────────────────────────────────────────────
    // 경계: Auto ISO ON 인데 d0b5 가 빈 문자열 → "iso" 로 폴백
    // ──────────────────────────────────────────────────────────────────────
    @Test
    fun autoIsoOn_emptyD0b5_fallsBackToIso() = runTest {
        stubLiveExposure(
            widgetJson(
                node("d054", "1"),
                node("d0b5", ""),       // 빈 값 → value() 가 건너뛴다
                node("iso", "64")
            )
        )

        val settings = repository.getCameraSettings().getOrThrow()

        assertEquals("d0b5 가 빈 문자열이면 다음 우선순위 iso=64 로 폴백", "64", settings.iso)
    }

    // ──────────────────────────────────────────────────────────────────────
    // 경계: Auto ISO ON 인데 d0b5 노드 자체 누락 → "iso" 로 폴백
    // ──────────────────────────────────────────────────────────────────────
    @Test
    fun autoIsoOn_missingD0b5Node_fallsBackToIso() = runTest {
        stubLiveExposure(
            widgetJson(
                node("d054", "1"),
                node("iso", "320")      // d0b5 노드 없음
            )
        )

        val settings = repository.getCameraSettings().getOrThrow()

        assertEquals("d0b5 노드가 없으면 manual iso=320 로 폴백", "320", settings.iso)
    }

    // ──────────────────────────────────────────────────────────────────────
    // 경계: Auto ISO OFF 인데 manual iso 누락 → d0b5 로 최종 폴백
    // (게이팅 분기 value("iso","isospeed","d0b5") 의 마지막 항목 검증)
    // ──────────────────────────────────────────────────────────────────────
    @Test
    fun autoIsoOff_missingManualIso_fallsBackToD0b5() = runTest {
        stubLiveExposure(
            widgetJson(
                node("d054", "0"),
                node("d0b5", "1600")    // iso/isospeed 없음
            )
        )

        val settings = repository.getCameraSettings().getOrThrow()

        assertEquals("manual OFF 인데 iso 누락 시 d0b5=1600 으로 폴백", "1600", settings.iso)
    }

    // ──────────────────────────────────────────────────────────────────────
    // 게이팅이 다른 노출 필드를 건드리지 않음(스코프 가드)
    // ──────────────────────────────────────────────────────────────────────
    @Test
    fun gatingDoesNotAffectOtherSettings() = runTest {
        stubLiveExposure(
            widgetJson(
                node("d054", "1"),
                node("d0b5", "900"),
                node("iso", "64"),
                node("shutterspeed", "1/250"),
                node("f-number", "2.8"),
                node("whitebalance", "Auto"),
                node("exposurecompensation", "0")
            )
        )

        val settings = repository.getCameraSettings().getOrThrow()

        assertEquals("900", settings.iso)
        assertEquals("1/250", settings.shutterSpeed)
        assertEquals("2.8", settings.aperture)
        assertEquals("Auto", settings.whiteBalance)
        assertEquals("0", settings.exposureCompensation)
    }

    // ──────────────────────────────────────────────────────────────────────
    // d054 가 "2" 같은 비표준 truthy 값이어도 "1" 이 아니면 manual 경로
    // (게이팅 규칙: == "1" 만 Auto, 그 외 존재값은 모두 manual)
    // ──────────────────────────────────────────────────────────────────────
    @Test
    fun d054NonOneValue_treatedAsManual() = runTest {
        stubLiveExposure(
            widgetJson(
                node("d054", "2"),
                node("d0b5", "900"),
                node("iso", "64")
            )
        )

        val settings = repository.getCameraSettings().getOrThrow()

        assertEquals("d054 가 '1' 이 아니면(예 '2') manual iso=64", "64", settings.iso)
    }

    // ──────────────────────────────────────────────────────────────────────
    // 모든 노출 필드 부재 → null(설정 미상) → Result.failure (가짜 값 방지)
    // ──────────────────────────────────────────────────────────────────────
    @Test
    fun allEmpty_returnsFailure() = runTest {
        // name 은 있으나(필터 통과) value 가 모두 비어 파서가 null 을 반환하는 경우.
        stubLiveExposure(widgetJson(node("d054", "1")))

        val result = repository.getCameraSettings()

        assertTrue("모든 노출 값이 비면 파싱 null → Result.failure", result.isFailure)
    }
}
