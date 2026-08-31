package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.PtpipCamera

/**
 * SSH 자격증명 다이얼로그가 열린 이유. 같은 입력 화면이지만 안내 문구가 달라진다.
 */
enum class SshCredentialsPromptReason {
    /** 저장된 자격증명이 아예 없다. 최초 입력 안내를 보여 준다. */
    REQUIRED,

    /** 저장된 값으로 로그인했으나 카메라가 거부했다. 값을 다시 확인하라고 안내한다. */
    AUTH_FAILED,

    /**
     * 입력은 받았으나 이 기기의 암호화 저장소를 쓸 수 없어 저장하지 못했다(fail-closed).
     * 평문 폴백을 만들지 않으므로 연결할 때마다 다시 입력해야 한다는 사실을 알린다.
     */
    STORE_FAILED
}

/**
 * SSH 연결 실패가 요구하는 사용자 조치. `PtpipConnectFailure`를 화면이 바로 쓸 수 있는
 * 형태로 옮긴 값이며, 조치 대상 카메라를 함께 들고 다녀 재연결에 그대로 쓴다.
 *
 * ⚠️ 호스트키 신뢰는 [HostKeyTrust] 한 갈래로만 이뤄진다. 지문 대조를 건너뛰고 신뢰하는
 * 값을 새로 추가하면 TOFU 보호가 사라지므로 추가하지 않는다.
 */
sealed interface SshConnectPrompt {

    /** 조치가 끝난 뒤 다시 연결할 대상. */
    val camera: PtpipCamera

    /** 사용자명·비밀번호 입력이 필요하다. */
    data class Credentials(
        override val camera: PtpipCamera,
        val reason: SshCredentialsPromptReason
    ) : SshConnectPrompt

    /** 최초 연결이라 지문을 대조받아야 한다(TOFU). */
    data class HostKeyTrust(
        override val camera: PtpipCamera,
        val fingerprint: String
    ) : SshConnectPrompt

    /**
     * 저장된 지문과 제시된 지문이 다르다. 경고만 하고 신뢰 경로를 제공하지 않는다.
     * [fingerprint]는 카메라가 이번에 제시한 값이며 없을 수도 있다.
     */
    data class HostKeyMismatch(
        override val camera: PtpipCamera,
        val fingerprint: String?
    ) : SshConnectPrompt

    /** 22번 포트에 닿지 못했거나 포워딩을 열지 못했다. 안내하고 재시도를 제공한다. */
    data class TunnelFailed(
        override val camera: PtpipCamera
    ) : SshConnectPrompt
}
