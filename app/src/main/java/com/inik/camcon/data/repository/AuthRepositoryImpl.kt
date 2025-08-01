package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.remote.AuthRemoteDataSource
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authRemoteDataSource: AuthRemoteDataSource
) : AuthRepository {
    
    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val user = authRemoteDataSource.signInWithGoogle(idToken)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun signOut() {
        authRemoteDataSource.signOut()
    }

    override fun getCurrentUser(): Flow<User?> = authRemoteDataSource.getCurrentUser()
    
    override suspend fun isUserLoggedIn(): Boolean {
        return authRemoteDataSource.getCurrentUserOnce() != null
    }
}
