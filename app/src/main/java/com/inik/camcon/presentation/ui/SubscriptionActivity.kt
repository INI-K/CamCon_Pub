package com.inik.camcon.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.SubscriptionScreen
import com.inik.camcon.presentation.viewmodel.SubscriptionViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 구독 페이월 화면 진입점.
 *
 * RAW/해상도 게이팅 차단 메시지("PRO 구독에서 사용 가능")의 다음 단계로 호출된다.
 * AndroidManifest에 등록되어 있으며 [start] 로 진입한다.
 */
@AndroidEntryPoint
class SubscriptionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CamConTheme {
                val viewModel: SubscriptionViewModel = hiltViewModel()
                SubscriptionScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }

    companion object {
        fun start(context: android.content.Context) {
            context.startActivity(android.content.Intent(context, SubscriptionActivity::class.java))
        }
    }
}
