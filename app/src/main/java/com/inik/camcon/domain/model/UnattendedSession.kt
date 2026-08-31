package com.inik.camcon.domain.model

/**
 * 자동 재연결이 지금 어느 단계에 있는가.
 *
 * **[Armed] 가 이 타입의 존재 이유다.** 예전에는 "재연결 중"을 실제 시도가 시작되는 순간
 * ([Attempting])에야 알렸는데, 끊김 감지부터 시도 시작까지 리스너 정지 대기(최대 12초) + 여유
 * 2초가 있다. 그 14초 동안 바깥에서 보면 "아무 일도 안 일어나는 끊김"이라, 무인 수신 서비스가
 * 세션을 끝내 버리고 사용자는 재연결을 못 본 채 촬영을 잃었다. 예약이 잡히는 즉시 [Armed] 로
 * 올려 그 사각을 없앤다.
 */
enum class ReconnectPhase {
    /** 재연결 예약이 없다(연결됐거나, 애초에 시도하지 않는다). */
    Idle,

    /** 예약됐고 아직 시도 전이다(리스너 정지 대기·백오프 대기 포함). */
    Armed,

    /** 실제로 연결을 시도하는 중이다. */
    Attempting,

    /** 더 시도하지 않는다(설정 OFF·대기 초과·USB 교차·재시도 소진·비재시도 실패). */
    GaveUp
}

/**
 * 무인 수신 세션의 수명.
 *
 * **서비스 수명 = "연결됨"이 아니라 "무인 수신 세션"이다.** 끊김은 상태이고, 종료는 최종
 * 포기일 때만 일어난다. 예전에는 연결이 끊기는 즉시 WakeLock 을 놓고 서비스를 세웠는데,
 * 재연결이 곧 성공하는 경우에도 그 사이에 서비스가 죽어 촬영물이 유실됐다.
 *
 * [Reconnecting]·[Recovering] 은 **FGS·WakeLock 을 유지**한다(알림 문구만 바뀐다).
 * [Terminating] 에서만 즉시 해제하고 서비스를 세운다 — idle 잔존 0초.
 */
sealed interface UnattendedSession {

    /** 세션이 없다. */
    data object Idle : UnattendedSession

    /** 카메라가 붙어 있고 수신 중이다. */
    data object Active : UnattendedSession

    /**
     * 끊겼고 다시 붙는 중이다. FGS·WakeLock 유지.
     *
     * @param deadlineMillis 이 시각을 넘기면 포기한다. 대기 상태는 **반드시 상한을 갖는다** —
     *   상한 없는 대기는 배터리를 무한히 먹는다.
     */
    data class Reconnecting(val deadlineMillis: Long) : UnattendedSession

    /** 프로세스가 죽었다 살아나 세션을 되살리는 중이다(2단계에서 사용). FGS·WakeLock 유지. */
    data class Recovering(val deadlineMillis: Long) : UnattendedSession

    /** 세션이 끝났다. 서비스는 이 상태를 보고 즉시 해제·정지한다. */
    data class Terminating(val cause: TerminationCause) : UnattendedSession
}

/**
 * 세션이 끝난 이유.
 *
 * @param notifiesUser 사용자에게 알릴 것인가. 사용자가 스스로 끊은 경우([UserDisconnect])나
 *   앱을 스와이프로 지운 경우([TaskRemoved])는 이미 알고 있으므로 알리지 않는다. 반대로 무인
 *   상태에서 조용히 멈춘 경우는 **알리지 않으면 촬영을 놓친 줄도 모른다.**
 */
enum class TerminationCause(val notifiesUser: Boolean) {
    /** 사용자가 직접 연결을 끊었다. */
    UserDisconnect(false),

    /** 앱을 최근 목록에서 지웠다. */
    TaskRemoved(false),

    /** 재연결을 상한까지 시도했지만 실패했다. */
    ReconnectGaveUp(true),

    /**
     * 끊겼는데 재연결을 시도하지 않는다(자동 재연결 OFF 등).
     *
     * 무인 중 케이블이 빠졌을 때 **유일한 통지 수단**이라 알림을 게시하되, 우선순위는 낮춘다
     * (팀 리드 결정 — 잦은 방해보다 놓침 방지가 우선이지만 시끄러울 이유도 없다).
     */
    LinkLostNoReconnect(true),

    /** 프로세스가 되살아났지만 세션을 복구하지 못했다(2단계). */
    RestartUnrecovered(true),

    /**
     * 프로세스가 되살아났는데 되살릴 세션 자체가 없었다.
     *
     * 사용자가 이미 끝낸 세션이라 알릴 것이 없다. 시스템이 서비스만 재기동한 경우이므로
     * 알림 없이 곧바로 정지해 idle 포그라운드 서비스가 남지 않게 한다.
     */
    RestartNoSession(false)
}

/**
 * 디스크에 남겨 두는 무인 수신 세션의 흔적.
 *
 * 프로세스가 죽으면 메모리의 세션 상태는 함께 사라진다. 시스템이 서비스를 되살렸을 때
 * "원래 무인 수신 중이었는가"를 판단할 근거가 이 기록뿐이라, 세션을 시작할 때 남기고
 * 끝낼 때 지운다(그 두 시점 외에는 쓰지 않는다 — 수신 중 반복 기록은 불필요한 디스크 쓰기다).
 *
 * @param cameraLabel 진단용 표시값이다. 카메라 이름·장치명은 식별 정보이므로 [com.inik.camcon.utils.LogMask]
 *   로 마스킹한 값만 저장한다.
 */
data class PersistedUnattendedSession(
    val startedAtMillis: Long,
    val connectionType: CameraConnectionType,
    val cameraLabel: String
)
