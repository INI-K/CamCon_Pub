package com.inik.camcon.presentation.ui.screens

import android.graphics.ColorSpace
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Snackbar
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.components.FullScreenPhotoViewer
import com.inik.camcon.presentation.viewmodel.ServerPhotosViewModel
import com.inik.camcon.data.datasource.local.ThemeMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyPhotosScreen(
    viewModel: ServerPhotosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedPhoto by remember { mutableStateOf<CapturedPhoto?>(null) }

    // 화면에 진입할 때마다 새로고침 - 탭 전환 시 확실히 실행됨
    DisposableEffect(Unit) {
        Log.d("MyPhotosScreen", "화면 진입 - 사진 목록 새로고침 실행")
        viewModel.refreshPhotos()
        onDispose {
            Log.d("MyPhotosScreen", "화면 종료")
        }
    }

    // 권한 요청 런처
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // 권한 승인됨, 대기 중인 삭제 작업 재시도
            viewModel.retryPendingDelete()
        } else {
            // 권한 거부됨
            viewModel.clearPendingDeleteRequest()
        }
    }

    // 권한 요청이 필요한 경우 처리
    uiState.pendingDeleteRequest?.let { recoverableSecurityException ->
        androidx.compose.runtime.LaunchedEffect(recoverableSecurityException) {
            try {
                val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                val request =
                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                deletePermissionLauncher.launch(request)
            } catch (e: Exception) {
                Log.e("MyPhotosScreen", "권한 요청 실패", e)
                viewModel.clearPendingDeleteRequest()
            }
        }
    }

    // 멀티 선택 모드에서 뒤로가기 처리
    BackHandler(enabled = uiState.isMultiSelectMode) {
        viewModel.exitMultiSelectMode()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 상단 헤더 - 멀티 선택 모드에 따라 다르게 표시
        if (uiState.isMultiSelectMode) {
            MyPhotosMultiSelectActionBar(
                selectedCount = uiState.selectedPhotos.size,
                onSelectAll = { viewModel.selectAllPhotos() },
                onDeselectAll = { viewModel.deselectAllPhotos() },
                onDelete = { viewModel.deleteSelectedPhotos() },
                onCancel = { viewModel.exitMultiSelectMode() }
            )
        } else {
            ModernMyPhotosHeader(
                photoCount = uiState.photos.size,
                onRefresh = { viewModel.refreshPhotos() }
            )
        }

        when {
            uiState.isLoading -> {
                LoadingIndicator()
            }

            uiState.photos.isEmpty() -> {
                EmptyMyPhotosState()
            }

            else -> {
                FluidPhotoGrid(
                    photos = uiState.photos, // ViewModel에서 이미 최신순으로 정렬됨
                    onPhotoClick = { photo -> selectedPhoto = photo },
                    onDeleteClick = { photo -> viewModel.deletePhoto(photo.id) },
                    isMultiSelectMode = uiState.isMultiSelectMode,
                    selectedPhotos = uiState.selectedPhotos,
                    onPhotoLongClick = { photo -> viewModel.startMultiSelectMode(photo.id) },
                    onToggleSelection = { photo -> viewModel.togglePhotoSelection(photo.id) }
                )
            }
        }
    }

    // 전체화면 사진 뷰어
    selectedPhoto?.let { photo ->
        val currentIndex = uiState.photos.indexOfFirst { it.id == photo.id }
        val cameraPhotos = uiState.photos.map { capturedPhoto ->
            CameraPhoto(
                path = capturedPhoto.filePath,
                name = File(capturedPhoto.filePath).name,
                date = capturedPhoto.captureTime,
                size = capturedPhoto.size
            )
        }

        if (currentIndex >= 0 && cameraPhotos.isNotEmpty()) {
            val currentCameraPhoto = cameraPhotos[currentIndex]

            // 파일 존재 여부 로그
            val file = File(currentCameraPhoto.path)
            Log.d("MyPhotosScreen", "선택된 사진: ${currentCameraPhoto.name}")
            Log.d("MyPhotosScreen", "파일 경로: ${currentCameraPhoto.path}")
            Log.d("MyPhotosScreen", "파일 존재: ${file.exists()}")
            Log.d("MyPhotosScreen", "파일 크기: ${file.length()} bytes")

            FullScreenPhotoViewer(
                photo = currentCameraPhoto,
                onDismiss = { selectedPhoto = null },
                onPhotoChanged = { newPhoto ->
                    // 변경된 사진에 해당하는 CapturedPhoto 찾기
                    val newCapturedPhoto = uiState.photos.find { it.filePath == newPhoto.path }
                    selectedPhoto = newCapturedPhoto
                },
                thumbnailData = null,
                fullImageData = ByteArray(0), // 빈 배열로 로컬 파일임을 표시
                onDownload = { /* 이미 로컬 파일이므로 무시 */ },
                hideDownloadButton = true,
                localPhotos = cameraPhotos
            )
        }
    }

    // 에러 표시
    uiState.error?.let { error ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                backgroundColor = MaterialTheme.colors.error,
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(
                            text = "확인",
                            color = MaterialTheme.colors.onError
                        )
                    }
                }
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colors.onError
                )
            }
        }
    }
}

@Composable
private fun ModernMyPhotosHeader(
    photoCount: Int,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "내 사진",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold
            )
            if (photoCount > 0) {
                Text(
                    text = "${photoCount}장의 사진",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "새로고침",
                tint = MaterialTheme.colors.onSurface
            )
        }
    }
}

@Composable
private fun FluidPhotoGrid(
    photos: List<CapturedPhoto>,
    onPhotoClick: (CapturedPhoto) -> Unit,
    onDeleteClick: (CapturedPhoto) -> Unit,
    isMultiSelectMode: Boolean = false,
    selectedPhotos: Set<String> = emptySet(),
    onPhotoLongClick: (CapturedPhoto) -> Unit = {},
    onToggleSelection: (CapturedPhoto) -> Unit = {}
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(4),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalItemSpacing = 4.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        items(photos) { photo ->
            FluidPhotoGridItem(
                photo = photo,
                onClick = {
                    if (isMultiSelectMode) {
                        onToggleSelection(photo)
                    } else {
                        onPhotoClick(photo)
                    }
                },
                onDelete = { onDeleteClick(photo) },
                onLongClick = { onPhotoLongClick(photo) },
                isSelected = selectedPhotos.contains(photo.id),
                isMultiSelectMode = isMultiSelectMode
            )
        }
    }
}

@Composable
private fun FluidPhotoGridItem(
    photo: CapturedPhoto,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false
) {
    // 원본 비율에 관계없이 썸네일은 세로 비율로 강제 설정
    val aspectRatio = remember(photo.id) {
        // photo.id를 기반으로 고정된 비율 생성 (무한 호출 방지)
        val hashCode = photo.id.hashCode()
        when (hashCode % 5) {
            0 -> 1f        // 정사각형
            1 -> 0.75f     // 3:4 세로형
            2 -> 0.6f      // 긴 세로형
            3 -> 0.8f      // 4:5 세로형  
            else -> 0.65f  // 중간 세로형
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick() }
            ),
        elevation = 2.dp,
        shape = RoundedCornerShape(6.dp),
        backgroundColor = if (isSelected) Color.LightGray else MaterialTheme.colors.surface
    ) {
        Box {
            // 사진 이미지 - 개선된 로딩
            val file = File(photo.filePath)

            val painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .size(300) // 썸네일 크기 제한
                    .crossfade(200)
                    .error(android.R.drawable.ic_menu_gallery) // 에러 시 기본 이미지
                    .fallback(android.R.drawable.ic_menu_gallery) // 로딩 실패 시 기본 이미지
                    .placeholder(android.R.drawable.ic_menu_gallery) // 로딩 중 표시할 이미지
                    .apply {
                        // sRGB 색공간 설정
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                        }
                    }
                    .build()
            )

            Image(
                painter = painter,
                contentDescription = "${photo.id} 썸네일",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // 로딩 상태 표시
            if (painter.state is coil.compose.AsyncImagePainter.State.Loading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colors.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colors.primary
                    )
                }
            }

            if (isMultiSelectMode && isSelected) {
                // 선택된 상태 오버레이
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Blue.copy(alpha = 0.3f))
                )

                // 체크 아이콘
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "선택됨",
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF27AE60) // 그린톤 (선택 표시)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colors.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "내 사진을 불러오는 중...",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun EmptyMyPhotosState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "저장된 사진이 없습니다",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "카메라에서 촬영하면\n이곳에 저장됩니다",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CapturedPhotoItem(
    photo: com.inik.camcon.domain.model.CapturedPhoto,
    onDelete: () -> Unit
) {
    // 이 함수는 더 이상 사용되지 않음 (그리드뷰로 변경)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: 사진 상세 보기 */ },
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 썸네일
            val painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(File(photo.filePath))
                    .crossfade(true)
                    .apply {
                        // sRGB 색공간 설정
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                        }
                    }
                    .build()
            )

            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 사진 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = File(photo.filePath).name,
                    style = MaterialTheme.typography.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
                Text(
                    text = dateFormat.format(Date(photo.captureTime)),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )

                // 파일 크기 표시
                val sizeText = when {
                    photo.size > 1024 * 1024 -> "${photo.size / (1024 * 1024)}MB"
                    photo.size > 1024 -> "${photo.size / 1024}KB"
                    else -> "${photo.size}B"
                }
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            // 삭제 버튼
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = Color.Red.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun MyPhotosMultiSelectActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$selectedCount 개의 항목 선택됨",
            style = MaterialTheme.typography.body1
        )

        Row {
            TextButton(onClick = onSelectAll) {
                Text("전체 선택")
            }
            TextButton(onClick = onDeselectAll) {
                Text("선택 해제")
            }
            TextButton(onClick = onDelete) {
                Text("삭제")
            }
            TextButton(onClick = onCancel) {
                Text("취소")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EmptyMyPhotosStatePreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        EmptyMyPhotosState()
    }
}

@Preview(showBackground = true)
@Composable
fun CapturedPhotoItemPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        CapturedPhotoItem(
            photo = CapturedPhoto(
                id = "1",
                filePath = "/storage/emulated/0/Pictures/IMG_001.jpg",
                thumbnailPath = "/storage/emulated/0/Pictures/thumb_IMG_001.jpg",
                captureTime = System.currentTimeMillis(),
                cameraModel = "Canon EOS R6",
                settings = null,
                size = 1024 * 1024 * 5,
                width = 1920,
                height = 1080
            ),
            onDelete = {}
        )
    }
}
