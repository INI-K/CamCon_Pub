package com.inik.camcon.presentation.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

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
 * @param url 열려는 URL
 */
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("UrlUtil", "URL을 열 수 있는 앱이 없습니다: $url", e)
    }
}
