package com.inik.camcon.utils

import android.util.Log

/**
 * Result 타입 확장 함수들
 *
 * 개선: 에러 처리 중복 코드 제거를 위한 공통 유틸리티
 */

/**
 * 안전하게 작업을 실행하고 Result로 래핑
 *
 * @param tag 로그 태그
 * @param operation 작업 설명
 * @param block 실행할 코드 블록
 * @return 성공 시 Result.success, 실패 시 Result.failure
 */
inline fun <T> safeExecute(
    tag: String,
    operation: String,
    block: () -> T
): Result<T> {
    return try {
        LogcatManager.d(tag, "$operation 시작")
        val result = block()
        LogcatManager.d(tag, "$operation 완료")
        Result.success(result)
    } catch (e: Exception) {
        LogcatManager.e(tag, "$operation 실패", e)
        Result.failure(e)
    }
}

/**
 * suspend 함수를 안전하게 실행하고 Result로 래핑
 *
 * @param tag 로그 태그
 * @param operation 작업 설명
 * @param block 실행할 suspend 코드 블록
 * @return 성공 시 Result.success, 실패 시 Result.failure
 */
suspend inline fun <T> safeExecuteSuspend(
    tag: String,
    operation: String,
    crossinline block: suspend () -> T
): Result<T> {
    return try {
        LogcatManager.d(tag, "$operation 시작")
        val result = block()
        LogcatManager.d(tag, "$operation 완료")
        Result.success(result)
    } catch (e: Exception) {
        LogcatManager.e(tag, "$operation 실패", e)
        Result.failure(e)
    }
}

/**
 * Result에서 성공 값을 가져오거나 기본값 반환
 */
fun <T> Result<T>.getOrDefault(default: T): T {
    return getOrElse { default }
}

/**
 * Result에서 성공 값을 가져오거나 null 반환
 */
fun <T> Result<T>.getOrNull(): T? {
    return getOrElse { null }
}

/**
 * Result가 성공인 경우에만 코드 블록 실행
 */
inline fun <T> Result<T>.onSuccessSafe(crossinline action: (T) -> Unit): Result<T> {
    if (isSuccess) {
        action(getOrThrow())
    }
    return this
}

/**
 * Result가 실패인 경우에만 코드 블록 실행
 */
inline fun <T> Result<T>.onFailureSafe(crossinline action: (Throwable) -> Unit): Result<T> {
    exceptionOrNull()?.let(action)
    return this
}
