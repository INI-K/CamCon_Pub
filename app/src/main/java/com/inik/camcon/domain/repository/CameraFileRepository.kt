package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.file.CameraFileInfoModel
import com.inik.camcon.domain.model.file.StorageInfo

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
    suspend fun getDetailedStorageInfo(): Result<StorageInfo>
    suspend fun initializeCache(): Result<Boolean>
    suspend fun invalidateFileCache(): Result<Boolean>
    suspend fun getRecentCapturedPaths(maxCount: Int): Result<List<String>>
    suspend fun clearRecentCapturedPaths(): Result<Boolean>
    suspend fun setFileInfo(folder: String, filename: String, info: CameraFileInfoModel): Result<Boolean>
}
