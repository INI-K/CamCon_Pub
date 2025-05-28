package com.inik.camcon.domain.usecase.auth

import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<User> {
        return authRepository.signInWithGoogle(idToken)
    }
}