package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.TextPrimary

/**
 * 사진 뷰어의 현재 페이지 표시 (예: "3 / 15")
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
                Background.copy(alpha = 0.6f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${currentIndex + 1} / $totalCount",
            color = TextPrimary,
            fontSize = 14.sp
        )
    }
}
