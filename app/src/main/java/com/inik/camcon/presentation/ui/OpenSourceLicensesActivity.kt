package com.inik.camcon.presentation.ui

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
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
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 네이티브 라이브러리 라이선스 정보
 */
data class NativeLicense(
    val name: String,
    val version: String,
    val license: String,
    val copyright: String,
    val url: String
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
        NativeLicense(
            name = "libgphoto2",
            version = "2.5.34",
            license = "LGPL-2.1-or-later",
            copyright = "Copyright (c) 2000-2024 The gphoto2 Team",
            url = "https://github.com/gphoto/libgphoto2"
        ),
        NativeLicense(
            name = "libgphoto2_port",
            version = "0.12.2",
            license = "LGPL-2.1-or-later",
            copyright = "Copyright (c) 2000-2024 The gphoto2 Team",
            url = "https://github.com/gphoto/libgphoto2"
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

@Composable
fun NativeLicenseItem(license: NativeLicense) {
    val uriHandler = LocalUriHandler.current
    val interaction = remember { MutableInteractionSource() }
    val urlPressed by interaction.collectIsPressedAsState()

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
                    Text(
                        text = license.version,
                        style = MonoNumeric,
                        color = TextSecondaryV2
                    )
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
        }
    }
}

/** 리드 문장 최대 폭 — 전폭 문단 나열을 끊는다. */
private val LICENSE_TEXT_MAX_WIDTH = 420.dp

/** 카드 좌측 메타(저작권) 최대 폭 — 우측 버전 열과 충돌하지 않게 잡는다. */
private val LICENSE_META_MAX_WIDTH = 320.dp

@Preview(showBackground = true, name = "Open Source Licenses Preview")
@Composable
fun OpenSourceLicensesScreenPreview() {
    CamConTheme() {
        OpenSourceLicensesScreen(
            onBackClick = {}
        )
    }
}
