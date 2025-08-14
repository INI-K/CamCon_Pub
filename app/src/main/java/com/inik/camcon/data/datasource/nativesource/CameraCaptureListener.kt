package com.inik.camcon.data.datasource.nativesource

/**
 * 카메라 캡처 이벤트를 처리하는 리스너 인터페이스
 */
interface CameraCaptureListener {
    /**
     * 플러시 완료 시 호출
     */
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

    /**
     * USB 디바이스가 분리되었을 때 호출
     */
    fun onUsbDisconnected()
}
