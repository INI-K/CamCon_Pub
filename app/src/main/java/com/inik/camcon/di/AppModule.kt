package com.inik.camcon.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNativeCameraDataSource(
        @ApplicationContext context: Context,
        uiStateManager: CameraUiStateManager
    ) = NativeCameraDataSource(context, uiStateManager)

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
    fun provideUsbConnectionManager(@ApplicationContext context: Context) =
        UsbConnectionManager(context)

    @Provides
    @Singleton
    fun provideCameraCapabilitiesManager(
        uiStateManager: CameraUiStateManager
    ) = CameraCapabilitiesManager(uiStateManager)

    @Provides
    @Singleton
    fun provideUsbCameraManager(
        @ApplicationContext context: Context,
        deviceDetector: UsbDeviceDetector,
        connectionManager: UsbConnectionManager,
        capabilitiesManager: CameraCapabilitiesManager
    ) = UsbCameraManager(context, deviceDetector, connectionManager, capabilitiesManager)

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
    fun provideNikonAuthenticationService() = NikonAuthenticationService()

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
        wifiHelper: WifiNetworkHelper
    ) = PtpipDataSource(context, discoveryService, connectionManager, nikonAuthService, wifiHelper)

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
    fun provideCameraConnectionGlobalManager(
        ptpipDataSource: PtpipDataSource,
        usbCameraManager: UsbCameraManager
    ) = CameraConnectionGlobalManager(ptpipDataSource, usbCameraManager)

    @Provides
    @Singleton
    fun provideCameraUiStateManager(): CameraUiStateManager = CameraUiStateManager()

//    @Provides
//    @Singleton
//    fun provideCameraDatabaseManager(@ApplicationContext context: Context) =
//        CameraDatabaseManager(context)
}
