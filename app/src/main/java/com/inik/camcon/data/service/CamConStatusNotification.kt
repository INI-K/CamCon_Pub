package com.inik.camcon.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.inik.camcon.R

/**
 * 앱 상태 알림 단일화 지점.
 *
 * 과거에는 WifiMonitoringService(3001)·AutoConnectForegroundService(2001)·
 * BackgroundSyncService(1001)가 각자 채널·ID로 알림을 띄워 상태 알림이 3개까지 쌓였다.
 * 세 포그라운드 서비스가 같은 ID/채널의 알림 하나를 공유하고, 마지막으로 갱신한 상태가 표시된다.
 *
 * owner 규약:
 *  - 각 서비스는 startForeground 시 [attach], onDestroy 시 [detach]를 호출한다.
 *  - detach 시 남은 owner가 있으면 그쪽 상태 텍스트로 되돌리고(예: 연결 해제 → "자동 연결 대기 중"),
 *    마지막 owner가 빠지면 알림을 제거한다.
 *  - 서비스는 stopForeground(STOP_FOREGROUND_DETACH)를 사용해야 한다 — REMOVE는 다른
 *    서비스가 아직 쓰는 공유 알림까지 지운다.
 */
object CamConStatusNotification {

    const val NOTIFICATION_ID = 1001

    const val OWNER_SYNC = "sync"
    const val OWNER_WIFI = "wifi"
    const val OWNER_AUTO_CONNECT = "autoConnect"

    private const val TAG = "CamCon상태알림"
    private const val CHANNEL_ID = "camcon_status"

    /** CINE INSTRUMENT 앰버 — 상태바 아이콘 틴트 브랜드 색. */
    private const val BRAND_COLOR = 0xFFE8A245.toInt()

    // 통합 전 서비스별 채널(+진동 이슈로 교체됐던 채널). 발견 즉시 삭제해 설정 잔재를 없앤다.
    private val LEGACY_CHANNEL_IDS = listOf(
        "background_sync_channel",
        "wifi_monitoring_channel",
        "auto_connect_channel",
        "auto_connect_channel_silent"
    )

    /** owner별 마지막 상태 텍스트 — detach 시 남은 owner의 상태로 복원하기 위해 보관 */
    private val ownerContents = LinkedHashMap<String, Pair<String, String>>()

    /** startForeground에 넘길 알림 생성 + owner 등록 */
    @Synchronized
    fun attach(context: Context, owner: String, title: String, text: String): Notification {
        ensureChannel(context)
        ownerContents[owner] = title to text
        return build(context, title, text)
    }

    /** owner의 상태 텍스트 갱신 (알림 재게시) */
    @Synchronized
    fun update(context: Context, owner: String, title: String, text: String) {
        ensureChannel(context)
        ownerContents[owner] = title to text
        post(context, title, text)
    }

    /**
     * 파일 전송 진행 등 일시 상태 표시 — owner 미등록.
     * 살아있는 FGS owner가 없으면 게시하지 않는다(제거 주체가 없는 고아 알림 방지).
     */
    @Synchronized
    fun updateTransient(context: Context, title: String, text: String) {
        if (ownerContents.isEmpty()) return
        post(context, title, text)
    }

    /** 서비스 종료 시 호출 — 남은 owner 상태로 복원하거나 마지막이면 알림 제거 */
    @Synchronized
    fun detach(context: Context, owner: String) {
        ownerContents.remove(owner)
        val remaining = ownerContents.values.lastOrNull()
        if (remaining == null) {
            try {
                notificationManager(context).cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.w(TAG, "상태 알림 제거 실패: ${e.message}")
            }
        } else {
            post(context, remaining.first, remaining.second)
        }
    }

    private fun post(context: Context, title: String, text: String) {
        try {
            notificationManager(context).notify(NOTIFICATION_ID, build(context, title, text))
        } catch (e: Exception) {
            Log.w(TAG, "상태 알림 게시 실패: ${e.message}")
        }
    }

    private fun build(context: Context, title: String, text: String): Notification {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(context.packageName)
            }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_camcon)
            .setColor(BRAND_COLOR)
            .setColorized(false)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = notificationManager(context)
            LEGACY_CHANNEL_IDS.forEach { manager.deleteNotificationChannel(it) }
            val channel = NotificationChannel(
                CHANNEL_ID,
                "카메라 상태",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "카메라 연결·파일 수신 상태 표시"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
