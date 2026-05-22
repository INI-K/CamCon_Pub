package com.inik.camcon.domain.usecase.camera

import com.inik.camcon.data.datasource.nativesource.NativeFileDataSource
import com.inik.camcon.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
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
 * - 본 UseCase는 [ioDispatcher]로 오프로드되며, 호출자는 본인 scope에서 collect한다.
 */
class GetThumbnailsBatchUseCase @Inject constructor(
    private val nativeFileDataSource: NativeFileDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * @param paths 카메라 사진 경로 목록 (예: "/store_00010001/DCIM/100NIKON/DSC_0001.JPG")
     * @return 경로별 썸네일 결과 Flow. `data == null`이면 실패.
     */
    operator fun invoke(paths: List<String>): Flow<Result> = callbackFlow {
        if (paths.isEmpty()) {
            close()
            return@callbackFlow
        }

        nativeFileDataSource.getCameraThumbnailBatch(paths) { path, data ->
            // callback은 네이티브 호출 스레드 = collector(IO) 스레드에서 동기 호출됨.
            // trySendBlocking으로 backpressure 대응.
            trySendBlocking(Result(path, data))
        }
        close()
        awaitClose { /* 네이티브 호출은 동기 완료. 별도 정리 불필요. */ }
    }.flowOn(ioDispatcher)

    /** 배치 썸네일 결과 1건. */
    data class Result(val path: String, val data: ByteArray?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Result) return false
            if (path != other.path) return false
            if (data == null) return other.data == null
            if (other.data == null) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = path.hashCode()
            result = 31 * result + (data?.contentHashCode() ?: 0)
            return result
        }
    }
}
