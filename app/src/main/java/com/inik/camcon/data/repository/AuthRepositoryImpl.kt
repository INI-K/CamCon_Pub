package com.inik.camcon.data.repository

import com.inik.camcon.data.datasource.remote.AuthRemoteDataSource
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authRemoteDataSource: AuthRemoteDataSource
) : AuthRepository {
    
    private val _currentUser = MutableStateFlow<User?>(null)
    
    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            val user = authRemoteDataSource.signInWithGoogle(idToken)
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun signOut() {
        authRemoteDataSource.signOut()
        _currentUser.value = null
    }
    
    override fun getCurrentUser(): Flow<User?> = _currentUser.asStateFlow()
    
    override suspend fun isUserLoggedIn(): Boolean {
        return _currentUser.value != null
    }
}
