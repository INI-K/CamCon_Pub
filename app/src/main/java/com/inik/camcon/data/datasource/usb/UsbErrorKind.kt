package com.inik.camcon.data.datasource.usb

import android.content.Context
import com.inik.camcon.R

/**
 * USB / libgphoto2 초기화 실패를 사용자 친화 메시지로 매핑하기 위한 도메인 키.
 *
 * - 네이티브 코드(`gphoto2`)는 음수 정수 에러 코드를 반환한다.
 * - 코드 매핑은 [fromInitResult] 한 곳에서만 수행한다. 새 코드가 추가되면 여기만 갱신.
 * - UI 노출 문자열은 `values<lang>/strings_connect.xml`에 정의된 stringResource id 를 통해
 *   조회한다. 본 enum은 R 참조만 보관하고, [toMessage]로 Context에서 해석한다.
 */
enum class UsbErrorKind(
    val titleRes: Int,
    val messageRes: Int
) {
    /** -52: GP_ERROR_IO_USB_FIND — 카메라가 PTP 인터페이스로 열리지 않음. */
    NotFound(R.string.error_usb_help_title, R.string.error_usb_not_found),

    /** -7: GP_ERROR_IO — Mass Storage 모드 등으로 잘못 마운트된 경우. */
    IoError(R.string.error_usb_help_title, R.string.error_usb_io_change_mode),

    /** -10: GP_ERROR_TIMEOUT. */
    Timeout(R.string.error_usb_help_title, R.string.error_usb_timeout),

    /** USB 권한이 거부됐거나 시스템에서 회수된 상태. */
    PermissionDenied(R.string.error_usb_help_title, R.string.error_usb_permission),

    /** 화이트리스트 외 VID 또는 PTP 미지원 디바이스. */
    Unsupported(R.string.error_usb_help_title, R.string.error_usb_unsupported),

    /** -1000 / -2000 — 네이티브 컨텍스트가 손상된 상태. 앱 재시작 필요. */
    Restart(R.string.error_usb_help_title, R.string.error_usb_restart);

    /** Localized 메시지 본문. UI 다이얼로그/Snackbar에 그대로 노출 가능. */
    fun toMessage(context: Context): String = context.getString(messageRes)

    /** Localized 제목. AlertDialog 헤더용. */
    fun toTitle(context: Context): String = context.getString(titleRes)

    companion object {
        /**
         * libgphoto2 / CameraNative 초기화 결과 코드를 [UsbErrorKind]로 매핑.
         *
         * 알려지지 않은 코드는 [Restart]로 폴백한다(컨텍스트가 깨졌을 가능성이 높음).
         */
        fun fromInitResult(code: Int): UsbErrorKind = when (code) {
            -52, -4 -> NotFound
            -7 -> IoError
            -10 -> Timeout
            -53 -> PermissionDenied
            -1000, -2000 -> Restart
            else -> Restart
        }
    }
}
