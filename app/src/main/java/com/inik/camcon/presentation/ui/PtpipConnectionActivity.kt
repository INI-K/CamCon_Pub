package com.inik.camcon.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.PtpipConnectionScreen
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * PTPIP Wi-Fi 카메라 연결을 관리하는 Activity
 */
@AndroidEntryPoint
class PtpipConnectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsState()

            CamConTheme(themeMode = themeMode) {
                PtpipConnectionScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}