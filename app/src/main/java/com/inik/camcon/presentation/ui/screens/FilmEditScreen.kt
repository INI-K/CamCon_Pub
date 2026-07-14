package com.inik.camcon.presentation.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inik.camcon.R
import com.inik.camcon.domain.model.FilmAdjustments
import com.inik.camcon.domain.model.FilmLut
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.MonoMicro
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.StrokeWidth
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface1
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.ui.screens.components.FilmEditPreview
import com.inik.camcon.presentation.viewmodel.FilmEditorViewModel
import kotlin.math.roundToInt

/**
 * 편집 화면(설계 §3.3). 상단바(뒤로/필름명/♥/리셋/내보내기) + 대형 프리뷰 + 강도/조정 8종 슬라이더 +
 * 하단 필름 전환 스트립으로 구성한다.
 *
 * 상태 호이스팅: 모든 상태는 [FilmEditorViewModel] 의 StateFlow. 이 Composable 은 표시 + 콜백만.
 * 프리뷰는 디바운스된 [FilmEditorViewModel.previewEdit] 를, 슬라이더 표시는 즉시값 [FilmEditorViewModel.filmEdit]
 * 를 소비한다(슬라이더는 끊김 없이, GPU 는 디바운스).
 *
 * 비트맵 소유: [FilmEditorViewModel.previewBitmap] = VM 소유, [FilmEditorViewModel.lookupBitmap] = 캐시 소유
 * → 본 화면에서 회수 금지(표시만).
 *
 * 선택 LUT 은 컨택트 시트 셀 탭 시 [FilmEditorViewModel.selectLut] 로 이미 설정되며, 본 화면은 공유 VM 의
 * [FilmEditorViewModel.selectedLutId]/[FilmEditorViewModel.filmEdit] 등에서 읽는다(라우트 인자로 받지 않는다).
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun FilmEditScreen(
    viewModel: FilmEditorViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val availableLuts by viewModel.availableLuts.collectAsStateWithLifecycle()
    val visibleLuts by viewModel.visibleLuts.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selectedLutId by viewModel.selectedLutId.collectAsStateWithLifecycle()
    val lockedLutIds by viewModel.lockedLutIds.collectAsStateWithLifecycle()
    val filmEdit by viewModel.filmEdit.collectAsStateWithLifecycle()
    val previewBitmap by viewModel.previewBitmap.collectAsStateWithLifecycle()
    val renderedPreview by viewModel.renderedPreview.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()


    // 내보내기 결과. 에러 채널 기준(설계 결정 #1):
    //  - 성공(안내성·비오류) → V2 ToastV2 오버레이(컴포지션 수명 내).
    //  - 실패(에러) → 플랫폼 Toast 유지(컴포지션 수명과 무관해야 하는 오류 통지).
    val okText = stringResource(R.string.fs_export_success)
    val failText = stringResource(R.string.fs_export_failed)
    var advisoryMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        when (message) {
            FilmEditorViewModel.MESSAGE_OK -> {
                advisoryMessage = okText
                viewModel.clearMessage()
            }

            FilmEditorViewModel.MESSAGE_FAIL -> {
                Toast.makeText(context, failText, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    val filmName = availableLuts.firstOrNull { it.id == selectedLutId }?.name
        ?: stringResource(R.string.fs_lut_none)
    val isFavorite = selectedLutId in favorites

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = {
                        Text(
                            text = filmName,
                            style = HeadingL,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        IconButton(
                            onClick = { if (selectedLutId.isNotEmpty()) viewModel.toggleFavorite(selectedLutId) },
                            enabled = selectedLutId.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (isFavorite) R.string.fs_favorite_remove else R.string.fs_favorite_add
                                ),
                                tint = if (isFavorite) Accent else TextPrimaryV2
                            )
                        }
                        IconButton(onClick = viewModel::resetAdjustments) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.fs_reset)
                            )
                        }
                        IconButton(
                            onClick = viewModel::export,
                            enabled = !isExporting && previewBitmap != null
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Accent
                                )
                            } else {
                                Icon(
                                    Icons.Default.FileUpload,
                                    contentDescription = stringResource(R.string.fs_export)
                                )
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
                // 앱바-컨텐츠 경계 헤어라인(순흑끼리 경계 소실 방지, CINE 계기판 언어).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StrokeWidth.hairline)
                        .background(DividerLine)
                )
            }
        },
        containerColor = Surface0,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface0)
                .padding(paddingValues)
        ) {
            // 프리뷰(고정 영역). 꾹 누르면 원본. 강도는 표시단 알파 합성으로 실시간 반영.
            FilmEditPreview(
                rendered = renderedPreview,
                original = previewBitmap,
                intensity = filmEdit.intensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(Color.Black)
            )

            Text(
                text = stringResource(R.string.fs_original_compare_hint),
                style = MicroLabel,
                color = TextTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.base, vertical = Spacing.xs)
            )

            // 컨트롤 패널(flat Surface1 + 헤어라인). 슬라이더 스택은 스크롤.
            SurfaceV2(
                tier = 1,
                border = true,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.base, vertical = Spacing.sm)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.base)
                ) {
                    SectionHeader(stringResource(R.string.fs_section_film))
                    IntensitySlider(
                        intensity = filmEdit.intensity,
                        onChange = viewModel::setIntensity
                    )

                    Spacer(Modifier.height(Spacing.md))
                    SectionHeader(stringResource(R.string.fs_section_adjust))
                    AdjustmentSliders(
                        adjustments = filmEdit.adjustments,
                        onChange = viewModel::setAdjustment
                    )
                    Spacer(Modifier.height(Spacing.md))
                }
            }

            // 하단 필름 전환 스트립.
            FilmSwitchStrip(
                luts = visibleLuts,
                thumbnails = thumbnails,
                selectedLutId = selectedLutId,
                lockedLutIds = lockedLutIds,
                onSelect = viewModel::selectLutGated,
                onEnter = viewModel::requestThumbnail,
                onLeave = viewModel::cancelThumbnail
            )
        }

            // 안내성(내보내기 성공) 토스트 → ToastV2 오버레이(상단, 3초 자동 소멸).
            AdvisoryToastHost(
                message = advisoryMessage,
                paddingValues = paddingValues,
                onDismiss = { advisoryMessage = null }
            )
        }
    }
}

/**
 * 안내성(비오류) 토스트 오버레이 — SettingsActivity 의 ToastV2 패턴을 따른다(설계 결정 #1 에러 채널 기준).
 * [message] 가 non-null 이면 상단에서 슬라이드 인 후 3초 뒤 [onDismiss] 로 소멸한다.
 */
@Composable
private fun AdvisoryToastHost(
    message: String?,
    paddingValues: PaddingValues,
    onDismiss: () -> Unit
) {
    val visible = message != null
    if (message != null) {
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(3000L)
            onDismiss()
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -80 }) +
            androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(260)
            ),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -80 }) +
            androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(260)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(start = Spacing.base, end = Spacing.base, top = Spacing.sm)
        ) {
            com.inik.camcon.presentation.ui.components.v2.ToastV2(
                message = message.orEmpty(),
                kind = com.inik.camcon.presentation.ui.components.v2.StatusKind.Connected,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    // 계측기 섹션 라벨(MicroLabel). CJK 가능성으로 .uppercase() 호출하지 않음.
    Text(
        text = text,
        style = MicroLabel,
        color = TextTertiary,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm)
    )
}

@Composable
private fun IntensitySlider(intensity: Float, onChange: (Float) -> Unit) {
    LabeledSlider(
        label = stringResource(R.string.fs_intensity_title),
        value = intensity,
        valueRange = 0f..1f,
        valueText = "${(intensity * 100f).roundToInt()}%",
        onChange = onChange
    )
}

@Composable
private fun AdjustmentSliders(
    adjustments: FilmAdjustments,
    onChange: (
        exposure: Float?,
        temperature: Float?,
        contrast: Float?,
        shadows: Float?,
        highlights: Float?,
        saturation: Float?,
        grain: Float?,
        chromaticAberration: Float?
    ) -> Unit
) {
    // 노출(EV, 양방향)
    LabeledSlider(
        label = stringResource(R.string.fs_adj_exposure),
        value = adjustments.exposure,
        valueRange = FilmAdjustments.EXPOSURE_MIN..FilmAdjustments.EXPOSURE_MAX,
        valueText = signed1(adjustments.exposure),
        onChange = { onChange(it, null, null, null, null, null, null, null) }
    )
    // 색온도(±100, 양방향)
    LabeledSlider(
        label = stringResource(R.string.fs_adj_temperature),
        value = adjustments.temperature,
        valueRange = FilmAdjustments.TEMPERATURE_MIN..FilmAdjustments.TEMPERATURE_MAX,
        valueText = signedInt(adjustments.temperature),
        onChange = { onChange(null, it, null, null, null, null, null, null) }
    )
    // 대비(±100, 양방향)
    LabeledSlider(
        label = stringResource(R.string.fs_adj_contrast),
        value = adjustments.contrast,
        valueRange = FilmAdjustments.BIPOLAR_MIN..FilmAdjustments.BIPOLAR_MAX,
        valueText = signedInt(adjustments.contrast),
        onChange = { onChange(null, null, it, null, null, null, null, null) }
    )
    // 섀도(±100, 양방향)
    LabeledSlider(
        label = stringResource(R.string.fs_adj_shadows),
        value = adjustments.shadows,
        valueRange = FilmAdjustments.BIPOLAR_MIN..FilmAdjustments.BIPOLAR_MAX,
        valueText = signedInt(adjustments.shadows),
        onChange = { onChange(null, null, null, it, null, null, null, null) }
    )
    // 하이라이트(±100, 양방향)
    LabeledSlider(
        label = stringResource(R.string.fs_adj_highlights),
        value = adjustments.highlights,
        valueRange = FilmAdjustments.BIPOLAR_MIN..FilmAdjustments.BIPOLAR_MAX,
        valueText = signedInt(adjustments.highlights),
        onChange = { onChange(null, null, null, null, it, null, null, null) }
    )
    // 채도(±100, 양방향)
    LabeledSlider(
        label = stringResource(R.string.fs_adj_saturation),
        value = adjustments.saturation,
        valueRange = FilmAdjustments.BIPOLAR_MIN..FilmAdjustments.BIPOLAR_MAX,
        valueText = signedInt(adjustments.saturation),
        onChange = { onChange(null, null, null, null, null, it, null, null) }
    )
    // 그레인(0..1, 단방향)
    LabeledSlider(
        label = stringResource(R.string.fs_adj_grain),
        value = adjustments.grain,
        valueRange = FilmAdjustments.UNIPOLAR_MIN..FilmAdjustments.UNIPOLAR_MAX,
        valueText = "${(adjustments.grain * 100f).roundToInt()}%",
        onChange = { onChange(null, null, null, null, null, null, it, null) }
    )
    // 색수차(0..1, 단방향)
    LabeledSlider(
        label = stringResource(R.string.fs_adj_chromatic_aberration),
        value = adjustments.chromaticAberration,
        valueRange = FilmAdjustments.UNIPOLAR_MIN..FilmAdjustments.UNIPOLAR_MAX,
        valueText = "${(adjustments.chromaticAberration * 100f).roundToInt()}%",
        onChange = { onChange(null, null, null, null, null, null, null, it) }
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimaryV2
            )
            // 수치 = MonoReadout 계열(MonoMicro), 앰버.
            Text(
                text = valueText,
                style = MonoMicro,
                color = Accent
            )
        }
        val sliderColors = SliderDefaults.colors(
            thumbColor = Accent,
            activeTrackColor = Accent,
            inactiveTrackColor = DividerLine
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onChange,
            valueRange = valueRange,
            colors = sliderColors,
            // 비활성 트랙 끝 stop indicator(앰버 점) 제거 — 썸으로 오인 방지.
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = sliderColors,
                    drawStopIndicator = null
                )
            }
        )
    }
}

@Composable
private fun FilmSwitchStrip(
    luts: List<FilmLut>,
    thumbnails: Map<String, Bitmap>,
    selectedLutId: String,
    lockedLutIds: Set<String>,
    onSelect: (String) -> Unit,
    onEnter: (String) -> Unit,
    onLeave: (String) -> Unit
) {
    if (luts.isEmpty()) return
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StrokeWidth.hairline)
                .background(DividerLine)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface0)
                .padding(vertical = Spacing.sm),
            contentPadding = PaddingValues(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            items(luts, key = { it.id }) { lut ->
                FilmStripCell(
                    lut = lut,
                    thumbnail = thumbnails[lut.id],
                    isSelected = lut.id == selectedLutId,
                    isLocked = lut.id in lockedLutIds,
                    onClick = { onSelect(lut.id) },
                    onEnter = { onEnter(lut.id) },
                    onLeave = { onLeave(lut.id) }
                )
            }
        }
    }
}

@Composable
private fun FilmStripCell(
    lut: FilmLut,
    thumbnail: Bitmap?,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    onEnter: () -> Unit,
    onLeave: () -> Unit
) {
    DisposableEffect(lut.id, thumbnail != null) {
        if (thumbnail == null) onEnter()
        onDispose { onLeave() }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(Surface1)
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
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = lut.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 잠금 배지(PRO 전용 필름) — 선택되지 않았을 때만 좌상단에 미니 자물쇠.
            if (isLocked && !isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .size(16.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Surface0.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = stringResource(R.string.fs_lut_locked_badge_cd),
                        tint = Accent,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Text(
            text = lut.name,
            style = MonoMicro,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) Accent else TextTertiary,
            modifier = Modifier
                .width(64.dp)
                .padding(top = Spacing.xs)
        )
    }
}

private fun signedInt(v: Float): String {
    val i = v.roundToInt()
    return if (i > 0) "+$i" else i.toString()
}

private fun signed1(v: Float): String {
    val s = String.format("%.1f", v)
    return if (v > 0f) "+$s" else s
}
