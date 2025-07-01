import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.data.datasource.ptpip.PtpipCamera
import com.inik.camcon.data.datasource.ptpip.PtpipCameraInfo
import com.inik.camcon.data.datasource.ptpip.PtpipConnectionState
import com.inik.camcon.data.datasource.ptpip.WifiCapabilities
import com.inik.camcon.presentation.viewmodel.PtpipViewModel

/**
 * PTPIP Wi-Fi 카메라 연결 화면
 * 카메라 검색, 연결, 관리 기능 제공
 */
@Composable
fun PtpipConnectionScreen(
    onBackClick: () -> Unit,
    ptpipViewModel: PtpipViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 위치 권한 상태
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showPermissionDialog by remember { mutableStateOf(false) }

    // 권한 요청 launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (!isGranted) {
            showPermissionDialog = true
        }
    }

    // 상태 수집
    val connectionState by ptpipViewModel.connectionState.collectAsState()
    val discoveredCameras by ptpipViewModel.discoveredCameras.collectAsState()
    val isDiscovering by ptpipViewModel.isDiscovering.collectAsState()
    val isConnecting by ptpipViewModel.isConnecting.collectAsState()
    val errorMessage by ptpipViewModel.errorMessage.collectAsState()
    val selectedCamera by ptpipViewModel.selectedCamera.collectAsState()
    val cameraInfo by ptpipViewModel.cameraInfo.collectAsState()
    val isPtpipEnabled by ptpipViewModel.isPtpipEnabled.collectAsState(initial = false)

    // 에러 메시지 표시
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            ptpipViewModel.clearError()
        }
    }

    // 권한 다이얼로그
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("위치 권한 필요") },
            text = {
                Text("Wi-Fi 네트워크 이름을 표시하려면 위치 권한이 필요합니다.\n설정에서 직접 권한을 허용해주세요.")
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("확인")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi 카메라 연결") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { ptpipViewModel.discoverCameras() },
                        enabled = !isDiscovering && isPtpipEnabled
                    ) {
                        if (isDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colors.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                        }
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Wi-Fi 상태 카드
            WifiStatusCard(
                isWifiConnected = ptpipViewModel.isWifiConnected(),
                isPtpipEnabled = isPtpipEnabled,
                onEnablePtpip = { ptpipViewModel.setPtpipEnabled(true) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Wi-Fi 기능 정보 카드
            WifiCapabilitiesCard(
                wifiCapabilities = ptpipViewModel.getWifiCapabilities(),
                hasLocationPermission = hasLocationPermission,
                onRequestPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 연결 상태 카드
            if (connectionState != PtpipConnectionState.DISCONNECTED) {
                ConnectionStatusCard(
                    connectionState = connectionState,
                    selectedCamera = selectedCamera,
                    cameraInfo = cameraInfo,
                    onDisconnect = { ptpipViewModel.disconnect() },
                    onCapture = { ptpipViewModel.capturePhoto() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 카메라 목록
            CameraListSection(
                cameras = discoveredCameras,
                isDiscovering = isDiscovering,
                isConnecting = isConnecting,
                selectedCamera = selectedCamera,
                onCameraSelect = { ptpipViewModel.selectCamera(it) },
                onCameraConnect = { ptpipViewModel.connectToCamera(it) },
                onDiscoverCameras = { ptpipViewModel.discoverCameras() },
                isPtpipEnabled = isPtpipEnabled
            )
        }
    }
}

@Composable
private fun WifiStatusCard(
    isWifiConnected: Boolean,
    isPtpipEnabled: Boolean,
    onEnablePtpip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = null,
                tint = if (isWifiConnected) Color.Green else Color.Red,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isWifiConnected) "Wi-Fi 연결됨" else "Wi-Fi 연결 안됨",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isPtpipEnabled) {
                        "PTPIP 기능 활성화됨"
                    } else {
                        "PTPIP 기능을 활성화하세요"
                    },
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
            if (!isPtpipEnabled) {
                Button(onClick = onEnablePtpip) {
                    Text("활성화")
                }
            }
        }
    }
}

@Composable
private fun WifiCapabilitiesCard(
    wifiCapabilities: WifiCapabilities,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val wifiManager = remember {
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    val supported = remember {
        wifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Wi-Fi 기능 정보",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 네트워크 정보
            if (wifiCapabilities.isConnected) {
                wifiCapabilities.networkName?.let { name ->
                    if (hasLocationPermission) {
                        InfoRow(label = "연결된 네트워크", value = name)
                    } else {
                        InfoRow(
                            label = "연결된 네트워크",
                            value = "권한 필요",
                            valueColor = Color.Red
                        )
                    }
                } ?: run {
                    if (hasLocationPermission) {
                        InfoRow(
                            label = "연결된 네트워크",
                            value = "이름 없음",
                            valueColor = Color.Gray
                        )
                    } else {
                        InfoRow(
                            label = "연결된 네트워크",
                            value = "권한 필요",
                            valueColor = Color.Red
                        )
                    }
                }

                wifiCapabilities.linkSpeed?.let { speed ->
                    InfoRow(label = "링크 속도", value = "${speed}Mbps")
                }
                wifiCapabilities.frequency?.let { freq ->
                    InfoRow(label = "주파수", value = "${freq}MHz")
                }
            }

            // STA 동시 연결 지원 여부 (핵심 정보)
            InfoRow(
                label = "STA 동시 연결 지원",
                value = if (supported) "✅ 지원됨" else "❌ 지원되지 않음",
                valueColor = if (supported) Color.Green else Color.Red
            )

            // 안드로이드 버전 정보
            InfoRow(
                label = "Android 버전",
                value = "API ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})"
            )

            // 권한 관련 안내
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasLocationPermission) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "💡 Wi-Fi 네트워크 이름을 확인하려면 위치 권한이 필요합니다.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("위치 권한 허용")
                }
            }

            if (!wifiCapabilities.isStaConcurrencySupported && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 STA 동시 연결 기능은 Android 10 (API 29) 이상에서 지원됩니다.",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colors.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: PtpipConnectionState,
    selectedCamera: PtpipCamera?,
    cameraInfo: PtpipCameraInfo?,
    onDisconnect: () -> Unit,
    onCapture: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = when (connectionState) {
                        PtpipConnectionState.CONNECTED -> Color.Green
                        PtpipConnectionState.CONNECTING -> Color(0xFFFF9800)
                        PtpipConnectionState.ERROR -> Color.Red
                        else -> Color.Gray
                    },
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedCamera?.name ?: "카메라",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (connectionState) {
                            PtpipConnectionState.CONNECTED -> "연결됨 - ${selectedCamera?.ipAddress}"
                            PtpipConnectionState.CONNECTING -> "연결 중..."
                            PtpipConnectionState.ERROR -> "연결 오류"
                            else -> "연결 안됨"
                        },
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            if (cameraInfo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${cameraInfo.manufacturer} ${cameraInfo.model}",
                    style = MaterialTheme.typography.caption
                )
            }

            if (connectionState == PtpipConnectionState.CONNECTED) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCapture,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("촬영")
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("연결 해제")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraListSection(
    cameras: List<PtpipCamera>,
    isDiscovering: Boolean,
    isConnecting: Boolean,
    selectedCamera: PtpipCamera?,
    onCameraSelect: (PtpipCamera) -> Unit,
    onCameraConnect: (PtpipCamera) -> Unit,
    onDiscoverCameras: () -> Unit,
    isPtpipEnabled: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "발견된 카메라 (${cameras.size})",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onDiscoverCameras,
                enabled = !isDiscovering && isPtpipEnabled
            ) {
                Text("검색")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isDiscovering -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("카메라 검색 중...")
                    }
                }
            }

            cameras.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isPtpipEnabled) {
                                "PTPIP 카메라를 찾을 수 없습니다.\n검색 버튼을 눌러 다시 시도하세요."
                            } else {
                                "PTPIP 기능을 먼저 활성화하세요."
                            },
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cameras) { camera ->
                        CameraItem(
                            camera = camera,
                            isSelected = camera == selectedCamera,
                            isConnecting = isConnecting,
                            onSelect = { onCameraSelect(camera) },
                            onConnect = { onCameraConnect(camera) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraItem(
    camera: PtpipCamera,
    isSelected: Boolean,
    isConnecting: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = if (isSelected) 8.dp else 2.dp,
        backgroundColor = if (isSelected) {
            MaterialTheme.colors.primary.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colors.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = if (camera.isOnline) Color.Green else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${camera.ipAddress}:${camera.port}",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = if (camera.isOnline) "온라인" else "오프라인",
                    style = MaterialTheme.typography.caption,
                    color = if (camera.isOnline) Color.Green else Color.Red
                )
            }
            Button(
                onClick = onConnect,
                enabled = camera.isOnline && !isConnecting
            ) {
                if (isConnecting && isSelected) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colors.onPrimary
                    )
                } else {
                    Text("연결")
                }
            }
        }
    }
}