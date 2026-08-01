package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.HeadingS
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.Micro
import com.inik.camcon.presentation.theme.MonoReadout
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.domain.model.CameraPhoto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 사진 뷰어 상단 컨트롤 버튼들
 */
@Composable
fun PhotoViewerTopControls(
    onShowDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Row(
        horizontalArrangement = Arrangement.End
    ) {
        IconButton(onClick = onShowDetails) {
            Icon(
                Icons.Default.Info,
                contentDescription = context.getString(R.string.photo_details),
                tint = TextPrimaryV2,
                modifier = Modifier.size(IconSize.lg)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Default.Close,
                contentDescription = context.getString(R.string.close),
                tint = TextPrimaryV2,
                modifier = Modifier.size(IconSize.lg)
            )
        }
    }
}

/**
 * 사진 상세 정보를 보여주는 다이얼로그
 */
@Composable
fun PhotoDetailsDialog(
    photo: CameraPhoto,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current

    AppDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(IconSize.md)
            )
        },
        title = {
            Text(text = context.getString(R.string.photo_details))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 제목/본문은 AppDialog 슬롯으로 이미 갈려 있고 선 양쪽 surface tier 가 같다.
                // 헤어라인을 빼고 간격으로만 분리한다.
                Spacer(modifier = Modifier.height(Spacing.md))

                InfoRow(
                    label = context.getString(R.string.file_name),
                    value = photo.name,
                    icon = Icons.Default.PhotoLibrary
                )

                InfoRow(
                    label = context.getString(R.string.file_size),
                    value = formatFileSize(photo.size),
                    icon = null,
                    isNumeric = true
                )

                if (photo.width > 0 && photo.height > 0) {
                    InfoRow(
                        label = context.getString(R.string.resolution),
                        value = "${photo.width} × ${photo.height}",
                        icon = null,
                        isNumeric = true
                    )
                }

                InfoRow(
                    label = context.getString(R.string.capture_date),
                    value = formatDate(photo.date, context),
                    icon = Icons.Default.DateRange
                )

                InfoRow(
                    label = context.getString(R.string.file_path),
                    value = photo.path,
                    icon = null
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                text = context.getString(R.string.download_photo),
                onClick = onDownload,
                leadingIcon = Icons.Default.Share,
                modifier = Modifier.fillMaxWidth()
            )
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    )
}

/**
 * 정보 행 컴포넌트
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    isNumeric: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(IconSize.sm)
                    .padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
        } else {
            Spacer(modifier = Modifier.width(IconSize.lg))
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 라벨은 계측기 라벨(11sp)로 내리고 값은 크기·무게·패밀리로 띄운다.
            // 자릿수가 변하는 값(용량·해상도)만 모노(tnum)라 자리가 흔들리지 않는다.
            Text(
                text = label,
                style = Micro,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = if (isNumeric) MonoReadout else HeadingS,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * 파일 크기를 사람이 읽기 쉬운 형태로 포맷
 */
private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * 날짜를 사람이 읽기 쉬운 형태로 포맷
 */
private fun formatDate(timestamp: Long, context: android.content.Context): String {
    val pattern = context.getString(R.string.date_format_pattern)
    val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

/**
 * Photo Viewer Top Controls 프리뷰
 */
@Preview(name = "Photo Viewer Top Controls", showBackground = false)
@Composable
private fun PhotoViewerTopControlsPreview() {
    CamConTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface0.copy(alpha = 0.5f))
                .padding(Spacing.base)
        ) {
            PhotoViewerTopControls(
                onShowDetails = {},
                onDismiss = {}
            )
        }
    }
}

/**
 * Photo Details Dialog 프리뷰
 */
@Preview(name = "Photo Details Dialog", showBackground = true)
@Composable
private fun PhotoDetailsDialogPreview() {
    CamConTheme {
        PhotoDetailsDialog(
            // 실촬영값. 딱 떨어지는 더미(IMG_0001 / 4032×3024)로는 긴 경로 줄바꿈도,
            // 자릿수가 큰 용량/해상도의 정렬도 프리뷰에서 드러나지 않는다.
            // date 는 고정값 — currentTimeMillis 는 프리뷰 스냅샷을 흔든다.
            photo = CameraPhoto(
                name = "DSC_4417.JPG",
                path = "/storage/emulated/0/DCIM/CamCon/2026-07-28/DSC_4417.JPG",
                size = 8_734_112,
                date = 1_753_680_237_000,
                width = 6048,
                height = 4024
            ),
            onDismiss = {},
            onDownload = {}
        )
    }
}