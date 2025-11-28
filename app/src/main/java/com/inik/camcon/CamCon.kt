package com.inik.camcon

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.view.WindowCompat
import com.google.firebase.FirebaseApp
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.data.service.WifiMonitoringService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CamCon : Application() {

    // WifiNetworkHelper를 public으로 노출 (BroadcastReceiver에서 사용)
    @Inject
    lateinit var wifiNetworkHelper: WifiNetworkHelper

    @Inject
    lateinit var preferencesDataSource: PtpipPreferencesDataSource

    // 현재 활성 Activity 추적 (포그라운드 상태 확인용)
    private var activeActivityCount = 0

    // Application 레벨 CoroutineScope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "CamCon"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "앱 초기화 시작")

        // Activity Lifecycle Callbacks 등록 (Edge-to-Edge 전역 설정 + 포그라운드 상태 추적)
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                setupEdgeToEdge(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                activeActivityCount++
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                activeActivityCount--
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // libgphoto2 플러그인 디렉토리 구조 생성
        createLibgphoto2PluginDirs()

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

        // WiFi 자동 연결 모니터링 Service 시작 (자동 연결이 켜져있으면)
        startWifiMonitoringServiceIfEnabled()

        Log.d(TAG, "앱 초기화 완료")
    }

    /**
     * libgphoto2 플러그인 디렉토리 구조 생성
     *
     * libgphoto2는 런타임에 특정 경로에서 플러그인을 로드합니다.
     * APK 빌드 시 하위 디렉토리는 포함되지 않으므로 런타임에 생성합니다.
     * nativeLibDir는 read-only이므로 앱의 private 디렉토리에 복사합니다.
     */
    private fun createLibgphoto2PluginDirs() {
        try {
            // 앱의 private 디렉토리에 플러그인 저장
            val gphoto2BaseDir = getDir("gphoto2_plugins", Context.MODE_PRIVATE)
            Log.d(TAG, "🔧 libgphoto2 플러그인 디렉토리 생성: ${gphoto2BaseDir.absolutePath}")

            val gphoto2VersionDir = java.io.File(gphoto2BaseDir, "libgphoto2/2.5.33.1")
            val portVersionDir = java.io.File(gphoto2BaseDir, "libgphoto2_port/0.12.2")

            gphoto2VersionDir.mkdirs()
            portVersionDir.mkdirs()

            var iolibCount = 0
            var camlibCount = 0

            // APK의 lib/arm64-v8a 경로에서 라이브러리 목록 조회
            val apkFile = java.util.zip.ZipFile(applicationInfo.sourceDir)
            try {
                val entries = apkFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name

                    // lib/arm64-v8a/ 하위의 .so 파일만 처리
                    if (!entryName.startsWith("lib/arm64-v8a/") || !entryName.endsWith(".so")) {
                        continue
                    }

                    val fileName = entryName.substringAfterLast("/")

                    when {
                        fileName.startsWith("libgphoto2_port_iolib_") -> {
                            val targetName = fileName.replace("libgphoto2_port_iolib_", "")
                            val targetFile = java.io.File(portVersionDir, targetName)
                            if (!targetFile.exists()) {
                                apkFile.getInputStream(entry).use { input ->
                                    targetFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                iolibCount++
                            }
                        }

                        fileName.startsWith("libgphoto2_camlib_") -> {
                            val targetName = fileName.replace("libgphoto2_camlib_", "")
                            val targetFile = java.io.File(gphoto2VersionDir, targetName)
                            if (!targetFile.exists()) {
                                apkFile.getInputStream(entry).use { input ->
                                    targetFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                camlibCount++
                            }
                        }
                    }
                }
            } finally {
                apkFile.close()
            }

            Log.d(TAG, "✅ 플러그인 복사 완료: I/O=$iolibCount, Camera=$camlibCount")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 플러그인 디렉토리 생성 실패", e)
        }
    }

    /**
     * Edge-to-Edge 전역 설정
     * 모든 액티비티에서 일관된 시스템 바 처리를 보장
     */
    private fun setupEdgeToEdge(activity: Activity) {
        try {
            // 1. WindowCompat을 사용하여 시스템 바를 앱 콘텐츠 위에 표시
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)

            // 2. 시스템 바 투명 설정
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT

            // 3. 시스템 바 아이콘 색상은 Compose Theme의 SideEffect에서 자동으로 조정됨
            // 여기서는 아무것도 설정하지 않음

            Log.d(TAG, "✅ Edge-to-Edge 설정 완료: ${activity.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Edge-to-Edge 설정 실패: ${activity.javaClass.simpleName}", e)
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

    /**
     * WiFi 자동 연결 모니터링 Service 시작 (조건부)
     *
     * 자동 연결이 활성화되어 있으면 WifiMonitoringService를 시작합니다.
     * 이 Service는 앱이 종료된 상태에서도 WiFi 연결을 감지할 수 있습니다.
     */
    private fun startWifiMonitoringServiceIfEnabled() {
        applicationScope.launch {
            try {
                val isAutoConnectEnabled = preferencesDataSource.isAutoConnectEnabledNow()
                Log.d(TAG, "========================================")
                Log.d(TAG, "🔍 WiFi 모니터링 Service 시작 확인")
                Log.d(TAG, "  - 자동 연결 활성화: $isAutoConnectEnabled")

                if (isAutoConnectEnabled) {
                    val autoConnectConfig = preferencesDataSource.getAutoConnectNetworkConfig()
                    if (autoConnectConfig != null) {
                        Log.d(TAG, "  - 저장된 네트워크: ${autoConnectConfig.ssid}")
                        Log.d(TAG, "✅ WifiMonitoringService 시작")
                        WifiMonitoringService.start(applicationContext)
                    } else {
                        Log.w(TAG, "⚠️ 자동 연결이 켜져있지만 네트워크 설정이 없음")
                    }
                } else {
                    Log.d(TAG, "❌ 자동 연결이 비활성화되어 있음 - Service 시작 안 함")
                }
                Log.d(TAG, "========================================")
            } catch (e: Exception) {
                Log.e(TAG, "❌ WiFi 모니터링 Service 시작 확인 중 오류", e)
            }
        }
    }

    /**
     * 앱이 현재 포그라운드에 있는지 확인
     */
    fun isAppInForeground(): Boolean {
        return activeActivityCount > 0
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
