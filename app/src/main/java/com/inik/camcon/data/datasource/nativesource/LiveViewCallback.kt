package com.inik.camcon.data.datasource.nativesource

import java.nio.ByteBuffer

/**
 * 라이브뷰 이벤트를 처리하는 콜백 인터페이스
 */
interface LiveViewCallback {
    /**
     * 라이브뷰 프레임 수신 시 호출
     * @param frame JPEG 프레임 데이터 (ByteBuffer)
     */
    fun onLiveViewFrame(frame: ByteBuffer)

    /**
     * 라이브뷰 중 사진 캡처 시 호출
     * @param path 저장된 파일 경로
     */
    fun onLivePhotoCaptured(path: String)
}
