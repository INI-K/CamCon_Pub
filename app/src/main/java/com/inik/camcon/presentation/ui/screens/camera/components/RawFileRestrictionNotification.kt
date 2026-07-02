package com.inik.camcon.presentation.ui.screens.camera.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.viewmodel.RawFileRestriction
import kotlinx.coroutines.delay

@Composable
fun RawFileRestrictionNotification(
    restriction: RawFileRestriction,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showUpgradeAction: Boolean = false,
    onUpgradeClick: () -> Unit = {}
) {
    // 업그레이드 액션이 없을 때만 5초 후 자동 소멸. 액션 버튼이 있으면 사용자가 닫을 때까지 유지.
    if (!showUpgradeAction) {
        LaunchedEffect(restriction.timestamp) {
            delay(5000L)
            onDismiss()
        }
    }

    // 화면 상단에 표시
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { -80 }
        ) + fadeIn(animationSpec = tween(260)),
        exit = slideOutVertically(
            targetOffsetY = { -80 }
        ) + fadeOut(animationSpec = tween(260)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp, start = Spacing.lg, end = Spacing.lg)
        ) {
            SurfaceV2(
                tier = 2,
                border = true,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier
                        .padding(Spacing.base)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = stringResource(R.string.cd_raw_notification),
                            tint = ErrorV2,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Text(
                            text = stringResource(R.string.camera_control_raw_file_restriction),
                            color = TextPrimaryV2,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = TextSecondaryV2,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    Text(
                        text = restriction.fileName,
                        color = TextPrimaryV2,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = restriction.message,
                        color = TextSecondaryV2,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (showUpgradeAction) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            PrimaryButton(
                                text = stringResource(R.string.diag_raw_upgrade),
                                onClick = onUpgradeClick
                            )
                        }
                    }
                }
            }
        }
    }
}
