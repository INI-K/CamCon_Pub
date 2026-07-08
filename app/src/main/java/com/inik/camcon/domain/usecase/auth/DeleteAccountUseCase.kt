package com.inik.camcon.domain.usecase.auth

import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * 계정·데이터 삭제 UseCase.
 * 서버(Cloud Function deleteAccount)가 Admin SDK로 Firestore 사용자 문서·구독·레퍼럴·구매기록과
 * Firebase Auth 사용자까지 삭제한다([AuthRepository.deleteAccount]). 성공 시 캐시된 구독 티어를
 * 비워 다음 로그인 오염을 막는다(로그아웃 흐름과 동일 정책 — 캐시 정리 실패가 삭제 결과를 뒤집지 않음).
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val appSettingsRepository: AppSettingsRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        val result = authRepository.deleteAccount()
        if (result.isSuccess) {
            runCatching { appSettingsRepository.saveSubscriptionTier(null) }
                .onFailure { if (it is CancellationException) throw it }
        }
        return result
    }
}
