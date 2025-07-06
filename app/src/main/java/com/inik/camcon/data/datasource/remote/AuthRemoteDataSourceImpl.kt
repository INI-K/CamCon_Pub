package com.inik.camcon.data.datasource.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import com.inik.camcon.domain.model.User
import javax.inject.Inject

class AuthRemoteDataSourceImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRemoteDataSource {
    override suspend fun signInWithGoogle(idToken: String): User {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val firebaseUser = authResult.user ?: throw Exception("구글 로그인 실패: Firebase 사용자가 null입니다")
        
        return User(
            id = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            displayName = firebaseUser.displayName ?: "",
            photoUrl = firebaseUser.photoUrl?.toString()
        )
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }
}
