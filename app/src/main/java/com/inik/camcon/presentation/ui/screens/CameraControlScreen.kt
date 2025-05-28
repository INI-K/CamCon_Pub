package com.inik.camcon.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.R
import com.inik.camcon.domain.model.ShootingMode
import com.inik.camcon.presentation.viewmodel.CameraViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CameraControlScreen(
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraFeed by viewModel.cameraFeed.collectAsState()
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    var showTimelapseDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    
    ModalBottomSheetLayout(
        sheetState = bottomSheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetContent = {
            CameraSettingsSheet(
                settings = uiState.cameraSettings,
                onSettingChange = { key, value ->
                    viewModel.updateCameraSetting(key, value)
                },
                onClose = {
                    scope.launch { bottomSheetState.hide() }
                }
            )
        }
    ) {
        if (isLandscape) {
            LandscapeCameraLayout(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel,
                scope = scope,
                bottomSheetState = bottomSheetState,
                onShowTimelapseDialog = { showTimelapseDialog = true }
            )
        } else {
            PortraitCameraLayout(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel,
                scope = scope,
                bottomSheetState = bottomSheetState,
                onShowTimelapseDialog = { showTimelapseDialog = true }
            )
        }
    }

    // Timelapse Settings Dialog
    if (showTimelapseDialog) {
        TimelapseSettingsDialog(
            onConfirm = { interval, shots ->
                viewModel.startTimelapse(interval, shots)
                showTimelapseDialog = false
            },
            onDismiss = { showTimelapseDialog = false }
        )
    }

    // Error handling
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error snackbar
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LandscapeCameraLayout(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>,
    viewModel: CameraViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    bottomSheetState: ModalBottomSheetState,
    onShowTimelapseDialog: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Left side - Live View Area (takes most space)
        Box(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CameraPreviewArea(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel
            )

            // Top Controls Overlay
            TopControlsBar(
                uiState = uiState,
                cameraFeed = cameraFeed,
                onSettingsClick = { scope.launch { bottomSheetState.show() } },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Camera Settings Overlay
            CameraSettingsOverlay(
                settings = uiState.cameraSettings,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
            )
        }

        // Right side - Controls
        Surface(
            color = Color.Black.copy(alpha = 0.9f),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Shooting Mode Selector
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ShootingMode.values()) { mode ->
                        ShootingModeChip(
                            mode = mode,
                            isSelected = uiState.shootingMode == mode,
                            isEnabled = uiState.isConnected,
                            onClick = { viewModel.setShootingMode(mode) }
                        )
                    }
                }

                // Capture Controls
                CaptureControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowTimelapseDialog = onShowTimelapseDialog,
                    isVertical = true
                )

                // Recent Captures
                if (uiState.capturedPhotos.isNotEmpty()) {
                    RecentCapturesGrid(
                        photos = uiState.capturedPhotos,
                        isVertical = true
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PortraitCameraLayout(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>,
    viewModel: CameraViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    bottomSheetState: ModalBottomSheetState,
    onShowTimelapseDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Controls Bar
        TopControlsBar(
            uiState = uiState,
            cameraFeed = cameraFeed,
            onSettingsClick = { scope.launch { bottomSheetState.show() } }
        )

        // Live View / Preview Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CameraPreviewArea(
                uiState = uiState,
                cameraFeed = cameraFeed,
                viewModel = viewModel
            )

            // Camera Settings Overlay
            CameraSettingsOverlay(
                settings = uiState.cameraSettings,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Bottom Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color.Black.copy(alpha = 0.9f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column {
                // Shooting Mode Selector
                LazyRow(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(ShootingMode.values()) { mode ->
                        ShootingModeChip(
                            mode = mode,
                            isSelected = uiState.shootingMode == mode,
                            isEnabled = uiState.isConnected,
                            onClick = { viewModel.setShootingMode(mode) }
                        )
                    }
                }

                // Capture Controls
                CaptureControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShowTimelapseDialog = onShowTimelapseDialog,
                    isVertical = false
                )

                // Recent Captures
                if (uiState.capturedPhotos.isNotEmpty()) {
                    RecentCapturesGrid(
                        photos = uiState.capturedPhotos,
                        isVertical = false
                    )
                }
            }
        }
    }
}

@Composable
fun TopControlsBar(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.7f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera Connection Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        if (uiState.isConnected)
                            Color.Green.copy(alpha = 0.2f)
                        else
                            Color.Red.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.isConnected) Color.Green else Color.Red
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isConnected) {
                        cameraFeed.firstOrNull()?.name ?: stringResource(R.string.camera_connected)
                    } else {
                        stringResource(R.string.camera_disconnected)
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Settings Button
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun CameraPreviewArea(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    cameraFeed: List<com.inik.camcon.domain.model.Camera>,
    viewModel: CameraViewModel
) {
    if (uiState.isLiveViewActive && uiState.liveViewFrame != null) {
        // Display live view frame
        uiState.liveViewFrame?.let { frame ->
            // Convert byte array to Bitmap and display
            // This is a placeholder - actual implementation would convert the frame data
            Text(stringResource(R.string.live_view_active), color = Color.White)
        }
    } else if (!uiState.isConnected) {
        // Camera not connected state
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.UsbOff,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.camera_not_connected),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.connect_camera_usb),
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    // Try to reconnect or show camera list
                    cameraFeed.firstOrNull()?.let { camera ->
                        viewModel.connectCamera(camera.id)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text(stringResource(R.string.retry_connection))
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                if (uiState.isLiveViewActive) Icons.Default.VideocamOff
                else Icons.Default.Videocam,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (uiState.isLiveViewActive) {
                        viewModel.stopLiveView()
                    } else {
                        viewModel.startLiveView()
                    }
                },
                enabled = uiState.isConnected,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (uiState.isConnected)
                        MaterialTheme.colors.primary
                    else
                        Color.Gray.copy(alpha = 0.5f),
                    disabledBackgroundColor = Color.Gray.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    if (uiState.isLiveViewActive)
                        stringResource(R.string.stop_live_view)
                    else
                        stringResource(R.string.start_live_view)
                )
            }
        }
    }
}

@Composable
fun CameraSettingsOverlay(
    settings: com.inik.camcon.domain.model.CameraSettings?,
    modifier: Modifier = Modifier
) {
    settings?.let { settings ->
        Row(
            modifier = modifier
                .padding(16.dp)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            CameraSettingChip("ISO ${settings.iso}")
            Spacer(modifier = Modifier.width(8.dp))
            CameraSettingChip(settings.shutterSpeed)
            Spacer(modifier = Modifier.width(8.dp))
            CameraSettingChip("f/${settings.aperture}")
        }
    }
}

@Composable
fun CaptureControls(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    viewModel: CameraViewModel,
    onShowTimelapseDialog: () -> Unit,
    isVertical: Boolean
) {
    if (isVertical) {
        // Vertical layout for landscape mode
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CaptureControlsContent(
                uiState = uiState,
                viewModel = viewModel,
                onShowTimelapseDialog = onShowTimelapseDialog,
                isVertical = true
            )
        }
    } else {
        // Horizontal layout for portrait mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CaptureControlsContent(
                uiState = uiState,
                viewModel = viewModel,
                onShowTimelapseDialog = onShowTimelapseDialog,
                isVertical = false
            )
        }
    }
}

@Composable
fun CaptureControlsContent(
    uiState: com.inik.camcon.presentation.viewmodel.CameraUiState,
    viewModel: CameraViewModel,
    onShowTimelapseDialog: () -> Unit,
    isVertical: Boolean
) {
    // Photo Gallery Button
    IconButton(
        onClick = { /* Navigate to gallery */ },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = stringResource(R.string.gallery),
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }

    // Main Capture Button
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(
                3.dp,
                if (uiState.isConnected) Color.White else Color.Gray,
                CircleShape
            )
            .clickable(
                enabled = uiState.isConnected && !uiState.isCapturing
            ) {
                when (uiState.shootingMode) {
                    ShootingMode.TIMELAPSE -> onShowTimelapseDialog()
                    else -> viewModel.capturePhoto()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isCapturing) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(60.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (uiState.isConnected) Color.White else Color.Gray
                    )
            )
        }
    }

    // Focus Button
    IconButton(
        onClick = { /* Auto focus */ },
        enabled = uiState.isConnected,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            Icons.Default.CenterFocusStrong,
            contentDescription = stringResource(R.string.focus),
            tint = if (uiState.isConnected) Color.White else Color.Gray,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun RecentCapturesGrid(
    photos: List<com.inik.camcon.domain.model.CapturedPhoto>,
    isVertical: Boolean
) {
    if (isVertical) {
        // Vertical grid for landscape mode
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            photos.take(6).chunked(2).forEach { rowPhotos ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowPhotos.forEach { photo ->
                        Card(
                            modifier = Modifier
                                .size(60.dp)
                                .clickable { /* Open photo */ },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.DarkGray)
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Horizontal list for portrait mode
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(photos.take(10)) { photo ->
                Card(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable { /* Open photo */ },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray)
                    )
                }
            }
        }
    }
}

@Composable
fun CameraSettingChip(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun ShootingModeChip(
    mode: ShootingMode,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val displayName = when (mode) {
        ShootingMode.SINGLE -> stringResource(R.string.single_shot)
        ShootingMode.BURST -> stringResource(R.string.burst_mode)
        ShootingMode.TIMELAPSE -> stringResource(R.string.timelapse)
        ShootingMode.BULB -> stringResource(R.string.bulb_mode)
        ShootingMode.HDR_BRACKET -> stringResource(R.string.hdr_bracket)
    }
    
    Chip(
        onClick = onClick,
        enabled = isEnabled,
        colors = ChipDefaults.chipColors(
            backgroundColor = if (isSelected && isEnabled) MaterialTheme.colors.primary
            else if (isEnabled) Color.Gray.copy(alpha = 0.3f)
            else Color.Gray.copy(alpha = 0.1f),
            contentColor = if (isEnabled) Color.White else Color.Gray,
            disabledBackgroundColor = Color.Gray.copy(alpha = 0.1f),
            disabledContentColor = Color.Gray
        )
    ) {
        Text(displayName, fontSize = 14.sp)
    }
}

@Composable
fun CameraSettingsSheet(
    settings: com.inik.camcon.domain.model.CameraSettings?,
    onSettingChange: (String, String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.camera_settings),
                style = MaterialTheme.typography.h6
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Settings would be dynamically loaded based on camera capabilities
        settings?.let {
            SettingRow(stringResource(R.string.iso), it.iso) { value ->
                onSettingChange("iso", value)
            }
            SettingRow(stringResource(R.string.shutter_speed), it.shutterSpeed) { value ->
                onSettingChange("shutterSpeed", value)
            }
            SettingRow(stringResource(R.string.aperture), it.aperture) { value ->
                onSettingChange("aperture", value)
            }
            SettingRow(stringResource(R.string.white_balance), it.whiteBalance) { value ->
                onSettingChange("whiteBalance", value)
            }
        }
    }
}

@Composable
fun SettingRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        // In real app, this would be a dropdown or appropriate control
        Text(
            value,
            color = MaterialTheme.colors.primary,
            modifier = Modifier.clickable { /* Show options */ }
        )
    }
}

@Composable
fun TimelapseSettingsDialog(
    onConfirm: (interval: Int, shots: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var interval by remember { mutableStateOf("5") }
    var totalShots by remember { mutableStateOf("100") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timelapse_settings)) },
        text = {
            Column {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text(stringResource(R.string.interval_seconds)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = totalShots,
                    onValueChange = { totalShots = it },
                    label = { Text(stringResource(R.string.total_shots)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        interval.toIntOrNull() ?: 5,
                        totalShots.toIntOrNull() ?: 100
                    )
                }
            ) {
                Text(stringResource(R.string.start_timelapse))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
