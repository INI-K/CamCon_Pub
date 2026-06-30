package com.inik.camcon

/**
 * JNI 카메라/로그 정리를 앱 종료·재시작 시점에 호출하는 단일 진입점(:app 경계).
 *
 * DI 불가한 static 컨텍스트(Activity companion 의 종료/재시작 헬퍼, Application 종료 경로)
 * 전용이다. Activity/Application 은 멀티모듈에서도 :app 에 잔류하고 [CameraNative] 역시
 * :app/:data-native 경계라 직접 참조가 레이어 위반은 아니나, close 계열 JNI 호출을 이 1파일로
 * 집약해 Wave 3 모듈 분할 시 네이티브 경계를 한 곳에서만 다루도록 한다(PM-01).
 */
internal object NativeLifecycle {

    /** 카메라 세션 + 로그 파일을 동기 종료한다. */
    fun closeCameraAndLog() {
        CameraNative.closeCamera()
        CameraNative.closeLogFile()
    }

    /** 카메라 세션을 비동기 종료한다(완료 콜백). */
    fun closeCameraAsync(callback: CameraCleanupCallback) {
        CameraNative.closeCameraAsync(callback)
    }

    /** 로그 파일을 닫는다. */
    fun closeLogFile() {
        CameraNative.closeLogFile()
    }
}
