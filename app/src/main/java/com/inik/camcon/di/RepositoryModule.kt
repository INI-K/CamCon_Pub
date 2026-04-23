package com.inik.camcon.di

import com.inik.camcon.data.datasource.billing.BillingDataSource
import com.inik.camcon.data.datasource.billing.BillingDataSourceImpl
import com.inik.camcon.data.datasource.nativesource.NativeErrorCallbackRegistrarImpl
import com.inik.camcon.data.datasource.remote.AuthRemoteDataSource
import com.inik.camcon.data.datasource.remote.AuthRemoteDataSourceImpl
import com.inik.camcon.data.repository.AppUpdateRepositoryImpl
import com.inik.camcon.data.repository.AuthRepositoryImpl
import com.inik.camcon.data.repository.CameraAdvancedCaptureRepositoryImpl
import com.inik.camcon.data.repository.CameraConfigRepositoryImpl
import com.inik.camcon.data.repository.CameraConnectionStateProviderImpl
import com.inik.camcon.data.repository.CameraDiagnosticsRepositoryImpl
import com.inik.camcon.data.repository.CameraFileRepositoryImpl
import com.inik.camcon.data.repository.CameraFocusRepositoryImpl
import com.inik.camcon.data.repository.CameraMockRepositoryImpl
import com.inik.camcon.data.repository.CameraRepositoryImpl
import com.inik.camcon.data.repository.CameraStreamingRepositoryImpl
import com.inik.camcon.data.repository.PtpipDebugRepositoryImpl
import com.inik.camcon.data.repository.PtpipPreferencesRepositoryImpl
import com.inik.camcon.data.repository.PtpipRepositoryImpl
import com.inik.camcon.data.repository.SubscriptionRepositoryImpl
import com.inik.camcon.data.repository.ColorTransferRepositoryImpl
import com.inik.camcon.data.repository.UsbDeviceRepositoryImpl
import com.inik.camcon.data.repository.managers.CameraConnectionGlobalManagerImpl
import com.inik.camcon.data.util.AndroidLogger
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.domain.manager.ErrorNotifier
import com.inik.camcon.domain.manager.NativeErrorCallbackRegistrar
import com.inik.camcon.presentation.viewmodel.state.ErrorHandlingManager
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.AppUpdateRepository
import com.inik.camcon.domain.repository.AuthRepository
import com.inik.camcon.domain.repository.CameraAdvancedCaptureRepository
import com.inik.camcon.domain.repository.CameraConfigRepository
import com.inik.camcon.domain.repository.CameraConnectionStateProvider
import com.inik.camcon.domain.repository.CameraDiagnosticsRepository
import com.inik.camcon.domain.repository.CameraFileRepository
import com.inik.camcon.domain.repository.CameraFocusRepository
import com.inik.camcon.domain.repository.CameraMockRepository
import com.inik.camcon.domain.repository.PtpipDebugRepository
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.domain.repository.CameraStreamingRepository
import com.inik.camcon.domain.repository.ColorTransferRepository
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.repository.SubscriptionRepository
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.util.Logger
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
import com.inik.camcon.presentation.viewmodel.CameraOperationsManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCameraRepository(
        cameraRepositoryImpl: CameraRepositoryImpl
    ): CameraRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindAuthRemoteDataSource(
        authRemoteDataSourceImpl: AuthRemoteDataSourceImpl
    ): AuthRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(
        appUpdateRepositoryImpl: AppUpdateRepositoryImpl
    ): AppUpdateRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        subscriptionRepositoryImpl: SubscriptionRepositoryImpl
    ): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindBillingDataSource(
        billingDataSourceImpl: BillingDataSourceImpl
    ): BillingDataSource

    @Binds
    @Singleton
    abstract fun bindCameraConnectionGlobalManager(
        impl: CameraConnectionGlobalManagerImpl
    ): CameraConnectionGlobalManager

    @Binds
    @Singleton
    abstract fun bindCameraConnectionStateProvider(
        impl: CameraConnectionStateProviderImpl
    ): CameraConnectionStateProvider

    @Binds
    @Singleton
    abstract fun bindUsbDeviceRepository(
        impl: UsbDeviceRepositoryImpl
    ): UsbDeviceRepository

    @Binds
    @Singleton
    abstract fun bindNativeErrorCallbackRegistrar(
        impl: NativeErrorCallbackRegistrarImpl
    ): NativeErrorCallbackRegistrar

    @Binds
    @Singleton
    abstract fun bindColorTransferRepository(
        impl: ColorTransferRepositoryImpl
    ): ColorTransferRepository

    @Binds
    @Singleton
    abstract fun bindLogger(
        impl: AndroidLogger
    ): Logger

    @Binds
    @Singleton
    abstract fun bindCameraStateObserver(
        impl: CameraUiStateManager
    ): CameraStateObserver

    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(
        impl: AppPreferencesDataSource
    ): AppSettingsRepository

    @Binds
    abstract fun bindAdvancedCaptureRepository(
        impl: CameraAdvancedCaptureRepositoryImpl
    ): CameraAdvancedCaptureRepository

    @Binds
    abstract fun bindFocusRepository(
        impl: CameraFocusRepositoryImpl
    ): CameraFocusRepository

    @Binds
    abstract fun bindFileRepository(
        impl: CameraFileRepositoryImpl
    ): CameraFileRepository

    @Binds
    abstract fun bindConfigRepository(
        impl: CameraConfigRepositoryImpl
    ): CameraConfigRepository

    @Binds
    abstract fun bindStreamingRepository(
        impl: CameraStreamingRepositoryImpl
    ): CameraStreamingRepository

    @Binds
    abstract fun bindDiagnosticsRepository(
        impl: CameraDiagnosticsRepositoryImpl
    ): CameraDiagnosticsRepository

    @Binds
    abstract fun bindMockRepository(
        impl: CameraMockRepositoryImpl
    ): CameraMockRepository

    @Binds
    @Singleton
    abstract fun bindPtpipRepository(
        impl: PtpipRepositoryImpl
    ): PtpipRepository

    @Binds
    @Singleton
    abstract fun bindPtpipPreferencesRepository(
        impl: PtpipPreferencesRepositoryImpl
    ): PtpipPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindPtpipDebugRepository(
        impl: PtpipDebugRepositoryImpl
    ): PtpipDebugRepository

    @Binds
    @Singleton
    abstract fun bindErrorNotifier(
        impl: ErrorHandlingManager
    ): ErrorNotifier

}
