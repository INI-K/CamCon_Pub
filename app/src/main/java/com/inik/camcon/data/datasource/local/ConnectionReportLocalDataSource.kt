package com.inik.camcon.data.datasource.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.connectionReportsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "connection_reports")

/**
 * 이미 서버에 보고한 (기종|방식) 키를 로컬에 기억해 중복 보고를 막는다.
 * 서버 호출이 성공한 뒤에만 mark 되므로, 실패한 보고는 다음 연결에서 재시도된다.
 */
@Singleton
class ConnectionReportLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val REPORTED = stringSetPreferencesKey("reported_model_method")
    }

    suspend fun isReported(key: String): Boolean {
        val snapshot = context.connectionReportsDataStore.data.first()
        return (snapshot[REPORTED] ?: emptySet()).contains(key)
    }

    suspend fun markReported(key: String) {
        context.connectionReportsDataStore.edit {
            it[REPORTED] = (it[REPORTED] ?: emptySet()) + key
        }
    }
}
