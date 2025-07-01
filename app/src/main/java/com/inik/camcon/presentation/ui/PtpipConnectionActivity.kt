package com.inik.camcon.presentation.ui

import PtpipConnectionScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.inik.camcon.presentation.theme.CamConTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * PTPIP Wi-Fi 카메라 연결을 관리하는 Activity
 */
@AndroidEntryPoint
class PtpipConnectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CamConTheme {
                PtpipConnectionScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}