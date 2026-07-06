package com.inik.camcon.domain.usecase

import com.inik.camcon.domain.model.SubscriptionTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ValidateFeatureAccessUseCase] 순수 판정 매트릭스 테스트.
 *
 * 순수 함수(I/O·상태 없음)이므로 MockK/코루틴 불필요. 세 판정의 티어별 진리표를 직접 검증한다.
 */
class ValidateFeatureAccessUseCaseTest {

    private val useCase = ValidateFeatureAccessUseCase()

    // ---------- isPhotoPreviewAllowed ----------

    @Test
    fun `isPhotoPreviewAllowed - FREE·BASIC 은 false, PRO·REFERRER·ADMIN 은 true`() {
        assertFalse(useCase.isPhotoPreviewAllowed(SubscriptionTier.FREE))
        assertFalse(useCase.isPhotoPreviewAllowed(SubscriptionTier.BASIC))
        assertTrue(useCase.isPhotoPreviewAllowed(SubscriptionTier.PRO))
        assertTrue(useCase.isPhotoPreviewAllowed(SubscriptionTier.REFERRER))
        assertTrue(useCase.isPhotoPreviewAllowed(SubscriptionTier.ADMIN))
    }

    // ---------- resolveExclusiveToggle ----------

    @Test
    fun `resolveExclusiveToggle - FREE 에서 다른 쪽 ON 이면 반대편을 끄는 Swap`() {
        val decision = useCase.resolveExclusiveToggle(
            SubscriptionTier.FREE, PipelineFeature.FILM_SIMULATION, otherEnabled = true
        )
        assertEquals(ToggleDecision.Swap(PipelineFeature.COLOR_TRANSFER), decision)
    }

    @Test
    fun `resolveExclusiveToggle - FREE 에서 다른 쪽 OFF 면 Allow`() {
        val decision = useCase.resolveExclusiveToggle(
            SubscriptionTier.FREE, PipelineFeature.FILM_SIMULATION, otherEnabled = false
        )
        assertEquals(ToggleDecision.Allow, decision)
    }

    @Test
    fun `resolveExclusiveToggle - BASIC 도 다른 쪽 ON 이면 Swap`() {
        val decision = useCase.resolveExclusiveToggle(
            SubscriptionTier.BASIC, PipelineFeature.COLOR_TRANSFER, otherEnabled = true
        )
        assertEquals(ToggleDecision.Swap(PipelineFeature.FILM_SIMULATION), decision)
    }

    @Test
    fun `resolveExclusiveToggle - PRO·REFERRER·ADMIN 은 다른 쪽 ON 이어도 Allow`() {
        listOf(SubscriptionTier.PRO, SubscriptionTier.REFERRER, SubscriptionTier.ADMIN)
            .forEach { tier ->
                assertEquals(
                    ToggleDecision.Allow,
                    useCase.resolveExclusiveToggle(
                        tier, PipelineFeature.FILM_SIMULATION, otherEnabled = true
                    )
                )
            }
    }

    // ---------- resolveActivePipeline ----------

    @Test
    fun `resolveActivePipeline - FREE 둘 다 ON 이면 필름 유지·색감 OFF·정합화 필요`() {
        val active = useCase.resolveActivePipeline(
            SubscriptionTier.FREE, filmOn = true, colorOn = true
        )
        assertEquals(ActivePipeline(filmEnabled = true, colorEnabled = false, needsReconcile = true), active)
    }

    @Test
    fun `resolveActivePipeline - PRO 둘 다 ON 이면 그대로`() {
        val active = useCase.resolveActivePipeline(
            SubscriptionTier.PRO, filmOn = true, colorOn = true
        )
        assertEquals(ActivePipeline(filmEnabled = true, colorEnabled = true, needsReconcile = false), active)
    }

    @Test
    fun `resolveActivePipeline - FREE 한 쪽만 ON 이면 그대로(정합화 불필요)`() {
        val filmOnly = useCase.resolveActivePipeline(
            SubscriptionTier.FREE, filmOn = true, colorOn = false
        )
        assertEquals(ActivePipeline(filmEnabled = true, colorEnabled = false, needsReconcile = false), filmOnly)

        val colorOnly = useCase.resolveActivePipeline(
            SubscriptionTier.FREE, filmOn = false, colorOn = true
        )
        assertEquals(ActivePipeline(filmEnabled = false, colorEnabled = true, needsReconcile = false), colorOnly)
    }

    // ---------- isFullLutCatalogAllowed ----------

    @Test
    fun `isFullLutCatalogAllowed - FREE·BASIC 은 false, PRO·REFERRER·ADMIN 은 true`() {
        assertFalse(useCase.isFullLutCatalogAllowed(SubscriptionTier.FREE))
        assertFalse(useCase.isFullLutCatalogAllowed(SubscriptionTier.BASIC))
        assertTrue(useCase.isFullLutCatalogAllowed(SubscriptionTier.PRO))
        assertTrue(useCase.isFullLutCatalogAllowed(SubscriptionTier.REFERRER))
        assertTrue(useCase.isFullLutCatalogAllowed(SubscriptionTier.ADMIN))
    }

    // ---------- isFilmLutAllowed ----------

    private val freeLutId = ValidateFeatureAccessUseCase.FREE_FILM_LUT_IDS.first()
    private val paidLutId = "luts/print/kodak_2393_cuspclip.cube" // 무료셋에 없는 임의 카탈로그 id

    @Test
    fun `isFilmLutAllowed - 빈 id 는 모든 티어에서 true(선택 없음)`() {
        SubscriptionTier.values().forEach { tier ->
            assertTrue(useCase.isFilmLutAllowed(tier, ""))
        }
    }

    @Test
    fun `isFilmLutAllowed - FREE·BASIC 은 무료 id 만 true, 유료 id 는 false`() {
        listOf(SubscriptionTier.FREE, SubscriptionTier.BASIC).forEach { tier ->
            assertTrue("무료 id 는 $tier 에서 허용", useCase.isFilmLutAllowed(tier, freeLutId))
            assertFalse("유료 id 는 $tier 에서 차단", useCase.isFilmLutAllowed(tier, paidLutId))
        }
    }

    @Test
    fun `isFilmLutAllowed - PRO·REFERRER·ADMIN 은 임의 id 도 true`() {
        listOf(SubscriptionTier.PRO, SubscriptionTier.REFERRER, SubscriptionTier.ADMIN).forEach { tier ->
            assertTrue(useCase.isFilmLutAllowed(tier, freeLutId))
            assertTrue(useCase.isFilmLutAllowed(tier, paidLutId))
        }
    }

    // ---------- resolveEffectiveLutId ----------

    @Test
    fun `resolveEffectiveLutId - FREE 에서 잠긴 id 는 빈 문자열로 마스킹`() {
        assertEquals("", useCase.resolveEffectiveLutId(SubscriptionTier.FREE, paidLutId))
    }

    @Test
    fun `resolveEffectiveLutId - FREE 에서 무료 id 는 그대로 통과`() {
        assertEquals(freeLutId, useCase.resolveEffectiveLutId(SubscriptionTier.FREE, freeLutId))
    }

    @Test
    fun `resolveEffectiveLutId - PRO 는 유료 id 도 그대로 통과`() {
        assertEquals(paidLutId, useCase.resolveEffectiveLutId(SubscriptionTier.PRO, paidLutId))
    }

    @Test
    fun `resolveEffectiveLutId - 빈 id 는 그대로 빈 문자열`() {
        assertEquals("", useCase.resolveEffectiveLutId(SubscriptionTier.FREE, ""))
    }
}
