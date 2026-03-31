package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.CameraNative
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeFileDataSource @Inject constructor() {

    fun downloadRawFile(folder: String, filename: String): ByteArray? =
        CameraNative.downloadRawFile(folder, filename)

    fun downloadAllRawFiles(folder: String): Int = CameraNative.downloadAllRawFiles(folder)

    fun extractRawMetadata(folder: String, filename: String): String? =
        CameraNative.extractRawMetadata(folder, filename)

    fun extractRawThumbnail(folder: String, filename: String): ByteArray? =
        CameraNative.extractRawThumbnail(folder, filename)

    fun filterRawFiles(folder: String, minSizeMB: Int, maxSizeMB: Int): Array<String>? =
        CameraNative.filterRawFiles(folder, minSizeMB, maxSizeMB)

    fun uploadFileToCamera(folder: String, filename: String, data: ByteArray): Boolean =
        CameraNative.uploadFileToCamera(folder, filename, data)

    fun deleteAllFilesInFolder(folder: String): Boolean =
        CameraNative.deleteAllFilesInFolder(folder)

    fun createCameraFolder(parentFolder: String, folderName: String): Boolean =
        CameraNative.createCameraFolder(parentFolder, folderName)

    fun removeCameraFolder(parentFolder: String, folderName: String): Boolean =
        CameraNative.removeCameraFolder(parentFolder, folderName)

    fun readFileChunk(path: String, offset: Long, size: Int): ByteArray? =
        CameraNative.readFileChunk(path, offset, size)

    fun downloadByObjectHandle(handle: Long): ByteArray? =
        CameraNative.downloadByObjectHandle(handle)

    fun getDetailedStorageInfo(): String = CameraNative.getDetailedStorageInfo()

    fun initializeCameraCache() = CameraNative.initializeCameraCache()

    fun invalidateFileCache() = CameraNative.invalidateFileCache()

    fun getRecentCapturedPaths(maxCount: Int): Array<String>? =
        CameraNative.getRecentCapturedPaths(maxCount)

    fun clearRecentCapturedPaths() = CameraNative.clearRecentCapturedPaths()

    fun setHandlePathMapping(handle: Long, path: String) =
        CameraNative.setHandlePathMapping(handle, path)

    fun clearHandlePathMapping() = CameraNative.clearHandlePathMapping()

    fun getObjectInfoCached(path: String): String = CameraNative.getObjectInfoCached(path)

    companion object {
        private const val TAG = "NativeFileDS"
    }
}
