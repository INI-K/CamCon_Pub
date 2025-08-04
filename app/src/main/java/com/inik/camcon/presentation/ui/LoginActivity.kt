package com.inik.camcon.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Snackbar
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.AppVersionViewModel
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
            Log.d("LoginActivity", "Google account received: ${account?.email}")
            account?.idToken?.let { idToken ->
                Log.d("LoginActivity", "ID Token received, length: ${idToken.length}")
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
            val themeMode by appSettingsViewModel.themeMode.collectAsState()

            CamConTheme(themeMode = themeMode) {
                val viewModel: LoginViewModel = hiltViewModel()
                val appVersionViewModel: AppVersionViewModel = hiltViewModel()
                loginViewModel = viewModel

                val uiState by viewModel.uiState.collectAsState()
                val versionState by appVersionViewModel.uiState.collectAsState()

                // 앱 시작 시 버전 체크
                LaunchedEffect(Unit) {
                    appVersionViewModel.checkForUpdate()
                }

                // 로그인 성공 시 MainActivity로 이동
                LaunchedEffect(uiState.isLoggedIn) {
                    if (uiState.isLoggedIn) {
                        Log.d("LoginActivity", "User logged in, navigating to MainActivity")
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                }

                LoginScreen(
                    uiState = uiState,
                    versionState = versionState,
                    onGoogleSignIn = { referralCode ->
                        Log.d(
                            "LoginActivity",
                            "Google Sign-In button clicked with referral code: $referralCode"
                        )
                        currentReferralCode = referralCode  // 추천 코드 저장
                        signInWithGoogle()  // Google 로그인 시작
                    },
                    onDismissError = { viewModel.clearError() },
                    onDismissReferralMessage = { viewModel.clearReferralMessage() },
                    onUpdateApp = { appVersionViewModel.startUpdate() },
                    onDismissUpdateDialog = {
                        // 강제 업데이트인 경우 앱 종료
                        if (versionState.versionInfo?.isUpdateRequired == true) {
                            finish()
                        } else {
                            appVersionViewModel.dismissUpdateDialog()
                        }
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
                .build()

            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            val signInIntent = googleSignInClient.signInIntent
            Log.d("LoginActivity", "Launching Google Sign-In intent")
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error creating Google Sign-In intent", e)
        }
    }
}

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    versionState: com.inik.camcon.presentation.viewmodel.AppVersionUiState,
    onGoogleSignIn: (String) -> Unit,
    onDismissError: () -> Unit,
    onDismissReferralMessage: () -> Unit,
    onUpdateApp: () -> Unit,
    onDismissUpdateDialog: () -> Unit
) {

    // State for recommendation code input
    var recommendCode by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colors.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                Card(
                    modifier = Modifier.size(120.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = 8.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_camera),
                            contentDescription = "Logo",
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )

                Text(
                    text = stringResource(R.string.app_description),
                    fontSize = 16.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = stringResource(R.string.welcome_message),
                    fontSize = 14.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 추천 코드 입력 필드
                OutlinedTextField(
                    value = recommendCode,
                    onValueChange = { recommendCode = it },
                    label = { Text("추천 코드(선택)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    maxLines = 1,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )

                // Google Sign In Button - 업데이트가 필요한 경우 비활성화
                Button(
                    onClick = {
                        // Google Sign-In 버튼 클릭 시, 추천 코드(recommendCode) 활용 가능
                        onGoogleSignIn(recommendCode)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.White,
                        contentColor = Color(0xFF4285F4)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = ButtonDefaults.elevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    ),
                    enabled = !uiState.isLoading && !(versionState.versionInfo?.isUpdateRequired == true)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF4285F4)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (versionState.versionInfo?.isUpdateRequired == true) {
                                    "업데이트가 필요합니다"
                                } else {
                                    stringResource(R.string.login_with_google)
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.terms_agreement),
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            // Error Snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = onDismissError) {
                            Text(stringResource(R.string.close))
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // Referral Message
            uiState.referralCodeMessage?.let { message ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    action = {
                        TextButton(onClick = onDismissReferralMessage) {
                            Text(stringResource(R.string.close))
                        }
                    }
                ) {
                    Text(message)
                }
            }

            // Update Dialog
            if (versionState.showUpdateDialog) {
                val versionInfo = versionState.versionInfo
                if (versionInfo != null) {
                    AlertDialog(
                        onDismissRequest = {
                            if (!versionInfo.isUpdateRequired) {
                                onDismissUpdateDialog()
                            }
                        },
                        title = {
                            Text(
                                text = if (versionInfo.isUpdateRequired) {
                                    "필수 업데이트"
                                } else {
                                    "업데이트 가능"
                                }
                            )
                        },
                        text = {
                            Text(
                                text = if (versionInfo.isUpdateRequired) {
                                    "앱을 사용하려면 최신 버전으로 업데이트해야 합니다.\n\n현재 버전: ${versionInfo.currentVersion}\n최신 버전: ${versionInfo.latestVersion}"
                                } else {
                                    "새로운 버전이 출시되었습니다. 업데이트하시겠습니까?\n\n현재 버전: ${versionInfo.currentVersion}\n최신 버전: ${versionInfo.latestVersion}"
                                }
                            )
                        },
                        confirmButton = {
                            Button(onClick = onUpdateApp) {
                                Text("업데이트")
                            }
                        },
                        dismissButton = if (!versionInfo.isUpdateRequired) {
                            {
                                TextButton(onClick = onDismissUpdateDialog) {
                                    Text("나중에")
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    CamConTheme {
        LoginScreen(
            uiState = LoginUiState(),
            versionState = com.inik.camcon.presentation.viewmodel.AppVersionUiState(),
            onGoogleSignIn = { _ -> },
            onDismissError = {},
            onDismissReferralMessage = {},
            onUpdateApp = {},
            onDismissUpdateDialog = {}
        )
    }
}
