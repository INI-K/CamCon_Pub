package com.inik.camcon.domain.model

/**
 * 카메라 캡처 이벤트를 처리하는 콜백 인터페이스.
 *
 * domain 레이어에 위치하여 presentation과 data 양쪽에서 참조 가능.
 * data 레이어의 CameraCaptureListener는 이 인터페이스를 확장한다.
 */
interface CameraCaptureCallback {
    /** 플러시 완료 시 호출 */
    fun onFlushComplete()

    /**
     * 사진 캡처 완료 시 호출
     * @param filePath 저장된 파일 경로
     * @param fileName 파일명
     */
    fun onPhotoCaptured(filePath: String, fileName: String)

    /**
     * 사진 다운로드 완료 시 호출 (Native에서 직접 다운로드)
     * @param filePath 카메라 내부 파일 경로
     * @param fileName 파일명
     * @param imageData 다운로드된 이미지 데이터
     */
    fun onPhotoDownloaded(filePath: String, fileName: String, imageData: ByteArray)

    /**
     * 캡처 실패 시 호출
     * @param errorCode 에러 코드
     */
    fun onCaptureFailed(errorCode: Int)

    /** USB 디바이스가 분리되었을 때 호출 */
    fun onUsbDisconnected()

    /**
     * 카메라 본체에서 디바이스 프로퍼티(설정)가 변경됐을 때 호출 (이벤트 푸시 — 폴링 대체).
     * @param configName 변경된 gphoto2 config 이름 (예: "iso", "shutterspeed", "f-number")
     * 기본 구현 무동작 — 관심 있는 구현만 override.
     */
    fun onPropertyChanged(configName: String) {}
}
