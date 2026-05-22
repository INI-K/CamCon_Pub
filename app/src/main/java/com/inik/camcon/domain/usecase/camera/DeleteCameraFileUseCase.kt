package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.repository.CameraConfigRepository
import javax.inject.Inject

/**
 * 카메라 SD 카드의 파일을 삭제한다.
 * libgphoto2 `gp_camera_file_delete` 직접 호출 — RAW 보호 정책 등 상위 게이팅은 호출 측 책임.
 *
 * @param folder 카메라 폴더 경로 (예: "/store_00010001/DCIM/100NIKON")
 * @param filename 파일 이름 (예: "DSC_0001.JPG")
 */
class DeleteCameraFileUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(folder: String, filename: String): Result<Unit> =
        repository.deleteCameraFile(folder, filename)
}
