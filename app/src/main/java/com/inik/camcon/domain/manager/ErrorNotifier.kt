package com.inik.camcon.domain.manager

import kotlinx.coroutines.flow.SharedFlow

/**
 * data 레이어가 의존하는 에러 보고·USB 분리 이벤트의 최소 도메인 표면.
 *
 * 구현체(ErrorHandlingManager)는 presentation.viewmodel.state에 위치하며,
 * data 레이어는 이 인터페이스만 참조한다 (Clean Architecture 경계 유지).
 */
interface ErrorNotifier {

    /**
     * USB 분리 이벤트 스트림. data 레이어가 collect하여 재연결 흐름을 트리거한다.
     */
    val usbDisconnectedEvent: SharedFlow<Unit>

    /**
     * 일반 에러 이벤트 보고.
     */
    fun emitError(
        type: ErrorType,
        message: String,
        exception: Throwable? = null,
        severity: ErrorSeverity = ErrorSeverity.MEDIUM
    )
}
