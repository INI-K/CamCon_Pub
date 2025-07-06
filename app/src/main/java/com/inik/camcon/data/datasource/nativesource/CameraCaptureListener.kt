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
     * 캡처 실패 시 호출
     * @param errorCode 에러 코드
     */
    fun onCaptureFailed(errorCode: Int)
}
