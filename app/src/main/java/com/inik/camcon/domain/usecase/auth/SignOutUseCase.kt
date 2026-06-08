package com.inik.camcon.domain.usecase.auth

import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val appSettingsRepository: AppSettingsRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            authRepository.signOut()
            // 계정 전환 시 이전 사용자의 상위 티어가 새 사용자에게 상속되지 않도록
            // 캐시된 구독 티어를 비운다(게이팅 우회·크로스계정 오염 방지 — H13).
            // 다음 로그인 시 GetSubscriptionUseCase 가 서버에서 재조회한다.
            // 인증 로그아웃은 이미 성공했으므로 캐시 정리 실패가 로그아웃을 실패로 만들지 않도록 분리한다.
            runCatching { appSettingsRepository.saveSubscriptionTier(null) }
                .onFailure { if (it is CancellationException) throw it }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}