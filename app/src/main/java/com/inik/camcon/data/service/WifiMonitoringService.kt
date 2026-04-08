package com.inik.camcon.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.inik.camcon.R
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WiFi 자동 연결 모니터링 전용 Foreground Service
 *
 * 자동 연결 토글이 켜져있을 때 상시 실행되어 WiFi 네트워크 변화를 감지합니다.
 *
 * ## 작동 방식:
 * 1. 자동 연결 토글 ON → Service 시작
 * 2. NetworkCallback으로 WiFi 연결 감지
 * 3. 조건 충족 시 AutoConnectForegroundService 실행
 * 4. 자동 연결 토글 OFF → Service 중지
 */
@AndroidEntryPoint
class WifiMonitoringService : Service() {

    @Inject
    lateinit var wifiNetworkHelper: WifiNetworkHelper

    @Inject
    lateinit var preferencesDataSource: PtpipPreferencesDataSource

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastConnectedSSID: String? = null

    // 네트워크 콜백 중복 방지
    private var lastProcessedNetwork: Network? = null
    private var lastProcessedTime: Long = 0
    private val MIN_PROCESS_INTERVAL_MS = 5000L // 5초 간격으로만 처리

    companion object {
        private const val TAG = "WifiMonitoringService"
        private const val CHANNEL_ID = "wifi_monitoring_channel"
        private const val NOTIFICATION_ID = 3001

        /**
         * Service 시작
         */
        fun start(context: Context) {
            val intent = Intent(context, WifiMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Service 중지
         */
        fun stop(context: Context) {
            val intent = Intent(context, WifiMonitoringService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "========================================")
        Log.d(TAG, "📡 WiFi 모니터링 Service 시작")
        Log.d(TAG, "========================================")

        startForegroundService()
        startWifiMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand 호출됨 - flags: $flags, startId: $startId")

        // 서비스가 재시작된 경우 (시스템에 의해 종료 후)
        if (intent == null) {
            Log.w(TAG, "⚠️ 시스템에 의한 서비스 재시작 감지")
            // 네트워크 모니터링이 중지되었을 수 있으므로 재시작
            if (networkCallback == null) {
                Log.d(TAG, "📡 네트워크 모니터링 재시작")
                startWifiMonitoring()
            }
        }

        // START_STICKY: 시스템이 서비스를 종료한 경우 자동으로 재시작
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "📱 앱이 태스크에서 제거됨 - 서비스는 계속 실행")
        // 앱이 종료되어도 서비스는 계속 실행
        // START_STICKY로 인해 시스템이 자동으로 재시작
    }

    override fun onDestroy() {
        Log.d(TAG, "========================================")
        Log.d(TAG, "📡 WiFi 모니터링 Service 종료")
        Log.d(TAG, "========================================")

        stopWifiMonitoring()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * Foreground Service 시작 (알림 표시)
     */
    private fun startForegroundService() {
        createNotificationChannel()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        Log.d(TAG, "✅ Foreground Service 시작 완료 (알림 표시됨)")
    }

    /**
     * 알림 생성
     */
    private fun buildNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_camera_24)
            .setContentTitle("카메라 자동 연결 대기 중")
            .setContentText("설정된 WiFi 네트워크 연결을 감지하고 있습니다")
            .setPriority(NotificationCompat.PRIORITY_LOW) // 조용한 알림
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true) // 스와이프로 제거 불가
            .build()
    }

    /**
     * 알림 채널 생성 (Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi 자동 연결 모니터링",
                NotificationManager.IMPORTANCE_LOW // 조용한 알림
            ).apply {
                description = "카메라 WiFi 자동 연결을 위한 백그라운드 모니터링"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * WiFi 네트워크 모니터링 시작
     */
    private fun startWifiMonitoring() {
        try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "📡 네트워크 사용 가능: $network")
                    checkAndTriggerAutoConnect(network)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        Log.d(TAG, "📡 WiFi 네트워크 기능 변경: $network")
                        if (shouldProcessNetwork(network)) {
                            checkAndTriggerAutoConnect(network)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "📡 네트워크 손실: $network")
                    lastConnectedSSID = null
                }
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            connectivityManager.registerNetworkCallback(request, networkCallback!!)
            Log.d(TAG, "✅ WiFi 모니터링 NetworkCallback 등록 완료")

        } catch (e: Exception) {
            Log.e(TAG, "❌ WiFi 모니터링 시작 실패", e)
        }
    }

    /**
     * WiFi 네트워크 모니터링 중지
     */
    private fun stopWifiMonitoring() {
        try {
            networkCallback?.let {
                val connectivityManager =
                    getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
                Log.d(TAG, "✅ NetworkCallback 해제 완료")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ NetworkCallback 해제 중 오류", e)
        }
    }

    private fun shouldProcessNetwork(network: Network): Boolean {
        val currentTime = System.currentTimeMillis()
        if (network == lastProcessedNetwork && currentTime - lastProcessedTime < MIN_PROCESS_INTERVAL_MS) {
            val timeSinceLastProcess = (currentTime - lastProcessedTime) / 1000.0
            Log.v(TAG, "⏭️ 네트워크 콜백 무시 (${String.format("%.1f", timeSinceLastProcess)}초 전 처리됨)")
            return false
        }
        lastProcessedNetwork = network
        lastProcessedTime = currentTime
        return true
    }

    /**
     * 자동 연결 조건 확인 및 실행
     */
    private fun checkAndTriggerAutoConnect(network: Network) {
        serviceScope.launch {
            try {
                Log.d(TAG, "========================================")
                Log.d(TAG, "🔍 자동 연결 조건 확인 시작 (Service)")

                // 0. WiFi 연결 상태 먼저 확인 (매우 중요!)
                val isWifiConnected = wifiNetworkHelper.isWifiConnected()
                Log.d(TAG, "  0. WiFi 연결 상태: $isWifiConnected")
                if (!isWifiConnected) {
                    Log.d(TAG, "❌ WiFi가 연결되어 있지 않음 - NetworkCallback이 너무 빨리 발동됨")
                    Log.d(TAG, "========================================")
                    return@launch
                }

                // 1. 자동 연결이 활성화되어 있는지 확인
                val isAutoConnectEnabled = preferencesDataSource.isAutoConnectEnabledNow()
                Log.d(TAG, "  1. 자동 연결 활성화: $isAutoConnectEnabled")
                if (!isAutoConnectEnabled) {
                    Log.d(TAG, "❌ 자동 연결이 비활성화되어 있음")
                    Log.d(TAG, "========================================")
                    return@launch
                }

                // 1-2. 저장된 자동 연결 설정 확인 (먼저 확인)
                val autoConnectConfig = preferencesDataSource.getAutoConnectNetworkConfig()
                Log.d(TAG, "  1-2. 저장된 설정 SSID: ${autoConnectConfig?.ssid ?: "(없음)"}")
                if (autoConnectConfig == null) {
                    Log.d(TAG, "❌ 저장된 자동 연결 설정 없음")
                    Log.d(TAG, "========================================")
                    return@launch
                }

                // 2. 현재 연결된 SSID 확인 (Network 객체에서 직접 추출)
                var currentSSID: String? = null

                try {
                    currentSSID = wifiNetworkHelper.getSSIDFromNetwork(network)
                    if (currentSSID != null) {
                        Log.d(TAG, "  2-a. Network 객체에서 SSID 획득 성공: $currentSSID")
                    } else {
                        Log.w(TAG, "  2-a. Network 객체에서 SSID를 가져올 수 없음")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "  2-a. Network에서 SSID 가져오기 실패", e)
                }

                // Network 방식이 실패한 경우 기존 방식 시도
                if (currentSSID.isNullOrEmpty()) {
                    currentSSID = wifiNetworkHelper.getCurrentSSID()
                    Log.d(TAG, "  2-b. WifiNetworkHelper에서 SSID 획득: ${currentSSID ?: "(null)"}")
                }

                // 재시도 로직 (1회만, 2초 대기)
                if (currentSSID.isNullOrEmpty() || currentSSID == "<unknown ssid>") {
                    Log.d(TAG, "  2-c. SSID 획득 실패, 2초 대기 후 재시도...")
                    kotlinx.coroutines.delay(2000)

                    currentSSID = wifiNetworkHelper.getSSIDFromNetwork(network)
                    if (currentSSID == null) {
                        currentSSID = wifiNetworkHelper.getCurrentSSID()
                    }
                    Log.d(TAG, "  2-d. 재시도 후 SSID: ${currentSSID ?: "(null)"}")
                }

                // SSID를 여전히 가져올 수 없는 경우, 저장된 설정의 SSID를 사용
                if (currentSSID.isNullOrEmpty() || currentSSID == "<unknown ssid>") {
                    Log.w(TAG, "  2-e. SSID를 가져올 수 없음 - 저장된 설정의 SSID 사용")
                    currentSSID = autoConnectConfig.ssid
                    Log.d(TAG, "  2-f. 저장된 설정 SSID 사용: $currentSSID")

                    // 저장된 SSID를 사용할 때는 실제로 WiFi에 연결되었는지 재확인
                    val recheckWifiConnected = wifiNetworkHelper.isWifiConnected()
                    Log.d(TAG, "  2-g. WiFi 재확인: $recheckWifiConnected")
                    if (!recheckWifiConnected) {
                        Log.w(TAG, "❌ SSID를 확인할 수 없고, WiFi도 연결되지 않음 - 건너뜀")
                        Log.d(TAG, "========================================")
                        return@launch
                    }
                }

                Log.d(TAG, "  2. 최종 SSID: $currentSSID")

                // 3. 이미 처리한 SSID인지 확인 (중복 방지)
                Log.d(TAG, "  3. 마지막 처리된 SSID: ${lastConnectedSSID ?: "(없음)"}")
                if (currentSSID == lastConnectedSSID) {
                    Log.d(TAG, "❌ 이미 처리된 SSID: $currentSSID")
                    Log.d(TAG, "========================================")
                    return@launch
                }

                // 4. SSID 일치 확인 (저장된 설정과 비교)
                Log.d(TAG, "  4. SSID 비교: '$currentSSID' vs '${autoConnectConfig.ssid}'")
                if (currentSSID != autoConnectConfig.ssid) {
                    Log.d(TAG, "❌ SSID 불일치")
                    Log.d(TAG, "========================================")
                    return@launch
                }

                // 5. 카메라 AP 확인 (SSID 패턴 기반)
                val isCameraSSID = isCameraNetwork(currentSSID)
                Log.d(TAG, "  5. 카메라 SSID 패턴: $isCameraSSID")
                if (!isCameraSSID) {
                    Log.d(TAG, "❌ 카메라 AP가 아님: $currentSSID")
                    Log.d(TAG, "========================================")
                    return@launch
                }

                Log.d(TAG, "========================================")
                Log.d(TAG, "✅✅✅ 자동 연결 조건 충족! (Service) ✅✅✅")
                Log.d(TAG, "  - SSID: $currentSSID")
                Log.d(TAG, "  - 카메라 SSID 패턴 일치: true")
                Log.d(TAG, "========================================")

                // 6. 중복 실행 방지
                lastConnectedSSID = currentSSID

                // 7. AutoConnectForegroundService 시작
                Log.d(TAG, "🚀 AutoConnectForegroundService 시작 (Service)")
                AutoConnectForegroundService.start(applicationContext, currentSSID)

            } catch (e: Exception) {
                Log.e(TAG, "❌ 자동 연결 확인 중 오류 (Service)", e)
                Log.d(TAG, "========================================")
            }
        }
    }

    /**
     * SSID가 카메라 관련 네트워크인지 판단
     */
    private fun isCameraNetwork(ssid: String): Boolean {
        val cameraPatterns = listOf(
            "CANON", "Canon",
            "NIKON", "Nikon",
            "SONY", "Sony",
            "FUJIFILM", "Fujifilm", "FUJI",
            "OLYMPUS", "Olympus",
            "PANASONIC", "Panasonic", "Lumix", "LUMIX",
            "PENTAX", "Pentax", "RICOH", "Ricoh",
            "LEICA", "Leica",
            "HASSELBLAD", "Hasselblad",
            "GoPro", "GOPRO", "Hero",
            "DJI", "Dji", "Mavic", "Phantom", "Inspire", "Mini",
            "Insta360", "INSTA360",
            "EOS", "PowerShot", "IXUS", "VIXIA",
            "COOLPIX", "Z_5", "Z_6", "Z_7", "Z8", "Z9", "Z30", "Z50", "Zfc",
            "D3500", "D5600", "D7500", "D780", "D850", "D500",
            "Alpha", "FX30", "FX3", "A7R", "A7S", "A7C", "A7IV", "A9",
            "RX100", "RX10", "ZV-1", "ZV-E10",
            "X-T4", "X-T5", "X-T30", "X-T50", "X-PRO3", "X-E4", "X-S10", "X-S20",
            "X-A7", "X-H1", "X-H2", "GFX50", "GFX100",
            "OM-D", "E-M1", "E-M5", "E-M10", "PEN", "E-PL", "E-P7",
            "GH5", "GH6", "GX9", "G9", "G100", "FZ1000", "LX100",
            "GODOX", "Godox", "Flashpoint",
            "Osmo", "OSMO", "Pocket", "Action", "Mobile",
            "SIGMA", "fp", "TAMRON"
        )

        return cameraPatterns.any { pattern ->
            ssid.contains(pattern, ignoreCase = true)
        }
    }
}
