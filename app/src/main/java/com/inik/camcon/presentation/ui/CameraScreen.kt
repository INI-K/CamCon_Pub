package com.inik.camcon.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.presentation.viewmodel.CameraViewModel

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel()
) {
    // StateFlow를 collectAsState()로 구독
    val cameraFeedState = viewModel.cameraFeed.collectAsState(initial = emptyList())


    Scaffold(
        topBar = {
            TopAppBar(title = { Text("CamCon") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Camera Feed:", style = MaterialTheme.typography.h6)
            cameraFeedState.value.forEach { camera ->
                Text(text = "ID: ${camera.id}, Name: ${camera.name}, Active: ${camera.isActive}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { viewModel.capturePhoto() }) {
                Text("Capture Photo")
            }
        }
    }
}