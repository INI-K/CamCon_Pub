package com.inik.camcon.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AutoConnectForegroundService : Service() {

    @Inject
    lateinit var autoConnectTaskRunner: AutoConnectTaskRunner

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        val ssid = intent?.getStringExtra(AutoConnectTaskRunner.EXTRA_SSID)
        serviceScope.launch {
            try {
                autoConnectTaskRunner.handlePostConnection(ssid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "자동 연결 후처리 실패", e)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        // 공유 상태 알림 — REMOVE는 다른 서비스(BackgroundSync 등)가 쓰는 알림까지 지운다
        stopForeground(STOP_FOREGROUND_DETACH)
        CamConStatusNotification.detach(this, CamConStatusNotification.OWNER_AUTO_CONNECT)
    }

    private fun startForegroundService() {
        val notification = CamConStatusNotification.attach(
            this,
            CamConStatusNotification.OWNER_AUTO_CONNECT,
            "카메라 자동 연결 준비",
            "카메라 이벤트 리스너를 준비하는 중입니다"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                CamConStatusNotification.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(CamConStatusNotification.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "AutoConnectFGService"

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
         * 파일 전송 등 일시 상태를 공유 알림에 표시.
         * 과거처럼 서비스를 깨우지 않으므로(startService) 갱신 알림이 서비스 잔존을 만들지 않는다.
         */
        fun updateNotification(context: Context, title: String, message: String) {
            CamConStatusNotification.updateTransient(context, title, message)
        }
    }
}
