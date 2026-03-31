package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.file.StorageInfo
import com.inik.camcon.domain.usecase.file.*
import com.inik.camcon.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraFileManager @Inject constructor(
    private val downloadRawFileUseCase: DownloadRawFileUseCase,
    private val downloadAllRawFilesUseCase: DownloadAllRawFilesUseCase,
    private val extractRawMetadataUseCase: ExtractRawMetadataUseCase,
    private val extractRawThumbnailUseCase: ExtractRawThumbnailUseCase,
    private val filterRawFilesUseCase: FilterRawFilesUseCase,
    private val uploadFileToCameraUseCase: UploadFileToCameraUseCase,
    private val deleteAllFilesInFolderUseCase: DeleteAllFilesInFolderUseCase,
    private val createFolderUseCase: CreateFolderUseCase,
    private val removeFolderUseCase: RemoveFolderUseCase,
    private val readFileChunkUseCase: ReadFileChunkUseCase,
    private val downloadByObjectHandleUseCase: DownloadByObjectHandleUseCase,
    private val getDetailedStorageInfoUseCase: GetDetailedStorageInfoUseCase,
    private val initializeCacheUseCase: InitializeCacheUseCase,
    private val invalidateFileCacheUseCase: InvalidateFileCacheUseCase,
    private val getRecentCapturedPathsUseCase: GetRecentCapturedPathsUseCase,
    private val clearRecentCapturedPathsUseCase: ClearRecentCapturedPathsUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo: StateFlow<StorageInfo?> = _storageInfo.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    fun refreshStorageInfo() {
        scope.launch {
            getDetailedStorageInfoUseCase().onSuccess {
                _storageInfo.value = it
            }
        }
    }

    suspend fun downloadRawFile(folder: String, filename: String): Result<ByteArray> =
        downloadRawFileUseCase(folder, filename)

    fun downloadAllRawFiles(folder: String) {
        scope.launch {
            _isOperating.value = true
            downloadAllRawFilesUseCase(folder)
            _isOperating.value = false
        }
    }

    suspend fun extractRawMetadata(folder: String, filename: String): Result<String> =
        extractRawMetadataUseCase(folder, filename)

    suspend fun extractRawThumbnail(folder: String, filename: String): Result<ByteArray> =
        extractRawThumbnailUseCase(folder, filename)

    suspend fun filterRawFiles(folder: String, minSizeMB: Int, maxSizeMB: Int): Result<List<String>> =
        filterRawFilesUseCase(folder, minSizeMB, maxSizeMB)

    fun uploadFileToCamera(folder: String, filename: String, data: ByteArray) {
        scope.launch {
            _isOperating.value = true
            uploadFileToCameraUseCase(folder, filename, data)
            _isOperating.value = false
        }
    }

    fun deleteAllFilesInFolder(folder: String) {
        scope.launch {
            deleteAllFilesInFolderUseCase(folder)
        }
    }

    fun createFolder(parentFolder: String, folderName: String) {
        scope.launch { createFolderUseCase(parentFolder, folderName) }
    }

    fun removeFolder(parentFolder: String, folderName: String) {
        scope.launch { removeFolderUseCase(parentFolder, folderName) }
    }

    suspend fun readFileChunk(path: String, offset: Long, size: Int): Result<ByteArray> =
        readFileChunkUseCase(path, offset, size)

    suspend fun downloadByObjectHandle(handle: Long): Result<ByteArray> =
        downloadByObjectHandleUseCase(handle)

    fun initializeCache() {
        scope.launch { initializeCacheUseCase() }
    }

    fun invalidateFileCache() {
        scope.launch { invalidateFileCacheUseCase() }
    }

    suspend fun getRecentCapturedPaths(maxCount: Int): Result<List<String>> =
        getRecentCapturedPathsUseCase(maxCount)

    fun clearRecentCapturedPaths() {
        scope.launch { clearRecentCapturedPathsUseCase() }
    }
}
