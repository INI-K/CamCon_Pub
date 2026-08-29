package com.inik.camcon.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.inik.camcon.R
import com.inik.camcon.domain.model.ThemeMode
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.AccentStrong
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.DisplayNum
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.Micro
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.theme.TouchTarget
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.util.openEmail
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 네이티브 라이브러리 라이선스 정보
 *
 * @param version 버전을 특정할 수 있을 때만 채운다. 빈 값이면 카드에서 버전 줄을 생략한다
 *                (필름 프리셋처럼 배포본에 버전 표기가 없는 자산이 있다).
 * @param modified CamCon이 소스를 수정해 빌드한 라이브러리인지 여부.
 *                 true면 수정 사실 배지를 표시하고 LGPL 소스 제공 오퍼 카드의 대상이 된다.
 */
data class NativeLicense(
    val name: String,
    val version: String = "",
    val license: String,
    val copyright: String,
    val url: String,
    val modified: Boolean = false
)

@AndroidEntryPoint
class OpenSourceLicensesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()

            CamConTheme() {
                OpenSourceLicensesScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun OpenSourceLicensesScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // 네이티브 라이브러리 라이선스 (Gradle 의존성이 아닌 것들)
    val nativeLicenses = listOf(
        // libgphoto2/libgphoto2_port는 업스트림 그대로가 아니라 CamCon 패치를 얹어 빌드한 바이너리다.
        // (camlibs/ptp2, libgphoto2/gphoto2-filesys.c, libgphoto2_port/libusb1) — LGPL 고지 대상.
        // ⚠️ 2.5.34 로 적지 말 것. build.sh 가 KEEP_VERSION_PATHS=1 로 동작해
        //    configure 버전 라벨(2.5.33.1)은 그대로 두고 camlibs/ptp2 등 일부 소스만
        //    2_5_34-release 태그본으로 교체한다(앱이 libgphoto2/2.5.33.1 경로를 하드코딩).
        //    실제 배포 .so 가 자기 버전을 2.5.33.1 로 보고하므로 여기도 그 값이어야 하고,
        //    2.5.34 태그본 + CamCon 패치는 modified=true 가 고지한다.
        //    LGPL 대응 소스 요청 시 넘길 것은 2.5.34 트리가 아니라 이 하이브리드 빌드트리다.
        NativeLicense(
            name = "libgphoto2",
            version = "2.5.33.1",
            license = "LGPL-2.1-or-later",
            copyright = "Copyright (c) 2000-2024 The gphoto2 Team",
            url = "https://github.com/gphoto/libgphoto2",
            modified = true
        ),
        NativeLicense(
            name = "libgphoto2_port",
            version = "0.12.2",
            license = "LGPL-2.1-or-later",
            copyright = "Copyright (c) 2000-2024 The gphoto2 Team",
            url = "https://github.com/gphoto/libgphoto2",
            modified = true
        ),
        NativeLicense(
            name = "libusb",
            version = "1.0.27",
            license = "LGPL-2.1-or-later",
            copyright = "Copyright (c) 2001 Johannes Erdfelt",
            url = "https://libusb.info"
        ),
        NativeLicense(
            name = "libexif",
            version = "0.6.24",
            license = "LGPL-2.1-or-later",
            copyright = "Copyright (c) 2001-2021 Lutz Mueller and others",
            url = "https://libexif.github.io"
        ),
        NativeLicense(
            name = "GNU Libtool (libltdl)",
            version = "2.4.7",
            license = "LGPL-2.1-or-later",
            copyright = "Copyright (c) 1998-2019 Free Software Foundation, Inc.",
            url = "https://www.gnu.org/software/libtool"
        )
    )

    // 번들 폰트 — 버전·저작권은 pretendard_regular.otf 의 name 테이블 실측값이다(ID 5·ID 0).
    // OFL 1.1 제2조는 사본마다 저작권 고지와 라이선스를 함께 담을 것을 요구한다. 폰트 파일 안의
    // name ID 13 은 전문이 아니라 144자 안내문일 뿐이고 앱이 그 필드를 노출하지도 않으므로,
    // '쉽게 열람 가능한 메타데이터' 예외에 기대지 않고 전문을 assets 에 동봉해 직접 보여준다.
    // 동봉본 첫머리의 저작권 4건(Pretendard·Source·Inter·M PLUS)이 제2조의 고지 의무를 함께 만족시킨다.
    val fontLicense = NativeLicense(
        name = "Pretendard",
        version = "1.309",
        license = "SIL Open Font License 1.1",
        copyright = "Copyright © 2023 Kil Hyung-jin",
        url = "https://github.com/orioncactus/pretendard"
    )

    // 번들 필름 시뮬레이션 프리셋 — assets/luts 의 .cube 296개에 대한 귀속 고지다.
    // 세 항목은 배포 사슬을 그대로 옮긴 것이라 하나라도 빠뜨리면 귀속 의무가 깨진다.
    //   Film-Luts(MIT)  = HaldCLUT → .cube 변환본을 묶은 저장소. 전문 동봉 대상.
    //   G'MIC           = 프리셋을 생성·배포하는 프로젝트. 각 .cube 첫 줄의 저작권 고지 주체다.
    //   Film Emulation Presets = 원 프리셋 저작자(gmic.eu/color_presets 명시), CC BY-SA 4.0.
    // ⚠️ .cube 파일 첫 줄의 "# Created by: G'MIC" 주석은 저작권 고지 그 자체다.
    //    용량 최적화로 주석을 스트립하면 그 순간 고지 의무 위반이 되므로 제거 금지.
    val filmLutLicenses = listOf(
        NativeLicense(
            name = "Film-Luts",
            version = "2024.03",
            license = "MIT",
            copyright = "Copyright (c) 2024 Yahia",
            url = "https://github.com/YahiaAngelo/Film-Luts"
        ),
        NativeLicense(
            name = "G'MIC",
            license = "CeCILL-2.1 / CeCILL-C",
            copyright = "Copyrights (C) Since July 2008, " +
                    "David Tschumperlé - GREYC UMR CNRS 6072, Image Team",
            url = "https://gmic.eu"
        ),
        NativeLicense(
            name = "G'MIC Film Emulation Presets",
            license = "CC BY-SA 4.0",
            copyright = "Pat David, Stuart Sowerby, Juan Melara",
            url = "https://gmic.eu/color_presets/"
        )
    )

    // LGPL 전문 다이얼로그 표시 여부.
    var showLgplFullText by rememberSaveable { mutableStateOf(false) }

    // 필름 프리셋 MIT 전문 다이얼로그 표시 여부.
    var showFilmLutFullText by rememberSaveable { mutableStateOf(false) }

    // 번들 폰트 OFL 전문 다이얼로그 표시 여부.
    var showFontFullText by rememberSaveable { mutableStateOf(false) }

    if (showLgplFullText) {
        LicenseFullTextDialog(
            title = LGPL_LICENSE_TITLE,
            assetPath = LGPL_LICENSE_ASSET,
            onDismiss = { showLgplFullText = false }
        )
    }

    if (showFilmLutFullText) {
        LicenseFullTextDialog(
            title = MIT_LICENSE_TITLE,
            assetPath = FILM_LUT_MIT_LICENSE_ASSET,
            onDismiss = { showFilmLutFullText = false }
        )
    }

    if (showFontFullText) {
        LicenseFullTextDialog(
            title = OFL_LICENSE_TITLE,
            assetPath = OFL_LICENSE_ASSET,
            onDismiss = { showFontFullText = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.open_source_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 히어로 — 앱이 실제로 싣고 있는 네이티브 라이브러리 수가 이 화면의 앵커다.
            item {
                LicensesHero(nativeCount = nativeLicenses.size)
            }

            // Gradle 의존성 라이선스 (자동 생성)
            item {
                GradleLicensesCard(
                    onClick = {
                        context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                    }
                )
            }

            item {
                Text(
                    text = stringResource(R.string.v3_licenses_native_section),
                    style = Caption,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
                )
            }

            // 네이티브 라이브러리 라이선스 (수동)
            items(
                items = nativeLicenses,
                key = { license -> license.name }
            ) { license ->
                NativeLicenseItem(license = license)
            }

            // LGPL 서면 제공 오퍼 — 수정 배포한 라이브러리가 있을 때만 노출한다.
            if (nativeLicenses.any { it.modified }) {
                item {
                    LgplSourceOfferCard(
                        onContactClick = { context.openEmail(Constants.Legal.CONTACT_EMAIL) },
                        onViewFullTextClick = { showLgplFullText = true }
                    )
                }
            }

            // 번들 폰트 고지 — 네이티브 라이브러리가 아니므로 히어로 집계와 분리한 별도 섹션이다.
            item {
                Text(
                    text = stringResource(R.string.licenses_fonts_section),
                    style = Caption,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
                )
            }

            item {
                NativeLicenseItem(
                    license = fontLicense,
                    onViewFullTextClick = { showFontFullText = true }
                )
            }

            // 번들 필름 프리셋 고지 — 폰트와 같은 리소스 계열이라 네이티브 집계와 분리한다.
            item {
                Text(
                    text = stringResource(R.string.licenses_film_luts_section),
                    style = Caption,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
                )
            }

            items(
                items = filmLutLicenses,
                key = { license -> license.name }
            ) { license ->
                NativeLicenseItem(license = license)
            }

            // 출처·변경 사실 고지 + MIT 전문 — CC BY-SA 4.0 의 '변경 여부 표시' 요구를 함께 만족시킨다.
            item {
                FilmLutNoticeCard(onViewFullTextClick = { showFilmLutFullText = true })
            }

            // 상표 면책 — 필름 시뮬레이션 이름이 상표를 지시적으로 사용하는 데 대한 고지.
            item {
                TrademarkNoticeCard()
            }

            item {
                Text(
                    text = stringResource(R.string.licenses_thanks),
                    style = Micro,
                    color = TextTertiary,
                    modifier = Modifier
                        .widthIn(max = LICENSE_TEXT_MAX_WIDTH)
                        .padding(top = Spacing.lg, bottom = Spacing.xl)
                )
            }
        }
    }
}

/**
 * 히어로 — 번들 네이티브 라이브러리 수(34sp tnum) + 라벨 + 리드 문장.
 * 화면에서 단 하나의 히어로 슬롯이다.
 */
@Composable
private fun LicensesHero(nativeCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xs)
    ) {
        Text(
            text = nativeCount.toString(),
            style = DisplayNum,
            color = TextPrimaryV2
        )
        Text(
            text = stringResource(R.string.native_libraries),
            style = Caption,
            color = TextTertiary,
            modifier = Modifier.padding(top = Spacing.xs)
        )
        Text(
            text = stringResource(R.string.licenses_description),
            style = BodySmall,
            color = TextSecondaryV2,
            modifier = Modifier
                .widthIn(max = LICENSE_TEXT_MAX_WIDTH)
                .padding(top = Spacing.md)
        )
    }
}

@Composable
fun GradleLicensesCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    SurfaceV2(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        // press 시 surface tier 승격으로 눌림을 표현한다(그림자 없음).
        tier = if (pressed) 3 else 2,
        border = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TouchTarget.min)
                .padding(Spacing.base),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.gradle_dependencies),
                    style = HeadingM,
                    color = TextPrimaryV2
                )
                Text(
                    text = stringResource(R.string.gradle_dependencies_desc),
                    style = BodySmall,
                    color = TextTertiary,
                    modifier = Modifier
                        .widthIn(max = LICENSE_TEXT_MAX_WIDTH)
                        .padding(top = Spacing.xs)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (pressed) Accent else TextTertiary
            )
        }
    }
}

/**
 * @param onViewFullTextClick 전문을 앱 안에서 열람시킬 항목만 넘긴다. null 이면 링크를 그리지 않는다.
 */
@Composable
fun NativeLicenseItem(
    license: NativeLicense,
    onViewFullTextClick: (() -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val interaction = remember { MutableInteractionSource() }
    val urlPressed by interaction.collectIsPressedAsState()
    val fullTextInteraction = remember { MutableInteractionSource() }
    val fullTextPressed by fullTextInteraction.collectIsPressedAsState()

    SurfaceV2(
        modifier = Modifier.fillMaxWidth(),
        tier = 2,
        border = true
    ) {
        Column(
            modifier = Modifier.padding(Spacing.base)
        ) {
            // 이름/저작권(좌) ↔ 버전/라이선스(우) 2단 — 전폭 균질 스택을 끊는다.
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = license.name,
                        style = HeadingM,
                        color = TextPrimaryV2
                    )
                    if (license.modified) {
                        // 수정 배포 사실은 이름 바로 아래에서 눈에 띄어야 한다(LGPL 고지).
                        Text(
                            text = stringResource(R.string.licenses_modified_badge),
                            style = Micro,
                            color = AccentStrong,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                    }
                    Text(
                        text = license.copyright,
                        style = Micro,
                        color = TextTertiary,
                        modifier = Modifier
                            .widthIn(max = LICENSE_META_MAX_WIDTH)
                            .padding(top = Spacing.xs)
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = Spacing.md)
                ) {
                    if (license.version.isNotEmpty()) {
                        Text(
                            text = license.version,
                            style = MonoNumeric,
                            color = TextSecondaryV2
                        )
                    }
                    Text(
                        text = license.license,
                        style = Micro,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }

            Text(
                text = license.url,
                style = BodySmall,
                color = if (urlPressed) Accent else AccentStrong,
                modifier = Modifier
                    .padding(top = Spacing.sm)
                    .defaultMinSize(minHeight = TouchTarget.min)
                    .clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current
                    ) { uriHandler.openUri(license.url) }
                    .wrapContentHeight(Alignment.CenterVertically)
            )

            if (onViewFullTextClick != null) {
                Text(
                    text = stringResource(R.string.licenses_view_full_text),
                    style = BodySmall,
                    color = if (fullTextPressed) Accent else AccentStrong,
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .defaultMinSize(minHeight = TouchTarget.min)
                        .clickable(
                            interactionSource = fullTextInteraction,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClick = onViewFullTextClick
                        )
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }
        }
    }
}

/**
 * LGPL 서면 제공 오퍼 카드.
 *
 * 수정 배포한 LGPL 라이브러리의 대응 소스를 요청 시 3년간 제공한다는 고지 + 연락처.
 * 연락처는 개인정보처리방침·이용약관에 기재된 운영자 주소와 동일해야 한다.
 */
@Composable
private fun LgplSourceOfferCard(
    onContactClick: () -> Unit,
    onViewFullTextClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fullTextInteraction = remember { MutableInteractionSource() }
    val fullTextPressed by fullTextInteraction.collectIsPressedAsState()

    SurfaceV2(
        modifier = Modifier.fillMaxWidth(),
        tier = 2,
        border = true
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Text(
                text = stringResource(R.string.licenses_lgpl_offer_title),
                style = HeadingM,
                color = TextPrimaryV2
            )
            Text(
                text = stringResource(R.string.licenses_lgpl_offer_body),
                style = BodySmall,
                color = TextSecondaryV2,
                modifier = Modifier
                    .widthIn(max = LICENSE_TEXT_MAX_WIDTH)
                    .padding(top = Spacing.sm)
            )
            Text(
                text = Constants.Legal.CONTACT_EMAIL,
                style = BodySmall,
                color = if (pressed) Accent else AccentStrong,
                modifier = Modifier
                    .padding(top = Spacing.sm)
                    .defaultMinSize(minHeight = TouchTarget.min)
                    // 스크린 리더가 일반 텍스트가 아니라 버튼으로 읽도록 role/라벨을 준다.
                    .clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClickLabel = stringResource(R.string.licenses_contact_click_label),
                        role = Role.Button,
                        onClick = onContactClick
                    )
                    .wrapContentHeight(Alignment.CenterVertically)
            )
            Text(
                text = stringResource(R.string.licenses_view_full_text),
                style = BodySmall,
                color = if (fullTextPressed) Accent else AccentStrong,
                modifier = Modifier
                    .padding(top = Spacing.sm)
                    .defaultMinSize(minHeight = TouchTarget.min)
                    .clickable(
                        interactionSource = fullTextInteraction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = onViewFullTextClick
                    )
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }
    }
}

/**
 * 라이선스 전문 다이얼로그.
 *
 * 전문은 법적 문서라 번역하지 않고 assets 의 영어 원문을 그대로 보여준다.
 * 26KB 남짓이라 한 번에 읽어 스크롤 가능한 본문에 싣는다.
 */
@Composable
private fun LicenseFullTextDialog(
    title: String,
    assetPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // 전문을 못 읽어도 화면이 죽지 않아야 한다. 실패는 안내 문구로 대신한다.
    val fallback = stringResource(R.string.error_unknown)
    val licenseText by produceState(initialValue = "", key1 = assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).bufferedReader().use { it.readText() }
            }.getOrElse { error ->
                Log.e(LICENSE_TAG, "라이선스 전문 읽기 실패: $assetPath", error)
                fallback
            }
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = HeadingM) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = LICENSE_DIALOG_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = licenseText,
                    style = BodySmall,
                    color = TextSecondaryV2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.close), color = AccentStrong)
            }
        }
    )
}

/**
 * 필름 프리셋 출처 고지 카드.
 *
 * 번들 LUT 가 어디서 왔고 CamCon 이 무엇을 바꿨는지(바꾸지 않았는지) 밝힌다.
 * CC BY-SA 4.0 은 저작자 표시와 함께 '변경 여부 표시'를 요구하므로 이 문장이 고지의 일부다.
 * MIT 는 허가 고지 사본 동봉을 요구하므로 전문 링크를 함께 둔다.
 */
@Composable
private fun FilmLutNoticeCard(onViewFullTextClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    SurfaceV2(
        modifier = Modifier.fillMaxWidth(),
        tier = 2,
        border = true
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Text(
                text = stringResource(R.string.licenses_film_luts_title),
                style = HeadingM,
                color = TextPrimaryV2
            )
            Text(
                text = stringResource(R.string.licenses_film_luts_body),
                style = BodySmall,
                color = TextSecondaryV2,
                modifier = Modifier
                    .widthIn(max = LICENSE_TEXT_MAX_WIDTH)
                    .padding(top = Spacing.sm)
            )
            Text(
                text = stringResource(R.string.licenses_view_full_text),
                style = BodySmall,
                color = if (pressed) Accent else AccentStrong,
                modifier = Modifier
                    .padding(top = Spacing.sm)
                    .defaultMinSize(minHeight = TouchTarget.min)
                    .clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = onViewFullTextClick
                    )
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }
    }
}

/**
 * 상표 면책 카드 — 필름 시뮬레이션 이름은 각 상표권자의 재산이며 제휴·보증 관계가 없음을 밝힌다.
 */
@Composable
private fun TrademarkNoticeCard() {
    SurfaceV2(
        modifier = Modifier.fillMaxWidth(),
        tier = 2,
        border = true
    ) {
        Column(modifier = Modifier.padding(Spacing.base)) {
            Text(
                text = stringResource(R.string.licenses_trademark_title),
                style = HeadingM,
                color = TextPrimaryV2
            )
            Text(
                text = stringResource(R.string.licenses_trademark_body),
                style = BodySmall,
                color = TextSecondaryV2,
                modifier = Modifier
                    .widthIn(max = LICENSE_TEXT_MAX_WIDTH)
                    .padding(top = Spacing.sm)
            )
        }
    }
}

/** 리드 문장 최대 폭 — 전폭 문단 나열을 끊는다. */
private val LICENSE_TEXT_MAX_WIDTH = 420.dp

/** 카드 좌측 메타(저작권) 최대 폭 — 우측 버전 열과 충돌하지 않게 잡는다. */
private val LICENSE_META_MAX_WIDTH = 320.dp

/** 전문 다이얼로그 본문 최대 높이 — 이 높이를 넘으면 안에서 스크롤한다. */
private val LICENSE_DIALOG_MAX_HEIGHT = 420.dp

/** LGPL-2.1 전문 asset 경로. gphoto-build_16k/libgphoto2/COPYING 를 바이트 그대로 복사한 파일이다. */
private const val LGPL_LICENSE_ASSET = "licenses/LGPL-2.1.txt"

/** 다이얼로그 제목 — 라이선스 식별자라 번역 대상이 아니다. */
private const val LGPL_LICENSE_TITLE = "GNU LGPL v2.1"

/** 번들 필름 프리셋(Film-Luts)의 MIT 전문 asset 경로. 저장소 LICENSE 를 그대로 옮긴 파일이다. */
private const val FILM_LUT_MIT_LICENSE_ASSET = "licenses/MIT-Film-Luts.txt"

/** 다이얼로그 제목 — 라이선스 식별자라 번역 대상이 아니다. */
private const val MIT_LICENSE_TITLE = "MIT License"

/**
 * 번들 폰트(Pretendard)의 OFL 전문 asset 경로.
 * 업스트림 저장소 LICENSE 를 그대로 옮긴 파일이라 저작권 고지 4건 + OFL 1.1 본문을 함께 담고 있다.
 */
private const val OFL_LICENSE_ASSET = "licenses/OFL-1.1.txt"

/** 다이얼로그 제목 — 라이선스 식별자라 번역 대상이 아니다. */
private const val OFL_LICENSE_TITLE = "SIL Open Font License 1.1"

/** 전문 읽기 실패 로그 태그. */
private const val LICENSE_TAG = "OpenSourceLicenses"

@Preview(showBackground = true, name = "Open Source Licenses Preview")
@Composable
fun OpenSourceLicensesScreenPreview() {
    CamConTheme() {
        OpenSourceLicensesScreen(
            onBackClick = {}
        )
    }
}
