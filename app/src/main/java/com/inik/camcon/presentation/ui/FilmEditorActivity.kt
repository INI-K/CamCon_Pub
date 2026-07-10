package com.inik.camcon.presentation.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.inik.camcon.R
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
        // 스코프드 스토리지(API29+) 갤러리 own-media 는 raw 파일경로 접근이 막혀 content URI 로 진입한다.
        val initialSourceUri = intent.getStringExtra(EXTRA_SOURCE_URI)
        val selectOnly = intent.getBooleanExtra(EXTRA_SELECT_ONLY, false)

        setContent {
            CamConTheme {
                FilmEditorHost(
                    initialSourcePath = initialSourcePath,
                    initialSourceUri = initialSourceUri,
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
        const val EXTRA_SOURCE_URI = "extra_source_uri"
        const val EXTRA_SELECT_ONLY = "extra_select_only"
        const val EXTRA_RESULT_LUT_ID = "extra_result_lut_id"

        const val ROUTE_CONTACT_SHEET = "contactSheet"
        // lutId 는 에셋 경로(슬래시 포함)라 nav path arg 로 못 넘긴다. 공유 VM 의 selectLut 로
        // 이미 선택되므로 정적 라우트만 쓰고 편집 화면은 VM 에서 선택값을 읽는다.
        const val ROUTE_EDIT = "edit"

        /** 갤러리/프리뷰 진입점: 특정 사진 경로를 대상으로 에디터를 연다(컨택트 시트 → 편집). */
        fun startForPhoto(context: Context, sourcePath: String) {
            context.startActivity(
                Intent(context, FilmEditorActivity::class.java)
                    .putExtra(EXTRA_SOURCE_PATH, sourcePath)
            )
        }

        /**
         * 갤러리 own-media(스코프드 스토리지, API29+) 진입점: content URI 를 대상으로 에디터를 연다.
         * onCreate 에서 URI 를 앱 cache 임시 파일로 복사한 뒤 기존 파이프라인(setSourceImage→decodeFile)을 재사용한다.
         */
        fun startForPhoto(context: Context, sourceUri: Uri) {
            context.startActivity(
                Intent(context, FilmEditorActivity::class.java)
                    .putExtra(EXTRA_SOURCE_URI, sourceUri.toString())
            )
        }
    }
}

/** content Uri 의 표시 파일명(OpenableColumns.DISPLAY_NAME)을 조회한다. 실패 시 null. */
private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()

/**
 * content Uri 를 앱 cache(film_editor_images)에 복사하고 파일을 반환한다. 원본 파일명 보존.
 * 큰 이미지 복사는 반드시 IO 스레드에서 호출할 것(메인 스레드 복사는 UI 멈춤/ANR 유발). 실패 시 null.
 */
private fun copyUriToCache(context: Context, uri: Uri): File? = runCatching {
    val imageDir = File(context.cacheDir, "film_editor_images")
    if (!imageDir.exists()) imageDir.mkdirs()
    val name = queryDisplayName(context, uri)
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: "image_${System.currentTimeMillis()}.jpg"
    val targetFile = File(imageDir, name)
    val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
    input.use { i -> FileOutputStream(targetFile).use { o -> i.copyTo(o) } }
    targetFile
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilmEditorHost(
    initialSourcePath: String?,
    initialSourceUri: String?,
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

    // select-only('기본 필름 선택') 진입인데 대상 사진이 없으면 번들 샘플을 자동 로드해 그리드를 즉시 보인다.
    // (편집 모드는 진짜 대상 사진이 필요하므로 EmptySourcePrompt 유지 — 여기서 로드하지 않는다.)
    LaunchedEffect(selectOnly) {
        if (selectOnly && initialSourcePath == null && initialSourceUri == null) {
            viewModel.ensureSampleSourceIfNeeded()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    // 진입 시 전달된 content URI(갤러리 own-media, API29+)를 1회 처리.
    // raw 파일경로 접근이 막히므로 URI 를 앱 cache 임시 파일로 복사한 뒤 기존 파이프라인을 재사용한다.
    LaunchedEffect(initialSourceUri) {
        val uriStr = initialSourceUri ?: return@LaunchedEffect
        val cached = withContext(Dispatchers.IO) { copyUriToCache(context, Uri.parse(uriStr)) }
        if (cached != null) {
            viewModel.setSourceImage(cached.absolutePath)
        } else {
            Log.e("FilmEditorActivity", "초기 대상 URI 복사 실패")
        }
    }

    val pickScope = rememberCoroutineScope()
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 큰 이미지 복사는 IO 스레드에서 수행한다(메인 스레드 복사는 UI 멈춤/ANR 유발).
        // 원본 파일명을 보존해 대상사진 바에 임시명(film_target_…) 대신 실제 이름이 보이게 한다.
        pickScope.launch(Dispatchers.IO) {
            val cached = copyUriToCache(context, uri)
            if (cached != null) {
                viewModel.setSourceImage(cached.absolutePath)
            } else {
                Log.e("FilmEditorActivity", "대상 이미지 복사 실패")
            }
        }
    }

    // 게이트 통과 선택 → 편집 이동/결과 반환. 잠긴 LUT 탭 → 토스트. 두 수집기 모두 호스트 1곳(공용).
    LaunchedEffect(Unit) {
        viewModel.lutSelectionAccepted.collect { lutId ->
            // [필수] 편집 화면 하단 스트립도 selectLutGated 로 이 이벤트를 발화한다. 라우트 가드가 없으면
            // 필름 전환마다 ROUTE_EDIT 가 중복 push 되어 백스택이 오염된다(연타 방지가 아니라 정상 조작 버그).
            if (navController.currentDestination?.route == FilmEditorActivity.ROUTE_CONTACT_SHEET) {
                if (selectOnly) {
                    onSelectAndFinish(lutId)
                } else {
                    navController.navigate(FilmEditorActivity.ROUTE_EDIT)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.lutLockNotice.collect {
            android.widget.Toast.makeText(
                context, R.string.fs_lut_locked_notice, android.widget.Toast.LENGTH_SHORT
            ).show()
            // TODO(billing): 업그레이드 CTA 추후 지원 (PhotoPreviewLockedScreen.kt 관례)
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
                onPickImage = { pickImageLauncher.launch("image/jpeg") },
                onLutClick = { lutId -> viewModel.selectLutGated(lutId) }
            )
        }

        composable(FilmEditorActivity.ROUTE_EDIT) {
            FilmEditScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
