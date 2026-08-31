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
import com.inik.camcon.data.repository.CameraRepositoryImpl
import com.inik.camcon.data.repository.CameraStreamingRepositoryImpl
import com.inik.camcon.data.repository.ConnectionReportRepositoryImpl
import com.inik.camcon.data.repository.PtpipDebugRepositoryImpl
import com.inik.camcon.data.repository.PtpipPreferencesRepositoryImpl
import com.inik.camcon.data.repository.PtpipRepositoryImpl
import com.inik.camcon.data.repository.SubscriptionRepositoryImpl
import com.inik.camcon.data.repository.ColorTransferRepositoryImpl
import com.inik.camcon.data.repository.FilmLutRepositoryImpl
import com.inik.camcon.data.processor.FilmEditProcessorImpl
import com.inik.camcon.data.repository.UsbDeviceRepositoryImpl
import com.inik.camcon.data.repository.managers.CameraConnectionGlobalManagerImpl
import com.inik.camcon.data.network.ptpip.wifi.WifiNetworkHelper
import com.inik.camcon.data.activity.ActivityProviderImpl
import com.inik.camcon.data.util.AndroidLogger
import com.inik.camcon.data.activity.ActivityProvider
import com.inik.camcon.domain.manager.CameraConnectionGlobalManager
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.domain.manager.ErrorNotifier
import com.inik.camcon.data.manager.UnattendedSessionManagerImpl
import com.inik.camcon.domain.manager.UnattendedSessionManager
import com.inik.camcon.domain.manager.NativeErrorCallbackRegistrar
import com.inik.camcon.presentation.viewmodel.state.ErrorHandlingManager
import com.inik.camcon.domain.repository.AppSettingsRepository
import com.inik.camcon.domain.repository.FilmLutRepository
import com.inik.camcon.domain.repository.FilmEditProcessor
import com.inik.camcon.domain.repository.AppUpdateRepository
import com.inik.camcon.domain.repository.AuthRepository
import com.inik.camcon.domain.repository.CameraAdvancedCaptureRepository
import com.inik.camcon.domain.repository.CameraConfigRepository
import com.inik.camcon.domain.repository.CameraConnectionStateProvider
import com.inik.camcon.domain.repository.CameraDiagnosticsRepository
import com.inik.camcon.domain.repository.CameraFileRepository
import com.inik.camcon.domain.repository.CameraFocusRepository
import com.inik.camcon.domain.repository.ConnectionReportRepository
import com.inik.camcon.domain.repository.PtpipDebugRepository
import com.inik.camcon.domain.repository.PtpipPreferencesRepository
import com.inik.camcon.domain.repository.PtpipRepository
import com.inik.camcon.domain.repository.WifiCapabilityProvider
import com.inik.camcon.domain.repository.CameraStreamingRepository
import com.inik.camcon.domain.repository.ColorTransferRepository
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.repository.SubscriptionRepository
import com.inik.camcon.domain.repository.UnattendedSessionRepository
import com.inik.camcon.domain.repository.UsbDeviceRepository
import com.inik.camcon.domain.util.Logger
import com.inik.camcon.presentation.viewmodel.state.CameraUiStateManager
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
    abstract fun bindActivityProvider(
        impl: ActivityProviderImpl
    ): ActivityProvider

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
    abstract fun bindFilmLutRepository(
        impl: FilmLutRepositoryImpl
    ): FilmLutRepository

    @Binds
    @Singleton
    abstract fun bindFilmEditProcessor(
        impl: FilmEditProcessorImpl
    ): FilmEditProcessor

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
    @Singleton
    abstract fun bindAdvancedCaptureRepository(
        impl: CameraAdvancedCaptureRepositoryImpl
    ): CameraAdvancedCaptureRepository

    @Binds
    @Singleton
    abstract fun bindFocusRepository(
        impl: CameraFocusRepositoryImpl
    ): CameraFocusRepository

    @Binds
    @Singleton
    abstract fun bindFileRepository(
        impl: CameraFileRepositoryImpl
    ): CameraFileRepository

    @Binds
    @Singleton
    abstract fun bindConfigRepository(
        impl: CameraConfigRepositoryImpl
    ): CameraConfigRepository

    @Binds
    @Singleton
    abstract fun bindStreamingRepository(
        impl: CameraStreamingRepositoryImpl
    ): CameraStreamingRepository

    @Binds
    @Singleton
    abstract fun bindDiagnosticsRepository(
        impl: CameraDiagnosticsRepositoryImpl
    ): CameraDiagnosticsRepository

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

    /**
     * 무인 수신 세션 매니저. 서비스(FGS·WakeLock)의 수명이 이 상태를 따른다.
     *
     * 도메인 포트에 바인딩해 서비스·UseCase 가 구현체를 모르게 한다(C1 규약).
     */
    @Binds
    @Singleton
    abstract fun bindUnattendedSessionManager(
        impl: UnattendedSessionManagerImpl
    ): UnattendedSessionManager

    /**
     * 무인 수신 세션 영속 플래그. 평문 DataStore 를 이미 소유한 [AppPreferencesDataSource] 가
     * 그대로 구현한다 — 같은 저장소를 두 클래스가 나눠 갖게 하면 키 관리 지점이 둘로 갈린다.
     */
    @Binds
    @Singleton
    abstract fun bindUnattendedSessionRepository(
        impl: AppPreferencesDataSource
    ): UnattendedSessionRepository

    @Binds
    @Singleton
    abstract fun bindWifiCapabilityProvider(
        impl: WifiNetworkHelper
    ): WifiCapabilityProvider

    @Binds
    @Singleton
    abstract fun bindConnectionReportRepository(
        impl: ConnectionReportRepositoryImpl
    ): ConnectionReportRepository

}
