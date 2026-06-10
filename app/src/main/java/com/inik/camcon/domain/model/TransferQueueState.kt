package com.inik.camcon.domain.model

/**
 * 다운로드/처리 진행 카운트 (요구 E).
 *
 * 외부 셔터 촬영 후 카메라→앱 전송 파이프라인의 현재 상태를 나타낸다.
 *  - [downloading]: 네이티브 콜백으로 감지되어 바이트 수신 대기 중인 파일 수
 *  - [processing]: 수신 완료 후 리사이즈/색감전송/MediaStore 저장 진행 중인 파일 수
 *  - [currentFileName]: 가장 최근 단계 전이가 일어난 파일명(처리 우선, 없으면 다운로드)
 *
 * capturedPhotos 와 동일한 UDF 경로(repo Flow → ViewModel observe → UiState)로 노출된다.
 */
data class TransferQueueState(
    val downloading: Int = 0,
    val processing: Int = 0,
    val currentFileName: String? = null
) {
    /** 진행 중인 전송이 하나라도 있으면 true. UI 배지 표시 게이트. */
    val isActive: Boolean get() = downloading > 0 || processing > 0

    /** 진행 중인 전체 파일 수(다운로드 + 처리). */
    val total: Int get() = downloading + processing
}
