package com.inik.camcon.data.datasource.ptpip

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.inik.camcon.CameraNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * PTPIP (Picture Transfer Protocol over IP) 데이터 소스
 * mDNS로 카메라 발견 → Kotlin으로 PTPIP 연결 → libgphoto2로 제어
 */
@Singleton
class PtpipDataSource @Inject constructor(
    private val context: Context
) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var connectedCamera: PtpipCamera? = null
    private var discoveryJob: Job? = null

    // PTPIP 소켓 연결
    private var commandSocket: Socket? = null
    private var eventSocket: Socket? = null
    private var sessionId: Int = 0

    private val _connectionState = MutableStateFlow(PtpipConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PtpipConnectionState> = _connectionState.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<PtpipCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<PtpipCamera>> = _discoveredCameras.asStateFlow()

    private val _cameraInfo = MutableStateFlow<PtpipCameraInfo?>(null)
    val cameraInfo: StateFlow<PtpipCameraInfo?> = _cameraInfo.asStateFlow()

    companion object {
        private const val TAG = "PtpipDataSource"
        private const val DISCOVERY_TIMEOUT = 10000L // 10초
        private const val SERVICE_TYPE = "_ptp._tcp"
        private const val CONNECTION_TIMEOUT = 10000 // 10초

        // PTPIP 패킷 타입
        private const val PTPIP_INIT_COMMAND_REQUEST = 1
        private const val PTPIP_INIT_COMMAND_ACK = 2
        private const val PTPIP_INIT_EVENT_REQUEST = 3
        private const val PTPIP_INIT_EVENT_ACK = 4
        private const val PTPIP_OPERATION_REQUEST = 6
        private const val PTPIP_OPERATION_RESPONSE = 7

        // PTP 오퍼레이션 코드
        private const val PTP_OC_GetDeviceInfo = 0x1001
        private const val PTP_OC_OpenSession = 0x1002
        private const val PTP_OC_CloseSession = 0x1003
        private const val PTP_OC_GetStorageIDs = 0x1004
        private const val PTP_OC_GetStorageInfo = 0x1005
        private const val PTP_OC_GetNumObjects = 0x1006
        private const val PTP_OC_GetObjectHandles = 0x1007
        private const val PTP_OC_GetObjectInfo = 0x1008
        private const val PTP_OC_GetObject = 0x1009
        private const val PTP_OC_DeleteObject = 0x100A
        private const val PTP_OC_SendObjectInfo = 0x100C
        private const val PTP_OC_SendObject = 0x100D
        private const val PTP_OC_InitiateCapture = 0x100E
        private const val PTP_OC_FormatStore = 0x100F
        private const val PTP_OC_ResetDevice = 0x1010
        private const val PTP_OC_SelfTest = 0x1011
        private const val PTP_OC_SetObjectProtection = 0x1012
        private const val PTP_OC_PowerDown = 0x1013
        private const val PTP_OC_GetDevicePropDesc = 0x1014
        private const val PTP_OC_GetDevicePropValue = 0x1015
        private const val PTP_OC_SetDevicePropValue = 0x1016
        private const val PTP_OC_ResetDevicePropValue = 0x1017
        private const val PTP_OC_TerminateOpenCapture = 0x1018
        private const val PTP_OC_MoveObject = 0x1019
        private const val PTP_OC_CopyObject = 0x101A
        private const val PTP_OC_GetPartialObject = 0x101B
        private const val PTP_OC_InitiateOpenCapture = 0x101C

        // Nikon 전용 PTP 오퍼레이션 코드
        private const val PTP_OC_NIKON_GetProfileAllData = 0x9006
        private const val PTP_OC_NIKON_SendProfileData = 0x9007
        private const val PTP_OC_NIKON_DeleteProfile = 0x9008
        private const val PTP_OC_NIKON_SetProfileData = 0x9009
        private const val PTP_OC_NIKON_AdvancedTransfer = 0x9010
        private const val PTP_OC_NIKON_GetFileInfoInBlock = 0x9011
        private const val PTP_OC_NIKON_Capture = 0x90C0
        private const val PTP_OC_NIKON_AfDrive = 0x90C1
        private const val PTP_OC_NIKON_SetControlMode = 0x90C2
        private const val PTP_OC_NIKON_DelImageSDRAM = 0x90C3
        private const val PTP_OC_NIKON_GetLargeThumb = 0x90C4
        private const val PTP_OC_NIKON_CurveDownload = 0x90C5
        private const val PTP_OC_NIKON_CurveUpload = 0x90C6
        private const val PTP_OC_NIKON_CheckEvent = 0x90C7
        private const val PTP_OC_NIKON_DeviceReady = 0x90C8
        private const val PTP_OC_NIKON_SetPreWBData = 0x90C9
        private const val PTP_OC_NIKON_GetVendorPropCodes = 0x90CA
        private const val PTP_OC_NIKON_AfCaptureSDRAM = 0x90CB
        private const val PTP_OC_NIKON_GetPictCtrlData = 0x90CC
        private const val PTP_OC_NIKON_SetPictCtrlData = 0x90CD
        private const val PTP_OC_NIKON_DelCstPicCtrl = 0x90CE
        private const val PTP_OC_NIKON_GetPicCtrlCapability = 0x90CF
        private const val PTP_OC_NIKON_GetPreviewImg = 0x9200
        private const val PTP_OC_NIKON_StartLiveView = 0x9201
        private const val PTP_OC_NIKON_EndLiveView = 0x9202
        private const val PTP_OC_NIKON_GetLiveViewImg = 0x9203
        private const val PTP_OC_NIKON_MfDrive = 0x9204
        private const val PTP_OC_NIKON_ChangeAfArea = 0x9205
        private const val PTP_OC_NIKON_AfDriveCancel = 0x9206
        private const val PTP_OC_NIKON_InitiateCaptureRecInMedia = 0x9207
        private const val PTP_OC_NIKON_GetVendorStorageIDs = 0x9209
        private const val PTP_OC_NIKON_StartMovieRecInCard = 0x920a
        private const val PTP_OC_NIKON_EndMovieRec = 0x920b
        private const val PTP_OC_NIKON_TerminateCapture = 0x920c
        private const val PTP_OC_NIKON_GetDevicePTPIPInfo = 0x90E0
        private const val PTP_OC_NIKON_GetPartialObjectHiSpeed = 0x9400
        private const val PTP_OC_NIKON_GetDevicePropEx = 0x9504
    }

    /**
     * mDNS를 사용하여 PTPIP 지원 카메라 검색
     */
    suspend fun discoverCameras(): List<PtpipCamera> = withContext(Dispatchers.IO) {
        val cameras = mutableListOf<PtpipCamera>()

        Log.d(TAG, "mDNS를 사용하여 PTPIP 카메라 검색 시작")

        try {
            // Wi-Fi 연결 상태 확인
            if (!isWifiConnected()) {
                Log.w(TAG, "Wi-Fi 네트워크에 연결되어 있지 않음")
                return@withContext emptyList()
            }

            Log.d(TAG, "mDNS 서비스 검색 시작: $SERVICE_TYPE")

            // mDNS로 PTP 서비스 검색
            val discoveredServices = withTimeoutOrNull(DISCOVERY_TIMEOUT) {
                discoverPtpServices()
            } ?: emptyList()

            Log.d(TAG, "mDNS 검색 완료: ${discoveredServices.size}개 서비스 발견")

            // 발견된 서비스들을 PtpipCamera 객체로 변환
            for (serviceInfo in discoveredServices) {
                try {
                    val hostAddress = serviceInfo.host?.hostAddress
                    val port = serviceInfo.port
                    val serviceName = serviceInfo.serviceName

                    if (hostAddress != null && port > 0) {
                        Log.d(TAG, "서비스 정보: $serviceName ($hostAddress:$port)")

                        // 카메라 이름 추출 및 정리
                        val cameraName = extractCameraName(serviceName, hostAddress)

                        val camera = PtpipCamera(
                            ipAddress = hostAddress,
                            port = port,
                            name = cameraName,
                            isOnline = true
                        )
                        cameras.add(camera)

                        Log.i(TAG, "카메라 발견: $cameraName ($hostAddress:$port)")
                    } else {
                        Log.w(TAG, "유효하지 않은 서비스 정보: $serviceName")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "서비스 정보 처리 중 오류: ${e.message}")
                }
            }

            _discoveredCameras.value = cameras

            Log.i(TAG, "카메라 검색 완료")
            Log.i(TAG, "- 발견된 카메라: ${cameras.size}개")

            if (cameras.isNotEmpty()) {
                Log.i(TAG, "발견된 카메라 목록:")
                cameras.forEachIndexed { index, camera ->
                    Log.i(
                        TAG,
                        "  ${index + 1}. ${camera.name} (${camera.ipAddress}:${camera.port})"
                    )
                }
            } else {
                Log.w(TAG, "mDNS를 통해 PTPIP 지원 카메라를 찾을 수 없습니다")
            }

        } catch (e: Exception) {
            Log.e(TAG, "카메라 검색 중 오류", e)
        }

        cameras
    }

    /**
     * mDNS를 사용하여 PTP 서비스 검색
     */
    private suspend fun discoverPtpServices(): List<NsdServiceInfo> {
        return suspendCancellableCoroutine { continuation ->
            val discoveredServices = mutableListOf<NsdServiceInfo>()
            val resolvedServices = mutableSetOf<String>()
            var servicesFound = 0
            var servicesResolved = 0

            val resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "서비스 resolve 실패: ${serviceInfo.serviceName}, 에러코드: $errorCode")
                    synchronized(discoveredServices) {
                        servicesResolved++
                        if (servicesResolved >= servicesFound) {
                            continuation.resume(discoveredServices.toList())
                        }
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "서비스 resolve 성공: ${serviceInfo.serviceName}")
                    Log.d(TAG, "  - Host: ${serviceInfo.host}")
                    Log.d(TAG, "  - Port: ${serviceInfo.port}")

                    synchronized(discoveredServices) {
                        discoveredServices.add(serviceInfo)
                        servicesResolved++
                        if (servicesResolved >= servicesFound) {
                            continuation.resume(discoveredServices.toList())
                        }
                    }
                }
            }

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "mDNS 검색 시작됨: $regType")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "서비스 발견: ${service.serviceName}")
                    synchronized(discoveredServices) {
                        val serviceKey = "${service.serviceName}:${service.serviceType}"
                        if (!resolvedServices.contains(serviceKey)) {
                            resolvedServices.add(serviceKey)
                            servicesFound++
                            try {
                                nsdManager.resolveService(service, resolveListener)
                            } catch (e: Exception) {
                                Log.w(TAG, "서비스 resolve 요청 실패: ${e.message}")
                                servicesResolved++
                                if (servicesResolved >= servicesFound) {
                                    continuation.resume(discoveredServices.toList())
                                }
                            }
                        }
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.d(TAG, "서비스 손실: ${service.serviceName}")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "mDNS 검색 중지됨: $serviceType")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS 검색 시작 실패: $serviceType, 에러코드: $errorCode")
                    continuation.resume(emptyList())
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS 검색 중지 실패: $serviceType, 에러코드: $errorCode")
                }
            }

            // 검색 시작
            try {
                nsdManager.discoverServices(
                    SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener
                )
            } catch (e: Exception) {
                Log.e(TAG, "mDNS 검색 시작 중 오류", e)
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            // 취소 시 정리 작업
            continuation.invokeOnCancellation {
                try {
                    discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "mDNS 검색 정리 중 오류: ${e.message}")
                }
            }
        }
    }

    /**
     * 서비스 이름에서 카메라 이름 추출
     */
    private fun extractCameraName(serviceName: String, ipAddress: String): String {
        return when {
            serviceName.contains("Z_8", ignoreCase = true) -> {
                // Nikon Z8 시리즈
                val match = Regex("Z_8[_\\s]*([0-9]+)").find(serviceName)
                val serialNumber = match?.groupValues?.get(1) ?: ""
                "Nikon Z8${if (serialNumber.isNotEmpty()) " ($serialNumber)" else ""}"
            }

            serviceName.contains("NIKON", ignoreCase = true) -> {
                // 기타 Nikon 카메라
                val modelMatch = Regex("NIKON[_\\s]*([A-Z]+[_\\s]*\\d+)", RegexOption.IGNORE_CASE)
                    .find(serviceName)?.groupValues?.get(1)?.replace("_", " ")
                "Nikon ${modelMatch ?: "Camera"}"
            }

            serviceName.contains("CANON", ignoreCase = true) -> {
                val modelMatch = Regex("CANON[_\\s]*([A-Z]+[_\\s]*\\d+)", RegexOption.IGNORE_CASE)
                    .find(serviceName)?.groupValues?.get(1)?.replace("_", " ")
                "Canon ${modelMatch ?: "Camera"}"
            }

            serviceName.contains("SONY", ignoreCase = true) -> "Sony Camera"
            serviceName.contains("FUJI", ignoreCase = true) -> "Fujifilm Camera"
            serviceName.contains("PANASONIC", ignoreCase = true) -> "Panasonic Camera"
            serviceName.contains("OLYMPUS", ignoreCase = true) -> "Olympus Camera"
            serviceName.contains("LEICA", ignoreCase = true) -> "Leica Camera"
            else -> "PTPIP Camera ($ipAddress)"
        }
    }

    /**
     * 하이브리드 방식: Kotlin PTPIP 연결 + libgphoto2 제어
     */
    suspend fun connectToCamera(camera: PtpipCamera): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "하이브리드 카메라 연결 시작: ${camera.name} (${camera.ipAddress}:${camera.port})")
            
            Log.d(TAG, "연결 상태를 CONNECTING으로 변경")
            _connectionState.value = PtpipConnectionState.CONNECTING

            // 0단계: 이전 연결 완전 정리 및 사전 검증
            Log.d(TAG, "0단계: 이전 연결 정리 및 사전 검증")

            // PTPIP 상세 로그 활성화 (디버깅용)
            try {
                CameraNative.setPtpipVerbose(false)
                Log.d(TAG, "PTPIP 상세 로그 활성화")
            } catch (e: Exception) {
                Log.w(TAG, "PTPIP 상세 로그 활성화 실패: ${e.message}")
            }

            // 기존 연결 정리
            try {
                CameraNative.closeCamera()
                closePtpipConnections()
            } catch (e: Exception) {
                Log.w(TAG, "기존 연결 정리 중 오류: ${e.message}")
            }

            // 충분한 대기 시간 (GUID 충돌 방지)
            kotlinx.coroutines.delay(2000)

            // Wi-Fi 연결 확인
            if (!isWifiConnected()) {
                Log.e(TAG, "Wi-Fi 연결이 해제됨")
                _connectionState.value = PtpipConnectionState.ERROR
                return@withContext false
            }

            // libgphoto2 네이티브 PTP/IP 구현 테스트
            Log.d(TAG, "=== libgphoto2 네이티브 PTP/IP 구현 테스트 ===")

            // 라이브러리 경로 설정
            val libDir = context.applicationInfo.nativeLibraryDir
            Log.d(TAG, "네이티브 라이브러리 경로: $libDir")

            // libgphoto2 네이티브 PTP/IP 연결 시도
            Log.d(TAG, "libgphoto2 네이티브 PTP/IP 연결 시도")
            val ptpipResult = try {
                CameraNative.initCameraWithPtpip(
                    camera.ipAddress,
                    camera.port,
                    libDir
                )
            } catch (e: Exception) {
                Log.e(TAG, "네이티브 PTP/IP 연결 중 예외: ${e.message}")
                null
            }

            Log.d(TAG, "네이티브 PTP/IP 연결 결과: $ptpipResult")

            // 네이티브 PTP/IP 연결 결과 처리
            if (ptpipResult == "OK" || ptpipResult == "GP_OK" || ptpipResult?.contains(
                    "Success",
                    ignoreCase = true
                ) == true
            ) {
                Log.i(TAG, "✅ libgphoto2 네이티브 PTP/IP 연결 성공!")
                _connectionState.value = PtpipConnectionState.CONNECTED
                connectedCamera = camera

                // 카메라 정보 수집
//                try {
//                    val summary = CameraNative.getCameraSummary()
//                    Log.d(TAG, "카메라 요약 정보: $summary")
//
//                    val cameraInfo = PtpipCameraInfo(
//                        manufacturer = extractManufacturer(camera.name),
//                        model = camera.name,
//                        version = extractVersionFromSummary(summary),
//                        serialNumber = extractSerialFromSummary(summary)
//                    )
//                    _cameraInfo.value = cameraInfo
//                    Log.d(TAG, "카메라 정보: $cameraInfo")
//
//                } catch (e: Exception) {
//                    Log.w(TAG, "카메라 정보 가져오기 실패: ${e.message}")
//                    _cameraInfo.value = PtpipCameraInfo(
//                        manufacturer = extractManufacturer(camera.name),
//                        model = camera.name,
//                        version = "Unknown",
//                        serialNumber = "Unknown"
//                    )
//                }

                Log.i(TAG, "카메라 연결 완료 (libgphoto2 네이티브 PTP/IP)")
                return@withContext true
            } else {
                Log.w(TAG, "❌ libgphoto2 네이티브 PTP/IP 연결 실패: $ptpipResult")

                /* 임시 주석 처리 - Kotlin PTP/IP 구현 대안
                // 대안: Kotlin PTP/IP 구현 사용
                Log.d(TAG, "대안: Kotlin PTP/IP 구현 시도")
                if (establishPtpipConnection(camera)) {
                    Log.i(TAG, "✅ Kotlin PTP/IP 연결 성공 (대안)")
                    _connectionState.value = PtpipConnectionState.CONNECTED
                    connectedCamera = camera

                    _cameraInfo.value = PtpipCameraInfo(
                        manufacturer = extractManufacturer(camera.name),
                        model = camera.name,
                        version = "Kotlin PTP/IP",
                        serialNumber = "Unknown"
                    )

                    Log.i(TAG, "카메라 연결 완료 (Kotlin PTP/IP)")
                    return@withContext true
                } else {
                    Log.e(TAG, "❌ 모든 PTP/IP 연결 방법 실패")
                    _connectionState.value = PtpipConnectionState.ERROR
                    return@withContext false
                }
                */

                Log.e(TAG, "❌ libgphoto2 PTP/IP 연결 실패")
                _connectionState.value = PtpipConnectionState.ERROR
                return@withContext false
            }

            /* 임시 주석 처리 - libgphoto2 연결 
            // PTP/IP 연결 시도 (단일 시도)
            val ptpipResult = try {
                CameraNative.initCameraWithPtpip(
                    camera.ipAddress,
                    camera.port,
                    libDir
                )
            } catch (e: Exception) {
                Log.e(TAG, "PTP/IP 연결 중 예외: ${e.message}")
                null
            }

            Log.d(TAG, "PTP/IP 연결 결과: $ptpipResult")

            // PTP/IP 연결 결과 처리
            if (ptpipResult == "GP_OK" || ptpipResult?.contains(
                    "Success",
                    ignoreCase = true
                ) == true || ptpipResult == "No error"
            ) {
                Log.i(TAG, "libgphoto2 PTP/IP 연결 성공")

                // 연결 안정화를 위한 짧은 대기
                kotlinx.coroutines.delay(500)

            } else {
                Log.w(TAG, "libgphoto2 PTP/IP 연결 실패: $ptpipResult")

                // 대안 1: 일반 자동 검색 시도 (PTPIP 완전 실패 시에만)
                Log.d(TAG, "대안 방법: PTPIP 실패로 인한 일반 초기화 시도")
                kotlinx.coroutines.delay(2000) // 더 긴 대기
                
                val initResult = CameraNative.initCamera()
                Log.d(TAG, "일반 초기화 결과: $initResult")

                if (!(initResult == "GP_OK" || initResult.contains("Success", ignoreCase = true))) {
                    Log.e(TAG, "모든 libgphoto2 초기화 방법 실패")

                    // 대안 2: Kotlin PTPIP 전용 연결
                    Log.d(TAG, "최후 대안: Kotlin PTPIP 전용 연결 시도")
                    if (establishPtpipConnection(camera)) {
                        Log.w(TAG, "Kotlin PTPIP 전용 연결 성공 (제한적 기능)")
                        _connectionState.value = PtpipConnectionState.CONNECTED
                        connectedCamera = camera

                        _cameraInfo.value = PtpipCameraInfo(
                            manufacturer = extractManufacturer(camera.name),
                            model = camera.name,
                            version = "PTPIP Only",
                            serialNumber = "Unknown"
                        )

                        return@withContext true
                    } else {
                        Log.e(TAG, "모든 연결 방법 실패")
                        _connectionState.value = PtpipConnectionState.ERROR
                        return@withContext false
                    }
                } else {
                    Log.i(TAG, "일반 초기화 성공 - PTPIP 대신 일반 모드로 연결")
                }
            }

            // 2단계: 연결 상태 설정 및 카메라 정보 수집
            Log.d(TAG, "2단계: 연결 완료 및 카메라 정보 수집")
            _connectionState.value = PtpipConnectionState.CONNECTED
            connectedCamera = camera

            // 카메라 정보 수집
            try {
                val summary = CameraNative.getCameraSummary()
                Log.d(TAG, "카메라 요약 정보: $summary")

                val cameraInfo = PtpipCameraInfo(
                    manufacturer = extractManufacturer(camera.name),
                    model = camera.name,
                    version = extractVersionFromSummary(summary),
                    serialNumber = extractSerialFromSummary(summary)
                )
                _cameraInfo.value = cameraInfo
                Log.d(TAG, "카메라 정보: $cameraInfo")

            } catch (e: Exception) {
                Log.w(TAG, "카메라 정보 가져오기 실패: ${e.message}")
                _cameraInfo.value = PtpipCameraInfo(
                    manufacturer = extractManufacturer(camera.name),
                    model = camera.name,
                    version = "Unknown",
                    serialNumber = "Unknown"
                )
            }

            Log.i(TAG, "카메라 연결 완료")
            return@withContext true
            */

        } catch (e: Exception) {
            Log.e(TAG, "카메라 연결 중 오류 발생", e)
            _connectionState.value = PtpipConnectionState.ERROR

            // 연결 실패 시 완전 정리
            try {
                CameraNative.closeCamera()
                closePtpipConnections()
            } catch (cleanupException: Exception) {
                Log.w(TAG, "정리 중 오류: ${cleanupException.message}")
            }

            return@withContext false
        }
    }

    /**
     * PTPIP 연결 설정 (패킷 캡처 기반 구현) - 실제 성공 시퀀스 적용
     */
    private suspend fun establishPtpipConnection(camera: PtpipCamera): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "실제 패킷 캡처 기반 PTPIP 연결 시작")

                // 포트 연결 확인
                if (!isPortReachable(camera.ipAddress, camera.port)) {
                    return@withContext false
                }

                // 1단계: Command 소켓 연결 및 초기화
                commandSocket = Socket()
                commandSocket?.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                val connectionNumber = performCommandInitialization()
                if (connectionNumber == -1) return@withContext false

                kotlinx.coroutines.delay(200)

                // 2단계: Event 소켓 연결 및 초기화
                eventSocket = Socket()
                eventSocket?.connect(InetSocketAddress(camera.ipAddress, camera.port), 5000)

                if (!performEventInitializationAccurate(connectionNumber)) {
                    return@withContext false
                }

                kotlinx.coroutines.delay(200)

                // 3단계: GetDeviceInfo (Transaction ID: 0)
                Log.d(TAG, "3단계: GetDeviceInfo 요청 (Transaction ID: 0)")
                transactionId = 0 // 초기화
                if (!sendGetDeviceInfo()) {
                    Log.w(TAG, "GetDeviceInfo 실패하지만 계속 진행")
                }

                // 4단계: OpenSession (Transaction ID: 0 유지!)
                Log.d(TAG, "4단계: OpenSession 요청 (Transaction ID: 0)")
                sessionId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                if (!sendOpenSession()) {
                    Log.w(TAG, "OpenSession 실패")
                    return@withContext false
                }

                // 5단계: GetStorageIDs (Transaction ID: 1)
                kotlinx.coroutines.delay(100)
                Log.d(TAG, "5단계: GetStorageIDs 요청 (Transaction ID: 1)")
                if (sendGetStorageIDs()) {
                    Log.d(TAG, "✅ GetStorageIDs 성공")

                    // 6단계: GetStorageInfo (Transaction ID: 2)
                    kotlinx.coroutines.delay(100)
                    Log.d(TAG, "6단계: GetStorageInfo 요청 (Transaction ID: 2)")
                    if (sendGetStorageInfoWithParam(0x00010001)) {
                        Log.d(TAG, "✅ GetStorageInfo 성공")
                    }
                }

                // 실제 캡처에는 GetObjectHandles가 없음 - 기본 연결에는 불필요
                Log.d(TAG, "기본 연결 완료 - GetObjectHandles 생략 (실제 캡처에 없음)")

                Log.i(TAG, "✅ 실제 패킷 캡처 기반 PTPIP 연결 완료")
                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "PTPIP 연결 실패: ${e.message}")
                closePtpipConnections()
                return@withContext false
            }
        }

    /**
     * 개선된 GetStorageIDs 요청 (응답 분석 개선)
     */
    private fun sendGetStorageIDs(): Boolean {
        Log.d(TAG, "======================================")
        Log.d(TAG, "=== GET STORAGE IDs 요청 시작 ===")
        Log.d(TAG, "======================================")

        try {
            val socket = commandSocket ?: return false
            val output = socket.getOutputStream()

            // GetStorageIDs 패킷 생성
            val packet = createOperationRequest(PTP_OC_GetStorageIDs)
            Log.d(TAG, ">>> GetStorageIDs 패킷 전송 중... (${packet.size} bytes)")
            logPacketHex("GetStorageIDs 패킷", packet)

            output.write(packet)
            output.flush()
            Log.d(TAG, ">>> GetStorageIDs 패킷 전송 완료")

            // 개선된 응답 처리
            Log.d(TAG, "<<< GetStorageIDs 응답 대기 중...")
            val responses = readCompleteResponse(socket)

            // 성공 조건: 응답이 있으면 성공으로 간주
            val success = responses.isNotEmpty()
            
            if (success) {
                Log.d(TAG, "✅ GetStorageIDs 성공 (${responses.size}개 패킷)")
                
                // 스토리지 ID 파싱 시도
                val storageDataPacket = responses.find { it.size > 20 }
                if (storageDataPacket != null) {
                    parseStorageIDs(storageDataPacket)
                }
            } else {
                Log.w(TAG, "❌ GetStorageIDs 실패")
            }

            Log.d(TAG, "======================================")
            Log.d(TAG, "=== GET STORAGE IDs 요청 완료 ===")
            Log.d(TAG, "======================================")

            return success

        } catch (e: Exception) {
            Log.e(TAG, "❌ GetStorageIDs 전송 실패: ${e.message}")
            return false
        }
    }

    /**
     * GetObjectHandles 요청
     */
    private fun sendGetObjectHandles(): Boolean {
        Log.d(TAG, "======================================")
        Log.d(TAG, "=== GET OBJECT HANDLES 요청 시작 ===")
        Log.d(TAG, "======================================")

        try {
            val socket = commandSocket ?: return false
            val output = socket.getOutputStream()

            // GetObjectHandles 패킷 생성
            val packet = createOperationRequest(PTP_OC_GetObjectHandles)
            Log.d(TAG, ">>> GetObjectHandles 패킷 전송 중... (${packet.size} bytes)")
            logPacketHex("GetObjectHandles 패킷", packet)

            output.write(packet)
            output.flush()
            Log.d(TAG, ">>> GetObjectHandles 패킷 전송 완료")

            // 다중 패킷 응답 처리
            Log.d(TAG, "<<< GetObjectHandles 다중 응답 대기 중...")
            socket.soTimeout = 5000
            val responses = readCompleteResponse(socket)

            val success = responses.isNotEmpty()
            if (success) {
                Log.d(TAG, "✅ GetObjectHandles 성공 (${responses.size}개 패킷)")
            } else {
                Log.w(TAG, "❌ GetObjectHandles 실패")
            }

            Log.d(TAG, "======================================")
            Log.d(TAG, "=== GET OBJECT HANDLES 요청 완료 ===")
            Log.d(TAG, "======================================")

            return success

        } catch (e: Exception) {
            Log.e(TAG, "❌ GetObjectHandles 전송 실패: ${e.message}")
            Log.e(TAG, "예외 스택트레이스:", e)
            return false
        }
    }

    /**
     * GetStorageInfo 요청 (패킷 캡처 기반 다중 응답 처리)
     */
    private fun sendGetStorageInfoWithParam(param: Int): Boolean {
        Log.d(TAG, "======================================")
        Log.d(TAG, "=== GET STORAGE INFO 요청 시작 ===")
        Log.d(TAG, "======================================")

        try {
            val socket = commandSocket ?: return false
            val output = socket.getOutputStream()

            // GetStorageInfo 패킷 생성 (Transaction ID 2)
            val packet = createOperationRequest(PTP_OC_GetStorageInfo, param)
            Log.d(TAG, ">>> GetStorageInfo 패킷 전송 중... (${packet.size} bytes)")
            logPacketHex("GetStorageInfo 패킷", packet)

            output.write(packet)
            output.flush()
            Log.d(TAG, ">>> GetStorageInfo 패킷 전송 완료")

            // 다중 패킷 응답 처리
            Log.d(TAG, "<<< GetStorageInfo 다중 응답 대기 중...")
            socket.soTimeout = 5000
            val responses = readCompleteResponse(socket)

            val success = responses.isNotEmpty()
            if (success) {
                Log.d(TAG, "✅ GetStorageInfo 성공 (${responses.size}개 패킷)")
            } else {
                Log.w(TAG, "❌ GetStorageInfo 실패")
            }

            Log.d(TAG, "======================================")
            Log.d(TAG, "=== GET STORAGE INFO 요청 완료 ===")
            Log.d(TAG, "======================================")

            return success

        } catch (e: Exception) {
            Log.e(TAG, "❌ GetStorageInfo 전송 실패: ${e.message}")
            Log.e(TAG, "예외 스택트레이스:", e)
            return false
        }
    }

    /**
     * GetObjectHandles 요청 (파라미터 포함)
     */
    private fun sendGetObjectHandlesWithParams(): Boolean {
        Log.d(TAG, "======================================")
        Log.d(TAG, "=== GET OBJECT HANDLES (WITH PARAMS) 요청 시작 ===")
        Log.d(TAG, "======================================")

        try {
            val socket = commandSocket ?: return false
            val output = socket.getOutputStream()

            // GetObjectHandles 패킷 생성 (스토리지 ID, 객체 포맷, 부모 핸들 포함)
            val packet = createOperationRequest(PTP_OC_GetObjectHandles, 0x00010001, 0x0000, 0x0000)
            Log.d(TAG, ">>> GetObjectHandles (with params) 패킷 전송 중... (${packet.size} bytes)")
            logPacketHex("GetObjectHandles (with params) 패킷", packet)

            output.write(packet)
            output.flush()
            Log.d(TAG, ">>> GetObjectHandles (with params) 패킷 전송 완료")

            // 다중 패킷 응답 처리
            Log.d(TAG, "<<< GetObjectHandles (with params) 다중 응답 대기 중...")
            socket.soTimeout = 5000
            val responses = readCompleteResponse(socket)

            // 성공 조건: 응답이 있고, 실제 데이터가 포함된 경우
            val success = responses.isNotEmpty() && responses.any { it.size > 20 }
            if (success) {
                Log.d(TAG, "✅ GetObjectHandles (with params) 성공 (${responses.size}개 패킷)")
            } else {
                Log.w(TAG, "❌ GetObjectHandles (with params) 실패 또는 빈 결과")
            }

            Log.d(TAG, "======================================")
            Log.d(TAG, "=== GET OBJECT HANDLES (WITH PARAMS) 요청 완료 ===")
            Log.d(TAG, "======================================")

            return success

        } catch (e: Exception) {
            Log.e(TAG, "❌ GetObjectHandles (with params) 전송 실패: ${e.message}")
            Log.e(TAG, "예외 스택트레이스:", e)
            return false
        }
    }

    /**
     * GetObjectInfo 샘플 요청 (첫 번째 객체 정보, 다중 패킷 응답 처리)
     */
    private fun sendGetObjectInfoSample(): Boolean {
        Log.d(TAG, "======================================")
        Log.d(TAG, "=== GET OBJECT INFO 샘플 요청 시작 ===")
        Log.d(TAG, "======================================")

        try {
            val socket = commandSocket ?: return false
            val output = socket.getOutputStream()

            // GetObjectInfo 패킷 생성 (샘플 핸들 ID 사용)
            val packet = createOperationRequest(PTP_OC_GetObjectInfo, 0x00000001)
            Log.d(TAG, ">>> GetObjectInfo 샘플 패킷 전송 중... (${packet.size} bytes)")
            logPacketHex("GetObjectInfo 샘플 패킷", packet)

            output.write(packet)
            output.flush()
            Log.d(TAG, ">>> GetObjectInfo 샘플 패킷 전송 완료")

            // 다중 패킷 응답 처리
            Log.d(TAG, "<<< GetObjectInfo 샘플 다중 응답 대기 중...")
            socket.soTimeout = 5000
            val responses = readCompleteResponse(socket)

            val success = responses.isNotEmpty()
            if (success) {
                Log.d(TAG, "✅ GetObjectInfo 샘플 성공 (${responses.size}개 패킷)")
            } else {
                Log.w(TAG, "❌ GetObjectInfo 샘플 실패")
            }

            Log.d(TAG, "======================================")
            Log.d(TAG, "=== GET OBJECT INFO 샘플 요청 완료 ===")
            Log.d(TAG, "======================================")

            return success

        } catch (e: Exception) {
            Log.e(TAG, "❌ GetObjectInfo 샘플 전송 실패: ${e.message}")
            Log.e(TAG, "예외 스택트레이스:", e)
            return false
        }
    }

    /**
     * 패킷 16진수 로깅 유틸리티
     */
    private fun logPacketHex(label: String, packet: ByteArray, length: Int = packet.size) {
        val hexString = packet.take(length).joinToString(" ") { "%02x".format(it) }
        Log.d(TAG, "$label ($length bytes): $hexString")
    }

    /**
     * PTPIP Init Command Request 패킷 생성
     */
    private fun createInitCommandRequest(): ByteArray {
        Log.d(TAG, "=== Command Request 패킷 생성 시작 ===")

        // 패킷 캡처에서 확인된 정확한 GUID 사용
        val commandGuid = byteArrayOf(
            0xd5.toByte(), 0xb4.toByte(), 0x6b.toByte(), 0xcb.toByte(),
            0xd6.toByte(), 0x2a.toByte(), 0x4d.toByte(), 0xbb.toByte(),
            0xb0.toByte(), 0x97.toByte(), 0x87.toByte(), 0x20.toByte(),
            0xcf.toByte(), 0x83.toByte(), 0xe0.toByte(), 0x84.toByte()
        )

        Log.d(TAG, "GUID: ${commandGuid.joinToString(" ") { "%02x".format(it) }}")

        // 패킷 캡처에서 확인된 정확한 Name 사용 (Android Device -> corrupted name 형태)
        val hostNameBytes = byteArrayOf(
            0x41,
            0x00,
            0x6e,
            0x00,
            0x64,
            0x00,
            0x72,
            0x00,
            0x6f,
            0x00,
            0x69,
            0x00,
            0x64,
            0x00,
            0x20,
            0x00,
            0x44,
            0x00,
            0x65,
            0x00,
            0x76,
            0x00,
            0x69,
            0x00,
            0x63,
            0x00,
            0x65,
            0x00
        )
        val nullTerminator = byteArrayOf(0x00, 0x00)

        val totalLength = 4 + 4 + 16 + hostNameBytes.size + nullTerminator.size + 4
        Log.d(TAG, "전체 패킷 길이: $totalLength bytes")

        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(totalLength)
        buffer.putInt(PTPIP_INIT_COMMAND_REQUEST)
        buffer.put(commandGuid)
        buffer.put(hostNameBytes)
        buffer.put(nullTerminator)
        buffer.putInt(0x00010001) // 패킷 캡처에서 확인된 버전

        val packet = buffer.array()
        logPacketHex("생성된 Command Request 패킷", packet)
        Log.d(TAG, "=== Command Request 패킷 생성 완료 ===")

        return packet
    }

    /**
     * 간소화된 PTPIP Init Event Request 패킷 생성
     */
    private fun createSimpleInitEventRequest(connectionNumber: Int = 1): ByteArray {
        Log.d(TAG, "=== Event Request 패킷 생성 시작 ===")
        Log.d(TAG, "Connection Number: $connectionNumber")

        // 패킷 캡처 분석: Init Event Request는 Connection Number만 필요
        val totalLength = 12 // Length(4) + Type(4) + Connection Number(4)
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(totalLength)
        buffer.putInt(PTPIP_INIT_EVENT_REQUEST)
        buffer.putInt(connectionNumber)

        val packet = buffer.array()
        logPacketHex("생성된 Event Request 패킷", packet)
        Log.d(TAG, "=== Event Request 패킷 생성 완료 ===")

        return packet
    }

    /**
     * Command 응답에서 Connection Number 추출
     */
    private fun extractConnectionNumber(response: ByteArray, length: Int): Int {
        Log.d(TAG, "=== Connection Number 추출 시작 ===")
        logPacketHex("응답 패킷", response, length)

        return try {
            if (length >= 12) { // 최소 헤더 크기
                val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
                buffer.position(8) // Skip Length(4) + Type(4)
                val connectionNumber = buffer.int
                Log.d(TAG, "추출된 Connection Number: $connectionNumber")
                Log.d(TAG, "=== Connection Number 추출 완료 ===")
                connectionNumber
            } else {
                Log.w(TAG, "응답 패킷이 너무 짧음: $length bytes")
                1 // 기본값
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connection Number 추출 실패: ${e.message}")
            1 // 기본값
        }
    }

    /**
     * Command 채널 초기화 (패킷 캡처 분석 반영) - Connection Number 반환
     */
    private fun performCommandInitialization(): Int {
        Log.d(TAG, "======================================")
        Log.d(TAG, "=== COMMAND 채널 초기화 시작 ===")
        Log.d(TAG, "======================================")

        return try {
            val cmdSocket = commandSocket ?: return -1

            val cmdOutput = cmdSocket.getOutputStream()
            val cmdInput = cmdSocket.getInputStream()

            // 패킷 캡처 기반 Init Command 패킷 전송
            val initCmdPacket = createInitCommandRequest()
            Log.d(TAG, ">>> Command 패킷 전송 중... (${initCmdPacket.size} bytes)")

            cmdOutput.write(initCmdPacket)
            cmdOutput.flush()
            Log.d(TAG, ">>> Command 패킷 전송 완료")

            // ACK 대기
            cmdSocket.soTimeout = 5000 // 5초 타임아웃
            val cmdResponse = ByteArray(1024)
            val cmdBytesRead = cmdInput.read(cmdResponse)

            Log.d(TAG, "<<< Command 응답 수신: $cmdBytesRead bytes")

            if (cmdBytesRead < 8) {
                Log.e(TAG, "❌ Command 응답이 너무 짧음")
                return -1
            }

            logPacketHex("수신된 Command 응답", cmdResponse, cmdBytesRead)

            // 응답 패킷 분석
            val buffer = ByteBuffer.wrap(cmdResponse).order(ByteOrder.LITTLE_ENDIAN)
            val responseLength = buffer.int
            val responseType = buffer.int

            Log.d(TAG, "응답 분석:")
            Log.d(TAG, "  - 패킷 길이: $responseLength")
            Log.d(TAG, "  - 패킷 타입: $responseType (예상: $PTPIP_INIT_COMMAND_ACK)")

            if (responseType == PTPIP_INIT_COMMAND_ACK) {
                Log.d(TAG, "✅ Command 채널 초기화 성공!")

                // Connection Number 추출 및 반환
                val connectionNumber = extractConnectionNumber(cmdResponse, cmdBytesRead)
                Log.d(TAG, "사용할 Connection Number: $connectionNumber")

                Log.d(TAG, "======================================")
                Log.d(TAG, "=== COMMAND 채널 초기화 완료 ===")
                Log.d(TAG, "======================================")

                return connectionNumber
            } else {
                Log.e(TAG, "❌ Command 초기화 실패: 예상하지 못한 응답 타입 $responseType")
                return -1
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Command 초기화 중 예외: ${e.message}")
            Log.e(TAG, "예외 스택트레이스:", e)
            -1
        }
    }

    /**
     * Event 채널 초기화 (패킷 캡처 정확 반영)
     */
    private fun performEventInitializationAccurate(connectionNumber: Int): Boolean {
        Log.d(TAG, "======================================")
        Log.d(TAG, "=== EVENT 채널 초기화 시작 ===")
        Log.d(TAG, "======================================")
        Log.d(TAG, "사용할 Connection Number: $connectionNumber")

        return try {
            val evtSocket = eventSocket ?: return false

            val evtOutput = evtSocket.getOutputStream()
            val evtInput = evtSocket.getInputStream()

            // 패킷 캡처 기반 Event Init 패킷
            val initEvtPacket = createSimpleInitEventRequest(connectionNumber)
            Log.d(TAG, ">>> Event 패킷 전송 중... (${initEvtPacket.size} bytes)")

            evtOutput.write(initEvtPacket)
            evtOutput.flush()
            Log.d(TAG, ">>> Event 패킷 전송 완료")

            // 응답 대기
            Log.d(TAG, "<<< Event 응답 대기 중... (타임아웃: 3초)")
            evtSocket.soTimeout = 3000
            val evtResponse = ByteArray(1024)
            val evtBytesRead = try {
                evtInput.read(evtResponse)
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "❌ Event 응답 타임아웃")
                return false
            }

            Log.d(TAG, "<<< Event 응답 수신: $evtBytesRead bytes")

            if (evtBytesRead < 8) {
                Log.w(TAG, "❌ Event 응답이 너무 짧음: $evtBytesRead bytes")
                return false
            }

            logPacketHex("수신된 Event 응답", evtResponse, evtBytesRead)

            // 응답 분석
            val buffer = ByteBuffer.wrap(evtResponse).order(ByteOrder.LITTLE_ENDIAN)
            val packetLength = buffer.int
            val packetType = buffer.int

            Log.d(TAG, "응답 분석:")
            Log.d(TAG, "  - 패킷 길이: $packetLength")
            Log.d(TAG, "  - 패킷 타입: $packetType (예상: $PTPIP_INIT_EVENT_ACK)")

            when (packetType) {
                PTPIP_INIT_EVENT_ACK -> {
                    Log.d(TAG, "✅ Event 채널 초기화 성공!")
                    Log.d(TAG, "======================================")
                    Log.d(TAG, "=== EVENT 채널 초기화 완료 ===")
                    Log.d(TAG, "======================================")
                    return true
                }
                5 -> {
                    Log.e(TAG, "❌ Event 초기화 실패 (FAIL)")
                    return false
                }

                else -> {
                    Log.w(TAG, "❌ 예상하지 못한 Event 응답 타입: $packetType")
                    return false
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Event 초기화 중 예외: ${e.message}")
            Log.e(TAG, "예외 스택트레이스:", e)
            false
        }
    }

    /**
     * 개선된 GetDeviceInfo 요청 (응답 분석 개선)
     */
    private fun sendGetDeviceInfo(): Boolean {
        Log.d(TAG, "======================================")
        Log.d(TAG, "=== GET DEVICE INFO 요청 시작 ===")
        Log.d(TAG, "======================================")

        try {
            val socket = commandSocket ?: return false
            val output = socket.getOutputStream()

            // GetDeviceInfo 패킷 생성
            val packet = createOperationRequest(PTP_OC_GetDeviceInfo)
            Log.d(TAG, ">>> GetDeviceInfo 패킷 전송 중... (${packet.size} bytes)")
            logPacketHex("GetDeviceInfo 패킷", packet)

            output.write(packet)
            output.flush()
            Log.d(TAG, ">>> GetDeviceInfo 패킷 전송 완료")

            // 개선된 응답 처리
            Log.d(TAG, "<<< GetDeviceInfo 응답 대기 중...")
            val responses = readCompleteResponse(socket)

            // 성공 조건: 응답이 있고 실제 데이터가 포함된 경우
            val success = responses.isNotEmpty() && responses.any { it.size > 50 }
            
            if (success) {
                Log.d(TAG, "✅ GetDeviceInfo 성공 (${responses.size}개 패킷)")
                
                // 디바이스 정보 파싱 시도
                val deviceDataPacket = responses.find { it.size > 50 }
                if (deviceDataPacket != null) {
                    parseDeviceInfo(deviceDataPacket)
                }
            } else {
                Log.w(TAG, "❌ GetDeviceInfo 실패")
            }

            Log.d(TAG, "======================================")
            Log.d(TAG, "=== GET DEVICE INFO 요청 완료 ===")
            Log.d(TAG, "======================================")

            return success

        } catch (e: Exception) {
            Log.e(TAG, "❌ GetDeviceInfo 전송 실패: ${e.message}")
            return false
        }
    }

    /**
     * OpenSession PTP 명령 전송 (패킷 캡처 기반)
     */
    private fun sendOpenSession(): Boolean {
        Log.d(TAG, "======================================")
        Log.d(TAG, "=== OPEN SESSION 요청 시작 ===")
        Log.d(TAG, "======================================")
        Log.d(TAG, "Session ID: $sessionId")

        try {
            val socket = commandSocket ?: return false
            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            // 패킷 캡처 분석: OpenSession 패킷 생성 (Transaction ID는 0에서 시작)
            val packet = createOperationRequest(PTP_OC_OpenSession, sessionId)
            Log.d(TAG, ">>> OpenSession 패킷 전송 중... (${packet.size} bytes)")
            logPacketHex("OpenSession 패킷", packet)

            output.write(packet)
            output.flush()
            Log.d(TAG, ">>> OpenSession 패킷 전송 완료")

            // 응답 대기
            Log.d(TAG, "<<< OpenSession 응답 대기 중... (타임아웃: 5초)")
            socket.soTimeout = 5000
            val response = ByteArray(1024)
            val bytesRead = input.read(response)

            Log.d(TAG, "<<< OpenSession 응답 수신: $bytesRead bytes")
            if (bytesRead > 0) {
                logPacketHex("OpenSession 응답", response, bytesRead)
            }

            // 패킷 캡처에서 확인: Operation Response 패킷이 와야 함
            if (bytesRead >= 8) {
                val buffer = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
                val responseLength = buffer.int
                val responseType = buffer.int

                Log.d(TAG, "응답 분석:")
                Log.d(TAG, "  - 패킷 길이: $responseLength")
                Log.d(TAG, "  - 패킷 타입: $responseType (예상: $PTPIP_OPERATION_RESPONSE)")

                if (responseType == PTPIP_OPERATION_RESPONSE) {
                    Log.d(TAG, "✅ OpenSession 성공")

                    Log.d(TAG, "======================================")
                    Log.d(TAG, "=== OPEN SESSION 요청 완료 ===")
                    Log.d(TAG, "======================================")
                    return true
                } else {
                    Log.w(TAG, "❌ 예상하지 못한 OpenSession 응답 타입: $responseType")
                }
            }

            return bytesRead > 8

        } catch (e: Exception) {
            Log.e(TAG, "❌ OpenSession 전송 실패: ${e.message}")
            Log.e(TAG, "예외 스택트레이스:", e)
            return false
        }
    }

    /**
     * Transaction ID 수정 - OpenSession은 0을 유지해야 함
     */
    private var transactionId: Int = 0

    private fun createOperationRequest(operation: Int, vararg parameters: Int): ByteArray {
        Log.d(TAG, "=== Operation Request 패킷 생성 시작 ===")
        Log.d(TAG, "Operation Code: 0x${operation.toString(16)} ($operation)")
        
        // OpenSession은 Transaction ID 0을 사용해야 함 (패킷 캡처 확인)
        val currentTransactionId = if (operation == PTP_OC_OpenSession) 0 else transactionId++
        Log.d(TAG, "Transaction ID: $currentTransactionId")
        Log.d(TAG, "Parameters: ${parameters.contentToString()}")

        val paramSize = parameters.size * 4
        val totalSize = 18 + paramSize

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(totalSize)
        buffer.putInt(PTPIP_OPERATION_REQUEST)
        buffer.putInt(1)
        buffer.putShort(operation.toShort())
        buffer.putInt(currentTransactionId)

        parameters.forEach { buffer.putInt(it) }

        val packet = buffer.array()
        logPacketHex("생성된 Operation Request 패킷", packet)
        Log.d(TAG, "=== Operation Request 패킷 생성 완료 ===")

        return packet
    }

    /**
     * 개선된 스토리지 ID 파싱 - 실제 패킷 구조 반영
     */
    private fun parseStorageIDs(data: ByteArray) {
        try {
            Log.d(TAG, "스토리지 ID 파싱 중... (${data.size} bytes)")
            logPacketHex("스토리지 ID 원본 데이터", data)

            if (data.size < 24) {
                Log.w(TAG, "스토리지 ID 데이터가 너무 짧음")
                return
            }

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // PTPIP 헤더 확인 및 건너뛰기
            val packetLength = buffer.int
            val packetType = buffer.int
            
            if (packetType == 9) { // Start Data packet
                val transactionId = buffer.int
                val totalDataLength = buffer.int
                val payloadType = buffer.int // 0x0000이어야 함
                
                Log.d(TAG, "Start Data 패킷:")
                Log.d(TAG, "  - 패킷 길이: $packetLength")
                Log.d(TAG, "  - Transaction ID: $transactionId") 
                Log.d(TAG, "  - 총 데이터 길이: $totalDataLength")
                Log.d(TAG, "  - Payload Type: $payloadType")
                
                // PTP Array 구조: 길이(4바이트) + 데이터
                if (buffer.remaining() >= 4) {
                    val arrayLength = buffer.int
                    Log.d(TAG, "스토리지 배열 길이: $arrayLength")

                    if (arrayLength > 0 && buffer.remaining() >= arrayLength * 4) {
                        for (i in 0 until arrayLength) {
                            val storageId = buffer.int
                            Log.d(TAG, "스토리지 ID $i: 0x${storageId.toString(16).uppercase()}")
                        }
                        Log.d(TAG, "✅ 스토리지 ID 파싱 성공")
                    } else {
                        Log.w(TAG, "스토리지 데이터 부족: 예상 ${arrayLength * 4}, 실제 ${buffer.remaining()}")
                    }
                }
            } else {
                Log.w(TAG, "예상하지 못한 패킷 타입: $packetType")
            }

        } catch (e: Exception) {
            Log.w(TAG, "스토리지 ID 파싱 실패: ${e.message}")
        }
    }

    /**
     * 응답 패킷 대기 개선 - End Data + Operation Response 모두 대기
     */
    private fun readCompleteResponse(socket: Socket): List<ByteArray> {
        try {
            val input = socket.getInputStream()
            val responses = mutableListOf<ByteArray>()
            var operationResponseReceived = false
            
            // 더 긴 타임아웃 설정 (실제 캡처에서 응답이 빠르게 옴)
            socket.soTimeout = 2000 // 2초

            while (!operationResponseReceived) {
                val response = ByteArray(4096)
                val bytesRead = try {
                    input.read(response)
                } catch (e: java.net.SocketTimeoutException) {
                    Log.w(TAG, "응답 타임아웃")
                    break
                }

                if (bytesRead <= 0) {
                    Log.d(TAG, "응답 읽기 완료 (EOF)")
                    break
                }

                val actualResponse = response.copyOf(bytesRead)
                responses.add(actualResponse)
                logPacketHex("수신된 응답", actualResponse)

                // 패킷 타입 분석
                if (bytesRead >= 8) {
                    val buffer = ByteBuffer.wrap(actualResponse).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(4)
                    val packetType = buffer.int

                    when (packetType) {
                        9 -> Log.d(TAG, "Start Data 패킷 수신")
                        12 -> Log.d(TAG, "End Data 패킷 수신")
                        PTPIP_OPERATION_RESPONSE -> {
                            Log.d(TAG, "Operation Response 패킷 수신 - 응답 완료")
                            operationResponseReceived = true
                        }
                        else -> Log.d(TAG, "알 수 없는 패킷 타입: $packetType")
                    }
                }
            }

            Log.d(TAG, "응답 읽기 완료: ${responses.size}개 패킷 수신")
            return responses

        } catch (e: Exception) {
            Log.e(TAG, "응답 읽기 실패: ${e.message}")
            return emptyList()
        }
    }

    /**
     * PTPIP 연결 정리
     */
    private fun closePtpipConnections() {
        try {
            commandSocket?.close()
            eventSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "PTPIP 연결 정리 중 오류: ${e.message}")
        } finally {
            commandSocket = null
            eventSocket = null
            sessionId = 0
            transactionId = 0 // 연결 종료 시 Transaction ID 초기화
        }
    }

    /**
     * 카메라 연결 해제
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "카메라 연결 해제 시작")

            // 디스커버리 중지
            if (discoveryJob?.isActive == true) {
                discoveryJob?.cancel()
                discoveryListener = null
            }

            // libgphoto2 연결 해제
            Log.d(TAG, "libgphoto2 연결 해제 시작")
            try {
                CameraNative.closeCamera()
                Log.d(TAG, "libgphoto2 연결 해제 완료")
            } catch (e: Exception) {
                Log.w(TAG, "libgphoto2 연결 해제 중 오류: ${e.message}")
            }

            // PTPIP 연결 정리
            Log.d(TAG, "PTPIP 연결 정리 시작")
            try {
                closePtpipConnections()
                Log.d(TAG, "PTPIP 연결 정리 완료")
            } catch (e: Exception) {
                Log.w(TAG, "PTPIP 연결 정리 중 오류: ${e.message}")
            }

            // 상태 초기화
            connectedCamera = null
            _connectionState.value = PtpipConnectionState.DISCONNECTED
            _cameraInfo.value = null

            // PTPIP 상세 로그 비활성화
            try {
                CameraNative.setPtpipVerbose(true)
                Log.d(TAG, "PTPIP 상세 로그 비활성화")
            } catch (e: Exception) {
                Log.w(TAG, "PTPIP 상세 로그 비활성화 실패: ${e.message}")
            }

            // 정리 작업 완료 대기
            kotlinx.coroutines.delay(500)

            Log.d(TAG, "카메라 연결 해제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "카메라 연결 해제 중 오류", e)
        }
    }

    /**
     * 원격 촬영 실행
     */
    suspend fun capturePhoto(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "사진 촬영 시작")

            // libgphoto2가 사용 가능하면 우선 사용
            val result = CameraNative.capturePhoto()
            Log.d(TAG, "libgphoto2 촬영 결과: $result")

            result >= 0
        } catch (e: Exception) {
            Log.e(TAG, "사진 촬영 중 오류", e)
            false
        }
    }

    /**
     * 현재 Wi-Fi 연결 상태 확인
     */
    fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * 포트 연결 가능성 확인
     */
    private suspend fun isPortReachable(ipAddress: String, port: Int): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext try {
                Log.d(TAG, "포트 연결 가능성 확인: $ipAddress:$port")
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, port), 2000) // 2초 타임아웃
                socket.close()
                Log.d(TAG, "포트 연결 가능: $ipAddress:$port")
                true
            } catch (e: Exception) {
                Log.w(TAG, "포트 연결 불가: $ipAddress:$port - ${e.message}")
                false
            }
        }

    /**
     * 카메라 핑 테스트
     */
    private suspend fun pingCamera(ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "카메라 핑 테스트: $ipAddress")
            val socket = Socket()
            socket.connect(InetSocketAddress(ipAddress, 80), 1000) // 1초 타임아웃
            socket.close()
            Log.d(TAG, "카메라 핑 테스트 성공")
            true
        } catch (e: Exception) {
            Log.w(TAG, "카메라 핑 테스트 실패: ${e.message}")
            false
        }
    }

    /**
     * Wi-Fi STA 동시 연결 지원 여부 확인 (Android 9+ 필요)
     */
    fun isStaConcurrencySupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                wifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    /**
     * Wi-Fi 기능 상세 정보 가져오기
     */
    fun getWifiCapabilities(): WifiCapabilities {
        val isConnected = isWifiConnected()
        val isStaConcurrencySupported = isStaConcurrencySupported()
        val connectionInfo = if (isConnected) wifiManager.connectionInfo else null

        return WifiCapabilities(
            isConnected = isConnected,
            isStaConcurrencySupported = isStaConcurrencySupported,
            networkName = connectionInfo?.ssid?.removeSurrounding("\""),
            linkSpeed = connectionInfo?.linkSpeed,
            frequency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                connectionInfo?.frequency
            } else null,
            ipAddress = connectionInfo?.ipAddress,
            macAddress = connectionInfo?.macAddress
        )
    }

    /**
     * 카메라 이름에서 제조사 추출
     */
    private fun extractManufacturer(cameraName: String): String {
        return when {
            cameraName.contains("Nikon", ignoreCase = true) -> "Nikon"
            cameraName.contains("Canon", ignoreCase = true) -> "Canon"
            cameraName.contains("Sony", ignoreCase = true) -> "Sony"
            cameraName.contains("Fuji", ignoreCase = true) -> "Fujifilm"
            cameraName.contains("Panasonic", ignoreCase = true) -> "Panasonic"
            cameraName.contains("Olympus", ignoreCase = true) -> "Olympus"
            cameraName.contains("Leica", ignoreCase = true) -> "Leica"
            else -> "Unknown"
        }
    }

    /**
     * 카메라 요약에서 버전 정보 추출
     */
    private fun extractVersionFromSummary(summary: String): String {
        val versionMatch = Regex("Version:\\s*([^\\n]+)").find(summary)
        return versionMatch?.groupValues?.get(1)?.trim() ?: "1.0"
    }

    /**
     * 카메라 요약에서 시리얼 번호 추출
     */
    private fun extractSerialFromSummary(summary: String): String {
        val serialMatch = Regex("Serial Number:\\s*([^\\n]+)").find(summary)
            ?: Regex("S/N:\\s*([^\\n]+)").find(summary)
            ?: Regex("Serial:\\s*([^\\n]+)").find(summary)
        return serialMatch?.groupValues?.get(1)?.trim() ?: "Unknown"
    }

    /**
     * 디바이스 정보 파싱
     */
    private fun parseDeviceInfo(data: ByteArray) {
        try {
            Log.d(TAG, "디바이스 정보 파싱 중... (${data.size} bytes)")
            logPacketHex("디바이스 정보 원본 데이터", data)

            // PTP DeviceInfo 구조 파싱
            if (data.size < 20) {
                Log.w(TAG, "디바이스 정보 데이터가 너무 짧음")
                return
            }

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // PTPIP 헤더 건너뛰기 (Start Data packet)
            if (data.size > 20 && buffer.int == 20) { // Start Data 패킷인지 확인
                buffer.position(20) // PTPIP 헤더 건너뛰기
            } else {
                buffer.position(0) // 순수 PTP 데이터라면 처음부터
            }

            try {
                // PTP DeviceInfo 구조 파싱
                val standardVersion = buffer.short
                val vendorExtensionID = buffer.int
                val vendorExtensionVersion = buffer.short

                Log.d(TAG, "PTP 표준 버전: $standardVersion")
                Log.d(TAG, "벤더 확장 ID: 0x${vendorExtensionID.toString(16)}")
                Log.d(TAG, "벤더 확장 버전: $vendorExtensionVersion")

                // Vendor Extension Description (문자열)
                val vendorDesc = readPtpString(buffer)
                Log.d(TAG, "벤더 설명: $vendorDesc")

                // Functional Mode
                val functionalMode = buffer.short
                Log.d(TAG, "기능 모드: $functionalMode")

                // Operations Supported 배열 건너뛰기
                skipPtpArray(buffer) // Operations
                skipPtpArray(buffer) // Events  
                skipPtpArray(buffer) // Device Properties
                skipPtpArray(buffer) // Capture Formats
                skipPtpArray(buffer) // Image Formats

                // Manufacturer (문자열)
                val manufacturer = readPtpString(buffer)
                Log.d(TAG, "제조사: $manufacturer")

                // Model (문자열)  
                val model = readPtpString(buffer)
                Log.d(TAG, "모델: $model")

                // Device Version (문자열)
                val deviceVersion = readPtpString(buffer)
                Log.d(TAG, "디바이스 버전: $deviceVersion")

                // Serial Number (문자열)
                val serialNumber = readPtpString(buffer)
                Log.d(TAG, "시리얼 번호: $serialNumber")

                // 파싱된 정보로 PtpipCameraInfo 생성
                val deviceInfo = PtpipCameraInfo(
                    manufacturer = manufacturer.ifEmpty { "Unknown" },
                    model = model.ifEmpty { "Unknown" },
                    version = deviceVersion.ifEmpty { "Unknown" },
                    serialNumber = serialNumber.ifEmpty { "Unknown" }
                )

                _cameraInfo.value = deviceInfo
                Log.d(TAG, "파싱된 디바이스 정보: $deviceInfo")

            } catch (e: Exception) {
                Log.w(TAG, "PTP 데이터 파싱 실패, 하드코딩 값 사용: ${e.message}")

                // 파싱 실패 시 기본값 또는 추정값 사용
                val deviceInfo = PtpipCameraInfo(
                    manufacturer = extractManufacturerFromData(data),
                    model = extractModelFromData(data),
                    version = extractVersionFromData(data),
                    serialNumber = extractSerialFromData(data)
                )

                _cameraInfo.value = deviceInfo
                Log.d(TAG, "추정된 디바이스 정보: $deviceInfo")
            }

        } catch (e: Exception) {
            Log.w(TAG, "디바이스 정보 파싱 실패: ${e.message}")
        }
    }

    /**
     * PTP 문자열 읽기 (길이 + UTF-16LE 문자열)
     */
    private fun readPtpString(buffer: ByteBuffer): String {
        return try {
            if (buffer.remaining() < 1) return ""

            val length = buffer.get().toInt() and 0xFF
            if (length == 0 || buffer.remaining() < length * 2) return ""

            val stringBytes = ByteArray(length * 2)
            buffer.get(stringBytes)

            // UTF-16LE로 디코딩 후 null 문자 제거
            String(stringBytes, Charsets.UTF_16LE).replace("\u0000", "")
        } catch (e: Exception) {
            Log.w(TAG, "PTP 문자열 읽기 실패: ${e.message}")
            ""
        }
    }

    /**
     * PTP 배열 건너뛰기
     */
    private fun skipPtpArray(buffer: ByteBuffer) {
        try {
            if (buffer.remaining() < 4) return

            val arrayLength = buffer.int
            val skipBytes = arrayLength * 2 // 각 요소는 2바이트

            if (buffer.remaining() >= skipBytes) {
                buffer.position(buffer.position() + skipBytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PTP 배열 건너뛰기 실패: ${e.message}")
        }
    }

    /**
     * 데이터에서 제조사 추정
     */
    private fun extractManufacturerFromData(data: ByteArray): String {
        val dataString = String(data, Charsets.UTF_8)
        return when {
            dataString.contains("Nikon", ignoreCase = true) -> "Nikon Corporation"
            dataString.contains("Canon", ignoreCase = true) -> "Canon Inc."
            dataString.contains("Sony", ignoreCase = true) -> "Sony Corporation"
            dataString.contains("Fuji", ignoreCase = true) -> "Fujifilm Corporation"
            else -> "Unknown"
        }
    }

    /**
     * 데이터에서 모델 추정
     */
    private fun extractModelFromData(data: ByteArray): String {
        val dataString = String(data, Charsets.UTF_8)
        return when {
            dataString.contains("Z 8", ignoreCase = true) -> "Z 8"
            dataString.contains("Z_8", ignoreCase = true) -> "Z 8"
            else -> "Unknown Model"
        }
    }

    /**
     * 데이터에서 버전 추정
     */
    private fun extractVersionFromData(data: ByteArray): String {
        val dataString = String(data, Charsets.UTF_8)
        val versionMatch = Regex("V?([0-9]+\\.[0-9]+)").find(dataString)
        return versionMatch?.groupValues?.get(1) ?: "Unknown"
    }

    /**
     * 데이터에서 시리얼 번호 추정
     */
    private fun extractSerialFromData(data: ByteArray): String {
        val dataString = String(data, Charsets.UTF_8)
        val serialMatch = Regex("([0-9]{6,})").find(dataString)
        return serialMatch?.groupValues?.get(1) ?: "Unknown"
    }
}

/**
 * PTPIP 연결 상태
 */
enum class PtpipConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * PTPIP 카메라 정보
 */
data class PtpipCamera(
    val ipAddress: String,
    val port: Int,
    val name: String,
    val isOnline: Boolean
)

/**
 * PTPIP 카메라 디바이스 정보
 */
data class PtpipCameraInfo(
    val manufacturer: String,
    val model: String,
    val version: String,
    val serialNumber: String
)

data class WifiCapabilities(
    val isConnected: Boolean,
    val isStaConcurrencySupported: Boolean,
    val networkName: String?,
    val linkSpeed: Int?,
    val frequency: Int?,
    val ipAddress: Int?,
    val macAddress: String?
)