package com.inik.camcon

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CamCon : Application() {

    companion object {
        private const val TAG = "CamCon"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "앱 초기화 시작")

        try {
            FirebaseApp.initializeApp(this)
            Log.d(TAG, "Firebase 초기화 완료")

            // Firebase 오프라인 지속성 활성화 (백그라운드 연결 유지)
            setupFirebasePersistence()
        } catch (e: Exception) {
            Log.e(TAG, "Firebase 초기화 실패", e)
        }

        Log.d(TAG, "앱 초기화 완료")
    }

    /**
     * Firebase 오프라인 지속성 설정
     */
    private fun setupFirebasePersistence() {
        try {
            // Firebase Database 오프라인 지속성 (사용하는 경우)
            // FirebaseDatabase.getInstance().setPersistenceEnabled(true)

            // Firebase Firestore 설정 (사용하는 경우)
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true) // 오프라인 지속성 활성화
                    .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
                firestore.firestoreSettings = settings
                Log.d(TAG, "Firestore 오프라인 지속성 활성화됨")
            } catch (e: Exception) {
                Log.w(TAG, "Firestore 설정 실패: ${e.message}")
            }

            Log.d(TAG, "Firebase 지속성 설정 완료")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase 지속성 설정 실패", e)
        }
    }

    override fun onTerminate() {
        Log.d(TAG, "앱 종료 - 카메라 세션 정리 시작")

        try {
            // 네이티브 카메라 세션 종료를 백그라운드 스레드에서 실행
            Thread {
                try {
                    com.inik.camcon.CameraNative.closeCamera()
                    com.inik.camcon.CameraNative.closeLogFile()
                    Log.d(TAG, "네이티브 리소스 정리 완료")
                } catch (e: Exception) {
                    Log.w(TAG, "네이티브 리소스 정리 중 오류", e)
                }
            }.start()
        } catch (e: Exception) {
            Log.w(TAG, "네이티브 리소스 정리 스레드 시작 중 오류", e)
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
