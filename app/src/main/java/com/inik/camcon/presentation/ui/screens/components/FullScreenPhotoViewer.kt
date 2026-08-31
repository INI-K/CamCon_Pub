package com.inik.camcon.presentation.ui.screens.components

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import androidx.core.content.FileProvider
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 공유 토스트/Chooser 라벨용 i18n 문자열 묶음.
 * Composable 밖 함수에서는 stringResource를 호출할 수 없으므로 호출자가 전달한다.
 */
private data class ShareStrings(
    val chooserTitle: String,
    val noImageData: String,
    val failed: String
)

/**
 * 현재 보여지는 사진을 공유
 */
private fun shareCurrentPhoto(
    scope: CoroutineScope,
    context: android.content.Context,
    photo: CameraPhoto,
    viewModel: PhotoPreviewViewModel?,
    fullImageData: ByteArray? = null,
    thumbnailData: ByteArray? = null,
    strings: ShareStrings
) {
    // TODO(LOW): Dispatchers.IO 하드코딩 — ViewModel/UseCase 위임으로 옮길 것
    scope.launch(Dispatchers.IO) {
        try {
            // 0. MediaStore content URI 가 있으면(로컬 갤러리 own-media) 직접 공유.
            //    스코프드 스토리지(API29+)에서 raw 경로가 막혀도 FileProvider/임시복사 없이
            //    content URI 를 EXTRA_STREAM 에 넣고 읽기 권한만 위임한다.
            val mediaUri = photo.uri
            if (mediaUri != null) {
                withContext(Dispatchers.Main) {
                    try {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(mediaUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(shareIntent, strings.chooserTitle)
                        context.startActivity(chooser)
                        Log.d("PhotoShare", "MediaStore URI 공유 시작: $mediaUri")
                    } catch (e: Exception) {
                        Log.e("PhotoShare", "MediaStore URI 공유 인텐트 실행 실패", e)
                        Toast.makeText(context, strings.failed, Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            // 1. 로컬 파일인 경우
            val isLocalFile = java.io.File(photo.path).exists()

            if (isLocalFile) {
                Log.d("PhotoShare", "로컬 파일 직접 공유: ${LogMask.path(photo.path)}")

                withContext(Dispatchers.Main) {
                    try {
                        val file = java.io.File(photo.path)
                        val fileUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )

                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(shareIntent, strings.chooserTitle)
                        context.startActivity(chooser)

                        Log.d("PhotoShare", "로컬 파일 공유 시작: ${file.name} (${file.length()} bytes)")
                    } catch (e: Exception) {
                        Log.e("PhotoShare", "로컬 파일 공유 인텐트 실행 실패", e)
                        Toast.makeText(context, strings.failed, Toast.LENGTH_SHORT).show()
                    }
                }
                return@launch
            }

            // 2. 서버 사진의 경우: 최대한 원본에 가까운 imageData를 우선 사용
            val imageData = when {
                fullImageData != null && fullImageData.isNotEmpty() -> fullImageData
                viewModel != null -> {
                    val fullImage = viewModel.fullImageCache.value[photo.path]
                    val thumbnail = viewModel.getThumbnail(photo.path)
                    fullImage ?: thumbnail
                }
                thumbnailData != null && thumbnailData.isNotEmpty() -> thumbnailData
                else -> null
            }

            if (imageData != null && imageData.isNotEmpty()) {
                val cacheDir = File(context.cacheDir, "shared_photos")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val tempFile = File(cacheDir, "share_${photo.name}")
                FileOutputStream(tempFile).use { fos ->
                    fos.write(imageData)
                }

                withContext(Dispatchers.Main) {
                    try {
                        val fileUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            tempFile
                        )

                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(shareIntent, strings.chooserTitle)
                        context.startActivity(chooser)

                        Log.d("PhotoShare", "서버 사진 공유 시작: ${tempFile.name} (${imageData.size} bytes)")
                    } catch (e: Exception) {
                        Log.e("PhotoShare", "공유 인텐트 실행 실패", e)
                        Toast.makeText(context, strings.failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, strings.noImageData, Toast.LENGTH_SHORT).show()
                    Log.w("PhotoShare", "공유할 이미지 데이터 없음: viewModel=${viewModel != null}, fullImageData=${fullImageData?.let { "${it.size} bytes" } ?: "null"}, thumbnailData=${thumbnailData?.let { "${it.size} bytes" } ?: "null"}")
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoShare", "사진 공유 준비 중 오류", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, strings.failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * 전체화면 사진 뷰어 — HorizontalPager 기반 스와이프 네비게이션
 */
/** 하단 썸네일 스트립(80dp 항목 + 상하 패딩)을 피해 플로팅 버튼을 띄우는 높이. */
private val ThumbnailStripClearance = 132.dp

@Composable
fun FullScreenPhotoViewer(
    photo: CameraPhoto,
    onDismiss: () -> Unit,
    onPhotoChanged: (CameraPhoto) -> Unit,
    thumbnailData: ByteArray?,
    fullImageData: ByteArray?,
    isDownloadingFullImage: Boolean = false,
    onDownload: () -> Unit,
    viewModel: PhotoPreviewViewModel? = null,
    hideDownloadButton: Boolean = false,
    localPhotos: List<CameraPhoto>? = null,
    onDeleteRequest: ((CameraPhoto) -> Unit)? = null,
    onFilmEdit: ((CameraPhoto) -> Unit)? = null,
    isRawFile: (String) -> Boolean = { false },
    /** 좋아요 상태 조회. null 을 돌려주면 하트를 감춘다(카메라 미리보기 탭). */
    isFavorite: ((CameraPhoto) -> Boolean)? = null,
    onToggleFavorite: ((CameraPhoto) -> Unit)? = null
) {
    val context = LocalContext.current

    // H7-B: 공유 토스트/Chooser 라벨 i18n
    val shareStrings = ShareStrings(
        chooserTitle = stringResource(R.string.share_chooser_title),
        noImageData = stringResource(R.string.share_no_image_data),
        failed = stringResource(R.string.share_failed)
    )

    val showPhotoInfoSheet = remember { mutableStateOf(false) }
    val modalBottomSheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()

    val photos by viewModel?.photos?.collectAsStateWithLifecycle() ?: remember(localPhotos) {
        mutableStateOf(localPhotos ?: listOf(photo))
    }
    // 썸네일 캐시는 스냅샷 맵이라 통째로 구독하지 않고 그대로 읽는다(PhotoImageManager 주석 참조).
    // viewModel 이 없는 프리뷰·로컬 경로에서는 넘겨받은 한 장짜리 맵을 쓴다.
    val thumbnailCache: Map<String, ByteArray> = viewModel?.thumbnailCache
        ?: remember(thumbnailData) {
            thumbnailData?.let { mapOf(photo.path to it) } ?: emptyMap()
        }

    val currentPhotoIndex = if (viewModel != null || localPhotos != null) {
        remember(photo.path, photos) {
            photos.indexOfFirst { it.path == photo.path }.takeIf { it >= 0 } ?: 0
        }
    } else {
        0
    }

    val fullImageCache by viewModel?.fullImageCache?.collectAsStateWithLifecycle() ?: remember {
        mutableStateOf(
            fullImageData?.let { mapOf(photo.path to it) } ?: emptyMap()
        )
    }

    val pagerState = rememberPagerState(
        initialPage = currentPhotoIndex,
        pageCount = { photos.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        val newPhoto = photos.getOrNull(pagerState.currentPage)
        if (newPhoto != null && newPhoto.path != photo.path) {
            Log.d(
                "FullScreenPhotoViewer",
                "Pager 페이지 변경 성공: ${photo.name} → ${newPhoto.name} (페이지: ${pagerState.currentPage})"
            )
            onPhotoChanged(newPhoto)
            viewModel?.onPhotoIndexReached(pagerState.currentPage)
        } else {
            Log.d(
                "FullScreenPhotoViewer",
                "Pager 현재 페이지: ${pagerState.currentPage}, 총 ${photos.size}장"
            )
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (localPhotos != null) {
            Log.d("ImageViewer", "로컬 사진이므로 다운로드 건너뛰기")
            return@LaunchedEffect
        }

        val currentPhoto = photos.getOrNull(pagerState.currentPage)
        if (currentPhoto != null) {
            val hasFullImage = fullImageCache.containsKey(currentPhoto.path)
            val isDownloading = viewModel?.isDownloadingFullImage(currentPhoto.path) ?: false

            if (!hasFullImage && !isDownloading) {
                Log.d("ImageViewer", "현재 사진 고화질 다운로드: ${currentPhoto.name}")
                viewModel?.downloadPhoto(currentPhoto)
            }
        }
    }

    val currentPhotoForSheet = photos.getOrNull(pagerState.currentPage) ?: photo

    @OptIn(ExperimentalMaterial3Api::class)
    if (showPhotoInfoSheet.value) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = {
                showPhotoInfoSheet.value = false
                scope.launch { modalBottomSheetState.hide() }
            },
            sheetState = modalBottomSheetState
        ) {
            PhotoInfoBottomSheetContent(
                photo = currentPhotoForSheet,
                viewModel = viewModel,
                onDismiss = {
                    showPhotoInfoSheet.value = false
                    scope.launch { modalBottomSheetState.hide() }
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pagePhoto = photos.getOrNull(pageIndex)
            if (pagePhoto != null) {
                PhotoPagerImage(
                    fullImageData = fullImageCache[pagePhoto.path],
                    thumbnailData = thumbnailCache[pagePhoto.path],
                    photo = pagePhoto,
                    onDismiss = onDismiss,
                    isLocalPhoto = localPhotos != null
                )
            }
        }

        FullScreenTopBar(
            photo = if (viewModel != null) (photos.getOrNull(pagerState.currentPage)
                ?: photo) else photo,
            onClose = onDismiss,
            onInfoClick = {
                val currentPhoto =
                    if (viewModel != null) (photos.getOrNull(pagerState.currentPage)
                        ?: photo) else photo
                Log.d("FullScreenPhotoViewer", "정보 버튼 클릭됨: ${currentPhoto.name}")
                try {
                    showPhotoInfoSheet.value = true
                    scope.launch {
                        modalBottomSheetState.show()
                    }
                    Log.d("FullScreenPhotoViewer", "PhotoInfo 바텀시트 호출 성공")
                } catch (e: Exception) {
                    Log.e("FullScreenPhotoViewer", "PhotoInfoDialog 바텀시트 호출 실패", e)
                }
            },
            onDownloadClick = if (hideDownloadButton) null else onDownload,
            onShareClick = {
                val currentPhoto =
                    if (viewModel != null) (photos.getOrNull(pagerState.currentPage)
                        ?: photo) else photo

                val currentFullImageData = if (viewModel != null) {
                    fullImageCache[currentPhoto.path]
                } else {
                    fullImageData
                }

                val currentThumbnailData = if (viewModel != null) {
                    thumbnailCache[currentPhoto.path]
                } else {
                    thumbnailData
                }

                Log.d("FullScreenPhotoViewer", "공유 버튼 클릭: ${currentPhoto.name}")
                Log.d(
                    "FullScreenPhotoViewer",
                    "현재 이미지 데이터 상태: 고화질=${currentFullImageData?.size ?: "null"} bytes, 썸네일=${currentThumbnailData?.size ?: "null"} bytes"
                )

                shareCurrentPhoto(
                    scope,
                    context,
                    currentPhoto,
                    viewModel,
                    currentFullImageData,
                    currentThumbnailData,
                    shareStrings
                )
            },
            onExportClick = viewModel?.let { vm ->
                val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: photo
                // 앱 전용 저장소에 있는 사진에만 뜬다 — 이미 기기 갤러리에 있으면 내보낼 것이 없다.
                if (vm.canExportToGallery(currentPhoto.path)) {
                    { vm.exportToGallery(currentPhoto.path) }
                } else {
                    null
                }
            },
            onFilmEditClick = onFilmEdit?.let { handler ->
                val currentPhoto =
                    if (viewModel != null) (photos.getOrNull(pagerState.currentPage)
                        ?: photo) else photo
                // 에디터는 JPEG/디코딩 가능한 이미지 대상. RAW 는 ValidateImageFormatUseCase 단일 지점으로 제외.
                // own-media(API29+)는 raw 파일경로 접근이 막혀 uri 로만 접근 가능하므로 uri 존재도 허용한다.
                if ((currentPhoto.uri != null || File(currentPhoto.path).exists()) && !isRawFile(currentPhoto.path)) {
                    { handler(currentPhoto) }
                } else {
                    null
                }
            },
            onDeleteClick = onDeleteRequest?.let { handler ->
                {
                    val currentPhoto =
                        if (viewModel != null) (photos.getOrNull(pagerState.currentPage)
                            ?: photo) else photo
                    handler(currentPhoto)
                }
            },
            modifier = Modifier.align(Alignment.TopStart)
        )

        // 좋아요(♥) 플로팅 토글 — 사진 뷰 **우하단**.
        //
        // 상단 액션 줄이 아니라 떠 있는 원형 버튼이다. 컬링은 사진을 보면서 반복해서 누르는
        // 동작이라, 엄지가 닿는 자리에 큰 표적이 있어야 한다.
        //
        // 겹침 처리: 아래 썸네일 스트립이 있으면 그 위로 올린다(가리지 않는다). 인셋은
        // navigationBarsPadding 으로 잡아 제스처 내비게이션 기기에서 시스템 바와 겹치지 않는다.
        // HorizontalPager 위에 얹힌 자식이라 이 버튼 영역의 탭만 가져가고, 스와이프·줌 제스처는
        // 아래 페이저가 그대로 받는다.
        if (isFavorite != null && onToggleFavorite != null) {
            val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: photo
            val liked = isFavorite(currentPhoto)
            val hasThumbnailStrip = (viewModel != null && photos.size > 1) ||
                    (localPhotos != null && localPhotos.size > 1)
            val favoriteLabel = stringResource(
                if (liked) R.string.cd_favorite_remove else R.string.cd_favorite_add
            )

            FloatingActionButton(
                onClick = { onToggleFavorite(currentPhoto) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = Spacing.base,
                        // 썸네일 스트립(80dp 항목 + 패딩) 위로 띄운다.
                        bottom = if (hasThumbnailStrip) ThumbnailStripClearance else Spacing.lg
                    )
                    .semantics { contentDescription = favoriteLabel },
                containerColor = Surface0.copy(alpha = 0.8f),
                // 채움은 앱 액센트(앰버). 빨강을 새로 들이지 않는다 — 이 앱에서 앰버가 이미
                // "활성/선택"을 뜻하고, 팔레트에 없는 색을 늘리지 않는다.
                contentColor = if (liked) Accent else TextPrimaryV2
            ) {
                Icon(
                    imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null
                )
            }
        }

        if ((viewModel != null && photos.size > 1) ||
            (localPhotos != null && localPhotos.size > 1)
        ) {
            if (viewModel != null) {
                FullScreenBottomThumbnails(
                    photos = photos,
                    currentPhotoIndex = pagerState.currentPage,
                    thumbnailCache = thumbnailCache,
                    viewModel = viewModel,
                    onPhotoSelected = { selectedPhoto ->
                        val newIndex = photos.indexOfFirst { it.path == selectedPhoto.path }
                        if (newIndex >= 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(newIndex)
                            }
                            onPhotoChanged(selectedPhoto)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } else {
                LocalBottomThumbnailStripWrapper(
                    photos = photos,
                    currentPhotoIndex = pagerState.currentPage,
                    onPhotoSelected = { selectedPhoto ->
                        val newIndex = photos.indexOfFirst { it.path == selectedPhoto.path }
                        if (newIndex >= 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(newIndex)
                            }
                            onPhotoChanged(selectedPhoto)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    LaunchedEffect(modalBottomSheetState.currentValue) {
        if (!modalBottomSheetState.isVisible) {
            showPhotoInfoSheet.value = false
        }
    }
}
