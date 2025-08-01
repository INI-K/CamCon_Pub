package com.inik.camcon.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inik.camcon.presentation.theme.CamConTheme
import com.inik.camcon.presentation.viewmodel.AppSettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

data class License(
    val name: String,
    val version: String,
    val license: String,
    val copyright: String,
    val url: String? = null
)

@AndroidEntryPoint
class OpenSourceLicensesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val themeMode by appSettingsViewModel.themeMode.collectAsState()

            CamConTheme(themeMode = themeMode) {
                OpenSourceLicensesScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun OpenSourceLicensesScreen(
    onBackClick: () -> Unit
) {
    val licenses = listOf(
        License(
            name = "Android Jetpack Compose",
            version = "1.5.4",
            license = "Apache License 2.0",
            copyright = "Copyright 2021 The Android Open Source Project",
            url = "https://developer.android.com/jetpack/compose"
        ),
        License(
            name = "Dagger Hilt",
            version = "2.49",
            license = "Apache License 2.0",
            copyright = "Copyright 2012 The Dagger Authors",
            url = "https://dagger.dev/hilt/"
        ),
        License(
            name = "Kotlin Coroutines",
            version = "1.7.3",
            license = "Apache License 2.0",
            copyright = "Copyright 2016-2021 JetBrains s.r.o. and Kotlin Programming Language contributors",
            url = "https://github.com/Kotlin/kotlinx.coroutines"
        ),
        License(
            name = "Firebase SDK",
            version = "33.4.0",
            license = "Apache License 2.0",
            copyright = "Copyright 2016 Google Inc.",
            url = "https://firebase.google.com/"
        ),
        License(
            name = "Google Play Services Auth",
            version = "21.0.0",
            license = "Android Software Development Kit License",
            copyright = "Copyright 2015 Google Inc.",
            url = "https://developers.google.com/android/guides/setup"
        ),
        License(
            name = "DataStore Preferences",
            version = "1.0.0",
            license = "Apache License 2.0",
            copyright = "Copyright 2020 The Android Open Source Project",
            url = "https://developer.android.com/topic/libraries/architecture/datastore"
        ),
        License(
            name = "Accompanist System UI Controller",
            version = "0.32.0",
            license = "Apache License 2.0",
            copyright = "Copyright 2021 Google LLC",
            url = "https://github.com/google/accompanist"
        ),
        License(
            name = "Coil Image Loading",
            version = "2.5.0",
            license = "Apache License 2.0",
            copyright = "Copyright 2023 Coil Contributors",
            url = "https://coil-kt.github.io/coil/"
        ),
        License(
            name = "ExifInterface",
            version = "1.4.1",
            license = "Apache License 2.0",
            copyright = "Copyright 2016 The Android Open Source Project",
            url = "https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface"
        ),
        License(
            name = "GPUImage for Android",
            version = "2.1.0",
            license = "Apache License 2.0",
            copyright = "Copyright 2018 CyberAgent, Inc.",
            url = "https://github.com/cats-oss/android-gpuimage"
        ),
        License(
            name = "ImageViewer by 0xZhangKe",
            version = "1.0.3",
            license = "Apache License 2.0",
            copyright = "Copyright 2021 0xZhangKe",
            url = "https://github.com/0xZhangKe/ImageViewer"
        ),
        License(
            name = "Navigation Compose",
            version = "2.7.7",
            license = "Apache License 2.0",
            copyright = "Copyright 2019 The Android Open Source Project",
            url = "https://developer.android.com/jetpack/compose/navigation"
        ),
        License(
            name = "Material Design Components",
            version = "1.7.0",
            license = "Apache License 2.0",
            copyright = "Copyright 2016 The Android Open Source Project",
            url = "https://material.io/develop/android"
        ),
        License(
            name = "Android Credentials API",
            version = "1.3.0",
            license = "Apache License 2.0",
            copyright = "Copyright 2023 The Android Open Source Project",
            url = "https://developer.android.com/training/sign-in/credman"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("오픈소스 라이선스") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "이 앱은 다음의 오픈소스 라이브러리들을 사용합니다:",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(licenses) { license ->
                LicenseItem(license = license)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "모든 오픈소스 라이브러리에 감사드립니다.",
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun LicenseItem(license: License) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = license.name,
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "버전: ${license.version}",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "라이선스: ${license.license}",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = license.copyright,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp)
            )

            license.url?.let { url ->
                Text(
                    text = url,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}