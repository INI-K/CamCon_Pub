package com.inik.camcon.domain.manager.fake

import com.inik.camcon.domain.manager.NativeErrorCallbackRegistrar

/**
 * 네이티브 에러 콜백 등록기 Fake.
 *
 * 실제 구현은 JNI 콜백을 등록하지만, 테스트에서는 [ErrorHandlingManager] 가 넘긴 콜백을 보관했다가
 * [trigger] 로 임의 시점에 발화시켜 네이티브 에러 라우팅 분기를 격리 검증한다.
 */
class FakeNativeErrorCallbackRegistrar :
    NativeErrorCallbackRegistrar {

    private var callback: ((errorCode: Int, errorMessage: String) -> Unit)? = null

    /** registerErrorCallback 이 호출된 횟수(재등록 가드 검증용). */
    var registerCount = 0
        private set

    /** unregisterErrorCallback 이 호출된 횟수. */
    var unregisterCount = 0
        private set

    val isRegistered: Boolean get() = callback != null

    override fun registerErrorCallback(onError: (errorCode: Int, errorMessage: String) -> Unit) {
        callback = onError
        registerCount++
    }

    override fun unregisterErrorCallback() {
        callback = null
        unregisterCount++
    }

    /** 네이티브가 에러를 통지한 상황을 모사한다. 등록 전이면 no-op. */
    fun trigger(errorCode: Int, errorMessage: String) {
        callback?.invoke(errorCode, errorMessage)
    }
}
