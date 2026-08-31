package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.CameraConnectionType
import com.inik.camcon.domain.model.PersistedUnattendedSession

/**
 * 무인 수신 세션의 흔적을 프로세스 밖에 남긴다.
 *
 * 프로세스가 죽으면 세션 상태는 메모리와 함께 사라지므로, 시스템이 서비스를 되살렸을 때
 * "원래 무인 수신 중이었는가"를 판단할 근거가 필요하다. 그 근거가 이 저장소다.
 *
 * 쓰기는 **세션 시작·종료 두 시점에만** 일어난다. 수신 중 상태를 계속 기록하면 얻는 것 없이
 * 디스크만 쓴다.
 */
interface UnattendedSessionRepository {

    /**
     * 세션 시작을 기록한다.
     *
     * @param cameraLabel 진단 표시용 카메라 이름. 구현이 마스킹해 저장하므로 원문을 넘겨도 된다.
     */
    suspend fun save(connectionType: CameraConnectionType, cameraLabel: String?)

    /** 남아 있는 세션 기록. 세션이 없거나 정상 종료됐으면 null. */
    suspend fun load(): PersistedUnattendedSession?

    /** 세션 기록을 지운다. 종료 시점에 부른다. */
    suspend fun clear()
}
