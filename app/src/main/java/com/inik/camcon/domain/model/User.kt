package com.inik.camcon.domain.model

import java.util.Date

/**
 * 사용자 정보 모델
 */
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val subscription: Subscription = Subscription(),  // 기본값은 FREE 구독
    val createdAt: Date? = null,
    val lastLoginAt: Date? = null,
    val isActive: Boolean = true,
    val referralCode: String? = null,  // 추천인 코드
    val referredBy: String? = null,    // 누구에게 추천받았는지
    val totalReferrals: Int = 0,       // 총 추천 수
    val deviceInfo: String? = null,    // 디바이스 정보
    val appVersion: String? = null     // 앱 버전
)