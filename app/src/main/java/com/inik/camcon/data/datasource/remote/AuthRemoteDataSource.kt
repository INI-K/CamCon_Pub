package com.inik.camcon.data.datasource.remote

import com.inik.camcon.domain.model.User

interface AuthRemoteDataSource {
    suspend fun signInWithGoogle(idToken: String): User
    suspend fun signOut()
}