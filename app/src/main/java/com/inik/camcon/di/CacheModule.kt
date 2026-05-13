package com.inik.camcon.di

import com.inik.camcon.BuildConfig
import com.inik.camcon.data.cache.CacheSweeper
import com.inik.camcon.data.cache.TtlLruProcessedFileCache
import com.inik.camcon.domain.cache.ProcessedFileCache
import com.inik.camcon.utils.LogcatManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * ProcessedFileCache + Clock + CacheSweeper 바인딩 모듈.
 *
 * T5 합의: `object CacheModule + @Provides` 통일. KSP 패턴 호환은 `AppModule` 과 동일.
 *
 * Sweep 주기 1h: 24h TTL 대비 24 회/일 — 핫패스 영향 zero, lazy on read 가 `contains` 시 보강.
 */
@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    private const val TAG = "CacheSweeper"
    private val SWEEP_INTERVAL_MILLIS: Long = TimeUnit.HOURS.toMillis(1)

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideProcessedFileCache(impl: TtlLruProcessedFileCache): ProcessedFileCache = impl

    /**
     * 1h 주기 sweep 코루틴을 [ApplicationScope] 위에서 시작하고 그 Job 을 [CacheSweeper] 에 보관.
     * `CameraCaptureRepositoryImpl` 가 [CacheSweeper] 를 주입받음으로써 앱 시작 직후 트리거된다.
     *
     * - K3 가드: `sweepExpired()` 가 던지는 예외로 while 루프가 죽지 않도록 `runCatching` 으로 감쌌다.
     * - 로그: `LogcatManager` 가 `BuildConfig.DEBUG` 로 자동 가드. 만료 키 자체는 로깅하지 않는다 (PII 회피).
     */
    @Provides
    @Singleton
    fun provideCacheSweeper(
        cache: ProcessedFileCache,
        @ApplicationScope scope: CoroutineScope,
    ): CacheSweeper {
        val job = scope.launch {
            while (isActive) {
                delay(SWEEP_INTERVAL_MILLIS)
                runCatching { cache.sweepExpired() }
                    .onFailure { throwable ->
                        if (BuildConfig.DEBUG) {
                            LogcatManager.w(TAG, "sweepExpired 실패: ${throwable.message}")
                        }
                    }
            }
        }
        return CacheSweeper(job)
    }
}
