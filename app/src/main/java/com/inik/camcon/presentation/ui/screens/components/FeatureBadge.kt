package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val background = Color(
        red = color.red * 0.20f + 0.08f,
        green = color.green * 0.20f + 0.08f,
        blue = color.blue * 0.20f + 0.08f,
        alpha = 0.96f
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 12.dp else 14.dp))
            .background(background)
            .border(
                1.dp,
                color.copy(alpha = 0.72f),
                RoundedCornerShape(if (compact) 12.dp else 14.dp)
            )
            .padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 4.dp else 5.dp
            )
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)) {
            Box(
                modifier = Modifier
                    .size(if (compact) 7.dp else 8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = text,
                color = Color(0xFFF4EDE4),
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
            FeatureBadge(context.getString(R.string.time_lapse), Color(0xFF0E8A68))
            FeatureBadge(context.getString(R.string.burst), Color(0xFFFF9800))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureBadge(context.getString(R.string.video_4k), Color.Red)
            FeatureBadge(context.getString(R.string.remote_control), Color.Green)
            FeatureBadge(context.getString(R.string.bracketing), Color.Cyan)
        }
    }
}
@Preview(name = "Feature Badges - Compact", showBackground = true, widthDp = 280)
@Composable
private fun FeatureBadgesCompactPreview() {
    val context = LocalContext.current
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FeatureBadge(
            text = context.getString(R.string.live_view),
            color = Color(0xFF5FA9FF),
            compact = true
        )
        FeatureBadge(
            text = context.getString(R.string.time_lapse),
            color = Color(0xFF0E8A68),
            compact = true
        )
        FeatureBadge(
            text = context.getString(R.string.burst),
            color = Color(0xFFFF9800),
            compact = true
        )
    }
}