package com.inik.camcon.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.inik.camcon.CameraNative
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import com.inik.camcon.presentation.viewmodel.AppVersionUiState
import com.inik.camcon.presentation.viewmodel.AppVersionViewModel
import com.inik.camcon.utils.LogcatManager
import com.inik.camcon.data.datasource.local.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : ComponentActivity() {
    
    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private var libraryLoadingStatus by mutableStateOf("초기화 중...")
    private var isLibraryLoaded by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 엣지-투-엣지 설정을 제거하여 시스템 영역(상단바, 하단바)에 배경이 채워지도록 설정 해제
        // WindowCompat.setDecorFitsSystemWindows(window, true)

        LogcatManager.i("SplashActivity", "=== 스플래시 화면 시작 ===")
        
        // 백그라운드에서 라이브러리 로딩 시작
        loadLibrariesInBackground()

        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val appVersionViewModel: AppVersionViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsState()

            CamConTheme(themeMode = themeMode) {
                val versionState by appVersionViewModel.uiState.collectAsState()

                // 앱 시작 시 버전 체크
                LaunchedEffect(Unit) {
                    appVersionViewModel.checkForUpdate()
                }

                SplashScreen(
                    versionState = versionState,
                    libraryLoadingStatus = libraryLoadingStatus,
                    isLibraryLoaded = isLibraryLoaded,
                    onUpdateApp = { appVersionViewModel.startUpdate() },
                    onDismissUpdateDialog = {
                        // 강제 업데이트인 경우 앱 종료
                        if (versionState.versionInfo?.isUpdateRequired == true) {
                            finish()
                        } else {
                            appVersionViewModel.dismissUpdateDialog()
                            navigateToNextScreen()
                        }
                    },
                    navigateToNext = {
                        // 업데이트 체크가 완료되고 업데이트 다이얼로그가 없는 경우에만 네비게이션
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
     * 라이브러리는 이제 CameraNative의 init 블록에서 자동으로 로드되므로 상태만 확인합니다.
     */
    private fun loadLibrariesInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LogcatManager.i("SplashActivity", "🚀 라이브러리 상태 확인 프로세스 시작")
                withContext(Dispatchers.Main) {
                    libraryLoadingStatus = "라이브러리 상태 확인 중..."
                }

                // 라이브러리 로딩 상태 확인
                val startTime = System.currentTimeMillis()
                val isLoaded = CameraNative.isLibrariesLoaded()

                LogcatManager.d(
                    "SplashActivity",
                    "라이브러리 로딩 상태: ${if (isLoaded) "정상 로드됨" else "로드되지 않음"}"
                )

                if (!isLoaded) {
                    LogcatManager.e("SplashActivity", "❌ 라이브러리가 로드되지 않았습니다")
                    withContext(Dispatchers.Main) {
                        libraryLoadingStatus = "라이브러리 로드 실패"
                        isLibraryLoaded = false
                    }
                    return@launch
                }

                // 환경변수 설정
                val nativeLibDir = applicationContext.applicationInfo.nativeLibraryDir
                LogcatManager.d("SplashActivity", "네이티브 라이브러리 경로: $nativeLibDir")

                val envSetupResult = CameraNative.setupEnvironmentPaths(nativeLibDir)
                if (!envSetupResult) {
                    LogcatManager.e("SplashActivity", "❌ 환경변수 설정 실패")
                    withContext(Dispatchers.Main) {
                        libraryLoadingStatus = "환경변수 설정 실패"
                        isLibraryLoaded = false
                    }
                    return@launch
                }

                LogcatManager.i("SplashActivity", "✅ 환경변수 설정 완료")

                val totalTime = System.currentTimeMillis() - startTime
                
                withContext(Dispatchers.Main) {
                    libraryLoadingStatus = "라이브러리 준비 완료 (${totalTime}ms)"
                    isLibraryLoaded = true
                }
                
                // 추가 검증을 위해 라이브러리 버전 확인 시도
                try {
                    delay(100) // 약간의 지연 후 검증
                    val version = CameraNative.getLibGphoto2Version()
                    LogcatManager.i("SplashActivity", "📋 Libgphoto2 버전: $version")
                } catch (e: Exception) {
                    LogcatManager.w("SplashActivity", "⚠️ 라이브러리 버전 확인 실패 (정상적일 수 있음): ${e.message}")
                }
                
            } catch (e: Exception) {
                LogcatManager.e("SplashActivity", "❌ 라이브러리 상태 확인 실패: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    libraryLoadingStatus = "라이브러리 상태 확인 실패: ${e.message}"
                    isLibraryLoaded = false
                }
            }
        }
    }

    private fun navigateToNextScreen() {
        // 자동 로그인 확인
        if (firebaseAuth.currentUser != null) {
            // 이미 로그인된 사용자는 MainActivity로 이동
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // 로그인되지 않은 사용자는 LoginActivity로 이동
            startActivity(Intent(this, LoginActivity::class.java))
        }
        // 스플래시 화면 종료
        finish()
    }
}

@Composable
fun SplashScreen(
    versionState: com.inik.camcon.presentation.viewmodel.AppVersionUiState,
    libraryLoadingStatus: String,
    isLibraryLoaded: Boolean,
    onUpdateApp: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    navigateToNext: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(1500)
    }

    // 버전 체크가 완료되고 업데이트가 필요하지 않은 경우 자동으로 다음 화면으로 이동
    LaunchedEffect(versionState.isLoading, versionState.showUpdateDialog) {
        if (!versionState.isLoading && !versionState.showUpdateDialog) {
            delay(500) // 애니메이션 완료를 위한 추가 지연
            navigateToNext()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(MaterialTheme.colors.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alphaAnim.value)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_camera),
                contentDescription = "Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "CamCon",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Camera Controller",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            // 라이브러리 로딩 상태 표시
            Text(
                text = libraryLoadingStatus,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            // 버전 체크 로딩 상태 표시
            if (versionState.isLoading) {
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "버전 확인 중...",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
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

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    CamConTheme(themeMode = ThemeMode.LIGHT) {
        SplashScreen(
            versionState = AppVersionUiState(),
            libraryLoadingStatus = "라이브러리 로딩 중...",
            isLibraryLoaded = false,
            onUpdateApp = {},
            onDismissUpdateDialog = {},
            navigateToNext = {}
        )
    }
}
