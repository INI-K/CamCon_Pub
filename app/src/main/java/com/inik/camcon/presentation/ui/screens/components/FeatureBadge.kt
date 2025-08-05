package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.R

/**
 * 카메라 기능을 표시하는 배지 컴포넌트
 */
@Composable
fun FeatureBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(name = "Feature Badges", showBackground = true)
@Composable
private fun FeatureBadgesPreview() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureBadge(context.getString(R.string.live_view), Color.Blue)
            FeatureBadge(context.getString(R.string.time_lapse), Color(0xFF9C27B0))
            FeatureBadge(context.getString(R.string.burst), Color(0xFFFF9800))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureBadge(context.getString(R.string.video_4k), Color.Red)
            FeatureBadge(context.getString(R.string.remote_control), Color.Green)
            FeatureBadge(context.getString(R.string.bracketing), Color.Cyan)
        }
    }
}