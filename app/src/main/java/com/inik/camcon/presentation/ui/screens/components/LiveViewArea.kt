package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.SurfaceElevated
import com.inik.camcon.presentation.theme.TextPrimary

/**
 * 전체화면 진입 힌트 배지
 *
 * 책임:
 * - "더블클릭으로 전체화면" 힌트 텍스트 표시
 * - 우상단 고정 위치 (TopStart + padding)
 * - 반투명 배경
 *
 * BoxScope 확장 함수로서 Box 내부에서만 호출 가능.
 * Compose 성능 최적화: Modifier chain을 remember()로 메모이제이션하여
 * 부모의 상태 변경이 이 컴포넌트의 Recomposition을 유발하지 않음 (B1 최적화).
 *
 * 사용 예:
 * ```kotlin
 * Box(...) {
 *     // 다른 컨텐츠
 *     if (canEnterFullscreen) {
 *         FullscreenHintBadge()
 *     }
 * }
 * ```
 */
@Composable
fun BoxScope.FullscreenHintBadge(modifier: Modifier = Modifier) {
    // B1 최적화: Modifier chain memoize
    // 배경색과 패딩이 부모의 상태 변경에 영향을 받지 않도록 고정
    val badgeModifier = remember {
        Modifier
            .padding(20.dp)
            .background(
                SurfaceElevated.copy(alpha = 0.8f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    }

    Box(
        modifier = modifier
            .align(Alignment.TopStart)
            .then(badgeModifier)
    ) {
        Text(
            text = stringResource(R.string.camera_control_double_click_fullscreen),
            color = TextPrimary,
            fontSize = 12.sp
        )
    }
}
