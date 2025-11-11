package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.CameraNative
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.PtpDeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 카메라 기능 정보 화면 ViewModel
 *
 * ADMIN 전용 - libgphoto2 API로 조회한 카메라 기능을 표시
 */
@HiltViewModel
class CameraAbilitiesViewModel @Inject constructor(
    private val nativeDataSource: NativeCameraDataSource,
    private val ptpipDataSource: PtpipDataSource
) : ViewModel() {

    private val _abilities = MutableStateFlow<CameraAbilitiesInfo?>(null)
    val abilities: StateFlow<CameraAbilitiesInfo?> = _abilities.asStateFlow()

    private val _deviceInfo = MutableStateFlow<PtpDeviceInfo?>(null)
    val deviceInfo: StateFlow<PtpDeviceInfo?> = _deviceInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    companion object {
        private const val TAG = "CameraAbilitiesViewModel"
    }

    init {
        loadAbilities()
    }

    /**
     * 카메라 기능 정보 로드
     */
    private fun loadAbilities() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 카메라 초기화 확인
                if (!CameraNative.isCameraInitialized()) {
                    _errorMessage.value = "카메라가 연결되지 않았습니다"
                    _isLoading.value = false
                    return@launch
                }

                // USB 연결인지 PTPIP 연결인지 확인
                val usbAbilities = nativeDataSource.getUsbCameraAbilities()
                val ptpipAbilities = ptpipDataSource.getCurrentAbilities()

                val abilities = usbAbilities ?: ptpipAbilities

                if (abilities == null) {
                    // 직접 조회 시도
                    val abilitiesJson = CameraNative.getCameraAbilities()
                    val deviceInfoJson = CameraNative.getCameraDeviceInfo()

                    if (abilitiesJson != null && deviceInfoJson != null) {
                        _abilities.value = parseAbilities(abilitiesJson)
                        _deviceInfo.value = parseDeviceInfo(deviceInfoJson)
                        Log.i(TAG, "카메라 기능 정보 조회 성공")
                    } else {
                        _errorMessage.value = "카메라 기능 정보를 조회할 수 없습니다"
                    }
                } else {
                    // 캐시된 정보 사용
                    _abilities.value = abilities
                    _deviceInfo.value = nativeDataSource.getUsbCameraDeviceInfo()
                        ?: ptpipDataSource.getCurrentDeviceInfo()
                    Log.i(TAG, "캐시된 카메라 기능 정보 사용")
                }

            } catch (e: Exception) {
                Log.e(TAG, "카메라 기능 정보 로드 실패", e)
                _errorMessage.value = "오류: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 새로고침
     */
    fun refresh() {
        loadAbilities()
    }

    /**
     * Abilities JSON 파싱
     */
    private fun parseAbilities(json: String): CameraAbilitiesInfo {
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
            supports = com.inik.camcon.domain.model.CameraSupports(
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
    }

    /**
     * DeviceInfo JSON 파싱
     */
    private fun parseDeviceInfo(json: String): PtpDeviceInfo {
        val obj = org.json.JSONObject(json)
        return PtpDeviceInfo(
            manufacturer = obj.getString("manufacturer"),
            model = obj.getString("model"),
            version = obj.getString("version"),
            serialNumber = obj.getString("serial_number")
        )
    }
}
