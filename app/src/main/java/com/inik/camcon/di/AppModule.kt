package com.inik.camcon.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.inik.camcon.data.datasource.local.PtpipPreferencesDataSource
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.network.ptpip.authentication.NikonAuthenticationService
import com.inik.camcon.data.network.ptpip.connection.PtpipConnectionManager
import com.inik.camcon.data.network.ptpip.discovery.PtpipDiscoveryService
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
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
    fun provideNativeCameraDataSource(@ApplicationContext context: Context) =
        NativeCameraDataSource(context)

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideUsbCameraManager(@ApplicationContext context: Context) = UsbCameraManager(context)

    @Provides
    @Singleton
    fun providePtpipDiscoveryService(@ApplicationContext context: Context) =
        PtpipDiscoveryService(context)

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

//    @Provides
//    @Singleton
//    fun provideCameraDatabaseManager(@ApplicationContext context: Context) =
//        CameraDatabaseManager(context)
}
