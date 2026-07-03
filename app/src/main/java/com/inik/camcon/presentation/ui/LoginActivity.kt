package com.inik.camcon.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.LoginUiEvent
import com.inik.camcon.presentation.viewmodel.LoginUiState
import com.inik.camcon.presentation.viewmodel.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint

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
            loginViewModel?.onSignInUiError(UiText.Resource(R.string.login_error_cancelled))
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
                loginViewModel?.signInWithGoogle(idToken, currentReferralCode.ifBlank { null })
            } ?: run {
                Log.e("LoginActivity", "ID Token is null")
                loginViewModel?.onSignInUiError(UiText.Resource(R.string.login_error_auth))
            }
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google Sign-In failed with code: ${e.statusCode}", e)
            // statusCode 12501(SIGN_IN_CANCELLED) 은 취소로 분류, 그 외는 일반 실패로 분류
            val message = if (e.statusCode == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                UiText.Resource(R.string.login_error_cancelled)
            } else {
                UiText.Resource(R.string.login_error_auth)
            }
            loginViewModel?.onSignInUiError(message)
        }
    }

    private var loginViewModel: LoginViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LoginActivity", "onCreate called")

        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsStateWithLifecycle()

            CamConTheme() {
                val viewModel: LoginViewModel = hiltViewModel()
                loginViewModel = viewModel

                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.uiEvent.collect { event ->
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
                }

                LoginScreen(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                    onGoogleSignIn = { referralCode ->
                        Log.d(
                            "LoginActivity",
                            "Google Sign-In button clicked with referral code: $referralCode"
                        )
                        currentReferralCode = referralCode
                        signInWithGoogle()
                    }
                )
            }
        }
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
    var referralCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface0)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- 브랜드 영역: 첫인상. 화면 상단 2/3를 채워 조리개 마크를 크게 발광시킨다 ----
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
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
            }

            // ---- 로그인 영역: 하단. 헤어라인으로 구획하여 계측기 패널처럼 분리 ----
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

            // 추천 코드 입력 — 선택. 공백이면 무시하고 로그인 진행.
            OutlinedTextField(
                value = referralCode,
                onValueChange = { referralCode = it },
                singleLine = true,
                enabled = !uiState.isLoading,
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

            Spacer(modifier = Modifier.height(Spacing.xl))
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
