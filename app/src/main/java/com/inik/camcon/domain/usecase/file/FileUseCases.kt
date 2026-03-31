package com.inik.camcon.domain.usecase.file

import com.inik.camcon.domain.model.file.CameraFileInfoModel
import com.inik.camcon.domain.model.file.StorageInfo
import com.inik.camcon.domain.repository.CameraFileRepository
import javax.inject.Inject

class DownloadRawFileUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(folder: String, filename: String): Result<ByteArray> =
        repository.downloadRawFile(folder, filename)
}

class DownloadAllRawFilesUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(folder: String): Result<Int> = repository.downloadAllRawFiles(folder)
}

class ExtractRawMetadataUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(folder: String, filename: String): Result<String> =
        repository.extractRawMetadata(folder, filename)
}

class ExtractRawThumbnailUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(folder: String, filename: String): Result<ByteArray> =
        repository.extractRawThumbnail(folder, filename)
}

class FilterRawFilesUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(folder: String, minSizeMB: Int, maxSizeMB: Int): Result<List<String>> =
        repository.filterRawFiles(folder, minSizeMB, maxSizeMB)
}

class UploadFileToCameraUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(folder: String, filename: String, data: ByteArray): Result<Boolean> =
        repository.uploadFileToCamera(folder, filename, data)
}

class DeleteAllFilesInFolderUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(folder: String): Result<Boolean> = repository.deleteAllFilesInFolder(folder)
}

class CreateFolderUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(parentFolder: String, folderName: String): Result<Boolean> =
        repository.createFolder(parentFolder, folderName)
}

class RemoveFolderUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(parentFolder: String, folderName: String): Result<Boolean> =
        repository.removeFolder(parentFolder, folderName)
}

class ReadFileChunkUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(path: String, offset: Long, size: Int): Result<ByteArray> =
        repository.readFileChunk(path, offset, size)
}

class DownloadByObjectHandleUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(handle: Long): Result<ByteArray> = repository.downloadByObjectHandle(handle)
}

class GetDetailedStorageInfoUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(): Result<StorageInfo> = repository.getDetailedStorageInfo()
}

class InitializeCacheUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.initializeCache()
}

class InvalidateFileCacheUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.invalidateFileCache()
}

class GetRecentCapturedPathsUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(maxCount: Int): Result<List<String>> = repository.getRecentCapturedPaths(maxCount)
}

class ClearRecentCapturedPathsUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(): Result<Boolean> = repository.clearRecentCapturedPaths()
}

class SetFileInfoUseCase @Inject constructor(
    private val repository: CameraFileRepository
) {
    suspend operator fun invoke(folder: String, filename: String, info: CameraFileInfoModel): Result<Boolean> =
        repository.setFileInfo(folder, filename, info)
}
