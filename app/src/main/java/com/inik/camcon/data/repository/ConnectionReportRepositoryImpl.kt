package com.inik.camcon.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.inik.camcon.data.datasource.local.ConnectionReportLocalDataSource
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.ConnectionReportMethod
import com.inik.camcon.domain.repository.ConnectionReportRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 성공한 카메라 연결을 익명으로 CF(reportCameraConnection)에 보고한다.
 *
 * - 미로그인 시에는 보고하지 않고 다음 연결에서 재시도한다(uid는 서버 게이트 용도).
 * - 로컬에 이미 보고한 (기종|방식)이면 호출 자체를 생략한다.
 * - 서버 호출이 성공한 경우에만 로컬 mark → 실패는 조용히 삼켜 다음에 재시도(UX 영향 0).
 */
@Singleton
class ConnectionReportRepositoryImpl @Inject constructor(
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth,
    private val local: ConnectionReportLocalDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ConnectionReportRepository {

    private companion object {
        private const val TAG = "ConnectionReport"
        private const val CALLABLE = "reportCameraConnection"
    }

    override suspend fun reportConnection(
        model: String,
        method: ConnectionReportMethod
    ) = withContext(ioDispatcher) {
        if (auth.currentUser == null) {
            Log.d(TAG, "미로그인 상태 — 연결 보고 생략(다음 연결에서 재시도)")
            return@withContext
        }

        val key = "$model|${method.wire}"
        if (local.isReported(key)) {
            return@withContext
        }

        try {
            functions
                .getHttpsCallable(CALLABLE)
                .call(mapOf("model" to model, "method" to method.wire))
                .await()
            local.markReported(key)
            Log.d(TAG, "연결 보고 성공: $key")
        } catch (e: FirebaseFunctionsException) {
            Log.w(TAG, "연결 보고 거부(code=${e.code}) — 미기록, 다음 연결에서 재시도", e)
        } catch (e: Exception) {
            Log.w(TAG, "연결 보고 실패 — 미기록, 다음 연결에서 재시도", e)
        }
    }
}
