package com.inik.camcon.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.inik.camcon.presentation.ui.screens.PtpipConnectionScreen
import com.inik.camcon.presentation.viewmodel.PtpipViewModel

/**
 * PTPIP 연결 화면 네비게이션
 */
@Composable
fun PtpipNavigation(
    navController: NavHostController,
    onBackClick: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = PtpipDestination.Connection.route
    ) {
        composable(PtpipDestination.Connection.route) {
            val ptpipViewModel: PtpipViewModel = hiltViewModel()
            PtpipConnectionScreen(
                onBackClick = onBackClick,
                ptpipViewModel = ptpipViewModel
            )
        }
    }
}

/**
 * PTPIP 화면 목적지
 */
sealed class PtpipDestination(val route: String) {
    object Connection : PtpipDestination("ptpip_connection")
}