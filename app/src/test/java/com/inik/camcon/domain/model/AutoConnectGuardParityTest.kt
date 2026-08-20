package com.inik.camcon.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 전경/배경 자동 연결 가드 **동등성** 가드(정적 소스 스캔).
 *
 * 회귀 배경: 자동 연결 금지 조건의 정본은 `PtpipDataSource.isAutoConnectBlocked()`
 * (= 검색금지 + USB 활성 + 영상녹화 + 라이브뷰 종료전이 + 취소 쿨다운)인데, `PtpipRepository`에는
 * 약한 `isDiscoveryBlocked()`만 노출돼 있어 **전경(ViewModel)만 약한 가드**를 썼다. 결과:
 * USB 카메라가 살아 있는 상태에서 Wi-Fi 화면 진입 → 무탭 자동 연결 → `initCameraWithPtpip`가
 * 공유 네이티브 핸들을 무경고 파괴(J8 회귀). 배경은 강한 가드를 써서 같은 상황에서 결과가 갈렸다.
 */
class AutoConnectGuardParityTest {

    private val viewModel =
        File("src/main/java/com/inik/camcon/presentation/viewmodel/PtpipViewModel.kt")
    private val service =
        File("src/main/java/com/inik/camcon/data/service/WifiMonitoringService.kt")
    private val repository =
        File("src/main/java/com/inik/camcon/domain/repository/PtpipRepository.kt")

    @Test
    fun `도메인 인터페이스가 강한 가드를 노출한다`() {
        require(repository.isFile) { "테스트는 :app 모듈에서 실행되어야 함" }

        assertTrue(
            "PtpipRepository에 isAutoConnectBlocked()가 없으면 전경은 약한 가드를 쓸 수밖에 없다",
            repository.readText().contains("fun isAutoConnectBlocked(): Boolean")
        )
    }

    @Test
    fun `전경 자동 연결 분기가 약한 가드를 쓰지 않는다`() {
        val source = viewModel.readText()

        // 자동 연결 판정(decide 인자)과 직전 재확인 모두 강한 가드여야 한다.
        assertTrue(
            "전경 자동 연결이 isAutoConnectBlocked를 쓰지 않는다",
            source.contains("autoConnectBlocked = ptpipRepository.isAutoConnectBlocked()")
        )
        assertFalse(
            "전경 자동 연결 분기에 약한 가드(isDiscoveryBlocked)가 남아 있다",
            source.contains("autoConnectBlocked = ptpipRepository.isDiscoveryBlocked()")
        )
    }

    @Test
    fun `배경 폴링도 같은 강한 가드를 통과한다`() {
        assertTrue(
            "배경 폴링 게이트에서 isAutoConnectBlocked가 빠지면 취소 쿨다운·USB 가드가 무력화된다",
            service.readText().contains("isAutoConnectBlocked()")
        )
    }
}
