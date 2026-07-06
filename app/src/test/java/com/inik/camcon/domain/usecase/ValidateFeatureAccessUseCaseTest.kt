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
}
