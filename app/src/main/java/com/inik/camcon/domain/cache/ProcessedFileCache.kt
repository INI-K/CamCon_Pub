package com.inik.camcon.domain.cache

/**
 * 이미 처리된 파일 키를 기억하는 dedup 캐시.
 *
 * LRU 1000 + TTL 24h.
 *
 * **호출자 책임**: 본 인터페이스는 키 보관·만료만 책임진다. 키의 의미(예: 다운로드한 파일의 식별자)는
 * 호출자가 정의한다. 키 정규화·중복 정의 회피도 호출자 책임.
 *
 * **dedup 전용**: 캐시 미스가 "처음 본 키" 외에 다른 도메인 결정(다운로드 실패, 권한 차단 등)을
 * 의미하지 않는다. 본 캐시는 "이 키를 이미 처리했는가?" 한 가지 질문에만 답한다.
 *
 * **atomic per call**: 단일 메서드 호출은 원자적이지만 여러 메서드 조합(예: contains 후 add)은
 * race window 를 만든다. 호출자가 한 번에 결정을 내려야 한다면 [add] 의 반환값을 활용한다.
 */
interface ProcessedFileCache {

    /**
     * key 를 처리 완료로 마킹.
     *
     * 반환값 분기:
     * - **신규 키**: 보관하고 `true` 반환.
     * - **보관 중 + 미만료**: access-order recency 만 갱신하고 `false` 반환.
     * - **보관 중 + 만료**: sweep 누락 케이스 보호. 만료 키를 새 키처럼 다시 등록하고 `true` 반환.
     *
     * 호출자는 `true` 일 때만 후속 처리(다운로드 진행 등)를 수행한다.
     */
    fun add(key: String): Boolean

    /**
     * key 가 보관 중이며 미만료인지 확인. 호출 시 만료된 키는 즉시 제거하며 `false` 반환.
     */
    fun contains(key: String): Boolean

    /**
     * 현재 보관 중인 키 개수.
     *
     * **약한 보장**: 본 카운트는 만료 키를 포함할 수 있다. 정확한 유효 카운트는 [sweepExpired]
     * 직후에만 보장된다. dedup 동작 정확성에는 영향 없음 (만료 키는 [contains] / [add] 시점에
     * 자동 정리됨).
     */
    fun size(): Int

    /**
     * 만료된 키를 모두 제거. CacheModule 의 1h 주기 sweep 코루틴이 호출.
     *
     * 호출자가 직접 호출할 필요는 없으나 테스트 / 디버깅 용도로 공개.
     */
    fun sweepExpired()

    /** 모든 키 제거. */
    fun clear()

    /**
     * key 를 보관에서 제거. 후속 처리(다운로드/저장)가 실패해 동일 키의 재수신·재시도를 허용해야 할 때 호출.
     * @return 보관 중이던 키를 제거했으면 true.
     */
    fun remove(key: String): Boolean
}
