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
    external fun listenCameraEvents(callback: CameraCaptureListener)
    external fun initCameraWithFd(fd: Int, nativeLibDir: String): Int
    external fun capturePhoto(): Int
    external fun capturePhotoAsync(callback: CameraCaptureListener, saveDir: String)
    external fun getCameraSummary(): String
    external fun closeCamera()
    external fun detectCamera(): String
    external fun isCameraConnected(): Boolean
    //    external fun listCameraCapabilities(): String
    external fun listCameraAbilities(): String
    external fun requestCapture()
    //    external fun startListenCameraEvents(callback: CameraCaptureListener)
    external fun stopListenCameraEvents()
    external fun cameraAutoDetect():String
    external fun buildWidgetJson():String
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
}
