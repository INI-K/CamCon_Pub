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
import androidx.core.content.FileProvider
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 현재 보여지는 사진을 공유
 */
private fun shareCurrentPhoto(
    scope: CoroutineScope,
    context: android.content.Context,
    photo: CameraPhoto,
    viewModel: PhotoPreviewViewModel?,
    fullImageData: ByteArray? = null,
    thumbnailData: ByteArray? = null
) {
    scope.launch(Dispatchers.IO) {
        try {
            // 1. 로컬 파일인 경우
            val isLocalFile = java.io.File(photo.path).exists()

            if (isLocalFile) {
                Log.d("PhotoShare", "로컬 파일 직접 공유: ${photo.path}")

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
                        val chooser = Intent.createChooser(shareIntent, "사진 공유")
                        context.startActivity(chooser)

                        Log.d("PhotoShare", "로컬 파일 공유 시작: ${file.name} (${file.length()} bytes)")
                    } catch (e: Exception) {
                        Log.e("PhotoShare", "로컬 파일 공유 인텐트 실행 실패", e)
                        Toast.makeText(context, "사진 공유에 실패했습니다.", Toast.LENGTH_SHORT).show()
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
                        val chooser = Intent.createChooser(shareIntent, "사진 공유")
                        context.startActivity(chooser)

                        Log.d("PhotoShare", "서버 사진 공유 시작: ${tempFile.name} (${imageData.size} bytes)")
                    } catch (e: Exception) {
                        Log.e("PhotoShare", "공유 인텐트 실행 실패", e)
                        Toast.makeText(context, "사진 공유에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "공유할 이미지 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                    Log.w("PhotoShare", "공유할 이미지 데이터 없음: viewModel=${viewModel != null}, fullImageData=${fullImageData?.let { "${it.size} bytes" } ?: "null"}, thumbnailData=${thumbnailData?.let { "${it.size} bytes" } ?: "null"}")
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoShare", "사진 공유 준비 중 오류", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "사진 공유에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * 전체화면 사진 뷰어 — HorizontalPager 기반 스와이프 네비게이션
 */
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
    localPhotos: List<CameraPhoto>? = null
) {
    val context = LocalContext.current

    val showPhotoInfoSheet = remember { mutableStateOf(false) }
    val modalBottomSheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()

    val photos by viewModel?.photos?.collectAsStateWithLifecycle() ?: remember(localPhotos) {
        mutableStateOf(localPhotos ?: listOf(photo))
    }
    val thumbnailCache by viewModel?.thumbnailCache?.collectAsStateWithLifecycle() ?: remember(thumbnailData) {
        mutableStateOf(thumbnailData?.let { mapOf(photo.path to it) } ?: emptyMap())
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
            .background(Background)
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
                    currentThumbnailData
                )
            },
            modifier = Modifier.align(Alignment.TopStart)
        )

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
