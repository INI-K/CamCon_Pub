package com.inik.camcon.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.hardware.usb.UsbManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.inik.camcon.R
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.utils.LogcatManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    @Inject
    lateinit var globalConnectionManager: com.inik.camcon.domain.manager.CameraConnectionGlobalManager

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private var serviceScope: CoroutineScope? = null
    private var syncJob: Job? = null
    private var eventListenerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // 카메라 연결 상태에서 앱이 최근앱에서 제거(onTaskRemoved)돼 서비스가 존속한 상황 표시.
    // 이 상태에서 이후 카메라 연결까지 끊기면 idle FGS·영구 알림 잔존을 막기 위해 self-stop 한다.
    // 앱이 (재)실행돼 onStartCommand가 다시 호출되면 false로 리셋된다.
    @Volatile
    private var taskRemovedWhileConnected = false

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

        serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)
        createNotificationChannel()

        // Wake Lock은 카메라가 실제로 연결됐을 때만 startBackgroundEventListenerManager()의
        // ensureWakeLock()으로 획득한다. (연결도 없는데 onCreate에서 무조건 잡았다가 곧장 해제하던
        // idle 낭비 제거 — 연결 없으면 깨워야 할 이벤트도 없으므로 락 불필요)
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

        // H15: OS의 START_STICKY 재기동(intent == null)으로 되살아났는데 이미 연결이 없으면
        // 즉시 self-stop 한다. (combine 레벨트리거의 첫 방출을 기다리는 사이 idle FGS·영구 알림이
        // 잔존하는 창을 없앤다 — Android 14+는 startForeground 후 stopSelf가 정상 종료 경로다.)
        if (intent == null) {
            val cameraConnected = try {
                globalConnectionManager.globalConnectionState.value.isAnyConnectionActive
            } catch (e: Exception) {
                false
            }
            if (!cameraConnected) {
                LogcatManager.d(TAG, "시스템 재기동 + 카메라 미연결 - idle FGS 잔존 방지로 즉시 종료")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // 앱이 (재)실행되어 onStartCommand가 다시 호출되면 "스와이프 후 존속" 표시를 해제한다.
        // (전경 복귀 시 disconnect로 인한 의도치 않은 self-stop 방지)
        taskRemovedWhileConnected = false

        // 백그라운드 동기화 작업 시작
        startBackgroundSync()

        // 백그라운드 이벤트 리스너 관리 시작
        startBackgroundEventListenerManager()

        // OS가 메모리 부족으로 서비스를 죽이면 재생성해 포그라운드+이벤트 리스너를 복원한다.
        // (스와이프 제거 시: 카메라 연결 중이면 onTaskRemoved가 존속시키고, 미연결이면 stopSelf로 종료)
        return START_STICKY
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
        // 카메라가 연결된 상태에서 앱을 최근앱에서 스와이프로 닫아도 백그라운드 수신을 끊지 않는다.
        // (manifest stopWithTask="false"와 함께 동작 — 서비스를 존속시켜 무인 테더링 수신 유지)
        val cameraConnected = try {
            globalConnectionManager.globalConnectionState.value.isAnyConnectionActive
        } catch (e: Exception) {
            false
        }

        if (cameraConnected) {
            LogcatManager.d(TAG, "Task removed - 카메라 연결 유지 중: 백그라운드 수신 지속(서비스 존속)")
            taskRemovedWhileConnected = true
            return
        }

        // 연결이 없으면 살려둘 이유가 없으므로 정리 후 종료
        LogcatManager.d(TAG, "Task removed - 카메라 미연결: 서비스 정리 후 종료")
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
                // renewWakeLock가 무조건 재acquire하므로 참조 카운팅 비활성화
                // (단일 release로 항상 해제되도록 보장)
                setReferenceCounted(false)
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
     * Wake Lock 갱신 (sync 루프 주기마다 = 타임아웃보다 짧은 주기로 무조건 재acquire)
     *
     * isHeld 가드를 두면 타임아웃 만료(isHeld=false)부터 다음 갱신까지 락 공백이 생긴다.
     * SYNC_INTERVAL(30초)이 타임아웃(10분)보다 훨씬 짧으므로, 만료 전에 무조건
     * 타임아웃을 다시 설정해 공백 없이 락을 유지한다.
     *
     * 단, 연결 해제로 releaseWakeLock()이 호출돼 wakeLock이 null이면 재획득하지 않는다.
     * (의도적으로 해제된 락을 sync 루프가 되살리는 것을 방지 — 재획득은 재연결 시 ensureWakeLock()이 담당)
     */
    private fun renewWakeLock() {
        try {
            wakeLock?.let { lock ->
                lock.acquire(10 * 60 * 1000L) // 무조건 타임아웃 재설정 (10분 연장)
            }
        } catch (e: Exception) {
            LogcatManager.e(TAG, "Wake Lock 갱신 실패", e)
            // 실패 시 다시 획득 시도
            releaseWakeLock()
            acquireWakeLock()
        }
    }

    /**
     * 카메라 연결 중 Wake Lock 보장 (없으면 획득, 있으면 타임아웃 갱신)
     */
    private fun ensureWakeLock() {
        if (wakeLock == null) {
            acquireWakeLock()
        } else {
            renewWakeLock()
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
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
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
            try {
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

                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // 코루틴 취소는 정상 종료
                        LogcatManager.d(TAG, "백그라운드 동기화 작업이 정상적으로 취소됨")
                        throw e // CancellationException은 다시 throw해야 함
                    } catch (e: Exception) {
                        LogcatManager.w(TAG, "백그라운드 동기화 작업 중 오류", e)
                        // 오류 발생 시 1분 대기 후 재시도
                        delay(60_000L)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                LogcatManager.d(TAG, "백그라운드 동기화 작업 종료")
                // CancellationException은 정상 종료이므로 아무것도 하지 않음
            } catch (e: Exception) {
                LogcatManager.e(TAG, "백그라운드 동기화 작업 중 치명적 오류", e)
            }
        }
    }

    /**
     * 백그라운드 이벤트 리스너 관리자 시작
     */
    private fun startBackgroundEventListenerManager() {
        eventListenerJob?.cancel()

        eventListenerJob = serviceScope?.launch {
            try {
                LogcatManager.d(TAG, " 백그라운드 이벤트 리스너 관리자 시작")

                // 연결 상태만이 아니라 이벤트 리스너 활성 여부·사진 미리보기 모드까지 함께
                // 관찰하는 레벨 트리거. "연결은 살아있는데 리스너만 꺼진" 경우(미리보기 탭이 USB
                // 리스너를 중단한 뒤 탭 이탈 등)를 감지해 자동 재시작한다. 미리보기 탭 체류 중
                // (previewMode=true)에는 USB 버스 경합 방지를 위해 재시작을 억제한다.
                combine(
                    globalConnectionManager.globalConnectionState
                        .map { it.isAnyConnectionActive }
                        .distinctUntilChanged(),
                    cameraRepository.isEventListenerActive(),
                    cameraRepository.isPhotoPreviewMode()
                ) { connected, listenerActive, previewMode ->
                    Triple(connected, listenerActive, previewMode)
                }.distinctUntilChanged()
                .collect { (isConnected, isListenerActive, isPreviewMode) ->
                    if (isConnected) {
                        // 카메라 연결 중에만 Wake Lock 유지 (없으면 획득)
                        ensureWakeLock()

                        when {
                            isPreviewMode -> {
                                // 사진 미리보기 모드 - USB 버스 경합 방지로 재시작 억제
                                LogcatManager.d(TAG, " 사진 미리보기 모드 - 이벤트 리스너 재시작 억제")
                            }

                            !isListenerActive -> {
                                LogcatManager.d(TAG, " 카메라 연결됨 + 리스너 비활성 + 미리보기 아님 - 재시작 시도")
                                try {
                                    val result = cameraRepository.startCameraEventListener()
                                    if (result.isSuccess) {
                                        LogcatManager.d(TAG, " 백그라운드에서 이벤트 리스너 재시작 성공")
                                        updateNotificationText("카메라 이벤트 리스너 활성 - 사진 수신 대기 중")
                                    } else {
                                        LogcatManager.w(TAG, " 백그라운드에서 이벤트 리스너 재시작 실패")
                                        updateNotificationText("카메라 연결 확인 중...")
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    LogcatManager.d(TAG, "이벤트 리스너 재시작 작업이 취소됨")
                                    throw e
                                } catch (e: Exception) {
                                    LogcatManager.e(TAG, "백그라운드 이벤트 리스너 시작 중 예외", e)
                                }
                            }

                            else -> {
                                updateNotificationText("카메라 이벤트 리스너 활성 - 사진 수신 대기 중")
                            }
                        }
                    } else {
                        // 카메라 연결이 끊어진 경우 - 리스너가 남아있을 때만 정리(중복 호출 방지)
                        if (isListenerActive) {
                            try {
                                val stopResult = cameraRepository.stopCameraEventListener()
                                if (stopResult.isSuccess) {
                                    LogcatManager.d(TAG, " 백그라운드에서 이벤트 리스너 정리 성공")
                                } else {
                                    LogcatManager.w(TAG, " 백그라운드에서 이벤트 리스너 정리 실패")
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                LogcatManager.d(TAG, "이벤트 리스너 정리 작업이 취소됨")
                                throw e
                            } catch (e: Exception) {
                                LogcatManager.e(TAG, "백그라운드 이벤트 리스너 정리 중 예외", e)
                            }
                        }

                        updateNotificationText("카메라 연결 대기 중...")

                        // 연결이 끊어지면 깨울 카메라가 없으므로 Wake Lock 해제 (배터리 절약)
                        releaseWakeLock()

                        LogcatManager.d(TAG, " 카메라 연결 끊김 - 이벤트 리스너 관리 대기 모드로 전환")

                        // 앱이 이미 최근앱에서 제거된 상태(스와이프 후 연결로 존속)에서 연결까지 끊기면
                        // idle FGS·영구 알림이 무한 잔존하지 않도록 서비스를 종료한다(onDestroy가 정리).
                        if (taskRemovedWhileConnected) {
                            LogcatManager.d(TAG, "태스크 제거 후 연결 끊김 - idle 잔존 방지로 서비스 종료")
                            stopSelf()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                LogcatManager.d(TAG, "백그라운드 이벤트 리스너 관리자가 정상적으로 종료됨")
                // CancellationException은 정상 종료이므로 아무것도 하지 않음
            } catch (e: Exception) {
                LogcatManager.e(TAG, "백그라운드 이벤트 리스너 관리자 중 치명적 오류", e)
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
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
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
            // 카메라 AP 모드로 연결 시 인터넷이 끊기므로 Firebase 연결 시도 생략
            val globalState = globalConnectionManager.globalConnectionState.first()
            if (globalState.activeConnectionType == com.inik.camcon.domain.model.CameraConnectionType.AP_MODE) {
                LogcatManager.d(TAG, "카메라 AP 모드 연결 중 - Firebase 연결 시도 생략")
                return
            }

            // Firebase Auth 연결 상태 확인
            val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val currentUser = firebaseAuth.currentUser

            if (currentUser != null) {
                // 필요시 토큰 갱신 (실패 시에만 로그)
                currentUser.getIdToken(false).addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
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

        } catch (e: Exception) {
            LogcatManager.w(TAG, "파일 동기화 상태 확인 실패", e)
        }
    }

}