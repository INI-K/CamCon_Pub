package com.inik.camcon.di

import com.inik.camcon.data.datasource.remote.AuthRemoteDataSource
import com.inik.camcon.data.datasource.remote.AuthRemoteDataSourceImpl
import com.inik.camcon.data.repository.AppUpdateRepositoryImpl
import com.inik.camcon.data.repository.AuthRepositoryImpl
import com.inik.camcon.data.repository.CameraRepositoryImpl
import com.inik.camcon.domain.repository.AppUpdateRepository
import com.inik.camcon.domain.repository.AuthRepository
import com.inik.camcon.domain.repository.CameraRepository
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
}
