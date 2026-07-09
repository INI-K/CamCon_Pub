package com.inik.camcon.presentation.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [FilmEditorActivity] 진입점 팩토리(`startForPhoto`)가 올바른 Intent extra 를 싣는지 회귀.
 *
 * 두 오버로드는 스코프드 스토리지(API29+) own-media 관통의 핵심 계약이다:
 *  - `startForPhoto(context, String)` → [FilmEditorActivity.EXTRA_SOURCE_PATH] (raw 파일경로 진입)
 *  - `startForPhoto(context, Uri)`    → [FilmEditorActivity.EXTRA_SOURCE_URI]  (content URI 진입)
 *
 * 두 extra 가 뒤섞이면 onCreate 의 분기가 잘못된 파이프라인을 타 own-media 진입이 깨진다.
 * 그래서 "각 오버로드는 자기 extra 만 싣고 상대 extra 는 비운다"까지 락인한다.
 * Intent 는 실제 발사 없이 [shadowOf] 로 캡처하므로 대상 Activity 인스턴스화·Hilt 그래프가 필요 없다.
 *
 * 컨텍스트 메모: `startForPhoto` 는 내부에서 `context.startActivity` 를 호출한다.
 * Application 컨텍스트로 호출하면 NEW_TASK 플래그 부재로 AndroidRuntimeException 이 나므로
 * (실사용은 Activity 진입점) Robolectric 로 띄운 순정 [Activity] 를 컨텍스트로 쓴다.
 *
 * application=Application::class: 실제 CamCon Application 의 네이티브 로드를 회피.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FilmEditorActivityIntentTest {

    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    @Test
    fun `Uri 오버로드는 EXTRA_SOURCE_URI 로 uri 문자열을 싣는다`() {
        val uri = Uri.parse("content://media/external/images/media/42")

        FilmEditorActivity.startForPhoto(activity, uri)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(
            uri.toString(),
            started.getStringExtra(FilmEditorActivity.EXTRA_SOURCE_URI)
        )
        assertNull(
            "Uri 진입은 PATH extra 를 싣지 않아야 함(파이프라인 오분기 방지)",
            started.getStringExtra(FilmEditorActivity.EXTRA_SOURCE_PATH)
        )
    }

    @Test
    fun `Path 오버로드는 EXTRA_SOURCE_PATH 로 경로를 싣는다`() {
        val path = "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg"

        FilmEditorActivity.startForPhoto(activity, path)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(
            path,
            started.getStringExtra(FilmEditorActivity.EXTRA_SOURCE_PATH)
        )
        assertNull(
            "Path 진입은 URI extra 를 싣지 않아야 함",
            started.getStringExtra(FilmEditorActivity.EXTRA_SOURCE_URI)
        )
    }

    @Test
    fun `startForPhoto 는 FilmEditorActivity 를 대상으로 한다`() {
        FilmEditorActivity.startForPhoto(activity, Uri.parse("content://x/1"))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(
            FilmEditorActivity::class.java.name,
            started.component?.className
        )
    }
}
