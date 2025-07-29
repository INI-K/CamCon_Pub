package com.inik.camcon.data.datasource.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.CameraCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsbCameraManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _connectedDevices = MutableStateFlow<List<UsbDevice>>(emptyList())
    val connectedDevices: StateFlow<List<UsbDevice>> = _connectedDevices.asStateFlow()

    private val _hasUsbPermission = MutableStateFlow(false)
    val hasUsbPermission: StateFlow<Boolean> = _hasUsbPermission.asStateFlow()

    private val _cameraCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    val cameraCapabilities: StateFlow<CameraCapabilities?> = _cameraCapabilities.asStateFlow()

    private val _isNativeCameraConnected = MutableStateFlow(false)
    val isNativeCameraConnected: StateFlow<Boolean> = _isNativeCameraConnected.asStateFlow()

    private var currentDevice: UsbDevice? = null
    private var currentConnection: UsbDeviceConnection? = null

    companion object {
        private const val TAG = "USB카메라매니저"
        private const val ACTION_USB_PERMISSION = "com.inik.camcon.USB_PERMISSION"
    }

    // 디바이스 캐시 관련 변수 추가
    private var lastDeviceListUpdate = 0L
    private val deviceListCacheTimeout = 1000L // 1초간 캐시 유효
    private var cachedDeviceList: List<UsbDevice>? = null

    // 카메라 기능 정보 중복 호출 방지를 위한 플래그 추가
    private var isFetchingCapabilities = false

    // 카메라 연결/초기화 Mutex (전역적 동기화)
    private val cameraInitMutex = Mutex()

    // 카메라 기능 정보 캐시 관련 변수 추가
    private var lastCapabilitiesFetch = 0L
    private val capabilitiesCacheTimeout = 30000L // 30초간 캐시 유효
    private var cachedCapabilities: CameraCapabilities? = null

    // 마스터 카메라 데이터를 가져오는 중앙집중 함수
    private suspend fun ensureMasterCameraData(): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            // 이미 가져오는 중이면 대기
            if (isFetchingMasterData) {
                // 간단한 대기 로직 (실제로는 더 정교한 동기화 필요)
                var attempts = 0
                while (isFetchingMasterData && attempts < 50) {
                    delay(100)
                    attempts++
                }
            }

            // 캐시된 데이터가 있고 유효하면 반환
            if (masterCameraAbilities != null && masterWidgetJson != null &&
                now - lastMasterFetch < masterCacheTimeout
            ) {
                Log.d(TAG, "마스터 카메라 데이터 캐시 사용 (${(now - lastMasterFetch) / 1000}초 전 생성)")
                return@withContext Pair(masterCameraAbilities!!, masterWidgetJson!!)
            }

            // 새로 가져오기
            isFetchingMasterData = true
            try {
                Log.d(TAG, "마스터 카메라 데이터 새로 가져오는 중...")
                val abilities = CameraNative.listCameraAbilities()
                val widgets = CameraNative.buildWidgetJson()

                masterCameraAbilities = abilities
                masterWidgetJson = widgets
                lastMasterFetch = now

                Log.d(
                    TAG,
                    "마스터 카메라 데이터 가져오기 완료 (abilities: ${abilities.length}, widgets: ${widgets.length})"
                )
                return@withContext Pair(abilities, widgets)
            } finally {
                isFetchingMasterData = false
            }
        }

    // 카메라 요약 정보 캐시 추가
    private var lastSummaryFetch = 0L
    private var cachedSummary: String? = null

    // 네이티브 카메라 초기화 중복 방지를 위한 플래그 추가
    private var isInitializingNativeCamera = false
    private var lastInitializedFd = -1 // 마지막으로 초기화한 FD 추적

    // 일반 초기화 중복 방지 플래그 추가
    private var isTryingGeneralInit = false // 일반 초기화 중복 방지 플래그 추가

    // 자동 연결 중복 방지 플래그 추가
    private var isAutoConnecting = false

    // 초기화 전용 Mutex
    private val initializationMutex = Mutex()

    private val knownCameraVendorIds = listOf(
        // 주요 DSLR/미러리스 제조사
        0x04A9, // Canon
        0x04B0, // Nikon  
        0x054C, // Sony
        0x04E8, // Samsung
        0x04DA, // Panasonic/Lumix
        0x07B4, // Olympus/OM System
        0x0A03, // Pentax/Ricoh
        0x0471, // Leica
        0x05AB, // Sigma
        0x0483, // Fujifilm
        0x0711, // Hasselblad
        0x0554, // Phase One

        // 액션카메라/드론 제조사
        0x2770, // Insta360
        0x2207, // DJI
        0x2731, // GoPro
        0x27C6, // Garmin VIRB
        0x2B1E, // YI Technology

        // 시네마/프로 카메라 제조사
        0x1B8C, // Blackmagic Design
        0x1954, // RED Digital Cinema
        0x2040, // ARRI
        0x3D8D, // Z CAM
        0x0451, // ZCAM
        0x2E04, // Kinefinity

        // 기타 카메라 제조사
        0x040A, // Kodak
        0x03F0, // HP
        0x05AC, // Apple
        0x0A5C, // Broadcom (일부 카메라에서 사용)
        0x0B05, // ASUS (일부 카메라에서 사용)
        0x413C, // Dell (일부 카메라에서 사용)
        0x0E8D, // MediaTek (일부 카메라에서 사용)
        0x1004, // LG Electronics
        0x19D2, // ZTE (일부 카메라에서 사용)
        0x2717, // Xiaomi (일부 카메라에서 사용)
        0x22B8, // Motorola (일부 카메라에서 사용)
        0x18D1, // Google (Pixel 카메라)
        0x0BB4, // HTC
        0x1D4D, // Peaq
        0x0FCE, // Sony Ericsson
        0x0409, // NEC
        0x0930, // Toshiba
        0x04F2, // Chicony Electronics
        0x058F, // Alcor Micro
        0x0C45, // Microdia
        0x046D, // Logitech (웹캠 등)
        0x1E4E, // Cubeternet
        0x174F, // Syntek
        0x05E3, // Genesys Logic
        0x1415, // Nam Tai E&E Products
        0x090C, // Silicon Motion
        0x13FE, // Kingston Technology
        0x0781, // SanDisk

        // 전문 방송/스튜디오 장비
        0x2A70, // OnSetLighting
        0x2BF9, // AVMATRIX
        0x1FCF, // Garmin
        0x1B3F, // Generalplus Technology
        0x2304, // Pinnacle Systems
        0x1131, // Integrated Technology Express
        0x0CCD, // TerraTec Electronic
        0x2040, // Hauppauge
        0x1164, // YUAN High-Tech Development
        0x0EB1, // WIS Technologies
        0x1F4D, // G-Technology
        0x059F, // LaCie
        0x1058, // Western Digital
        0x04E6, // SCM Microsystems
        0x0D64, // DXG Technology

        // 스마트폰 제조사 (카메라 기능)
        0x05C6, // Qualcomm
        0x1BBB, // T & A Mobile Phones
        0x2A45, // Meizu
        0x2916, // Android
        0x1F53, // NextIO
        0x2232, // Silicon Integrated Systems
        0x1004, // LG Electronics
        0x04E8, // Samsung Electronics
        0x12D1, // Huawei Technologies
        0x19A5, // BYD Company
        0x1D09, // TechFaith
        0x201E, // Haier
        0x8087  // Intel
    )

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                Log.d(TAG, "USB 권한이 승인되었습니다: ${it.deviceName}")
                                _hasUsbPermission.value = true
                                currentDevice = it
                                connectToCamera(it)
                            }
                        } else {
                            Log.d(TAG, "USB 권한이 거부되었습니다: ${device?.deviceName}")
                            _hasUsbPermission.value = false
                        }
                    }
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d(TAG, "USB 디바이스가 연결되었습니다: ${it.deviceName}")
                        if (isCameraDevice(it)) {
                            updateDeviceList()
                        }
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d(TAG, "USB 디바이스가 분리되었습니다: ${it.deviceName}")
                        if (it == currentDevice) {
                            _hasUsbPermission.value = false
                            currentDevice = null
                            currentConnection?.close()
                            currentConnection = null
                        }
                        updateDeviceList()
                    }
                }
            }
        }
    }

    init {
        registerUsbReceiver()

        // 앱 재개 시 기존 카메라 연결 상태를 먼저 확인
        checkExistingCameraConnection()

        initializeDeviceList()
    }

    /**
     * 앱 재개 시 기존 카메라 연결 상태를 확인하는 함수
     */
    private fun checkExistingCameraConnection() {
        try {
            // 네이티브 카메라가 이미 초기화되어 있는지 확인
            val isNativeInitialized = try {
                // 1단계: 카메라 요약 정보 확인
                val summary = CameraNative.getCameraSummary()
                if (summary.isEmpty() || summary.contains("에러", ignoreCase = true) ||
                    summary.contains("error", ignoreCase = true)
                ) {
                    Log.d(TAG, "카메라 요약 정보가 없거나 오류: $summary")
                    false
                } else {
                    // 2단계: 실제 카메라 감지 시도
                    val detection = CameraNative.detectCamera()
                    if (detection.contains("감지된 카메라", ignoreCase = true) &&
                        !detection.contains("감지되지 않았습니다", ignoreCase = true) &&
                        !detection.contains("Error loading a library", ignoreCase = true)
                    ) {
                        Log.d(TAG, "카메라 감지 성공: $detection")
                        true
                    } else {
                        Log.d(TAG, "카메라 감지 실패: $detection")
                        false
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "네이티브 카메라 상태 확인 중 예외 (정상): ${e.message}")
                false
            }

            if (isNativeInitialized) {
                Log.d(TAG, "앱 재개 시 네이티브 카메라가 실제로 초기화되어 있음")
                // 상태를 true로 미리 설정하여 불필요한 상태 변경 방지
                _isNativeCameraConnected.value = true
            } else {
                Log.d(TAG, "앱 재개 시 네이티브 카메라 초기화되지 않음 - 새로 초기화 필요")
                // false 상태 유지 (기본값)
            }
        } catch (e: Exception) {
            Log.d(TAG, "기존 카메라 연결 상태 확인 실패: ${e.message}")
        }
    }

    private fun registerUsbReceiver() {
        // 커스텀 USB 권한 브로드캐스트 (앱 전용)
        val permissionFilter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(usbReceiver, permissionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        // 시스템 USB 브로드캐스트 (시스템 전용)
        val systemFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.registerReceiver(usbReceiver, systemFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, systemFilter)
        }
    }

    private fun initializeDeviceList() {
        Log.d(TAG, "USB 디바이스 목록 초기화 시작")

        // 앱 재개 시 기존 연결 상태 확인
        val currentlyConnected = _isNativeCameraConnected.value
        if (currentlyConnected && currentDevice != null) {
            Log.d(TAG, "앱 재개 시 기존 연결된 디바이스 확인: ${currentDevice?.deviceName}")

            // 기존 디바이스에 여전히 권한이 있는지 확인
            currentDevice?.let { device ->
                if (usbManager.hasPermission(device)) {
                    Log.d(TAG, "기존 디바이스에 권한이 있음: ${device.deviceName}")
                    _hasUsbPermission.value = true
                    _connectedDevices.value = listOf(device)
                    // 이미 연결되어 있으므로 재연결하지 않음
                    return
                } else {
                    Log.w(TAG, "기존 디바이스 권한이 없어짐: ${device.deviceName}")
                    // 권한이 없어졌으므로 상태 초기화 - 코루틴 스코프에서 실행
                    CoroutineScope(Dispatchers.IO).launch {
                        updateNativeCameraConnectionState(false, "권한 손실")
                    }
                    currentDevice = null
                    currentConnection?.close()
                    currentConnection = null
                }
            }
        }

        // 디바이스 목록을 한 번만 가져와서 캐시
        val devices = getUsbDevicesInternal()
        cachedDeviceList = devices
        lastDeviceListUpdate = System.currentTimeMillis()

        Log.d(TAG, "초기 USB 디바이스 발견: ${devices.size}개")

        // 연결된 디바이스 목록 업데이트
        _connectedDevices.value = devices

        // 권한 확인 및 연결 시도
        devices.forEach { device ->
            Log.d(
                TAG,
                "디바이스: ${device.deviceName}, 제조사ID: 0x${device.vendorId.toString(16)}, 제품ID: 0x${
                    device.productId.toString(16)
                }"
            )

            val hasPermission = usbManager.hasPermission(device)
            Log.d(TAG, "디바이스 ${device.deviceName}에 대한 권한 상태: $hasPermission")

            if (!hasPermission) {
                Log.d(TAG, "디바이스에 대한 권한을 요청합니다: ${device.deviceName}")
                requestPermission(device)
            } else {
                Log.d(TAG, "이미 권한이 있는 디바이스입니다: ${device.deviceName}")
                _hasUsbPermission.value = true
                currentDevice = device

                // 이미 네이티브 카메라가 연결되어 있다면 재연결하지 않음
                if (!_isNativeCameraConnected.value) {
                    connectToCamera(device)
                } else {
                    Log.d(TAG, "네이티브 카메라가 이미 연결되어 있음 - 재연결 생략")
                }
            }
        }
    }

    private fun checkInitialDevices() {
        // 이 메서드는 더 이상 필요 없음 - initializeDeviceList()에서 처리
        Log.d(TAG, "checkInitialDevices() 호출됨 - initializeDeviceList()에서 이미 처리됨")
    }

    fun getCameraDevices(): List<UsbDevice> {
        // 캐시된 결과가 있고 아직 유효하면 캐시 반환
        val now = System.currentTimeMillis()
        cachedDeviceList?.let { cached ->
            if (now - lastDeviceListUpdate < deviceListCacheTimeout) {
                Log.d(TAG, "캐시된 USB 디바이스 목록 반환: ${cached.size}개")
                return cached
            }
        }

        // 캐시가 없거나 만료되었으면 새로 가져오기
        val devices = getUsbDevicesInternal()
        cachedDeviceList = devices
        lastDeviceListUpdate = now

        return devices
    }

    private fun getUsbDevicesInternal(): List<UsbDevice> {
        val allDevices = usbManager.deviceList.values.toList()
        Log.d(TAG, "총 USB 디바이스 발견: ${allDevices.size}")

        allDevices.forEach { device ->
            Log.d(TAG, "디바이스: ${device.deviceName}")
            Log.d(TAG, "  제조사ID: 0x${device.vendorId.toString(16)}")
            Log.d(TAG, "  제품ID: 0x${device.productId.toString(16)}")
            Log.d(TAG, "  인터페이스 개수: ${device.interfaceCount}")

            for (i in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(i)
                Log.d(
                    TAG,
                    "  인터페이스 $i: 클래스=${usbInterface.interfaceClass}, 서브클래스=${usbInterface.interfaceSubclass}, 프로토콜=${usbInterface.interfaceProtocol}"
                )
            }
        }

        return allDevices.filter { device ->
            val isCamera = isCameraDevice(device)
            Log.d(TAG, "디바이스 ${device.deviceName}가 카메라인지: $isCamera")
            isCamera
        }
    }

    private fun updateDeviceList() {
        // 캐시 무효화
        cachedDeviceList = null

        val cameraDevices = getCameraDevices()
        _connectedDevices.value = cameraDevices
        Log.d(TAG, "카메라 디바이스 목록 업데이트: ${cameraDevices.size}개")
    }

    private fun isCameraDevice(device: UsbDevice): Boolean {
        // 먼저 USB 인터페이스 클래스 확인
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            // 클래스 6 = Still Image Capture Device (PTP)
            // 클래스 255 = Vendor Specific (많은 카메라가 사용)
            // 클래스 8 = Mass Storage (일부 카메라가 사용)
            if (usbInterface.interfaceClass == 6 ||
                usbInterface.interfaceClass == 255 ||
                usbInterface.interfaceClass == 8
            ) {
                Log.d(
                    TAG,
                    "인터페이스 클래스로 카메라로 식별됨: ${usbInterface.interfaceClass}"
                )
                return true
            }
        }

        // 제조사 ID로 확인 (더 관대하게)
        val isKnownVendor = device.vendorId in knownCameraVendorIds
        if (isKnownVendor) {
            Log.d(
                TAG,
                "제조사 ID로 카메라로 식별됨: 0x${device.vendorId.toString(16)}"
            )
            return true
        }

        // 디바이스 이름으로 확인
        val deviceName = device.deviceName?.lowercase() ?: ""
        val hasCamera = deviceName.contains("camera") ||
                deviceName.contains("canon") ||
                deviceName.contains("nikon") ||
                deviceName.contains("sony") ||
                deviceName.contains("fuji")

        if (hasCamera) {
            Log.d(TAG, "디바이스 이름으로 카메라로 식별됨: ${device.deviceName}")
            return true
        }

        Log.d(TAG, "카메라로 식별되지 않은 디바이스: ${device.deviceName}")
        return false
    }

    fun requestPermission(device: UsbDevice) {
        Log.d(TAG, "USB 권한 요청 시작: ${device.deviceName}")

        // 이미 권한이 있는지 다시 한번 확인
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "이미 권한이 있습니다: ${device.deviceName}")
            _hasUsbPermission.value = true
            currentDevice = device
            connectToCamera(device)
            return
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            device.deviceId, // 디바이스별 고유 ID 사용
            Intent(ACTION_USB_PERMISSION).apply {
                putExtra(UsbManager.EXTRA_DEVICE, device)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d(TAG, "USB 권한 대화상자를 표시합니다: ${device.deviceName}")
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun connectToCamera(device: UsbDevice) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "백그라운드에서 카메라 연결 시작: ${device.deviceName}")

                // 이미 네이티브 카메라가 연결되어 있으면 중복 연결 방지
                if (_isNativeCameraConnected.value) {
                    Log.d(TAG, "네이티브 카메라가 이미 연결되어 있음 - 중복 연결 방지: ${device.deviceName}")
                    return@launch
                }

                // 이미 같은 디바이스에 연결 중이거나 연결되어 있으면 중복 실행 방지
                if (currentDevice?.deviceName == device.deviceName && currentConnection != null) {
                    Log.d(TAG, "같은 디바이스에 이미 연결되어 있음 - 중복 연결 방지: ${device.deviceName}")
                    return@launch
                }

                // 이미 초기화 중이면 중복 실행 방지
                if (isInitializingNativeCamera) {
                    Log.d(TAG, "네이티브 카메라 초기화가 이미 진행 중 - 중복 연결 방지: ${device.deviceName}")
                    return@launch
                }

                val connection = usbManager.openDevice(device)
                connection?.let {
                    currentConnection = it
                    val fd = it.fileDescriptor
                    Log.d(TAG, "카메라에 연결되었습니다. 파일 디스크립터: $fd")

                    // USB 디바이스 정보 로깅
                    Log.d(TAG, "디바이스 정보:")
                    Log.d(TAG, "  이름: ${device.deviceName}")
                    Log.d(TAG, "  제조사ID: 0x${device.vendorId.toString(16)}")
                    Log.d(TAG, "  제품ID: 0x${device.productId.toString(16)}")
                    Log.d(TAG, "  클래스: ${device.deviceClass}")
                    Log.d(TAG, "  서브클래스: ${device.deviceSubclass}")
                    Log.d(TAG, "  프로토콜: ${device.deviceProtocol}")

                    // 인터페이스 정보
                    for (i in 0 until device.interfaceCount) {
                        val intf = device.getInterface(i)
                        Log.d(TAG, "  인터페이스 $i:")
                        Log.d(TAG, "    클래스: ${intf.interfaceClass}")
                        Log.d(TAG, "    서브클래스: ${intf.interfaceSubclass}")
                        Log.d(TAG, "    프로토콜: ${intf.interfaceProtocol}")
                        Log.d(TAG, "    엔드포인트 수: ${intf.endpointCount}")
                    }

                    // 네이티브 카메라 초기화 시도
                    initializeNativeCamera(fd)
                } ?: run {
                    Log.e(TAG, "USB 디바이스 열기 실패: ${device.deviceName}")
                    updateNativeCameraConnectionState(false, "USB 디바이스 열기 실패")
                }
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결에 실패했습니다", e)
                updateNativeCameraConnectionState(false, "카메라 연결에 실패했습니다")
            }
        }
    }

    private suspend fun initializeNativeCamera(fd: Int) = withContext(Dispatchers.IO) {
        // 초기화 전용 Mutex로 더 강력한 동기화
        initializationMutex.withLock {
            try {
                // 중복 FD 초기화 방지 - 더 엄격한 체크
                if (fd == lastInitializedFd && _isNativeCameraConnected.value) {
                    Log.d(TAG, "동일한 FD로 이미 초기화 완료 - 중복 방지: $fd")
                    return@withLock
                }

                // 초기화 진행 중 체크
                if (isInitializingNativeCamera) {
                    Log.d(TAG, "네이티브 카메라 초기화가 이미 진행 중 - 대기: $fd")
                    return@withLock
                }

                // 이미 카메라가 연결되어 있으면 재초기화 차단
                if (_isNativeCameraConnected.value) {
                    Log.d(TAG, "카메라가 이미 연결되어 있음 - 재초기화 차단: $fd")
                    return@withLock
                }

                Log.d(
                    TAG,
                    "initCameraWithFd 시작: fd=$fd, libDir=${context.applicationInfo.nativeLibraryDir}"
                )
                isInitializingNativeCamera = true
                lastInitializedFd = fd

                val nativeLibDir = context.applicationInfo.nativeLibraryDir
                Log.d(TAG, "네이티브 라이브러리 디렉토리: $nativeLibDir")

                // USB 연결 안정화를 위한 짧은 지연
                delay(500)

                // 네이티브 함수 호출을 IO 스레드에서 실행
                val result = CameraNative.initCameraWithFd(fd, nativeLibDir)
                Log.d(TAG, "initCameraWithFd 완료 -> 최종 결과 ret=$result")

                if (result == 0) { // GP_OK
                    Log.d(TAG, "네이티브 카메라 초기화 성공")
                    updateNativeCameraConnectionState(true, "초기화 성공")

                    // 카메라 요약 정보 가져오기 - 한 번만 호출
                    val summary = getCachedOrFetchSummary()
                    Log.d(TAG, "카메라 요약: $summary")

                    // 카메라 기능 정보 가져오기는 다른 곳에서 호출되므로 여기서는 제거
                    // fetchCameraCapabilitiesIfNeeded() 제거

                } else if (result == -52) { // GP_ERROR_IO_USB_FIND
                    Log.e(TAG, "USB 포트에서 카메라를 찾을 수 없음. 일반 초기화로 대체")
                    // USB 재연결 대신 바로 일반 초기화로 이동
                    tryGeneralInit()

                } else {
                    Log.e(TAG, "네이티브 카메라 초기화 실패: $result")
                    updateNativeCameraConnectionState(false, "초기화 실패")
                    lastInitializedFd = -1 // 실패 시 FD 초기화
                    // USB 초기화가 실패하면 일반 초기화 시도
                    tryGeneralInit()
                }
            } catch (e: Exception) {
                Log.e(TAG, "네이티브 카메라 초기화 중 예외 발생", e)
                updateNativeCameraConnectionState(false, "예외 발생")
                lastInitializedFd = -1 // 예외 발생 시 FD 초기화
                tryGeneralInit()
            } finally {
                isInitializingNativeCamera = false
                Log.d(TAG, "네이티브 카메라 초기화 완료 - 플래그 해제")
            }
        }
    }

    private fun getCachedOrFetchSummary(): String {
        val now = System.currentTimeMillis()

        // 캐시된 요약 정보가 있고 유효하면 반환 (60초간 유효)
        cachedSummary?.let { cached ->
            if (now - lastSummaryFetch < 60000) {
                Log.d(TAG, "캐시된 카메라 요약 정보 사용 (${(now - lastSummaryFetch) / 1000}초 전 생성)")
                return cached
            }
        }

        // 캐시가 없거나 만료되었으면 새로 가져오기
        Log.d(TAG, "새로운 카메라 요약 정보 가져오는 중...")
        val summary = CameraNative.getCameraSummary()
        cachedSummary = summary
        lastSummaryFetch = now
        Log.d(TAG, "새로 가져온 카메라 요약 정보 길이: ${summary.length}")
        return summary
    }

    private suspend fun fetchCameraCapabilities() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "카메라 기능 정보 가져오기 시작")

            // 마스터 데이터 사용
            val (abilitiesJson, widgetJson) = ensureMasterCameraData()
            Log.d(TAG, "마스터 데이터에서 카메라 기능 정보 파싱")

            // JSON 파싱하여 CameraCapabilities 객체 생성 - 무거운 작업
            val capabilities = parseCameraCapabilities(abilitiesJson, widgetJson)

            // 캐시 갱신
            cachedCapabilities = capabilities
            lastCapabilitiesFetch = System.currentTimeMillis()
            Log.d(
                TAG,
                "카메라 능력 정보 캐시 저장 완료: ${capabilities.model} (다음 ${capabilitiesCacheTimeout / 1000}초간 유효)"
            )

            // UI 업데이트만 메인 스레드에서
            withContext(Dispatchers.Main) {
                _cameraCapabilities.value = capabilities
            }

            // 캐시 업데이트
            cachedCapabilities = capabilities
            lastCapabilitiesFetch = System.currentTimeMillis()
            Log.d(TAG, "카메라 기능 정보 업데이트 완료: ${capabilities.model}")

        } catch (e: Exception) {
            Log.e(TAG, "카메라 기능 정보 가져오기 실패", e)
            withContext(Dispatchers.Main) {
                _cameraCapabilities.value = null
            }
        }
    }

    private fun parseCameraCapabilities(abilitiesJson: String, widgetJson: String): CameraCapabilities {
        return try {
            val abilitiesObj = JSONObject(abilitiesJson)
            
            // 기본 기능들 파싱
            val captureImage = abilitiesObj.optBoolean("captureImage", false)
            val captureVideo = abilitiesObj.optBoolean("captureVideo", false)
            val capturePreview = abilitiesObj.optBoolean("capturePreview", false)
            val config = abilitiesObj.optBoolean("config", false)
            val triggerCapture = abilitiesObj.optBoolean("triggerCapture", false)
            
            // 파일 작업
            val fileDownload = abilitiesObj.optBoolean("fileDownload", false)
            val fileDelete = abilitiesObj.optBoolean("fileDelete", false)
            val filePreview = abilitiesObj.optBoolean("filePreview", false)
            
            // 폴더 작업
            val deleteAll = abilitiesObj.optBoolean("deleteAll", false)
            val putFile = abilitiesObj.optBoolean("putFile", false)
            val makeDir = abilitiesObj.optBoolean("makeDir", false)
            
            // 위젯에서 설정 가능한 기능들 추출
            val hasAutofocus = widgetJson.contains("autofocus", ignoreCase = true)
            val hasManualFocus = widgetJson.contains("manualfocus", ignoreCase = true)
            val hasLiveView = widgetJson.contains("liveview", ignoreCase = true) || capturePreview
            val hasTimelapse = captureImage && triggerCapture
            val hasBracketing = captureImage && config
            val hasBurstMode = captureImage && triggerCapture
            
            // ISO 설정 확인
            val isoSettings = extractSettingOptions(widgetJson, "iso")
            val shutterSpeedSettings = extractSettingOptions(widgetJson, "shutter")
            val apertureSettings = extractSettingOptions(widgetJson, "aperture")
            val whiteBalanceSettings = extractSettingOptions(widgetJson, "whitebalance")
            
            CameraCapabilities(
                model = abilitiesObj.optString("model", "알 수 없음"),
                
                // 기본 촬영 기능
                canCapturePhoto = captureImage,
                canCaptureVideo = captureVideo,
                canLiveView = hasLiveView,
                canTriggerCapture = triggerCapture,
                
                // 고급 촬영 기능
                supportsBurstMode = hasBurstMode,
                supportsTimelapse = hasTimelapse,
                supportsBracketing = hasBracketing,
                supportsBulbMode = widgetJson.contains("bulb", ignoreCase = true),
                
                // 초점 기능
                supportsAutofocus = hasAutofocus,
                supportsManualFocus = hasManualFocus,
                supportsFocusPoint = hasManualFocus,
                
                // 파일 관리
                canDownloadFiles = fileDownload,
                canDeleteFiles = fileDelete,
                canPreviewFiles = filePreview,
                
                // 설정 가능한 옵션들
                availableIsoSettings = isoSettings,
                availableShutterSpeeds = shutterSpeedSettings,
                availableApertures = apertureSettings,
                availableWhiteBalanceSettings = whiteBalanceSettings,
                
                // 기타
                supportsRemoteControl = config,
                supportsConfigChange = config,
                batteryLevel = null // 추후 구현
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "카메라 기능 정보 파싱 실패", e)
            CameraCapabilities(
                model = "파싱 실패",
                canCapturePhoto = false,
                canCaptureVideo = false,
                canLiveView = false,
                canTriggerCapture = false,
                supportsBurstMode = false,
                supportsTimelapse = false,
                supportsBracketing = false,
                supportsBulbMode = false,
                supportsAutofocus = false,
                supportsManualFocus = false,
                supportsFocusPoint = false,
                canDownloadFiles = false,
                canDeleteFiles = false,
                canPreviewFiles = false,
                availableIsoSettings = emptyList(),
                availableShutterSpeeds = emptyList(),
                availableApertures = emptyList(),
                availableWhiteBalanceSettings = emptyList(),
                supportsRemoteControl = false,
                supportsConfigChange = false,
                batteryLevel = null
            )
        }
    }

    private fun extractSettingOptions(widgetJson: String, settingName: String): List<String> {
        return try {
            val json = JSONObject(widgetJson)
            val options = mutableListOf<String>()
            
            // JSON에서 해당 설정의 선택지들을 재귀적으로 찾기
            extractOptionsFromJson(json, settingName.lowercase(), options)
            
            options.distinct()
        } catch (e: Exception) {
            Log.w(TAG, "$settingName 설정 옵션 추출 실패", e)
            emptyList()
        }
    }

    private fun extractOptionsFromJson(json: JSONObject, settingName: String, options: MutableList<String>) {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.opt(key)

            if (key.lowercase().contains(settingName) && json.has("choices")) {
                val choices = json.optJSONArray("choices")
                if (choices != null) {
                    for (i in 0 until choices.length()) {
                        options.add(choices.optString(i))
                    }
                }
            } else if (value is JSONObject) {
                extractOptionsFromJson(value, settingName, options)
            } else if (value is org.json.JSONArray) {
                for (i in 0 until value.length()) {
                    val item = value.opt(i)
                    if (item is JSONObject) {
                        extractOptionsFromJson(item, settingName, options)
                    }
                }
            }
        }
    }

    /**
     * 현재 연결된 카메라의 기능 정보를 새로고침합니다.
     */
    fun refreshCameraCapabilities() {
        if (_isNativeCameraConnected.value) {
            CoroutineScope(Dispatchers.IO).launch {
                // 강제 새로고침을 위해 캐시 무효화
                invalidateCapabilitiesCache()
                fetchCameraCapabilitiesIfNeeded()
            }
        }
    }

    /**
     * 카메라 연결 해제
     */
    fun disconnectCamera() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (_isNativeCameraConnected.value) {
                    Log.d(TAG, "카메라 PC 모드 완전 종료 시작")

                    // 카메라 이벤트 리스너 중지
                    try {
                        CameraNative.stopListenCameraEvents()
                        Log.d(TAG, "카메라 이벤트 리스너 중지 완료")
                    } catch (e: Exception) {
                        Log.w(TAG, "카메라 이벤트 리스너 중지 중 오류 (정상적일 수 있음)", e)
                    }

                    // 카메라 연결 완전 해제 (PC 모드 종료 포함)
                    CameraNative.closeCamera()
                    Log.d(TAG, "카메라 네이티브 연결 해제 완료")

                    updateNativeCameraConnectionState(false, "연결 해제")
                    withContext(Dispatchers.Main) {
                        _cameraCapabilities.value = null
                    }
                }

                currentDevice = null
                withContext(Dispatchers.Main) {
                    _hasUsbPermission.value = false
                }

                currentConnection?.close()
                currentConnection = null

                // 캐시 무효화
                invalidateCapabilitiesCache()

                // FD 추적 초기화
                lastInitializedFd = -1

                Log.d(TAG, "카메라 연결 해제 완료 - PC 모드에서 완전히 해제됨")
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 해제 중 오류", e)

                // 오류가 발생해도 상태는 초기화
                updateNativeCameraConnectionState(false, "오류 발생")
                withContext(Dispatchers.Main) {
                    _cameraCapabilities.value = null
                    _hasUsbPermission.value = false
                }
                currentDevice = null
                currentConnection?.close()
                currentConnection = null

                // 캐시 무효화
                invalidateCapabilitiesCache()

                // FD 초기화
                lastInitializedFd = -1
            }
        }
    }

    private fun invalidateCapabilitiesCache() {
        cachedCapabilities = null
        lastCapabilitiesFetch = 0
        cachedSummary = null
        lastSummaryFetch = 0
    }

    fun getCurrentDevice(): UsbDevice? = currentDevice

    fun getFileDescriptor(): Int? {
        return currentDevice?.let { device ->
            try {
                // 기존 연결이 있다면 재사용
                currentConnection?.let { existingConnection ->
                    Log.d(TAG, "기존 연결 재사용: FD=${existingConnection.fileDescriptor}")
                    return existingConnection.fileDescriptor
                }

                // 새 연결 생성
                val connection = usbManager.openDevice(device)
                connection?.let { conn ->
                    currentConnection = conn
                    Log.d(TAG, "새 연결 생성: FD=${conn.fileDescriptor}")
                    conn.fileDescriptor
                } ?: run {
                    Log.e(TAG, "USB 디바이스 연결 실패")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "파일 디스크립터 가져오기 실패", e)
                null
            }
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(usbReceiver)
            disconnectCamera()
        } catch (e: Exception) {
            Log.w(TAG, "USB 리시버 등록 해제 실패", e)
        }
    }

    // 네이티브 카메라 상태를 안전하게 업데이트하는 함수
    private suspend fun updateNativeCameraConnectionState(newState: Boolean, reason: String = "") {
        if (_isNativeCameraConnected.value == newState) {
            Log.d(
                TAG,
                "USB 카메라 연결 상태 이미 $newState - UI 업데이트 생략 ${if (reason.isNotEmpty()) "($reason)" else ""}"
            )
            return
        }

        withContext(Dispatchers.Main) {
            _isNativeCameraConnected.value = newState
        }
        Log.d(
            TAG,
            "USB 카메라 연결 상태 변경: $newState ${if (reason.isNotEmpty()) "($reason)" else ""}"
        )
    }

    private suspend fun tryGeneralInit() = withContext(Dispatchers.IO) {
        // 전역 Mutex로 일반 초기화도 동기화
        cameraInitMutex.withLock {
            // 이미 카메라가 연결되어 있으면 일반 초기화도 차단
            if (_isNativeCameraConnected.value) {
                Log.d(TAG, "카메라가 이미 연결되어 있음. 일반 초기화 차단")
                return@withLock
            }

            // 일반 초기화 중복 방지
            if (isTryingGeneralInit) {
                Log.d(TAG, "일반 초기화가 이미 진행 중입니다. (중복 실행 방지)")
                return@withLock
            }

            isTryingGeneralInit = true

            Log.d(TAG, "일반 카메라 초기화 시도...")

            try {
                // USB FD를 사용하지 않는 일반 초기화
                val generalResult = CameraNative.initCamera()
                Log.d(TAG, "일반 카메라 초기화 결과: $generalResult")

                if (generalResult.contains("OK", ignoreCase = true)) {
                    updateNativeCameraConnectionState(true, "일반 초기화 성공")
                    // 중복 방지하면서 capabilities 가져오기
                    fetchCameraCapabilitiesIfNeeded()
                } else {
                    Log.e(TAG, "일반 초기화 실패: $generalResult")
                    updateNativeCameraConnectionState(false, "일반 초기화 실패")
                }

            } catch (e: Exception) {
                Log.e(TAG, "일반 카메라 초기화 중 예외 발생", e)
                updateNativeCameraConnectionState(false, "예외 발생")
            } finally {
                isTryingGeneralInit = false
            }
        }
    }

    // 중복 방지가 있는 capabilities 가져오기 함수
    private suspend fun fetchCameraCapabilitiesIfNeeded() {
        // 이미 가져오는 중이면 건너뛰기
        if (isFetchingCapabilities) {
            Log.d(TAG, "카메라 기능 정보 가져오기 중복 호출 방지")
            return
        }

        // 캐시된 결과가 있고 아직 유효하면 캐시 반환
        val now = System.currentTimeMillis()
        cachedCapabilities?.let { cached ->
            if (now - lastCapabilitiesFetch < capabilitiesCacheTimeout) {
                Log.d(TAG, "캐시에서 카메라 기능 정보 반환: ${cached.model}")
                withContext(Dispatchers.Main) {
                    _cameraCapabilities.value = cached
                }
                return
            }
        }

        Log.d(TAG, "카메라 기능 정보 업데이트")
        isFetchingCapabilities = true
        try {
            fetchCameraCapabilities()
        } finally {
            isFetchingCapabilities = false
        }
    }

    // 카메라 능력 정보 중앙 집중 관리
    private var masterCameraAbilities: String? = null
    private var masterWidgetJson: String? = null
    private var lastMasterFetch = 0L
    private val masterCacheTimeout = 60000L // 1분간 유효
    private var isFetchingMasterData = false

    /**
     * 마스터 데이터를 활용한 빠른 라이브뷰 지원 확인
     */
    suspend fun isLiveViewSupported(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, widgetJson) = ensureMasterCameraData()
            val hasLiveViewSize = widgetJson.contains("liveviewsize", ignoreCase = true)
            val hasLiveView = widgetJson.contains("liveview", ignoreCase = true)

            Log.d(TAG, "라이브뷰 지원 확인 - 마스터 데이터 사용: ${hasLiveViewSize || hasLiveView}")
            hasLiveViewSize || hasLiveView
        } catch (e: Exception) {
            Log.e(TAG, "라이브뷰 지원 확인 실패", e)
            false
        }
    }

    /**
     * 마스터 데이터를 활용한 특정 설정 지원 확인
     */
    suspend fun hasCapability(capability: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val (abilities, widgets) = ensureMasterCameraData()
            val hasInAbilities = abilities.contains(capability, ignoreCase = true)
            val hasInWidgets = widgets.contains(capability, ignoreCase = true)

            Log.d(TAG, "$capability 지원 확인 - 마스터 데이터 사용: ${hasInAbilities || hasInWidgets}")
            hasInAbilities || hasInWidgets
        } catch (e: Exception) {
            Log.e(TAG, "$capability 지원 확인 실패", e)
            false
        }
    }

    /**
     * 마스터 데이터를 활용한 위젯 JSON 반환 (외부 호출용)
     */
    suspend fun buildWidgetJsonFromMaster(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, widgetJson) = ensureMasterCameraData()
            Log.d(TAG, "마스터 데이터에서 위젯 JSON 반환: ${widgetJson.length} chars")
            widgetJson
        } catch (e: Exception) {
            Log.e(TAG, "마스터 데이터에서 위젯 JSON 가져오기 실패", e)
            "{\"error\": \"마스터 데이터 접근 실패\"}"
        }
    }

    /**
     * 마스터 데이터를 활용한 카메라 능력 정보 반환 (외부 호출용)
     */
    suspend fun getCameraAbilitiesFromMaster(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val (abilities, _) = ensureMasterCameraData()
            Log.d(TAG, "마스터 데이터에서 카메라 능력 반환: ${abilities.length} chars")
            abilities
        } catch (e: Exception) {
            Log.e(TAG, "마스터 데이터에서 카메라 능력 가져오기 실패", e)
            "{\"error\": \"마스터 데이터 접근 실패\"}"
        }
    }
}
