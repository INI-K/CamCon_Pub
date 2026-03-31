package com.inik.camcon.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 앱 설정에 대한 Repository 인터페이스.
 * Domain 레이어에서 Data 레이어 직접 참조 없이 설정에 접근한다.
 */
interface AppSettingsRepository {
    val isRawFileDownloadEnabled: Flow<Boolean>
}
