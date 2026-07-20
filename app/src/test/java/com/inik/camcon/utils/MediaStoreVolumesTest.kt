package com.inik.camcon.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MediaStoreVolumes.pickPreferredVolume] 순수 로직 락인.
 *
 * 이 함수는 제거식 SD카드가 마운트돼 있으면 그중 사전순 첫 번째를, 없으면 primary("external_primary")로
 * 폴백하는 볼륨 선택 규칙이다. `MediaStore.VOLUME_EXTERNAL_PRIMARY`와 동일한 리터럴을 리터럴로 비교하므로
 * android.* 의존이 전혀 없어 JVM 단위 테스트로 충분하다(Robolectric 불필요).
 *
 * [MediaStoreVolumes.preferredImagesUri]는 `MediaStore` 네이티브 의존이라 여기서 검증하지 않는다.
 *
 * 결정성 요구: 같은(순서만 다른) 마운트 집합이면 언제나 같은 볼륨을 골라야 한다 →
 * `minOrNull`(사전순)로 tie-break.
 */
class MediaStoreVolumesTest {

    // MediaStore.VOLUME_EXTERNAL_PRIMARY 와 동일한 값(구현이 리터럴로 비교하므로 테스트도 리터럴로 락인).
    private val primary = "external_primary"

    @Test
    fun `빈 컬렉션이면 primary 로 폴백`() {
        assertEquals(primary, MediaStoreVolumes.pickPreferredVolume(emptyList()))
    }

    @Test
    fun `primary 만 있으면 primary 반환`() {
        assertEquals(primary, MediaStoreVolumes.pickPreferredVolume(listOf(primary)))
    }

    @Test
    fun `primary 와 SD 1개면 SD 볼륨 선택`() {
        assertEquals(
            "1234-5678",
            MediaStoreVolumes.pickPreferredVolume(listOf(primary, "1234-5678"))
        )
    }

    @Test
    fun `primary 와 SD 2개 이상이면 사전순 첫 번째(결정성)`() {
        // 사전순: "1111-2222" < "9999-0000"
        assertEquals(
            "1111-2222",
            MediaStoreVolumes.pickPreferredVolume(listOf(primary, "9999-0000", "1111-2222"))
        )
    }

    @Test
    fun `primary 미포함 비-primary 만 있으면 그중 사전순 첫 번째`() {
        assertEquals(
            "aaaa-0001",
            MediaStoreVolumes.pickPreferredVolume(listOf("cccc-0003", "aaaa-0001", "bbbb-0002"))
        )
    }

    @Test
    fun `비-primary 단일이면 그 볼륨 반환`() {
        assertEquals(
            "abcd-ef01",
            MediaStoreVolumes.pickPreferredVolume(listOf("abcd-ef01"))
        )
    }

    @Test
    fun `중복과 뒤섞인 순서에도 결정적 결과`() {
        val shuffled = listOf("9999-0000", "1111-2222", primary, "1111-2222", "9999-0000", primary)
        val sorted = listOf(primary, "1111-2222", "9999-0000")
        val result = MediaStoreVolumes.pickPreferredVolume(shuffled)
        assertEquals("1111-2222", result)
        // 같은 집합(순서만 다름)이면 동일 결과여야 결정적이다.
        assertEquals(result, MediaStoreVolumes.pickPreferredVolume(sorted))
        // 반복 호출도 안정적.
        assertEquals(result, MediaStoreVolumes.pickPreferredVolume(shuffled))
    }
}
