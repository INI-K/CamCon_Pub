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
import com.inik.camcon.presentation.theme.DarkBackgroundGradientEnd
import com.inik.camcon.presentation.theme.DarkBackgroundGradientMid
import com.inik.camcon.presentation.theme.DarkBackgroundGradientStart
import com.inik.camcon.presentation.theme.DarkBodyText
import com.inik.camcon.presentation.theme.DarkCardBackground
import com.inik.camcon.presentation.theme.DarkCardBorder
import com.inik.camcon.presentation.theme.DarkFilterChipLockedText
import com.inik.camcon.presentation.theme.DarkFilterChipSelectedBackground
import com.inik.camcon.presentation.theme.DarkFilterChipSelectedBorder
import com.inik.camcon.presentation.theme.DarkFilterChipUnselectedBackground
import com.inik.camcon.presentation.theme.DarkFilterChipUnselectedBorder
import com.inik.camcon.presentation.theme.DarkIconButtonBackground
import com.inik.camcon.presentation.theme.DarkIconButtonTint
import com.inik.camcon.presentation.theme.DarkStatusBadgeText
import com.inik.camcon.presentation.theme.DarkTabRowBackground
import com.inik.camcon.presentation.theme.DarkTabRowBorder
import com.inik.camcon.presentation.theme.DarkTabSelectedBackground
import com.inik.camcon.presentation.theme.DarkTabSelectedText
import com.inik.camcon.presentation.theme.DarkTabUnselectedText
import com.inik.camcon.presentation.theme.DarkTitleText
import com.inik.camcon.presentation.theme.DarkTopBarGradientEnd
import com.inik.camcon.presentation.theme.DarkTopBarGradientStart

private val DarkCardBorderStroke = BorderStroke(1.dp, DarkCardBorder)

fun Modifier.darkScreenBackground(): Modifier =
    background(
        Brush.verticalGradient(
            colors = listOf(
                DarkBackgroundGradientStart,
                DarkBackgroundGradientMid,
                DarkBackgroundGradientEnd
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
        elevation = 4.dp,
        backgroundColor = DarkCardBackground,
        border = DarkCardBorderStroke,
        shape = RoundedCornerShape(18.dp)
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
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkTopBarGradientStart,
                        DarkTopBarGradientEnd
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkIconButtonBackground)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = DarkIconButtonTint
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        color = DarkTitleText,
                        style = MaterialTheme.typography.h6
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = DarkBodyText,
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
            color = DarkTitleText,
            style = MaterialTheme.typography.subtitle1
        )
        Text(
            text = subtitle,
            color = DarkBodyText,
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
        color = DarkStatusBadgeText,
        fontSize = if (compact) 9.sp else 10.sp,
        modifier = Modifier
            .background(background.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
            .border(1.dp, border.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
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
            .clip(RoundedCornerShape(14.dp))
            .background(DarkTabRowBackground)
            .border(1.dp, DarkTabRowBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = selectedIndex == index
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onTabSelected(index) },
                color = if (selected) DarkTabSelectedBackground else Color.Transparent
            ) {
                Text(
                    text = title,
                    color = if (selected) DarkTabSelectedText else DarkTabUnselectedText,
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
    val background = if (isSelected) DarkFilterChipSelectedBackground else DarkFilterChipUnselectedBackground
    val border = if (isSelected) DarkFilterChipSelectedBorder else DarkFilterChipUnselectedBorder
    val content = when {
        isLocked -> DarkFilterChipLockedText
        isSelected -> DarkTabSelectedText
        else -> DarkTabUnselectedText
    }
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !isLocked, onClick = onClick)
            .border(1.dp, border, RoundedCornerShape(10.dp)),
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
