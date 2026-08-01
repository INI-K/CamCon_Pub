package com.inik.camcon.presentation.ui.screens

import android.util.Log
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import android.content.ContentUris
import android.graphics.Bitmap
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CapturedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.ButtonText
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DisplayNum
import com.inik.camcon.presentation.theme.ErrorV2
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.HeadingS
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.MonoMicro
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface2
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.theme.TouchTarget
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.SkeletonLoader
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.ui.screens.components.FullScreenPhotoViewer
import com.inik.camcon.presentation.util.imageContentUriOrNull
import com.inik.camcon.presentation.viewmodel.ServerPhotosViewModel
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.utils.LogMask
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyPhotosScreen(
    viewModel: ServerPhotosViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPhoto by remember { mutableStateOf<CapturedPhoto?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0)
            .statusBarsPadding()
    ) {
        // 상단 헤더 - 멀티 선택 모드에 따라 다르게 표시
        if (uiState.isMultiSelectMode) {
            MyPhotosMultiSelectActionBar(
                selectedCount = uiState.selectedPhotos.size,
                onSelectAll = { viewModel.selectAllPhotos() },
                onDeselectAll = { viewModel.deselectAllPhotos() },
                onDelete = { showDeleteConfirmDialog = true },
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
                size = capturedPhoto.size,
                // CapturedPhoto.id 가 MediaStore _ID 면 content URI 로 관통(로드/EXIF/공유).
                // UUID 폴백 row(파일시스템 폴백)면 uri=null → 기존 File 경로 사용. (경계 회귀: MediaStoreUrisTest)
                uri = imageContentUriOrNull(capturedPhoto.id)
            )
        }

        if (currentIndex >= 0 && cameraPhotos.isNotEmpty()) {
            val currentCameraPhoto = cameraPhotos[currentIndex]

            // 파일 존재 여부 로그
            val file = File(currentCameraPhoto.path)
            Log.d(
                "MyPhotosScreen",
                "선택된 사진: ${LogMask.path(currentCameraPhoto.path)}, 존재=${file.exists()}, 크기=${file.length()}bytes"
            )

            val viewerContext = LocalContext.current

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
                localPhotos = cameraPhotos,
                onFilmEdit = { target ->
                    // own-media(API29+)는 uri 로만 접근 가능 → uri 우선, 없으면 기존 파일경로.
                    val uri = target.uri
                    if (uri != null) {
                        com.inik.camcon.presentation.ui.FilmEditorActivity.startForPhoto(
                            viewerContext, android.net.Uri.parse(uri)
                        )
                    } else {
                        com.inik.camcon.presentation.ui.FilmEditorActivity.startForPhoto(
                            viewerContext, target.path
                        )
                    }
                },
                isRawFile = viewModel::isRawFile
            )
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteConfirmDialog) {
        AppDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.server_photos_delete_photos)) },
            text = { Text(stringResource(R.string.server_photos_delete_confirm, uiState.selectedPhotos.size)) },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(R.string.delete),
                    onClick = {
                        viewModel.deleteSelectedPhotos()
                        showDeleteConfirmDialog = false
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 에러 표시
    uiState.error?.let { error ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                modifier = Modifier.padding(Spacing.base),
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                action = {
                    TextButton(onClick = { viewModel.refreshPhotos(); viewModel.clearError() }) {
                        Text(
                            text = stringResource(R.string.server_photos_retry),
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                },
                // 영구 표시되던 에러 스낵바에 닫기 수단 추가(재시도 없이 해제 가능).
                dismissAction = {
                    IconButton(onClick = { viewModel.clearError() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onError
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
            .padding(
                start = Spacing.base,
                // IconButton 내부 12dp를 감안한 광학 정렬(4+12=16 ≈ base).
                end = Spacing.xs,
                top = Spacing.sm,
                bottom = Spacing.xs
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hero: 이 화면의 존재 이유인 저장 사진 수. eyebrow(11sp) : 카운터(34sp) = 1:3 콘트라스트.
        Column {
            Text(
                text = stringResource(R.string.server_photos_v3_eyebrow),
                style = MicroLabel,
                color = TextTertiary
            )
            Row {
                Text(
                    text = photoCount.toString(),
                    style = DisplayNum,   // 34sp Bold + tnum
                    color = TextPrimaryV2,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(R.string.server_photos_v3_photos_unit),
                    style = MicroLabel,
                    color = TextSecondaryV2,
                    modifier = Modifier.alignByBaseline()
                )
            }
        }

        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = stringResource(R.string.cd_refresh),
                tint = TextSecondaryV2
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
        // 바깥 여백은 헤더 좌측 앵커(Spacing.base)와 일치시키고, 타일 간 거터만 4dp로 촘촘히 유지한다.
        contentPadding = PaddingValues(
            start = Spacing.base,
            end = Spacing.base,
            top = Spacing.xs,
            bottom = Spacing.lg
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalItemSpacing = Spacing.xs,
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = photos,
            key = { it.id }  // key 추가: 아이템 안정성 보장
        ) { photo ->
            // 선택 상태를 derivedStateOf로 최적화
            val isSelected by remember(photo.id) {
                derivedStateOf { selectedPhotos.contains(photo.id) }
            }
            
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
                isSelected = isSelected,
                isMultiSelectMode = isMultiSelectMode
            )
        }
    }
}

// RAW 파일 확장자 목록
private val RAW_EXTENSIONS = setOf("nef", "cr2", "cr3", "arw", "dng", "orf", "rw2", "raf", "raw")

// 빈 화면 안내 문구가 넓은 화면에서 한 줄로 늘어지지 않도록 잡는 측정 폭 상한.
private val EmptyStateMaxWidth = 320.dp

// 썸네일 LRU 캐시 (메모리의 1/8 사용, 최대 64MB)
private val thumbnailCache: LruCache<String, Bitmap> by lazy {
    val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    val cacheSize = minOf(maxMemory / 8, 64 * 1024)  // 최대 64MB
    object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }
}

// 동시 썸네일 로드 수 제한 (최대 4개)
private val thumbnailLoadSemaphore = kotlinx.coroutines.sync.Semaphore(4)

@Composable
private fun FluidPhotoGridItem(
    photo: CapturedPhoto,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false
) {
    val aspectRatio = remember(photo.id) {
        if (photo.width > 0 && photo.height > 0) {
            photo.width.toFloat() / photo.height.toFloat()
        } else {
            0.75f
        }
    }

    // RAW 파일 여부 확인
    val isRawFile = remember(photo.filePath) {
        photo.filePath.substringAfterLast('.', "").lowercase() in RAW_EXTENSIONS
    }

    SurfaceV2(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { onLongClick() }
            ),
        tier = if (isSelected) 4 else 2,
        border = true,
        shape = RoundedCornerShape(Radius.sm)
    ) {
        Box {
            if (isRawFile) {
                // RAW 파일: 캐시 → content URI(ExifInterface) 순서로 내장 썸네일 로드.
                // 스코프드 스토리지(API29+)에서 raw 경로 접근이 막히므로 _ID → content URI 로 EXIF 관통.
                // id 가 _ID(Long) 가 아니면(UUID 폴백) 기존 filePath 로 폴백.
                val context = LocalContext.current
                val rawThumbnailState = produceState<Bitmap?>(
                    initialValue = thumbnailCache.get(photo.id),
                    key1 = photo.id
                ) {
                    if (value == null) {
                        thumbnailLoadSemaphore.acquire()
                        try {
                            value = withContext(Dispatchers.IO) {
                                try {
                                    val mediaId = photo.id.toLongOrNull()
                                    val exif = if (mediaId != null) {
                                        val uri = ContentUris.withAppendedId(
                                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId
                                        )
                                        context.contentResolver.openInputStream(uri)?.use {
                                            ExifInterface(it)
                                        }
                                    } else {
                                        ExifInterface(photo.filePath)
                                    }
                                    exif?.thumbnailBitmap?.also { bitmap ->
                                        thumbnailCache.put(photo.id, bitmap)
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        } finally {
                            thumbnailLoadSemaphore.release()
                        }
                    }
                }

                when (val thumbnail = rawThumbnailState.value) {
                    null -> {
                        // 로딩 중 또는 썸네일 없음: placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Surface2),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.lg),
                                    tint = TextTertiary
                                )
                                Text(
                                    // NEF/CR3 같은 포맷 코드 — 배지형 모노 수치 슬롯.
                                    text = photo.filePath.substringAfterLast('.', "").uppercase(),
                                    style = MonoMicro,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                    else -> {
                        // 내장 썸네일이 있으면 표시
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = "${photo.id} RAW 썸네일",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            } else {
                // JPEG/PNG: 캐시 → 시스템 썸네일 순서로 로드
                val context = LocalContext.current
                val thumbnailState = produceState<Bitmap?>(
                    initialValue = thumbnailCache.get(photo.id),
                    key1 = photo.id
                ) {
                    if (value == null) {
                        thumbnailLoadSemaphore.acquire()
                        try {
                            value = withContext(Dispatchers.IO) {
                                try {
                                    val mediaId = photo.id.toLongOrNull()
                                    val bitmap = if (mediaId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        // Android 10+: 시스템 썸네일 사용 (가장 빠름)
                                        val uri = ContentUris.withAppendedId(
                                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                                            mediaId
                                        )
                                        context.contentResolver.loadThumbnail(
                                            uri,
                                            Size(300, 300),
                                            CancellationSignal()
                                        )
                                    } else {
                                        // 폴백: ExifInterface로 내장 썸네일 추출
                                        val exif = ExifInterface(photo.filePath)
                                        exif.thumbnailBitmap
                                    }
                                    bitmap?.also { thumbnailCache.put(photo.id, it) }
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        } finally {
                            thumbnailLoadSemaphore.release()
                        }
                    }
                }

                when (val thumbnail = thumbnailState.value) {
                    null -> {
                        // 로딩 중: CINE 정합 스켈레톤 shimmer (그리드 개별 타일이라 발화 억제)
                        SkeletonLoader(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(Radius.sm),
                            announceLoading = false
                        )
                    }
                    else -> {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = "${photo.id} 썸네일",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // 다중선택 모드에서는 미선택 타일에도 빈 체크 링을 그려 '선택 가능' 상태를 알린다.
            if (isMultiSelectMode) {
                if (isSelected) {
                    // 사진 위 앰버 워시는 색 판정을 왜곡하므로 중립 스크림 + 앰버 엣지 1px으로 표현한다.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.38f))
                            .border(
                                StrokeWidth.thin,
                                Accent,
                                RoundedCornerShape(Radius.sm)
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(Spacing.xs),
                    contentAlignment = Alignment.TopEnd
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.cd_selected),
                            modifier = Modifier.size(IconSize.lg),
                            tint = Accent
                        )
                    } else {
                        val unselectedLabel =
                            stringResource(R.string.server_photos_v3_cd_unselected)
                        Box(
                            modifier = Modifier
                                .size(IconSize.lg)
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .border(
                                    StrokeWidth.thin,
                                    TextSecondaryV2.copy(alpha = 0.8f),
                                    CircleShape
                                )
                                .semantics { contentDescription = unselectedLabel }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    // CINE 정합: 스피너+텍스트 대신 4열 타일 스켈레톤 shimmer로 목록 로딩을 표현.
    val placeholders = remember {
        listOf(
            "s0" to 0.75f, "s1" to 1f, "s2" to 0.66f, "s3" to 1.33f,
            "s4" to 0.85f, "s5" to 1f, "s6" to 0.7f, "s7" to 1.2f
        )
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(4),
        // 로딩→로드 전환에서 좌측 앵커가 튀지 않도록 FluidPhotoGrid와 동일한 여백을 쓴다.
        contentPadding = PaddingValues(
            start = Spacing.base,
            end = Spacing.base,
            top = Spacing.xs,
            bottom = Spacing.lg
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalItemSpacing = Spacing.xs,
        modifier = Modifier.fillMaxSize()
    ) {
        items(items = placeholders, key = { it.first }) { (key, ratio) ->
            SkeletonLoader(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio),
                shape = RoundedCornerShape(Radius.sm),
                announceLoading = key == "s0"
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
        // 빈 화면 = Airy 등급(base 20dp). 중앙 정렬은 이 슬롯에서만 허용된다.
        Column(
            modifier = Modifier
                .padding(Spacing.lg)
                .widthIn(max = EmptyStateMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(IconSize.xl),
                tint = TextTertiary
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.server_photos_no_saved_photos),
                style = HeadingL,
                color = TextPrimaryV2,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.server_photos_capture_hint),
                style = BodySmall,
                color = TextSecondaryV2,
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
    SurfaceV2(
        modifier = Modifier.fillMaxWidth(),
        tier = 2,
        border = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 썸네일
            val painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(File(photo.filePath))
                    .crossfade(true)
                    .memoryCacheKey(photo.id)
                    .diskCacheKey(photo.id)
                    .build()
            )

            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(Spacing.md))

            // 사진 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = File(photo.filePath).name,
                    style = HeadingS,
                    color = TextPrimaryV2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
                Text(
                    text = dateFormat.format(Date(photo.captureTime)),
                    style = MonoNumeric,
                    color = TextSecondaryV2
                )

                // 파일 크기 표시
                val sizeText = when {
                    photo.size > 1024 * 1024 -> "${photo.size / (1024 * 1024)}MB"
                    photo.size > 1024 -> "${photo.size / 1024}KB"
                    else -> "${photo.size}B"
                }
                Text(
                    text = sizeText,
                    style = MonoNumeric,
                    color = TextTertiary
                )
            }

            // 삭제 버튼
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.base,
                end = Spacing.sm,
                top = Spacing.sm,
                bottom = Spacing.xs
            )
    ) {
        // 다중선택 모드에서는 선택 개수가 헤더와 같은 자리의 Hero 역할을 이어받는다.
        Text(
            text = stringResource(R.string.server_photos_v3_selected_eyebrow),
            style = MicroLabel,
            color = TextTertiary
        )
        Text(
            text = selectedCount.toString(),
            style = DisplayNum,   // 34sp Bold + tnum
            color = Accent        // 선택 = 활성 상태 → 앰버
        )

        // 액션 4종은 개수 옆에 두면 독일어처럼 라벨이 긴 로케일에서 잘리므로 별도 행으로 내리고,
        // weight(fill = false)로 폭이 모자랄 때만 균등 분배되게 해 어떤 로케일에서도 버튼이 사라지지 않게 한다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionBarTextButton(
                text = stringResource(R.string.server_photos_select_all),
                color = TextSecondaryV2,
                onClick = onSelectAll,
                modifier = Modifier.weight(1f, fill = false)
            )
            ActionBarTextButton(
                text = stringResource(R.string.server_photos_deselect_all),
                color = TextSecondaryV2,
                onClick = onDeselectAll,
                modifier = Modifier.weight(1f, fill = false)
            )
            ActionBarTextButton(
                text = stringResource(R.string.delete),
                color = ErrorV2,
                onClick = onDelete,
                modifier = Modifier.weight(1f, fill = false)
            )
            ActionBarTextButton(
                text = stringResource(R.string.cancel),
                color = TextSecondaryV2,
                onClick = onCancel,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun ActionBarTextButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = TouchTarget.min),
        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(
            text = text,
            style = ButtonText,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}

// 실촬영 값 기준 프리뷰 샘플 — MediaStore _ID, Z8 RAW 파일명/용량/화소.
private val PreviewCapturedPhoto = CapturedPhoto(
    id = "1000004417",
    filePath = "/storage/emulated/0/DCIM/CamCon/DSC_4417.NEF",
    thumbnailPath = "/storage/emulated/0/DCIM/CamCon/.thumb/DSC_4417.jpg",
    captureTime = 1_753_901_640_000L,   // 2025.07.31 02:34
    cameraModel = "NIKON Z 8",
    settings = null,
    size = 54_318_912L,
    width = 8256,
    height = 5504
)

@Preview(name = "MyPhotos Header Hero", showBackground = true, backgroundColor = 0xFF050607)
@Composable
private fun ModernMyPhotosHeaderPreview() {
    CamConTheme {
        ModernMyPhotosHeader(photoCount = 1284, onRefresh = {})
    }
}

@Preview(name = "MyPhotos MultiSelect Bar", showBackground = true, backgroundColor = 0xFF050607)
@Composable
private fun MyPhotosMultiSelectActionBarPreview() {
    CamConTheme {
        MyPhotosMultiSelectActionBar(
            selectedCount = 37,
            onSelectAll = {},
            onDeselectAll = {},
            onDelete = {},
            onCancel = {}
        )
    }
}

@Preview(name = "MyPhotos Empty", showBackground = true, backgroundColor = 0xFF050607)
@Composable
fun EmptyMyPhotosStatePreview() {
    CamConTheme() {
        EmptyMyPhotosState()
    }
}

@Preview(name = "MyPhotos Captured Row", showBackground = true, backgroundColor = 0xFF050607)
@Composable
fun CapturedPhotoItemPreview() {
    CamConTheme() {
        CapturedPhotoItem(
            photo = PreviewCapturedPhoto,
            onDelete = {}
        )
    }
}
