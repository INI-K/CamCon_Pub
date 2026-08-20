package com.inik.camcon.data.service

import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.domain.repository.PtpipRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.reflect.full.functions

/**
 * 가드 유실 방지 아키텍처 테스트 (정적 소스 스캔 + 계약 reflection).
 *
 * 두 종류의 회귀를 막는다:
 *
 * 1. **J8 USB 가드 유실** — `WifiMonitoringService.isCameraBusy()`의 4항
 *    (`isUsbCameraActive` / `isVideoRecording` / `isLiveViewStopping` / `isPhotoPreviewMode`)은
 *    살아있는 USB 세션·촬영 중에 폴링 연결이 공유 네이티브 핸들을 파괴하는 것을 막는다.
 *    자동연결 단일 지점 이관(Wave 3) 과정에서 조건이 이동·약화되면 USB 물리셔터 수신이 영구 사망한다.
 *
 * 2. **도메인 계약 파손** — `PtpipRepository.discoverCameras`는 1-파라미터 형태를 유지해야 한다.
 *    도메인 인터페이스에 default 인자를 늘리면 Kotlin이 `$default` 브리지를 만들어 기존 MockK
 *    스텁이 취약해진다. 예산 파라미터는 DataSource를 직접 보유한 서비스에만 노출한다.
 */
class WifiMonitoringGuardPreservedTest {

    private val serviceSource =
        File("src/main/java/com/inik/camcon/data/service/WifiMonitoringService.kt")

    private val dataSourceSource =
        File("src/main/java/com/inik/camcon/data/datasource/ptpip/PtpipDataSource.kt")

    @Test
    fun `J8 USB 가드 4항이 배경 폴링 경로에 살아 있다`() {
        require(serviceSource.isFile) {
            "테스트는 :app 모듈 디렉터리에서 실행되어야 함: ${serviceSource.absolutePath}"
        }
        require(dataSourceSource.isFile) {
            "PtpipDataSource를 찾을 수 없음: ${dataSourceSource.absolutePath}"
        }
        // Wave 3에서 USB·녹화·라이브뷰 조건의 **정의**는 PtpipDataSource.isAutoConnectBlocked()
        // 한 곳으로 모았고 서비스는 그 함수에 위임한다. 조건 자체의 소멸을 막으려면 두 파일의
        // union으로 봐야 한다 — 어느 한쪽에서 없어져도 실패해야 한다.
        val union = serviceSource.readText() + "\n" + dataSourceSource.readText()

        val requiredTerms = listOf(
            "isUsbCameraActive()",
            "isVideoRecording()",
            "isLiveViewStopping()",
            "isPhotoPreviewMode()"
        )
        val missing = requiredTerms.filterNot { union.contains(it) }

        assertTrue(
            "USB/촬영 중 폴링 연결 차단 조건 유실: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun `프리뷰 탭 조건은 서비스에 남고 나머지는 단일 정의에 있다`() {
        val service = serviceSource.readText()
        val dataSource = dataSourceSource.readText()

        // 프리뷰 탭 판정 원천은 CameraRepository(도메인)라 data 파사드로 끌어올리면 의존이 역전된다.
        assertTrue(
            "프리뷰 탭 가드가 서비스에서 사라졌다",
            service.contains("isPhotoPreviewMode()")
        )
        // 조건 정의 복제 금지 — 갈리면 전경과 배경이 같은 상황에서 다르게 행동한다.
        listOf(
            "isUsbCameraActive()",
            "isVideoRecording()",
            "isLiveViewStopping()"
        ).forEach { term ->
            assertTrue(
                "$term 이 단일 정의(PtpipDataSource.isAutoConnectBlocked)에 없다",
                dataSource.contains(term)
            )
        }
    }

    @Test
    fun `배경 폴링이 자동연결 차단 게이트를 통과한다`() {
        val source = serviceSource.readText()
        assertTrue(
            "폴링 게이트에 isAutoConnectBlocked() 항이 없으면 취소 쿨다운/USB 가드가 무력화된다",
            source.contains("isAutoConnectBlocked()")
        )
        assertTrue(
            "배경 폴링은 응답성 보존을 위해 BackgroundReconnect 예산을 써야 한다",
            source.contains("DiscoveryBudget.BackgroundReconnect")
        )
    }

    @Test
    fun `PtpipRepository discoverCameras는 1-파라미터 형태를 유지한다`() {
        val fns = PtpipRepository::class.functions.filter { it.name == "discoverCameras" }
        assertEquals("discoverCameras 오버로드가 늘어나면 도메인 계약이 파손된다", 1, fns.size)
        // receiver + forceApMode = 2
        assertEquals(2, fns.single().parameters.size)
    }

    @Test
    fun `PtpipRepository는 취소·가드 API를 노출한다`() {
        val names = PtpipRepository::class.functions.map { it.name }.toSet()
        assertTrue("requestConnectCancel 누락", "requestConnectCancel" in names)
        assertTrue("isDiscoveryBlocked 누락", "isDiscoveryBlocked" in names)
    }

    @Test
    fun `PtpipDataSource connectToCamera 오버로드 2종이 유지된다`() {
        val fns = PtpipDataSource::class.functions.filter { it.name == "connectToCamera" }
        assertEquals(
            "connectToCamera(camera, forceApMode) / (camera, ConnectionMethod) 2종 유지",
            2,
            fns.size
        )
    }
}
