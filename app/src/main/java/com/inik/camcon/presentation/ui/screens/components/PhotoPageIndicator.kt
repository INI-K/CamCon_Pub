package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2

/**
 * 사진 뷰어의 현재 페이지 표시 (예: "3 / 15")
 *
 * 자릿수가 바뀌어도 배지 폭이 흔들리지 않도록 [MonoNumeric](tnum) 슬롯을 쓴다.
 *
 * @param currentIndex 현재 페이지 인덱스 (0-based)
 * @param totalCount 총 사진 개수
 * @param modifier 모디파이어
 */
@Composable
fun PhotoPageIndicator(
    currentIndex: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Surface0.copy(alpha = 0.6f),
                RoundedCornerShape(Radius.sm)
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${currentIndex + 1} / $totalCount",
            color = TextPrimaryV2,
            style = MonoNumeric
        )
    }
}
