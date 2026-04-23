package com.inik.camcon.data.datasource.nativesource

/**
 * 라이브뷰 이벤트를 처리하는 콜백 인터페이스
 */
interface LiveViewCallback {
    /**
     * 라이브뷰 프레임 수신 시 호출
     * @param frame JPEG 프레임 데이터 (ByteArray)
     */
    fun onLiveViewFrame(frame: ByteArray)

    /**
     * 라이브뷰 중 사진 캡처 시 호출 (앱 내 캡처 — gp_camera_capture 경로)
     * @param path 저장된 파일 경로
     */
    fun onLivePhotoCaptured(path: String)

    /**
     * 라이브뷰 중 물리 셔터로 촬영된 사진 감지 시 호출 (FILE_ADDED 이벤트)
     * @param filePath 카메라 내 전체 경로 (folder/name)
     * @param fileName 파일명
     */
    fun onPhotoCaptured(filePath: String, fileName: String)

    /**
     * 라이브뷰 중 물리 셔터 사진 다운로드 완료 시 호출
     * @param filePath 카메라 내 전체 경로
     * @param fileName 파일명
     * @param imageData 다운로드된 이미지 바이너리 데이터
     */
    fun onPhotoDownloaded(filePath: String, fileName: String, imageData: ByteArray)
}
