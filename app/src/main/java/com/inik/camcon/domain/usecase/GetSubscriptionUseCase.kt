package com.inik.camcon.domain.usecase

import android.util.Log
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 사용자 구독 정보를 가져오는 UseCase
 */
class GetSubscriptionUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {

    companion object {
        private const val TAG = "GetSubscriptionUseCase"
    }

    /**
     * 현재 사용자의 구독 정보를 Flow로 반환
     */
    operator fun invoke(): Flow<Subscription> {
        return authRepository.getCurrentUser().map { user ->
            user?.subscription ?: Subscription(tier = SubscriptionTier.FREE)
        }
    }

    /**
     * 현재 사용자의 구독 등급만 반환
     */
    fun getSubscriptionTier(): Flow<SubscriptionTier> {
        return invoke().map { it.tier }
    }

    /**
     * 구독 상태 동기화 (현재는 아무 작업 안함)
     */
    suspend fun syncSubscriptionStatus() {
        Log.d(TAG, "🔄 구독 상태 동기화 요청 (현재는 Firebase 미구현)")
        // 현재는 Firebase가 구현되지 않아 아무 작업 안함
    }

    /**
     * 현재 사용자 티어를 즉시 로그에 출력하는 유틸리티 함수
     */
    suspend fun logCurrentTier() {
        try {
            // Flow에서 첫 번째 값만 가져와서 로그 출력 (중복 방지)
            val subscription = invoke().first()
            
            Log.i(TAG, "======================================")
            Log.i(TAG, " 현재 사용자 구독 티어 정보")
            Log.i(TAG, "️ 티어: ${subscription.tier}")
            Log.i(TAG, "✨ 지원 기능:")
            when (subscription.tier) {
                SubscriptionTier.FREE -> {
                    Log.i(TAG, "    JPG/JPEG 포맷 지원")
                    Log.i(TAG, "    기본 카메라 제어")
                }

                SubscriptionTier.BASIC -> {
                    Log.i(TAG, "    JPG/JPEG/PNG 포맷 지원")
                    Log.i(TAG, "    기본 카메라 제어")
                    Log.i(TAG, "    배치 처리")
                }

                SubscriptionTier.PRO -> {
                    Log.i(TAG, "    모든 포맷 지원 (RAW 포함)")
                    Log.i(TAG, "    고급 카메라 제어")
                    Log.i(TAG, "    배치 처리")
                    Log.i(TAG, "    고급 필터")
                    Log.i(TAG, "    WebP 내보내기")
                }

                SubscriptionTier.REFERRER -> {
                    Log.i(TAG, "    모든 PRO 기능")
                    Log.i(TAG, "    추천인 혜택")
                    Log.i(TAG, "    우선 고객 지원")
                }

                SubscriptionTier.ADMIN -> {
                    Log.i(TAG, "    모든 기능 접근")
                    Log.i(TAG, "    사용자 관리")
                    Log.i(TAG, "    시스템 관리")
                }
            }
            Log.i(TAG, "======================================")
        } catch (e: Exception) {
            Log.e(TAG, "티어 로그 출력 실패", e)
        }
    }
}