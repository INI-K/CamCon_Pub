package com.inik.camcon.data.repository

import android.content.Context
import com.inik.camcon.domain.model.BracketingSettings
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.PaginatedCameraPhotos
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.repository.ColorTransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CameraRepository 얇은 Facade.
 *
 * H8 분해(2026-04-23): 1263줄 God Repository를 3개 sub-Impl로 위임 구조로 재구성.
 *  - [CameraLifecycleRepositoryImpl] — Connection 8 override
 *  - [CameraCaptureRepositoryImpl] — Event + Capture + LiveView + _capturedPhotos + LRU (14 override)
 *  - [CameraControlRepositoryImpl] — Settings + Focus + PhotoAccess + Abilities (17 override)
 *
 * Facade 책임:
 *  - 각 sub-Impl로 단순 delegate (CameraRepository 인터페이스 시그니처 보존)
 *  - GPU 초기화·PTPIP 콜백 설치·USB 구독 등 orchestration-only 초기화
 *  - Capture의 `cameraSettingsProvider`를 Control의 `getCachedSettings()`와 연결
 *  - Capture의 `onFlushCompleteCallback`을 `CameraStateObserver.updateCameraInitialization(false)`와 연결
 *  - 테스트용 LRU 헬퍼는 Capture로 delegate하여 기존 테스트 무변경 유지
 */
@Singleton
class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val colorTransferRepository: ColorTransferRepository,
    private val cameraStateObserver: com.inik.camcon.domain.manager.CameraStateObserver,
    private val lifecycleRepo: CameraLifecycleRepositoryImpl,
    private val captureRepo: CameraCaptureRepositoryImpl,
    private val controlRepo: CameraControlRepositoryImpl
) : CameraRepository, Closeable {

    companion object {
        private const val TAG = "카메라레포지토리"
    }

    init {
        // 테스트 환경에서 모의 객체들이 초기화를 방해하지 않도록 try-catch 적용
        try {
            initializeRepository()
        } catch (e: Exception) {
            com.inik.camcon.utils.LogcatManager.w(TAG, "Repository 초기화 실패 (테스트 환경일 수 있음): ${e.message}")
        }
    }

    /**
     * Repository 초기화.
     *  - GPU 초기화 (색변환 사용)
     *  - Capture ↔ Control 공유 상태 연결 (settings provider)
     *  - Capture의 flush complete → CameraStateObserver 연결
     *  - PTPIP 다운로드 콜백 설치
     *  - USB 분리 이벤트 구독
     */
    private fun initializeRepository() {
        colorTransferRepository.initializeGPU(context)

        captureRepo.cameraSettingsProvider = { controlRepo.getCachedSettings() }
        captureRepo.onFlushCompleteCallback = {
            cameraStateObserver.updateCameraInitialization(false)
        }
        captureRepo.installPtpipDownloadCallback()

        lifecycleRepo.subscribeToUsbEvents()
    }

    // ── Lifecycle delegates ──

    override fun getCameraFeed(): Flow<List<Camera>> = lifecycleRepo.getCameraFeed()
    override suspend fun connectCamera(cameraId: String): Result<Boolean> = lifecycleRepo.connectCamera(cameraId)
    override suspend fun disconnectCamera(): Result<Boolean> = lifecycleRepo.disconnectCamera()
    override fun isCameraConnected(): Flow<Boolean> = lifecycleRepo.isCameraConnected()
    override fun isInitializing(): Flow<Boolean> = lifecycleRepo.isInitializing()
    override fun isPtpipConnected(): Flow<Boolean> = lifecycleRepo.isPtpipConnected()
    override suspend fun isCameraConnectedNow(): Result<Boolean> = lifecycleRepo.isCameraConnectedNow()
    override suspend fun isCameraInitializedNow(): Result<Boolean> = lifecycleRepo.isCameraInitializedNow()

    // ── Capture delegates ──

    override fun isEventListenerActive(): Flow<Boolean> = captureRepo.isEventListenerActive()
    override fun setPhotoPreviewMode(enabled: Boolean) = captureRepo.setPhotoPreviewMode(enabled)
    override suspend fun startCameraEventListener(): Result<Boolean> = captureRepo.startCameraEventListener()
    override suspend fun stopCameraEventListener(): Result<Boolean> = captureRepo.stopCameraEventListener()
    override suspend fun capturePhoto(mode: ShootingMode): Result<CapturedPhoto> = captureRepo.capturePhoto(mode)
    override fun startBurstCapture(count: Int): Flow<CapturedPhoto> = captureRepo.startBurstCapture(count)
    override fun startTimelapse(settings: TimelapseSettings): Flow<CapturedPhoto> = captureRepo.startTimelapse(settings)
    override fun startBracketing(settings: BracketingSettings): Flow<CapturedPhoto> = captureRepo.startBracketing(settings)
    override suspend fun startBulbCapture(): Result<Boolean> = captureRepo.startBulbCapture()
    override suspend fun stopBulbCapture(): Result<CapturedPhoto> = captureRepo.stopBulbCapture()
    override fun startLiveView(): Flow<LiveViewFrame> = captureRepo.startLiveView()
    override suspend fun stopLiveView(): Result<Boolean> = captureRepo.stopLiveView()
    override fun getCapturedPhotos(): Flow<List<CapturedPhoto>> = captureRepo.getCapturedPhotos()
    override suspend fun deletePhoto(photoId: String): Result<Boolean> = captureRepo.deletePhoto(photoId)
    override fun setRawFileRestrictionCallback(
        callback: ((fileName: String, restrictionMessage: String) -> Unit)?
    ) = captureRepo.setRawFileRestrictionCallback(callback)

    // ── Control delegates ──

    override suspend fun getCameraSettings(): Result<CameraSettings> = controlRepo.getCameraSettings()
    override suspend fun getCameraInfo(): Result<String> = controlRepo.getCameraInfo()
    override suspend fun updateCameraSetting(key: String, value: String): Result<Boolean> =
        controlRepo.updateCameraSetting(key, value)
    override suspend fun getCameraCapabilities(): Result<CameraCapabilities?> = controlRepo.getCameraCapabilities()
    override suspend fun autoFocus(): Result<Boolean> = controlRepo.autoFocus()
    override suspend fun manualFocus(x: Float, y: Float): Result<Boolean> = controlRepo.manualFocus(x, y)
    override suspend fun setFocusPoint(x: Float, y: Float): Result<Boolean> = controlRepo.setFocusPoint(x, y)
    override suspend fun downloadPhotoFromCamera(photoId: String): Result<CapturedPhoto> =
        controlRepo.downloadPhotoFromCamera(photoId)

    override suspend fun getCameraPhotos(): Result<List<CameraPhoto>> =
        controlRepo.getCameraPhotos(onEventListenerRestart = { captureRepo.restartEventListenerIfNeeded() })

    override suspend fun getCameraPhotosPaged(
        page: Int,
        pageSize: Int
    ): Result<PaginatedCameraPhotos> =
        controlRepo.getCameraPhotosPaged(
            page = page,
            pageSize = pageSize,
            onEventListenerRestart = { captureRepo.restartEventListenerIfNeeded() }
        )

    override suspend fun getCameraThumbnail(photoPath: String): Result<ByteArray> =
        controlRepo.getCameraThumbnail(photoPath)

    override fun getCameraAbilitiesInfo(): com.inik.camcon.domain.model.CameraAbilitiesInfo? =
        controlRepo.getCameraAbilitiesInfo()

    override fun getCameraDeviceInfoDetail(): com.inik.camcon.domain.model.PtpDeviceInfo? =
        controlRepo.getCameraDeviceInfoDetail()

    override suspend fun setSubscriptionTier(tier: com.inik.camcon.domain.model.SubscriptionTier): Result<Unit> =
        controlRepo.setSubscriptionTier(tier)

    override suspend fun setRawFileDownloadEnabled(enabled: Boolean): Result<Unit> =
        controlRepo.setRawFileDownloadEnabled(enabled)

    override suspend fun getCameraFileListNow(): Result<List<String>> = controlRepo.getCameraFileListNow()

    // ── C3 라운드 1: Native Gateway delegates ──

    override suspend fun isNativeLibrariesLoaded(): Boolean = controlRepo.isNativeLibrariesLoaded()
    override suspend fun setupNativeEnvironment(pluginDir: String): Boolean =
        controlRepo.setupNativeEnvironment(pluginDir)
    override suspend fun getLibGphoto2Version(): String = controlRepo.getLibGphoto2Version()
    override suspend fun startNativeLog(logPath: String, level: Int): Boolean =
        controlRepo.startNativeLog(logPath, level)
    override suspend fun stopNativeLog(): Boolean = controlRepo.stopNativeLog()
    override suspend fun readNativeLog(filePath: String): String = controlRepo.readNativeLog(filePath)
    override suspend fun getCameraAbilitiesJson(): String? = controlRepo.getCameraAbilitiesJson()
    override suspend fun getCameraDeviceInfoJson(): String? = controlRepo.getCameraDeviceInfoJson()
    override suspend fun deleteGphotoSettings(): String = controlRepo.deleteGphotoSettings()
    override suspend fun resumeNativeOperations() = controlRepo.resumeNativeOperations()
    override suspend fun downloadCameraPhoto(photoPath: String): ByteArray? =
        controlRepo.downloadCameraPhoto(photoPath)
    override suspend fun getCameraPhotoExifJson(photoPath: String): String? =
        controlRepo.getCameraPhotoExifJson(photoPath)

    // ── LRU 캐시 테스트 헬퍼 (기존 테스트 호환용) ──

    fun markFileAsProcessed(filePath: String) = captureRepo.markFileAsProcessed(filePath)
    fun isFileProcessed(filePath: String): Boolean = captureRepo.isFileProcessed(filePath)
    fun getProcessedFilesCount(): Int = captureRepo.getProcessedFilesCount()

    override fun close() {
        captureRepo.clearProcessedFiles()
    }
}
