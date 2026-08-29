package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.file.CameraFileInfoModel
import com.inik.camcon.domain.model.file.CameraThumbnailResult
import com.inik.camcon.domain.model.file.DetailedStorageInfo
import kotlinx.coroutines.flow.Flow

interface CameraFileRepository {
    suspend fun downloadRawFile(folder: String, filename: String): Result<ByteArray>
    suspend fun downloadAllRawFiles(folder: String): Result<Int>
    suspend fun extractRawMetadata(folder: String, filename: String): Result<String>
    suspend fun extractRawThumbnail(folder: String, filename: String): Result<ByteArray>
    suspend fun filterRawFiles(folder: String, minSizeMB: Int, maxSizeMB: Int): Result<List<String>>
    suspend fun uploadFileToCamera(folder: String, filename: String, data: ByteArray): Result<Boolean>
    suspend fun deleteAllFilesInFolder(folder: String): Result<Boolean>
    suspend fun createFolder(parentFolder: String, folderName: String): Result<Boolean>
    suspend fun removeFolder(parentFolder: String, folderName: String): Result<Boolean>
    suspend fun readFileChunk(path: String, offset: Long, size: Int): Result<ByteArray>
    suspend fun downloadByObjectHandle(handle: Long): Result<ByteArray>
    suspend fun getDetailedStorageInfo(): Result<DetailedStorageInfo>
    suspend fun initializeCache(): Result<Boolean>
    suspend fun invalidateFileCache(): Result<Boolean>

    /**
     * 소니 콘텐츠 전송 모드를 켜고 끈다.
     *
     * 켜면 카메라가 메모리 카드를 스토어로 노출해 기존 목록·다운로드 경로가 동작하고, 켜져
     * 있는 동안은 촬영·라이브뷰가 카메라 쪽에서 막힌다. 지원하지 않는 카메라에서는 실패를
     * 반환하며, 그 판정 비용은 왕복 한 번이다.
     */
    suspend fun setSonyContentsTransferMode(enabled: Boolean): Result<Boolean>
    suspend fun getRecentCapturedPaths(maxCount: Int): Result<List<String>>
    suspend fun clearRecentCapturedPaths(): Result<Boolean>
    suspend fun setFileInfo(folder: String, filename: String, info: CameraFileInfoModel): Result<Boolean>
    fun getThumbnailsBatch(paths: List<String>): Flow<CameraThumbnailResult>
}
