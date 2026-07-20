package com.inik.camcon.presentation.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inik.camcon.BuildConfig
import com.inik.camcon.R
import com.inik.camcon.domain.usecase.PipelineFeature
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.HeadingL
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.ui.screens.settings.AdminReferralSection
import com.inik.camcon.presentation.ui.screens.settings.AdminSection
import com.inik.camcon.presentation.ui.screens.settings.AdvisoryToastHost
import com.inik.camcon.presentation.ui.screens.settings.AppSection
import com.inik.camcon.presentation.ui.screens.settings.CameraControlSection
import com.inik.camcon.presentation.ui.screens.settings.ColorTransferSection
import com.inik.camcon.presentation.ui.screens.settings.ConnectedCameraSection
import com.inik.camcon.presentation.ui.screens.settings.DeleteAccountDialog
import com.inik.camcon.presentation.ui.screens.settings.DeleteConfirmDialog
import com.inik.camcon.presentation.ui.screens.settings.FilmSimulationSection
import com.inik.camcon.presentation.ui.screens.settings.InfoSection
import com.inik.camcon.presentation.ui.screens.settings.LanguageSelectionDialog
import com.inik.camcon.presentation.ui.screens.settings.LiveViewQualitySelectionDialog
import com.inik.camcon.presentation.ui.screens.settings.LogoutConfirmDialog
import com.inik.camcon.presentation.ui.screens.settings.NativeLogDialog
import com.inik.camcon.presentation.ui.screens.settings.RawDownloadSection
import com.inik.camcon.presentation.ui.screens.settings.ReferralRedeemDialog
import com.inik.camcon.presentation.ui.screens.settings.ServerSection
import com.inik.camcon.presentation.ui.screens.settings.SettingsConnectionViewModel
import com.inik.camcon.presentation.ui.screens.settings.UserInfoSection
import com.inik.camcon.presentation.ui.screens.settings.WifiPtpipSection
import com.inik.camcon.presentation.ui.screens.settings.currentLanguageLabel
import com.inik.camcon.presentation.util.copyToClipboard
import com.inik.camcon.presentation.util.openUrl
import com.inik.camcon.presentation.viewmodel.AdminReferralCodeViewModel
import com.inik.camcon.presentation.viewmodel.AdminReferralUiEvent
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.AuthUiEvent
import com.inik.camcon.presentation.viewmodel.AuthViewModel
import com.inik.camcon.presentation.viewmodel.PtpipViewModel
import com.inik.camcon.presentation.viewmodel.ReferralRedeemEvent
import com.inik.camcon.presentation.viewmodel.ReferralRedeemViewModel
import com.inik.camcon.utils.Constants
import com.inik.camcon.utils.resolve
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val authViewModel: AuthViewModel = hiltViewModel()

            CamConTheme {
                SettingsScreen(
                    onBackClick = { finish() },
                    authViewModel = authViewModel
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "SettingsScreen Preview")
@Composable
fun SettingsScreenPreview() {
    CamConTheme {
        SettingsScreen(
            onBackClick = {},
            authViewModel = null
        )
    }
}

@Preview(showBackground = true, name = "SettingsActivity Preview")
@Composable
fun SettingsActivityPreview() {
    SettingsScreenPreview()
}

/**
 * 설정 화면 조립부 — 섹션/다이얼로그 컴포저블은 presentation.ui.screens.settings 로 분해했고
 * 여기서는 상태 관찰·이벤트 수집·런처·내비게이션 콜백만 배선한다.
 *
 * 연결 상태는 무거운 CameraViewModel 대신 경량 [SettingsConnectionViewModel] 로 관찰한다
 * (별도 Activity 에서 CameraViewModel 인스턴스가 2개 공존하며 @Singleton 매니저 cleanup 을
 * 유발해 진행 중 라이브뷰를 끊는 결함을 근본 제거).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    ptpipViewModel: PtpipViewModel = hiltViewModel(),
    appSettingsViewModel: AppSettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel? = hiltViewModel(),
    adminReferralCodeViewModel: AdminReferralCodeViewModel = hiltViewModel(),
    referralRedeemViewModel: ReferralRedeemViewModel = hiltViewModel(),
    connectionViewModel: SettingsConnectionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val authUiState by authViewModel?.uiState?.collectAsStateWithLifecycle() ?: remember {
        mutableStateOf(com.inik.camcon.presentation.viewmodel.AuthUiState())
    }

    val adminReferralState by adminReferralCodeViewModel.uiState.collectAsStateWithLifecycle()
    val referralRedeemState by referralRedeemViewModel.uiState.collectAsStateWithLifecycle()

    val cameraUiState by connectionViewModel.uiState.collectAsStateWithLifecycle()
    val isUsbConnected = cameraUiState.isNativeCameraConnected
    val isPtpipConnected = cameraUiState.isPtpipConnected

    val logoutFailedTemplate = stringResource(R.string.settings_toast_logout_failed)

    // 1회 구독 — uiEvent 채널은 ViewModel lifetime 동안 단일 collector만 필요
    LaunchedEffect(Unit) {
        authViewModel?.uiEvent?.collect { event ->
            when (event) {
                is AuthUiEvent.ShowError -> {
                    android.widget.Toast.makeText(
                        context,
                        logoutFailedTemplate.format(event.message),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                is AuthUiEvent.SignOutSuccess -> {
                    // NavigateToLogin에서 처리
                }
                is AuthUiEvent.NavigateToLogin -> {
                    val intent = Intent(context, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                }
                is AuthUiEvent.AccountDeleteSuccess -> {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.account_delete_success),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    // 화면 이동은 이어서 emit되는 NavigateToLogin 이 처리한다.
                }
                is AuthUiEvent.AccountDeleteFailure -> {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.account_delete_failed).format(event.message),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // 1회 구독 — uiEvent 채널은 ViewModel lifetime 동안 단일 collector만 필요
    LaunchedEffect(Unit) {
        adminReferralCodeViewModel.uiEvent.collect { event ->
            when (event) {
                is AdminReferralUiEvent.ShowError -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_LONG).show()
                }
                is AdminReferralUiEvent.ShowSuccess -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val isPtpipEnabled by ptpipViewModel.isPtpipEnabled.collectAsStateWithLifecycle(initialValue = false)
    val isAutoConnectEnabled by ptpipViewModel.isAutoConnectEnabled.collectAsStateWithLifecycle(initialValue = false)
    val lastConnectedName by ptpipViewModel.lastConnectedName.collectAsStateWithLifecycle(initialValue = null)

    val isCameraControlsEnabled by appSettingsViewModel.isCameraControlsEnabled.collectAsStateWithLifecycle()
    val isLiveViewEnabled by appSettingsViewModel.isLiveViewEnabled.collectAsStateWithLifecycle()
    val liveViewQuality by appSettingsViewModel.liveViewQuality.collectAsStateWithLifecycle()
    val isAdminTier by appSettingsViewModel.isAdminTier.collectAsStateWithLifecycle()
    val isAutoStartEventListener by appSettingsViewModel.isAutoStartEventListenerEnabled.collectAsStateWithLifecycle()
    val isShowLatestPhotoWhenDisabled by appSettingsViewModel.isShowLatestPhotoWhenDisabled.collectAsStateWithLifecycle()

    val isColorTransferEnabled by appSettingsViewModel.isColorTransferEnabled.collectAsStateWithLifecycle()
    val colorTransferReferenceImagePath by appSettingsViewModel.colorTransferReferenceImagePath.collectAsStateWithLifecycle()
    val isVibrateOnPhotoReceivedEnabled by appSettingsViewModel.isVibrateOnPhotoReceivedEnabled.collectAsStateWithLifecycle()
    val isFilmSimulationEnabled by appSettingsViewModel.isFilmSimulationEnabled.collectAsStateWithLifecycle()
    val selectedFilmLutId by appSettingsViewModel.selectedFilmLutId.collectAsStateWithLifecycle()
    val selectedFilmLutLocked by appSettingsViewModel.selectedFilmLutLocked.collectAsStateWithLifecycle()
    val filmSimulationIntensity by appSettingsViewModel.filmSimulationIntensity.collectAsStateWithLifecycle()
    val isRawFileDownloadEnabled by appSettingsViewModel.isRawFileDownloadEnabled.collectAsStateWithLifecycle()
    val isShutterSoundEnabled by appSettingsViewModel.isShutterSoundEnabled.collectAsStateWithLifecycle()

    val subscriptionTier by appSettingsViewModel.subscriptionTier.collectAsStateWithLifecycle()
    val isRawDownloadAllowed by appSettingsViewModel.isRawDownloadAllowed.collectAsStateWithLifecycle()

    val notificationGrantedText = stringResource(R.string.settings_toast_notification_granted)
    val notificationDeniedText = stringResource(R.string.settings_toast_notification_denied)
    val notificationUnsupportedText = stringResource(R.string.settings_toast_notification_unsupported)
    val batterySettingsErrorTemplate = stringResource(R.string.settings_toast_battery_settings_error)
    val nativeLogToastEnableText = stringResource(R.string.settings_native_log_toast_enable)
    val nativeLogToastDisableText = stringResource(R.string.settings_native_log_toast_disable)
    val nativeLogNoFilesText = stringResource(R.string.settings_native_log_no_files)
    val nativeLogClipboardLabel = stringResource(R.string.settings_native_log_clipboard_label)
    val nativeLogCopiedText = stringResource(R.string.settings_native_log_copied)
    val referralClipboardLabel = stringResource(R.string.settings_referral_clipboard_label)
    val referralCopiedTemplate = stringResource(R.string.settings_referral_copied)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val message = if (isGranted) notificationGrantedText else notificationDeniedText
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }

    val referenceImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val imageDir = File(context.filesDir, "color_transfer_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }
                val fileName = "color_ref_${System.currentTimeMillis()}.jpg"
                val targetFile = File(imageDir, fileName)
                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                appSettingsViewModel.setColorTransferReferenceImagePath(targetFile.absolutePath)
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "참조 이미지 복사 실패", e)
            }
        }
    }

    // 자동적용 "기본 필름 선택" — 에디터를 select-only 로 열어 결과 LUT id 를 설정에 반영.
    val defaultFilmSelectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val lutId = result.data?.getStringExtra(FilmEditorActivity.EXTRA_RESULT_LUT_ID)
            if (!lutId.isNullOrEmpty()) {
                appSettingsViewModel.setSelectedFilmLutId(lutId)
            }
        }
    }

    var logDialogContent by remember { mutableStateOf<String?>(null) }

    // 안내성(비오류·비시스템) 토스트를 V2 ToastV2 오버레이로 표시. null 이면 숨김.
    // 에러/시스템(권한·결제·Firebase 삭제·자동연결 결과)은 컴포지션 수명과 무관하게
    // 동작해야 하므로 기존 android.widget.Toast 를 유지한다(아래 각 호출부 참조).
    var advisoryToastMessage by remember { mutableStateOf<String?>(null) }

    // 그룹 1 — 계정/로그아웃/삭제 UX, 언어 선택
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteConfirmInput by remember { mutableStateOf("") }
    var showReferralRedeemDialog by remember { mutableStateOf(false) }
    var referralRedeemInput by remember { mutableStateOf("") }

    // 추천 코드 등록 결과 — 성공 시 다이얼로그를 닫고, 성공/실패 모두 토스트로 안내.
    LaunchedEffect(Unit) {
        referralRedeemViewModel.uiEvent.collect { event ->
            when (event) {
                is ReferralRedeemEvent.Success -> {
                    showReferralRedeemDialog = false
                    referralRedeemInput = ""
                    android.widget.Toast.makeText(
                        context,
                        event.message.resolve(context),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                is ReferralRedeemEvent.Error -> {
                    android.widget.Toast.makeText(
                        context,
                        event.message.resolve(context),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    // 계정 삭제 진행 상태는 AuthViewModel(서버 CF 호출)에서 관리 — 로컬 상태 분산 방지.
    val isDeletingAccount = authUiState.isDeletingAccount
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLiveViewQualityDialog by remember { mutableStateOf(false) }

    // 필름↔색감 배타 스왑 안내 — 기존 advisoryToastMessage 호스트 재사용(신규 토스트 추가 금지).
    val pipelineSwapColorDisabledText = stringResource(R.string.pipeline_swap_color_disabled)
    val pipelineSwapFilmDisabledText = stringResource(R.string.pipeline_swap_film_disabled)
    LaunchedEffect(Unit) {
        appSettingsViewModel.pipelineSwapEvent.collect { disabled ->
            advisoryToastMessage = when (disabled) {
                PipelineFeature.COLOR_TRANSFER -> pipelineSwapColorDisabledText
                PipelineFeature.FILM_SIMULATION -> pipelineSwapFilmDisabledText
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.settings_v2_title), style = HeadingL, color = TextPrimaryV2) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_v2_back),
                            tint = TextPrimaryV2
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface0,
                    titleContentColor = TextPrimaryV2,
                    navigationIconContentColor = TextPrimaryV2
                )
            )
        },
        containerColor = Surface0,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface0)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 카메라 제어 설정 — 개발 빌드 또는 ADMIN 티어에 노출.
            // (빌드 타입만으로 게이트하면 릴리즈에서 관리자에게도 안 보인다)
            if (BuildConfig.SHOW_DEVELOPER_FEATURES || isAdminTier) {
                CameraControlSection(
                    isCameraControlsEnabled = isCameraControlsEnabled,
                    isAdminTier = isAdminTier,
                    isLiveViewEnabled = isLiveViewEnabled,
                    liveViewQuality = liveViewQuality,
                    isAutoStartEventListener = isAutoStartEventListener,
                    isShowLatestPhotoWhenDisabled = isShowLatestPhotoWhenDisabled,
                    onCameraControlsChange = { appSettingsViewModel.setCameraControlsEnabled(it) },
                    onLiveViewChange = { appSettingsViewModel.setLiveViewEnabled(it) },
                    onLiveViewQualityClick = { showLiveViewQualityDialog = true },
                    onAutoStartEventChange = { appSettingsViewModel.setAutoStartEventListenerEnabled(it) },
                    onShowLatestPhotoChange = { appSettingsViewModel.setShowLatestPhotoWhenDisabled(it) }
                )
            }

            // Wi-Fi PTP/IP 연결은 전 티어 노출 — 무선 연결은 구독 기능이 아니다(FREE 포함).
            WifiPtpipSection(
                isPtpipEnabled = isPtpipEnabled,
                lastConnectedName = lastConnectedName,
                isAutoConnectEnabled = isAutoConnectEnabled,
                connectionStatusText = ptpipViewModel.getConnectionStatusText(),
                onPtpipEnabledChange = { ptpipViewModel.setPtpipEnabled(it) },
                onAutoConnectChange = { enabled ->
                    ptpipViewModel.updateAutoConnectEnabled(
                        enabled = enabled,
                        onResult = { _, message ->
                            android.widget.Toast.makeText(
                                context, message, android.widget.Toast.LENGTH_LONG
                            ).show()
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    notificationUnsupportedText,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                },
                onManageConnectionClick = {
                    val intent = Intent(context, PtpipConnectionActivity::class.java)
                    context.startActivity(intent)
                }
            )

            // 색감 전송 설정
            ColorTransferSection(
                isColorTransferEnabled = isColorTransferEnabled,
                hasReferenceImage = colorTransferReferenceImagePath != null,
                isVibrateOnPhotoReceivedEnabled = isVibrateOnPhotoReceivedEnabled,
                onColorTransferChange = { appSettingsViewModel.setColorTransferEnabled(it) },
                onColorTransferDetailClick = {
                    val intent = Intent(context, ColorTransferSettingsActivity::class.java)
                    context.startActivity(intent)
                },
                onVibrateChange = { appSettingsViewModel.setVibrateOnPhotoReceivedEnabled(it) }
            )

            // 필름 시뮬레이션 설정
            FilmSimulationSection(
                isFilmSimulationEnabled = isFilmSimulationEnabled,
                selectedFilmLutId = selectedFilmLutId,
                selectedFilmLutLocked = selectedFilmLutLocked,
                filmSimulationIntensity = filmSimulationIntensity,
                onFilmEditorClick = {
                    val intent = Intent(context, FilmEditorActivity::class.java)
                    context.startActivity(intent)
                },
                onFilmSimulationChange = { appSettingsViewModel.setFilmSimulationEnabled(it) },
                onDefaultFilmClick = {
                    val intent = Intent(context, FilmEditorActivity::class.java)
                        .putExtra(FilmEditorActivity.EXTRA_SELECT_ONLY, true)
                    defaultFilmSelectLauncher.launch(intent)
                },
                onIntensityChange = { appSettingsViewModel.setFilmSimulationIntensity(it) }
            )

            // RAW 파일 다운로드 설정
            RawDownloadSection(
                isRawDownloadAllowed = isRawDownloadAllowed,
                isRawFileDownloadEnabled = isRawFileDownloadEnabled,
                onRawDownloadChange = { appSettingsViewModel.setRawFileDownloadEnabled(it) }
            )

            // 연결된 카메라 정보
            ConnectedCameraSection(
                isUsbConnected = isUsbConnected,
                isPtpipConnected = isPtpipConnected,
                connectedCameraModel = cameraUiState.connectedCameraModel,
                connectedCameraManufacturer = cameraUiState.connectedCameraManufacturer,
                cameraFunctionLimitation = cameraUiState.cameraFunctionLimitation
            )

            // 사용자 정보
            UserInfoSection(
                user = authUiState.currentUser,
                subscriptionTier = subscriptionTier,
                isLoggingOut = authUiState.isLoading,
                onProfileClick = { },
                onSubscriptionClick = { SubscriptionActivity.start(context) },
                onReferralRedeemClick = {
                    referralRedeemInput = ""
                    showReferralRedeemDialog = true
                },
                onLogoutClick = {
                    if (!authUiState.isLoading) {
                        showLogoutConfirm = true
                    }
                },
                onDeleteAccountClick = {
                    if (!isDeletingAccount) {
                        showDeleteAccountDialog = true
                    }
                }
            )

            // 서버 설정 — 개발 빌드 또는 ADMIN 티어에 노출
            if (BuildConfig.SHOW_DEVELOPER_FEATURES || isAdminTier) {
                ServerSection()
            }

            // 앱 설정
            val currentLocaleLabel = currentLanguageLabel()
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
            AppSection(
                isShutterSoundEnabled = isShutterSoundEnabled,
                currentLanguageLabel = currentLocaleLabel,
                isIgnoringBatteryOptimizations = isIgnoringOptimizations,
                onShutterSoundChange = { appSettingsViewModel.setShutterSoundEnabled(it) },
                onLanguageClick = { showLanguageDialog = true },
                onNotificationSettingsClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback: 앱 상세 설정 화면
                        try {
                            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            fallback.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(fallback)
                        } catch (_: Exception) {
                        }
                    }
                },
                onBatteryClick = {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            context,
                            batterySettingsErrorTemplate.format(e.localizedMessage ?: ""),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )

            if (isAdminTier) {
                val isNativeLogCaptureEnabled by appSettingsViewModel.isNativeLogCaptureEnabled.collectAsStateWithLifecycle()

                AdminSection(
                    isNativeLogCaptureEnabled = isNativeLogCaptureEnabled,
                    showDeveloperFeatures = BuildConfig.SHOW_DEVELOPER_FEATURES,
                    onMockCameraClick = {
                        runCatching {
                            val cls = Class.forName("com.inik.camcon.presentation.ui.MockCameraActivity")
                            context.startActivity(Intent(context, cls))
                        }
                    },
                    onCameraAbilitiesClick = {
                        context.startActivity(Intent(context, CameraAbilitiesActivity::class.java))
                    },
                    onNativeLogCaptureChange = {
                        appSettingsViewModel.setNativeLogCaptureEnabled(it)
                        // 안내성 토스트 → ToastV2 오버레이
                        advisoryToastMessage =
                            if (it) nativeLogToastEnableText else nativeLogToastDisableText
                    },
                    onViewLogClick = {
                        val logFiles = appSettingsViewModel.getLogFiles()
                        if (logFiles.isEmpty()) {
                            // 안내성 토스트 → ToastV2 오버레이
                            advisoryToastMessage = nativeLogNoFilesText
                        } else {
                            coroutineScope.launch {
                                logDialogContent = appSettingsViewModel.getLogFileContent()
                            }
                        }
                    }
                )
            }

            // 네이티브 로그 다이얼로그 — V2 PrimaryButton/SecondaryButton
            logDialogContent?.let { logContent ->
                NativeLogDialog(
                    logContent = logContent,
                    onCopy = {
                        context.copyToClipboard(nativeLogClipboardLabel, logContent)
                        // 안내성 토스트 → ToastV2 오버레이
                        advisoryToastMessage = nativeLogCopiedText
                    },
                    onDismiss = { logDialogContent = null }
                )
            }

            // 정보
            InfoSection(
                appVersion = BuildConfig.VERSION_NAME,
                onOssLicenseClick = {
                    val intent = Intent(context, OpenSourceLicensesActivity::class.java)
                    context.startActivity(intent)
                },
                onPrivacyClick = { context.openUrl(Constants.Legal.PRIVACY_POLICY_URL) },
                onTermsClick = { context.openUrl(Constants.Legal.TERMS_OF_SERVICE_URL) }
            )

            // Mock Camera 진입점은 ADMIN 섹션에 이미 노출됨 (위쪽 isAdminTier 블록).
            // 개발자 빌드 분기는 ADMIN 권한과 중복되어 동일 화면이 두 번 보이는 문제가 있어 삭제.
            // 관리자 레퍼럴 코드 관리 — 개발 빌드 또는 ADMIN 티어에 노출(릴리즈 관리자 대응).
            if (BuildConfig.SHOW_DEVELOPER_FEATURES || isAdminTier) {
                AdminReferralSection(
                    statistics = adminReferralState.statistics,
                    isLoading = adminReferralState.isLoading,
                    onRefreshClick = { adminReferralCodeViewModel.refreshData() },
                    onGenerateClick = {
                        if (!adminReferralState.isLoading) {
                            adminReferralCodeViewModel.generateReferralCodes(30)
                        }
                    },
                    onExtractClick = {
                        val code = adminReferralCodeViewModel.extractOneAvailableCode()
                        if (code != null) {
                            context.copyToClipboard(referralClipboardLabel, code)
                            // 안내성(코드 복사됨) 토스트 → ToastV2 오버레이
                            advisoryToastMessage = referralCopiedTemplate.format(code)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }

        // 안내성 토스트 오버레이 — 상단에서 슬라이드 인, 3초 후 자동 소멸.
        AdvisoryToastHost(
            message = advisoryToastMessage,
            paddingValues = paddingValues,
            onDismiss = { advisoryToastMessage = null }
        )

        // -------- 그룹 1 — 로그아웃 / 계정 삭제 / 언어 선택 다이얼로그 --------

        if (showLogoutConfirm) {
            LogoutConfirmDialog(
                onConfirm = {
                    showLogoutConfirm = false
                    authViewModel?.signOut()
                },
                onDismiss = { showLogoutConfirm = false }
            )
        }

        if (showDeleteAccountDialog) {
            DeleteAccountDialog(
                onContinue = {
                    showDeleteAccountDialog = false
                    deleteConfirmInput = ""
                    showDeleteConfirmDialog = true
                },
                onDismiss = { showDeleteAccountDialog = false }
            )
        }

        if (showDeleteConfirmDialog) {
            DeleteConfirmDialog(
                input = deleteConfirmInput,
                onInputChange = { deleteConfirmInput = it },
                isDeleting = isDeletingAccount,
                onConfirm = {
                    // 서버(deleteAccount CF)가 Firestore 사용자 데이터·구독·레퍼럴·구매기록과
                    // Firebase Auth 사용자까지 삭제한다. 로딩(isDeletingAccount)·성공/실패 토스트·
                    // 로그인 화면 이동은 AuthViewModel 의 uiState/uiEvent 에서 처리한다.
                    authViewModel?.deleteAccount() ?: run {
                        showDeleteConfirmDialog = false
                        val intent = Intent(context, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(intent)
                        (context as? ComponentActivity)?.finish()
                    }
                },
                onDismiss = { showDeleteConfirmDialog = false }
            )
        }

        if (showReferralRedeemDialog) {
            ReferralRedeemDialog(
                input = referralRedeemInput,
                onInputChange = { referralRedeemInput = it },
                isLoading = referralRedeemState.isLoading,
                onApply = { referralRedeemViewModel.redeem(referralRedeemInput) },
                onDismiss = { showReferralRedeemDialog = false }
            )
        }

        if (showLanguageDialog) {
            LanguageSelectionDialog(
                onDismissRequest = { showLanguageDialog = false },
                onLanguageSelected = { tag ->
                    val locales = if (tag == null) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(tag)
                    }
                    AppCompatDelegate.setApplicationLocales(locales)
                    showLanguageDialog = false
                }
            )
        }

        if (showLiveViewQualityDialog) {
            LiveViewQualitySelectionDialog(
                current = liveViewQuality,
                onSelected = {
                    appSettingsViewModel.setLiveViewQuality(it)
                    showLiveViewQualityDialog = false
                },
                onDismissRequest = { showLiveViewQualityDialog = false }
            )
        }
    }
}
