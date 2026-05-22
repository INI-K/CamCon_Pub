package com.inik.camcon

/**
 * [CameraNative.getCameraThumbnailBatch] 호출 시 각 경로별 결과를 받는 콜백.
 *
 * 네이티브가 단일 카메라 락 보유 상태에서 N개 경로를 순회하며
 * 경로 1건당 한 번씩 [onThumbnail]을 호출한다. 호출 스레드는 JNI
 * 호출자 스레드와 동일하므로, 무거운 작업은 호출자가 IO 디스패처로
 * 오프로드해야 한다.
 *
 * @property onThumbnail 카메라 경로와 썸네일 데이터(실패/미지원 시 null).
 */
interface ThumbnailBatchCallback {
    fun onThumbnail(path: String, data: ByteArray?)
}
