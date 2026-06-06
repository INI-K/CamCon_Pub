package com.inik.camcon.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.inik.camcon.CamCon

/**
 * 기기 재부팅 시 Application을 초기화하는 리시버
 *
 * Application이 초기화되면 NetworkCallback이 등록되어
 * WiFi 연결 변화를 모니터링할 수 있습니다.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "기기 재부팅 완료 - Application 초기화 시작")

        try {
            // Application 컨텍스트 접근하여 초기화 트리거
            val application = context.applicationContext as? CamCon

            if (application != null) {
                // Application.onCreate()가 자동으로 호출되어
                // NetworkCallback이 등록됩니다.
                Log.d(TAG, "재부팅 후 초기화 완료 - NetworkCallback 자동 등록됨")
            } else {
                Log.w(TAG, "Application을 가져올 수 없습니다")
            }
        } catch (e: Exception) {
            Log.e(TAG, "재부팅 후 초기화 실패", e)
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
