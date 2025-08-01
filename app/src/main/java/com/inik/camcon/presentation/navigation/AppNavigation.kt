package com.inik.camcon.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.inik.camcon.presentation.ui.screens.CameraControlScreen
import com.inik.camcon.presentation.ui.screens.MyPhotosScreen
import com.inik.camcon.presentation.ui.screens.PhotoPreviewScreen
import com.inik.camcon.presentation.ui.screens.PtpipConnectionScreen
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import com.inik.camcon.presentation.viewmodel.PtpipViewModel

/**
 * 앱의 전체 네비게이션 구조
 * 메인 화면의 바텀 네비게이션과 모달 화면들을 관리
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    cameraViewModel: CameraViewModel,
    startDestination: String = AppDestination.CameraControl.route,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // 메인 화면들 (바텀 네비게이션)
        composable(AppDestination.PhotoPreview.route) {
            PhotoPreviewScreen()
        }

        composable(AppDestination.CameraControl.route) {
            CameraControlScreen(viewModel = cameraViewModel)
        }

        composable(AppDestination.ServerPhotos.route) {
            MyPhotosScreen()
        }

        // 모달 화면들
        composable(AppDestination.PtpipConnection.route) {
            val ptpipViewModel: PtpipViewModel = hiltViewModel()
            PtpipConnectionScreen(
                onBackClick = { navController.popBackStack() },
                ptpipViewModel = ptpipViewModel
            )
        }
    }
}

/**
 * 앱 전체 화면 목적지 정의
 */
sealed class AppDestination(val route: String) {
    // 메인 화면들
    object PhotoPreview : AppDestination("photo_preview")
    object CameraControl : AppDestination("camera_control")
    object ServerPhotos : AppDestination("server_photos")

    // 모달 화면들
    object PtpipConnection : AppDestination("ptpip_connection")
}

/**
 * 바텀 네비게이션 전용 아이템들
 */
object BottomNavDestinations {
    val items = listOf(
        AppDestination.PhotoPreview,
        AppDestination.CameraControl,
        AppDestination.ServerPhotos
    )
}