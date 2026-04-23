package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.manager.ErrorNotifier
import com.inik.camcon.domain.model.Camera
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 연결/초기화 생명주기 담당 sub-impl.
 *
 * H8 분해: 원본 CameraRepositoryImpl에서 Connection 책임 8개 override를 이 클래스로 이동.
 * Facade가 여기에 delegate 한다.
 */
@Singleton
class CameraLifecycleRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeCameraDataSource,
    private val usbCameraManager: com.inik.camcon.data.datasource.usb.UsbCameraManager,
    private val connectionManager: CameraConnectionManager,
    private val eventManager: CameraEventManager,
    private val errorNotifier: ErrorNotifier,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "카메라생명주기레포"
    }

    /**
     * USB 분리 이벤트 구독. Facade init에서 1회 호출.
     */
    fun subscribeToUsbEvents() {
        scope.launch(ioDispatcher) {
            errorNotifier.usbDisconnectedEvent.collect {
                com.inik.camcon.utils.LogcatManager.d(TAG, "USB 분리 이벤트 감지 - USB 분리 처리 시작")
                usbCameraManager.handleUsbDisconnection()
            }
        }
    }

    fun getCameraFeed(): Flow<List<Camera>> =
        connectionManager.cameraFeed

    suspend fun connectCamera(cameraId: String): Result<Boolean> {
        val result = connectionManager.connectCamera(cameraId)
        if (result.isSuccess) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "카메라 연결 완료 - 안정화 대기 시작")
            kotlinx.coroutines.delay(300)
            // 이벤트 리스너는 UI에서 명시적으로 시작되도록 변경
        }
        return result
    }

    suspend fun disconnectCamera(): Result<Boolean> {
        // 이벤트 리스너 중지
        eventManager.stopCameraEventListener()
        return connectionManager.disconnectCamera()
    }

    fun isCameraConnected(): Flow<Boolean> =
        combine(
            connectionManager.isConnected,
            eventManager.isEventListenerActive
        ) { isConnected, isListenerActive ->
            // libgphoto2(AP 강제) 경로에서도 이벤트 리스너가 활성화되어 있으면 연결로 간주
            isConnected || isListenerActive
        }

    fun isInitializing(): Flow<Boolean> =
        connectionManager.isInitializing

    fun isPtpipConnected(): Flow<Boolean> =
        connectionManager.isPtpipConnected

    suspend fun isCameraConnectedNow(): Result<Boolean> = try {
        val connected = nativeDataSource.isCameraConnectedNow()
        android.util.Log.d(TAG, "✅ 카메라 연결 상태 확인: $connected")
        Result.success(connected)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e(TAG, "❌ 카메라 연결 상태 확인 실패", e)
        Result.failure(e)
    }

    suspend fun isCameraInitializedNow(): Result<Boolean> = try {
        val initialized = nativeDataSource.isCameraInitialized()
        android.util.Log.d(TAG, "✅ 카메라 초기화 상태 확인: $initialized")
        Result.success(initialized)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e(TAG, "❌ 카메라 초기화 상태 확인 실패", e)
        Result.failure(e)
    }
}
