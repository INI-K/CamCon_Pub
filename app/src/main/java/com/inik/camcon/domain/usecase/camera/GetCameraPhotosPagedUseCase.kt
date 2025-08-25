package com.inik.camcon.domain.usecase.camera

import android.util.Log
import com.inik.camcon.domain.model.PaginatedCameraPhotos
import com.inik.camcon.domain.repository.CameraRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetCameraPhotosPagedUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    companion object {
        private const val TAG = "GetCameraPhotosPagedUseCase"
    }

    suspend operator fun invoke(page: Int, pageSize: Int = 20): Result<PaginatedCameraPhotos> {
        return try {
            // PTPIP 연결 상태 확인 - 연결된 경우 차단
            val isPtpipConnected = cameraRepository.isPtpipConnected().first()
            if (isPtpipConnected) {
                Log.w(TAG, "⚠️ PTPIP 연결 상태 - 파일 목록 가져오기 차단 (페이지: $page)")
                return Result.failure(Exception("PTPIP 연결 시 사진 미리보기는 지원되지 않습니다.\nUSB 케이블 연결을 사용해주세요."))
            }

            Log.d(TAG, "파일 목록 가져오기 시작 (페이지: $page, 크기: $pageSize)")
            cameraRepository.getCameraPhotosPaged(page, pageSize)
        } catch (e: Exception) {
            Log.e(TAG, "파일 목록 가져오기 실패", e)
            Result.failure(e)
        }
    }
}

class GetCameraThumbnailUseCase @Inject constructor(
    private val cameraRepository: CameraRepository
) {
    suspend operator fun invoke(photoPath: String): Result<ByteArray> {
        return cameraRepository.getCameraThumbnail(photoPath)
    }
}