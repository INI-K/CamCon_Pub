package com.inik.camcon.presentation.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.data.datasource.local.ThemeMode
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.PtpipConnectionScreen
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * PTPIP Wi-Fi 카메라 연결을 관리하는 Activity
 */
@AndroidEntryPoint
class PtpipConnectionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAVIGATE_TO_TAB = "extra_navigate_to_tab"
        const val TAB_CAMERA_CONTROL = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsState()

            CamConTheme(themeMode = themeMode) {
                PtpipConnectionScreen(
                    onBackClick = {
                        // 연결 완료 시 MainActivity로 돌아가면서 카메라 컨트롤 탭 선택
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags =
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(EXTRA_NAVIGATE_TO_TAB, TAB_CAMERA_CONTROL)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "PTPIP Connection Screen Preview")
@Composable
fun PtpipConnectionActivityPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        PtpipConnectionScreen(
            onBackClick = {}
        )
    }
}