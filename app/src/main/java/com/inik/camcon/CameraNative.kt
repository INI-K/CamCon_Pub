package com.inik.camcon

import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.nativesource.LiveViewCallback

object CameraNative {
    init {
        // 필수 라이브러리 먼저 로드
        System.loadLibrary("usb") // libusb는 gphoto2_port보다 먼저 로드되어야 할 수 있음
        System.loadLibrary("gphoto2_port_iolib_disk")
        System.loadLibrary("gphoto2_port_iolib_usb1") // "lib" prefix와 ".so" 확장자 없이 호출
        System.loadLibrary("gphoto2_port") // Port 라이브러리 먼저

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
    external fun initCameraWithFd(fd: Int, nativeLibDir: String): Int
    external fun listenCameraEvents(callback: CameraCaptureListener)
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
    external fun invalidateFileCache()  // 캐시 무효화
    external fun getCameraThumbnail(photoPath: String): ByteArray?
    external fun downloadCameraPhoto(photoPath: String): ByteArray?
    external fun deleteCameraPhoto(photoPath: String): Boolean

    // PTP/IP 연결 안정성을 위한 함수들
    external fun clearPtpipSettings(): Boolean // libgphoto2의 ptp2_ip 설정을 모두 삭제하여 새로운 GUID 생성 강제
    external fun waitBeforeRetry(ms: Int): Boolean // 네이티브에서 정확한 타이밍으로 대기
    external fun resetPtpipGuid(): Boolean // GUID만 특별히 초기화
    external fun setPtpipVerbose(enabled: Boolean): Boolean // PTPIP 디버그 로그 활성화

    // Nikon 전용 PTP 명령어 전송 함수 추가
    external fun sendNikonCommand(operationCode: Int): Int
    external fun sendNikonCommands(): Int

    // gphoto2 호환성을 위한 함수들 추가
    external fun isGphoto2Available(): Boolean
    external fun waitForGphoto2Access(timeoutMs: Int): Boolean
    external fun releaseForGphoto2(): Boolean
}