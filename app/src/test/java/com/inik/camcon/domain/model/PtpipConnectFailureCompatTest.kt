package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PtpipConnectFailure] 확장 호환성 테스트 (설계 D11 테스트 8).
 *
 * SSH 계열 값 5종이 늘어난 뒤에도 기존 [PtpipConnectFailure.PAIRING_PENDING] 소비 경로가 그대로
 * 동작해야 한다. 이 열거형은 재시도 폴링이 UI 에 넘기는 유일한 구조적 사유이므로, 값이 늘면서
 * 기존 분기가 조용히 다른 가지로 흘러가면 "카메라에서 연결을 허용하세요" 안내가 사라진다.
 */
class PtpipConnectFailureCompatTest {

    /**
     * 기존 소비 경로를 그대로 흉내 낸 함수.
     *
     * `PtpipDataSource` 가 페어링 대기에서만 안내를 띄우고 나머지는 무시하던 형태다. 값이 늘어난
     * 뒤에도 이 형태가 컴파일되고 같은 판정을 유지해야 한다.
     */
    private fun legacyConsume(failure: PtpipConnectFailure?): String = when (failure) {
        PtpipConnectFailure.PAIRING_PENDING -> "카메라에서 연결을 허용하세요"
        null -> "실패 없음"
        else -> "기타 실패"
    }

    @Test
    fun `페어링 대기는 기존 안내 문구로 그대로 소비된다`() {
        assertEquals(
            "카메라에서 연결을 허용하세요",
            legacyConsume(PtpipConnectFailure.PAIRING_PENDING)
        )
    }

    @Test
    fun `값이 없으면 기존 경로가 실패 없음으로 판정한다`() {
        assertEquals("실패 없음", legacyConsume(null))
    }

    @Test
    fun `SSH 계열 값은 페어링 대기 경로로 흘러가지 않는다`() {
        sshFailures().forEach { failure ->
            assertEquals(
                "$failure 가 페어링 대기 안내를 띄우면 사용자가 카메라만 쳐다보게 된다",
                "기타 실패",
                legacyConsume(failure)
            )
        }
    }

    @Test
    fun `설계 D7 이 정한 SSH 값 5종이 모두 존재한다`() {
        listOf(
            "SSH_CREDENTIALS_REQUIRED",
            "SSH_HOST_KEY_UNVERIFIED",
            "SSH_HOST_KEY_MISMATCH",
            "SSH_AUTH_FAILED",
            "SSH_TUNNEL_FAILED"
        ).forEach { name ->
            assertNotNull(
                "$name 이 없으면 ⑤단계 실패 분류가 문자열로 되돌아간다",
                PtpipConnectFailure.entries.find { it.name == name }
            )
        }
    }

    @Test
    fun `페어링 대기는 첫 번째 값 자리를 지킨다`() {
        assertEquals(
            "새 값을 앞에 끼워 넣으면 순서에 의존하는 기존 로그와 비교가 어긋난다",
            PtpipConnectFailure.PAIRING_PENDING,
            PtpipConnectFailure.entries.first()
        )
    }

    @Test
    fun `열거형에는 페어링 대기와 SSH 계열 외의 값이 없다`() {
        val expected = setOf(PtpipConnectFailure.PAIRING_PENDING) + sshFailures()

        assertEquals(
            "분류에 없는 값이 늘면 UI 분기가 조용히 else 로 빠진다",
            expected,
            PtpipConnectFailure.entries.toSet()
        )
    }

    @Test
    fun `이름으로 되찾은 값은 같은 상수다`() {
        PtpipConnectFailure.entries.forEach { failure ->
            assertTrue(
                "이름 기반 복원이 깨지면 저장된 사유를 다시 읽을 수 없다",
                PtpipConnectFailure.valueOf(failure.name) === failure
            )
        }
    }

    private fun sshFailures(): Set<PtpipConnectFailure> =
        PtpipConnectFailure.entries.filter { it.name.startsWith("SSH_") }.toSet()
}
