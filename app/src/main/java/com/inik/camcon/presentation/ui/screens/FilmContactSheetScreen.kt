package com.inik.camcon.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inik.camcon.R
import com.inik.camcon.domain.model.FilmLut
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.Body
import com.inik.camcon.presentation.theme.DisplayNum
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.MonoMicro
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.OnAccent
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.theme.TouchTarget
import com.inik.camcon.presentation.ui.components.v2.FilterChipV2
import com.inik.camcon.presentation.ui.components.v2.SkeletonLoader
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.viewmodel.FilmEditorViewModel

/**
 * 컨택트 시트 화면(설계 §3.2). 내 사진 + 각 필름 룩 썸네일을 2열 그리드로 보여준다.
 *
 * 상태 호이스팅: 모든 상태는 [FilmEditorViewModel] 의 StateFlow. 이 Composable 은 표시 + 콜백만.
 * 썸네일 비트맵은 생성기 캐시 소유 → 표시만(회수 금지).
 *
 * @param onLutClick 셀 탭 콜백. 일반 모드=편집 화면 이동, select-only 모드=선택 후 종료(호스트가 결정).
 * @param onPickImage 대상사진 변경(GetContent 피커) 트리거.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun FilmContactSheetScreen(
    viewModel: FilmEditorViewModel,
    onBackClick: () -> Unit,
    onLutClick: (String) -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableLuts by viewModel.availableLuts.collectAsStateWithLifecycle()
    val visibleLuts by viewModel.visibleLuts.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedLutId by viewModel.selectedLutId.collectAsStateWithLifecycle()
    val lockedLutIds by viewModel.lockedLutIds.collectAsStateWithLifecycle()
    val sourcePath by viewModel.sourcePath.collectAsStateWithLifecycle()
    val sourceId by viewModel.sourceId.collectAsStateWithLifecycle()
    val previewSize by viewModel.previewSize.collectAsStateWithLifecycle()

    var searchVisible by remember { mutableStateOf(false) }

    val categories = remember(availableLuts) {
        availableLuts.map { it.category }.distinct().sorted()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Text(
                        text = stringResource(R.string.fs_contact_sheet_title),
                        style = HeadingL
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    // 카탈로그 규모 카운터는 TargetPhotoBar 의 Hero 슬롯으로 이관했다(11sp 압축 해소).
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(R.string.fs_search_toggle)
                        )
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
        containerColor = Surface0,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface0)
                .padding(paddingValues)
        ) {
            // 헤더 묶음(검색·대상사진·카테고리)을 Surface1 tier 로 올린다. 이렇게 해야 아래 헤어라인
            // 1줄이 실제 tier 경계(Surface1 → Surface0)가 되고, 이전의 장식용 헤어라인 2줄 적층이 사라진다.
            SurfaceV2(
                tier = 1,
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (searchVisible) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearch,
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.fs_search_hint)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.base, vertical = Spacing.sm)
                        )
                    }

                    TargetPhotoBar(
                        lutCount = availableLuts.size,
                        sourcePath = sourcePath,
                        previewSize = previewSize,
                        onPickImage = onPickImage
                    )

                    CategoryChipRow(
                        categories = categories,
                        selected = categoryFilter,
                        onSelect = viewModel::setCategory
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StrokeWidth.hairline)
                    .background(DividerLine)
            )

            if (sourceId == null) {
                if (sourcePath == null) {
                    EmptySourcePrompt(onPickImage = onPickImage)
                } else {
                    // 사진 선택 직후 디코딩 중: thumbSource 준비되어 sourceId 가 set 되면 그리드로 전환.
                    // (그리드를 sourceId 로 게이팅해야 셀의 requestThumbnail 이 thumbSource 준비 후 호출된다.)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            } else {
                // 사진 그리드 = Standard 밀도(14dp). 셀 간 리듬은 12dp(DESIGN_SYSTEM_V2 §8.3).
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(Spacing.base),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visibleLuts, key = { lut -> lut.id }) { lut ->
                        ContactSheetCell(
                            lut = lut,
                            thumbnail = thumbnails[lut.id],
                            isFavorite = lut.id in favorites,
                            isSelected = lut.id == selectedLutId,
                            isLocked = lut.id in lockedLutIds,
                            onClick = { onLutClick(lut.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(lut.id) },
                            onEnter = { viewModel.requestThumbnail(lut.id) },
                            onLeave = { viewModel.cancelThumbnail(lut.id) }
                        )
                    }
                }
            }
        }
    }
}

/** 계기 단위 라벨(비지역화). ISO/EV 처럼 고정 표기이며, MicroLabel 은 대문자 라틴 전제라 번역하지 않는다. */
private const val LOOKS_UNIT = "LOOKS"

@Composable
private fun TargetPhotoBar(
    lutCount: Int,
    sourcePath: String?,
    previewSize: Pair<Int, Int>?,
    onPickImage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.sm),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Hero — 카탈로그 규모 카운터(DisplayNum 34sp tnum). 이 화면 최대 타이포 슬롯이며,
            // 단위 "LOOKS" 는 MicroLabel 로 강등해 크기 위계를 3단계로 벌린다.
            if (lutCount > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = lutCount.toString(),
                        style = DisplayNum,
                        color = TextPrimaryV2
                    )
                    Text(
                        text = LOOKS_UNIT,
                        style = MicroLabel,
                        color = TextTertiary,
                        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs)
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
            Text(
                text = stringResource(R.string.fs_target_label),
                style = MicroLabel,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                text = sourcePath?.substringAfterLast('/')
                    ?: stringResource(R.string.fs_target_none),
                style = MonoNumeric,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = TextPrimaryV2
            )
            previewSize?.let { (w, h) ->
                Text(
                    text = "$w × $h",
                    style = MonoMicro,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        TextButton(onClick = onPickImage) {
            Text(
                text = stringResource(R.string.fs_target_change),
                style = MicroLabel,
                color = Accent
            )
        }
    }
}

@Composable
private fun CategoryChipRow(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    // 하단 헤어라인은 호출부(헤더 블록 바깥)가 tier 경계로 1줄만 그린다 — 여기서 중복 적층하지 않는다.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = Spacing.base, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChipV2(
            text = stringResource(R.string.fs_category_all),
            selected = selected == FilmEditorViewModel.CATEGORY_ALL,
            onClick = { onSelect(FilmEditorViewModel.CATEGORY_ALL) }
        )
        FilterChipV2(
            text = stringResource(R.string.fs_category_favorites),
            selected = selected == FilmEditorViewModel.CATEGORY_FAVORITES,
            onClick = { onSelect(FilmEditorViewModel.CATEGORY_FAVORITES) },
            leadingIcon = Icons.Default.Favorite
        )
        categories.forEach { category ->
            FilterChipV2(
                text = category,
                selected = selected == category,
                onClick = { onSelect(category) }
            )
        }
    }
}

@Composable
private fun ContactSheetCell(
    lut: FilmLut,
    thumbnail: android.graphics.Bitmap?,
    isFavorite: Boolean,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEnter: () -> Unit,
    onLeave: () -> Unit
) {
    // 셀 진입/이탈에 맞춰 썸네일 생성 요청/취소(스크롤 협조).
    DisposableEffect(lut.id, thumbnail != null) {
        if (thumbnail == null) onEnter()
        onDispose { onLeave() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Radius.sm))
                .border(
                    border = if (isSelected) {
                        androidx.compose.foundation.BorderStroke(StrokeWidth.thick, Accent)
                    } else {
                        androidx.compose.foundation.BorderStroke(StrokeWidth.hairline, DividerLine)
                    },
                    shape = RoundedCornerShape(Radius.sm)
                )
        ) {
            val bmp = thumbnail
            if (bmp != null && !bmp.isRecycled) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = lut.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SkeletonLoader(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(Radius.sm),
                    announceLoading = false
                )
            }

            // 잠긴 필름은 프리뷰를 가라앉혀 사용 가능 항목과 한눈에 구분되게 한다(룩 자체는 보이게).
            if (isLocked && !isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Surface0.copy(alpha = 0.45f))
                )
            }

            // 히트 영역(44dp)과 시각 칩(24dp)을 분리한다 — 칩 크기로 터치 타깃을 깎지 않는다.
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(TouchTarget.min)
            ) {
                Box(
                    modifier = Modifier
                        .size(IconSize.lg)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Surface0.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.fs_favorite_remove else R.string.fs_favorite_add
                        ),
                        tint = if (isFavorite) Accent else TextTertiary,
                        modifier = Modifier.size(IconSize.sm)
                    )
                }
            }

            // 좌상단 슬롯: 선택 틱(앰버) 우선 > 잠금 배지(PRO 전용 필름). 프리뷰는 보여주고 선택만 차단.
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = OnAccent,
                        modifier = Modifier.size(12.dp)
                    )
                }
            } else if (isLocked) {
                // 사진 위에서도 확실히 보이도록 진한 칩 + 앰버 자물쇠(선택 틱과 구분되는 배색).
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Surface0.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = stringResource(R.string.fs_lut_locked_badge_cd),
                        tint = Accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // LUT 이름 = MonoMicro(라틴 대문자 관례는 데이터 자체), 선택 시 앰버.
        Text(
            text = lut.name,
            style = MonoMicro,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) Accent else TextTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs, start = Spacing.xs, end = Spacing.xs)
        )
    }
}

@Composable
private fun EmptySourcePrompt(onPickImage: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.fs_pick_prompt),
                style = Body,
                textAlign = TextAlign.Center,
                color = TextSecondaryV2,
                modifier = Modifier.widthIn(max = 320.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            TextButton(onClick = onPickImage) {
                Text(
                    text = stringResource(R.string.fs_pick_target),
                    style = MicroLabel,
                    color = Accent
                )
            }
        }
    }
}
