package com.inik.camcon.domain.usecase
import com.inik.camcon.domain.model.ReferralCode
import com.inik.camcon.domain.model.Subscription
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.User
import com.inik.camcon.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
class GetSubscriptionUseCaseTest {
    @Test
    fun `invoke returns FREE when user is null`() = runBlocking {
        val repository = FakeAuthRepository(null)
        val useCase = GetSubscriptionUseCase(repository)
        val subscription = useCase().first()
        assertEquals(SubscriptionTier.FREE, subscription.tier)
    }
    @Test
    fun `getSubscriptionTier returns current user tier`() = runBlocking {
        val repository = FakeAuthRepository(
            User(
                id = "1",
                email = "test@camcon.com",
                displayName = "tester",
                subscription = Subscription(tier = SubscriptionTier.PRO)
            )
        )
        val useCase = GetSubscriptionUseCase(repository)
        val tier = useCase.getSubscriptionTier().first()
        assertEquals(SubscriptionTier.PRO, tier)
    }
    private class FakeAuthRepository(initialUser: User?) : AuthRepository {
        private val userFlow = MutableStateFlow(initialUser)
        override suspend fun signInWithGoogle(idToken: String): Result<User> {
            return Result.failure(UnsupportedOperationException())
        }
        override suspend fun signOut() {}
        override fun getCurrentUser(): Flow<User?> = userFlow
        override suspend fun isUserLoggedIn(): Boolean = userFlow.value != null
        override suspend fun getUserById(userId: String): User? = null
        override suspend fun getAllUsers(): List<User> = emptyList()
        override suspend fun updateUser(user: User): Boolean = false
        override suspend fun updateUserTier(userId: String, tier: SubscriptionTier): Boolean = false
        override suspend fun updateUserReferralCode(userId: String, referralCode: String): Boolean = false
        override suspend fun deactivateUser(userId: String): Boolean = false
        override suspend fun reactivateUser(userId: String): Boolean = false
        override suspend fun getUsersByTier(tier: SubscriptionTier): List<User> = emptyList()
        override suspend fun searchUsers(query: String): List<User> = emptyList()
        override suspend fun getReferralStats(userId: String): Map<String, Any> = emptyMap()
        override suspend fun generateReferralCode(userId: String): String? = null
        override suspend fun createReferralCode(
            code: String,
            tier: SubscriptionTier?,
            description: String?
        ): Boolean = false
        override suspend fun getAllReferralCodes(): List<ReferralCode> = emptyList()
        override suspend fun getAvailableReferralCodes(): List<ReferralCode> = emptyList()
        override suspend fun getUsedReferralCodes(): List<ReferralCode> = emptyList()
        override suspend fun validateReferralCode(code: String): ReferralCode? = null
        override suspend fun useReferralCode(code: String, userId: String): Boolean = false
        override suspend fun deleteReferralCode(code: String): Boolean = false
    }
}