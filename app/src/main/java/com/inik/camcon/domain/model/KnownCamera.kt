package com.inik.camcon.domain.model

/**
 * 카메라 본체 지문. **연결 후에만** 알 수 있다(시리얼은 abilities 조회 결과라 접속 전에는 없다).
 *
 * 그래서 지문의 역할은 "발견 시점의 기지 판정"이 아니라 **연결 후 검증**이다:
 * 같은 IP·같은 이름인데 다른 본체가 응답하는 경우(렌탈샵·스튜디오)를 잡아 자동 연결 승인을 회수한다.
 *
 * ⚠️ libgphoto2는 카메라가 시리얼을 보고하지 않으면 **리터럴 문자열 `"None"`** 을 돌려준다
 * (`camlibs/ptp2/config.c`의 `params->deviceinfo.SerialNumber ? … : _("None")`). 이를 유효 지문으로
 * 받으면 같은 기종 두 대의 지문이 동일해져 남의 바디가 자동 연결 게이트를 통과한다.
 */
object CameraFingerprint {

    /** 시리얼로 인정하지 않는 값(소문자 비교). libgphoto2의 `"None"` 폴백이 대표적이다. */
    private val INVALID_SERIALS = setOf("none", "unknown", "n/a", "na", "null", "0", "-")

    /** 지문 구분자. 시리얼·모델에 등장하지 않는 문자를 쓴다. */
    private const val SEPARATOR = "|"

    /**
     * 시리얼 + 모델로 지문을 만든다. 신뢰할 수 없으면 **null**(= 지문 없음)이다.
     *
     * null이 나오는 것은 오류가 아니다 — 지문 없이도 성립하는 보조 경로(mDNS 인스턴스명 + IP 힌트)를
     * 1급으로 두는 것이 설계다. abilities JSON 파싱 실패 시 `cameraInfo`가 아예 없는 경로도 있다.
     */
    fun of(serialNumber: String?, model: String?): String? {
        val serial = serialNumber?.trim().orEmpty()
        val modelName = model?.trim().orEmpty()
        if (serial.isEmpty() || serial.lowercase() in INVALID_SERIALS) return null
        if (modelName.isEmpty()) return null
        return "$serial$SEPARATOR$modelName"
    }

    /** 저장된 지문이 비교에 쓸 수 있는 값인가. */
    fun isValid(value: String?): Boolean {
        val fingerprint = value?.trim().orEmpty()
        if (fingerprint.isEmpty()) return false
        val serial = fingerprint.substringBefore(SEPARATOR).trim()
        return serial.isNotEmpty() && serial.lowercase() !in INVALID_SERIALS
    }
}

/**
 * 기억된 카메라. 자동 연결(무탭) 대상 판정의 근거다.
 *
 * 발견 시점에는 지문을 알 수 없으므로 [matches]는 **mDNS 인스턴스명 → IP 힌트** 순으로 판정한다.
 * 인스턴스명(예: `Z_8_5003869`)이 IP보다 강한 신호다 — DHCP로 IP는 바뀌지만 본체 이름은 유지되므로,
 * "IP가 바뀌면 자동 연결이 사망"하던 한계가 이 순서로 해소된다.
 *
 * @param autoConnectApproved 자동 연결을 허용할지. **기존 사용자 그랜드파더링을 위해 기본값이 true**다 —
 *   저장된 것이 IP·이름뿐인 사용자에게 승인 절차를 소급 적용하면 업데이트 직후 자동 연결이 무증상
 *   사망한다(백그라운드 폴링 경로에는 승인 UI가 없다). 지문 불일치가 관측될 때만 false로 회수한다.
 */
data class KnownCameraRef(
    val ipHint: String? = null,
    val serviceName: String? = null,
    val fingerprint: String? = null,
    val autoConnectApproved: Boolean = true
) {
    /** 기억된 기기와 같은 후보인가(발견 시점 판정 — 지문은 쓰지 않는다). */
    fun matches(camera: PtpipCamera): Boolean {
        val name = serviceName?.trim()
        if (!name.isNullOrEmpty() && camera.name.trim().equals(name, ignoreCase = true)) {
            return true
        }
        val ip = ipHint?.trim()
        return !ip.isNullOrEmpty() && camera.ipAddress == ip
    }

    /** 비어 있는 기억(첫 실행). */
    fun isEmpty(): Boolean = ipHint.isNullOrBlank() && serviceName.isNullOrBlank()

    /**
     * 연결 후 얻은 지문이 기억된 본체와 다른가.
     *
     * 양쪽 지문이 모두 유효할 때만 판정한다 — 한쪽이 없으면(시리얼 미보고·abilities 실패)
     * "다르다"고 단정할 근거가 없고, 단정하면 정상 사용자의 자동 연결을 끊는다.
     */
    fun isDifferentBody(observedFingerprint: String?): Boolean {
        if (!CameraFingerprint.isValid(fingerprint)) return false
        if (!CameraFingerprint.isValid(observedFingerprint)) return false
        return fingerprint?.trim() != observedFingerprint?.trim()
    }
}
