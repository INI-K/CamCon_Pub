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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.inik.camcon.R
import com.inik.camcon.presentation.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AutoConnectForegroundService : Service() {

    @Inject
    lateinit var autoConnectTaskRunner: AutoConnectTaskRunner

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var notificationManager: NotificationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent?.action) {
            ACTION_UPDATE_NOTIFICATION -> {
                val title = intent.getStringExtra(EXTRA_NOTIFICATION_TITLE)
                val message = intent.getStringExtra(EXTRA_NOTIFICATION_MESSAGE)
                // startForegroundService()로 시작된 경우 반드시 startForeground() 호출 필요
                val notification = buildNotification(title, message)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                Log.d(TAG, "알림 업데이트: $title - $message")
                // 알림 업데이트만 하고 바로 종료
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                startForegroundService()
                val ssid = intent?.getStringExtra(AutoConnectTaskRunner.EXTRA_SSID)
                serviceScope.launch {
                    autoConnectTaskRunner.handlePostConnection(ssid)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification = buildNotification(
            "카메라 자동 연결 준비",
            "카메라 이벤트 리스너를 준비하는 중입니다"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        title: String? = null,
        message: String? = null
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_camera_24)
            .setContentTitle(title ?: "카메라 자동 연결 준비")
            .setContentText(message ?: "카메라 이벤트 리스너를 준비하는 중입니다")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String?, message: String?) {
        val notification = buildNotification(title, message)
        notificationManager?.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "알림 업데이트: $title - $message")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "카메라 자동 연결",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "카메라 자동 연결 처리 중 표시되는 알림"
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "auto_connect_channel"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "AutoConnectFGService"

        private const val ACTION_UPDATE_NOTIFICATION = "com.inik.camcon.ACTION_UPDATE_NOTIFICATION"
        private const val EXTRA_NOTIFICATION_TITLE = "extra_notification_title"
        private const val EXTRA_NOTIFICATION_MESSAGE = "extra_notification_message"

        fun start(context: Context, ssid: String?) {
            val intent = Intent(context, AutoConnectForegroundService::class.java)
            intent.putExtra(AutoConnectTaskRunner.EXTRA_SSID, ssid)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 알림 텍스트 업데이트
         */
        fun updateNotification(context: Context, title: String, message: String) {
            val intent = Intent(context, AutoConnectForegroundService::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
                putExtra(EXTRA_NOTIFICATION_TITLE, title)
                putExtra(EXTRA_NOTIFICATION_MESSAGE, message)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
