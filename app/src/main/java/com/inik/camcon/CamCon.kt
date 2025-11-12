package com.inik.camcon

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
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

        // Activity Lifecycle Callbacks 등록 (Edge-to-Edge 전역 설정)
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                setupEdgeToEdge(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // 네이티브 라이브러리 로딩 상태 확인
        checkNativeLibraryStatus()

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
     * Edge-to-Edge 전역 설정
     * Compose Scaffold가 패딩을 자동으로 처리하므로 여기서는 시스템 UI만 투명하게 설정
     */
    private fun setupEdgeToEdge(activity: Activity) {
        try {
            // WindowCompat을 사용하여 시스템 바를 앱 콘텐츠 위에 표시
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)

            Log.d(TAG, "Edge-to-Edge 설정 완료: ${activity.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.w(TAG, "Edge-to-Edge 설정 실패: ${activity.javaClass.simpleName}", e)
        }
    }

    /**
     * 네이티브 라이브러리 로딩 상태 확인
     */
    private fun checkNativeLibraryStatus() {
        try {
            Log.d(TAG, "🔍 네이티브 라이브러리 상태 확인 중...")

            if (CameraNative.isLibrariesLoaded()) {
                Log.d(TAG, "✅ 네이티브 라이브러리 정상 로딩됨")

                // 라이브러리 로딩 테스트
                try {
                    val testResult = CameraNative.testLibraryLoad()
                    Log.d(TAG, "✅ 네이티브 라이브러리 테스트 성공: $testResult")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ 네이티브 라이브러리 테스트 실패", e)
                }

                // libgphoto2 버전 확인
                try {
                    val version = CameraNative.getLibGphoto2Version()
                    Log.d(TAG, "📦 libgphoto2 버전: $version")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ libgphoto2 버전 확인 실패", e)
                }

            } else {
                Log.e(TAG, "🔴 네이티브 라이브러리 로딩 실패 - 앱 기능이 제한될 수 있습니다")
            }

        } catch (e: Exception) {
            Log.e(TAG, "🔴 네이티브 라이브러리 상태 확인 실패", e)
        }
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
                    .setPersistenceEnabled(false) // 오프라인 캐시 비활성화 - 항상 서버에서 최신 데이터 가져오기
                    .build()
                firestore.firestoreSettings = settings
                Log.d(TAG, "Firestore 오프라인 캐시 비활성화됨 (항상 서버에서 최신 데이터 조회)")
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
                    if (CameraNative.isLibrariesLoaded()) {
                        CameraNative.closeCamera()
                        CameraNative.closeLogFile()
                        Log.d(TAG, "네이티브 리소스 정리 완료")
                    } else {
                        Log.w(TAG, "네이티브 라이브러리가 로딩되지 않아 정리 생략")
                    }
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
