package com.inik.camcon.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.LoginUiState
import com.inik.camcon.presentation.viewmodel.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

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
                loginViewModel?.signInWithGoogle(idToken)
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
            CamConTheme {
                val viewModel: LoginViewModel = hiltViewModel()
                loginViewModel = viewModel

                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(uiState.isLoggedIn) {
                    if (uiState.isLoggedIn) {
                        Log.d("LoginActivity", "User logged in, navigating to MainActivity")
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                }

                LoginScreen(
                    uiState = uiState,
                    onGoogleSignIn = {
                        Log.d("LoginActivity", "Google Sign-In button clicked")
                        signInWithGoogle()
                    },
                    onDismissError = { viewModel.clearError() }
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
    onGoogleSignIn: () -> Unit,
    onDismissError: () -> Unit
) {
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

                // Google Sign In Button
                Button(
                    onClick = onGoogleSignIn,
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
                    enabled = !uiState.isLoading
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
                                text = stringResource(R.string.login_with_google),
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
        }
    }
}
