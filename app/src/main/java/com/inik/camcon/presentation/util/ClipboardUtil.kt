package com.inik.camcon.presentation.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 클립보드 복사 공통 헬퍼.
 *
 * 사용 예:
 * ```
 * context.copyToClipboard("native_log", logContent)
 * ```
 *
 * 호출자는 이후 사용자에게 "복사 완료" 안내(Toast 등)를 직접 보여주면 된다.
 *
 * @param label 시스템에 노출되는 ClipData 라벨 (디버그/접근성)
 * @param text  실제 복사될 텍스트
 */
fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
