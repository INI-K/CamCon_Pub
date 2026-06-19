package com.inik.camcon

import android.util.Log
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.nativesource.LiveViewCallback

// 네이티브 에러 콜백 인터페이스
interface NativeErrorCallback {
    fun onNativeError(errorCode: Int, errorMessage: String)
}

// 카메라 정리 완료 콜백 인터페이스
interface CameraCleanupCallback {
    fun onCleanupComplete(success: Boolean, message: String)
}

// 이벤트 리스너 종료 콜백 인터페이스
interface EventListenerStopCallback {
    fun onStopped()
}

object CameraNative {
    private const val TAG = "CameraNative"

    // libgphoto2 로그 레벨 상수들
    const val GP_LOG_ERROR = 0
    const val GP_LOG_VERBOSE = 1
    const val GP_LOG_DEBUG = 2
    const val GP_LOG_DATA = 3
    const val GP_LOG_ALL = GP_LOG_DATA

    // 라이브러리 로딩 상태 추적
    @Volatile
    private var librariesLoaded = false

    init {
        try {
            loadNativeLibraries()
            librariesLoaded = true
            Log.d(TAG, "모든 네이티브 라이브러리 로딩 완료")
        } catch (e: Throwable) {
            Log.e(TAG, "네이티브 라이브러리 로딩 실패", e)
            librariesLoaded = false
            // 라이브러리 로딩 실패를 앱에서 감지할 수 있도록 예외를 다시 던짐
            throw RuntimeException("네이티브 라이브러리 로딩 실패: ${e.message}", e)
        }
    }

    /**
     * 네이티브 라이브러리들을 안전한 순서로 로딩
     */
    private fun loadNativeLibraries() {
        val libraries = listOf(
            // gphoto2 라이브러리들
            "gphoto2_port" to "gphoto2 포트 라이브러리",
            "gphoto2_port_iolib_disk" to "gphoto2 디스크 I/O 라이브러리",
            "gphoto2_port_iolib_usb1" to "gphoto2 USB1 I/O 라이브러리",
            "gphoto2" to "gphoto2 메인 라이브러리",
            "native-lib" to "CamCon JNI 라이브러리"
        )

        val successList = mutableListOf<String>()
        val failureList = mutableListOf<Pair<String, String>>()

        for ((libName, description) in libraries) {
            try {
                System.loadLibrary(libName)
                successList.add(description)

                // 각 라이브러리 로딩 후 약간의 대기 시간 (릴리즈 모드 안정성)
                Thread.sleep(10)

            } catch (e: UnsatisfiedLinkError) {
                val errorMsg = e.message ?: "알 수 없는 오류"
                failureList.add(description to errorMsg)
                Log.e(TAG, "$description 로딩 실패: $libName", e)

                // 중요한 라이브러리 로딩 실패 시 예외 발생
                throw RuntimeException(
                    "$description 로딩 실패\n" +
                            "라이브러리: lib$libName.so\n" +
                            "에러: $errorMsg\n" +
                            "성공: ${successList.joinToString()}\n" +
                            "실패: $description",
                    e
                )
            } catch (e: SecurityException) {
                val errorMsg = e.message ?: "알 수 없는 보안 오류"
                failureList.add(description to errorMsg)
                Log.e(TAG, "$description 로딩 권한 오류: $libName", e)

                throw RuntimeException(
                    "$description 로딩 권한 오류\n" +
                            "라이브러리: lib$libName.so\n" +
                            "에러: $errorMsg",
                    e
                )
            } catch (e: Exception) {
                val errorMsg = e.message ?: "알 수 없는 예외"
                failureList.add(description to errorMsg)
                Log.e(TAG, "$description 로딩 중 예외 발생: $libName", e)

                throw RuntimeException(
                    "$description 로딩 중 예외 발생\n" +
                            "라이브러리: lib$libName.so\n" +
                            "에러: $errorMsg",
                    e
                )
            }
        }

        // 모든 라이브러리 로딩 성공 시 로그
        Log.i(TAG, "모든 네이티브 라이브러리 로딩 성공 (${successList.size}개)")
    }

    /**
     * 라이브러리 로딩 상태 확인
     *
     * 실제 게이팅은 호출자 레이어(NativeCameraDataSource/PtpipDataSource)에서 이 값을
     * 확인해 수행한다. init 블록이 로딩 실패 시 RuntimeException을 던지므로,
     * 라이브러리 미로딩 상태로 external 함수에 진입하는 경로는 존재하지 않는다.
     */
    fun isLibrariesLoaded(): Boolean = librariesLoaded

    /**
     * libgphoto2 환경변수를 설정합니다.
     * PTP/IP나 USB 연결 전에 호출해야 합니다.
     */
    external fun setupEnvironmentPaths(nativeLibDir: String): Boolean

    external fun testLibraryLoad(): String
    external fun getLibGphoto2Version(): String
    external fun getPortInfo(): String
    external fun initCamera(): String
    external fun initCameraWithPtpip(ipAddress: String, port: Int, libDir: String): String
    external fun initCameraForAPMode(ipAddress: String, port: Int, libDir: String): String
    external fun initCameraWithFd(fd: Int, nativeLibDir: String): Int
    external fun listenCameraEvents(callback: CameraCaptureListener)
    external fun initCameraWithSessionMaintenance(ipAddress: String, port: Int, libDir: String): Int
    external fun capturePhoto(): Int
    external fun capturePhotoAsync(callback: CameraCaptureListener, saveDir: String)
    external fun getCameraSummary(): String
    external fun closeCamera(): String
    external fun closeCameraAsync(callback: CameraCleanupCallback) // 비동기 closeCamera 메서드 추가
    external fun detectCamera(): String
    external fun isCameraConnected(): Boolean
    external fun listCameraAbilities(): String
    external fun requestCapture()
    external fun stopListenCameraEvents()
    external fun stopListenCameraEventsAsync(callback: EventListenerStopCallback)
    external fun cameraAutoDetect(): String
    external fun buildWidgetJson(): String
    // 라이브뷰 노출 스트립용 경량 조회(필요 노출 프로퍼티만, 캐시 없음). 폴링용.
    external fun getLiveExposureJson(): String
    external fun queryConfig()

    // 범용 설정 접근 (C++ 구현은 camera_config.cpp에 존재)
    external fun getConfigString(key: String): String?
    external fun setConfigString(key: String, value: String): Int
    external fun getConfigInt(key: String): Int
    external fun setConfigInt(key: String, value: Int): Int

    external fun getSupportedCameras(): Array<String>?
    external fun getCameraDetails(model: String): Array<String>?

    // --- 진단 및 문제 해결 함수들 (범용) ---
    external fun diagnoseCameraIssues(): String
    external fun diagnoseUSBConnection(): String

    // --- 라이브뷰 관련 ---
    external fun startLiveView(callback: LiveViewCallback)
    external fun stopLiveView()
    external fun autoFocus(): Int

    // --- 파일 관리 관련 ---
    external fun getCameraFileList(): String
    external fun getCameraFileListPaged(page: Int, pageSize: Int): String  // 페이징 지원
    external fun getLatestCameraFile(): String  // 최신 파일만 가져오기 (촬영 후 사용)
    external fun invalidateFileCache()  // 캐시 무효화
    external fun getCameraThumbnail(photoPath: String): ByteArray?

    /**
     * 다수 경로의 썸네일을 단일 카메라 락 보유 상태에서 순회 처리한다.
     *
     * 갤러리 첫 페이지처럼 N장을 빠르게 미리 채워야 할 때 사용한다.
     * - JNI 진입 1회 + 카메라 락 1회 획득으로 락 경합 / JNI 경계 비용 절감.
     * - 경로 1건당 [ThumbnailBatchCallback.onThumbnail]이 호출되며 실패 시 `data=null`.
     * - 호출 스레드는 본 함수 호출 스레드와 동일하므로 IO 디스패처에서 호출할 것.
     */
    external fun getCameraThumbnailBatch(
        paths: Array<String>,
        callback: ThumbnailBatchCallback
    )

    external fun getCameraPhotoExif(photoPath: String): String? // EXIF 정보를 JSON 문자열로 반환
    external fun downloadCameraPhoto(photoPath: String): ByteArray?

    // 빠른 경로: ObjectHandle 기반 다운로드 및 캐시/매핑 API
    external fun downloadByObjectHandle(handle: Long): ByteArray?
    external fun setHandlePathMapping(handle: Long, path: String)
    external fun clearHandlePathMapping()
    external fun getObjectInfoCached(path: String): String

    // 최근 촬영 경로 조회/초기화 (이벤트 기반 빠른 접근)
    external fun getRecentCapturedPaths(maxCount: Int): Array<String>?
    external fun clearRecentCapturedPaths()

    // PTP/IP 연결 안정성을 위한 함수들
    external fun clearPtpipSettings(): Boolean // libgphoto2의 ptp2_ip 설정을 모두 삭제하여 새로운 GUID 생성 강제
    external fun resetPtpipGuid(): Boolean // GUID만 특별히 초기화
    external fun setPtpipGuid(guid: String): Boolean // 지정 GUID를 libgphoto2 ptp2_ip/guid에 주입 (Nikon STA 페어링 GUID 일치용)

    // PtpipConnectionManager에서 받은 카메라 정보를 libgphoto2에 전달
    external fun setCameraInfoFromPtpip(
        manufacturer: String,
        model: String,
        version: String,
        serial: String
    ): Int

    /**
     * PTP-IP 초기화 변형 — 디바이스 정보(제조사/모델/버전/시리얼) 함께 전달.
     */
    external fun initCameraWithDeviceInfo(
        ipAddress: String,
        port: Int,
        libDir: String,
        manufacturer: String,
        model: String,
        version: String,
        serial: String
    ): Int

    /**
     * Nikon STA 인증 직후 libgphoto2 초기화를 이어 수행하는 변형.
     */
    external fun initCameraAfterNikonSta(ipAddress: String, port: Int, libDir: String): Int

    // ===== Nikon STA 인증 (camera_nikon_auth.cpp) =====

    /**
     * Nikon STA 모드 Phase 1 인증. libgphoto2 초기화 전에 호출.
     * 성공 시 GUID 저장 + 소켓 유지.
     */
    external fun performNikonStaAuth(ipAddress: String, port: Int): Boolean

    /**
     * libgphoto2 경유 Nikon STA 인증(현재 stub: "OK" 반환).
     */
    external fun performNikonStaAuthWithLibgphoto2(): String?

    /** 저장된 Nikon 인증 GUID 조회. 미설정 시 빈 문자열. */
    external fun getNikonAuthGuid(): String?

    /** Nikon 인증 소켓 유지 중인지 여부. */
    external fun hasNikonAuthSockets(): Boolean

    /** Nikon 인증 소켓 수동 정리. */
    external fun closeNikonAuthSockets()

    // 연결 타입 감지 및 세션 관리
    external fun maintainSessionForStaMode(): Int

    // 로그 파일 관련 함수들
    external fun closeLogFile()
    external fun getLogFilePath(): String

    // libgphoto2 설정 파일 관리 함수들
    external fun readGphotoSettings(): String   // 설정 파일 내용 읽기
    external fun deleteGphotoSettings(): String  // 설정 파일 삭제 (카메라별 독립 설정을 위해)

    /**
     * libgphoto2 로그 레벨을 설정한다.
     *
     * 네이티브 측은 `gp_log_add_func((GPLogLevel)level, ...)` 로 등록하며,
     * libgphoto2의 GPLogLevel enum (gphoto2-port-log.h) 정의는 다음과 같다.
     *  - `0` = `GP_LOG_ERROR`   (error만)
     *  - `1` = `GP_LOG_VERBOSE` (error + verbose)
     *  - `2` = `GP_LOG_DEBUG`   (error + verbose + debug)
     *  - `3` = `GP_LOG_DATA`    (전체. 헥스덤프 포함, 매우 verbose)
     *
     * 상수는 위 `GP_LOG_*` 필드 (`GP_LOG_ALL == GP_LOG_DATA`) 를 사용한다.
     * 별도의 NONE 레벨은 없으므로, 로그를 완전히 끄려면 [stopLogFile] 등
     * 콜백 해제 API 를 사용해야 한다.
     */
    external fun setLogLevel(level: Int): Boolean

    /**
     * ⚠️ logcat verbose 출력을 켠다(`g_logcatVerbose=true`). DEBUG/VERBOSE가 logcat에 쏟아진다.
     * 자동/시작/연결 경로에서 호출 금지 — 수동 디버깅용 escape hatch 전용.
     * 파일 로그는 [startLogFile]+[setLogLevel](레벨만 올림, logcat 영향 없음)을 쓴다.
     */
    external fun enableVerboseLogging(enabled: Boolean): Boolean

    /** ⚠️ [enableVerboseLogging]와 동일 경고: logcat verbose를 켠다. 수동 디버깅 전용. */
    external fun enableDebugLogging(enabled: Boolean): Boolean

    // 로그 파일 제어 함수들
    external fun startLogFile(filePath: String): Boolean
    external fun stopLogFile(): Boolean
    external fun getLogFileContent(filePath: String): String
    external fun isLogFileActive(): Boolean

    // 카메라 초기화 상태 확인
    external fun isCameraInitialized(): Boolean

    // **카메라 Abilities 조회 (libgphoto2 API)**
    /**
     * 현재 연결된 카메라의 Abilities 정보를 JSON으로 반환
     *
     * @return JSON 문자열: {
     *   "model": "Canon EOS R5",
     *   "manufacturer": "Canon Inc.",
     *   "operations": 127,  // 비트 마스크
     *   "file_operations": 62,  // 비트 마스크
     *   "folder_operations": 15,  // 비트 마스크
     *   "port_type": 4,  // GP_PORT_USB 또는 GP_PORT_PTPIP
     *   "status": "PRODUCTION",
     *   "usb_vendor": "0x04a9",
     *   "usb_product": "0x32f0",
     *   "supports": {
     *     "capture_image": true,
     *     "capture_video": true,
     *     "capture_preview": true,
     *     "trigger_capture": true,
     *     "config": true,
     *     "liveview": true,
     *     "delete": true,
     *     "raw": true,
     *     "exif": true
     *   }
     * }
     */
    external fun getCameraAbilities(): String?

    /**
     * 특정 기능 지원 여부 확인
     *
     * @param operation 기능 이름:
     *   - "capture_image" (GP_OPERATION_CAPTURE_IMAGE)
     *   - "capture_video" (GP_OPERATION_CAPTURE_VIDEO)
     *   - "capture_preview" (GP_OPERATION_CAPTURE_PREVIEW)
     *   - "trigger_capture" (GP_OPERATION_TRIGGER_CAPTURE)
     *   - "config" (GP_OPERATION_CONFIG)
     *   - "delete" (GP_FILE_OPERATION_DELETE)
     *   - "raw" (GP_FILE_OPERATION_RAW)
     *   - "exif" (GP_FILE_OPERATION_EXIF)
     * @return 지원 여부
     */
    external fun supportsOperation(operation: String): Boolean

    /**
     * 카메라 제조사 및 모델 정보 조회
     *
     * @return JSON 문자열: {
     *   "manufacturer": "Canon Inc.",
     *   "model": "Canon EOS R5",
     *   "version": "3-1.1.2",
     *   "serial_number": "1234567890"
     * }
     */
    external fun getCameraDeviceInfo(): String?

    // **글로벌 작업 중단 제어 함수들**
    external fun cancelAllOperations()      // 모든 네이티브 작업 즉시 중단
    external fun resumeOperations()         // 네이티브 작업 재개
    external fun isOperationCanceled(): Boolean  // 현재 중단 상태 확인

    // 네이티브 에러 콜백 등록
    external fun setErrorCallback(callback: NativeErrorCallback?)

    // 구독 티어 관리 (0: FREE, 1: PREMIUM, 2: PRO)
    external fun setSubscriptionTier(tier: Int)
    external fun getSubscriptionTier(): Int

    external fun setRawFileDownloadEnabled(enabled: Boolean)
    external fun isRawFileDownloadEnabled(): Boolean

    external fun initializeCameraCache() // 카메라 캐시 초기화

    // ===== 네이티브 캐시 무효화 / 진단 =====

    /** 카메라 감지 캐시 무효화 (camera_detection.cpp). */
    external fun invalidateDetectionCache()

    /** 카메라 abilities 캐시 무효화 (camera_samples_impl.cpp). */
    external fun invalidateAbilitiesCache()

    /** 라이브뷰 캐시 무효화 (camera_config.cpp). */
    external fun invalidateLiveViewCache()

    /** 감지 캐시 hit/miss 통계 문자열 조회. */
    external fun getCacheStatistics(): String?

    // ===== 카메라 설정 일괄/단일 조회 (camera_events.cpp) =====

    /** 모든 카메라 설정값을 JSON 문자열로 반환. */
    external fun getAllCameraSettings(): String?

    /** 특정 설정의 현재 값 조회. 미지원 시 "error"/"unknown". */
    external fun getCameraSetting(settingName: String): String?

    // ===== 파일 삭제 변형 (camera_files.cpp) =====

    /** 카메라 사진 삭제(절대 경로). 성공 여부 반환. */
    external fun deleteCameraPhoto(photoPath: String): Boolean

    // ===== Canon 캡처 / 캡처 타깃 =====

    /** Canon 캡처 모드 활성/비활성 (camera_config.cpp). 결과 메시지 반환. */
    external fun enableCanonCapture(enable: Boolean): String?

    /** 캡처 타깃 설정 (0: internal, 1: card 등). GP 에러 코드 반환. */
    external fun setCaptureTarget(targetMode: Int): Int

    // ===== 고급 촬영 기능 (새로 추가) =====

    // Trigger Capture - 카메라가 자체적으로 캡처하도록 트리거
    external fun triggerCapture(): Int

    // Bulb 모드 - 장노출 촬영
    external fun startBulbCapture(): Int
    external fun endBulbCapture(): Int
    external fun bulbCaptureWithDuration(seconds: Int): Int

    // 비디오 녹화
    external fun startVideoRecording(): Int
    external fun stopVideoRecording(): Int
    external fun isVideoRecording(): Boolean

    // 인터벌 촬영 / 타임랩스
    external fun startIntervalCapture(intervalSeconds: Int, totalFrames: Int): Int
    external fun stopIntervalCapture(): Int
    external fun getIntervalCaptureStatus(): IntArray // [isRunning, capturedFrames, totalFrames]

    // 고급 AF 기능
    external fun setAFMode(mode: String): Int
    external fun getAFMode(): String?
    external fun setAFArea(x: Int, y: Int, width: Int, height: Int): Int
    external fun driveManualFocus(steps: Int): Int

    // Hook 이벤트 콜백 인터페이스
    interface HookEventCallback {
        fun onHookEvent(action: String, argument: String)
    }

    external fun registerHookCallback(callback: HookEventCallback): Int
    external fun unregisterHookCallback()

    // ===== 카메라별 고급 설정 =====

    // Canon EOS 전용 설정
    external fun setCanonColorTemperature(kelvin: Int): Int
    external fun getCanonColorTemperature(): Int // 오류 시 -1
    external fun setCanonPictureStyle(style: String): Int
    external fun setCanonWhiteBalanceAdjust(adjustBA: Int, adjustGM: Int): Int

    // Nikon DSLR 전용 설정
    external fun setNikonActiveSlot(slot: String): Int
    external fun setNikonVideoMode(enable: Boolean): Int
    external fun setNikonExposureDelayMode(enable: Boolean): Int
    external fun getNikonBatteryLevel(): Int // 오류 시 -1

    // Sony Alpha 전용 설정
    external fun setSonyFocusArea(area: String): Int
    external fun setSonyLiveViewEffect(enable: Boolean): Int
    external fun setSonyManualFocusing(steps: Int): Int

    // Fuji X 전용 설정
    external fun setFujiFilmSimulation(simulation: String): Int
    external fun setFujiColorSpace(colorSpace: String): Int
    external fun getFujiShutterCounter(): Int // 오류 시 -1

    // Panasonic Lumix 전용 설정
    external fun setPanasonicMovieRecording(enable: Boolean): Int
    external fun setPanasonicManualFocusDrive(steps: Int): Int

    // ===== PTP 1.1 Streaming =====
    external fun startPTPStreaming(): Int
    external fun stopPTPStreaming(): Int
    external fun getPTPStreamFrame(): ByteArray?
    external fun setPTPStreamingParameters(width: Int, height: Int, fps: Int): Int

    // ===== RAW 파일 특별 처리 =====
    external fun downloadRawFile(folder: String, filename: String): ByteArray?
    external fun downloadAllRawFiles(folder: String): Int
    external fun extractRawMetadata(folder: String, filename: String): String?
    external fun extractRawThumbnail(folder: String, filename: String): ByteArray?
    external fun captureDualMode(keepRawOnCard: Boolean, downloadJpeg: Boolean): Int
    external fun filterRawFiles(folder: String, minSizeMB: Int, maxSizeMB: Int): Array<String>?

    /**
     * 에러 핸들러 JNI 함수들
     */
    external fun getErrorHistory(count: Int): String
    external fun clearErrorHistory()

    /**
     * 메모리 풀 상태 조회 JNI 함수들
     */
    external fun getCameraFilePoolCount(): Int
    external fun clearCameraFilePool()
    external fun getMemoryPoolStatus(): String

    /**
     * Mock Camera (가상 카메라) JNI 함수들
     * ADMIN 티어 전용 개발/테스트 기능
     */
    external fun enableMockCamera(enable: Boolean): Boolean
    external fun isMockCameraEnabled(): Boolean
    external fun setMockCameraModel(manufacturer: String, model: String): Boolean
    external fun getMockCameraModel(): String
    external fun setMockCameraImages(imagePaths: Array<String>): Boolean
    external fun addMockCameraImage(imagePath: String): Boolean
    external fun clearMockCameraImages(): Boolean
    external fun getMockCameraImageCount(): Int
    external fun setMockCameraDelay(delayMs: Int): Boolean
    external fun getMockCameraDelay(): Int
    external fun simulateCameraError(errorCode: Int, errorMessage: String): Boolean
    external fun setMockCameraAutoCapture(enable: Boolean, intervalMs: Int): Boolean
    external fun getMockCameraInfo(): String

    // ===== libgphoto2 확장 API (새로 추가) =====

    /**
     * 모든 설정 목록을 플랫 리스트로 조회 (gp_camera_list_config)
     * @return JSON: {"count": N, "configs": ["config1", "config2", ...]}
     */
    external fun listAllConfigs(): String

    /**
     * 카메라 매뉴얼 정보 조회 (gp_camera_get_manual)
     * @return JSON: {"manual": "..."}
     */
    external fun getCameraManual(): String

    /**
     * 폴더 내 모든 파일 삭제 (gp_camera_folder_delete_all)
     * @param folder 폴더 경로 (예: "/store_00010001/DCIM/100NIKON")
     * @return 성공 여부
     */
    external fun deleteAllFilesInFolder(folder: String): Boolean

    /**
     * 파일 부분 읽기 - 대용량 파일용 (gp_camera_file_read)
     * @param path 파일 경로
     * @param offset 시작 위치 (바이트)
     * @param size 읽을 크기 (바이트)
     * @return 읽은 데이터
     */
    external fun readFileChunk(path: String, offset: Long, size: Int): ByteArray?

    /**
     * 카메라에 파일 업로드 (gp_camera_folder_put_file)
     * @param folder 대상 폴더
     * @param filename 파일명
     * @param data 업로드할 데이터
     * @return 성공 여부
     */
    external fun uploadFileToCamera(folder: String, filename: String, data: ByteArray): Boolean

    /**
     * 카메라에 폴더 생성 (gp_camera_folder_make_dir)
     * @param parentFolder 상위 폴더
     * @param folderName 생성할 폴더명
     * @return 성공 여부
     */
    external fun createCameraFolder(parentFolder: String, folderName: String): Boolean

    /**
     * 카메라 폴더 삭제 (gp_camera_folder_remove_dir)
     * @param parentFolder 상위 폴더
     * @param folderName 삭제할 폴더명
     * @return 성공 여부
     */
    external fun removeCameraFolder(parentFolder: String, folderName: String): Boolean

    /**
     * 상세 스토리지 정보 조회 (gp_camera_get_storageinfo 확장)
     * @return JSON: {
     *   "count": N,
     *   "storages": [{
     *     "basedir": "/",
     *     "label": "SD Card",
     *     "description": "...",
     *     "type": "removable_ram",
     *     "access": "readwrite",
     *     "capacityKB": 123456789,
     *     "freeKB": 12345678,
     *     "freeImages": 1234
     *   }, ...]
     * }
     */
    external fun getDetailedStorageInfo(): String

    // 카메라 about 정보 (C++에서 구현 예정)
    external fun getCameraAbout(): String

    // ===== Widget 확장: 노출 보정 / 배터리 / 스토리지 / 파일 삭제 =====

    /** 현재 노출 보정(EV) 값 — exposurecompensation widget value, 미지원 시 null */
    external fun getExposureCompensationNative(): String?

    /** 노출 보정(EV) 값을 설정. 반환은 libgphoto2 GP 에러 코드(GP_OK=0) */
    external fun setExposureCompensationNative(value: String): Int

    /** 카메라가 지원하는 EV 선택지 목록(예: "-2", "-5/3", "0", "+1/3"). 미지원 시 null */
    external fun listExposureCompensationOptionsNative(): Array<String>?

    /** 배터리 레벨 widget 값. "50%" 등 카메라가 반환하는 raw 문자열. 미지원 시 null */
    external fun getBatteryLevelNative(): String?

    /** 첫 스토리지의 [totalKb, freeKb, imagesFree]. 미지원/실패 시 null */
    external fun getStorageInfoNative(): LongArray?

    /** 카메라 내 파일 삭제. 반환은 libgphoto2 GP 에러 코드(GP_OK=0) */
    external fun deleteCameraFileNative(folder: String, filename: String): Int

    // 오디오 캡처 (C++에서 구현 예정)
    external fun captureAudio(): Int

    // 파일 정보 설정 (C++에서 구현 예정)
    external fun setFileInfo(folder: String, filename: String, permissions: Int, mtime: Long): Int

    // Context progress 콜백 (C++에서 구현 예정)
    external fun setProgressCallback(enabled: Boolean): Int

    // Context status 콜백 (C++에서 구현 예정)
    external fun setStatusCallback(enabled: Boolean): Int

    // Context cancel 콜백 (C++에서 구현 예정)
    external fun setCancelCallback(enabled: Boolean): Int

}