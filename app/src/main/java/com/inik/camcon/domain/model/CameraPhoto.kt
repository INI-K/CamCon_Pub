package com.inik.camcon.domain.model

data class CameraPhoto(
    val path: String,
    val name: String,
    val size: Long,
    val date: Long,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailPath: String? = null,
    /**
     * MediaStore content URI 문자열(로컬 갤러리 own-media 전용, 기본 null=하위호환).
     * 스코프드 스토리지(API29+)에서 raw File 경로 접근이 막힐 때 로드/EXIF/공유를 이 URI 로 관통한다.
     * 카메라 캡처분은 null 로 두고 기존 File 경로를 사용한다.
     */
    val uri: String? = null
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