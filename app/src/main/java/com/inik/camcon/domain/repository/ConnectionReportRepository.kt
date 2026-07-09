package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.ConnectionReportMethod

/**
 * 성공한 카메라 연결(기종 + 방식)을 익명으로 서버에 보고하는 리포지토리.
 * uid·카운트·이력은 저장하지 않는다.
 */
interface ConnectionReportRepository {
    suspend fun reportConnection(model: String, method: ConnectionReportMethod)
}
