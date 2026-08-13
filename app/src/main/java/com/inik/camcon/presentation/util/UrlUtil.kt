package com.inik.camcon.presentation.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.inik.camcon.R

private const val TAG = "UrlUtil"

/** 메일 URI 스킴. */
private const val MAILTO_SCHEME = "mailto:"

/** 메일 주소 복사 시 시스템에 노출되는 ClipData 라벨. */
private const val EMAIL_CLIP_LABEL = "email"

/**
 * 외부 브라우저로 URL 열기 공통 헬퍼.
 *
 * 사용 예:
 * ```
 * context.openUrl(Constants.Legal.PRIVACY_POLICY_URL)
 * ```
 *
 * URL을 처리할 수 있는 앱(브라우저 등)이 없는 기기에서
 * ActivityNotFoundException으로 크래시하지 않도록 방어한다.
 *
 * `mailto:`는 ACTION_VIEW가 아니라 [openEmail]로 넘긴다(메일 컴포즈의 정규 액션은 ACTION_SENDTO).
 *
 * @param url 열려는 URL
 */
fun Context.openUrl(url: String) {
    if (url.startsWith(MAILTO_SCHEME, ignoreCase = true)) {
        openEmail(url.substring(MAILTO_SCHEME.length))
        return
    }

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "URL을 열 수 있는 앱이 없습니다: $url", e)
    }
}

/**
 * 메일 앱으로 새 메일 작성 화면 열기.
 *
 * 메일 컴포즈의 정규 액션은 ACTION_SENDTO다. ACTION_VIEW로 던지면 SENDTO만 등록한 메일 앱이
 * 잡히지 않아 "앱 없음"으로 떨어질 수 있다.
 *
 * 처리할 앱이 없어도 조용히 끝내지 않는다 — 이 경로가 LGPL 서면 제공 오퍼의 유일한 연락
 * 수단이라 무반응이면 준수 장치 자체가 성립하지 않는다. 주소를 클립보드에 복사하고 Toast로
 * 알려 사용자가 다른 수단으로 연락할 수 있게 한다.
 *
 * @param address 메일 주소 (`mailto:` 접두어 없이)
 */
fun Context.openEmail(address: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("$MAILTO_SCHEME$address"))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "메일 앱이 없어 주소를 클립보드에 복사합니다", e)
        copyToClipboard(EMAIL_CLIP_LABEL, address)
        Toast.makeText(
            this,
            getString(R.string.contact_email_copied, address),
            Toast.LENGTH_LONG
        ).show()
    }
}
