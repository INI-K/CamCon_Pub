package com.inik.camcon.presentation.viewmodel

import com.inik.camcon.domain.model.focus.FocusConfig
import com.inik.camcon.domain.usecase.focus.*
import com.inik.camcon.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraFocusManager @Inject constructor(
    private val setAFModeUseCase: SetAFModeUseCase,
    private val getAFModeUseCase: GetAFModeUseCase,
    private val setAFAreaUseCase: SetAFAreaUseCase,
    private val driveManualFocusUseCase: DriveManualFocusUseCase,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _focusConfig = MutableStateFlow<FocusConfig?>(null)
    val focusConfig: StateFlow<FocusConfig?> = _focusConfig.asStateFlow()

    private val _isFocusDriving = MutableStateFlow(false)
    val isFocusDriving: StateFlow<Boolean> = _isFocusDriving.asStateFlow()

    fun setAFMode(mode: String) {
        scope.launch {
            setAFModeUseCase(mode).onSuccess {
                refreshAFMode()
            }
        }
    }

    fun refreshAFMode() {
        scope.launch {
            getAFModeUseCase().onSuccess { mode ->
                _focusConfig.value = FocusConfig(mode = mode)
            }
        }
    }

    fun setAFArea(x: Int, y: Int, width: Int, height: Int) {
        scope.launch {
            setAFAreaUseCase(x, y, width, height)
        }
    }

    fun driveManualFocus(steps: Int) {
        scope.launch {
            _isFocusDriving.value = true
            driveManualFocusUseCase(steps)
            _isFocusDriving.value = false
        }
    }
}
