package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.domain.model.file.CameraThumbnailResult
import com.inik.camcon.domain.repository.CameraFileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 다수 경로의 썸네일을 배치로 prefetch하여 Flow로 방출한다.
 *
 * 갤러리 첫 페이지를 빠르게 채우거나 라이브 페이지 진입 직전 prefetch에 사용.
 *
 * 단일 JNI 진입 / 단일 카메라 락 획득으로 N장을 직렬 처리하므로,
 * N번 직렬 호출 대비 락 경합과 JNI 경계 비용이 크게 줄어든다.
 *
 * - paths 비어있으면 즉시 종료.
 * - data == null 이면 실패 / 미지원.
 * - IO 오프로드는 [CameraFileRepository] 구현이 담당하며, 호출자는 본인 scope에서 collect한다.
 */
class GetThumbnailsBatchUseCase @Inject constructor(
    private val cameraFileRepository: CameraFileRepository
) {
    /**
     * @param paths 카메라 사진 경로 목록 (예: "/store_00010001/DCIM/100NIKON/DSC_0001.JPG")
     * @return 경로별 썸네일 결과 Flow. `data == null`이면 실패.
     */
    operator fun invoke(paths: List<String>): Flow<CameraThumbnailResult> =
        cameraFileRepository.getThumbnailsBatch(paths)
}
