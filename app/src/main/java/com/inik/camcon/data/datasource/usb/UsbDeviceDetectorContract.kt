package com.inik.camcon.data.datasource.usb

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * USB 디바이스 감지 및 권한 관리의 공개 계약 인터페이스
 */
interface UsbDeviceDetectorContract {
    /**
     * 현재 연결된 USB 카메라 디바이스 목록
     */
    val connectedDevices: StateFlow<List<UsbDevice>>

    /**
     * 현재 USB 카메라에 대한 권한 여부
     */
    val hasPermission: StateFlow<Boolean>

    /**
     * USB 권한 관련 오류 메시지 (없으면 null)
     */
    val permissionError: StateFlow<String?>

    /**
     * 카메라 디바이스 목록 조회
     */
    fun getCameraDevices(): List<UsbDevice>

    /**
     * 지정된 USB 디바이스에 대한 권한 요청
     */
    fun requestPermission(device: UsbDevice)

    /**
     * USB 권한이 승인되었을 때 호출될 콜백 설정
     */
    fun setPermissionGrantedCallback(callback: (UsbDevice) -> Unit)

    /**
     * USB 디바이스가 분리되었을 때 호출될 콜백 설정
     */
    fun setDisconnectionCallback(callback: (UsbDevice) -> Unit)

    /**
     * 리소스 정리
     */
    fun cleanup()
}
