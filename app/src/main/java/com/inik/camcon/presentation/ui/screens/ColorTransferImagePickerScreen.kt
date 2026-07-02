package com.inik.camcon.presentation.ui.screens

import android.graphics.ColorSpace
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.MonoMicro
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.viewmodel.ColorTransferViewModel
import java.io.File

/**
 * 색감 전송용 참조 이미지 선택 화면
 */
@Composable
fun ColorTransferImagePickerScreen(
    onBackClick: () -> Unit,
    onImageSelected: (String) -> Unit,
    viewModel: ColorTransferViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val availableImages by viewModel.availableImages.collectAsStateWithLifecycle()
    val selectedImagePath by viewModel.selectedImagePath.collectAsStateWithLifecycle()

    var selectedLocalPath by remember { mutableStateOf<String?>(null) }

    // 이미지 선택 런처
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleImageSelection(it, context) }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAvailableImages(context)
    }

    LaunchedEffect(selectedImagePath) {
        selectedImagePath?.let { path ->
            selectedLocalPath = path
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.color_transfer_select_reference)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (selectedLocalPath != null) {
                        IconButton(
                            onClick = {
                                selectedLocalPath?.let { path ->
                                    onImageSelected(path)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.color_transfer_selection_done))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface0,
                    titleContentColor = TextPrimaryV2,
                    navigationIconContentColor = TextPrimaryV2,
                    actionIconContentColor = TextPrimaryV2
                )
            )
        },
        containerColor = Surface0
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface0)
                .padding(paddingValues)
        ) {
            // 설명 텍스트 — flat SurfaceV2(tier=1) + 헤어라인.
            SurfaceV2(
                tier = 1,
                border = true,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.base)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.base)
                ) {
                    Text(
                        text = stringResource(R.string.color_transfer_select_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryV2
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.color_transfer_select_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }

            // 새 이미지 추가 버튼 — V2 SecondaryButton.
            SecondaryButton(
                text = stringResource(R.string.color_transfer_add_from_gallery),
                onClick = { imagePickerLauncher.launch("image/*") },
                leadingIcon = Icons.Default.Add,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.base)
            )

            Spacer(modifier = Modifier.height(Spacing.base))

            // 에러 메시지 — flat SurfaceV2 + 헤어라인.
            errorMessage?.let { message ->
                SurfaceV2(
                    tier = 1,
                    border = true,
                    shape = RoundedCornerShape(Radius.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.base)
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.base),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            // 이미지 목록
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Accent)
                }
            } else if (availableImages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(Spacing.base))
                        Text(
                            text = stringResource(R.string.color_transfer_no_images),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.color_transfer_add_from_gallery_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    items(
                        items = availableImages,
                        key = { imagePath -> imagePath }
                    ) { imagePath ->
                        ImageItem(
                            imagePath = imagePath,
                            isSelected = selectedLocalPath == imagePath,
                            onImageClick = { selectedLocalPath = imagePath }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageItem(
    imagePath: String,
    isSelected: Boolean,
    onImageClick: () -> Unit
) {
    val file = File(imagePath)
    val fileName = file.name

    // 콘택트시트 언어 — flat SurfaceV2 + 선택 시 앰버 프레임(2dp)/평시 헤어라인.
    SurfaceV2(
        tier = 1,
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .border(
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(StrokeWidth.thick, Accent)
                } else {
                    androidx.compose.foundation.BorderStroke(StrokeWidth.hairline, DividerLine)
                },
                shape = RoundedCornerShape(Radius.md)
            )
            .clickable { onImageClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 이미지 미리보기 — 2dp 코너 + 헤어라인(갤러리 타일 언어).
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .border(
                        androidx.compose.foundation.BorderStroke(StrokeWidth.hairline, DividerLine),
                        RoundedCornerShape(Radius.sm)
                    )
                    .background(Surface0)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imagePath)
                            .crossfade(true)
                            .memoryCacheKey(imagePath)
                            .apply {
                                // sRGB 색공간 설정
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    colorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                                }
                            }
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // 선택 표시 — 앰버 틱.
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Surface0.copy(alpha = 0.4f),
                                RoundedCornerShape(Radius.sm)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.cd_selected),
                            tint = Accent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.base))

            // 파일 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = fileName,
                    style = MonoMicro,
                    color = if (isSelected) Accent else TextPrimaryV2
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                Text(
                    text = stringResource(R.string.color_transfer_file_size, file.length() / 1024),
                    style = MonoMicro,
                    color = TextTertiary
                )
            }
        }
    }
}