package com.inik.camcon

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 네이티브 Mock 카메라가 **실물 카메라 없이** 촬영 파이프라인을 구동함을 실기기/에뮬레이터에서 증명.
 *
 * `capturePhoto`(JNI, camera_capture.cpp:193)는 `MockCamera::g_enabled`이면 camera/context 핸들
 * 없이 지정 이미지를 `filesDir/MOCK_*.jpg`로 복사하고 GP_OK(0)을 반환한다.
 * arm64-v8a 기기/에뮬레이터에서만 네이티브 .so가 로드된다(순수 JVM 단위테스트 불가).
 */
@RunWith(AndroidJUnit4::class)
class MockCameraPipelineInstrumentedTest {

    private val payload = "CAMCON-MOCK-CAPTURE-PROOF-0708".toByteArray()

    @Test
    fun mockCapture_producesFile_withoutRealCamera() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("네이티브 라이브러리 로드됨", CameraNative.isLibrariesLoaded())

        val mockFilter = { f: File -> f.name.startsWith("MOCK_") }
        ctx.filesDir.listFiles(mockFilter)?.forEach { it.delete() }
        val src = File(ctx.filesDir, "mock_src.bin").apply { writeBytes(payload) }

        assertTrue("enableMockCamera(true)", CameraNative.enableMockCamera(true))
        assertTrue("isMockCameraEnabled", CameraNative.isMockCameraEnabled())
        assertTrue(
            "setMockCameraModel(Nikon Z8)",
            CameraNative.setMockCameraModel("Nikon", "Z8")
        )
        assertTrue(
            "setMockCameraImages",
            CameraNative.setMockCameraImages(arrayOf(src.absolutePath))
        )
        assertEquals("모의 이미지 1개 등록", 1, CameraNative.getMockCameraImageCount())

        // 실물 카메라 init 없이 촬영
        val rc = CameraNative.capturePhoto()
        assertEquals("capturePhoto는 GP_OK(0) 반환", 0, rc)

        val produced = ctx.filesDir.listFiles(mockFilter)?.toList().orEmpty()
        assertTrue("MOCK_*.jpg 파일이 생성됨", produced.isNotEmpty())
        assertArrayEquals(
            "생성된 파일 내용이 모의 원본과 바이트 동일",
            payload,
            produced.first().readBytes()
        )

        // 정리
        CameraNative.clearMockCameraImages()
        CameraNative.enableMockCamera(false)
        src.delete()
        produced.forEach { it.delete() }
    }
}
