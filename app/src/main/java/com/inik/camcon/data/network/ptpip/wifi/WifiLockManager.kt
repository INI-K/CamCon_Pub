package com.inik.camcon.data.network.ptpip.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi 성능 락(High Performance Lock) 관리
 * 카메라 통신 중 일정한 Wi-Fi 성능 보장
 */
@Singleton
class WifiLockManager @Inject constructor(
    context: Context
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * High performance Wi-Fi lock 획득
     */
    fun acquireWifiLock(tag: String = "CamConWifiLock"): Boolean {
        synchronized(this) {
            if (wifiLock?.isHeld == true) {
                Log.d(TAG, "이미 Wi-Fi 락이 획득됨")
                return true
            }
            return try {
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag)
                wifiLock?.setReferenceCounted(false)
                wifiLock?.acquire()
                Log.d(TAG, "✅ Wi-Fi high performance 락 획득")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi 락 획득 실패: ${e.message}")
                false
            }
        }
    }

    /**
     * Wi-Fi 락 해제
     */
    fun releaseWifiLock() {
        synchronized(this) {
            try {
                wifiLock?.let {
                    if (it.isHeld) {
                        it.release()
                        Log.d(TAG, "✅ Wi-Fi 락 해제")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi 락 해제 실패: ${e.message}")
            } finally {
                wifiLock = null
            }
        }
    }

    /**
     * Wi-Fi 락 현재 상태 확인
     */
    fun isWifiLockHeld(): Boolean {
        return wifiLock?.isHeld == true
    }

    companion object {
        private const val TAG = "WifiNetworkHelper"
    }
}
