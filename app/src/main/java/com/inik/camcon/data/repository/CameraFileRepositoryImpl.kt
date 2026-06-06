package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.nativesource.NativeFileDataSource
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.file.CameraFileInfoModel
import com.inik.camcon.domain.model.file.CameraThumbnailResult
import com.inik.camcon.domain.model.file.StorageInfo
import com.inik.camcon.domain.repository.CameraFileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraFileRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeFileDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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
        // setFileInfo C++ JNI 바인딩이 추가되면 구현 예정
        throw UnsupportedOperationException("setFileInfo C++ binding not yet implemented")
    }

    /**
     * 다수 경로의 썸네일을 배치로 prefetch하여 Flow로 방출한다.
     *
     * 단일 JNI 진입 / 단일 카메라 락 획득으로 N장을 직렬 처리하므로,
     * N번 직렬 호출 대비 락 경합과 JNI 경계 비용이 크게 줄어든다.
     *
     * - paths 비어있으면 즉시 종료.
     * - data == null 이면 실패 / 미지원.
     * - [ioDispatcher]로 오프로드되며, 호출자는 본인 scope에서 collect한다.
     */
    override fun getThumbnailsBatch(paths: List<String>): Flow<CameraThumbnailResult> = callbackFlow {
        if (paths.isEmpty()) {
            close()
            return@callbackFlow
        }

        nativeDataSource.getCameraThumbnailBatch(paths) { path, data ->
            // callback은 네이티브 호출 스레드 = collector(IO) 스레드에서 동기 호출됨.
            // trySendBlocking으로 backpressure 대응.
            trySendBlocking(CameraThumbnailResult(path, data))
        }
        close()
        awaitClose { /* 네이티브 호출은 동기 완료. 별도 정리 불필요. */ }
    }.flowOn(ioDispatcher)

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
