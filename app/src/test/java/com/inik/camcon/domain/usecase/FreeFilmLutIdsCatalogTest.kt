package com.inik.camcon.domain.usecase

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS] 각 항목이 `assets/film_luts.json` 카탈로그에
 * 실존하는지 검증하는 순수 JUnit 테스트(Robolectric 불필요).
 *
 * 단위테스트 작업 디렉터리 = app 모듈이라 `src/main/assets/...` 상대 경로로 카탈로그 원본을 직접 파싱한다.
 * org.json 은 [returnDefaultValues]=true 로 stub 을 반환하므로 실제 JVM 파서(Gson)를 쓴다.
 * 개발자가 무료셋 상수 한 줄을 교체하다 오타를 내거나 카탈로그가 개편되면 이 테스트가 빌드 단계에서 잡는다.
 */
class FreeFilmLutIdsCatalogTest {

    private val catalogLutFiles: Set<String> by lazy {
        val text = File("src/main/assets/film_luts.json").readText()
        val arr = JsonParser.parseString(text).asJsonObject.getAsJsonArray("filmLUTs")
        arr.map { it.asJsonObject.get("lut_file").asString }.toSet()
    }

    @Test
    fun `무료 필름 LUT id 5종이 모두 카탈로그에 실존`() {
        assertEquals(5, ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS.size)
        ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS.forEach { id ->
            assertTrue("무료 LUT id 가 카탈로그에 없음: $id", id in catalogLutFiles)
        }
    }

    @Test
    fun `카탈로그가 비어있지 않음(파싱 정상 동작 가드)`() {
        assertTrue(catalogLutFiles.size > ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS.size)
    }
}
