package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.ConnectionReportMethod
import com.inik.camcon.domain.repository.ConnectionReportRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * [ReportCameraConnectionUseCase] 정크 필터 단위 테스트.
 *
 * 검증(위임만 — 정규화는 CF 담당이므로 미검증):
 *  - 제네릭/미확정 드라이버명·공백·"알 수 없음"·"generic" 포함 → repo 위임 0회
 *  - 정상 기종명 → trim 후 repo 위임 1회 + method 그대로 전달
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportCameraConnectionUseCaseTest {

    private lateinit var repo: ConnectionReportRepository
    private lateinit var useCase: ReportCameraConnectionUseCase

    @Before
    fun setUp() {
        repo = mockk()
        coEvery { repo.reportConnection(any(), any()) } just Runs
        useCase = ReportCameraConnectionUseCase(repo)
    }

    @Test
    fun `공백 문자열은 no-op`() = runTest {
        useCase("", ConnectionReportMethod.USB)
        useCase("   ", ConnectionReportMethod.WIFI)
        coVerify(exactly = 0) { repo.reportConnection(any(), any()) }
    }

    @Test
    fun `PTP IP Camera 드라이버명은 no-op`() = runTest {
        useCase("PTP/IP Camera", ConnectionReportMethod.WIFI)
        coVerify(exactly = 0) { repo.reportConnection(any(), any()) }
    }

    @Test
    fun `PTP IP Camera 대소문자 무시 no-op`() = runTest {
        useCase("ptp/ip camera", ConnectionReportMethod.WIFI)
        coVerify(exactly = 0) { repo.reportConnection(any(), any()) }
    }

    @Test
    fun `USB PTP Class Camera 드라이버명은 no-op`() = runTest {
        useCase("USB PTP Class Camera", ConnectionReportMethod.USB)
        coVerify(exactly = 0) { repo.reportConnection(any(), any()) }
    }

    @Test
    fun `알 수 없음은 no-op`() = runTest {
        useCase("알 수 없음", ConnectionReportMethod.USB)
        coVerify(exactly = 0) { repo.reportConnection(any(), any()) }
    }

    @Test
    fun `generic 포함 문자열은 no-op`() = runTest {
        useCase("Generic PTP Camera", ConnectionReportMethod.USB)
        useCase("Sony Generic", ConnectionReportMethod.WIFI)
        coVerify(exactly = 0) { repo.reportConnection(any(), any()) }
    }

    @Test
    fun `정상 기종 USB 는 1회 위임되고 method 가 전달된다`() = runTest {
        useCase("Nikon Z 8", ConnectionReportMethod.USB)
        coVerify(exactly = 1) { repo.reportConnection("Nikon Z 8", ConnectionReportMethod.USB) }
    }

    @Test
    fun `정상 기종 WIFI 는 1회 위임되고 method 가 전달된다`() = runTest {
        useCase("Canon EOS R5", ConnectionReportMethod.WIFI)
        coVerify(exactly = 1) { repo.reportConnection("Canon EOS R5", ConnectionReportMethod.WIFI) }
    }

    @Test
    fun `앞뒤 공백은 trim 후 전달된다`() = runTest {
        useCase("  Sony ILCE-7M3  ", ConnectionReportMethod.WIFI)
        coVerify(exactly = 1) { repo.reportConnection("Sony ILCE-7M3", ConnectionReportMethod.WIFI) }
    }
}
