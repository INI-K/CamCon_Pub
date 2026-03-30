package com.inik.camcon.domain.manager

interface NativeErrorCallbackRegistrar {
    fun registerErrorCallback(onError: (errorCode: Int, errorMessage: String) -> Unit)
    fun unregisterErrorCallback()
}
