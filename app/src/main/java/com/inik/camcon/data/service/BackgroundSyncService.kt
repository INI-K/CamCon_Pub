package com.inik.camcon.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.inik.camcon.R
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.presentation.ui.MainActivity
import com.inik.camcon.utils.LogcatManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 백그라운드에서 Firebase 연결을 유지하고 카메라 이벤트 리스너를 관리하는 서비스
 * 도즈 모드에서도 카메라 이벤트 수신을 위한 Wake Lock 관리
 * Google Play 정책 준수를 위해 최소한의 작업만 수행
 */
@AndroidEntryPoint
class BackgroundSyncService : Service() {

    @Inject
    lateinit var cameraRepository: CameraRepository

    private var serviceScope: CoroutineScope? = null
    private var syncJob: Job? = null
    private var eventListenerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "BackgroundSyncService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "background_sync_channel"
        private const val SYNC_INTERVAL = 30_000L // 30초마다 체크
        private const val EVENT_LISTENER_CHECK_INTERVAL = 10_000L // 10초마다 이벤트 리스너 체크

        /**
         * 서비스 시작
         */
        fun startService(context: Context) {
            val intent = Intent(context, BackgroundSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 서비스 중지
         */
        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundSyncService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        LogcatManager.d(TAG, "BackgroundSyncService 생성됨 - 백그라운드 이벤트 리스너 관리 포함")

        serviceScope = CoroutineScope(Dispatchers.IO + Job())
        createNotificationChannel()

        // Wake Lock 획득 (화면이 꺼져도 이벤트 리스너 유지)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogcatManager.d(TAG, "BackgroundSyncService 시작됨 - 도즈 모드 대응")

        try {
            // Foreground Service로 시작
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34+)에서는 서비스 타입 지정 필수
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            LogcatManager.e(TAG, "포그라운드 서비스 시작 실패", e)
            stopSelf()
            return START_NOT_STICKY
        }

        // 백그라운드 동기화 작업 시작
        startBackgroundSync()

        // 백그라운드 이벤트 리스너 관리 시작
        startBackgroundEventListenerManager()

        // 앱이 종료되면 재시작되지 않도록 설정
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        LogcatManager.d(TAG, "BackgroundSyncService 종료됨")

        syncJob?.cancel()
        eventListenerJob?.cancel()
        serviceScope?.cancel()
        releaseWakeLock()

        try {
            stopForeground(true)
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            LogcatManager.w(TAG, "서비스 종료 중 알림 정리 실패", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        LogcatManager.d(TAG, "Task removed - 앱 종료로 서비스 정리")
        try {
            syncJob?.cancel()
            eventListenerJob?.cancel()
            serviceScope?.cancel()
            releaseWakeLock()

            stopForeground(true)
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            LogcatManager.w(TAG, "Task removed 처리 중 정리 실패", e)
        } finally {
            stopSelf()
        }
    }

    /**
     * Wake Lock 획득 (화면이 꺼져도 작동)
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CamCon::BackgroundEventListener"
            ).apply {
                acquire(10 * 60 * 1000L /*10 minutes*/) // 10분 제한 설정
                LogcatManager.d(TAG, " Wake Lock 획득 - 백그라운드 이벤트 리스너 유지")
            }
        } catch (e: Exception) {
            LogcatManager.e(TAG, "Wake Lock 획득 실패", e)
        }
    }

    /**
     * Wake Lock 해제
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    LogcatManager.d(TAG, " Wake Lock 해제됨")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            LogcatManager.e(TAG, "Wake Lock 해제 실패", e)
        }
    }

    /**
     * Wake Lock 갱신 (10분마다)
     */
    private fun renewWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire(10 * 60 * 1000L) // 다시 10분 연장
                    LogcatManager.d(TAG, " Wake Lock 갱신됨")
                }
            }
        } catch (e: Exception) {
            LogcatManager.e(TAG, "Wake Lock 갱신 실패", e)
            // 실패 시 다시 획득 시도
            releaseWakeLock()
            acquireWakeLock()
        }
    }

    /**
     * 알림 채널 생성 (Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "백그라운드 동기화",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "카메라 파일 동기화 및 연결 유지"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Foreground Service 알림 생성
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CamCon 백그라운드 동기화")
            .setContentText("카메라 이벤트 리스너 활성 상태 - 화면이 꺼져도 사진 수신 가능")
            .setSmallIcon(R.drawable.ic_camera_24) // 카메라 아이콘 사용
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 사용자가 스와이프로 제거할 수 없도록
            .setSilent(true) // 소리 없음
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // 잠금화면에서 숨김
            .setPriority(NotificationCompat.PRIORITY_MIN) // 최소 우선순위
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * 백그라운드 동기화 작업 시작
     */
    private fun startBackgroundSync() {
        syncJob?.cancel()

        syncJob = serviceScope?.launch {
            LogcatManager.d(TAG, "백그라운드 동기화 작업 시작")

            while (true) {
                try {
                    // Firebase 연결 상태 확인 및 유지
                    checkFirebaseConnection()

                    // 필요시 파일 동기화 상태 확인
                    checkFileSyncStatus()

                    // Wake Lock 갱신 (10분마다)
                    renewWakeLock()

                    // 30초 대기
                    delay(SYNC_INTERVAL)

                } catch (e: Exception) {
                    LogcatManager.w(TAG, "백그라운드 동기화 작업 중 오류", e)
                    // 오류 발생 시 1분 대기 후 재시도
                    delay(60_000L)
                }
            }
        }
    }

    /**
     * 백그라운드 이벤트 리스너 관리자 시작
     */
    private fun startBackgroundEventListenerManager() {
        eventListenerJob?.cancel()

        eventListenerJob = serviceScope?.launch {
            LogcatManager.d(TAG, " 백그라운드 이벤트 리스너 관리자 시작")

            while (true) {
                try {
                    // 카메라 연결 상태 확인
                    val isConnected = cameraRepository.isCameraConnected().first()
                    val isEventListenerActive = cameraRepository.isEventListenerActive().first()

                    LogcatManager.d(
                        TAG,
                        " 카메라 연결 상태: $isConnected, 이벤트 리스너: $isEventListenerActive"
                    )

                    if (!isConnected) {
                        LogcatManager.d(TAG, " 카메라 연결되지 않음 - 모든 이벤트 리스너 정리")

                        // 카메라 연결이 끊어지면 모든 이벤트 리스너 완전 정리
                        try {
                            val stopResult = cameraRepository.stopCameraEventListener()
                            if (stopResult.isSuccess) {
                                LogcatManager.d(TAG, " 백그라운드에서 이벤트 리스너 정리 성공")
                            } else {
                                LogcatManager.w(TAG, " 백그라운드에서 이벤트 리스너 정리 실패")
                            }
                        } catch (e: Exception) {
                            LogcatManager.e(TAG, "백그라운드 이벤트 리스너 정리 중 예외", e)
                        }

                        updateNotificationText("카메라 연결 대기 중...")

                        // 카메라 연결이 끊어지면 리스너 관리 루프 중지하고 대기 모드로 전환
                        LogcatManager.d(TAG, " 카메라 연결 끊김 - 이벤트 리스너 관리 대기 모드로 전환")

                        // 연결이 복원될 때까지 더 긴 간격으로 체크 (30초)
                        delay(30_000L)
                        continue
                    }

                    if (isConnected && !isEventListenerActive) {
                        LogcatManager.d(TAG, " 카메라는 연결되어 있으나 이벤트 리스너가 비활성 - 재시작 시도")

                        try {
                            val result = cameraRepository.startCameraEventListener()
                            if (result.isSuccess) {
                                LogcatManager.d(TAG, " 백그라운드에서 이벤트 리스너 재시작 성공")
                                updateNotificationText("카메라 이벤트 리스너 활성 - 사진 수신 대기 중")
                            } else {
                                LogcatManager.w(TAG, " 백그라운드에서 이벤트 리스너 재시작 실패")
                                updateNotificationText("카메라 연결 확인 중...")
                            }
                        } catch (e: Exception) {
                            LogcatManager.e(TAG, "백그라운드 이벤트 리스너 시작 중 예외", e)
                        }

                    } else if (isConnected && isEventListenerActive) {
                        LogcatManager.d(TAG, " 카메라 연결 및 이벤트 리스너 정상 작동 중")
                        updateNotificationText("카메라 이벤트 리스너 활성 - 사진 수신 대기 중")
                    }

                    // 10초마다 체크 (카메라 연결된 경우)
                    delay(EVENT_LISTENER_CHECK_INTERVAL)

                } catch (e: Exception) {
                    LogcatManager.w(TAG, "백그라운드 이벤트 리스너 관리 중 오류", e)

                    // 오류 발생 시 이벤트 리스너 정리 시도
                    try {
                        cameraRepository.stopCameraEventListener()
                        LogcatManager.d(TAG, " 오류 처리 중 이벤트 리스너 정리 완료")
                    } catch (cleanupException: Exception) {
                        LogcatManager.e(TAG, "오류 처리 중 이벤트 리스너 정리 실패", cleanupException)
                    }

                    updateNotificationText("연결 오류 - 재시도 중...")
                    // 오류 발생 시 30초 대기 후 재시도
                    delay(30_000L)
                }
            }
        }
    }

    /**
     * 알림 텍스트 업데이트
     */
    private fun updateNotificationText(text: String) {
        try {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("CamCon 백그라운드 동기화")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_camera_24)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            LogcatManager.w(TAG, "알림 텍스트 업데이트 실패", e)
        }
    }

    /**
     * Firebase 연결 상태 확인
     */
    private suspend fun checkFirebaseConnection() {
        try {
            // Firebase Auth 연결 상태 확인
            val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val currentUser = firebaseAuth.currentUser

            if (currentUser != null) {
                LogcatManager.d(TAG, "Firebase 사용자 인증 유지됨: ${currentUser.uid}")

                // 필요시 토큰 갱신
                currentUser.getIdToken(false).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        LogcatManager.d(TAG, "Firebase 토큰 갱신 성공")
                    } else {
                        LogcatManager.w(TAG, "Firebase 토큰 갱신 실패", task.exception)
                    }
                }
            } else {
                LogcatManager.d(TAG, "Firebase 사용자 인증되지 않음")
            }

        } catch (e: Exception) {
            LogcatManager.w(TAG, "Firebase 연결 상태 확인 실패", e)
        }
    }

    /**
     * 파일 동기화 상태 확인
     */
    private suspend fun checkFileSyncStatus() {
        try {
            // 실제 구현에서는 필요한 동기화 작업 수행
            // 예: 대기 중인 업로드 파일 확인, 클라우드 상태 동기화 등
            LogcatManager.d(TAG, "파일 동기화 상태 확인 완료")

        } catch (e: Exception) {
            LogcatManager.w(TAG, "파일 동기화 상태 확인 실패", e)
        }
    }

    /**
     * 카메라 연결 완전 해제 시 서비스 정리
     */
    fun cleanupOnCameraDisconnection() {
        LogcatManager.d(TAG, "카메라 연결 해제로 인한 서비스 정리 시작")

        try {
            // 이벤트 리스너 관리 작업 중지
            eventListenerJob?.cancel()
            LogcatManager.d(TAG, "백그라운드 이벤트 리스너 관리 작업 중지됨")

            // Wake Lock 해제
            releaseWakeLock()

            // 알림 업데이트
            updateNotificationText("카메라 연결 해제됨 - 서비스 대기 중")

            LogcatManager.d(TAG, "카메라 연결 해제로 인한 서비스 정리 완료")
        } catch (e: Exception) {
            LogcatManager.e(TAG, "카메라 연결 해제 정리 중 오류", e)
        }
    }
}