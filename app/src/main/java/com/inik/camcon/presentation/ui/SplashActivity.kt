package com.inik.camcon.presentation.ui

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawnWhen
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
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.camera.GetLibGphoto2VersionUseCase
import com.inik.camcon.domain.usecase.camera.IsNativeLibrariesLoadedUseCase
import com.inik.camcon.domain.usecase.camera.SetupNativeEnvironmentUseCase
import com.inik.camcon.domain.usecase.camera.StartNativeLogUseCase
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.AccentEdge
import com.inik.camcon.presentation.theme.BodyLarge
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DisplayL
import com.inik.camcon.presentation.theme.Micro
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
import com.inik.camcon.presentation.viewmodel.UsbAutoConnectManager
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

    @Inject
    lateinit var globalManager: CameraConnectionGlobalManager

    @Inject
    lateinit var usbAutoConnectManager: UsbAutoConnectManager

    @Inject
    lateinit var usbDeviceRepository: UsbDeviceRepository

    private var libraryLoadingStatus by mutableStateOf<UiText>(
        UiText.Resource(R.string.splash_initializing)
    )
    private var isLibraryLoaded by mutableStateOf(false)

    // 관찰 가능해야 한다. 일반 필드로 두면 ReportDrawnWhen 조건이 티어 도착 시 재평가되지 않고,
    // SplashScreen 의 티어 표시도 갱신되지 않는다. 항상 Main 디스패처에서만 쓴다.
    private var subscriptionTier by mutableStateOf<SubscriptionTier?>(null)
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 부착 문맥은 가장 먼저 기록한다. 케이블로 앱이 기동될 때는 USB 브로드캐스트 리시버가
        // 등록되기 전에 시스템 브로드캐스트가 지나가므로 이 인텐트가 유일한 증거이고, 장치
        // 관찰자가 이 사실을 보기 전에 기록돼 있어야 권한 유예가 짧게 적용되지 않는다.
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            usbDeviceRepository.noteCameraAttached()
        }

        if (consumeLateAttachIntent()) return

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

                // TTFD(time to full display) 기준점. 이게 없으면 StartupTimingMetric 의
                // timeToFullDisplay 가 timeToInitialDisplay 로 붕괴해, libgphoto2 .so 로딩과
                // 구독 티어 조회에 걸린 시간이 측정에서 통째로 빠진다(스타트업 수치가 거짓으로 좋아진다).
                ReportDrawnWhen { isLibraryLoaded && subscriptionTier != null }

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

                // 플러그인 경로는 UseCase 가 스스로 정한다. 예전엔 여기서 베이스 디렉터리를
                // 넘겼는데, `.so` 는 버전 하위 디렉터리에만 있어서 CAMLIBS/IOLIBS 가 빈 곳을
                // 가리켰다(앱 시작 직후 올바른 설정을 덮어씀 — 2026-08-20 실측).
                val envSetupResult = setupNativeEnvironmentUseCase()
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

    /**
     * 이미 카메라를 쓰고 있는 중에 시스템이 케이블 부착으로 스플래시를 또 띄운 경우, 화면을
     * 그리지 않고 조용히 사라진다.
     *
     * 매니페스트의 USB_DEVICE_ATTACHED 필터 때문에 케이블이 꽂힐 때마다 시스템이 이 액티비티를
     * 새로 띄운다. 앱을 이미 쓰고 있고 카메라도 붙어 있는 상태에서는 그 진입이 아무 일도 하지
     * 않으면서 스플래시만 한 번 번쩍이게 만든다(실기 로그의 SplashActivity 재기동).
     *
     * [isTaskRoot] 가 거짓이라는 것은 이 태스크 아래에 이미 다른 화면이 깔려 있다는 뜻이다.
     * 그때만 물러난다 — 앱이 꺼진 상태에서 케이블로 기동된 경우에는 아래에 아무것도 없으므로
     * 정상적으로 스플래시를 띄워야 한다.
     *
     * @return 소비하고 물러났으면 true. 호출부는 그대로 onCreate 를 끝내야 한다.
     */
    private fun consumeLateAttachIntent(): Boolean {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return false
        if (isTaskRoot) return false

        val connectionBusy = try {
            globalManager.isAnyCameraConnected() || usbAutoConnectManager.isAutoConnecting.value
        } catch (e: Exception) {
            LogcatManager.w("SplashActivity", "연결 상태 확인 실패 - 정상 경로로 진행", e)
            false
        }
        if (!connectionBusy) return false

        LogcatManager.d("SplashActivity", "연결 중/연결됨 상태의 케이블 부착 진입 - 화면 전환 없이 소비")
        finish()
        // 이미 떠 있는 화면이 그대로 보이도록 전환 애니메이션을 없앤다(깜빡임 제거).
        overridePendingTransition(0, 0)
        return true
    }

    private fun navigateToNextScreen() {
        // 버전 체크 완료 이펙트 재발화 + 다이얼로그 dismiss 직접 호출이 겹치면
        // LoginActivity(standard launchMode)가 2개 쌓이므로 1회만 실행
        if (hasNavigated) return
        hasNavigated = true

        if (firebaseAuth.currentUser != null) {
            startActivity(forwardAttachIntentTo(Intent(this, MainActivity::class.java)))
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

    /**
     * 케이블로 앱이 기동된 경우, 부착 인텐트를 다음 화면으로 넘겨 준다.
     *
     * 시스템은 부착 인텐트를 스플래시에만 배달하는데 여기서 버려지면 MainActivity 의 USB 진입
     * 경로가 한 번도 불리지 않는다(적대 검수 M2). 그 결과 정식 경로 대신 장치 목록 폴링 같은
     * 우회로에만 기대게 되어, 어떤 장치가 꽂혀서 앱이 떴는지조차 알 수 없었다.
     */
    private fun forwardAttachIntentTo(target: Intent): Intent {
        val source = intent ?: return target
        if (source.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return target

        target.action = UsbManager.ACTION_USB_DEVICE_ATTACHED
        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            source.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            source.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        if (device != null) {
            target.putExtra(UsbManager.EXTRA_DEVICE, device)
        }
        LogcatManager.d("SplashActivity", "케이블 기동 - 부착 인텐트를 다음 화면으로 전달")
        return target
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

            // 주 상태 판독값 — 16sp 리드. 아래 종속 라벨(11sp)과 5sp 스케일 대비를 만든다.
            Text(
                text = libraryLoadingStatus.resolve(),
                style = BodyLarge,
                color = TextSecondaryV2,
                textAlign = TextAlign.Center
            )

            // 종속 라벨 — 주 판독값에 딸린 부가 상태. 간격도 xs로 좁혀 종속 관계를 드러낸다.
            // MicroLabel(트래킹 1.4)이 아닌 Micro: 이 문자열들은 로케일에 따라 CJK가 들어온다.
            if (versionState.isLoading) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.splash_version_check),
                    style = Micro,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
            }

            if (BuildConfig.DEBUG && subscriptionTier != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.splash_subscription_tier, subscriptionTier.name),
                    style = Micro,
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
            // 실제 콜드 스타트 값 — libgphoto2 로드 1284ms, 버전 체크 진행 중, PRO 계정
            versionState = AppVersionUiState(isLoading = true),
            libraryLoadingStatus = UiText.Resource(
                R.string.splash_library_ready,
                listOf(1284)
            ),
            isLibraryLoaded = true,
            subscriptionTier = SubscriptionTier.PRO,
            onUpdateApp = {},
            onDismissUpdateDialog = {},
            navigateToNext = {}
        )
    }
}
