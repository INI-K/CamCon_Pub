package com.inik.camcon.domain.repository

import com.inik.camcon.domain.model.config.CameraConfigTree
import com.inik.camcon.domain.model.config.ManufacturerSetting
import com.inik.camcon.domain.model.config.ManufacturerSettingQuery

interface CameraConfigRepository {
    suspend fun getConfigTree(): Result<CameraConfigTree>
    suspend fun listAllConfigs(): Result<List<String>>
    suspend fun getConfigValue(key: String): Result<String>
    suspend fun setConfigValue(key: String, value: String): Result<Boolean>
    suspend fun getConfigInt(key: String): Result<Int>
    suspend fun setConfigInt(key: String, value: Int): Result<Boolean>
    suspend fun getCameraManual(): Result<String>
    suspend fun getCameraAbout(): Result<String>
    suspend fun readGphotoSettings(): Result<String>
    suspend fun setManufacturerSetting(setting: ManufacturerSetting): Result<Boolean>
    suspend fun getManufacturerSetting(query: ManufacturerSettingQuery): Result<String>
}
