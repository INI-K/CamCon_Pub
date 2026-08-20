package com.inik.camcon.data.repository.managers

import com.inik.camcon.utils.Constants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * capt 합성명 판별·실명 복원·EXIF 시각명 생성 순수 함수 검증 (리뷰 MEDIUM 반영).
 *
 * - [PhotoDownloadManager.isSyntheticCaptName]: 확장자 화이트리스트는
 *   [Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS] 단일 소스에서 파생(리뷰 HIGH 반영)
 *   — 목록 전체를 순회 검증해 드리프트 회귀를 잡는다.
 * - [PhotoDownloadManager.stripCaptRealNamePrefix]: `capt_<실명>`(A7C 실측 capt_JUN01569.JPG)
 *   접두 제거로 실제 카메라 파일명 복원.
 * - [PhotoDownloadManager.buildExifTimestampName]: 로컬 타임존 YYYYMMDD_HHMMSS 포맷.
 */
class PhotoDownloadManagerCaptNameTest {

    private lateinit var originalTz: TimeZone

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    @Test
    fun `capt 합성명은 지원 확장자 전체에서 참이다`() {
        for (ext in Constants.ImageProcessing.SUPPORTED_IMAGE_EXTENSIONS) {
            assertTrue("capt0001.$ext", PhotoDownloadManager.isSyntheticCaptName("capt0001.$ext"))
            assertTrue(
                "CAPT0002.${ext.uppercase()}",
                PhotoDownloadManager.isSyntheticCaptName("CAPT0002.${ext.uppercase()}")
            )
        }
    }

    @Test
    fun `실파일명과 capt_ 실명 래핑은 합성명이 아니다`() {
        listOf(
            "DSC00001.JPG",      // 카드 실명
            "_DSC1234.ARW",      // 소니 카드 실명
            "KY6_0035.JPG",      // 니콘 커스텀 접두 실명
            "capt_JUN01569.JPG", // capt_<실명> 래핑(별도 복원 경로)
            "capt.jpg",          // 숫자 없음
            "capt0001.xyz",      // 미지원 확장자
            "capt0001"           // 확장자 없음
        ).forEach {
            assertFalse(it, PhotoDownloadManager.isSyntheticCaptName(it))
        }
    }

    @Test
    fun `stripCaptRealNamePrefix 는 capt_ 실명에서 접두만 벗긴다`() {
        assertEquals("JUN01569.JPG", PhotoDownloadManager.stripCaptRealNamePrefix("capt_JUN01569.JPG"))
        assertEquals("DSC09999.ARW", PhotoDownloadManager.stripCaptRealNamePrefix("CAPT_DSC09999.ARW"))
    }

    @Test
    fun `stripCaptRealNamePrefix 는 실명 꼴이 아니면 null 이다`() {
        listOf(
            "capt0001.jpg",  // 순수 합성명(EXIF 시각명 경로 대상)
            "DSC00001.JPG",  // 접두 없음
            "capt_.jpg",     // 이름부 없음
            "capt_noext",    // 확장자 없음
            "capt_",         // 빈 잔여
            "capt_."         // 이름·확장자 모두 없음
        ).forEach {
            assertNull(it, PhotoDownloadManager.stripCaptRealNamePrefix(it))
        }
    }

    @Test
    fun `buildExifTimestampName 은 로컬시각 YYYYMMDD_HHMMSS 포맷이다`() {
        assertEquals("19700101_000000.jpg", PhotoDownloadManager.buildExifTimestampName(0L, "jpg"))
        assertEquals("19700101_000130.arw", PhotoDownloadManager.buildExifTimestampName(90_000L, "arw"))
    }

    @Test
    fun `buildExifTimestampName 은 빈 확장자를 jpg 로 폴백한다`() {
        assertEquals("19700101_000000.jpg", PhotoDownloadManager.buildExifTimestampName(0L, ""))
    }
}
