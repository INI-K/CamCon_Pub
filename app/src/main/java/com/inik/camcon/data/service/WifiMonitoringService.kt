package com.inik.camcon.data.service

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
import com.inik.camcon.CameraNative
import com.inik.camcon.R
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.ConnectionMethod
import com.inik.camcon.domain.model.PtpipConnectionState
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    @Inject
    lateinit var ptpipDataSource: PtpipDataSource

    @Inject
    lateinit var cameraRepository: CameraRepository

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // 콜백 스레드(onLost)와 serviceScope 코루틴 워커 스레드 양쪽에서 접근하므로
    // @Volatile로 cross-thread 가시성을 보장한다. autoConnectMutex는 코루틴 본문만
    // 직렬화할 뿐 콜백 스레드의 write는 보호하지 못하므로 plain var는 가시성 미보장.
    @Volatile
    private var lastConnectedSSID: String? = null

    // 두 콜백(onAvailable/onCapabilitiesChanged)이 거의 동시에 발동될 때
    // lastConnectedSSID check-then-act 및 AutoConnectForegroundService 시작을
    // 단일 진입으로 직렬화해 중복 시작을 방지한다.
    private val autoConnectMutex = Mutex()

    // 네트워크 콜백 중복 방지 (콜백 스레드에서 접근, 가시성 보장 위해 @Volatile)
    @Volatile
    private var lastProcessedNetwork: Network? = null
    @Volatile
    private var lastProcessedTime: Long = 0
    private val MIN_PROCESS_INTERVAL_MS = 5000L // 5초 간격으로만 처리

    companion object {
        private const val TAG = "WifiMonitoringService"

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
        Log.d(TAG, "📡 WiFi 모니터링 Service 시작")

        // Android 14+는 onCreate~onStartCommand 사이 startForeground가 필수이므로 일단 FGS로 승격한다.
        startForegroundService()
        startWifiMonitoring()

        // H14: 자동 연결 토글이 실제로 켜져 있을 때만 connectedDevice FGS를 유지한다.
        // 토글이 꺼져 있으면(직접 시작·시스템 재기동 등) idle FGS·상시 알림을 남기지 않도록 즉시 종료.
        ensureAutoConnectEnabledOrStop()

        // STA 핫스팟 상시 자동 연결 폴링 시작 (managed serviceScope에서 실행,
        // onDestroy의 serviceScope.cancel()로 자동 정리)
        startHotspotAutoConnectPolling()
    }

    /**
     * H14: 자동 연결 토글이 꺼져 있으면 FGS를 유지하지 않고 서비스를 종료한다.
     * (연결도 아닌 단순 스캔 대기 상태의 상시 connectedDevice FGS 방지)
     */
    private fun ensureAutoConnectEnabledOrStop() {
        serviceScope.launch {
            try {
                if (!preferencesDataSource.isAutoConnectEnabledNow()) {
                    Log.d(TAG, "⏹️ 자동 연결 토글 OFF - FGS 유지 안 함, 서비스 종료")
                    stopWifiMonitoring()
                    // 공유 상태 알림 규약: REMOVE는 다른 서비스(BackgroundSync 등)가 쓰는 알림까지
                    // 지운다. DETACH + owner 해제로 남은 owner가 있으면 그쪽 상태로 복원, 없으면 제거한다.
                    stopForeground(STOP_FOREGROUND_DETACH)
                    CamConStatusNotification.detach(this@WifiMonitoringService, CamConStatusNotification.OWNER_WIFI)
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.w(TAG, "자동 연결 토글 확인 실패", e)
            }
        }
    }

    /**
     * 폰 핫스팟(STA) 상시 자동 연결 폴링.
     *
     * managed [serviceScope]에서 4초 주기로 실행하며, 다음 조건을 모두 만족할 때만
     * 기존 discovery/connect 경로를 그대로 호출한다:
     *  - 자동 연결 설정(auto_connect, "마지막 연결된 카메라에 자동 연결") ON
     *  - 폰 핫스팟 활성화 상태
     *  - PTP/IP 연결 상태 == DISCONNECTED
     *  - 한 번이라도 연결 성공한 "직전 카메라"가 기억되어 있음 (없으면 자동 연결 안 함)
     *  - 촬영/영상녹화/라이브뷰-종료전이/프리뷰 미활성
     *
     * 자동 연결 대상은 "기억된 직전 카메라"로 한정한다. discoverCameras(false)가 캐시 IP(직전 카메라)를
     * 우선 시도하므로 대부분 그 카메라를 반환하지만, 발견 카메라 IP가 기억된 카메라와 다르면
     * (다른 카메라) 오연결을 막기 위해 연결하지 않는다.
     *
     * 신규 discovery/connect 로직·네이티브 변경 없음 — 파라미터만 전달한다.
     */
    private fun startHotspotAutoConnectPolling() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val remembered = ptpipDataSource.getLastConnectedCamera()
                    if (preferencesDataSource.isAutoConnectEnabledNow()
                        && wifiNetworkHelper.isHotspotEnabled()
                        && ptpipDataSource.connectionState.value == PtpipConnectionState.DISCONNECTED
                        && remembered != null
                        && !isCameraBusy()
                    ) {
                        withContext(ioDispatcher) {
                            val cameras = ptpipDataSource.discoverCameras(forceApMode = false)
                            // 기억된 직전 카메라와 IP가 일치하는 발견 카메라만 자동 연결 대상.
                            val camera = cameras.firstOrNull {
                                it.ipAddress == remembered.ipAddress
                            }
                            // discover 사이 상태 변화/촬영 진입에 대비해 connect 직전 재확인
                            if (camera != null
                                && ptpipDataSource.connectionState.value == PtpipConnectionState.DISCONNECTED
                                && !isCameraBusy()
                            ) {
                                Log.d(TAG, "🔗 STA 핫스팟 자동 연결 시도(직전 카메라): ${camera.name}")
                                val ok = ptpipDataSource.connectToCamera(
                                    camera,
                                    ConnectionMethod.STA_PHONE_HOTSPOT
                                )
                                // 폴링 경로는 PtpipConnectionHelper를 거치지 않으므로 여기서 직접 영속 저장한다.
                                // (수동 연결 경로는 Helper가 이미 저장 — 앱 재시작 시 restoreLastConnectedCamera 복원)
                                if (ok) {
                                    preferencesDataSource.saveLastConnectedCamera(
                                        camera.ipAddress,
                                        camera.name
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "STA 핫스팟 자동 연결 폴링 오류", e)
                }
                delay(4000)
            }
        }
    }

    /**
     * 촬영/영상녹화/라이브뷰-종료전이/프리뷰 탭 중이면 세션 방해를 피하기 위해 스킵한다.
     * sibling BackgroundSyncService와 동일하게 CameraRepository(도메인 인터페이스) +
     * CameraNative(전역 싱글톤)를 재사용하므로 신규 레이어 위반 없음.
     */
    private suspend fun isCameraBusy(): Boolean {
        return runCatching {
            // J8: 살아있는 USB 세션(공유 네이티브 핸들)이 있으면 폴링 연결 시도를 스킵한다.
            // discoverCameras→connectToCamera가 mDNS 발견 순간 initCameraWithPtpip로 USB 핸들을
            // 무경고 파괴하기 때문. USB가 소유 중이면 PTP/IP 상태가 DISCONNECTED여도 손대지 않는다.
            ptpipDataSource.isUsbCameraActive()
                || CameraNative.isVideoRecording()
                || CameraNative.isLiveViewStopping()
                || cameraRepository.isPhotoPreviewMode().first()
        }.getOrDefault(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand 호출됨 - flags: $flags, startId: $startId")

        // 서비스가 재시작된 경우 (시스템에 의해 종료 후)
        if (intent == null) {
            Log.w(TAG, "⚠️ 시스템에 의한 서비스 재시작 감지")
            // H14: START_STICKY 재기동 시에도 자동 연결 토글이 여전히 켜져 있을 때만 유지한다.
            // (재기동 사이 토글이 꺼졌다면 idle FGS·상시 알림이 잔존하지 않도록 종료)
            ensureAutoConnectEnabledOrStop()
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
        Log.d(TAG, "📡 WiFi 모니터링 Service 종료")

        stopWifiMonitoring()
        serviceScope.cancel()
        // 공유 상태 알림 — REMOVE는 다른 서비스가 쓰는 알림까지 지운다
        stopForeground(STOP_FOREGROUND_DETACH)
        CamConStatusNotification.detach(this, CamConStatusNotification.OWNER_WIFI)
        super.onDestroy()
    }

    /**
     * Foreground Service 시작 (알림 표시)
     */
    private fun startForegroundService() {
        val notification = CamConStatusNotification.attach(
            this,
            CamConStatusNotification.OWNER_WIFI,
            getString(R.string.notif_wifi_waiting_title),
            getString(R.string.notif_wifi_waiting_text)
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

        Log.d(TAG, "✅ Foreground Service 시작 완료 (알림 표시됨)")
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
                    if (shouldProcessNetwork(network)) {
                        checkAndTriggerAutoConnect(network)
                    }
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
        } finally {
            // 재해제 시 IllegalArgumentException(이미 unregister된 콜백) 방지
            networkCallback = null
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
            // check-then-act(lastConnectedSSID) + 서비스 시작을 단일 Mutex로 직렬화
            autoConnectMutex.withLock {
            try {
                Log.d(TAG, "🔍 자동 연결 조건 확인 시작 (Service)")

                // 0. WiFi 연결 상태 먼저 확인 (매우 중요!)
                val isWifiConnected = wifiNetworkHelper.isWifiConnected()
                Log.d(TAG, "  0. WiFi 연결 상태: $isWifiConnected")
                if (!isWifiConnected) {
                    Log.d(TAG, "❌ WiFi가 연결되어 있지 않음 - NetworkCallback이 너무 빨리 발동됨")
                    return@launch
                }

                // 1. 자동 연결이 활성화되어 있는지 확인
                val isAutoConnectEnabled = preferencesDataSource.isAutoConnectEnabledNow()
                Log.d(TAG, "  1. 자동 연결 활성화: $isAutoConnectEnabled")
                if (!isAutoConnectEnabled) {
                    Log.d(TAG, "❌ 자동 연결이 비활성화되어 있음")
                    return@launch
                }

                // 1-2. 저장된 자동 연결 설정 확인 (먼저 확인)
                val autoConnectConfig = preferencesDataSource.getAutoConnectNetworkConfig()
                Log.d(TAG, "  1-2. 저장된 설정 SSID: ${LogMask.ssid(autoConnectConfig?.ssid)}")
                if (autoConnectConfig == null) {
                    Log.d(TAG, "❌ 저장된 자동 연결 설정 없음")
                    return@launch
                }

                // 2. 현재 연결된 SSID 확인 (Network 객체에서 직접 추출)
                var currentSSID: String? = null

                try {
                    currentSSID = wifiNetworkHelper.getSSIDFromNetwork(network)
                    if (currentSSID != null) {
                        Log.d(TAG, "  2-a. Network 객체에서 SSID 획득 성공: ${LogMask.ssid(currentSSID)}")
                    } else {
                        Log.w(TAG, "  2-a. Network 객체에서 SSID를 가져올 수 없음")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "  2-a. Network에서 SSID 가져오기 실패", e)
                }

                // Network 방식이 실패한 경우 기존 방식 시도
                if (currentSSID.isNullOrEmpty()) {
                    currentSSID = wifiNetworkHelper.getCurrentSSID()
                    Log.d(TAG, "  2-b. WifiNetworkHelper에서 SSID 획득: ${LogMask.ssid(currentSSID)}")
                }

                // 재시도 로직 (1회만, 2초 대기)
                if (currentSSID.isNullOrEmpty() || currentSSID == "<unknown ssid>") {
                    Log.d(TAG, "  2-c. SSID 획득 실패, 2초 대기 후 재시도...")
                    kotlinx.coroutines.delay(2000)

                    currentSSID = wifiNetworkHelper.getSSIDFromNetwork(network)
                    if (currentSSID == null) {
                        currentSSID = wifiNetworkHelper.getCurrentSSID()
                    }
                    Log.d(TAG, "  2-d. 재시도 후 SSID: ${LogMask.ssid(currentSSID)}")
                }

                // J9: SSID를 확정할 수 없으면(위치 권한 부재 등 API 29+ 마스킹으로 <unknown ssid>)
                // 저장된 카메라 SSID로 '간주'하지 않는다. 과거엔 여기서 저장 SSID로 대체해 이후
                // 비교가 자기 자신과의 비교라 항상 통과 → 일반 집/회사/공용 Wi-Fi에서도 FGS 기동·
                // 프로세스 바인딩·15740 포트 스캔이 오발동했다. 불확실하면 no-op으로 건너뛴다.
                if (currentSSID.isNullOrEmpty() || currentSSID == "<unknown ssid>") {
                    Log.w(TAG, "  2-e. SSID를 확정할 수 없음 - 저장 SSID 대체 안 함, 자동 연결 건너뜀")
                    return@launch
                }

                Log.d(TAG, "  2. 최종 SSID: ${LogMask.ssid(currentSSID)}")

                // 3. 이미 처리한 SSID인지 확인 (중복 방지)
                Log.d(TAG, "  3. 마지막 처리된 SSID: ${LogMask.ssid(lastConnectedSSID)}")
                if (currentSSID == lastConnectedSSID) {
                    Log.d(TAG, "❌ 이미 처리된 SSID: ${LogMask.ssid(currentSSID)}")
                    return@launch
                }

                // 4. SSID 일치 확인 (저장된 설정과 비교)
                Log.d(TAG, "  4. SSID 비교: '${LogMask.ssid(currentSSID)}' vs '${LogMask.ssid(autoConnectConfig.ssid)}'")
                if (currentSSID != autoConnectConfig.ssid) {
                    Log.d(TAG, "❌ SSID 불일치")
                    return@launch
                }

                // 5. 카메라 AP 확인 (SSID 패턴 기반) — 패턴 정본은 WifiNetworkHelper 단일 소스로 단일화.
                // (과거엔 이 서비스가 자체 리스트를 중복 관리해 WifiNetworkHelper와 발산했다.)
                val isCameraSSID = WifiNetworkHelper.isCameraApSsid(currentSSID)
                Log.d(TAG, "  5. 카메라 SSID 패턴: $isCameraSSID")
                if (!isCameraSSID) {
                    Log.d(TAG, "❌ 카메라 AP가 아님: ${LogMask.ssid(currentSSID)}")
                    return@launch
                }

                Log.d(TAG, "✅ 자동 연결 조건 충족 (Service) - SSID: ${LogMask.ssid(currentSSID)}, 카메라 패턴 일치")

                // 6. 중복 실행 방지
                lastConnectedSSID = currentSSID

                // 7. AutoConnectForegroundService 시작
                Log.d(TAG, "🚀 AutoConnectForegroundService 시작 (Service)")
                AutoConnectForegroundService.start(applicationContext, currentSSID)

            } catch (e: Exception) {
                Log.e(TAG, "❌ 자동 연결 확인 중 오류 (Service)", e)
            }
            }
        }
    }

}
