package com.inik.camcon.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.inik.camcon.R
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

    // 중복 onStartCommand 시 탈락자(AutoConnectTaskRunner CAS 탈락)의 finally stopSelf가 진행 중인
    // 승자 작업을 취소하지 않도록, 진행 중 작업 수를 세어 마지막 작업이 끝날 때만 종료한다.
    // 증가(onStartCommand)와 감소+종료(finally)를 같은 lock으로 직렬화해 종료 직전 새 기동이
    // 끼어드는 레이스를 차단한다.
    private val taskLock = Any()
    private var activeTasks = 0
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        val ssid = intent?.getStringExtra(AutoConnectTaskRunner.EXTRA_SSID)
        synchronized(taskLock) {
            latestStartId = startId
            activeTasks++
        }
        serviceScope.launch {
            try {
                autoConnectTaskRunner.handlePostConnection(ssid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "자동 연결 후처리 실패", e)
            } finally {
                synchronized(taskLock) {
                    // 진행 중인 다른 작업이 없을 때만 종료. 종료 직전 새 기동이 들어왔다면
                    // stopSelf(latestStartId)가 최신 startId가 아니어서 서비스를 살려둔다.
                    if (--activeTasks == 0) {
                        stopSelf(latestStartId)
                    }
                }
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
            getString(R.string.notif_auto_connect_prepare_title),
            getString(R.string.notif_auto_connect_prepare_text)
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
