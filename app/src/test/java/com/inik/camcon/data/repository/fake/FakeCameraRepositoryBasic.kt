package com.inik.camcon.data.repository.fake

import com.inik.camcon.domain.model.BracketingSettings
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.PtpDeviceInfo
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.TimelapseSettings
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.PaginatedCameraPhotos
import com.inik.camcon.domain.repository.CameraRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * 테스트용 카메라 저장소 Fake 구현
 *
 * 목적: UseCase 및 ViewModel 테스트에서 카메라 저장소의 동작을 제어 가능하게 제공
 *
 * 특징:
 * - Result<T> 제어 속성으로 성공/실패 시나리오 설정 가능
 * - 메서드 호출 추적으로 상호작용 검증 가능
 * - Flow 기반 메서드는 고정 값 또는 제어 가능한 MutableStateFlow 반환
 * - 하드웨어 의존성 없음 — 순수 in-memory 구현
 */
class FakeCameraRepositoryBasic : CameraRepository {

    // ======================== 제어 속성 ========================
    // 테스트에서 설정하여 메서드의 반환 값을 제어

    var connectCameraResult: Result<Boolean> = Result.success(true)
    var disconnectCameraResult: Result<Boolean> = Result.success(true)
    var capturePhotoResult: Result<CapturedPhoto> = Result.success(
        CapturedPhoto(
            id = "photo-001",
            filePath = "/sdcard/DCIM/IMG_001.JPG",
            thumbnailPath = "/sdcard/DCIM/.thumbs/IMG_001.JPG",
            captureTime = System.currentTimeMillis(),
            cameraModel = "Test Camera Model",
            settings = CameraSettings(
                iso = "100",
                shutterSpeed = "1/125",
                aperture = "f/5.6",
                whiteBalance = "Auto",
                exposureCompensation = "0",
                focusMode = "AF-S"
            ),
            size = 2_500_000L,
            width = 6000,
            height = 4000
        )
    )
    var getCameraInfoResult: Result<String> = Result.success("Camera Info String")
    var getCameraSettingsResult: Result<CameraSettings> = Result.success(
        CameraSettings(
            iso = "100",
            shutterSpeed = "1/125",
            aperture = "f/5.6",
            whiteBalance = "Auto",
            exposureCompensation = "0",
            focusMode = "AF-S"
        )
    )
    var getCameraCapabilitiesResult: Result<CameraCapabilities?> = Result.success(
        CameraCapabilities(
            model = "Test Camera",
            canCapturePhoto = true,
            canCaptureVideo = true,
            canLiveView = true,
            canTriggerCapture = true,
            supportsBurstMode = true,
            supportsTimelapse = true,
            supportsBracketing = true,
            supportsBulbMode = true,
            supportsAutofocus = true,
            supportsManualFocus = true,
            supportsFocusPoint = true,
            canDownloadFiles = true,
            canDeleteFiles = true,
            canPreviewFiles = true,
            availableIsoSettings = listOf("100", "200", "400", "800"),
            availableShutterSpeeds = listOf("1/8000", "1/4000", "1/2000", "1/1000"),
            availableApertures = listOf("f/2.8", "f/4", "f/5.6", "f/8"),
            availableWhiteBalanceSettings = listOf("Auto", "Daylight", "Cloudy", "Tungsten"),
            supportsRemoteControl = true,
            supportsConfigChange = true
        )
    )
    var updateCameraSettingResult: Result<Boolean> = Result.success(true)
    var startCameraEventListenerResult: Result<Boolean> = Result.success(true)
    var stopCameraEventListenerResult: Result<Boolean> = Result.success(true)
    var stopLiveViewResult: Result<Boolean> = Result.success(true)
    var startLiveViewFlow: Flow<LiveViewFrame> = emptyFlow()
    var autoFocusResult: Result<Boolean> = Result.success(true)
    var manualFocusResult: Result<Boolean> = Result.success(true)
    var setFocusPointResult: Result<Boolean> = Result.success(true)
    var getCameraPhotosResult: Result<List<CameraPhoto>> = Result.success(emptyList())
    var getCameraPhotosPagedResult: Result<PaginatedCameraPhotos> = Result.success(
        PaginatedCameraPhotos(
            photos = emptyList(),
            currentPage = 0,
            pageSize = 20,
            totalItems = 0,
            totalPages = 0,
            hasNext = false
        )
    )
    var getCameraThumbnailResult: Result<ByteArray> = Result.success(ByteArray(1000))
    var deletePhotoResult: Result<Boolean> = Result.success(true)
    var downloadPhotoFromCameraResult: Result<CapturedPhoto> = Result.success(
        CapturedPhoto(
            id = "downloaded-photo",
            filePath = "/sdcard/DCIM/Downloaded.JPG",
            thumbnailPath = "/sdcard/DCIM/.thumbs/Downloaded.JPG",
            captureTime = System.currentTimeMillis(),
            cameraModel = "Test Camera Model",
            settings = CameraSettings(
                iso = "200",
                shutterSpeed = "1/250",
                aperture = "f/4",
                whiteBalance = "Daylight",
                exposureCompensation = "0",
                focusMode = "AF-S"
            ),
            size = 5_000_000L,
            width = 6000,
            height = 4000
        )
    )
    var startBulbCaptureResult: Result<Boolean> = Result.success(true)
    var stopBulbCaptureResult: Result<CapturedPhoto> = Result.success(
        CapturedPhoto(
            id = "bulb-photo",
            filePath = "/sdcard/DCIM/Bulb.JPG",
            thumbnailPath = "/sdcard/DCIM/.thumbs/Bulb.JPG",
            captureTime = System.currentTimeMillis(),
            cameraModel = "Test Camera Model",
            settings = CameraSettings(
                iso = "100",
                shutterSpeed = "2\"",
                aperture = "f/5.6",
                whiteBalance = "Auto",
                exposureCompensation = "0",
                focusMode = "AF-S"
            ),
            size = 3_000_000L,
            width = 6000,
            height = 4000
        )
    )
    var isCameraConnectedNowResult: Result<Boolean> = Result.success(true)
    var isCameraInitializedNowResult: Result<Boolean> = Result.success(true)
    var getCameraFileListNowResult: Result<List<String>> = Result.success(emptyList())
    var setSubscriptionTierResult: Result<Unit> = Result.success(Unit)
    var setRawFileDownloadEnabledResult: Result<Unit> = Result.success(Unit)

    // ======================== 상태 추적 속성 ========================
    // 메서드 호출 기록 및 파라미터 검증용

    var connectCameraCallCount: Int = 0
    var lastConnectCameraId: String? = null

    var disconnectCameraCallCount: Int = 0

    var capturePhotoCallCount: Int = 0
    var lastCapturePhotoMode: ShootingMode? = null

    var getCameraInfoCallCount: Int = 0
    var getCameraSettingsCallCount: Int = 0
    var getCameraCapabilitiesCallCount: Int = 0

    var updateCameraSettingCallCount: Int = 0
    var lastUpdateCameraSettingKey: String? = null
    var lastUpdateCameraSettingValue: String? = null

    var startCameraEventListenerCallCount: Int = 0
    var stopCameraEventListenerCallCount: Int = 0

    var startLiveViewCallCount: Int = 0
    var stopLiveViewCallCount: Int = 0
    var autoFocusCallCount: Int = 0

    var manualFocusCallCount: Int = 0
    var lastManualFocusX: Float? = null
    var lastManualFocusY: Float? = null

    var setFocusPointCallCount: Int = 0
    var lastSetFocusPointX: Float? = null
    var lastSetFocusPointY: Float? = null

    var getCameraPhotosCallCount: Int = 0
    var getCameraPhotosPagedCallCount: Int = 0
    var lastCameraPhotosPagedPage: Int? = null
    var lastCameraPhotosPagedPageSize: Int? = null

    var getCameraThumbnailCallCount: Int = 0
    var lastGetCameraThumbnailPath: String? = null

    var deletePhotoCallCount: Int = 0
    var lastDeletePhotoId: String? = null

    var downloadPhotoFromCameraCallCount: Int = 0
    var lastDownloadPhotoId: String? = null

    var startBulbCaptureCallCount: Int = 0
    var stopBulbCaptureCallCount: Int = 0

    var isCameraConnectedNowCallCount: Int = 0
    var isCameraInitializedNowCallCount: Int = 0
    var getCameraFileListNowCallCount: Int = 0
    var setSubscriptionTierCallCount: Int = 0
    var lastSetSubscriptionTier: SubscriptionTier? = null
    var setRawFileDownloadEnabledCallCount: Int = 0
    var lastSetRawFileDownloadEnabled: Boolean? = null

    // ======================== Flow 상태 속성 ========================

    private val cameraFeedFlow = MutableStateFlow<List<Camera>>(emptyList())
    private val isCameraConnectedFlow = MutableStateFlow(true)
    private val isInitializingFlow = MutableStateFlow(false)
    private val isPtpipConnectedFlow = MutableStateFlow(false)
    private val isEventListenerActiveFlow = MutableStateFlow(false)

    // ======================== 인터페이스 구현 ========================

    override fun getCameraFeed(): Flow<List<Camera>> = cameraFeedFlow

    override suspend fun connectCamera(cameraId: String): Result<Boolean> {
        connectCameraCallCount++
        lastConnectCameraId = cameraId
        return connectCameraResult
    }

    override suspend fun disconnectCamera(): Result<Boolean> {
        disconnectCameraCallCount++
        return disconnectCameraResult
    }

    override fun isCameraConnected(): Flow<Boolean> = isCameraConnectedFlow

    override fun isInitializing(): Flow<Boolean> = isInitializingFlow

    override fun isPtpipConnected(): Flow<Boolean> = isPtpipConnectedFlow

    override suspend fun getCameraInfo(): Result<String> {
        getCameraInfoCallCount++
        return getCameraInfoResult
    }

    override suspend fun getCameraSettings(): Result<CameraSettings> {
        getCameraSettingsCallCount++
        return getCameraSettingsResult
    }

    override suspend fun getCameraCapabilities(): Result<CameraCapabilities?> {
        getCameraCapabilitiesCallCount++
        return getCameraCapabilitiesResult
    }

    override suspend fun updateCameraSetting(key: String, value: String): Result<Boolean> {
        updateCameraSettingCallCount++
        lastUpdateCameraSettingKey = key
        lastUpdateCameraSettingValue = value
        return updateCameraSettingResult
    }

    override suspend fun startCameraEventListener(): Result<Boolean> {
        startCameraEventListenerCallCount++
        return startCameraEventListenerResult
    }

    override suspend fun stopCameraEventListener(): Result<Boolean> {
        stopCameraEventListenerCallCount++
        return stopCameraEventListenerResult
    }

    override fun setPhotoPreviewMode(enabled: Boolean) {
        // No-op for testing
    }

    override fun isEventListenerActive(): Flow<Boolean> = isEventListenerActiveFlow

    override suspend fun capturePhoto(mode: ShootingMode): Result<CapturedPhoto> {
        capturePhotoCallCount++
        lastCapturePhotoMode = mode
        return capturePhotoResult
    }

    override fun startBurstCapture(count: Int): Flow<CapturedPhoto> {
        // Returns empty flow for test; can be overridden by subclass if needed
        return emptyFlow()
    }

    override fun startTimelapse(settings: TimelapseSettings): Flow<CapturedPhoto> {
        // Returns empty flow for test; can be overridden by subclass if needed
        return emptyFlow()
    }

    override fun startBracketing(settings: BracketingSettings): Flow<CapturedPhoto> {
        // Returns empty flow for test; can be overridden by subclass if needed
        return emptyFlow()
    }

    override suspend fun startBulbCapture(): Result<Boolean> {
        startBulbCaptureCallCount++
        return startBulbCaptureResult
    }

    override suspend fun stopBulbCapture(): Result<CapturedPhoto> {
        stopBulbCaptureCallCount++
        return stopBulbCaptureResult
    }

    override fun startLiveView(): Flow<LiveViewFrame> {
        startLiveViewCallCount++
        return startLiveViewFlow
    }

    override suspend fun stopLiveView(): Result<Boolean> {
        stopLiveViewCallCount++
        return stopLiveViewResult
    }

    override suspend fun autoFocus(): Result<Boolean> {
        autoFocusCallCount++
        return autoFocusResult
    }

    override suspend fun manualFocus(x: Float, y: Float): Result<Boolean> {
        manualFocusCallCount++
        lastManualFocusX = x
        lastManualFocusY = y
        return manualFocusResult
    }

    override suspend fun setFocusPoint(x: Float, y: Float): Result<Boolean> {
        setFocusPointCallCount++
        lastSetFocusPointX = x
        lastSetFocusPointY = y
        return setFocusPointResult
    }

    override fun getCapturedPhotos(): Flow<List<CapturedPhoto>> {
        return flowOf(emptyList())
    }

    override suspend fun getCameraPhotos(): Result<List<CameraPhoto>> {
        getCameraPhotosCallCount++
        return getCameraPhotosResult
    }

    override suspend fun getCameraPhotosPaged(
        page: Int,
        pageSize: Int
    ): Result<PaginatedCameraPhotos> {
        getCameraPhotosPagedCallCount++
        lastCameraPhotosPagedPage = page
        lastCameraPhotosPagedPageSize = pageSize
        return getCameraPhotosPagedResult
    }

    override suspend fun getCameraThumbnail(photoPath: String): Result<ByteArray> {
        getCameraThumbnailCallCount++
        lastGetCameraThumbnailPath = photoPath
        return getCameraThumbnailResult
    }

    override suspend fun deletePhoto(photoId: String): Result<Boolean> {
        deletePhotoCallCount++
        lastDeletePhotoId = photoId
        return deletePhotoResult
    }

    override suspend fun downloadPhotoFromCamera(photoId: String): Result<CapturedPhoto> {
        downloadPhotoFromCameraCallCount++
        lastDownloadPhotoId = photoId
        return downloadPhotoFromCameraResult
    }

    override fun setRawFileRestrictionCallback(callback: ((fileName: String, restrictionMessage: String) -> Unit)?) {
        // No-op for testing
    }

    override fun getCameraAbilitiesInfo(): CameraAbilitiesInfo? {
        return null
    }

    override fun getCameraDeviceInfoDetail(): PtpDeviceInfo? {
        return null
    }

    override suspend fun setSubscriptionTier(tier: SubscriptionTier): Result<Unit> {
        setSubscriptionTierCallCount++
        lastSetSubscriptionTier = tier
        return setSubscriptionTierResult
    }

    override suspend fun setRawFileDownloadEnabled(enabled: Boolean): Result<Unit> {
        setRawFileDownloadEnabledCallCount++
        lastSetRawFileDownloadEnabled = enabled
        return setRawFileDownloadEnabledResult
    }

    override suspend fun isCameraConnectedNow(): Result<Boolean> {
        isCameraConnectedNowCallCount++
        return isCameraConnectedNowResult
    }

    override suspend fun isCameraInitializedNow(): Result<Boolean> {
        isCameraInitializedNowCallCount++
        return isCameraInitializedNowResult
    }

    override suspend fun getCameraFileListNow(): Result<List<String>> {
        getCameraFileListNowCallCount++
        return getCameraFileListNowResult
    }

    // ======================== 테스트 헬퍼 메서드 ========================

    fun setCameraConnected(connected: Boolean) {
        isCameraConnectedFlow.value = connected
    }

    fun setInitializing(initializing: Boolean) {
        isInitializingFlow.value = initializing
    }

    fun setPtpipConnected(connected: Boolean) {
        isPtpipConnectedFlow.value = connected
    }

    fun setEventListenerActive(active: Boolean) {
        isEventListenerActiveFlow.value = active
    }

    fun setCameraFeed(cameras: List<Camera>) {
        cameraFeedFlow.value = cameras
    }

    fun resetCallCounts() {
        connectCameraCallCount = 0
        disconnectCameraCallCount = 0
        capturePhotoCallCount = 0
        getCameraInfoCallCount = 0
        getCameraSettingsCallCount = 0
        getCameraCapabilitiesCallCount = 0
        updateCameraSettingCallCount = 0
        startCameraEventListenerCallCount = 0
        stopCameraEventListenerCallCount = 0
        stopLiveViewCallCount = 0
        autoFocusCallCount = 0
        manualFocusCallCount = 0
        setFocusPointCallCount = 0
        getCameraPhotosCallCount = 0
        getCameraPhotosPagedCallCount = 0
        getCameraThumbnailCallCount = 0
        deletePhotoCallCount = 0
        downloadPhotoFromCameraCallCount = 0
        startBulbCaptureCallCount = 0
        stopBulbCaptureCallCount = 0
        isCameraConnectedNowCallCount = 0
        isCameraInitializedNowCallCount = 0
        getCameraFileListNowCallCount = 0
        setSubscriptionTierCallCount = 0
        setRawFileDownloadEnabledCallCount = 0
    }
}
