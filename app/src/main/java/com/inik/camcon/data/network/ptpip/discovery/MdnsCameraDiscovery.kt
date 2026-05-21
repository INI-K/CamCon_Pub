package com.inik.camcon.data.network.ptpip.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.inik.camcon.domain.model.CameraEndpoint
import com.inik.camcon.domain.model.EndpointSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * NsdManager 기반 mDNS 카메라 검색.
 *
 * 표준 PTP/IP 서비스 타입(`_ptp._tcp`) 및 제조사 변형을 순회한다.
 * 카메라가 자기 자신을 어떤 타입으로 광고하는지 모델별로 달라서 복수 타입을 시도한다.
 */
@Singleton
class MdnsCameraDiscovery @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val TAG = "MdnsCameraDiscovery"

        /** 시도할 mDNS 서비스 타입. NsdManager 형식이라 끝에 `.`이 필요. */
        private val SERVICE_TYPES = listOf(
            "_ptp._tcp.",          // 표준 PTP/IP
            "_nikon._tcp.",        // 일부 Nikon
            "_dpsoffer._tcp.",     // 일부 Canon
        )

        /** 한 서비스 타입당 검색 시간 (ms). */
        private const val PER_TYPE_TIMEOUT_MS = 2500L
    }

    private val nsdManager: NsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * mDNS 검색 1회 수행. 발견된 모든 카메라 후보를 IP 중복 제거 후 반환.
     */
    suspend fun discover(): List<CameraEndpoint> {
        val results = mutableMapOf<String, CameraEndpoint>()
        for (type in SERVICE_TYPES) {
            val found = discoverForType(type)
            for (endpoint in found) {
                results.putIfAbsent(endpoint.ipAddress, endpoint)
            }
        }
        return results.values.toList()
    }

    private suspend fun discoverForType(serviceType: String): List<CameraEndpoint> {
        val collected = mutableListOf<CameraEndpoint>()
        val pendingNames = mutableSetOf<String>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String?, errorCode: Int) {
                Log.w(TAG, "discovery 시작 실패 type=$type code=$errorCode")
            }

            override fun onStopDiscoveryFailed(type: String?, errorCode: Int) {
                Log.w(TAG, "discovery 중지 실패 type=$type code=$errorCode")
            }

            override fun onDiscoveryStarted(type: String?) {
                Log.d(TAG, "discovery 시작: $type")
            }

            override fun onDiscoveryStopped(type: String?) {
                Log.d(TAG, "discovery 중지: $type")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "서비스 발견: name=${service.serviceName} type=${service.serviceType}")
                pendingNames.add(service.serviceName)
                resolve(service) { endpoint ->
                    if (endpoint != null) collected.add(endpoint)
                    pendingNames.remove(service.serviceName)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "서비스 소실: ${service.serviceName}")
            }
        }

        return try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            withTimeoutOrNull(PER_TYPE_TIMEOUT_MS) {
                while (pendingNames.isNotEmpty()) {
                    delay(100)
                }
            }
            collected.toList()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "discovery 중 예외 type=$serviceType", e)
            emptyList()
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }

    private fun resolve(service: NsdServiceInfo, onDone: (CameraEndpoint?) -> Unit) {
        val callback = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.w(TAG, "resolve 실패 name=${serviceInfo?.serviceName} code=$errorCode")
                onDone(null)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host
                val ipv4 = host as? Inet4Address
                val ip = ipv4?.hostAddress
                if (ip == null) {
                    onDone(null)
                    return
                }
                onDone(
                    CameraEndpoint(
                        ipAddress = ip,
                        port = serviceInfo.port.takeIf { it > 0 } ?: 15740,
                        name = serviceInfo.serviceName.ifBlank { "PTP/IP Camera" },
                        source = EndpointSource.MDNS,
                    )
                )
            }
        }
        runCatching { nsdManager.resolveService(service, callback) }
            .onFailure {
                Log.w(TAG, "resolveService 호출 실패", it)
                onDone(null)
            }
    }
}

@Suppress("unused")
private inline fun <T> suspendUnused(crossinline block: (T) -> Unit) {
    // 자리표시자 — 향후 동시 resolveService(Api 34+) 마이그레이션을 위한 훅.
}
