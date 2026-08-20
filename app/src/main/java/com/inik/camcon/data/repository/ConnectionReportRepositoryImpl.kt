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
 * - 서버가 **실제로 기록한**(`recorded: true`) 경우에만 로컬 mark → 실패·중복은 조용히 삼켜
 *   다음에 재시도(UX 영향 0). 서버에 이미 등재된 경우 매 세션 1회 호출이 더 나가지만,
 *   그쪽은 조기 반환이라 쿼터를 차감하지 않고 읽기 1회로 끝난다. 잘못 마킹해서 영구히
 *   보고가 끊기는 쪽이 훨씬 비싸다.
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
            val result = functions
                .getHttpsCallable(CALLABLE)
                .call(mapOf("model" to model, "method" to method.wire))
                .await()

            // ⚠️ 서버가 **실제로 기록했을 때만** 로컬에 마킹한다.
            // 서버는 이미 같은 (기종|방식)이 등재돼 있으면 아무것도 쓰지 않고 ok 를 준다.
            // 그것까지 "보고 완료"로 새기면, 그 뒤 서버 데이터가 지워졌을 때
            // (관리자의 오탐 삭제·컬렉션 초기화) 이 기기는 다시는 보고하지 않는다.
            // 실측 2026-08-20: 집계를 비웠는데 재연결해도 아무것도 안 올라왔다.
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?>
            if (data?.get("recorded") == true) {
                local.markReported(key)
                Log.d(TAG, "연결 보고 기록됨: $key")
            } else {
                Log.d(TAG, "연결 보고 수용(서버에 이미 등재됨) — 로컬 마킹 보류: $key")
            }
        } catch (e: FirebaseFunctionsException) {
            Log.w(TAG, "연결 보고 거부(code=${e.code}) — 미기록, 다음 연결에서 재시도", e)
        } catch (e: Exception) {
            Log.w(TAG, "연결 보고 실패 — 미기록, 다음 연결에서 재시도", e)
        }
    }
}
