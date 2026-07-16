package com.inik.camcon.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.inik.camcon.R
import com.inik.camcon.domain.model.UiText
import com.inik.camcon.domain.model.resolve
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.ButtonText
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.Caption
import com.inik.camcon.presentation.theme.DisplayL
import com.inik.camcon.presentation.theme.MicroLabel
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Accent
import com.inik.camcon.presentation.theme.AccentEdge
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.Surface3
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
import com.inik.camcon.presentation.ui.components.v2.DividerLineV2
import com.inik.camcon.presentation.ui.components.v2.SurfaceV2
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.LoginUiEvent
import com.inik.camcon.presentation.viewmodel.LoginUiState
import com.inik.camcon.presentation.viewmodel.LoginViewModel
import com.inik.camcon.presentation.util.openUrl
import com.inik.camcon.utils.Constants
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.onSubscription

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    // 추천 코드를 저장할 변수
    private var currentReferralCode: String = ""

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("LoginActivity", "Google Sign-In result received with code: ${result.resultCode}")

        // 사용자가 계정 선택 화면에서 취소한 경우
        // GMS는 DEVELOPER_ERROR(10) 등 실패도 RESULT_CANCELED로 돌려주므로 실제 상태 코드를 확인해 남긴다
        if (result.resultCode == RESULT_CANCELED) {
            var cancelStatusCode: Int? = null
            try {
                GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
            } catch (e: ApiException) {
                cancelStatusCode = e.statusCode
            }
            Log.e(
                "LoginActivity",
                "Google Sign-In cancelled (RESULT_CANCELED), status=$cancelStatusCode"
            )
            deliverSignInResult { it.onSignInUiError(UiText.Resource(R.string.login_error_cancelled)) }
            return@registerForActivityResult
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (com.inik.camcon.BuildConfig.DEBUG) {
                Log.d("LoginActivity", "Google account received: ${account?.email}")
            }
            account?.idToken?.let { idToken ->
                Log.d("LoginActivity", "ID Token received")
                val referral = currentReferralCode.ifBlank { null }
                deliverSignInResult { it.signInWithGoogle(idToken, referral) }
            } ?: run {
                Log.e("LoginActivity", "ID Token is null")
                deliverSignInResult { it.onSignInUiError(UiText.Resource(R.string.login_error_auth)) }
            }
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google Sign-In failed with code: ${e.statusCode}", e)
            // statusCode 12501(SIGN_IN_CANCELLED) 은 취소로 분류, 그 외는 일반 실패로 분류
            val message = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                UiText.Resource(R.string.login_error_cancelled)
            } else {
                UiText.Resource(R.string.login_error_auth)
            }
            deliverSignInResult { it.onSignInUiError(message) }
        }
    }

    // Hilt VM을 액티비티 스코프 지연 프로퍼티로 보유 → ActivityResult 콜백 시점에 항상 non-null.
    // (과거 nullable var는 Compose 첫 컴포지션에서야 할당돼, 프로세스 사망 복원 시 콜백이 그보다
    //  먼저 실행되면 null이라 로그인 결과가 무음 폐기됐다.)
    private val loginViewModel: LoginViewModel by viewModels()

    // 프로세스 사망 복원 경로에서는 ActivityResult 콜백이 uiEvent 구독(첫 컴포지션의 LaunchedEffect)보다
    // 먼저 실행된다. VM은 항상 보장되지만 결과를 즉시 넘기면 replay=0 uiEvent(에러/네비게이션)가
    // 구독 성립 전 방출돼 유실될 수 있으므로, 구독 전이면 보류했다가 onSubscription 시점에 적용한다.
    private var isUiEventSubscribed = false
    private var pendingSignInAction: ((LoginViewModel) -> Unit)? = null

    private fun deliverSignInResult(action: (LoginViewModel) -> Unit) {
        if (isUiEventSubscribed) {
            action(loginViewModel)
        } else {
            pendingSignInAction = action
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LoginActivity", "onCreate called")

        // Google 로그인 왕복 중 프로세스 사망/구성 변경으로 Activity가 재생성돼도
        // 입력했던 추천 코드가 유실되지 않도록 복원한다.
        currentReferralCode = savedInstanceState?.getString(KEY_REFERRAL_CODE).orEmpty()

        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()

            CamConTheme() {
                // 콜백과 동일 인스턴스를 쓰도록 액티비티 스코프 VM(viewModels)을 그대로 참조한다.
                val viewModel = loginViewModel

                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    try {
                        viewModel.uiEvent
                            .onSubscription {
                                // 구독 성립 직후 — 보류된 로그인 결과를 여기서 적용하면
                                // 이어지는 replay=0 이벤트가 이 수집기에 확실히 전달된다.
                                isUiEventSubscribed = true
                                pendingSignInAction?.let { pending ->
                                    pendingSignInAction = null
                                    pending(viewModel)
                                }
                            }
                            .collect { event ->
                                when (event) {
                                    is LoginUiEvent.ShowError -> {
                                        val text = event.message.resolve(this@LoginActivity)
                                        Log.e("LoginActivity", "Error: $text")
                                        snackbarHostState.showSnackbar(text)
                                    }
                                    is LoginUiEvent.ShowReferralMessage -> {
                                        val text = event.message.resolve(this@LoginActivity)
                                        Log.d("LoginActivity", "Referral message: $text")
                                        snackbarHostState.showSnackbar(text)
                                    }
                                    is LoginUiEvent.NavigateToHome -> {
                                        Log.d("LoginActivity", "User logged in, navigating to MainActivity")
                                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                        finish()
                                    }
                                }
                            }
                    } finally {
                        isUiEventSubscribed = false
                    }
                }

                LoginScreen(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                    onGoogleSignIn = { referralCode ->
                        if (com.inik.camcon.BuildConfig.DEBUG) {
                            Log.d(
                                "LoginActivity",
                                "Google Sign-In clicked, referral=${LogMask.id(referralCode)}"
                            )
                        }
                        currentReferralCode = referralCode
                        signInWithGoogle()
                    }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_REFERRAL_CODE, currentReferralCode)
    }

    private fun signInWithGoogle() {
        Log.d("LoginActivity", "signInWithGoogle() called")
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build()

            val googleSignInClient = GoogleSignIn.getClient(this, gso)

            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                Log.d("LoginActivity", "Launching Google Sign-In intent")
                googleSignInLauncher.launch(signInIntent)
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error creating Google Sign-In intent", e)
        }
    }

    private companion object {
        private const val KEY_REFERRAL_CODE = "referral_code"
    }
}

/**
 * CINE 레티클 프레임 — 순흑 위 코너 틱(앰버 1px)만으로 브랜드 마크를 계측기처럼 감싼다.
 * 목업 preview_cine.html 의 .tick(코너 4틱) 언어. 박스/배경 없음 — 코너 4곳에 L자 스트로크만.
 */
@Composable
private fun ReticleFrame(
    modifier: Modifier = Modifier,
    tickColor: Color = AccentEdge,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.drawBehind {
            val tick = 14.dp.toPx()
            val stroke = 1.dp.toPx()
            val w = size.width
            val h = size.height
            // 좌상
            drawLine(tickColor, Offset(0f, 0f), Offset(tick, 0f), stroke)
            drawLine(tickColor, Offset(0f, 0f), Offset(0f, tick), stroke)
            // 우상
            drawLine(tickColor, Offset(w, 0f), Offset(w - tick, 0f), stroke)
            drawLine(tickColor, Offset(w, 0f), Offset(w, tick), stroke)
            // 좌하
            drawLine(tickColor, Offset(0f, h), Offset(tick, h), stroke)
            drawLine(tickColor, Offset(0f, h), Offset(0f, h - tick), stroke)
            // 우하
            drawLine(tickColor, Offset(w, h), Offset(w - tick, h), stroke)
            drawLine(tickColor, Offset(w, h), Offset(w, h - tick), stroke)
        },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    snackbarHostState: SnackbarHostState,
    onGoogleSignIn: (String) -> Unit
) {
    // 회전/구성 변경에도 입력값·펼침 상태가 유지되도록 rememberSaveable 사용.
    var referralCode by rememberSaveable { mutableStateOf("") }
    // 추천 코드 접이식 노출. 기본 접힘, 세션 내 단방향(한 번 펼치면 유지).
    var showReferral by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 뷰포트 높이 확보(heightIn min) + verticalScroll 조합 = "들어가면 세로 중앙, 넘치면 스크롤".
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 폭 제한 콘텐츠 블록 — 태블릿에서 400dp로 캡, 좌우 순흑 여백이 프레이밍.
                // (디자인 시스템에 width 토큰 부재 → 레이아웃 상수. 추후 토큰화 여지.)
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ---- 브랜드 영역 ----
                    // 브랜드 마크 — 런처 조리개 심볼을 레티클 프레임 안에 크게(새 아트웍 제작 없이 재사용)
                    ReticleFrame(
                        modifier = Modifier.size(148.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(120.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg))

                    // 앱명 — DisplayL, 중앙
                    Text(
                        text = stringResource(R.string.app_name),
                        style = DisplayL,
                        color = TextPrimaryV2
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // 태그라인 — MicroLabel(대문자 라벨, letterSpacing 1.4), Accent
                    Text(
                        text = stringResource(R.string.login_v2_tagline),
                        style = MicroLabel,
                        color = Accent,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(Spacing.xl))

                    // ---- SIGN IN 구획 라벨(브랜드↔패널 브릿지) ----
                    // MicroLabel 구획 라벨(양옆 헤어라인) — CINE 계측기 섹션 헤더 언어
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        DividerLineV2(modifier = Modifier.weight(1f), color = DividerLine)
                        Text(
                            text = stringResource(R.string.login_v2_signin_label),
                            style = MicroLabel,
                            color = TextTertiary
                        )
                        DividerLineV2(modifier = Modifier.weight(1f), color = DividerLine)
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg))

                    // ---- 계측 패널: 추천 + Google + 약관을 헤어라인 보더 안에 그룹핑 ----
                    SurfaceV2(
                        modifier = Modifier.fillMaxWidth(),
                        tier = 1,
                        border = true,
                        shape = RoundedCornerShape(Radius.md)
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 추천 코드 — 접이식 강등. 접힘 상태에선 토글만 노출.
                            AnimatedVisibility(visible = !showReferral) {
                                TextButton(
                                    onClick = { showReferral = true },
                                    enabled = !uiState.isLoading
                                ) {
                                    Text(
                                        text = stringResource(R.string.login_v2_referral_toggle),
                                        style = BodySmall,
                                        color = TextTertiary
                                    )
                                }
                            }
                            // 펼침 상태 — 死 키 login_v2_referral_label을 필드 헤더로 재사용.
                            AnimatedVisibility(visible = showReferral) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.login_v2_referral_label),
                                        style = MicroLabel,
                                        color = TextTertiary
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    // 추천 코드 입력 — 선택. 공백이면 무시하고 로그인 진행.
                                    OutlinedTextField(
                                        value = referralCode,
                                        onValueChange = { referralCode = it },
                                        singleLine = true,
                                        enabled = !uiState.isLoading,
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Characters
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = {
                                            Text(
                                                text = stringResource(R.string.referral_input_label),
                                                color = TextTertiary,
                                                style = BodySmall
                                            )
                                        },
                                        supportingText = {
                                            Text(
                                                text = stringResource(R.string.referral_input_helper),
                                                color = TextTertiary,
                                                style = Caption
                                            )
                                        },
                                        shape = RoundedCornerShape(Radius.sm),
                                        colors = TextFieldDefaults.colors(
                                            focusedTextColor = TextPrimaryV2,
                                            unfocusedTextColor = TextPrimaryV2,
                                            focusedContainerColor = Surface3,
                                            unfocusedContainerColor = Surface3,
                                            disabledContainerColor = Surface3,
                                            focusedIndicatorColor = Accent,
                                            unfocusedIndicatorColor = Surface3,
                                            cursorColor = Accent
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.lg))

                            // Google Sign In Button — 외부 브랜드 색상 유지
                            // 다크 테마 규약 예외: Google Sign-In 디자인 가이드라인에 따라
                            // 버튼은 흰 배경 + Google 브랜드 파랑(0xFF4285F4) 콘텐츠 고정.
                            Button(
                                onClick = { onGoogleSignIn(referralCode.trim()) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF4285F4),
                                    disabledContainerColor = Color.White.copy(alpha = 0.6f),
                                    disabledContentColor = Color(0xFF4285F4).copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(Radius.sm),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp
                                ),
                                enabled = !uiState.isLoading
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color(0xFF4285F4),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_google),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.sm))
                                        Text(
                                            text = stringResource(R.string.login_with_google),
                                            style = ButtonText
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.md))

                            // 약관 — Caption + TextTertiary, 중앙
                            Text(
                                text = stringResource(R.string.terms_agreement),
                                style = Caption,
                                color = TextTertiary,
                                textAlign = TextAlign.Center
                            )

                            // 이용약관 · 개인정보처리방침 — 탭 가능한 링크(로케일별 substring 위치 비의존).
                            // 각 링크 터치 타깃 최소 44dp.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.terms_of_service),
                                    style = Caption,
                                    color = Accent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .clickable {
                                            context.openUrl(Constants.Legal.TERMS_OF_SERVICE_URL)
                                        }
                                        .heightIn(min = 44.dp)
                                        .wrapContentHeight(Alignment.CenterVertically)
                                        .padding(horizontal = Spacing.sm)
                                )
                                Text(
                                    text = " · ",
                                    style = Caption,
                                    color = TextTertiary
                                )
                                Text(
                                    text = stringResource(R.string.privacy_policy),
                                    style = Caption,
                                    color = Accent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .clickable {
                                            context.openUrl(Constants.Legal.PRIVACY_POLICY_URL)
                                        }
                                        .heightIn(min = 44.dp)
                                        .wrapContentHeight(Alignment.CenterVertically)
                                        .padding(horizontal = Spacing.sm)
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    CamConTheme() {
        LoginScreen(
            uiState = LoginUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onGoogleSignIn = { _ -> }
        )
    }
}

@Preview(name = "Login — Tablet", showBackground = true, widthDp = 840, heightDp = 1200)
@Composable
fun LoginScreenTabletPreview() {
    CamConTheme() {
        LoginScreen(
            uiState = LoginUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onGoogleSignIn = { _ -> }
        )
    }
}
