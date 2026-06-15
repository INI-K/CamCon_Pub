package com.inik.camcon.presentation.ui.screens.components
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.AccentMuted
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.Elevation
import com.inik.camcon.presentation.theme.OnAccent
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextDisabled
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2

private val DarkCardBorderStroke = BorderStroke(1.dp, DividerLine)

fun Modifier.darkScreenBackground(): Modifier =
    background(
        Brush.verticalGradient(
            colors = listOf(
                Surface0,
                Surface1,
                Surface0
            )
        )
    )

@Composable
fun DarkScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .darkScreenBackground(),
        content = content
    )
}

@Composable
fun DarkInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = Elevation.low,
        backgroundColor = Surface2,
        border = DarkCardBorderStroke,
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun DarkTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Surface1,
                        Surface0
                    )
                )
            )
            .border(1.dp, DividerLine, RoundedCornerShape(Radius.md))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(Surface3)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = TextPrimaryV2
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        color = Accent,
                        style = MaterialTheme.typography.h6
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = TextSecondaryV2,
                            style = MaterialTheme.typography.caption
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions
            )
        }
    }
}

@Composable
fun DarkSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = Accent,
            style = MaterialTheme.typography.subtitle1
        )
        Text(
            text = subtitle,
            color = TextSecondaryV2,
            style = MaterialTheme.typography.caption
        )
    }
}

@Composable
fun DarkStatusBadge(
    text: String,
    background: Color,
    border: Color,
    compact: Boolean = false
) {
    Text(
        text = text,
        color = TextPrimaryV2,
        fontSize = if (compact) 9.sp else 10.sp,
        modifier = Modifier
            .background(background.copy(alpha = 0.92f), RoundedCornerShape(Radius.sm))
            .border(1.dp, border.copy(alpha = 0.95f), RoundedCornerShape(Radius.sm))
            .padding(
                horizontal = if (compact) 7.dp else 8.dp,
                vertical = if (compact) 3.dp else 4.dp
            )
    )
}

@Composable
fun DarkTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Surface1)
            .border(1.dp, DividerLine, RoundedCornerShape(Radius.md))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = selectedIndex == index
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onTabSelected(index) },
                color = if (selected) Accent else Color.Transparent
            ) {
                Text(
                    text = title,
                    color = if (selected) OnAccent else TextSecondaryV2,
                    style = MaterialTheme.typography.body2,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DarkFilterChip(
    text: String,
    isSelected: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    val background = if (isSelected) AccentMuted else Surface2
    val border = if (isSelected) Accent else DividerLine
    val content = when {
        isLocked -> TextDisabled
        isSelected -> OnAccent
        else -> TextSecondaryV2
    }
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(enabled = !isLocked, onClick = onClick)
            .border(1.dp, border, RoundedCornerShape(Radius.sm)),
        color = background
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.button,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
