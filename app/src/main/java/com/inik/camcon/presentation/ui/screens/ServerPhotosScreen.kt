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
import androidx.compose.runtime.LaunchedEffect
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
import coil.compose.SubcomposeAsyncImage
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import com.inik.camcon.presentation.viewmodel.CameraFolderGroup
import com.inik.camcon.presentation.viewmodel.CameraFolderSelection
import com.inik.camcon.presentation.viewmodel.GalleryExportProgress
import com.inik.camcon.presentation.viewmodel.GalleryExportTargets
import com.inik.camcon.presentation.viewmodel.GalleryGroup
import com.inik.camcon.presentation.viewmodel.GalleryGroupKey
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
    // null 이 아니면 내보내기 확인 다이얼로그가 떠 있다. 미리 계산한 대상 집계를 함께 들고 있어
    // 다이얼로그 본문이 다시 계산하지 않는다.
    var exportConfirmTargets by remember { mutableStateOf<GalleryExportTargets?>(null) }

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

    // 그룹이 바뀌거나 닫히면 열려 있던 사진 선택도 버린다. 남겨 두면 다른 날짜를 열었을 때
    // 그 사진이 목록에 있는 경우 뷰어가 저절로 다시 열린다.
    LaunchedEffect(uiState.openedGroup) {
        selectedPhoto = null
    }

    // 뒤로가기 사슬 — 뷰어 → 3단 사진 그리드 → 2단 폴더 목록 → 1단 날짜 목록.
    // 한 번에 한 단계씩만 내려간다. 네 핸들러의 enabled 조건은 **서로 배타적**이라 어느 것이
    // 먼저 소비할지 선언 순서에 의존하지 않는다(직전 배치에서 확립한 규칙).

    // 멀티 선택 모드에서 뒤로가기 처리
    BackHandler(enabled = uiState.isMultiSelectMode && selectedPhoto == null) {
        viewModel.exitMultiSelectMode()
    }

    // 3단(사진 그리드) → 폴더 목록. 폴더 단을 건너뛰고 들어왔으면 날짜 목록으로 나간다
    // (그 판정은 ViewModel 의 closeFolder 가 한다 — 화면은 경로를 기억하지 않는다).
    BackHandler(
        enabled = selectedPhoto == null &&
                !uiState.isMultiSelectMode &&
                uiState.openedGroup != null &&
                uiState.openedFolder != null
    ) {
        viewModel.closeFolder()
    }

    // 2단(폴더 목록) → 날짜 목록. 탭을 떠나지 않는다.
    BackHandler(
        enabled = selectedPhoto == null &&
                !uiState.isMultiSelectMode &&
                uiState.openedGroup != null &&
                uiState.openedFolder == null
    ) {
        viewModel.closeGroup()
    }

    // 전체화면 뷰어에서 시스템 백은 뷰어만 닫고 2단 그리드에 남는다.
    //
    // ⚠️ [FullScreenPhotoViewer] 자체에는 BackHandler 가 없다 — 여는 쪽이 등록하는 규약이다
    // (CameraControlScreen 도 같은 방식). 이 핸들러가 없으면 위의 그룹 핸들러가 백을 대신
    // 소비해서 뷰어에서 곧장 1단 날짜 목록까지 건너뛴다.
    BackHandler(enabled = selectedPhoto != null) {
        selectedPhoto = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0)
            .statusBarsPadding()
    ) {
        // 상단 헤더 - 멀티 선택 모드에 따라 다르게 표시
        val opened = uiState.openedGroup
        if (opened != null && !uiState.isMultiSelectMode) {
            val openedFolder = uiState.openedFolder
            GalleryGroupHeader(
                group = opened,
                // 3단은 그 폴더의 사진 수, 2단은 폴더들의 합.
                photoCount = if (openedFolder != null) {
                    uiState.photos.size
                } else {
                    uiState.folders.sumOf { it.photoCount }
                },
                // 3단이면 어느 원본 폴더를 보고 있는지 제목 옆에 밝힌다. 폴더 단을 건너뛴
                // 날짜에서도 폴더명이 보여야 "어디를 보고 있는지"가 사라지지 않는다.
                folderLabel = openedFolder?.name,
                onBack = {
                    if (openedFolder != null) viewModel.closeFolder() else viewModel.closeGroup()
                },
                onRefresh = { viewModel.refreshPhotos() }
            )
        } else if (uiState.isMultiSelectMode) {
            MyPhotosMultiSelectActionBar(
                selectedCount = uiState.selectedPhotos.size,
                onSelectAll = { viewModel.selectAllPhotos() },
                onDeselectAll = { viewModel.deselectAllPhotos() },
                onExport = {
                    // 대상이 0장(전부 기기 저장소)이면 확인을 물을 것이 없다 —
                    // 그대로 실행해 "이미 기기 저장소" 안내만 띄운다.
                    val plan = viewModel.previewExportTargets()
                    if (plan.targets.isEmpty()) {
                        viewModel.exportSelectedPhotos()
                    } else {
                        exportConfirmTargets = plan
                    }
                },
                onDelete = { showDeleteConfirmDialog = true },
                onCancel = { viewModel.exitMultiSelectMode() },
                exportProgress = uiState.exportProgress,
                onCancelExport = { viewModel.cancelExport() }
            )
        } else {
            ModernMyPhotosHeader(
                photoCount = uiState.groups.sumOf { it.photoCount },
                onRefresh = { viewModel.refreshPhotos() }
            )
        }

        when {
            uiState.isLoading -> {
                LoadingIndicator()
            }

            // 1단 — 날짜 목록. 사진은 아직 읽지 않는다(폴더 이름과 개수만 보고 그린다).
            opened == null -> {
                if (uiState.groups.isEmpty()) {
                    EmptyMyPhotosState()
                } else {
                    GalleryGroupList(
                        groups = uiState.groups,
                        onGroupClick = { viewModel.openGroup(it) }
                    )
                }
            }

            // 2단 — 그 날짜의 원본 폴더 목록. 여기도 파일을 읽지 않고 폴더 이름과 개수만 그린다.
            // (폴더가 하나뿐인 날짜는 ViewModel 이 3단으로 건너뛰므로 이 화면에 오지 않는다.)
            uiState.openedFolder == null -> {
                if (uiState.folders.isEmpty()) {
                    EmptyMyPhotosState()
                } else {
                    CameraFolderList(
                        folders = uiState.folders,
                        onFolderClick = { viewModel.openFolder(CameraFolderSelection(it.folder)) }
                    )
                }
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

    // 내보내기 확인 다이얼로그. 대상이 0장이면 열지 않는다(위 onExport 에서 걸러진다).
    exportConfirmTargets?.let { plan ->
        AppDialog(
            onDismissRequest = { exportConfirmTargets = null },
            title = { Text(stringResource(R.string.gallery_export_confirm_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.gallery_export_confirm_message,
                            plan.targets.size
                        )
                    )
                    if (plan.alreadyInDeviceStorage > 0) {
                        Text(
                            text = stringResource(
                                R.string.gallery_export_confirm_skipped,
                                plan.alreadyInDeviceStorage
                            ),
                            style = BodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = stringResource(R.string.gallery_export_action),
                    onClick = {
                        viewModel.exportSelectedPhotos()
                        exportConfirmTargets = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { exportConfirmTargets = null }) {
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

    // 일괄 내보내기 결과 안내. 성공·건너뜀·실패를 한 줄에 정직하게 싣는다.
    uiState.exportSummary?.let { summary ->
        // 한 장도 못 내보냈고 실패만 있으면 오류로 알린다(그 외에는 정보 안내).
        val isFailure = summary.exported == 0 && summary.failed > 0
        val message = buildList {
            if (summary.exported > 0) {
                add(stringResource(R.string.gallery_export_result_exported, summary.exported))
            }
            if (summary.alreadyInDeviceStorage > 0) {
                add(
                    stringResource(
                        R.string.gallery_export_result_skipped,
                        summary.alreadyInDeviceStorage
                    )
                )
            }
            if (summary.failed > 0) {
                add(stringResource(R.string.gallery_export_result_failed, summary.failed))
            }
        }.joinToString(", ")

        LaunchedEffect(summary) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearExportSummary()
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Snackbar(
                modifier = Modifier.padding(Spacing.base),
                containerColor = if (isFailure) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                dismissAction = {
                    IconButton(onClick = { viewModel.clearExportSummary() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = if (isFailure) {
                                MaterialTheme.colorScheme.onError
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            ) {
                Text(
                    text = message,
                    color = if (isFailure) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
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
    // ⚠️ 스태거드 그리드가 아니라 **균일 그리드**다.
    //
    // 스태거드는 항목을 "가장 짧은 열"에 넣으므로 마지막 줄의 한 장이 첫 칸이 아니라 아무 칸에나
    // 뜨고, 항목 높이가 제각각이라 줄마다 좌우 오프셋도 미묘하게 어긋난다(실기 스크린샷 확인).
    // 사진 격자는 줄이 가지런한 편이 훑기 좋으므로 고정 4칸·정사각 타일로 간다.
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        // 바깥 여백은 헤더 좌측 앵커(Spacing.base)와 일치시키고, 타일 간 거터만 4dp로 촘촘히 유지한다.
        contentPadding = PaddingValues(
            start = Spacing.base,
            end = Spacing.base,
            top = Spacing.xs,
            bottom = Spacing.lg
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = photos,
            key = { it.id }  // key 추가: 아이템 안정성 보장
        ) { photo ->
            // ⚠️ remember(photo.id){ derivedStateOf{...} } 금지 — selectedPhotos 는 State 가 아니라
            // 일반 파라미터라 derivedStateOf 가 변화를 감지하지 못하고, remember 키(photo.id)도 안
            // 바뀌어 첫 컴포지션의 Set 을 영원히 캡처한다(전체 선택해도 체크 표시 안 됨, 2026-08-18 실측).
            val isSelected = selectedPhotos.contains(photo.id)
            
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

/**
 * EXIF 방향만큼 비트맵을 돌린다. RAW 내장 썸네일 전용이다.
 *
 * JPEG/PNG 는 Coil 이 알아서 처리하므로 이 함수를 쓰지 않는다. RAW 만 Coil 이 못 읽어 수동
 * 경로가 남았고, 그 경로에도 같은 보정을 넣어야 화면이 어긋나지 않는다.
 */
private fun rotateByExif(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees % 360 == 0) return bitmap
    return try {
        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    } catch (e: OutOfMemoryError) {
        // 썸네일이라 사실상 안 나지만, 나더라도 눕은 채로 보여주는 편이 크래시보다 낫다.
        Log.w("ServerPhotosScreen", "RAW 썸네일 회전 중 메모리 부족 — 원본 그대로 표시", e)
        bitmap
    }
}

// 빈 화면 안내 문구가 넓은 화면에서 한 줄로 늘어지지 않도록 잡는 측정 폭 상한.
private val EmptyStateMaxWidth = 320.dp

// RAW 내장 썸네일 전용 LRU 캐시 (메모리의 1/8, 최대 64MB).
//
// ⚠️ **JPEG/PNG 는 여기 오지 않는다.** 그쪽은 Coil 이 맡고 Coil 의 메모리·디스크 캐시를 쓴다
// (수동 decode 금지 규약). RAW 만 Coil 이 읽지 못해 수동 경로가 남았고, 그 경로가 스크롤마다
// EXIF 를 다시 읽지 않도록 이 캐시와 아래 세마포어를 남겨 둔다.
private val thumbnailCache: LruCache<String, Bitmap> by lazy {
    val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    val cacheSize = minOf(maxMemory / 8, 64 * 1024)  // 최대 64MB
    object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }
}

// RAW 내장 썸네일 동시 로드 수 제한 (최대 4개). JPEG/PNG 는 Coil 이 자체 큐로 관리한다.
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
    // 균일 그리드라 타일은 정사각이다. 사진 비율은 Crop 이 맞추고, 원본 비율은 뷰어에서 본다.
    val aspectRatio = 1f

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
                                    // ⚠️ 내장 썸네일에도 **방향 보정을 적용한다.** RAW 는 Coil 이
                                    // 읽지 못해 이 수동 경로가 남는데, 보정을 빠뜨리면 JPEG 만
                                    // 바로 서고 RAW 만 눕는 어긋난 화면이 된다.
                                    exif?.thumbnailBitmap?.let { bitmap ->
                                        rotateByExif(bitmap, exif.rotationDegrees)
                                    }?.also { bitmap ->
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
                // JPEG/PNG: **Coil 로 로드한다. 수동 디코딩 금지.**
                //
                // 예전에는 여기서 시스템 썸네일(300×300)을 직접 받아 셀에 그렸다. 두 가지가 한꺼번에
                // 틀어졌다.
                //  ① 흐림 — 300px 짜리를 셀 크기로 확대하니 뭉개진다.
                //  ② 세로 사진 눕힘 — 수동 경로는 EXIF orientation 을 읽지 않는다. 수신 사진을
                //     픽셀 회전 없이 태그만 보존하도록 바꾼 뒤(저장 배치)로는 태그를 해석하지 않는
                //     표시 경로가 곧 눕은 사진이 된다.
                //
                // Coil 은 orientation 을 자동 적용하고, size() 로 셀 크기를 알려 주면 그 해상도로
                // 다운샘플한다. 자체 LruCache·세마포어는 Coil 의 메모리/디스크 캐시가 대신한다
                // (갤러리 스트립 7.7초 정지 사고 이후 굳은 규약 — 수동 decode 금지).
                val context = LocalContext.current
                val model = remember(photo.id, photo.filePath) {
                    // MediaStore 항목은 content URI 로 관통한다(스코프드 스토리지에서 파일 경로가 막힌다).
                    photo.id.toLongOrNull()
                        ?.let {
                            ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, it
                            )
                        }
                        ?: photo.filePath
                }

                // SubcomposeAsyncImage 는 **컴포저블의 실측 크기**를 요청에 넘긴다. 셀이 얼마나
                // 큰지 상수로 추측할 필요가 없어 흐림도 과다 디코딩도 생기지 않는다.
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(true)
                        .allowHardware(true)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = photo.filePath.substringAfterLast('/'),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        SkeletonLoader(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(Radius.sm),
                            announceLoading = false
                        )
                    }
                )
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
    // 실사진 그리드와 **같은 배치**여야 로딩→로드 전환에서 타일이 튀지 않는다.
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        // 로딩→로드 전환에서 좌측 앵커가 튀지 않도록 FluidPhotoGrid와 동일한 여백을 쓴다.
        contentPadding = PaddingValues(
            start = Spacing.base,
            end = Spacing.base,
            top = Spacing.xs,
            bottom = Spacing.lg
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    /** null 이 아니면 일괄 내보내기가 도는 중이다. */
    exportProgress: GalleryExportProgress? = null,
    onCancelExport: () -> Unit = {}
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

        if (exportProgress != null) {
            // 내보내는 동안에는 액션을 감추고 진행 상황과 취소만 남긴다 — 도는 중에 삭제나
            // 선택 변경이 끼어들면 집계가 무엇을 센 것인지 알 수 없게 된다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.gallery_export_progress,
                        exportProgress.done,
                        exportProgress.total
                    ),
                    style = ButtonText,
                    color = TextSecondaryV2,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ActionBarTextButton(
                    text = stringResource(R.string.cancel),
                    color = TextSecondaryV2,
                    onClick = onCancelExport,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            return@Column
        }

        // 액션은 개수 옆에 두면 독일어처럼 라벨이 긴 로케일에서 잘리므로 별도 행으로 내리고,
        // weight(fill = false)로 폭이 모자랄 때만 균등 분배되게 해 어떤 로케일에서도 버튼이 사라지지 않게 한다.
        // 다섯 개를 한 줄에 세우면 그 로케일에서 라벨이 다시 잘리므로 선택 도우미와 실제 동작을
        // 두 줄로 가른다.
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
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionBarTextButton(
                text = stringResource(R.string.gallery_export_action),
                color = TextSecondaryV2,
                onClick = onExport,
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
            onExport = {},
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

/**
 * 갤러리 탭 1단 — 날짜 목록.
 *
 * 사진을 읽지 않는다. 항목은 날짜(또는 "기기 저장소")와 장수뿐이고, 그 값은 폴더 이름과 파일
 * 개수만으로 구한다([PhotoLibraryLocation.listDateFolders]). 수백 장에서도 탭 진입이 가볍다.
 *
 * 대표 썸네일은 넣지 않았다. 넣으려면 날짜마다 사진 한 장을 골라 디코딩해야 하는데, 그러면 이
 * 화면이 가벼워야 한다는 요건과 정면으로 어긋난다.
 */
@Composable
private fun GalleryGroupList(
    groups: List<GalleryGroup>,
    onGroupClick: (GalleryGroupKey) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(
            count = groups.size,
            key = { index -> groups[index].key.toString() }
        ) { index ->
            val group = groups[index]
            GalleryGroupRow(group = group, onClick = { onGroupClick(group.key) })
        }
    }
}

/** 날짜 목록의 항목 하나. 기기 저장소도 같은 문법으로 그린다(명칭 + 장수). */
@Composable
private fun GalleryGroupRow(
    group: GalleryGroup,
    onClick: () -> Unit
) {
    SurfaceV2(
        tier = 1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (group.key) {
                    is GalleryGroupKey.Date -> Icons.Default.DateRange
                    GalleryGroupKey.DeviceStorage -> Icons.Default.PhoneAndroid
                },
                contentDescription = null,
                tint = Accent
            )
            Spacer(modifier = Modifier.width(Spacing.base))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (val key = group.key) {
                        is GalleryGroupKey.Date -> key.date
                        GalleryGroupKey.DeviceStorage ->
                            stringResource(R.string.gallery_group_device_storage)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryV2
                )
                Text(
                    text = stringResource(R.string.gallery_group_photo_count, group.photoCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryV2
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondaryV2
            )
        }
    }
}

/**
 * 갤러리 탭 2단(원본 폴더 목록).
 *
 * 1단과 같은 문법으로 그린다 — 이름 + 장수 + 진입 화살표. 항목 수가 적어 파일을 읽지 않는다.
 */
@Composable
private fun CameraFolderList(
    folders: List<CameraFolderGroup>,
    onFolderClick: (CameraFolderGroup) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(
            count = folders.size,
            key = { index -> folders[index].folder ?: "__other__" }
        ) { index ->
            val folder = folders[index]
            CameraFolderRow(folder = folder, onClick = { onFolderClick(folder) })
        }
    }
}

/** 폴더 목록의 항목 하나. 원본 폴더 조각이 없는 묶음은 "기타"로 그린다. */
@Composable
private fun CameraFolderRow(
    folder: CameraFolderGroup,
    onClick: () -> Unit
) {
    SurfaceV2(
        tier = 1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = Accent
            )
            Spacer(modifier = Modifier.width(Spacing.base))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.folder ?: stringResource(R.string.gallery_folder_other),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryV2
                )
                Text(
                    text = stringResource(R.string.gallery_group_photo_count, folder.photoCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryV2
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondaryV2
            )
        }
    }
}

/**
 * 갤러리 탭 2·3단 헤더 — 뒤로가기 + 그룹 이름 + 장수.
 *
 * [folderLabel] 이 있으면 3단(그 원본 폴더의 사진)이라는 뜻이라 이름 옆에 함께 적는다.
 * 시스템 백은 화면 상단의 [BackHandler] 가 같은 동작을 한다.
 */
@Composable
private fun GalleryGroupHeader(
    group: GalleryGroupKey,
    photoCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    folderLabel: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = TextPrimaryV2
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            val title = when (group) {
                is GalleryGroupKey.Date -> group.date
                GalleryGroupKey.DeviceStorage ->
                    stringResource(R.string.gallery_group_device_storage)
            }
            Text(
                text = if (folderLabel != null) "$title · $folderLabel" else title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryV2
            )
            Text(
                text = stringResource(R.string.gallery_group_photo_count, photoCount),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryV2
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.cd_refresh),
                tint = TextPrimaryV2
            )
        }
    }
}
