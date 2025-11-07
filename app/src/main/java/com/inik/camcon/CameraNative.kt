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
            Log.d(TAG, "✅ 모든 네이티브 라이브러리 로딩 완료")
        } catch (e: Throwable) {
            Log.e(TAG, "🔴 네이티브 라이브러리 로딩 실패", e)
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

        for ((libName, description) in libraries) {
            try {
                Log.d(TAG, "📦 $description 로딩 중...")
                System.loadLibrary(libName)
                Log.d(TAG, "✅ $description 로딩 완료")

                // 각 라이브러리 로딩 후 약간의 대기 시간 (릴리즈 모드 안정성)
                Thread.sleep(10)

            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "🔴 $description 로딩 실패: $libName", e)
                throw RuntimeException("$description 로딩 실패: ${e.message}", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "🔴 $description 로딩 권한 오류: $libName", e)
                throw RuntimeException("$description 로딩 권한 오류: ${e.message}", e)
            }
        }
    }

    /**
     * 라이브러리 로딩 상태 확인
     */
    fun isLibrariesLoaded(): Boolean = librariesLoaded

    /**
     * 네이티브 메서드 호출 전 라이브러리 로딩 상태 확인
     */
    private fun ensureLibrariesLoaded() {
        if (!librariesLoaded) {
            throw IllegalStateException("네이티브 라이브러리가 로딩되지 않았습니다. 앱을 재시작해주세요.")
        }
    }

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
    external fun cameraAutoDetect(): String
    external fun buildWidgetJson(): String
    external fun queryConfig()

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
    external fun getCameraPhotoExif(photoPath: String): String? // EXIF 정보를 JSON 문자열로 반환
    external fun downloadCameraPhoto(photoPath: String): ByteArray?

    // Fast Path: ObjectHandle 기반 다운로드 및 캐시/매핑 API
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

    // PtpipConnectionManager에서 받은 카메라 정보를 libgphoto2에 전달
    external fun setCameraInfoFromPtpip(
        manufacturer: String,
        model: String,
        version: String,
        serial: String
    ): Int

    // Connection type detection and session management
    external fun maintainSessionForStaMode(): Int

    // 로그 파일 관련 함수들
    external fun closeLogFile()
    external fun getLogFilePath(): String

    // libgphoto2 로그 레벨 설정 함수 추가
    external fun setLogLevel(level: Int): Boolean
    external fun enableVerboseLogging(enabled: Boolean): Boolean
    external fun enableDebugLogging(enabled: Boolean): Boolean

    // 카메라 초기화 상태 확인
    external fun isCameraInitialized(): Boolean

    // **글로벌 작업 중단 제어 함수들**
    external fun cancelAllOperations()      // 모든 네이티브 작업 즉시 중단
    external fun resumeOperations()         // 네이티브 작업 재개
    external fun isOperationCanceled(): Boolean  // 현재 중단 상태 확인

    // 네이티브 에러 콜백 등록
    external fun setErrorCallback(callback: NativeErrorCallback?)

    // 구독 티어 관리 (0: FREE, 1: PREMIUM, 2: PRO)
    external fun setSubscriptionTier(tier: Int)
    external fun getSubscriptionTier(): Int

    external fun initializeCameraCache() // 카메라 캐시 초기화

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
    external fun getCanonColorTemperature(): Int // -1 on error
    external fun setCanonPictureStyle(style: String): Int
    external fun setCanonWhiteBalanceAdjust(adjustBA: Int, adjustGM: Int): Int

    // Nikon DSLR 전용 설정
    external fun setNikonActiveSlot(slot: String): Int
    external fun setNikonVideoMode(enable: Boolean): Int
    external fun setNikonExposureDelayMode(enable: Boolean): Int
    external fun getNikonBatteryLevel(): Int // -1 on error

    // Sony Alpha 전용 설정
    external fun setSonyFocusArea(area: String): Int
    external fun setSonyLiveViewEffect(enable: Boolean): Int
    external fun setSonyManualFocusing(steps: Int): Int

    // Fuji X 전용 설정
    external fun setFujiFilmSimulation(simulation: String): Int
    external fun setFujiColorSpace(colorSpace: String): Int
    external fun getFujiShutterCounter(): Int // -1 on error

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

    // ===== 라이브러리 상태 관리 =====

}