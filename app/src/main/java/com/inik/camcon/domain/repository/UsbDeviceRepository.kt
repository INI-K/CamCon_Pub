package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.UsbDeviceInfo
import kotlinx.coroutines.flow.StateFlow

interface UsbDeviceRepository {
    fun getCameraDevices(): List<UsbDeviceInfo>
    fun requestPermission(deviceId: String)

    // USB 카메라 연결 관리용 메서드
    val connectedDeviceCount: StateFlow<Int>
    val hasUsbPermission: StateFlow<Boolean>
    val isNativeCameraConnected: StateFlow<Boolean>
    fun requestPermissionForFirstDevice()

    /**
     * 부착된 카메라 중 지금 이 순간 USB 권한을 가진 장치가 있는가.
     *
     * [hasUsbPermission] 은 브로드캐스트를 받아 갱신되는 상태 흐름이라 프로세스가 막 되살아난
     * 시점에는 아직 false 로 남아 있다. 그 값을 믿고 복구를 포기하면 권한이 있는데도 재연결을
     * 못 하므로, 이 함수는 시스템에 직접 물어 지금의 사실을 돌려준다.
     */
    fun hasPermissionForAttachedCamera(): Boolean
    suspend fun connectToFirstCamera()
    fun getCurrentDeviceInfo(): UsbDeviceInfo?
    suspend fun checkPowerStateAndTest()
    fun cleanup()
}
