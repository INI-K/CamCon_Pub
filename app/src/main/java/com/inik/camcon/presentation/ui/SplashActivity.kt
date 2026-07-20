package com.inik.camcon.presentation.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.inik.camcon.BuildConfig
import com.inik.camcon.R
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.camera.GetLibGphoto2VersionUseCase
import com.inik.camcon.domain.usecase.camera.IsNativeLibrariesLoadedUseCase
import com.inik.camcon.domain.usecase.camera.SetupNativeEnvironmentUseCase
import com.inik.camcon.domain.usecase.camera.StartNativeLogUseCase
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.AccentEdge
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DisplayL
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.AppDialog
import com.inik.camcon.presentation.ui.components.v2.PrimaryButton
import com.inik.camcon.presentation.ui.components.v2.ProgressBarV2
import com.inik.camcon.presentation.ui.components.v2.SecondaryButton
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.AppVersionUiState
import com.inik.camcon.presentation.viewmodel.AppVersionViewModel
import com.inik.camcon.utils.LogMask
import com.inik.camcon.utils.LogcatManager
import com.inik.camcon.di.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var getSubscriptionUseCase: GetSubscriptionUseCase

    @Inject
    lateinit var isNativeLibrariesLoadedUseCase: IsNativeLibrariesLoadedUseCase

    @Inject
    lateinit var setupNativeEnvironmentUseCase: SetupNativeEnvironmentUseCase

    @Inject
    lateinit var getLibGphoto2VersionUseCase: GetLibGphoto2VersionUseCase

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var startNativeLogUseCase: StartNativeLogUseCase

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    private var libraryLoadingStatus by mutableStateOf<UiText>(
        UiText.Resource(R.string.splash_initializing)
    )
    private var isLibraryLoaded by mutableStateOf(false)
    private var subscriptionTier: SubscriptionTier? = null
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LogcatManager.i("SplashActivity", "스플래시 화면 시작")

        loadLibrariesInBackground()
        loadSubscriptionTierInBackground()

        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val appVersionViewModel: AppVersionViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()

            CamConTheme() {
                val versionState by appVersionViewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    appVersionViewModel.checkForUpdate()
                }

                SplashScreen(
                    versionState = versionState,
                    libraryLoadingStatus = libraryLoadingStatus,
                    isLibraryLoaded = isLibraryLoaded,
                    subscriptionTier = subscriptionTier,
                    onUpdateApp = { appVersionViewModel.startUpdate() },
                    onDismissUpdateDialog = {
                        if (versionState.versionInfo?.isUpdateRequired == true) {
                            finish()
                        } else {
                            appVersionViewModel.dismissUpdateDialog()
                            navigateToNextScreen()
                        }
                    },
                    navigateToNext = {
                        if (!versionState.showUpdateDialog) {
                            navigateToNextScreen()
                        }
                    }
                )
            }
        }
    }

    /**
     * 백그라운드에서 Libgphoto2 라이브러리들의 로딩 상태를 확인합니다.
     */
    private fun loadLibrariesInBackground() {
        lifecycleScope.launch(ioDispatcher) {
            try {
                LogcatManager.i("SplashActivity", "🚀 라이브러리 상태 확인 프로세스 시작")
                withContext(Dispatchers.Main) {
                    libraryLoadingStatus = UiText.Resource(R.string.splash_library_check)
                }

                val startTime = System.currentTimeMillis()
                val isLoaded = isNativeLibrariesLoadedUseCase()

                LogcatManager.d(
                    "SplashActivity",
                    "라이브러리 로딩 상태: ${if (isLoaded) "정상 로드됨" else "로드되지 않음"}"
                )

                if (!isLoaded) {
                    LogcatManager.e("SplashActivity", "❌ 라이브러리가 로드되지 않았습니다")
                    withContext(Dispatchers.Main) {
                        libraryLoadingStatus = UiText.Resource(R.string.splash_library_failed)
                        isLibraryLoaded = false
                    }
                    return@launch
                }

                val pluginDir =
                    applicationContext.getDir("gphoto2_plugins", MODE_PRIVATE).absolutePath
                LogcatManager.d("SplashActivity", "플러그인 디렉토리 경로: ${LogMask.path(pluginDir)}")

                val envSetupResult = setupNativeEnvironmentUseCase(pluginDir)
                if (!envSetupResult) {
                    LogcatManager.e("SplashActivity", "❌ 환경변수 설정 실패")
                    withContext(Dispatchers.Main) {
                        libraryLoadingStatus = UiText.Resource(R.string.splash_env_failed)
                        isLibraryLoaded = false
                    }
                    return@launch
                }

                LogcatManager.i("SplashActivity", "✅ 환경변수 설정 완료")

                // 디버그 빌드에서는 네이티브 로그 캡처를 기본 활성화 (설정에서 끌 수 있음)
                if (BuildConfig.DEBUG && appSettingsRepository.isNativeLogCaptureEnabled.first()) {
                    val logPath =
                        "${applicationContext.filesDir}/libgphoto2_debug_${System.currentTimeMillis()}.txt"
                    val logStarted = startNativeLogUseCase(logPath)
                    LogcatManager.i(
                        "SplashActivity",
                        "디버그 네이티브 로그 자동 시작: $logStarted (${LogMask.path(logPath)})"
                    )
                }

                val totalTime = System.currentTimeMillis() - startTime

                withContext(Dispatchers.Main) {
                    libraryLoadingStatus = UiText.Resource(
                        R.string.splash_library_ready,
                        listOf(totalTime.toInt())
                    )
                    isLibraryLoaded = true
                }

                try {
                    delay(100)
                    val version = getLibGphoto2VersionUseCase()
                    LogcatManager.i("SplashActivity", "📋 Libgphoto2 버전: $version")
                } catch (e: Exception) {
                    LogcatManager.w("SplashActivity", "⚠️ 라이브러리 버전 확인 실패 (정상적일 수 있음): ${e.message}")
                }

            } catch (e: Exception) {
                LogcatManager.e("SplashActivity", "❌ 라이브러리 상태 확인 실패: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    libraryLoadingStatus = UiText.Resource(
                        R.string.splash_library_status_check_failed,
                        listOf(e.message.orEmpty())
                    )
                    isLibraryLoaded = false
                }
            }
        }
    }

    private fun loadSubscriptionTierInBackground() {
        lifecycleScope.launch(ioDispatcher) {
            try {
                val tier = getSubscriptionUseCase.getSubscriptionTier()
                    .drop(1)
                    .firstOrNull()

                if (tier != null) {
                    getSubscriptionUseCase.persistSubscriptionTier(tier)
                    withContext(Dispatchers.Main) {
                        subscriptionTier = tier
                        LogcatManager.d("SplashActivity", "📱 구독 티어 로드 완료: $tier")
                    }
                }
            } catch (e: Exception) {
                LogcatManager.e("SplashActivity", "❌ 구독 정보 로드 실패: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    subscriptionTier = SubscriptionTier.FREE
                }
            }
        }
    }

    private fun navigateToNextScreen() {
        // 버전 체크 완료 이펙트 재발화 + 다이얼로그 dismiss 직접 호출이 겹치면
        // LoginActivity(standard launchMode)가 2개 쌓이므로 1회만 실행
        if (hasNavigated) return
        hasNavigated = true

        if (firebaseAuth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // USB 연결로 새 Splash가 기존 LoginActivity 위에 뜬 경우 기존 인스턴스 재사용
            startActivity(
                Intent(this, LoginActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }
        finish()
    }
}

@Composable
private fun UiText.resolve(): String = when (this) {
    is UiText.Empty -> ""
    is UiText.Raw -> value
    is UiText.Resource -> if (args.isEmpty()) {
        stringResource(resId)
    } else {
        stringResource(resId, *args.toTypedArray())
    }
}

/**
 * CINE 레티클 프레임 — 순흑 위 코너 틱(앰버 1px)으로 브랜드 마크를 감싼다.
 * 로그인 화면 ReticleFrame 과 동일 언어(목업 preview_cine.html .tick).
 *
 * [progress] 0→1 으로 코너 4개가 순차 드로잉(TL→TR→BL→BR)되며 등장한다. 각 코너는 progress 를 4등분한
 * 자기 구간이 채워질 때 틱 길이가 0→full 로 자란다. 기본 1f = 완성 상태(정적 표시).
 */
@Composable
private fun SplashReticleFrame(
    modifier: Modifier = Modifier,
    tickColor: Color = AccentEdge,
    progress: Float = 1f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.drawBehind {
            val tick = 14.dp.toPx()
            val stroke = 1.dp.toPx()
            val w = size.width
            val h = size.height
            // 코너별 드로잉 진척(0..1) — progress 를 4등분한 구간.
            fun seg(index: Int): Float =
                ((progress - index * 0.25f) / 0.25f).coerceIn(0f, 1f)
            val tl = seg(0)
            val tr = seg(1)
            val bl = seg(2)
            val br = seg(3)
            // 좌상단
            drawLine(tickColor, Offset(0f, 0f), Offset(tick * tl, 0f), stroke)
            drawLine(tickColor, Offset(0f, 0f), Offset(0f, tick * tl), stroke)
            // 우상단
            drawLine(tickColor, Offset(w, 0f), Offset(w - tick * tr, 0f), stroke)
            drawLine(tickColor, Offset(w, 0f), Offset(w, tick * tr), stroke)
            // 좌하단
            drawLine(tickColor, Offset(0f, h), Offset(tick * bl, h), stroke)
            drawLine(tickColor, Offset(0f, h), Offset(0f, h - tick * bl), stroke)
            // 우하단
            drawLine(tickColor, Offset(w, h), Offset(w - tick * br, h), stroke)
            drawLine(tickColor, Offset(w, h), Offset(w, h - tick * br), stroke)
        },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun SplashScreen(
    versionState: AppVersionUiState,
    libraryLoadingStatus: UiText,
    isLibraryLoaded: Boolean,
    subscriptionTier: SubscriptionTier?,
    onUpdateApp: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    navigateToNext: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "splashAlpha"
    )
    // 레티클 코너 틱 draw-on(순차 등장, ~600ms). 페이드 인과 병행 — 네비게이션은 버전 체크에만
    // 의존하므로 이 애니메이션이 스플래시 총 체류 시간을 늘리지 않는다.
    val reticleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "splashReticle"
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        // H6: 강제 지연 제거 — 페이드 인 애니메이션과 병행하여 라이브러리/버전 체크 완료 즉시 다음으로
    }

    LaunchedEffect(versionState.isLoading, versionState.showUpdateDialog) {
        if (!versionState.isLoading && !versionState.showUpdateDialog) {
            navigateToNext()
        }
    }

    // V2 Airy 등급: 풀 블랙(Surface0) + 중앙 로고 + 단일 ProgressBarV2
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(Surface0),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .alpha(alphaAnim.value)
        ) {
            // 브랜드 마크 — 런처 조리개 심볼을 레티클 프레임 안에(로그인 화면과 동일 언어)
            SplashReticleFrame(
                modifier = Modifier.size(140.dp),
                progress = reticleAnim.value
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(112.dp)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
            Text(
                text = "CamCon",
                style = DisplayL,
                color = TextPrimaryV2
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            // 태그라인 — MicroLabel(대문자 라벨), Accent (CINE 정합)
            Text(
                text = stringResource(R.string.splash_camera_controller),
                style = MicroLabel,
                color = Accent,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            // 단일 ProgressBarV2 — 항상 indeterminate로 진행감 제공
            ProgressBarV2(
                progress = null,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // 라이브러리 로딩 상태 — bodyMedium 정렬
            Text(
                text = libraryLoadingStatus.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryV2,
                textAlign = TextAlign.Center
            )

            if (versionState.isLoading) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.splash_version_check),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
            }

            if (BuildConfig.DEBUG && subscriptionTier != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.splash_subscription_tier, subscriptionTier.name),
                    style = BodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Update Dialog — V2 PrimaryButton / SecondaryButton 사용
        if (versionState.showUpdateDialog) {
            val versionInfo = versionState.versionInfo
            if (versionInfo != null) {
                AppDialog(
                    onDismissRequest = {
                        if (!versionInfo.isUpdateRequired) {
                            onDismissUpdateDialog()
                        }
                    },
                    title = {
                        Text(
                            text = stringResource(
                                if (versionInfo.isUpdateRequired) {
                                    R.string.splash_update_required_title
                                } else {
                                    R.string.splash_update_available_title
                                }
                            )
                        )
                    },
                    text = {
                        Text(
                            text = if (versionInfo.isUpdateRequired) {
                                stringResource(
                                    R.string.splash_update_required_message,
                                    versionInfo.currentVersion,
                                    versionInfo.latestVersion
                                )
                            } else {
                                stringResource(
                                    R.string.splash_update_available_message,
                                    versionInfo.currentVersion,
                                    versionInfo.latestVersion
                                )
                            }
                        )
                    },
                    confirmButton = {
                        PrimaryButton(
                            text = stringResource(R.string.splash_update_button),
                            onClick = onUpdateApp
                        )
                    },
                    dismissButton = if (!versionInfo.isUpdateRequired) {
                        {
                            SecondaryButton(
                                text = stringResource(R.string.splash_later_button),
                                onClick = onDismissUpdateDialog
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    CamConTheme() {
        SplashScreen(
            versionState = AppVersionUiState(),
            libraryLoadingStatus = UiText.Resource(R.string.splash_initializing),
            isLibraryLoaded = false,
            subscriptionTier = null,
            onUpdateApp = {},
            onDismissUpdateDialog = {},
            navigateToNext = {}
        )
    }
}
