package com.inik.camcon.data.cache

import kotlinx.coroutines.Job

/**
 * `ProcessedFileCache` 의 1h 주기 sweep 코루틴 핸들.
 *
 * 실제 launch 는 `di/CacheModule.provideCacheSweeper` 에서 `@ApplicationScope` 위에 띄운다.
 * 본 클래스는 만들어진 [Job] 을 보관하고 [cancel] 만 제공한다.
 *
 * **`Dispatchers.Default` 점유**: `@ApplicationScope` 는 `SupervisorJob + Dispatchers.Default` 묶음.
 * 1h delay 가 대부분이고 sweep 자체는 LRU 1000 순회로 sub-ms 수준이라 Default 풀 영향은 무시 가능.
 *
 * **Eager 트리거**: `CameraCaptureRepositoryImpl` 가 본 클래스를 주입받아 보유함으로써
 * 앱 시작 직후 `provideCacheSweeper` 가 호출되고 sweep loop 가 시작된다.
 */
class CacheSweeper(
    private val job: Job,
) {
    fun cancel() = job.cancel()
}
