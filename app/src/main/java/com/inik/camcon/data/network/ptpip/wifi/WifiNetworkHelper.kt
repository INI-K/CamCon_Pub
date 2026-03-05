package com.inik.camcon.data.network.ptpip.wifi
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.inik.camcon.domain.model.WifiCapabilities
import com.inik.camcon.domain.model.WifiNetworkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class WifiNetworkHelper @Inject constructor(context: Context) {
    companion object {
        private const val TAG = "WifiNetworkHelper"
    }
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _networkState = MutableStateFlow(readState())
    val networkStateFlow: StateFlow<WifiNetworkState> = _networkState.asStateFlow()
    fun isWifiConnected(): Boolean {
        refresh()
        return _networkState.value.isConnected
    }
    fun isConnectedToCameraAP(): Boolean {
        refresh()
        return _networkState.value.isConnectedToCameraAP
    }
    fun getCurrentSSID(): String? {
        return runCatching { wifiManager.connectionInfo?.ssid?.trim('"') }.getOrNull()
    }
    fun findAvailableCameraIP(): String? {
        refresh()
        return _networkState.value.detectedCameraIP
    }
    fun isStaConcurrencySupported(): Boolean {
        return runCatching {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        }.getOrDefault(false)
    }
    fun getWifiCapabilities(): WifiCapabilities {
        refresh()
        val info = runCatching { wifiManager.connectionInfo }.getOrNull()
        return WifiCapabilities(
            isConnected = _networkState.value.isConnected,
            isStaConcurrencySupported = isStaConcurrencySupported(),
            isConnectedToCameraAP = _networkState.value.isConnectedToCameraAP,
            networkName = _networkState.value.ssid,
            linkSpeed = info?.linkSpeed,
            frequency = info?.frequency,
            ipAddress = info?.ipAddress,
            macAddress = info?.macAddress,
            detectedCameraIP = _networkState.value.detectedCameraIP
        )
    }
    private fun refresh() {
        _networkState.value = readState()
    }
    private fun readState(): WifiNetworkState {
        return try {
            val active = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(active)
            val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val ssid = getCurrentSSID()
            val isCameraAp = ssid?.let { looksLikeCameraAp(it) } ?: false
            WifiNetworkState(
                isConnected = connected,
                isConnectedToCameraAP = isCameraAp,
                ssid = ssid,
                detectedCameraIP = if (isCameraAp) "192.168.1.1" else null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Wi-Fi 상태 조회 실패", e)
            WifiNetworkState(
                isConnected = false,
                isConnectedToCameraAP = false,
                ssid = null,
                detectedCameraIP = null
            )
        }
    }
    private fun looksLikeCameraAp(ssid: String): Boolean {
        val lower = ssid.lowercase()
        return lower.contains("nikon") || lower.contains("canon") || lower.contains("sony") || lower.contains("camera")
    }
}