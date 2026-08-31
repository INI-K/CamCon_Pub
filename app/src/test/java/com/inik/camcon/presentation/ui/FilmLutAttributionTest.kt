package com.inik.camcon.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 번들 필름 프리셋의 귀속 고지가 배포본에 살아 있는지 검증하는 순수 JUnit 테스트(Robolectric 불필요).
 *
 * 단위테스트 작업 디렉터리 = app 모듈이라 `src/main/assets/...` 상대 경로로 자산 원본을 직접 읽는다.
 *
 * 왜 테스트로 막는가 — LUT 는 G'MIC(CeCILL) · 원 프리셋 저작자 · Film-Luts(MIT) 세 층을 타고
 * 들어온 자산이다. MIT 는 저장소 패키징에만 걸릴 뿐 LUT 데이터의 저작권 귀속이 아니고,
 * 원 프리셋 저작자 3인의 조건은 제각각이라(Pat David = CC BY-SA 4.0, Stuart Sowerby =
 * All Rights Reserved, Juan Melara = 무표기) 어느 쪽으로도 일괄 표기할 수 없다. 다만 세 층이
 * 공통으로 기대는 최소선이 저작권 고지 보존이고, 그 고지는 각 .cube 첫 줄 주석과 MIT 전문 사본
 * 두 곳에만 남아 있다. 용량 최적화로 주석을 스트립하거나 자산을 옮기는 순간 조용히 위반이 되므로
 * 빌드 단계에서 잡는다. 고지 화면 문구는 [OpenSourceLicensesActivity] 의 filmLutLicenses 가 짝이다.
 */
class FilmLutAttributionTest {

    private val lutFiles: List<File> by lazy {
        File("src/main/assets/luts").walkTopDown().filter { it.extension == "cube" }.toList()
    }

    @Test
    fun `번들 LUT 가 존재한다(경로 이동 가드)`() {
        assertTrue("assets/luts 아래 .cube 를 찾지 못함 - 자산 경로가 바뀌었는지 확인", lutFiles.isNotEmpty())
    }

    @Test
    fun `모든 LUT 가 G'MIC 저작권 고지를 첫 줄에 보존한다`() {
        val missing = lutFiles.filterNot { file ->
            file.useLines { lines -> lines.firstOrNull()?.contains(GMIC_NOTICE) == true }
        }
        assertEquals(
            "저작권 고지가 사라진 LUT: ${missing.take(5).map { it.name }} (총 ${missing.size}개). " +
                    "첫 줄의 \"$GMIC_NOTICE\" 주석은 라이선스 고지 그 자체라 제거하면 안 된다.",
            emptyList<File>(),
            missing
        )
    }

    @Test
    fun `MIT 전문 사본이 저작권자와 함께 동봉되어 있다`() {
        val license = File("src/main/assets/licenses/MIT-Film-Luts.txt")
        assertTrue("MIT 전문 사본이 없음: ${license.path}", license.exists())

        val text = license.readText()
        assertTrue("MIT 저작권자 표기 누락", text.contains("Copyright (c) 2024 Yahia"))
        assertTrue(
            "MIT 허가 고지 누락 - 전문을 그대로 동봉해야 한다",
            text.contains("Permission is hereby granted")
        )
    }

    private companion object {
        /** 각 .cube 첫 줄에 박혀 있는 원 저작권 고지. G'MIC 이 생성 시 직접 써넣는 문자열이다. */
        const val GMIC_NOTICE = "Created by: G'MIC"
    }
}
