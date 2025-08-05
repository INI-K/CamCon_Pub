package com.inik.camcon.data.datasource.remote

import com.inik.camcon.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRemoteDataSource {
    suspend fun signInWithGoogle(idToken: String): User
    suspend fun signOut()
    fun getCurrentUser(): Flow<User?>
    suspend fun getCurrentUserOnce(): User?
}