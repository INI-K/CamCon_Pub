package com.inik.camcon.presentation.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Top Controls Bar
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Camera Connection Status
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                cameraFeed.firstOrNull()?.name ?: "Connected"
                            } else {
                                "Not Connected"
                            },
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    
                    // Settings Button
                    IconButton(
                        onClick = { 
                            scope.launch { bottomSheetState.show() }
                        }
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            }
            
            // Live View / Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLiveViewActive && uiState.liveViewFrame != null) {
                    // Display live view frame
                    uiState.liveViewFrame?.let { frame ->
                        // Convert byte array to Bitmap and display
                        // This is a placeholder - actual implementation would convert the frame data
                        Text("Live View Active", color = Color.White)
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
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.primary
                            )
                        ) {
                            Text(if (uiState.isLiveViewActive) "Stop Live View" else "Start Live View")
                        }
                    }
                }
                
                // Camera Settings Overlay
                if (uiState.cameraSettings != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        CameraSettingChip("ISO ${uiState.cameraSettings.iso}")
                        Spacer(modifier = Modifier.width(8.dp))
                        CameraSettingChip(uiState.cameraSettings.shutterSpeed)
                        Spacer(modifier = Modifier.width(8.dp))
                        CameraSettingChip("f/${uiState.cameraSettings.aperture}")
                    }
                }
            }
            
            // Shooting Mode Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color.Black.copy(alpha = 0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column {
                    LazyRow(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(ShootingMode.values()) { mode ->
                            ShootingModeChip(
                                mode = mode,
                                isSelected = uiState.shootingMode == mode,
                                onClick = { viewModel.setShootingMode(mode) }
                            )
                        }
                    }
                    
                    // Capture Button Area
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Photo Gallery Button
                        IconButton(
                            onClick = { /* Navigate to gallery */ },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        // Main Capture Button
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .border(3.dp, Color.White, CircleShape)
                                .clickable(enabled = !uiState.isCapturing) {
                                    when (uiState.shootingMode) {
                                        ShootingMode.TIMELAPSE -> showTimelapseDialog = true
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
                                        .background(Color.White)
                                )
                            }
                        }
                        
                        // Focus Button
                        IconButton(
                            onClick = { /* Auto focus */ },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.CenterFocusStrong,
                                contentDescription = "Focus",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    
                    // Recent Captures Preview
                    if (uiState.capturedPhotos.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(uiState.capturedPhotos.take(10)) { photo ->
                                Card(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clickable { /* Open photo */ },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    // Thumbnail placeholder
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
            }
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
    onClick: () -> Unit
) {
    val displayName = when (mode) {
        ShootingMode.SINGLE -> "Single"
        ShootingMode.BURST -> "Burst"
        ShootingMode.TIMELAPSE -> "Timelapse"
        ShootingMode.BULB -> "Bulb"
        ShootingMode.HDR_BRACKET -> "HDR"
    }
    
    Chip(
        onClick = onClick,
        colors = ChipDefaults.chipColors(
            backgroundColor = if (isSelected) MaterialTheme.colors.primary 
                            else Color.Gray.copy(alpha = 0.3f),
            contentColor = Color.White
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
                "Camera Settings",
                style = MaterialTheme.typography.h6
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Settings would be dynamically loaded based on camera capabilities
        settings?.let {
            SettingRow("ISO", it.iso) { value ->
                onSettingChange("iso", value)
            }
            SettingRow("Shutter Speed", it.shutterSpeed) { value ->
                onSettingChange("shutterSpeed", value)
            }
            SettingRow("Aperture", it.aperture) { value ->
                onSettingChange("aperture", value)
            }
            SettingRow("White Balance", it.whiteBalance) { value ->
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
        title = { Text("Timelapse Settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text("Interval (seconds)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = totalShots,
                    onValueChange = { totalShots = it },
                    label = { Text("Total Shots") },
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
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
