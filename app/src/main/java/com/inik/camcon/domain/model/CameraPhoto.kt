package com.inik.camcon.domain.model

data class CameraPhoto(
    val path: String,
    val name: String,
    val size: Long,
    val date: Long,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailPath: String? = null
)

// 페이징된 카메라 사진 목록
data class PaginatedCameraPhotos(
    val photos: List<CameraPhoto>,
    val currentPage: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
    val hasNext: Boolean
) {
    val hasPrevious: Boolean get() = currentPage > 0
    val isEmpty: Boolean get() = photos.isEmpty()
    val isNotEmpty: Boolean get() = photos.isNotEmpty()
}