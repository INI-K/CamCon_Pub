package com.inik.camcon.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.inik.camcon.CameraNative
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.CameraCapabilitiesManager
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.datasource.usb.UsbConnectionManager
import com.inik.camcon.data.datasource.usb.UsbDeviceDetector
import com.inik.camcon.data.network.ptpip.authentication.NikonAuthenticationService
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.data.network.ptpip.discovery.PtpipDiscoveryService
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import com.inik.camcon.data.service.AutoConnectManager
import com.inik.camcon.data.service.AutoConnectTaskRunner
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PtpDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @PtpDispatcher
    fun providePtpDispatcher(): CoroutineDispatcher =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "ptp-command-thread").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    @Provides
    @Singleton
    fun provideNativeConfigDataSource(
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): com.inik.camcon.data.datasource.nativesource.NativeConfigDataSource =
        com.inik.camcon.data.datasource.nativesource.NativeConfigDataSource(ioDispatcher)

    @Provides
    @Singleton
    fun provideNativeCameraDataSource(
        @ApplicationContext context: Context,
        cameraStateObserver: CameraStateObserver,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) = NativeCameraDataSource(context, cameraStateObserver, scope, ioDispatcher)

    @Provides
    @Singleton
    fun provideCameraNative(): CameraNative = CameraNative

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideUsbDeviceDetector(@ApplicationContext context: Context) =
        UsbDeviceDetector(context)

    @Provides
    @Singleton
    fun provideUsbConnectionManager(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) = UsbConnectionManager(context, scope, ioDispatcher)

    @Provides
    @Singleton
    fun provideCameraCapabilitiesManager(
        cameraStateObserver: CameraStateObserver,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) = CameraCapabilitiesManager(cameraStateObserver, scope, ioDispatcher)

    @Provides
    @Singleton
    fun provideUsbCameraManager(
        @ApplicationContext context: Context,
        deviceDetector: UsbDeviceDetector,
        connectionManager: UsbConnectionManager,
        capabilitiesManager: CameraCapabilitiesManager,
        @ApplicationScope scope: CoroutineScope
    ) = UsbCameraManager(context, deviceDetector, connectionManager, capabilitiesManager, scope)

    @Provides
    @Singleton
    fun providePtpipDiscoveryService(
        @ApplicationContext context: Context,
        wifiHelper: WifiNetworkHelper
    ) = PtpipDiscoveryService(context, wifiHelper)

    @Provides
    @Singleton
    fun providePtpipConnectionManager() = PtpipConnectionManager()

    @Provides
    @Singleton
    fun provideNikonAuthenticationService(
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) = NikonAuthenticationService(ioDispatcher)

    @Provides
    @Singleton
    fun provideWifiNetworkHelper(@ApplicationContext context: Context) =
        WifiNetworkHelper(context)

    @Provides
    @Singleton
    fun providePtpipDataSource(
        @ApplicationContext context: Context,
        discoveryService: PtpipDiscoveryService,
        connectionManager: PtpipConnectionManager,
        nikonAuthService: NikonAuthenticationService,
        wifiHelper: WifiNetworkHelper,
        cameraEventManager: CameraEventManager,
        cameraStateObserver: CameraStateObserver,
        photoDownloadManager: PhotoDownloadManager,
        autoConnectManager: AutoConnectManager,
        autoConnectTaskRunner: Lazy<AutoConnectTaskRunner>,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): PtpipDataSource {
        return PtpipDataSource(
            context,
            discoveryService,
            connectionManager,
            nikonAuthService,
            wifiHelper,
            cameraEventManager,
            cameraStateObserver,
            photoDownloadManager,
            autoConnectManager,
            autoConnectTaskRunner,
            scope,
            ioDispatcher
        )
    }

    @Provides
    @Singleton
    fun providePtpipPreferencesDataSource(@ApplicationContext context: Context) =
        PtpipPreferencesDataSource(context)

    @Provides
    @Singleton
    fun provideAppPreferencesDataSource(@ApplicationContext context: Context) =
        AppPreferencesDataSource(context)

    @Provides
    @Singleton
    fun provideCameraUiStateManager(): CameraUiStateManager = CameraUiStateManager()

    @Provides
    @Singleton
    fun provideCameraEventManager(
        nativeDataSource: NativeCameraDataSource,
        usbCameraManager: UsbCameraManager,
        validateImageFormatUseCase: ValidateImageFormatUseCase,
        photoDownloadManager: PhotoDownloadManager,
        errorHandlingManager: com.inik.camcon.domain.manager.ErrorHandlingManager,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): CameraEventManager =
        CameraEventManager(
            nativeDataSource,
            usbCameraManager,
            validateImageFormatUseCase,
            photoDownloadManager,
            errorHandlingManager,
            scope,
            ioDispatcher
        )

    @Provides
    @Singleton
    fun provideAutoConnectManager(
        ptpipPreferencesDataSource: PtpipPreferencesDataSource,
        wifiNetworkHelper: WifiNetworkHelper
    ): AutoConnectManager =
        AutoConnectManager(ptpipPreferencesDataSource, wifiNetworkHelper)

    @Provides
    @Singleton
    fun provideAutoConnectTaskRunner(
        ptpipDataSource: PtpipDataSource,
        autoConnectManager: AutoConnectManager,
        wifiNetworkHelper: WifiNetworkHelper
    ): AutoConnectTaskRunner =
        AutoConnectTaskRunner(ptpipDataSource, autoConnectManager, wifiNetworkHelper)
}
