package com.inik.camcon.data.activity

import android.app.Activity
import com.inik.camcon.domain.manager.ActivityProvider
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ActivityProvider] 구현체.
 *
 * 현재 resumed Activity를 [WeakReference]로 보관하여 Activity 누수를 방지한다.
 * CamCon(Application)의 ActivityLifecycleCallbacks가 [onResumed]/[onPaused]/[onDestroyed]에
 * 위임하여 상태를 갱신한다. android.app.Activity 의존을 data 레이어로 격리한다.
 */
@Singleton
class ActivityProviderImpl @Inject constructor() : ActivityProvider {

    private var resumedActivityRef: WeakReference<Activity>? = null

    override fun currentResumedActivity(): Activity? = resumedActivityRef?.get()

    /** Activity가 resumed 상태가 되면 현재 Activity로 갱신. */
    fun onResumed(activity: Activity) {
        resumedActivityRef = WeakReference(activity)
    }

    /** Activity가 paused 상태가 되면 해당 Activity 참조를 해제(누수 방지). */
    fun onPaused(activity: Activity) {
        if (resumedActivityRef?.get() === activity) {
            resumedActivityRef = null
        }
    }

    /** Activity가 destroyed 되면 해당 Activity 참조를 해제(누수 방지). */
    fun onDestroyed(activity: Activity) {
        if (resumedActivityRef?.get() === activity) {
            resumedActivityRef = null
        }
    }
}
