package com.inik.camcon

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CamCon: Application() {

    companion object {
        private const val TAG = "CamCon"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "앱 초기화 시작")

        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase 초기화 완료")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase 초기화 실패", e)
        }

        Log.d(TAG, "앱 초기화 완료")
    }

    override fun onTerminate() {
        Log.d(TAG, "앱 종료 - 카메라 세션 정리 시작")

        try {
            // 네이티브 카메라 세션 강제 종료
            CameraNative.closeCamera()
            Log.d(TAG, "카메라 세션 정리 완료")
        } catch (e: Exception) {
            Log.w(TAG, "카메라 세션 정리 중 오류", e)
        }

        super.onTerminate()
        Log.d(TAG, "앱 종료 완료")
    }

    override fun onLowMemory() {
        Log.w(TAG, "메모리 부족 경고")
        super.onLowMemory()
        // 메모리 정리 로직을 여기에 추가할 수 있음
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "메모리 정리 요청: 레벨 $level")
        // 메모리 정리 로직을 여기에 추가할 수 있음
    }
}
