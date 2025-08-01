package com.inik.camcon.domain.model

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val subscription: Subscription = Subscription()  // 기본값은 FREE 구독
)