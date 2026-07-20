package com.inik.camcon.presentation.ui.screens.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.inik.camcon.R
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton

/**
 * 현재 적용된 언어 라벨. AppCompatDelegate 의 application locales 가 비어 있으면
 * 시스템 기본, 그렇지 않으면 첫 locale 의 language tag 를 매핑해 반환한다.
 */
@Composable
internal fun currentLanguageLabel(): String {
    val applied = AppCompatDelegate.getApplicationLocales()
    val tag = if (applied.isEmpty) {
        null
    } else {
        applied.toLanguageTags().substringBefore(',').substringBefore('-')
    }
    val resId = when (tag) {
        "ko" -> R.string.language_option_ko
        "ja" -> R.string.language_option_ja
        "zh" -> R.string.language_option_zh
        "de" -> R.string.language_option_de
        "es" -> R.string.language_option_es
        "fr" -> R.string.language_option_fr
        "it" -> R.string.language_option_it
        "en" -> R.string.language_option_en
        else -> R.string.language_system_default
    }
    return stringResource(resId)
}

/**
 * 언어 선택 다이얼로그 — System default + 8개 언어 라디오 리스트.
 *
 * [onLanguageSelected] 의 인자는 BCP-47 tag 또는 null(시스템 기본).
 * 호출자가 AppCompatDelegate.setApplicationLocales 처리 책임을 가진다.
 */
@Composable
internal fun LanguageSelectionDialog(
    onDismissRequest: () -> Unit,
    onLanguageSelected: (String?) -> Unit
) {
    data class LangOption(val tag: String?, val labelRes: Int)
    val options = listOf(
        LangOption(null, R.string.language_system_default),
        LangOption("ko", R.string.language_option_ko),
        LangOption("ja", R.string.language_option_ja),
        LangOption("zh", R.string.language_option_zh),
        LangOption("de", R.string.language_option_de),
        LangOption("es", R.string.language_option_es),
        LangOption("fr", R.string.language_option_fr),
        LangOption("it", R.string.language_option_it),
        LangOption("en", R.string.language_option_en)
    )

    val applied = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (applied.isEmpty) {
        null
    } else {
        applied.toLanguageTags().substringBefore(',').substringBefore('-')
    }

    AppDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                stringResource(R.string.language_dialog_title),
                style = HeadingL,
                color = TextPrimaryV2
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                options.forEach { option ->
                    val selected = option.tag == currentTag
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(option.tag) }
                            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onLanguageSelected(option.tag) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Accent,
                                unselectedColor = TextSecondaryV2
                            )
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = stringResource(option.labelRes),
                            style = HeadingM,
                            color = TextPrimaryV2
                        )
                    }
                }
            }
        },
        confirmButton = {
            SecondaryButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest
            )
        }
    )
}

/**
 * 라이브뷰 화질(속도/균형/품질) 선택 다이얼로그.
 * LanguageSelectionDialog 패턴 복제 — AppDialog + RadioButton 리스트, 다크 V2 토큰.
 */
@Composable
internal fun LiveViewQualitySelectionDialog(
    current: LiveViewQuality,
    onSelected: (LiveViewQuality) -> Unit,
    onDismissRequest: () -> Unit
) {
    data class QualityOption(
        val quality: LiveViewQuality,
        @StringRes val labelRes: Int,
        @StringRes val descRes: Int
    )

    val options = listOf(
        QualityOption(
            LiveViewQuality.SPEED,
            R.string.settings_v2_liveview_quality_speed,
            R.string.settings_v2_liveview_quality_speed_desc
        ),
        QualityOption(
            LiveViewQuality.BALANCED,
            R.string.settings_v2_liveview_quality_balanced,
            R.string.settings_v2_liveview_quality_balanced_desc
        ),
        QualityOption(
            LiveViewQuality.QUALITY,
            R.string.settings_v2_liveview_quality_quality,
            R.string.settings_v2_liveview_quality_quality_desc
        )
    )

    AppDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                stringResource(R.string.settings_v2_liveview_quality_title),
                style = HeadingL,
                color = TextPrimaryV2
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                options.forEach { option ->
                    val selected = option.quality == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option.quality) }
                            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelected(option.quality) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Accent,
                                unselectedColor = TextSecondaryV2
                            )
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = stringResource(option.labelRes),
                                style = HeadingM,
                                color = TextPrimaryV2
                            )
                            Text(
                                text = stringResource(option.descRes),
                                style = BodySmall,
                                color = TextSecondaryV2
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            SecondaryButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest
            )
        }
    )
}
