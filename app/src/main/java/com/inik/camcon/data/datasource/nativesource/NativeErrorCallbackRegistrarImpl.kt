package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.CameraNative
import com.inik.camcon.NativeErrorCallback
import com.inik.camcon.domain.manager.NativeErrorCallbackRegistrar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeErrorCallbackRegistrarImpl @Inject constructor() : NativeErrorCallbackRegistrar {
    override fun registerErrorCallback(onError: (errorCode: Int, errorMessage: String) -> Unit) {
        CameraNative.setErrorCallback(object : NativeErrorCallback {
            override fun onNativeError(errorCode: Int, errorMessage: String) {
                onError(errorCode, errorMessage)
            }
        })
    }

    override fun unregisterErrorCallback() {
        CameraNative.setErrorCallback(null)
    }
}
