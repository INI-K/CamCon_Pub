package com.inik.camcon.data.repository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class AppUpdateRepositoryImplTest {
    private val repository = AppUpdateRepositoryImpl()
    @Test
    fun `checkForUpdate returns no update`() = runBlocking {
        val result = repository.checkForUpdate()
        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertFalse(info.isUpdateAvailable)
        assertFalse(info.isUpdateRequired)
        assertEquals(info.currentVersion, info.latestVersion)
    }
    @Test
    fun `startImmediateUpdate returns success`() = runBlocking {
        val result = repository.startImmediateUpdate()
        assertTrue(result.isSuccess)
    }
}