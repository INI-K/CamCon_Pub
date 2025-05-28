package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.*
import kotlinx.coroutines.flow.Flow

interface CameraRepository {
    // 카메라 연결 관련
    fun getCameraFeed(): Flow<List<Camera>>
    suspend fun connectCamera(cameraId: String): Result<Boolean>
    suspend fun disconnectCamera(): Result<Boolean>
    fun isCameraConnected(): Flow<Boolean>

    // 카메라 정보
    suspend fun getCameraInfo(): Result<String>
    suspend fun getCameraSettings(): Result<CameraSettings>
    suspend fun updateCameraSetting(key: String, value: String): Result<Boolean>

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

    // 파일 관리
    fun getCapturedPhotos(): Flow<List<CapturedPhoto>>
    suspend fun deletePhoto(photoId: String): Result<Boolean>
    suspend fun downloadPhotoFromCamera(photoId: String): Result<CapturedPhoto>
}
