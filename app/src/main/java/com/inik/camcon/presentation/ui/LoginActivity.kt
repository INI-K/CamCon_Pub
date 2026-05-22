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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.android.gms.common.api.ApiException
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.ButtonText
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.HeadingXL
import com.inik.camcon.presentation.theme.Radius
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.Surface0
import com.inik.camcon.presentation.theme.TextPrimaryV2
import com.inik.camcon.presentation.theme.TextSecondaryV2
import com.inik.camcon.presentation.theme.TextTertiary
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
            }
        } catch (e: ApiException) {
            Log.e("LoginActivity", "Google Sign-In failed with code: ${e.statusCode}", e)
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
                                Log.e("LoginActivity", "Error: ${event.message}")
                                snackbarHostState.showSnackbar(event.message)
                            }
                            is LoginUiEvent.ShowReferralMessage -> {
                                Log.d("LoginActivity", "Referral message: ${event.message}")
                                snackbarHostState.showSnackbar(event.message)
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

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    snackbarHostState: SnackbarHostState,
    onGoogleSignIn: (String) -> Unit
) {
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
            // V2 Airy 등급: 좌측 정렬 헤드라인
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.ic_camera),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            // 좌측 정렬 헤드라인 (HeadingXL)
            Text(
                text = stringResource(R.string.app_name),
                style = HeadingXL,
                color = TextPrimaryV2
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = stringResource(R.string.app_description),
                style = BodySmall,
                color = TextSecondaryV2
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            // 환영 메시지 — BodySmall + TextSecondaryV2
            Text(
                text = stringResource(R.string.welcome_message),
                style = BodySmall,
                color = TextSecondaryV2
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            // Google Sign In Button — 외부 브랜드 색상 유지
            // 다크 테마 규약 예외: Google Sign-In 디자인 가이드라인에 따라
            // 버튼은 흰 배경 + Google 브랜드 파랑(0xFF4285F4) 콘텐츠 고정.
            Button(
                onClick = { onGoogleSignIn("") },
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

            // 약관 — BodySmall + TextTertiary
            Text(
                text = stringResource(R.string.terms_agreement),
                style = BodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Start
            )
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
