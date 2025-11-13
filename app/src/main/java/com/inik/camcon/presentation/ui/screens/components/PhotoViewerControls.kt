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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inik.camcon.R
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
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Default.Close,
                contentDescription = context.getString(R.string.close),
                tint = Color.White,
                modifier = Modifier.size(28.dp)
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.photo_details),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                InfoRow(
                    label = context.getString(R.string.file_name),
                    value = photo.name,
                    icon = Icons.Default.PhotoLibrary
                )

                InfoRow(
                    label = context.getString(R.string.file_size),
                    value = formatFileSize(photo.size),
                    icon = null
                )

                if (photo.width > 0 && photo.height > 0) {
                    InfoRow(
                        label = context.getString(R.string.resolution),
                        value = "${photo.width} × ${photo.height}",
                        icon = null
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

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.download_photo),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * 정보 행 컴포넌트
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
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
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp)
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
    MaterialTheme {
        PhotoDetailsDialog(
            photo = CameraPhoto(
                name = "IMG_0001.JPG",
                path = "/sdcard/DCIM/Camera/IMG_0001.JPG",
                size = 2048576,
                date = System.currentTimeMillis() / 1000,
                width = 4032,
                height = 3024
            ),
            onDismiss = {},
            onDownload = {}
        )
    }
}