package com.inik.camcon.presentation.ui.screens.components

// Coil imports for image loading
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inik.camcon.R
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.presentation.theme.Background
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.theme.DividerLine
import com.inik.camcon.presentation.theme.Error
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.theme.SurfaceElevated
import com.inik.camcon.presentation.theme.TextPrimary
import com.inik.camcon.presentation.theme.TextSecondary
import com.inik.camcon.presentation.theme.Warning
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.LiveViewFrame
import com.inik.camcon.presentation.viewmodel.CameraConnectionState
import com.inik.camcon.presentation.viewmodel.CameraCaptureState
import com.inik.camcon.presentation.viewmodel.CameraLiveViewState
import com.inik.camcon.presentation.viewmodel.CameraUiState
import com.inik.camcon.domain.model.ThemeMode

/**
 * 카메라 프리뷰 영역 — state+callback 패턴
 *
 * @param liveViewState 라이브뷰 on/off 상태
 * @param liveViewFrame 라이브뷰 프레임 (별도 StateFlow에서 수집)
 * @param connectionState 연결 상태
 * @param captureState 촬영 상태
 * @param cameraCapabilities 카메라 능력
 * @param cameraFeed 카메라 목록
 * @param onStopLiveView 라이브뷰 중지 콜백
 * @param onStartLiveView 라이브뷰 시작 콜백
 * @param onConnectCamera 카메라 연결 콜백
 * @param onRefreshUsb USB 새로고침 콜백
 * @param onRequestUsbPermission USB 권한 요청 콜백
 * @param onDoubleClick 더블클릭 콜백
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraPreviewArea(
    liveViewState: CameraLiveViewState,
    liveViewFrame: LiveViewFrame?,
    decodedBitmap: android.graphics.Bitmap?,  // ✅ 새 파라미터: IO 디스패처에서 디코딩된 Bitmap
    connectionState: CameraConnectionState,
    captureState: CameraCaptureState,
    cameraCapabilities: CameraCapabilities?,
    cameraFeed: List<Camera>,
    onStopLiveView: () -> Unit,
    onStartLiveView: () -> Unit,
    onConnectCamera: (String) -> Unit,
    onRefreshUsb: () -> Unit,
    onRequestUsbPermission: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleClick: (() -> Unit)? = null,
    isGridOverlayEnabled: Boolean = false,
    onToggleGridOverlay: (() -> Unit)? = null,
    histogramData: com.inik.camcon.presentation.util.HistogramData? = null,
    isHistogramEnabled: Boolean = false,
    onToggleHistogram: (() -> Unit)? = null,
    isFocusPeakingEnabled: Boolean = false,
    onToggleFocusPeaking: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = { /* 단일 클릭 처리 */ },
                onDoubleClick = { onDoubleClick?.invoke() }
            )
    ) {
        // ✅ 수정 (CRITICAL-1 + W-2 해결): remember 기반 디코딩 제거, DisposableEffect 추가
        if (liveViewState.isLiveViewActive && liveViewFrame != null && decodedBitmap != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 포커스 피킹: 토글 ON 이면 edge 가 입혀진 별도 Bitmap 으로 표시.
                // IO 작업이므로 LaunchedEffect 에서 처리하고 결과만 화면에 반영.
                val focusPeakingBitmap = androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null)
                }
                val ioDispatcher = kotlinx.coroutines.Dispatchers.IO
                androidx.compose.runtime.LaunchedEffect(decodedBitmap, isFocusPeakingEnabled) {
                    if (!isFocusPeakingEnabled) {
                        focusPeakingBitmap.value = null
                        return@LaunchedEffect
                    }
                    val processed = kotlinx.coroutines.withContext(ioDispatcher) {
                        try {
                            com.inik.camcon.presentation.util.applyFocusPeaking(decodedBitmap)
                        } catch (e: Exception) {
                            Log.w("CameraPreview", "포커스 피킹 처리 실패", e)
                            null
                        }
                    }
                    focusPeakingBitmap.value = processed
                }

                val displayBitmap = focusPeakingBitmap.value ?: decodedBitmap

                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Live View",
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = { /* 단일 클릭 처리 */ },
                            onDoubleClick = {
                                Log.d("CameraPreview", "라이브뷰 이미지 더블클릭 감지")
                                onDoubleClick?.invoke()
                            }
                        ),
                    contentScale = ContentScale.Fit
                )

                // ✅ DisposableEffect 추가: Bitmap 명시적 회수 (W-2 해결)
                // Key를 Unit으로 변경하여 교체 중 경합 방지
                androidx.compose.runtime.DisposableEffect(Unit) {
                    onDispose {
                        try {
                            if (!decodedBitmap.isRecycled) {
                                decodedBitmap.recycle()
                            }
                        } catch (e: Exception) {
                            Log.w("CameraPreview", "Bitmap recycle 실패", e)
                        }
                    }
                }

                // 포커스 피킹 결과 Bitmap 도 화면에서 사라질 때 회수.
                androidx.compose.runtime.DisposableEffect(focusPeakingBitmap.value) {
                    val bmp = focusPeakingBitmap.value
                    onDispose {
                        try {
                            if (bmp != null && !bmp.isRecycled) bmp.recycle()
                        } catch (e: Exception) {
                            Log.w("CameraPreview", "포커스 피킹 Bitmap recycle 실패", e)
                        }
                    }
                }

                // H5: Rule-of-Thirds 그리드 오버레이
                if (isGridOverlayEnabled) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val stroke = 1.dp.toPx()
                        val color = androidx.compose.ui.graphics.Color(DividerLine.value)
                            .copy(alpha = 0.7f)
                        // 2 vertical lines at 1/3 and 2/3
                        drawLine(color, androidx.compose.ui.geometry.Offset(w / 3f, 0f),
                            androidx.compose.ui.geometry.Offset(w / 3f, h), strokeWidth = stroke)
                        drawLine(color, androidx.compose.ui.geometry.Offset(2f * w / 3f, 0f),
                            androidx.compose.ui.geometry.Offset(2f * w / 3f, h), strokeWidth = stroke)
                        // 2 horizontal lines at 1/3 and 2/3
                        drawLine(color, androidx.compose.ui.geometry.Offset(0f, h / 3f),
                            androidx.compose.ui.geometry.Offset(w, h / 3f), strokeWidth = stroke)
                        drawLine(color, androidx.compose.ui.geometry.Offset(0f, 2f * h / 3f),
                            androidx.compose.ui.geometry.Offset(w, 2f * h / 3f), strokeWidth = stroke)
                    }
                }

                // H5 + G7: 우상단 토글 버튼 묶음 (그리드 / 히스토그램 / 포커스 피킹)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onToggleGridOverlay != null) {
                        androidx.compose.material3.Surface(
                            color = SurfaceElevated.copy(alpha = 0.7f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            IconButton(
                                onClick = onToggleGridOverlay,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isGridOverlayEnabled) Icons.Default.GridOn else Icons.Default.GridOff,
                                    contentDescription = stringResource(R.string.liveview_grid_toggle),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (onToggleHistogram != null) {
                        androidx.compose.material3.Surface(
                            color = SurfaceElevated.copy(
                                alpha = if (isHistogramEnabled) 0.9f else 0.7f
                            ),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            IconButton(
                                onClick = onToggleHistogram,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = stringResource(R.string.liveview_histogram_toggle),
                                    tint = if (isHistogramEnabled)
                                        MaterialTheme.colorScheme.primary else TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (onToggleFocusPeaking != null) {
                        androidx.compose.material3.Surface(
                            color = SurfaceElevated.copy(
                                alpha = if (isFocusPeakingEnabled) 0.9f else 0.7f
                            ),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            IconButton(
                                onClick = onToggleFocusPeaking,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CenterFocusWeak,
                                    contentDescription = stringResource(R.string.liveview_focus_peaking_toggle),
                                    tint = if (isFocusPeakingEnabled)
                                        MaterialTheme.colorScheme.primary else TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // G7: 좌상단 히스토그램 오버레이 (토글 ON 시에만)
                if (isHistogramEnabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Spacing.md)
                    ) {
                        com.inik.camcon.presentation.ui.components.v2.HistogramOverlay(
                            data = histogramData
                        )
                    }
                }

                // 라이브뷰 중지 버튼 오버레이
                Button(
                    onClick = {
                        Log.d("CameraControl", "Stop live view button clicked")
                        onStopLiveView()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Error.copy(alpha = 0.8f)
                    )
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = stringResource(R.string.cd_stop_live_view),
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.stop_live_view), color = TextPrimary)
                }
            }
        } else if (!connectionState.isConnected) {
            CameraDisconnectedState(
                connectionState = connectionState,
                cameraFeed = cameraFeed,
                onConnectCamera = onConnectCamera,
                onRefreshUsb = onRefreshUsb,
                onRequestUsbPermission = onRequestUsbPermission
            )
        } else {
            CameraConnectedState(
                isConnected = connectionState.isConnected,
                isLiveViewActive = liveViewState.isLiveViewActive,
                cameraCapabilities = cameraCapabilities,
                onStartLiveView = onStartLiveView,
                onStopLiveView = onStopLiveView
            )
        }

        // 전역 로딩 오버레이
        if (captureState.isCapturing) {
            LoadingOverlay(stringResource(R.string.capturing_photo))
        }

        // 라이브뷰 로딩 오버레이
        if (liveViewState.isLiveViewLoading) {
            LoadingOverlay(stringResource(R.string.starting_liveview))
        }
    }
}

/**
 * 카메라 연결 안됨 상태 — state+callback 패턴
 */
@Composable
fun CameraDisconnectedState(
    connectionState: CameraConnectionState,
    cameraFeed: List<Camera>,
    onConnectCamera: (String) -> Unit,
    onRefreshUsb: () -> Unit,
    onRequestUsbPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.UsbOff,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.camera_not_connected),
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.connect_camera_usb),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
        CameraConnectionButtons(
            connectionState = connectionState,
            cameraFeed = cameraFeed,
            onConnectCamera = onConnectCamera,
            onRefreshUsb = onRefreshUsb,
            onRequestUsbPermission = onRequestUsbPermission
        )
    }
}

/**
 * 카메라 연결됨 상태 (라이브뷰 비활성) — state+callback 패턴
 */
@Composable
fun CameraConnectedState(
    isConnected: Boolean,
    isLiveViewActive: Boolean,
    cameraCapabilities: CameraCapabilities?,
    onStartLiveView: () -> Unit,
    onStopLiveView: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        // 라이브뷰 지원 여부 확인
        val supportsLiveView = cameraCapabilities?.canLiveView ?: false

        if (supportsLiveView) {
            Icon(
                if (isLiveViewActive) Icons.Default.VideocamOff
                else Icons.Default.Videocam,
                contentDescription = stringResource(R.string.cd_live_view_frame),
                tint = TextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isLiveViewActive) {
                        onStopLiveView()
                    } else {
                        onStartLiveView()
                    }
                },
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected)
                        MaterialTheme.colorScheme.primary
                    else
                        TextSecondary.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    if (isLiveViewActive)
                        stringResource(R.string.stop_live_view)
                    else
                        stringResource(R.string.start_live_view)
                )
            }
        } else {
            // 라이브뷰를 지원하지 않는 경우
            Icon(
                Icons.Default.VideocamOff,
                contentDescription = stringResource(R.string.cd_live_view_frame),
                tint = Error.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.liveview_not_supported),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.liveview_not_supported_detail),
                color = TextSecondary.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 카메라 연결 버튼들 — state+callback 패턴
 */
@Composable
fun CameraConnectionButtons(
    connectionState: CameraConnectionState,
    cameraFeed: List<Camera>,
    onConnectCamera: (String) -> Unit,
    onRefreshUsb: () -> Unit,
    onRequestUsbPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                // Try to reconnect or show camera list
                cameraFeed.firstOrNull()?.let { camera ->
                    onConnectCamera(camera.id)
                } ?: run {
                    // 카메라가 없으면 강제로 연결 시도
                    onConnectCamera("auto")
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(stringResource(R.string.retry_connection))
        }

        // USB 새로고침 버튼
        Button(
            onClick = onRefreshUsb,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.refresh_usb))
            }
        }

        // USB 권한 요청 버튼
        if (connectionState.usbDeviceCount > 0 && !connectionState.hasUsbPermission) {
            Button(
                onClick = onRequestUsbPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Warning
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = stringResource(R.string.cd_camera_status),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.request_usb_permission), color = TextPrimary)
                }
            }
        }
    }
}

@Preview(name = "Camera Preview - Connected", showBackground = true)
@Composable
private fun CameraPreviewConnectedPreview() {
    CamConTheme() {
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(Background)
        ) {
            CameraConnectedState(
                isConnected = true,
                isLiveViewActive = false,
                cameraCapabilities = com.inik.camcon.domain.model.CameraCapabilities(
                    model = "Canon EOS R5",
                    canLiveView = true,
                    canCapturePhoto = true,
                    canCaptureVideo = true,
                    canTriggerCapture = true,
                    supportsAutofocus = true,
                    supportsManualFocus = true,
                    supportsFocusPoint = true,
                    supportsBurstMode = true,
                    supportsTimelapse = true,
                    supportsBracketing = true,
                    supportsBulbMode = true,
                    canDownloadFiles = true,
                    canDeleteFiles = true,
                    canPreviewFiles = true,
                    availableIsoSettings = emptyList(),
                    availableShutterSpeeds = emptyList(),
                    availableApertures = emptyList(),
                    availableWhiteBalanceSettings = emptyList(),
                    supportsRemoteControl = true,
                    supportsConfigChange = true,
                    batteryLevel = 85
                ),
                onStartLiveView = {},
                onStopLiveView = {}
            )
        }
    }
}