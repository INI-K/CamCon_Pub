package com.inik.camcon.data.repository.managers
import android.util.Log
import com.inik.camcon.data.datasource.nativesource.CameraCaptureListener
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class CameraEventManager @Inject constructor(
    private val nativeDataSource: NativeCameraDataSource
) {
    companion object {
        private const val TAG = "CameraEventManager"
    }
    private val _isEventListenerActive = MutableStateFlow(false)
    val isEventListenerActive: StateFlow<Boolean> = _isEventListenerActive.asStateFlow()
    var onUsbDisconnectedCallback: (() -> Unit)? = null
    var onRawFileRestricted: ((fileName: String, restrictionMessage: String) -> Unit)? = null
    private var isPhotoPreviewMode = false
    fun setPhotoPreviewMode(enabled: Boolean) {
        isPhotoPreviewMode = enabled
    }
    fun isPhotoPreviewMode(): Boolean = isPhotoPreviewMode
    fun isRunning(): Boolean = _isEventListenerActive.value
    fun startCameraEventListener(
        isConnected: Boolean,
        isInitializing: Boolean,
        saveDirectory: String,
        onPhotoCaptured: (String, String) -> Unit,
        onFlushComplete: () -> Unit,
        onCaptureFailed: (Int) -> Unit
    ): Result<Boolean> {
        if (!isConnected || isInitializing) {
            return Result.failure(IllegalStateException("카메라 연결 상태가 준비되지 않았습니다"))
        }
        Log.d(TAG, "이벤트 리스너 저장 경로: $saveDirectory")
        if (_isEventListenerActive.value) return Result.success(true)
        return runCatching {
            nativeDataSource.listenCameraEvents(object : CameraCaptureListener {
                override fun onFlushComplete() = onFlushComplete()
                override fun onPhotoCaptured(filePath: String, fileName: String) {
                    val ext = fileName.substringAfterLast(".", "").lowercase()
                    if (ext in Constants.ImageProcessing.RAW_EXTENSIONS && !isPhotoPreviewMode) {
                        onRawFileRestricted?.invoke(fileName, "RAW 파일은 현재 모드에서 자동 수신 제한됨")
                    }
                    onPhotoCaptured(filePath, fileName)
                }
                override fun onCaptureFailed(errorCode: Int) = onCaptureFailed(errorCode)
                override fun onUsbDisconnected() {
                    onUsbDisconnectedCallback?.invoke()
                    _isEventListenerActive.value = false
                }
            })
            _isEventListenerActive.value = true
            true
        }.onFailure { Log.e(TAG, "이벤트 리스너 시작 실패", it) }
    }
    fun stopCameraEventListener(): Result<Boolean> {
        return runCatching {
            nativeDataSource.stopListenCameraEvents()
            _isEventListenerActive.value = false
            true
        }
    }
}