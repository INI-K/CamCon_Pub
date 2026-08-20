package com.inik.camcon.data.datasource.nativesource

import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 니콘 Application Mode(0x9435 / 0xD1F0) 단일 관리 지점.
 *
 * ## 왜 토글인가
 * 니콘 공식 명세 §2.2 "PC 연결 모드에서 달라지는 점" 3번은 조건절 없이
 * *"The image playback cannot be performed ... (Except in the application mode)"* 라고 못박는다.
 * 즉 [카메라 컨트롤]로 연결하면 본체 재생(▶)·삭제가 잠기고, **유일한 해제 수단이 application mode**다.
 *
 * 그런데 §2.4 는 application mode 진입 시 `StoreRemoved` 가 발화된다고 명시하며, 실기에서도
 * 카드 저장소가 PTP 목록에서 사라져 갤러리(카드 탐색)가 죽는 것이 확인됐다
 * (Z8 2026-08-19 16:42 — GetStorageIDs 빈 목록 → 폴더 나열 -107).
 *
 * 두 기능은 동시에 성립할 수 없으므로 **필요한 쪽만 켠다**:
 *  - 카드 탐색(미리보기/갤러리 탭)에 들어갈 때 → OFF (`StoreAdded` 로 카드 복귀, §6.4.1.4)
 *  - 그 외 구간 → ON (본체 ▶ 해방)
 *
 * ## 안전
 * libgphoto2 의 `applicationmode` 위젯(`_put_Nikon_ApplicationMode2`)이 스스로 0x9435 광고 여부를
 * 검사해 미지원이면 GP_ERROR_NOT_SUPPORTED 를 반환한다 → 타 벤더·미지원 바디·미지원 프로파일에서는
 * 조용히 실패하고 아무것도 바꾸지 않는다. 1세대(Z6/Z7/Z5/Z50)는 opcode 대신 속성
 * `ApplicationMode(0xD1F0)` 이 진입 수단이라 폴백으로 시도한다.
 *
 * 실측 주의: Wi-Fi 에서 0x9435 광고는 프로파일에 따라 갈린다([사진 전송]=광고, [카메라 컨트롤]=미광고,
 * Z8 2026-08-19). 미광고 세션에서는 이 매니저의 모든 호출이 no-op 이 된다 — 그래도 무해하다.
 */
@Singleton
class NikonApplicationModeManager @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private companion object {
        const val TAG = "니콘앱모드"
        const val WIDGET_OPCODE = "applicationmode"   // 신세대: 0x9435 ChangeApplicationMode
        const val WIDGET_PROPERTY = "d1f0"            // 1세대: 0xD1F0 ApplicationMode
        const val GP_OK = 0

        /**
         * 제조사 판별용 위젯. `getCameraDeviceInfo()` 는 내부에서 `gp_camera_get_summary` 를 태워
         * Nikon PTP/IP 에서 ~6초가 걸린다(PtpipDataSource 주석). 이 위젯은 PTP DeviceInfo 캐시
         * 기반이라 즉시 반환된다 — 화면 전환 경로에서 6초를 태울 이유가 없다.
         */
        const val PROP_MANUFACTURER = "manufacturer"
    }

    private val mutex = Mutex()

    /** 마지막으로 **성공적으로 적용된** 상태. null = 미시도/미지원(세션 시작 상태). */
    private var appliedEnabled: Boolean? = null

    /** 이 세션이 니콘인가. null = 아직 판별 전. 세션 동안 불변이라 1회만 조회한다. */
    private var isNikonSession: Boolean? = null

    /**
     * **카드 탐색을 필요로 하는 화면의 수.** 앱 모드는 이 값이 0 일 때만 ON 이다.
     *
     * 왜 불리언이 아니라 카운트인가 — Compose 는 화면 전환에서 **들어오는 화면을 먼저 구성하고
     * 나가는 화면을 나중에 폐기**한다. 그래서 진입=OFF / 이탈=ON 을 그대로 적용하면 촬영↔미리보기
     * 왕복에서 항상 이탈의 ON 이 마지막에 착지해 최종 상태가 ON 이 된다(실측 2026-08-20:
     * `OFF 적용(카드 탐색)` 76ms 뒤 `ON 적용(본체 재생 해방)`, 양방향 동일).
     *
     * ON 은 카드 저장소를 PTP 목록에서 지우므로(§2.4 StoreRemoved) 그 상태의 앱 셔터는
     * 카드 부재로 판정돼 SDRAM 으로 폴백한다 — 패치 0034 로 고친 증상이 그대로 되살아난다.
     *
     * 카운트로 두면 전환 도중 값이 1→2→1 로 움직여 0 이 되지 않으므로 OFF 가 유지되고,
     * 두 화면을 모두 벗어났을 때만(1→0) ON 이 적용된다.
     */
    private var cardBrowsingHolders = 0

    /**
     * 명시적 벤더 게이트.
     *
     * libgphoto2 위젯이 스스로 0x9435 광고 여부를 검사하긴 하지만, 그건 "미지원이면 실패한다"는
     * 방어일 뿐 벤더 격리가 아니다. CamCon 규약상 벤더 고유 동작은 코드에 게이트를 둔다 —
     * 캐논/소니/후지 세션에서는 와이어를 한 번도 건드리지 않는다.
     */
    private suspend fun isNikon(): Boolean {
        isNikonSession?.let { return it }
        val manufacturer = withContext(ioDispatcher) {
            // getConfigString 은 `"성공: <값>"` / `"실패: <사유>"` 로 감싸 돌려준다(camera_config.cpp).
            runCatching { CameraNative.getConfigString(PROP_MANUFACTURER) }
                .getOrNull()
                ?.takeIf { it.startsWith("성공") }
                ?.removePrefix("성공: ")
        }

        // ⚠️ 읽기 자체가 실패했으면 **캐시하지 않는다**. "니콘 아님"과 "아직 모를 뿐"은 다르다.
        // 실패를 false 로 캐시하면 이후 세션 내내 니콘 기능이 통째로 죽는다(콜드스타트에
        // 카메라 없이 한 번 불리는 것만으로 USB 앱 모드가 영구 무력화됐다 — 2026-08-20 실측).
        if (manufacturer.isNullOrBlank()) {
            Log.d(TAG, "제조사 판별 불가 — 이번 호출만 건너뛴다(캐시하지 않음)")
            return false
        }

        val nikon = manufacturer.contains("nikon", ignoreCase = true)
        isNikonSession = nikon
        return nikon
    }

    /**
     * 네이티브 카메라 핸들이 살아 있는가.
     *
     * 화면 진입/이탈 이펙트는 연결 여부와 무관하게 발화하므로(예: 콜드스타트의 촬영 탭이
     * 기본 탭이라 카메라 없이 먼저 뜬다) 여기서 걸러내지 않으면 카메라 없는 상태로
     * 제조사 판별까지 내려가 에러 로그와 오판을 만든다.
     */
    private suspend fun isCameraReady(): Boolean = withContext(ioDispatcher) {
        runCatching { CameraNative.isCameraInitialized() }.getOrElse { false }
    }

    /**
     * 세션 경계에서 호출한다. 카메라를 새로 열면 앱 모드는 기본값(OFF)으로 돌아가므로
     * 캐시된 상태를 버려야 다음 요청이 실제로 와이어를 탄다.
     */
    suspend fun onCameraSessionStarted(): Boolean = mutex.withLock {
        appliedEnabled = null
        isNikonSession = null
        // ⚠️ cardBrowsingHolders 는 **일부러 건드리지 않는다**. 이 값은 카메라 세션이 아니라
        // 화면 점유를 센다. 재연결 시 0 으로 되돌리면 이미 떠 있는 화면의 이탈이 짝을 잃어
        // 카운트가 어긋난다.

        // 새 세션의 앱 모드는 카메라 기본값(OFF)이다. 지금 화면이 카드 탐색을 점유하고 있지
        // 않다면 곧바로 ON 을 걸어야 **연결 직후부터** 본체 재생(▶)이 눌린다. 화면 전환을
        // 한 번 해야 풀리는 것은 사용자가 겪은 그 증상이다(2026-08-20).
        applyLocked(enabled = cardBrowsingHolders == 0, reason = "새 세션 초기 상태")
    }

    /**
     * 카드 탐색(미리보기 탭) 진입 — 점유를 하나 올린다. 첫 점유일 때만 앱 모드를 끈다.
     *
     * ⚠️ **촬영(카메라 제어) 화면은 점유하지 않는다.** 그 화면에서는 본체 재생(▶)이 눌려야
     * 한다는 것이 사용자 요구이고(2026-08-20), ▶ 를 여는 유일한 수단이 앱 모드 ON 이다.
     * 앱 셔터를 UI 에서 제거했으므로 그 화면에서 OFF 를 유지할 이유(카드 라우팅)도 사라졌다.
     * 앱 셔터를 되살린다면 **촬영 동작 구간만** 점유했다 반납하도록 감싸야 한다 — 화면 단위로
     * 잡으면 다시 ▶ 가 잠긴다.
     * @return 실제로 상태를 바꿨으면 true(호출자가 목록을 다시 읽어야 함).
     */
    suspend fun enterCardBrowsing(): Boolean = mutex.withLock {
        cardBrowsingHolders++
        if (cardBrowsingHolders > 1) return false   // 이미 다른 화면이 OFF 를 잡고 있다
        applyLocked(enabled = false, reason = "카드 탐색")
    }

    /**
     * 카드 탐색 이탈 — 점유를 하나 내린다. **마지막 점유가 빠질 때만** 앱 모드를 켜
     * 본체 재생(▶)을 해방한다(§2.2 No.3).
     * @return 실제로 상태를 바꿨으면 true.
     */
    suspend fun leaveCardBrowsing(): Boolean = mutex.withLock {
        // 짝이 맞지 않는 이탈(진입 없이 이탈, 중복 이탈)은 무시한다 — 음수로 내려가면
        // 이후 진입이 0→1 전이를 못 만들어 OFF 가 영영 적용되지 않는다.
        if (cardBrowsingHolders == 0) return false
        cardBrowsingHolders--
        if (cardBrowsingHolders > 0) return false   // 아직 다른 화면이 카드 탐색 중
        applyLocked(enabled = true, reason = "본체 재생 해방")
    }

    /** 호출자가 이미 [mutex]를 잡고 있어야 한다. */
    private suspend fun applyLocked(enabled: Boolean, reason: String): Boolean {
        if (appliedEnabled == enabled) return false
        // 연결 게이트가 벤더 게이트보다 **먼저** 와야 한다 — 카메라가 없으면 제조사도 못 읽는다.
        if (!isCameraReady()) return false
        if (!isNikon()) return false

        val value = if (enabled) "1" else "0"
        val changed = withContext(ioDispatcher) {
            var ret = CameraNative.setConfigString(WIDGET_OPCODE, value)
            if (ret != GP_OK) {
                // 1세대 폴백. 여기도 실패하면 이 카메라/프로파일은 앱 모드를 제공하지 않는다.
                ret = CameraNative.setConfigString(WIDGET_PROPERTY, value)
            }
            if (ret != GP_OK) {
                Log.d(TAG, "앱 모드 $value 미지원/실패 ($reason) — 기능 영향 없음")
                return@withContext false
            }

            // 저장소 구성이 바뀌었다(StoreRemoved/StoreAdded). 네이티브의 DCIM 폴더 캐시와
            // '저장소 미노출' sticky 판정을 함께 풀어야 다음 목록 조회가 카드를 다시 본다.
            CameraNative.invalidateFileCache()
            Log.i(TAG, "앱 모드 ${if (enabled) "ON" else "OFF"} 적용 ($reason)")
            true
        }

        if (changed) appliedEnabled = enabled
        return changed
    }
}
