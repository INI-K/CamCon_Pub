package com.inik.camcon.domain.manager

import com.inik.camcon.domain.model.TerminationCause
import com.inik.camcon.domain.model.UnattendedSession
import kotlinx.coroutines.flow.StateFlow

/**
 * 무인 수신 세션의 수명을 소유하는 도메인 포트.
 *
 * 연결 상태(`isAnyConnectionActive`)와 **다른 축**이다. 그 필드는 촬영 게이트라
 * "재연결 중"을 섞으면 세션 없는 핸들로 촬영 명령이 통과한다. 여기서는 "무인 수신을 계속할
 * 의사가 있는가"만 다룬다 — 서비스(FGS·WakeLock)의 수명이 이 상태를 따른다.
 */
interface UnattendedSessionManager {

    /** 지금 세션 상태. 서비스가 이 흐름 하나만 보고 FGS·WakeLock·알림을 정한다. */
    val state: StateFlow<UnattendedSession>

    /**
     * 사용자 의사로 세션을 끝낸다(연결 해제·앱 종료). 알리지 않는 종료다.
     *
     * @param cause [TerminationCause.UserDisconnect] 또는 [TerminationCause.TaskRemoved].
     */
    fun endByUser(cause: TerminationCause)

    /**
     * 프로세스가 죽었다 되살아난 뒤 세션을 되살린다.
     *
     * 세션을 [UnattendedSession.Recovering] 으로 올려 서비스가 FGS·WakeLock 을 유지하게 한
     * 다음, 디스크에 남은 세션 기록이 가리키는 방식으로 한 번 재연결한다. 성공하면 연결 상태가
     * 세션을 [UnattendedSession.Active] 로 되돌리고, 실패하면
     * [TerminationCause.RestartUnrecovered] 로 끝난다.
     *
     * @return 복구에 성공했으면 true.
     */
    suspend fun recoverAfterProcessRestart(): Boolean

    /**
     * 종료 사유를 한 번만 꺼내 간다. 알림을 두 번 띄우지 않기 위한 소비형 읽기다.
     *
     * @return 아직 소비되지 않은 사유. 없으면 null.
     */
    fun consumeTermination(): TerminationCause?
}
