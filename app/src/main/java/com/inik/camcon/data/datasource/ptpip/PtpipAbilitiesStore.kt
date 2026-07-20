package com.inik.camcon.data.datasource.ptpip

import android.util.Log
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.CameraSupports
import com.inik.camcon.domain.model.PtpDeviceInfo
import com.inik.camcon.domain.model.PtpipCameraInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PTP/IP 카메라 Abilities·DeviceInfo 보관 협력자 (PtpipDataSource에서 분리).
 *
 * 연결 성공 시 파싱된 Abilities/DeviceInfo를 보관하고, UI 관찰용 [cameraInfo] StateFlow와
 * 도메인 상태 관찰자([CameraStateObserver])를 갱신한다. 연결 엔진(파사드)과 공유 상태가 없어
 * 독립적으로 이동 가능한 순수 보관/변환 책임이다.
 */
internal class PtpipAbilitiesStore(
    private val cameraStateObserver: CameraStateObserver
) {
    private companion object {
        private const val TAG = "PtpipDataSource"
    }

    private var currentAbilities: CameraAbilitiesInfo? = null
    private var currentDeviceInfo: PtpDeviceInfo? = null

    private val _cameraInfo = MutableStateFlow<PtpipCameraInfo?>(null)
    val cameraInfo: StateFlow<PtpipCameraInfo?> = _cameraInfo.asStateFlow()

    /**
     * Abilities JSON 파싱
     */
    fun parseAbilities(json: String): CameraAbilitiesInfo {
        try {
            val obj = org.json.JSONObject(json)
            val supportsObj = obj.getJSONObject("supports")

            return CameraAbilitiesInfo(
                model = obj.getString("model"),
                status = obj.getString("status"),
                portType = obj.getInt("port_type"),
                usbVendor = obj.getString("usb_vendor"),
                usbProduct = obj.getString("usb_product"),
                usbClass = obj.getInt("usb_class"),
                operations = obj.getInt("operations"),
                fileOperations = obj.getInt("file_operations"),
                folderOperations = obj.getInt("folder_operations"),
                supports = CameraSupports(
                    captureImage = supportsObj.getBoolean("capture_image"),
                    captureVideo = supportsObj.getBoolean("capture_video"),
                    captureAudio = supportsObj.getBoolean("capture_audio"),
                    capturePreview = supportsObj.getBoolean("capture_preview"),
                    triggerCapture = supportsObj.getBoolean("trigger_capture"),
                    config = supportsObj.getBoolean("config"),
                    delete = supportsObj.getBoolean("delete"),
                    preview = supportsObj.getBoolean("preview"),
                    raw = supportsObj.getBoolean("raw"),
                    audio = supportsObj.getBoolean("audio"),
                    exif = supportsObj.getBoolean("exif"),
                    deleteAll = supportsObj.getBoolean("delete_all"),
                    putFile = supportsObj.getBoolean("put_file"),
                    makeDir = supportsObj.getBoolean("make_dir"),
                    removeDir = supportsObj.getBoolean("remove_dir")
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Abilities 파싱 실패", e)
            throw e
        }
    }

    /**
     * Abilities 및 DeviceInfo 저장
     */
    fun storeAbilities(abilities: CameraAbilitiesInfo, deviceInfo: PtpDeviceInfo) {
        currentAbilities = abilities
        currentDeviceInfo = deviceInfo

        // 기존 PtpipCameraInfo도 업데이트
        _cameraInfo.value = PtpipCameraInfo(
            manufacturer = deviceInfo.manufacturer,
            model = deviceInfo.model,
            version = deviceInfo.version,
            serialNumber = deviceInfo.serialNumber
        )

        // UI 상태 업데이트 (PTPIP도 동일하게)
        try {
            cameraStateObserver.updateCameraAbilities(abilities)
            Log.i(TAG, "✅ PTPIP 연결 - UI 상태 업데이트 완료")
        } catch (e: Exception) {
            Log.e(TAG, "UI 상태 업데이트 실패", e)
        }
    }

    /**
     * 현재 카메라 Abilities 조회
     */
    fun getCurrentAbilities(): CameraAbilitiesInfo? = currentAbilities

    /**
     * 현재 카메라 DeviceInfo 조회
     */
    fun getCurrentDeviceInfo(): PtpDeviceInfo? = currentDeviceInfo

    /**
     * 연결 해제 시 cameraInfo 초기화 (disconnectInternal의 `_cameraInfo.value = null` 대응).
     * currentAbilities/currentDeviceInfo는 기존 동작과 동일하게 유지한다(원본도 리셋하지 않았음).
     */
    fun clearCameraInfo() {
        _cameraInfo.value = null
    }
}
