package com.inik.camcon.data.service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.inik.camcon.R
class BackgroundSyncService : Service() {
    companion object {
        private const val CHANNEL_ID = "camcon_background_sync"
        private const val NOTIFICATION_ID = 1001
        fun startService(context: Context) {
            val intent = Intent(context, BackgroundSyncService::class.java)
            context.startForegroundService(intent)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    private fun createNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("백그라운드 동기화 중")
            .setOngoing(true)
            .build()
    }
}