package com.inik.camcon.presentation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.inik.camcon.R
import com.inik.camcon.presentation.ui.components.v2.EmptyState

/**
 * 미리보기 탭 잠금 화면.
 *
 * PRO 미만 티어에서 미리보기 탭 진입 시 표시한다. PhotoPreviewViewModel 을 생성하지 않으므로
 * (PhotoPreviewScreen 의 hiltViewModel() 이 유일한 생성처) 연결 옵저버의 자동 사진 로딩이
 * 원천 차단된다. 다크 테마·v2 톤은 [EmptyState] 가 보장한다.
 */
@Composable
fun PhotoPreviewLockedScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Outlined.Lock,
            title = stringResource(R.string.photo_preview_locked_title),
            description = stringResource(R.string.photo_preview_locked_body),
            // TODO(billing): 업그레이드 CTA 부착 지점 — 추후 지원.
            action = null
        )
    }
}
