package com.inik.camcon.data.repository.managers
import android.content.Context
import android.util.Log
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.PaginatedCameraPhotos
import com.inik.camcon.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class PhotoDownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val nativeDataSource: NativeCameraDataSource
) {
    companion object {
        private const val TAG = "PhotoDownloadManager"
    }
    fun getSaveDirectory(): String {
        val dir = File(context.cacheDir, Constants.FilePaths.TEMP_DOWNLOADS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }
    fun handlePhotoDownload(
        photo: CapturedPhoto,
        fullPath: String,
        fileName: String,
        cameraCapabilities: CameraCapabilities?,
        cameraSettings: CameraSettings?,
        onPhotoDownloaded: (CapturedPhoto) -> Unit,
        onDownloadFailed: (String) -> Unit
    ) {
        Log.d(TAG, "다운로드 처리: ${cameraCapabilities?.model ?: "unknown"}")
        cameraSettings?.iso
        val data = nativeDataSource.downloadCameraPhoto(fullPath)
        if (data == null || data.isEmpty()) {
            onDownloadFailed(fileName)
            return
        }
        val file = File(getSaveDirectory(), fileName)
        file.writeBytes(data)
        onPhotoDownloaded(
            photo.copy(
                filePath = file.absolutePath,
                size = file.length(),
                isDownloading = false,
                downloadCompleteTime = System.currentTimeMillis()
            )
        )
    }
    fun downloadPhotoFromCamera(
        photoId: String,
        cameraCapabilities: CameraCapabilities?,
        cameraSettings: CameraSettings?
    ): Result<CapturedPhoto> {
        return runCatching {
            val latest = nativeDataSource.getLatestCameraFile()
                ?: throw IllegalStateException("카메라 파일이 없습니다")
            val bytes = nativeDataSource.downloadCameraPhoto(latest.path)
                ?: throw IllegalStateException("카메라 파일 다운로드 실패")
            val file = File(getSaveDirectory(), latest.name.ifBlank { "${UUID.randomUUID()}.jpg" })
            file.writeBytes(bytes)
            CapturedPhoto(
                id = photoId,
                filePath = file.absolutePath,
                thumbnailPath = null,
                captureTime = latest.date,
                cameraModel = cameraCapabilities?.model ?: "알 수 없음",
                settings = cameraSettings,
                size = file.length(),
                width = latest.width,
                height = latest.height,
                isDownloading = false,
                downloadCompleteTime = System.currentTimeMillis()
            )
        }
    }
    fun getCameraPhotos(): Result<List<CameraPhoto>> {
        return runCatching {
            nativeDataSource.getCameraPhotos().map {
                CameraPhoto(
                    path = it.path,
                    name = it.name,
                    size = it.size,
                    date = it.date,
                    width = it.width,
                    height = it.height,
                    thumbnailPath = it.thumbnailPath
                )
            }
        }
    }
    suspend fun getCameraPhotosPaged(
        page: Int,
        pageSize: Int,
        isPhotoPreviewMode: Boolean,
        onEventListenerRestart: suspend () -> Unit
    ): Result<PaginatedCameraPhotos> {
        return runCatching {
            val paged = nativeDataSource.getCameraPhotosPaged(page, pageSize)
            if (!isPhotoPreviewMode) onEventListenerRestart()
            PaginatedCameraPhotos(
                photos = paged.photos.map {
                    CameraPhoto(
                        path = it.path,
                        name = it.name,
                        size = it.size,
                        date = it.date,
                        width = it.width,
                        height = it.height,
                        thumbnailPath = it.thumbnailPath
                    )
                },
                currentPage = paged.currentPage,
                pageSize = paged.pageSize,
                totalItems = paged.totalItems,
                totalPages = paged.totalPages,
                hasNext = paged.hasNext
            )
        }
    }
    fun getCameraThumbnail(
        photoPath: String,
        isConnected: Boolean,
        isInitializing: Boolean,
        isNativeCameraConnected: Boolean
    ): Result<ByteArray> {
        return runCatching {
            if (!isConnected || isInitializing || !isNativeCameraConnected) {
                throw IllegalStateException("카메라 썸네일 요청 조건이 충족되지 않았습니다")
            }
            nativeDataSource.getCameraThumbnail(photoPath)
                ?: throw IllegalStateException("썸네일 생성 실패")
        }.onFailure {
            Log.w(TAG, "썸네일 가져오기 실패: $photoPath", it)
        }
    }
}