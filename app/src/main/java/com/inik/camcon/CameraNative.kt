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

    // 안전한 네이티브 메서드 호출을 위한 래퍼 함수들
    fun safeTestLibraryLoad(): String {
        ensureLibrariesLoaded()
        return testLibraryLoad()
    }

    fun safeGetLibGphoto2Version(): String {
        ensureLibrariesLoaded()
        return getLibGphoto2Version()
    }

    fun safeInitCamera(): String {
        ensureLibrariesLoaded()
        return initCamera()
    }

    fun safeInitCameraWithFd(fd: Int, nativeLibDir: String): Int {
        ensureLibrariesLoaded()
        return initCameraWithFd(fd, nativeLibDir)
    }

    fun safeCapturePhoto(): Int {
        ensureLibrariesLoaded()
        return capturePhoto()
    }

    fun safeGetCameraSummary(): String {
        ensureLibrariesLoaded()
        return getCameraSummary()
    }

    fun safeCloseCamera(): String {
        ensureLibrariesLoaded()
        return closeCamera()
    }
}