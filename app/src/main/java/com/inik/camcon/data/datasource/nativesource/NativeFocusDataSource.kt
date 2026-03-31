package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.CameraNative
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeFocusDataSource @Inject constructor() {

    fun setAFMode(mode: String): Int = CameraNative.setAFMode(mode)

    fun getAFMode(): String? = CameraNative.getAFMode()

    fun setAFArea(x: Int, y: Int, width: Int, height: Int): Int =
        CameraNative.setAFArea(x, y, width, height)

    fun driveManualFocus(steps: Int): Int = CameraNative.driveManualFocus(steps)

    companion object {
        private const val TAG = "NativeFocusDS"
    }
}
