package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.presentation.theme.CamConTheme

/**
 * 간단한 로딩 인디케이터
 */
@Composable
fun LoadingIndicator(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            message,
            color = Color.White,
            fontSize = 16.sp
        )
    }
}

/**
 * 반투명 오버레이가 있는 로딩 컴포넌트
 */
@Composable
fun LoadingOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.DarkGray.copy(alpha = 0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    message,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 전체화면용 로딩 오버레이
 */
@Composable
fun FullscreenLoadingOverlay(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.DarkGray.copy(alpha = 0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    message,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * USB 초기화 중 전체 UI를 블로킹하는 오버레이 컴포넌트
 */
@Composable
fun UsbInitializationOverlay(
    message: String = "USB 카메라 초기화 중...",
    modifier: Modifier = Modifier
) {
    // 애니메이션 설정
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    // 카메라 아이콘 스케일 애니메이션
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    // 배경 알파 애니메이션
    val backgroundAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backgroundAlpha"
    )

    // 텍스트 알파 애니메이션
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .clickable { /* 클릭 이벤트 차단 */ },
        contentAlignment = Alignment.Center
    ) {
        // 메인 카드
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(0.85f)
                .graphicsLayer {
                    // 카드 등장 애니메이션
                    scaleX = 1f
                    scaleY = 1f
                },
            color = MaterialTheme.colors.surface,
            elevation = 16.dp,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 카메라 아이콘
                Surface(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(iconScale),
                    color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "카메라",
                        modifier = Modifier
                            .size(48.dp)
                            .padding(24.dp),
                        tint = MaterialTheme.colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 제목
                Text(
                    text = "카메라 연결 중",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 메시지
                Text(
                    text = message,
                    fontSize = 15.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(36.dp))

                // 프로그레스 바 영역
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // 백그라운드 서클
                    Surface(
                        modifier = Modifier.size(72.dp),
                        color = MaterialTheme.colors.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(36.dp)
                    ) {}

                    // 프로그레스 인디케이터
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colors.primary,
                        strokeWidth = 4.dp
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 안내 텍스트
                Text(
                    text = "잠시만 기다려주세요...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = textAlpha),
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Preview(name = "Loading Components", showBackground = true)
@Composable
private fun LoadingComponentsPreview() {
    CamConTheme {
        Column(
            modifier = Modifier
                .background(Color.Black)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Loading Indicator:", color = Color.White, fontWeight = FontWeight.Bold)
            LoadingIndicator("라이브뷰 시작 중...")

            Text("Loading Overlay:", color = Color.White, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color.DarkGray)
            ) {
                LoadingOverlay("타임랩스 촬영 중...")
            }
        }
    }
}

@Preview(name = "USB 초기화 오버레이", showBackground = true)
@Composable
private fun UsbInitializationOverlayPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Gray)
    ) {
        UsbInitializationOverlay(
            message = "USB 카메라를 초기화하고 있습니다.\n잠시만 기다려주세요."
        )
    }
}

@Preview(name = "USB 초기화 오버레이 - 권한 요청", showBackground = true)
@Composable
private fun UsbInitializationOverlayPermissionPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Gray)
    ) {
        UsbInitializationOverlay(
            message = "USB 권한을 요청하고 있습니다.\n팝업에서 허용을 눌러주세요."
        )
    }
}

@Preview(name = "USB 초기화 오버레이 - 다크 모드", showBackground = true)
@Composable
private fun UsbInitializationOverlayDarkPreview() {
    MaterialTheme(
        colors = MaterialTheme.colors.copy(
            surface = Color.Black,
            onSurface = Color.White,
            primary = Color.Cyan
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            UsbInitializationOverlay(
                message = "USB 카메라 연결 중입니다.\n잠시만 기다려주세요."
            )
        }
    }
}