package com.inik.camcon.data.network.ptpip.discovery
import android.content.Context
import android.util.Log
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.domain.model.PtpipCamera
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class PtpipDiscoveryService @Inject constructor(
    private val context: Context,
    private val wifiHelper: WifiNetworkHelper
) {
    companion object {
        private const val TAG = "PtpipDiscoveryService"
    }
    fun discoverCameras(): List<PtpipCamera> {
        if (!wifiHelper.isWifiConnected()) return emptyList()
        val ip = wifiHelper.findAvailableCameraIP() ?: return emptyList()
        val name = wifiHelper.getCurrentSSID() ?: context.packageName
        Log.d(TAG, "카메라 검색 결과: $ip")
        return listOf(PtpipCamera(ipAddress = ip, port = 15740, name = name, isOnline = true))
    }
    fun stopDiscovery() {
        Log.d(TAG, "카메라 검색 중지")
    }
}