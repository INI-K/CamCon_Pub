package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.PtpDeviceInfo
import com.inik.camcon.domain.repository.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CameraAbilitiesViewModel UI 상태
 */
data class CameraAbilitiesUiState(
    val abilities: CameraAbilitiesInfo? = null,
    val deviceInfo: PtpDeviceInfo? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 카메라 기능 정보 화면 ViewModel
 *
 * ADMIN 전용 - libgphoto2 API로 조회한 카메라 기능을 표시
 */
@HiltViewModel
class CameraAbilitiesViewModel @Inject constructor(
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraAbilitiesUiState())
    val uiState: StateFlow<CameraAbilitiesUiState> = _uiState.asStateFlow()

    // 하위 호환성을 위한 computed properties
    val abilities: StateFlow<CameraAbilitiesInfo?> get() = _uiState.mapState { it.abilities }
    val deviceInfo: StateFlow<PtpDeviceInfo?> get() = _uiState.mapState { it.deviceInfo }
    val isLoading: StateFlow<Boolean> get() = _uiState.mapState { it.isLoading }
    val errorMessage: StateFlow<String?> get() = _uiState.mapState { it.errorMessage }

    companion object {
        private const val TAG = "CameraAbilitiesViewModel"
    }

    private inline fun <T, R> StateFlow<T>.mapState(crossinline transform: (T) -> R): StateFlow<R> {
        return object : StateFlow<R> {
            override val value: R get() = transform(this@mapState.value)
            override val replayCache: List<R> get() = listOf(value)
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<R>): Nothing {
                this@mapState.collect { collector.emit(transform(it)) }
            }
        }
    }

    init {
        loadAbilities()
    }

    /**
     * 카메라 기능 정보 로드
     */
    private fun loadAbilities() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                // 카메라 초기화 확인
                if (!CameraNative.isCameraInitialized()) {
                    _uiState.update { it.copy(errorMessage = "카메라가 연결되지 않았습니다", isLoading = false) }
                    return@launch
                }

                // Repository를 통해 abilities/deviceInfo 조회
                val abilities = cameraRepository.getCameraAbilitiesInfo()

                if (abilities == null) {
                    // 직접 조회 시도
                    val abilitiesJson = CameraNative.getCameraAbilities()
                    val deviceInfoJson = CameraNative.getCameraDeviceInfo()

                    if (abilitiesJson != null && deviceInfoJson != null) {
                        _uiState.update {
                            it.copy(
                                abilities = parseAbilities(abilitiesJson),
                                deviceInfo = parseDeviceInfo(deviceInfoJson)
                            )
                        }
                        Log.i(TAG, "카메라 기능 정보 조회 성공")
                    } else {
                        _uiState.update { it.copy(errorMessage = "카메라 기능 정보를 조회할 수 없습니다") }
                    }
                } else {
                    // 캐시된 정보 사용
                    _uiState.update {
                        it.copy(
                            abilities = abilities,
                            deviceInfo = cameraRepository.getCameraDeviceInfoDetail()
                        )
                    }
                    Log.i(TAG, "캐시된 카메라 기능 정보 사용")
                }

            } catch (e: Exception) {
                Log.e(TAG, "카메라 기능 정보 로드 실패", e)
                _uiState.update { it.copy(errorMessage = "오류: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
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
