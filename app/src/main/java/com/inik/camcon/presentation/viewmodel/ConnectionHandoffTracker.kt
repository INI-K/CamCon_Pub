package com.inik.camcon.presentation.viewmodel

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 연결 성공 후 "카메라 컨트롤 화면으로의 핸드오프"가 진행 중인지 추적하는 앱 전역 싱글톤.
 *
 * 연결을 맺은 [PtpipConnectionActivity]가 finish되거나, `FLAG_ACTIVITY_CLEAR_TOP`으로 스택 중간의
 * SettingsActivity가 파괴되면, 각 Activity 스코프 ViewModel(PtpipViewModel/CameraViewModel)의
 * `onCleared`가 **싱글톤으로 공유되는** 카메라 연결을 disconnect로 끊어버린다.
 * 핸드오프 중에는 이 정리를 건너뛰어 연결을 유지해야 한다.
 *
 * - [begin]: 연결 성공 → 카메라 컨트롤로 이동하기 직전 호출(핸드오프 시작).
 * - [clear]: 사용자가 명시적으로 연결을 끊을 때(disconnect 버튼) 호출.
 */
@Singleton
class ConnectionHandoffTracker @Inject constructor() {
    @Volatile
    var isActive: Boolean = false
        private set

    fun begin() {
        isActive = true
    }

    fun clear() {
        isActive = false
    }
}
