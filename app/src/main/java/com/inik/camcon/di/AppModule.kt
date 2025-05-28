package com.inik.camcon.di

import com.google.firebase.auth.FirebaseAuth
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
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
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
}
