package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.ConnectionReportMethod
import com.inik.camcon.domain.repository.ConnectionReportRepository
import javax.inject.Inject

/**
 * 연결에 성공한 카메라 기종·방식을 익명으로 보고한다.
 *
 * 제네릭/미확정 드라이버명(예: "PTP/IP Camera", "USB PTP Class Camera")이나
 * 공백·"알 수 없음"은 집계 의미가 없으므로 no-op으로 걸러낸다.
 * 벤더/모델 정규화는 서버(CF)가 담당하므로 여기서는 하지 않는다.
 */
class ReportCameraConnectionUseCase @Inject constructor(
    private val repo: ConnectionReportRepository
) {
    suspend operator fun invoke(rawModel: String, method: ConnectionReportMethod) {
        val m = rawModel.trim()
        if (m.isBlank()) return

        val lower = m.lowercase()
        val isJunk = lower == "ptp/ip camera" ||
            lower == "usb ptp class camera" ||
            lower == "알 수 없음" ||
            lower.contains("generic")
        if (isJunk) return

        repo.reportConnection(m, method)
    }
}
