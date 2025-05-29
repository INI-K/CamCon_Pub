package com.inik.camcon.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.inik.camcon.data.datasource.camera.CameraDatabaseManager
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
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
    fun provideNativeCameraDataSource() = NativeCameraDataSource()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideUsbCameraManager(@ApplicationContext context: Context) = UsbCameraManager(context)

    @Provides
    @Singleton
    fun provideCameraDatabaseManager(@ApplicationContext context: Context) =
        CameraDatabaseManager(context)
}
