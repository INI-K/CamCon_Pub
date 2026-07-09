package com.inik.camcon.presentation.ui

import android.Manifest
import android.os.Build

/**
 * 앱 실행 시 재요청할 런타임 권한 계산 로직.
 *
 * Android Context/Activity 에 의존하지 않는 순수 함수만 모아 Robolectric 없이 단위 테스트가
 * 가능하도록 한다. (실제 권한 판정·요청 launch·설정 이동은 [MainActivity] 가 Context 로 수행)
 */
object AppPermissions {

    /**
     * SDK 레벨에 따라 앱이 사용하는 저장소/미디어/알림 권한 중 아직 미허용인 것만 반환한다.
     * 매 콜드 스타트마다 이 목록으로 시스템 권한 다이얼로그를 재요청한다.
     *
     * @param sdkInt 실행 기기의 [Build.VERSION.SDK_INT]
     * @param isGranted 해당 권한이 이미 승인됐는지 판정 (테스트에서 주입)
     */
    fun storagePermissionsToRequest(
        sdkInt: Int,
        isGranted: (String) -> Boolean
    ): List<String> {
        // 저장소/미디어 권한은 스코프드 스토리지(MediaStore)로만 처리하므로 요청하지 않는다.
        // API33+ 에서 알림 권한만 요청한다.
        val used = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
        return used.filterNot(isGranted)
    }

    /**
     * 권한 요청 결과 중 '영구 거부'(안드로이드 2회 거부 캡 또는 '다시 묻지 않음')된 권한만 반환한다.
     * 이 권한들은 다시 요청해도 시스템 다이얼로그가 뜨지 않으므로 앱이 설정 이동을 안내해야 한다.
     *
     * @param results 요청 결과 (권한 → 승인 여부)
     * @param isPermanentlyDenied 해당 권한이 영구 거부 상태인지 판정
     *        (Activity 에서 `{ !shouldShowRequestPermissionRationale(it) }` 로 주입)
     */
    fun permanentlyDeniedPermissions(
        results: Map<String, Boolean>,
        isPermanentlyDenied: (String) -> Boolean
    ): List<String> {
        return results
            .filter { (permission, granted) -> !granted && isPermanentlyDenied(permission) }
            .keys
            .toList()
    }
}
