package com.inik.camcon.domain.model

/**
 * 네이티브 libgphoto2 로그 레벨 상수.
 *
 * `CameraNative.GP_LOG_*` 값과 동일하지만, domain 레이어 공용으로 재선언하여
 * Presentation/UseCase가 `com.inik.camcon.CameraNative`를 직접 참조하지 않도록 한다.
 */
object NativeLogLevel {
    const val ERROR: Int = 0
    const val VERBOSE: Int = 1
    const val DEBUG: Int = 2
    const val DATA: Int = 3
    const val ALL: Int = DATA
}
