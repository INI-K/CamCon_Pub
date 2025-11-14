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

        Log.d(TAG, "========================================")
        Log.d(TAG, "📱 기기 재부팅 완료")
        Log.d(TAG, "🔄 Application 초기화 시작")
        Log.d(TAG, "========================================")

        try {
            // Application 컨텍스트 접근하여 초기화 트리거
            val application = context.applicationContext as? CamCon

            if (application != null) {
                Log.d(TAG, " Application 초기화 완료")
                Log.d(TAG, "   - NetworkCallback: 자동 등록됨")

                // Application.onCreate()가 자동으로 호출되어
                // NetworkCallback이 등록됩니다.

            } else {
                Log.e(TAG, "❌ Application을 가져올 수 없습니다")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 재부팅 후 초기화 실패", e)
        }

        Log.d(TAG, "✅ 재부팅 후 초기화 완료")
        Log.d(TAG, "   이제 카메라 WiFi 연결 시 자동으로 감지됩니다")
        Log.d(TAG, "========================================")
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
