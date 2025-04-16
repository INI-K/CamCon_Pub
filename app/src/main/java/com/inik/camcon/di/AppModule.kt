package com.inik.camcon.di

import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.repository.CameraRepositoryImpl
import com.inik.camcon.domain.repository.CameraRepository
import com.inik.camcon.domain.usecase.CapturePhotoUseCase
import com.inik.camcon.domain.usecase.GetCameraFeedUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNativeCameraDataSource() = NativeCameraDataSource()

    @Provides
    @Singleton
    fun provideCameraRepository(
        nativeCameraDataSource: NativeCameraDataSource
    ): CameraRepository = CameraRepositoryImpl(nativeCameraDataSource)

    @Provides
    fun provideGetCameraFeedUseCase(repository: CameraRepository) =
        GetCameraFeedUseCase(repository)

    @Provides
    fun provideCapturePhotoUseCase(repository: CameraRepository) =
        CapturePhotoUseCase(repository)
}