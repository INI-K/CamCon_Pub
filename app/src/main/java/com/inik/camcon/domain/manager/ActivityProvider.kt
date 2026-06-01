package com.inik.camcon.domain.manager

import android.app.Activity

/**
 * 현재 resumed 상태인 Activity를 제공하는 도메인 표면.
 *
 * UseCase/Repository public API에서 Activity 파라미터 노출을 제거하기 위한 추상화.
 * 'Activity 획득 책임'을 호출자가 아닌 data 레이어 구현체(ActivityProviderImpl)에 위임한다.
 *
 * 구현체는 ActivityLifecycleCallbacks로 resumed Activity를 WeakReference로 추적하며,
 * 결제 시트 호출 등 Activity 컨텍스트가 필요한 시점에 현재 화면을 반환한다.
 */
interface ActivityProvider {

    /**
     * 현재 resumed 상태인 Activity. 포그라운드 Activity가 없으면 null.
     */
    fun currentResumedActivity(): Activity?
}
