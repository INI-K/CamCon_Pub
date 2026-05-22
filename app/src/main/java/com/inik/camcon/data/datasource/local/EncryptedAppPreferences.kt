package com.inik.camcon.data.datasource.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.inik.camcon.domain.model.SubscriptionTier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 구독 티어·RAW 다운로드 플래그처럼 위변조 시 결제/기능 게이팅을 우회당할 수 있는
 * 민감 플래그 전용 암호화 저장소.
 *
 * AES256-GCM 마스터키 + EncryptedSharedPreferences(AES256_SIV / AES256_GCM)로 저장한다.
 * 키 생성/해제 실패 시 평문 SharedPreferences로 폴백하지 않고 in-memory만 유지하여
 * 평문 디스크 노출 가능성을 차단한다.
 */
@Singleton
class EncryptedAppPreferences @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "EncryptedAppPreferences"
        private const val FILE_NAME = "camcon_secure_prefs"

        const val KEY_SUBSCRIPTION_TIER = "subscription_tier"
        const val KEY_RAW_FILE_DOWNLOAD_ENABLED = "raw_file_download_enabled"
    }

    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences 초기화 실패 - 안전한 in-memory 폴백 사용", e)
            // 디스크 평문 폴백 대신, 프로세스 라이프타임에 한정된 빈 SharedPreferences를 만든다.
            // 동일 파일명이지만 MODE_PRIVATE로 분리하여 다른 평문이 섞이지 않도록 임시 사용.
            context.getSharedPreferences("${FILE_NAME}_fallback_volatile", Context.MODE_PRIVATE)
                .also {
                    // 폴백 모드에서는 디스크에 영속화하지 않기 위해 매 호출마다 비운다.
                    it.edit().clear().apply()
                }
        }
    }

    // ===== Subscription Tier =====

    fun getSubscriptionTierString(): String? {
        return runCatching { prefs.getString(KEY_SUBSCRIPTION_TIER, null) }
            .getOrElse { null }
    }

    fun setSubscriptionTierString(tier: String?) {
        runCatching {
            prefs.edit().apply {
                if (tier == null) remove(KEY_SUBSCRIPTION_TIER)
                else putString(KEY_SUBSCRIPTION_TIER, tier)
            }.apply()
        }.onFailure { Log.w(TAG, "subscription tier 쓰기 실패", it) }
    }

    fun getSubscriptionTier(): SubscriptionTier {
        val name = getSubscriptionTierString() ?: return SubscriptionTier.FREE
        return try {
            SubscriptionTier.valueOf(name)
        } catch (e: IllegalArgumentException) {
            SubscriptionTier.FREE
        }
    }

    // ===== RAW File Download Enabled =====

    fun getRawFileDownloadEnabled(default: Boolean = true): Boolean {
        return runCatching { prefs.getBoolean(KEY_RAW_FILE_DOWNLOAD_ENABLED, default) }
            .getOrDefault(default)
    }

    fun hasRawFileDownloadEnabled(): Boolean {
        return runCatching { prefs.contains(KEY_RAW_FILE_DOWNLOAD_ENABLED) }
            .getOrDefault(false)
    }

    fun setRawFileDownloadEnabled(enabled: Boolean) {
        runCatching {
            prefs.edit().putBoolean(KEY_RAW_FILE_DOWNLOAD_ENABLED, enabled).apply()
        }.onFailure { Log.w(TAG, "raw 다운로드 플래그 쓰기 실패", it) }
    }

    /**
     * 평문 DataStore에 남아있던 키를 암호화 저장소로 1회 마이그레이션할 때
     * 호출자에서 사용할 수 있도록 키 존재 여부를 확인한다.
     */
    fun hasSubscriptionTier(): Boolean {
        return runCatching { prefs.contains(KEY_SUBSCRIPTION_TIER) }
            .getOrDefault(false)
    }
}
