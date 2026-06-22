package com.inik.camcon.presentation.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.ui.screens.FilmContactSheetScreen
import com.inik.camcon.presentation.ui.screens.FilmEditScreen
import com.inik.camcon.presentation.viewmodel.FilmEditorViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream

/**
 * 필름 시뮬레이션 풀 에디터 호스트(Phase 2).
 *
 * 단일 Activity + Navigation Compose 2 목적지:
 * - [ROUTE_CONTACT_SHEET]: 컨택트 시트(그리드).
 * - [ROUTE_EDIT]("edit/{lutId}"): 편집 화면. Phase 3 에서 구현, 지금은 placeholder.
 *
 * VM([FilmEditorViewModel])은 두 목적지가 공유한다(Activity 스코프 = NavHost 상위에서 1회 hiltViewModel).
 *
 * Intent extra:
 * - [EXTRA_SOURCE_PATH](옵션): 진입 시 바로 적용할 대상 이미지 경로(갤러리/프리뷰 진입점용, Phase 4).
 * - [EXTRA_SELECT_ONLY](옵션): true 면 셀 탭 시 편집 대신 선택한 lutId 를 결과로 반환하고 종료
 *   (자동적용 "기본 필름 선택" 용).
 */
@AndroidEntryPoint
class FilmEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialSourcePath = intent.getStringExtra(EXTRA_SOURCE_PATH)
        val selectOnly = intent.getBooleanExtra(EXTRA_SELECT_ONLY, false)

        setContent {
            CamConTheme {
                FilmEditorHost(
                    initialSourcePath = initialSourcePath,
                    selectOnly = selectOnly,
                    onSelectAndFinish = { lutId ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(EXTRA_RESULT_LUT_ID, lutId)
                        )
                        finish()
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE_PATH = "extra_source_path"
        const val EXTRA_SELECT_ONLY = "extra_select_only"
        const val EXTRA_RESULT_LUT_ID = "extra_result_lut_id"

        const val ROUTE_CONTACT_SHEET = "contactSheet"
        const val ROUTE_EDIT = "edit/{lutId}"

        fun editRoute(lutId: String): String = "edit/$lutId"

        /** 갤러리/프리뷰 진입점: 특정 사진 경로를 대상으로 에디터를 연다(컨택트 시트 → 편집). */
        fun startForPhoto(context: android.content.Context, sourcePath: String) {
            context.startActivity(
                Intent(context, FilmEditorActivity::class.java)
                    .putExtra(EXTRA_SOURCE_PATH, sourcePath)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilmEditorHost(
    initialSourcePath: String?,
    selectOnly: Boolean,
    onSelectAndFinish: (String) -> Unit,
    onClose: () -> Unit
) {
    val navController = rememberNavController()
    // NavHost 상위에서 hiltViewModel 을 호출하므로 ViewModelStoreOwner = Activity → 두 목적지가
    // 동일 VM 인스턴스를 공유한다(NavHost 내부에서 호출하면 목적지별 백스택 스코프로 분리되어 버림).
    val viewModel: FilmEditorViewModel = hiltViewModel()

    // 진입 시 전달된 소스 경로를 1회 설정.
    LaunchedEffect(initialSourcePath) {
        if (initialSourcePath != null) {
            viewModel.setSourceImage(initialSourcePath)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val imageDir = File(context.cacheDir, "film_editor_images")
                if (!imageDir.exists()) imageDir.mkdirs()
                val targetFile = File(imageDir, "film_target_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                }
                viewModel.setSourceImage(targetFile.absolutePath)
            } catch (e: Exception) {
                Log.e("FilmEditorActivity", "대상 이미지 복사 실패", e)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = FilmEditorActivity.ROUTE_CONTACT_SHEET
    ) {
        composable(FilmEditorActivity.ROUTE_CONTACT_SHEET) {
            FilmContactSheetScreen(
                viewModel = viewModel,
                onBackClick = onClose,
                onPickImage = { pickImageLauncher.launch("image/*") },
                onLutClick = { lutId ->
                    viewModel.selectLut(lutId)
                    if (selectOnly) {
                        onSelectAndFinish(lutId)
                    } else {
                        navController.navigate(FilmEditorActivity.editRoute(lutId))
                    }
                }
            )
        }

        composable(
            route = FilmEditorActivity.ROUTE_EDIT,
            arguments = listOf(navArgument("lutId") { type = NavType.StringType })
        ) { backStackEntry ->
            val lutId = backStackEntry.arguments?.getString("lutId").orEmpty()
            FilmEditScreen(
                lutId = lutId,
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
