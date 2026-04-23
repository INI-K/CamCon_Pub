package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeFileDataSource
import com.inik.camcon.domain.model.file.CameraFileInfoModel
import com.inik.camcon.domain.model.file.StorageInfo
import com.inik.camcon.domain.repository.CameraFileRepository
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraFileRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeFileDataSource
) : CameraFileRepository {

    override suspend fun downloadRawFile(folder: String, filename: String): Result<ByteArray> = runCatching {
        nativeDataSource.downloadRawFile(folder, filename)
            ?: throw IllegalStateException("RAW file download failed: $folder/$filename")
    }

    override suspend fun downloadAllRawFiles(folder: String): Result<Int> = runCatching {
        nativeDataSource.downloadAllRawFiles(folder)
    }

    override suspend fun extractRawMetadata(folder: String, filename: String): Result<String> = runCatching {
        nativeDataSource.extractRawMetadata(folder, filename)
            ?: throw IllegalStateException("RAW metadata extraction failed: $folder/$filename")
    }

    override suspend fun extractRawThumbnail(folder: String, filename: String): Result<ByteArray> = runCatching {
        nativeDataSource.extractRawThumbnail(folder, filename)
            ?: throw IllegalStateException("RAW thumbnail extraction failed: $folder/$filename")
    }

    override suspend fun filterRawFiles(folder: String, minSizeMB: Int, maxSizeMB: Int): Result<List<String>> = runCatching {
        nativeDataSource.filterRawFiles(folder, minSizeMB, maxSizeMB)?.toList() ?: emptyList()
    }

    override suspend fun uploadFileToCamera(folder: String, filename: String, data: ByteArray): Result<Boolean> = runCatching {
        nativeDataSource.uploadFileToCamera(folder, filename, data)
    }

    override suspend fun deleteAllFilesInFolder(folder: String): Result<Boolean> = runCatching {
        nativeDataSource.deleteAllFilesInFolder(folder)
    }

    override suspend fun createFolder(parentFolder: String, folderName: String): Result<Boolean> = runCatching {
        nativeDataSource.createCameraFolder(parentFolder, folderName)
    }

    override suspend fun removeFolder(parentFolder: String, folderName: String): Result<Boolean> = runCatching {
        nativeDataSource.removeCameraFolder(parentFolder, folderName)
    }

    override suspend fun readFileChunk(path: String, offset: Long, size: Int): Result<ByteArray> = runCatching {
        nativeDataSource.readFileChunk(path, offset, size)
            ?: throw IllegalStateException("File chunk read failed: $path")
    }

    override suspend fun downloadByObjectHandle(handle: Long): Result<ByteArray> = runCatching {
        nativeDataSource.downloadByObjectHandle(handle)
            ?: throw IllegalStateException("Download by handle failed: $handle")
    }

    override suspend fun getDetailedStorageInfo(): Result<StorageInfo> = runCatching {
        val json = nativeDataSource.getDetailedStorageInfo()
        parseStorageInfo(json)
    }

    override suspend fun initializeCache(): Result<Boolean> = runCatching {
        nativeDataSource.initializeCameraCache()
        true
    }

    override suspend fun invalidateFileCache(): Result<Boolean> = runCatching {
        nativeDataSource.invalidateFileCache()
        true
    }

    override suspend fun getRecentCapturedPaths(maxCount: Int): Result<List<String>> = runCatching {
        nativeDataSource.getRecentCapturedPaths(maxCount)?.toList() ?: emptyList()
    }

    override suspend fun clearRecentCapturedPaths(): Result<Boolean> = runCatching {
        nativeDataSource.clearRecentCapturedPaths()
        true
    }

    override suspend fun setFileInfo(folder: String, filename: String, info: CameraFileInfoModel): Result<Boolean> = runCatching {
        // Will be implemented when C++ JNI binding for setFileInfo is added
        throw UnsupportedOperationException("setFileInfo C++ binding not yet implemented")
    }

    private fun parseStorageInfo(json: String): StorageInfo {
        val obj = JSONObject(json)
        return StorageInfo(
            label = obj.optString("label", ""),
            description = obj.optString("description", ""),
            totalKB = obj.optLong("totalKB", 0),
            freeKB = obj.optLong("freeKB", 0),
            freeImages = obj.optInt("freeImages", 0),
            storageType = obj.optString("storageType", "unknown"),
            accessType = obj.optString("accessType", "unknown"),
            filesystemType = obj.optString("filesystemType", "unknown")
        )
    }
}
