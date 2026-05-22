package com.inik.camcon.domain.model

/**
 * 카메라 스토리지 정보 (libgphoto2 CameraStorageInformation 기반)
 *
 * @property totalBytes 전체 용량(바이트)
 * @property freeBytes 남은 공간(바이트)
 * @property imagesFree 남은 촬영 가능 매수 (카메라가 보고할 때만 — 미지원 시 null)
 */
data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val imagesFree: Int? = null
)
