package com.inik.camcon.di

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.inik.camcon.CameraNative
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.CameraCapabilitiesManager
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.datasource.usb.UsbConnectionManager
import com.inik.camcon.data.datasource.usb.UsbDeviceDetector
import com.inik.camcon.data.network.ptpip.PtpipTetherService
import com.inik.camcon.data.network.ptpip.authentication.NikonAuthenticationService
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.data.network.ptpip.discovery.PtpipDiscoveryService
import com.inik.camcon.data.network.ptpip.discovery.SsdpDiscoveryService
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.data.service.AutoConnectManager
import com.inik.camcon.data.service.AutoConnectTaskRunner
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
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
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PtpDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        // 앱 전역 scope에서 처리되지 않은 예외가 조용히 사라지지 않도록 핸들러로 로깅한다.
        // SupervisorJob이라 한 자식의 실패가 형제·scope를 취소하지 않는다.
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e("ApplicationScope", "앱 스코프 코루틴에서 처리되지 않은 예외", throwable)
        }
        return CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

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
        cameraStateObserver: CameraStateObserver,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) = NativeCameraDataSource(cameraStateObserver, scope, ioDispatcher)

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
    fun provideFirebaseFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance("asia-northeast3")

    @Provides
    @Singleton
    fun provideUsbDeviceDetector(@ApplicationContext context: Context) =
        UsbDeviceDetector(context)

    @Provides
    @Singleton
    fun provideUsbConnectionManager(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        libgphoto2PluginInstaller: com.inik.camcon.data.datasource.Libgphoto2PluginInstaller,
        errorNotifier: com.inik.camcon.domain.manager.ErrorNotifier
    ) = UsbConnectionManager(context, scope, ioDispatcher, libgphoto2PluginInstaller, errorNotifier)

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
        wifiHelper: WifiNetworkHelper,
        ssdpDiscoveryService: SsdpDiscoveryService
    ) = PtpipDiscoveryService(context, wifiHelper, ssdpDiscoveryService)

    @Provides
    @Singleton
    fun providePtpipConnectionManager(
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) = PtpipConnectionManager(ioDispatcher)

    @Provides
    @Singleton
    fun provideNikonAuthenticationService(
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) = NikonAuthenticationService(ioDispatcher)

    @Provides
    @Singleton
    fun provideWifiNetworkHelper(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) = WifiNetworkHelper(context, ioDispatcher)

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
        ptpipPreferencesDataSource: PtpipPreferencesDataSource,
        tetherService: PtpipTetherService,
        nativeCameraDataSource: com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource,
        libgphoto2PluginInstaller: com.inik.camcon.data.datasource.Libgphoto2PluginInstaller,
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
            ptpipPreferencesDataSource,
            tetherService,
            nativeCameraDataSource,
            libgphoto2PluginInstaller,
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
    fun provideEncryptedAppPreferences(@ApplicationContext context: Context) =
        com.inik.camcon.data.datasource.local.EncryptedAppPreferences(context)

    @Provides
    @Singleton
    fun provideAppPreferencesDataSource(
        @ApplicationContext context: Context,
        encryptedPrefs: com.inik.camcon.data.datasource.local.EncryptedAppPreferences,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) = AppPreferencesDataSource(context, encryptedPrefs, ioDispatcher)

    @Provides
    @Singleton
    fun provideCameraEventManager(
        @ApplicationContext context: Context,
        nativeDataSource: NativeCameraDataSource,
        usbCameraManager: UsbCameraManager,
        validateImageFormatUseCase: ValidateImageFormatUseCase,
        photoDownloadManager: PhotoDownloadManager,
        transferProgressTracker: com.inik.camcon.data.repository.managers.TransferProgressTracker,
        errorHandlingManager: com.inik.camcon.domain.manager.ErrorNotifier,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @MainDispatcher mainDispatcher: CoroutineDispatcher
    ): CameraEventManager =
        CameraEventManager(
            context,
            nativeDataSource,
            usbCameraManager,
            validateImageFormatUseCase,
            photoDownloadManager,
            transferProgressTracker,
            errorHandlingManager,
            scope,
            ioDispatcher,
            mainDispatcher
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
        wifiNetworkHelper: WifiNetworkHelper,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): AutoConnectTaskRunner =
        AutoConnectTaskRunner(ptpipDataSource, autoConnectManager, wifiNetworkHelper, ioDispatcher)
}
