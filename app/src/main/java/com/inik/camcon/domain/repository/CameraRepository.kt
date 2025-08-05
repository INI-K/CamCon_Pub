package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.BracketingSettings
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.domain.model.TimelapseSettings
import kotlinx.coroutines.flow.Flow

interface CameraRepository {
    // 카메라 연결 관련
    fun getCameraFeed(): Flow<List<Camera>>
    suspend fun connectCamera(cameraId: String): Result<Boolean>
    suspend fun disconnectCamera(): Result<Boolean>
    fun isCameraConnected(): Flow<Boolean>
    fun isInitializing(): Flow<Boolean>

    // 카메라 정보
    suspend fun getCameraInfo(): Result<String>
    suspend fun getCameraSettings(): Result<CameraSettings>
    suspend fun getCameraCapabilities(): Result<CameraCapabilities?>
    suspend fun updateCameraSetting(key: String, value: String): Result<Boolean>

    // 이벤트 리스너 관련
    suspend fun startCameraEventListener(): Result<Boolean>

    /**
     * 카메라 이벤트 리스너 종료
     */
    suspend fun stopCameraEventListener(): Result<Boolean>

    /**
     * 사진 미리보기 모드 설정 (이벤트 리스너 자동 시작 방지용)
     */
    fun setPhotoPreviewMode(enabled: Boolean)
    fun isEventListenerActive(): Flow<Boolean>

    // 촬영 관련
    suspend fun capturePhoto(mode: ShootingMode = ShootingMode.SINGLE): Result<CapturedPhoto>
    fun startBurstCapture(count: Int): Flow<CapturedPhoto>
    fun startTimelapse(settings: TimelapseSettings): Flow<CapturedPhoto>
    fun startBracketing(settings: BracketingSettings): Flow<CapturedPhoto>
    suspend fun startBulbCapture(): Result<Boolean>
    suspend fun stopBulbCapture(): Result<CapturedPhoto>

    // 라이브뷰
    fun startLiveView(): Flow<LiveViewFrame>
    suspend fun stopLiveView(): Result<Boolean>

    // 포커스 제어
    suspend fun autoFocus(): Result<Boolean>
    suspend fun manualFocus(x: Float, y: Float): Result<Boolean>
    suspend fun setFocusPoint(x: Float, y: Float): Result<Boolean>

    // 사진 관리
    fun getCapturedPhotos(): Flow<List<CapturedPhoto>>
    suspend fun getCameraPhotos(): Result<List<com.inik.camcon.domain.model.CameraPhoto>>
    suspend fun getCameraPhotosPaged(
        page: Int,
        pageSize: Int = 20
    ): Result<com.inik.camcon.domain.model.PaginatedCameraPhotos>

    suspend fun getCameraThumbnail(photoPath: String): Result<ByteArray>
    suspend fun deletePhoto(photoId: String): Result<Boolean>
    suspend fun downloadPhotoFromCamera(photoId: String): Result<CapturedPhoto>
}
