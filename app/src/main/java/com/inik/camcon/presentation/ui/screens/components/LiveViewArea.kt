package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2

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
    // 룩은 모니터 오버레이 칩 언어와 통일 — Surface0 반투명 스크림 + 각형(Radius.sm) 헤어라인.
    // (Radius.xl 은 BottomSheet 상단 전용이라 배지에 쓰지 않는다.)
    val badgeModifier = remember {
        val shape = RoundedCornerShape(Radius.sm)
        Modifier
            .padding(Spacing.lg)
            .background(Surface0.copy(alpha = 0.72f), shape)
            .border(StrokeWidth.hairline, DividerLine, shape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    }

    Box(
        modifier = modifier
            .align(Alignment.TopStart)
            .then(badgeModifier)
    ) {
        Text(
            text = stringResource(R.string.camera_control_double_click_fullscreen),
            color = TextPrimaryV2,
            style = Caption
        )
    }
}
