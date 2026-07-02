package com.inik.camcon.data.datasource.ptpip

/**
 * PTP/IP 사진 다운로드 파이프라인 이벤트.
 *
 * 과거 단일 콜백 슬롯(`onPhotoDownloadedCallback`)은 화면 수명주기 cleanup이 null로 지우면
 * 재설치 경로가 없어 수신사진 리스트가 조용히 멈추는 문제가 있었다. SharedFlow 스트림은
 * 발행자(PtpipDataSource)와 수집자(CameraCaptureRepositoryImpl, 싱글톤 스코프)가
 * 화면과 무관하게 배선되므로 이 계열 버그가 구조적으로 불가능하다.
 */
sealed interface PtpipPhotoEvent {

    /** MediaStore 저장까지 완료된 사진. [filePath]는 실제 저장 경로, [imageData]는 원본 바이트. */
    class Downloaded(
        val filePath: String,
        val fileName: String,
        val imageData: ByteArray
    ) : PtpipPhotoEvent

    /** 다운로드/저장 실패 — 수집자는 UI placeholder 제거·dedup 해제로 재시도를 허용해야 한다. */
    class DownloadFailed(val fileName: String) : PtpipPhotoEvent
}
