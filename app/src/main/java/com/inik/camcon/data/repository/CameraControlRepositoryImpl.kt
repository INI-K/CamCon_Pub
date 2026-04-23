package com.inik.camcon.data.repository

import android.util.Log
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.PaginatedCameraPhotos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 설정·포커스·사진 접근·능력 정보 담당 sub-impl.
 *
 * H8 분해: 원본 CameraRepositoryImpl의 Settings(3) + Focus(3) + PhotoAccess(5) + Abilities(3) +
 * Subscription/Raw(2) + FileList(1) = 17개 override 이동.
 *
 * `_cameraSettings` StateFlow 소유. Facade·Capture 측에서 `getCachedSettings()`로 읽기 전용 접근.
 * 이벤트 리스너 재시작 훅은 Facade가 `onEventListenerRestart` 콜백으로 주입 (Capture에 위임).
 */
@Singleton
class CameraControlRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeCameraDataSource,
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val connectionManager: CameraConnectionManager,
    private val eventManager: CameraEventManager,
    private val downloadManager: PhotoDownloadManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "카메라제어레포"
    }

    private val _cameraSettings = MutableStateFlow<CameraSettings?>(null)

    /** Facade·Capture에서 참조하는 캐시된 설정 (PTPIP 콜백·createCapturedPhoto 등). */
    fun getCachedSettings(): CameraSettings? = _cameraSettings.value

    suspend fun getCameraSettings(): Result<CameraSettings> {
        return withContext(ioDispatcher) {
            try {
                // 캐시된 설정이 있으면 우선 반환
                _cameraSettings.value?.let { cachedSettings ->
                    com.inik.camcon.utils.LogcatManager.d(TAG, "캐시된 카메라 설정 반환")
                    return@withContext Result.success(cachedSettings)
                }

                val widgetJson = getWidgetJsonFromSource()
                val settings = parseWidgetJsonToSettings(widgetJson)

                withContext(Dispatchers.Main) {
                    _cameraSettings.value = settings
                }

                com.inik.camcon.utils.LogcatManager.d(TAG, "카메라 설정 업데이트")
                Result.success(settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 설정 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 위젯 JSON 가져오기. USB > PTPIP > 마스터 데이터 우선순위.
     */
    private suspend fun getWidgetJsonFromSource(): String {
        val isUsbConnected = usbCameraManager.isNativeCameraConnected.value
        val isPtpipConnected = connectionManager.isPtpipConnected.value

        return if (isUsbConnected) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "USB 카메라 연결됨 - 마스터 데이터 사용")
            usbCameraManager.buildWidgetJsonFromMaster()
        } else if (isPtpipConnected) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "PTPIP 카메라 연결됨 - 직접 네이티브 호출")
            nativeDataSource.buildWidgetJson()
        } else {
            val masterData = usbCameraManager.buildWidgetJsonFromMaster()
            if (masterData.isNotEmpty()) {
                com.inik.camcon.utils.LogcatManager.d(TAG, "카메라 미연결이지만 마스터 데이터 사용")
                masterData
            } else {
                com.inik.camcon.utils.LogcatManager.d(TAG, "마스터 데이터 없음 - 직접 네이티브 호출")
                nativeDataSource.buildWidgetJson()
            }
        }
    }

    /**
     * 위젯 JSON을 CameraSettings로 변환. (TODO: 실제 파싱)
     */
    private fun parseWidgetJsonToSettings(widgetJson: String): CameraSettings {
        // TODO: JSON 파싱하여 설정 추출
        return CameraSettings(
            iso = "100",
            shutterSpeed = "1/125",
            aperture = "2.8",
            whiteBalance = "자동",
            focusMode = "AF-S",
            exposureCompensation = "0"
        )
    }

    suspend fun getCameraInfo(): Result<String> {
        return withContext(ioDispatcher) {
            try {
                val summary = nativeDataSource.getCameraSummary()
                Result.success(summary.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 정보 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    suspend fun updateCameraSetting(key: String, value: String): Result<Boolean> {
        return withContext(ioDispatcher) {
            try {
                com.inik.camcon.utils.LogcatManager.d(TAG, "카메라 설정 업데이트: $key = $value")
                Result.success(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 설정 업데이트 실패", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getCameraCapabilities(): Result<CameraCapabilities?> {
        return withContext(ioDispatcher) {
            try {
                val capabilities = connectionManager.cameraCapabilities.value
                    ?: nativeDataSource.getCameraCapabilities()
                Result.success(capabilities)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 기능 정보 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    // ── Focus ──

    suspend fun autoFocus(): Result<Boolean> {
        return withContext(ioDispatcher) {
            try {
                com.inik.camcon.utils.LogcatManager.d(TAG, "자동초점 시작")
                val result = nativeDataSource.autoFocus()
                com.inik.camcon.utils.LogcatManager.d(TAG, "자동초점 결과: $result")
                Result.success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "자동초점 실패", e)
                Result.failure(e)
            }
        }
    }

    suspend fun manualFocus(x: Float, y: Float): Result<Boolean> {
        // TODO: 수동 초점 기능 구현
        return Result.success(true)
    }

    suspend fun setFocusPoint(x: Float, y: Float): Result<Boolean> {
        // TODO: 초점 포인트 설정 기능 구현
        return Result.success(true)
    }

    // ── Photo Access ──

    suspend fun downloadPhotoFromCamera(photoId: String): Result<com.inik.camcon.domain.model.CapturedPhoto> {
        return downloadManager.downloadPhotoFromCamera(
            photoId = photoId,
            cameraCapabilities = connectionManager.cameraCapabilities.value,
            cameraSettings = _cameraSettings.value
        )
    }

    suspend fun getCameraPhotos(
        onEventListenerRestart: suspend () -> Unit
    ): Result<List<CameraPhoto>> {
        val result = downloadManager.getCameraPhotos()

        // 이벤트 리스너가 중지되었을 가능성이 있으므로 안전하게 재시작
        if (connectionManager.isConnected.value && result.isSuccess) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "사진 목록 가져오기 후 이벤트 리스너 상태 확인 및 재시작")
            kotlinx.coroutines.delay(500)

            if (!eventManager.isRunning()) {
                try {
                    com.inik.camcon.utils.LogcatManager.d(TAG, "이벤트 리스너 재시작 시도")
                    onEventListenerRestart()
                } catch (e: Exception) {
                    Log.w(TAG, "이벤트 리스너 재시작 실패, 나중에 다시 시도", e)
                }
            } else {
                com.inik.camcon.utils.LogcatManager.d(TAG, "이벤트 리스너가 이미 실행 중")
            }
        }

        return result
    }

    suspend fun getCameraPhotosPaged(
        page: Int,
        pageSize: Int,
        onEventListenerRestart: suspend () -> Unit
    ): Result<PaginatedCameraPhotos> {
        return downloadManager.getCameraPhotosPaged(
            page = page,
            pageSize = pageSize,
            isPhotoPreviewMode = eventManager.isPhotoPreviewMode(),
            onEventListenerRestart = {
                if (!eventManager.isRunning()) {
                    onEventListenerRestart()
                }
            }
        )
    }

    suspend fun getCameraThumbnail(photoPath: String): Result<ByteArray> {
        return downloadManager.getCameraThumbnail(
            photoPath = photoPath,
            isConnected = connectionManager.isConnected.value,
            isInitializing = connectionManager.isInitializing.value,
            isNativeCameraConnected = usbCameraManager.isNativeCameraConnected.value
        )
    }

    // ── Abilities / Device Info ──

    fun getCameraAbilitiesInfo(): com.inik.camcon.domain.model.CameraAbilitiesInfo? {
        return nativeDataSource.getUsbCameraAbilities()
            ?: ptpipDataSource.getCurrentAbilities()
    }

    fun getCameraDeviceInfoDetail(): com.inik.camcon.domain.model.PtpDeviceInfo? {
        return nativeDataSource.getUsbCameraDeviceInfo()
            ?: ptpipDataSource.getCurrentDeviceInfo()
    }

    // ── Subscription / Raw / FileList (C-3 수정: CameraViewModel CameraNative 직접 호출 제거) ──

    suspend fun setSubscriptionTier(tier: com.inik.camcon.domain.model.SubscriptionTier): Result<Unit> = try {
        val tierInt = when (tier) {
            com.inik.camcon.domain.model.SubscriptionTier.FREE -> 0
            com.inik.camcon.domain.model.SubscriptionTier.BASIC -> 1
            com.inik.camcon.domain.model.SubscriptionTier.PRO -> 2
            com.inik.camcon.domain.model.SubscriptionTier.REFERRER -> 2
            com.inik.camcon.domain.model.SubscriptionTier.ADMIN -> 2
        }
        nativeDataSource.setSubscriptionTier(tierInt)
        Log.d(TAG, "✅ 구독 티어 설정 완료: $tier (네이티브: $tierInt)")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "❌ 구독 티어 설정 실패", e)
        Result.failure(e)
    }

    suspend fun setRawFileDownloadEnabled(enabled: Boolean): Result<Unit> = try {
        nativeDataSource.setRawFileDownloadEnabled(enabled)
        Log.d(TAG, "✅ RAW 파일 다운로드 설정 완료: $enabled")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "❌ RAW 파일 다운로드 설정 실패", e)
        Result.failure(e)
    }

    suspend fun getCameraFileListNow(): Result<List<String>> = try {
        val fileList = nativeDataSource.getCameraFileListNow()
        Log.d(TAG, "✅ 카메라 파일 목록 조회 완료: ${fileList.size}개")
        Result.success(fileList)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "❌ 카메라 파일 목록 조회 실패", e)
        Result.failure(e)
    }

    // ── C3 라운드 1: Native Gateway (Presentation→JNI 직접 호출 래핑, 2026-04-23) ──

    suspend fun isNativeLibrariesLoaded(): Boolean = nativeDataSource.isLibrariesLoaded()

    suspend fun setupNativeEnvironment(pluginDir: String): Boolean =
        nativeDataSource.setupEnvironmentPaths(pluginDir)

    suspend fun getLibGphoto2Version(): String =
        withContext(ioDispatcher) { nativeDataSource.getLibGphoto2Version() }

    suspend fun startNativeLog(logPath: String, level: Int): Boolean =
        nativeDataSource.startLogFile(logPath, level)

    suspend fun stopNativeLog(): Boolean = nativeDataSource.stopLogFile()

    suspend fun readNativeLog(filePath: String): String =
        nativeDataSource.getLogFileContent(filePath)

    suspend fun getCameraAbilitiesJson(): String? = nativeDataSource.getCameraAbilitiesJson()

    suspend fun getCameraDeviceInfoJson(): String? = nativeDataSource.getCameraDeviceInfoJson()

    suspend fun deleteGphotoSettings(): String = nativeDataSource.deleteGphotoSettings()

    suspend fun resumeNativeOperations() {
        nativeDataSource.resumeOperations()
    }

    suspend fun downloadCameraPhoto(photoPath: String): ByteArray? =
        withContext(ioDispatcher) { nativeDataSource.downloadCameraPhoto(photoPath) }

    suspend fun getCameraPhotoExifJson(photoPath: String): String? =
        nativeDataSource.getCameraPhotoExifJson(photoPath)
}
