package com.inik.camcon

import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.nativesource.LiveViewCallback

// 네이티브 에러 콜백 인터페이스
interface NativeErrorCallback {
    fun onNativeError(errorCode: Int, errorMessage: String)
}

object CameraNative {
    // libgphoto2 로그 레벨 상수들
    const val GP_LOG_ERROR = 0
    const val GP_LOG_VERBOSE = 1
    const val GP_LOG_DEBUG = 2
    const val GP_LOG_DATA = 3
    const val GP_LOG_ALL = GP_LOG_DATA

    init {
        System.loadLibrary("gphoto2_port") // Port 라이브러리 먼저
        System.loadLibrary("gphoto2_port_iolib_disk")
        System.loadLibrary("gphoto2_port_iolib_usb1") // "lib" prefix와 ".so" 확장자 없이 호출

        // gphoto2 port 라이브러리 및 I/O 모듈 로드 (순서 중요)
        // 일반적인 의존성은 port -> iolib -> gphoto2

        // gphoto2 메인 라이브러리 로드
        System.loadLibrary("gphoto2")

        // 애플리케이션 JNI 라이브러리 로드 (가장 마지막에)
        System.loadLibrary("native-lib")
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
}