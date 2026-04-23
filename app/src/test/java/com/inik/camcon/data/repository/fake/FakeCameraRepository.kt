package com.inik.camcon.data.repository.fake

import com.inik.camcon.domain.model.BracketingSettings
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.PaginatedCameraPhotos
import com.inik.camcon.domain.model.PtpDeviceInfo
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.repository.CameraRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Fake CameraRepository 구현
 *
 * 역할:
 * - CameraRepository 인터페이스 완전 구현
 * - 테스트용 스텁 메서드 제공
 * - 촬영된 사진 시뮬레이션
 */
class FakeCameraRepository : CameraRepository {

    // 촬영된 사진 목록
    private val _capturedPhotos = MutableStateFlow<List<CapturedPhoto>>(emptyList())
    val capturedPhotos = _capturedPhotos.asStateFlow()

    // 처리된 파일 LRU (1000개 제한)
    private val processedFiles = LinkedHashMap<String, Long>(16, 0.75f, true) // access order
    private val MAX_PROCESSED_FILES = 1000

    /**
     * 처리된 파일 개수 조회
     */
    fun getProcessedFilesCount(): Int = processedFiles.size

    /**
     * 처리된 파일 정리
     */
    fun clearProcessedFiles() {
        processedFiles.clear()
    }

    // ============== CameraRepository 인터페이스 구현 ==============

    override fun getCameraFeed(): Flow<List<Camera>> {
        return flowOf(emptyList())
    }

    override suspend fun connectCamera(cameraId: String): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun disconnectCamera(): Result<Boolean> {
        return Result.success(true)
    }

    override fun isCameraConnected(): Flow<Boolean> {
        return flowOf(true)
    }

    override fun isInitializing(): Flow<Boolean> {
        return flowOf(false)
    }

    override fun isPtpipConnected(): Flow<Boolean> {
        return flowOf(false)
    }

    override suspend fun getCameraInfo(): Result<String> {
        return Result.success("Fake Camera")
    }

    override suspend fun getCameraSettings(): Result<CameraSettings> {
        return Result.success(
            CameraSettings(
                iso = "100",
                shutterSpeed = "1/100",
                aperture = "f/2.8",
                whiteBalance = "AUTO",
                focusMode = "AF",
                exposureCompensation = "0"
            )
        )
    }

    override suspend fun getCameraCapabilities(): Result<CameraCapabilities?> {
        return Result.success(null)
    }

    override suspend fun updateCameraSetting(key: String, value: String): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun startCameraEventListener(): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun stopCameraEventListener(): Result<Boolean> {
        return Result.success(true)
    }

    override fun setPhotoPreviewMode(enabled: Boolean) {}

    override fun isEventListenerActive(): Flow<Boolean> {
        return flowOf(false)
    }

    override suspend fun capturePhoto(mode: ShootingMode): Result<CapturedPhoto> {
        return Result.success(
            CapturedPhoto(
                id = "single_shot",
                filePath = "/storage/photo.jpg",
                thumbnailPath = null,
                captureTime = System.currentTimeMillis(),
                cameraModel = "Test Camera",
                settings = CameraSettings(
                    iso = "100",
                    shutterSpeed = "1/100",
                    aperture = "f/2.8",
                    whiteBalance = "AUTO",
                    focusMode = "AF",
                    exposureCompensation = "0"
                ),
                size = 2048000L,
                width = 1920,
                height = 1440
            )
        )
    }

    override fun startBurstCapture(count: Int): Flow<CapturedPhoto> {
        return emptyFlow()
    }

    override fun startTimelapse(settings: TimelapseSettings): Flow<CapturedPhoto> {
        // 실제 구현을 위해서는 이 메서드를 Flow로 변환해야 하지만,
        // 테스트에서는 startTimelapseForTest와 simulateShot 사용
        return emptyFlow()
    }

    override fun startBracketing(settings: BracketingSettings): Flow<CapturedPhoto> {
        return emptyFlow()
    }

    override suspend fun startBulbCapture(): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun stopBulbCapture(): Result<CapturedPhoto> {
        return Result.success(
            CapturedPhoto(
                id = "bulb_shot",
                filePath = "/storage/bulb.jpg",
                thumbnailPath = null,
                captureTime = System.currentTimeMillis(),
                cameraModel = "Test Camera",
                settings = CameraSettings(
                    iso = "100",
                    shutterSpeed = "1s",
                    aperture = "f/2.8",
                    whiteBalance = "AUTO",
                    focusMode = "AF",
                    exposureCompensation = "0"
                ),
                size = 2048000L,
                width = 1920,
                height = 1440
            )
        )
    }

    override fun startLiveView(): Flow<LiveViewFrame> {
        return emptyFlow()
    }

    override suspend fun stopLiveView(): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun autoFocus(): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun manualFocus(x: Float, y: Float): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun setFocusPoint(x: Float, y: Float): Result<Boolean> {
        return Result.success(true)
    }

    override fun getCapturedPhotos(): Flow<List<CapturedPhoto>> {
        return _capturedPhotos.asSharedFlow()
    }

    override suspend fun getCameraPhotos(): Result<List<CameraPhoto>> {
        return Result.success(emptyList())
    }

    override suspend fun getCameraPhotosPaged(
        page: Int,
        pageSize: Int
    ): Result<PaginatedCameraPhotos> {
        return Result.success(
            PaginatedCameraPhotos(
                photos = emptyList(),
                currentPage = page,
                pageSize = pageSize,
                totalItems = 0,
                totalPages = 0,
                hasNext = false
            )
        )
    }

    override suspend fun getCameraThumbnail(photoPath: String): Result<ByteArray> {
        return Result.success(ByteArray(0))
    }

    override suspend fun deletePhoto(photoId: String): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun downloadPhotoFromCamera(photoId: String): Result<CapturedPhoto> {
        return Result.success(
            CapturedPhoto(
                id = photoId,
                filePath = "/storage/downloaded.jpg",
                thumbnailPath = null,
                captureTime = System.currentTimeMillis(),
                cameraModel = "Test Camera",
                settings = CameraSettings(
                    iso = "100",
                    shutterSpeed = "1/100",
                    aperture = "f/2.8",
                    whiteBalance = "AUTO",
                    focusMode = "AF",
                    exposureCompensation = "0"
                ),
                size = 2048000L,
                width = 1920,
                height = 1440
            )
        )
    }

    override fun setRawFileRestrictionCallback(callback: ((fileName: String, restrictionMessage: String) -> Unit)?) {}

    override fun getCameraAbilitiesInfo(): CameraAbilitiesInfo? {
        return null
    }

    override fun getCameraDeviceInfoDetail(): PtpDeviceInfo? {
        return null
    }

    override suspend fun setSubscriptionTier(tier: SubscriptionTier): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun setRawFileDownloadEnabled(enabled: Boolean): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun isCameraConnectedNow(): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun isCameraInitializedNow(): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun getCameraFileListNow(): Result<List<String>> {
        return Result.success(emptyList())
    }

    // ============== Helper Methods ==============

    private fun addProcessedFile(fileName: String) {
        processedFiles[fileName] = System.currentTimeMillis()

        // LRU: 1000개 초과 시 가장 오래된 항목 제거
        if (processedFiles.size > MAX_PROCESSED_FILES) {
            val iterator = processedFiles.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    // ── C3 라운드 1 stub (2026-04-23) ──
    override suspend fun isNativeLibrariesLoaded(): Boolean = false
    override suspend fun setupNativeEnvironment(pluginDir: String): Boolean = false
    override suspend fun getLibGphoto2Version(): String = ""
    override suspend fun startNativeLog(logPath: String, level: Int): Boolean = false
    override suspend fun stopNativeLog(): Boolean = false
    override suspend fun readNativeLog(filePath: String): String = ""
    override suspend fun getCameraAbilitiesJson(): String? = null
    override suspend fun getCameraDeviceInfoJson(): String? = null
    override suspend fun deleteGphotoSettings(): String = ""
    override suspend fun resumeNativeOperations() {}
    override suspend fun downloadCameraPhoto(photoPath: String): ByteArray? = null
    override suspend fun getCameraPhotoExifJson(photoPath: String): String? = null
}
