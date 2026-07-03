package com.inik.camcon.data.repository.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PhotoDownloadManager.isDisplayNameMismatch] 단위 테스트.
 *
 * 파일명 밀림 버그(MediaStore 가 이름 충돌 시 "KY6_9110.JPG" → "KY6_9111.JPG"/"KY6_9110 (1).JPG"
 * 로 조용히 리네임)를 감지하는 순수 판정 로직만 검증한다.
 * MediaStore/ContentResolver 는 실기 의존이므로 커버 불가 — 판정 함수만 JVM 단위 테스트.
 */
class PhotoDownloadManagerDisplayNameTest {

    @Test
    fun `요청명과 실제명이 같으면 불일치 아님`() {
        assertFalse(
            PhotoDownloadManager.isDisplayNameMismatch("KY6_9110.JPG", "KY6_9110.JPG")
        )
    }

    @Test
    fun `MediaStore 가 다음 번호로 밀어 저장하면 불일치로 감지`() {
        // 실기 로그 재현: 요청 9110 인데 실제 저장이 9111
        assertTrue(
            PhotoDownloadManager.isDisplayNameMismatch("KY6_9110.JPG", "KY6_9111.JPG")
        )
    }

    @Test
    fun `MediaStore 가 괄호 접미사로 리네임하면 불일치로 감지`() {
        assertTrue(
            PhotoDownloadManager.isDisplayNameMismatch("KY6_9110.JPG", "KY6_9110 (1).JPG")
        )
    }

    @Test
    fun `실제명이 null 이면 불일치로 보지 않음 (조회 실패는 저장 실패가 아님)`() {
        assertFalse(
            PhotoDownloadManager.isDisplayNameMismatch("KY6_9110.JPG", null)
        )
    }

    @Test
    fun `확장자 대소문자만 다르면 불일치 아님 (일부 제조사 MediaStore 정규화 허용)`() {
        assertFalse(
            PhotoDownloadManager.isDisplayNameMismatch("KY6_9110.JPG", "KY6_9110.jpg")
        )
    }

    @Test
    fun `베이스명 대소문자가 달라도 동일 파일이면 불일치 아님`() {
        assertFalse(
            PhotoDownloadManager.isDisplayNameMismatch("ky6_9110.jpg", "KY6_9110.JPG")
        )
    }
}
