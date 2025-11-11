package com.inik.camcon.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.CameraNative
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

data class MockCameraUiState(
    val isEnabled: Boolean = false,
    val imageCount: Int = 0,
    val delayMs: Int = 500,
    val autoCapture: Boolean = false,
    val autoCaptureInterval: Int = 3000,
    val images: List<String> = emptyList(),
    val cameraModel: String = "",
    val manufacturer: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class MockCameraViewModel @Inject constructor(
    private val cameraNative: CameraNative
) : ViewModel() {

    companion object {
        private const val TAG = "MockCameraViewModel"

        // 주요 카메라 모델 목록
        val CAMERA_MODELS = listOf(
            "Canon" to listOf(
                "Canon EOS R5", "Canon EOS R6", "Canon EOS 5D Mark IV",
                "Canon EOS 90D", "Canon EOS M50", "Canon EOS Rebel T7i"
            ),
            "Nikon" to listOf(
                "Nikon Z9", "Nikon Z7 II", "Nikon D850",
                "Nikon D780", "Nikon D7500", "Nikon D5600"
            ),
            "Sony" to listOf(
                "Sony Alpha A7R IV", "Sony Alpha A7 III", "Sony Alpha A6600",
                "Sony Alpha A6400", "Sony ZV-E10", "Sony RX100 VII"
            ),
            "Fujifilm" to listOf(
                "Fujifilm X-T4", "Fujifilm X-T3", "Fujifilm X-S10",
                "Fujifilm X-E4", "Fujifilm GFX 100S", "Fujifilm X100V"
            ),
            "Panasonic" to listOf(
                "Panasonic GH5", "Panasonic GH6", "Panasonic S5",
                "Panasonic G9", "Panasonic S1H", "Panasonic S1R"
            ),
            "Olympus" to listOf(
                "Olympus OM-D E-M1 Mark III", "Olympus OM-D E-M5 Mark III",
                "Olympus PEN E-P7", "Olympus OM-D E-M10 Mark IV"
            ),
            "Pentax" to listOf(
                "Pentax K-3 III", "Pentax K-1 Mark II", "Pentax KP"
            )
        )
    }

    private val _uiState = MutableStateFlow(MockCameraUiState())
    val uiState: StateFlow<MockCameraUiState> = _uiState.asStateFlow()

    init {
        try {
            // CameraNative 라이브러리가 로드되었는지 확인
            if (cameraNative.isLibrariesLoaded()) {
                refreshState()
            } else {
                Log.e(TAG, "CameraNative 라이브러리가 로드되지 않음")
                _uiState.value = _uiState.value.copy(
                    error = "네이티브 라이브러리 로딩 실패"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "ViewModel 초기화 실패", e)
            _uiState.value = _uiState.value.copy(
                error = "초기화 실패: ${e.message}"
            )
        }
    }

    fun refreshState() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val info = withContext(Dispatchers.IO) {
                    cameraNative.getMockCameraInfo()
                }

                val json = JSONObject(info)
                val images = mutableListOf<String>()
                val imagesArray = json.getJSONArray("images")
                for (i in 0 until imagesArray.length()) {
                    images.add(imagesArray.getString(i))
                }

                _uiState.value = _uiState.value.copy(
                    isEnabled = json.getBoolean("enabled"),
                    imageCount = json.getInt("imageCount"),
                    delayMs = json.getInt("delayMs"),
                    autoCapture = json.getBoolean("autoCapture"),
                    autoCaptureInterval = json.getInt("autoCaptureInterval"),
                    cameraModel = json.optString("cameraModel", ""),
                    manufacturer = json.optString("manufacturer", ""),
                    images = images,
                    isLoading = false
                )

                Log.d(TAG, "Mock Camera 상태 업데이트: ${_uiState.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Mock Camera 상태 조회 실패", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "상태 조회 실패: ${e.message}"
                )
            }
        }
    }

    fun enableMockCamera(enable: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val result = withContext(Dispatchers.IO) {
                    cameraNative.enableMockCamera(enable)
                }

                if (result) {
                    _uiState.value = _uiState.value.copy(
                        isEnabled = enable,
                        isLoading = false,
                        successMessage = if (enable) "Mock Camera 활성화됨" else "Mock Camera 비활성화됨"
                    )
                    Log.i(TAG, "Mock Camera ${if (enable) "활성화" else "비활성화"} 성공")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Mock Camera 설정 실패"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mock Camera 설정 실패", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Mock Camera 설정 실패: ${e.message}"
                )
            }
        }
    }

    fun addMockImages(paths: List<String>) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val result = withContext(Dispatchers.IO) {
                    cameraNative.setMockCameraImages(paths.toTypedArray())
                }

                if (result) {
                    refreshState()
                    _uiState.value = _uiState.value.copy(
                        successMessage = "${paths.size}개 이미지 추가됨"
                    )
                    Log.i(TAG, "${paths.size}개 Mock 이미지 추가 성공")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "이미지 추가 실패"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mock 이미지 추가 실패", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "이미지 추가 실패: ${e.message}"
                )
            }
        }
    }

    fun clearMockImages() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val result = withContext(Dispatchers.IO) {
                    cameraNative.clearMockCameraImages()
                }

                if (result) {
                    refreshState()
                    _uiState.value = _uiState.value.copy(
                        successMessage = "모든 이미지 삭제됨"
                    )
                    Log.i(TAG, "Mock 이미지 모두 삭제 성공")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "이미지 삭제 실패"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mock 이미지 삭제 실패", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "이미지 삭제 실패: ${e.message}"
                )
            }
        }
    }

    fun setDelay(delayMs: Int) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    cameraNative.setMockCameraDelay(delayMs)
                }

                if (result) {
                    _uiState.value = _uiState.value.copy(
                        delayMs = delayMs,
                        successMessage = "딜레이 ${delayMs}ms로 설정"
                    )
                    Log.i(TAG, "Mock Camera 딜레이 ${delayMs}ms 설정 성공")
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "딜레이 설정 실패"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "딜레이 설정 실패", e)
                _uiState.value = _uiState.value.copy(
                    error = "딜레이 설정 실패: ${e.message}"
                )
            }
        }
    }

    fun setAutoCapture(enable: Boolean, intervalMs: Int) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val result = withContext(Dispatchers.IO) {
                    cameraNative.setMockCameraAutoCapture(enable, intervalMs)
                }

                if (result) {
                    _uiState.value = _uiState.value.copy(
                        autoCapture = enable,
                        autoCaptureInterval = intervalMs,
                        isLoading = false,
                        successMessage = if (enable) {
                            "자동 캡처 활성화 (${intervalMs}ms 간격)"
                        } else {
                            "자동 캡처 비활성화"
                        }
                    )
                    Log.i(TAG, "Mock Camera 자동 캡처 설정 성공")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "자동 캡처 설정 실패"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "자동 캡처 설정 실패", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "자동 캡처 설정 실패: ${e.message}"
                )
            }
        }
    }

    fun simulateError(errorCode: Int, message: String) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    cameraNative.simulateCameraError(errorCode, message)
                }

                if (result) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "에러 시뮬레이션: $message"
                    )
                    Log.i(TAG, "에러 시뮬레이션 성공: $errorCode - $message")
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "에러 시뮬레이션 실패"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "에러 시뮬레이션 실패", e)
                _uiState.value = _uiState.value.copy(
                    error = "에러 시뮬레이션 실패: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun setMockCameraModel(manufacturer: String, model: String) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    cameraNative.setMockCameraModel(manufacturer, model)
                }

                if (result) {
                    _uiState.value = _uiState.value.copy(
                        manufacturer = manufacturer,
                        cameraModel = model,
                        successMessage = "카메라 모델 설정: $manufacturer $model"
                    )
                    Log.i(TAG, "Mock Camera 모델 설정 성공: $manufacturer $model")
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "카메라 모델 설정 실패"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "카메라 모델 설정 실패", e)
                _uiState.value = _uiState.value.copy(
                    error = "카메라 모델 설정 실패: ${e.message}"
                )
            }
        }
    }
}