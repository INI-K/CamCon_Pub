package com.inik.camcon.domain.model.file

data class StorageInfo(
    val label: String,
    val description: String,
    val totalKB: Long,
    val freeKB: Long,
    val freeImages: Int,
    val storageType: String,
    val accessType: String,
    val filesystemType: String
)

data class RawFileInfo(
    val folder: String,
    val filename: String,
    val sizeMB: Int
)

data class CameraFileInfoModel(
    val permissions: Int,
    val mtime: Long,
    val width: Int,
    val height: Int
)

data class FileTransferProgress(
    val current: Float,
    val total: Float,
    val percentage: Int
)
