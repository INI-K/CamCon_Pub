package com.inik.camcon.data.datasource

import android.app.Application
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * [Libgphoto2PluginInstaller] 존재 판정·멱등 보증 회귀.
 *
 * 자동 검증 범위(파일시스템 경계 — Robolectric temp dir 로 모사):
 *  - [Libgphoto2PluginInstaller.arePluginsPresent] 는 iolib·camlib 두 디렉토리에 각각 .so 가
 *    하나 이상 있을 때만 true (한쪽만·둘 다 비었을 때 false, .so 아닌 파일은 무시).
 *  - [Libgphoto2PluginInstaller.ensurePluginDirs] 는 이미 플러그인이 있으면 재추출 없이
 *    기존 파일을 보존하고 [pluginDirArg] 를 반환(멱등).
 *  - [Libgphoto2PluginInstaller.pluginDirArg] 는 `iolib:camlib` 콜론 구분 규약을 지킨다.
 *
 * 자동 불가(명시): 실제 APK(+split)에서의 .so 추출 경로는 arm64-v8a 네이티브 엔트리가 든
 * 실제 설치본(sourceDir/splitSourceDirs)이 필요해 JVM 단위 테스트로 못 다룬다. 여기서는
 * "추출물이 없어도 예외 없이 arg 를 반환한다"는 그레이스풀 경로까지만 확인한다.
 *
 * application=Application::class: 실제 CamCon Application 은 onCreate 에서 네이티브를 로드해
 * JVM 에서 실패하므로 순정 Application 으로 대체.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class Libgphoto2PluginInstallerTest {

    private lateinit var context: Context
    private lateinit var installer: Libgphoto2PluginInstaller

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        installer = Libgphoto2PluginInstaller(context)
        // 프로세스 공유 getDir 을 쓰므로 테스트 간 잔여 파일 격리.
        installer.iolibDir().deleteRecursively()
        installer.camlibDir().deleteRecursively()
    }

    @After
    fun tearDown() {
        installer.iolibDir().deleteRecursively()
        installer.camlibDir().deleteRecursively()
    }

    private fun writeSo(dir: File, name: String, content: String = "SO") {
        dir.mkdirs()
        File(dir, name).writeText(content)
    }

    @Test
    fun `두 디렉토리가 비어 있으면 arePluginsPresent 는 false`() {
        assertFalse(installer.arePluginsPresent())
    }

    @Test
    fun `iolib 만 so 가 있고 camlib 가 비면 false`() {
        writeSo(installer.iolibDir(), "libgphoto2_port_iolib_usb1.so")
        assertFalse(installer.arePluginsPresent())
    }

    @Test
    fun `camlib 만 so 가 있고 iolib 가 비면 false`() {
        writeSo(installer.camlibDir(), "libgphoto2_camlib_ptp2.so")
        assertFalse(installer.arePluginsPresent())
    }

    @Test
    fun `iolib camlib 둘 다 so 가 있으면 true`() {
        writeSo(installer.iolibDir(), "libgphoto2_port_iolib_usb1.so")
        writeSo(installer.camlibDir(), "libgphoto2_camlib_ptp2.so")
        assertTrue(installer.arePluginsPresent())
    }

    @Test
    fun `so 확장자가 아닌 파일만 있으면 false`() {
        writeSo(installer.iolibDir(), "readme.txt")
        writeSo(installer.camlibDir(), "notes.md")
        assertFalse(installer.arePluginsPresent())
    }

    @Test
    fun `ensurePluginDirs 는 이미 존재하면 기존 파일을 보존하고 재추출하지 않는다`() {
        val sentinel = "SENTINEL-DO-NOT-OVERWRITE"
        writeSo(installer.iolibDir(), "usb1.so", sentinel)
        writeSo(installer.camlibDir(), "ptp2.so", sentinel)

        val arg = installer.ensurePluginDirs()

        assertEquals("멱등 경로는 pluginDirArg 를 그대로 반환", installer.pluginDirArg(), arg)
        assertTrue("존재하면 여전히 present", installer.arePluginsPresent())
        assertEquals(
            "재추출·덮어쓰기 없이 원본 내용 유지",
            sentinel,
            File(installer.iolibDir(), "usb1.so").readText()
        )
        assertEquals(sentinel, File(installer.camlibDir(), "ptp2.so").readText())
    }

    @Test
    fun `pluginDirArg 는 iolib 콜론 camlib 형식이며 버전 서브디렉토리를 포함한다`() {
        val arg = installer.pluginDirArg()

        assertTrue("콜론 구분이어야 함: $arg", arg.contains(":"))
        val (iolibPart, camlibPart) = arg.split(":").let { it[0] to it[1] }
        assertEquals(installer.iolibDir().absolutePath, iolibPart)
        assertEquals(installer.camlibDir().absolutePath, camlibPart)
        assertTrue("iolib 버전 서브디렉토리 포함: $arg", arg.contains("libgphoto2_port/0.12.2"))
        assertTrue("camlib 버전 서브디렉토리 포함: $arg", arg.contains("libgphoto2/2.5.34"))
    }

    @Test
    fun `ensurePluginDirs 는 추출물이 없어도 예외 없이 pluginDirArg 를 반환한다`() {
        // 플러그인 미존재 + 테스트 APK 에 lib/arm64-v8a 엔트리 없음 → 추출 0개.
        // catch-all 로 감싸여 있으므로 throw 없이 arg 를 돌려줘야 한다.
        val arg = installer.ensurePluginDirs()
        assertEquals(installer.pluginDirArg(), arg)
    }
}
