package com.inik.camcon.domain.model

/**
 * 앱 버전 정보
 */
data class AppVersionInfo(
    val currentVersion: String,
    val latestVersion: String,
    val isUpdateRequired: Boolean,
    val isUpdateAvailable: Boolean
)