package com.inik.camcon.domain.usecase.config

import com.inik.camcon.domain.model.config.CameraConfigTree
import com.inik.camcon.domain.model.config.ManufacturerSetting
import com.inik.camcon.domain.model.config.ManufacturerSettingQuery
import com.inik.camcon.domain.repository.CameraConfigRepository
import javax.inject.Inject

class GetConfigTreeUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(): Result<CameraConfigTree> = repository.getConfigTree()
}

class ListAllConfigsUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(): Result<List<String>> = repository.listAllConfigs()
}

class GetConfigValueUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(key: String): Result<String> = repository.getConfigValue(key)
}

class SetConfigValueUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(key: String, value: String): Result<Boolean> = repository.setConfigValue(key, value)
}

class GetConfigIntUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(key: String): Result<Int> = repository.getConfigInt(key)
}

class SetConfigIntUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(key: String, value: Int): Result<Boolean> = repository.setConfigInt(key, value)
}

class GetCameraManualUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(): Result<String> = repository.getCameraManual()
}

class GetCameraAboutUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(): Result<String> = repository.getCameraAbout()
}

class ReadGphotoSettingsUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(): Result<String> = repository.readGphotoSettings()
}

class SetManufacturerSettingUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(setting: ManufacturerSetting): Result<Boolean> =
        repository.setManufacturerSetting(setting)
}

class GetManufacturerSettingUseCase @Inject constructor(
    private val repository: CameraConfigRepository
) {
    suspend operator fun invoke(query: ManufacturerSettingQuery): Result<String> =
        repository.getManufacturerSetting(query)
}
