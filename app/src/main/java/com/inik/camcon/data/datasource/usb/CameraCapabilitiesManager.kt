package com.inik.camcon.data.datasource.usb

import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.manager.CameraStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 기능 정보 관리를 담당하는 클래스
 */
@Singleton
class CameraCapabilitiesManager @Inject constructor(
    private val cameraStateObserver: CameraStateObserver
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _cameraCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    val cameraCapabilities: StateFlow<CameraCapabilities?> = _cameraCapabilities.asStateFlow()

    // 캐시 관리
    private var cachedCapabilities: CameraCapabilities? = null
    private var lastCapabilitiesFetch = 0L
    private val capabilitiesCacheTimeout = 30000L // 30초간 캐시 유효

    // 마스터 데이터 캐시
    private var masterCameraAbilities: String? = null
    private var masterWidgetJson: String? = null
    private var lastMasterFetch = 0L
    private val masterCacheTimeout = 300000L // 5분간 유효 (이전: 1분)
    private var isFetchingMasterData = false

    // 요약 정보 캐시
    private var cachedSummary: String? = null
    private var lastSummaryFetch = 0L
    private val summaryCacheTimeout = 300000L // 5분간 유효

    // 중복 호출 방지 플래그
    private var isFetchingCapabilities = false

    companion object {
        private const val TAG = "카메라기능관리자"
    }

    /**
     * 카메라 기능 정보를 가져옵니다 (캐시 우선)
     */
    suspend fun fetchCameraCapabilities() = withContext(Dispatchers.IO) {
        // 이미 가져오는 중이면 건너뛰기
        if (isFetchingCapabilities) {
            Log.d(TAG, "카메라 기능 정보 가져오기 중복 호출 방지")
            return@withContext
        }

        // 캐시된 결과가 있고 아직 유효하면 캐시 반환
        val now = System.currentTimeMillis()
        cachedCapabilities?.let { cached ->
            if (now - lastCapabilitiesFetch < capabilitiesCacheTimeout) {
                Log.d(TAG, "캐시에서 카메라 기능 정보 반환: ${cached.model}")
                _cameraCapabilities.value = cached
                return@withContext
            }
        }

        isFetchingCapabilities = true
        try {
            Log.d(TAG, "카메라 기능 정보 가져오기 시작 (libgphoto2 API 사용)")

            // libgphoto2 API로 Abilities 직접 조회
            val abilitiesJson = CameraNative.getCameraAbilities()
            if (abilitiesJson == null) {
                Log.w(TAG, "Abilities 조회 실패 - 레거시 방식으로 폴백")
                // 레거시 방식으로 폴백
                val (legacyAbilities, widgetJson) = ensureMasterCameraData()
                val capabilities = parseCameraCapabilities(legacyAbilities, widgetJson)
                cachedCapabilities = capabilities
                lastCapabilitiesFetch = System.currentTimeMillis()
                _cameraCapabilities.value = capabilities
                return@withContext
            }

            // libgphoto2 Abilities 파싱
            val capabilities = parseCameraAbilitiesFromApi(abilitiesJson)

            // 캐시 갱신
            cachedCapabilities = capabilities
            lastCapabilitiesFetch = System.currentTimeMillis()
            _cameraCapabilities.value = capabilities

            Log.d(TAG, "카메라 기능 정보 업데이트 완료: ${capabilities.model}")

        } catch (e: Exception) {
            Log.e(TAG, "카메라 기능 정보 가져오기 실패", e)
            _cameraCapabilities.value = null
        } finally {
            isFetchingCapabilities = false
        }
    }

    /**
     * libgphoto2 API Abilities를 CameraCapabilities로 변환
     */
    private fun parseCameraAbilitiesFromApi(abilitiesJson: String): CameraCapabilities {
        try {
            val obj = JSONObject(abilitiesJson)
            val supportsObj = obj.getJSONObject("supports")

            return CameraCapabilities(
                model = obj.getString("model"),

                // libgphoto2 Abilities 기반
                canCapturePhoto = supportsObj.getBoolean("capture_image"),
                canCaptureVideo = supportsObj.getBoolean("capture_video"),
                canLiveView = supportsObj.getBoolean("capture_preview"),
                canTriggerCapture = supportsObj.getBoolean("trigger_capture"),

                // 고급 기능 (조합으로 판단)
                supportsBurstMode = supportsObj.getBoolean("capture_image") &&
                        supportsObj.getBoolean("trigger_capture"),
                supportsTimelapse = supportsObj.getBoolean("capture_image") &&
                        supportsObj.getBoolean("trigger_capture"),
                supportsBracketing = supportsObj.getBoolean("capture_image") &&
                        supportsObj.getBoolean("config"),
                supportsBulbMode = supportsObj.getBoolean("capture_image"),

                // 초점 (config 지원하면 대부분 가능)
                supportsAutofocus = supportsObj.getBoolean("config"),
                supportsManualFocus = supportsObj.getBoolean("config"),
                supportsFocusPoint = supportsObj.getBoolean("config"),

                // 파일 관리
                canDownloadFiles = true,  // 기본적으로 가능
                canDeleteFiles = supportsObj.getBoolean("delete"),
                canPreviewFiles = supportsObj.getBoolean("preview"),

                // 설정
                availableIsoSettings = emptyList(),  // 상세는 위젯에서
                availableShutterSpeeds = emptyList(),
                availableApertures = emptyList(),
                availableWhiteBalanceSettings = emptyList(),

                // 기타
                supportsRemoteControl = supportsObj.getBoolean("config"),
                supportsConfigChange = supportsObj.getBoolean("config"),
                batteryLevel = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "libgphoto2 Abilities 파싱 실패", e)
            throw e
        }
    }

    /**
     * 마스터 카메라 데이터를 가져오는 중앙집중 함수
     * 지연 로딩: 실제로 필요할 때만 가져옴
     */
    private suspend fun ensureMasterCameraData(): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            // 이미 가져오는 중이면 대기
            if (isFetchingMasterData) {
                var attempts = 0
                while (isFetchingMasterData && attempts < 50) {
                    kotlinx.coroutines.delay(100)
                    attempts++
                }
            }

            // 캐시된 데이터가 있고 유효하면 반환
            if (masterCameraAbilities != null && masterWidgetJson != null &&
                now - lastMasterFetch < masterCacheTimeout
            ) {
                Log.d(TAG, "마스터 카메라 데이터 캐시 사용 (${(now - lastMasterFetch) / 1000}초 전 생성)")
                return@withContext Pair(masterCameraAbilities!!, masterWidgetJson!!)
            }

            // 새로 가져오기 (초기화 직후 자동 실행 방지를 위해 지연 추가)
            isFetchingMasterData = true
            try {
                // 카메라가 완전히 초기화될 때까지 짧은 지연
                kotlinx.coroutines.delay(500)

                Log.d(TAG, "마스터 카메라 데이터 새로 가져오는 중...")
                val abilities = CameraNative.listCameraAbilities()
                val widgets = CameraNative.buildWidgetJson()

                masterCameraAbilities = abilities
                masterWidgetJson = widgets
                lastMasterFetch = now

                Log.d(
                    TAG,
                    "마스터 카메라 데이터 가져오기 완료 (abilities: ${abilities.length}, widgets: ${widgets.length})"
                )
                return@withContext Pair(abilities, widgets)
            } finally {
                isFetchingMasterData = false
            }
        }

    private fun parseCameraCapabilities(
        abilitiesJson: String,
        widgetJson: String
    ): CameraCapabilities {
        return try {
            val abilitiesObj = JSONObject(abilitiesJson)

            // 기본 기능들 파싱
            val captureImage = abilitiesObj.optBoolean("captureImage", false)
            val captureVideo = abilitiesObj.optBoolean("captureVideo", false)
            val capturePreview = abilitiesObj.optBoolean("capturePreview", false)
            val config = abilitiesObj.optBoolean("config", false)
            val triggerCapture = abilitiesObj.optBoolean("triggerCapture", false)

            // 위젯에서 설정 가능한 기능들 추출
            val hasAutofocus = widgetJson.contains("autofocus", ignoreCase = true)
            val hasManualFocus = widgetJson.contains("manualfocus", ignoreCase = true)
            val hasLiveView = widgetJson.contains("liveview", ignoreCase = true) || capturePreview
            val hasTimelapse = captureImage && triggerCapture
            val hasBracketing = captureImage && config
            val hasBurstMode = captureImage && triggerCapture

            // ISO 설정 확인
            val isoSettings = extractSettingOptions(widgetJson, "iso")
            val shutterSpeedSettings = extractSettingOptions(widgetJson, "shutter")
            val apertureSettings = extractSettingOptions(widgetJson, "aperture")
            val whiteBalanceSettings = extractSettingOptions(widgetJson, "whitebalance")

            CameraCapabilities(
                model = abilitiesObj.optString("model", "알 수 없음"),

                // 기본 촬영 기능
                canCapturePhoto = captureImage,
                canCaptureVideo = captureVideo,
                canLiveView = hasLiveView,
                canTriggerCapture = triggerCapture,

                // 고급 촬영 기능
                supportsBurstMode = hasBurstMode,
                supportsTimelapse = hasTimelapse,
                supportsBracketing = hasBracketing,
                supportsBulbMode = widgetJson.contains("bulb", ignoreCase = true),

                // 초점 기능
                supportsAutofocus = hasAutofocus,
                supportsManualFocus = hasManualFocus,
                supportsFocusPoint = hasManualFocus,

                // 파일 관리
                canDownloadFiles = abilitiesObj.optBoolean("fileDownload", false),
                canDeleteFiles = abilitiesObj.optBoolean("fileDelete", false),
                canPreviewFiles = abilitiesObj.optBoolean("filePreview", false),

                // 설정 가능한 옵션들
                availableIsoSettings = isoSettings,
                availableShutterSpeeds = shutterSpeedSettings,
                availableApertures = apertureSettings,
                availableWhiteBalanceSettings = whiteBalanceSettings,

                // 기타
                supportsRemoteControl = config,
                supportsConfigChange = config,
                batteryLevel = null // 추후 구현
            )

        } catch (e: Exception) {
            Log.e(TAG, "카메라 기능 정보 파싱 실패", e)
            CameraCapabilities(
                model = "파싱 실패",
                canCapturePhoto = false,
                canCaptureVideo = false,
                canLiveView = false,
                canTriggerCapture = false,
                supportsBurstMode = false,
                supportsTimelapse = false,
                supportsBracketing = false,
                supportsBulbMode = false,
                supportsAutofocus = false,
                supportsManualFocus = false,
                supportsFocusPoint = false,
                canDownloadFiles = false,
                canDeleteFiles = false,
                canPreviewFiles = false,
                availableIsoSettings = emptyList(),
                availableShutterSpeeds = emptyList(),
                availableApertures = emptyList(),
                availableWhiteBalanceSettings = emptyList(),
                supportsRemoteControl = false,
                supportsConfigChange = false,
                batteryLevel = null
            )
        }
    }

    private fun extractSettingOptions(widgetJson: String, settingName: String): List<String> {
        return try {
            val json = JSONObject(widgetJson)
            val options = mutableListOf<String>()

            // JSON에서 해당 설정의 선택지들을 재귀적으로 찾기
            extractOptionsFromJson(json, settingName.lowercase(), options)

            options.distinct()
        } catch (e: Exception) {
            Log.w(TAG, "$settingName 설정 옵션 추출 실패", e)
            emptyList()
        }
    }

    private fun extractOptionsFromJson(
        json: JSONObject,
        settingName: String,
        options: MutableList<String>
    ) {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.opt(key)

            if (key.lowercase().contains(settingName) && json.has("choices")) {
                val choices = json.optJSONArray("choices")
                if (choices != null) {
                    for (i in 0 until choices.length()) {
                        options.add(choices.optString(i))
                    }
                }
            } else if (value is JSONObject) {
                extractOptionsFromJson(value, settingName, options)
            } else if (value is org.json.JSONArray) {
                for (i in 0 until value.length()) {
                    val item = value.opt(i)
                    if (item is JSONObject) {
                        extractOptionsFromJson(item, settingName, options)
                    }
                }
            }
        }
    }

    /**
     * 캐시된 카메라 요약 정보를 가져옵니다
     */
    fun getCachedOrFetchSummary(): String {
        val now = System.currentTimeMillis()

        // 캐시된 요약 정보가 있고 유효하면 반환 (60초간 유효)
        cachedSummary?.let { cached ->
            if (now - lastSummaryFetch < summaryCacheTimeout) {
                Log.d(TAG, "캐시된 카메라 요약 정보 사용 (${(now - lastSummaryFetch) / 1000}초 전 생성)")
                return cached
            }
        }

        // 캐시가 없거나 만료되었으면 새로 가져오기
        Log.d(TAG, "새로운 카메라 요약 정보 가져오는 중...")

        // NativeCameraDataSource를 통해 가져와 전원 상태 확인 및 테스트 실행
        return try {
            // TODO: NativeCameraDataSource 주입이 필요하지만, 
            // 현재는 직접 CameraNative 호출
            val summary = CameraNative.getCameraSummary()

            // 전원 상태 확인 및 테스트 실행
            checkPowerStateAndRunTest(summary)

            cachedSummary = summary
            lastSummaryFetch = now
            Log.d(TAG, "새로 가져온 카메라 요약 정보 길이: ${summary.length}")
            summary
        } catch (e: Exception) {
            Log.e(TAG, "카메라 요약 정보 가져오기 실패", e)
            "{\"에러\":\"카메라 요약 정보 가져오기 실패: ${e.message}\"}"
        }
    }

    /**
     * 카메라 전원 상태를 확인하고 필요시 테스트 실행
     */
    private fun checkPowerStateAndRunTest(summary: String) {
        try {
            val json = JSONObject(summary)
            val summaryText = json.optString("summary", "")

            // PTP error 200f가 많이 발견되면 전원 꺼짐
            val errorCount = summaryText.split("PTP error 200f").size - 1
            val hasNoCaptureCapability = summaryText.contains("No Image Capture")

            // 배터리 레벨이 있고 시리얼 번호가 있으면 일부 통신은 되고 있음
            val hasBatteryLevel =
                summaryText.contains("Battery Level") && summaryText.contains("value:")
            val hasSerialNumber =
                summaryText.contains("Serial Number") && !summaryText.contains("00000000000000000000000000000000")

            Log.d(
                TAG,
                "전원 상태 확인: PTP 에러 개수=$errorCount, 촬영불가=$hasNoCaptureCapability, 배터리=$hasBatteryLevel, 시리얼=$hasSerialNumber"
            )

            // 디버깅을 위한 상세 로그
            Log.d(TAG, "=== 전원 상태 디버깅 ===")
            Log.d(TAG, "PTP 에러 개수: $errorCount")
            Log.d(TAG, "촬영 불가: $hasNoCaptureCapability")
            Log.d(TAG, "배터리 정보 있음: $hasBatteryLevel")
            Log.d(TAG, "시리얼 정보 있음: $hasSerialNumber")

            if (hasBatteryLevel) {
                val batteryStart = summaryText.indexOf("Battery Level")
                val batteryEnd = summaryText.indexOf("\n", batteryStart)
                val batteryLine = if (batteryEnd > 0) summaryText.substring(
                    batteryStart,
                    batteryEnd
                ) else "찾을 수 없음"
                Log.d(TAG, "배터리 라인: $batteryLine")
            }

            if (hasSerialNumber) {
                val serialStart = summaryText.indexOf("Serial Number:")
                val serialEnd = summaryText.indexOf("\n", serialStart)
                val serialLine =
                    if (serialEnd > 0) summaryText.substring(serialStart, serialEnd) else "찾을 수 없음"
                Log.d(TAG, "시리얼 라인: $serialLine")
            }

            Log.d(
                TAG,
                "판단 조건: errorCount >= 10 && hasNoCaptureCapability && (!hasBatteryLevel || !hasSerialNumber)"
            )
            Log.d(
                TAG,
                "실제 값: $errorCount >= 10 && $hasNoCaptureCapability && (!$hasBatteryLevel || !$hasSerialNumber)"
            )
            Log.d(
                TAG,
                "계산 결과: ${errorCount >= 10} && $hasNoCaptureCapability && ${!hasBatteryLevel || !hasSerialNumber}"
            )

            // 더 엄격한 판단: 에러가 많고 + 촬영불가 + 배터리/시리얼 정보도 없을 때만 꺼진 것으로 판단
            val isPoweredOff =
                errorCount >= 10 && hasNoCaptureCapability && (!hasBatteryLevel || !hasSerialNumber)

            Log.d(TAG, "최종 판단: isPoweredOff = $isPoweredOff")
            Log.d(TAG, "========================")

            if (isPoweredOff) {
                Log.w(TAG, "🔴 카메라가 꺼진 상태로 감지됨 - 사용자에게 카메라 상태 점검 알러트 표시")
                showCameraStatusAlert()
            } else {
                Log.d(TAG, "🟢 카메라가 켜진 상태 (일부 PTP 에러는 정상적인 세션 충돌일 수 있음)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "전원 상태 확인 실패", e)
        }
    }

    /**
     * 카메라 상태 점검 알러트를 사용자에게 표시
     */
    private fun showCameraStatusAlert() {
        Log.e(TAG, "🚨 카메라 상태 점검이 필요합니다")
        Log.e(TAG, "📋 다음 사항을 확인해주세요:")
        Log.e(TAG, "   1. 카메라 전원이 켜져 있는지 확인")
        Log.e(TAG, "   2. 카메라 배터리가 충분한지 확인")
        Log.e(TAG, "   3. USB 케이블 연결 상태 확인")
        Log.e(TAG, "   4. 카메라가 PC 연결 모드로 설정되어 있는지 확인")
        Log.e(TAG, "   5. 카메라를 껐다가 다시 켜보세요")
        Log.e(TAG, "🔄 문제가 계속되면 카메라를 재��결해주세요")

        // 실제 UI 알러트는 ViewModel이나 Activity에서 처리해야 하므로
        // 여기서는 로그로만 표시하고 상태를 업데이트
        cameraStateObserver.showCameraStatusCheckDialog(true)
    }

    /**
     * 카메라가 꺼진 상태일 때 파일 다운로드 기능 테스트
     * 주의: 이 함수는 카메라가 꺼진 상태로 확실히 판단될 때만 실행되어야 함
     */
    private fun runPoweredOffTest() {
        scope.launch {
            try {
                Log.d(TAG, "🔴 카메라 꺼진 상태 테스트 시작 - USB 연결은 유지, 카메라 전원은 OFF")
                Log.d(TAG, "📁 꺼진 카메라에서 파일 목록 가져오기 테스트...")

                // 1. 카메라 초기화 상태 확인
                val isInitialized = try {
                    CameraNative.isCameraInitialized()
                } catch (e: Exception) {
                    Log.e(TAG, "카메라 초기화 상태 확인 실패", e)
                    false
                }

                if (!isInitialized) {
                    Log.w(TAG, "📁 카메라가 초기화되지 않음 - 테스트 중단")
                    Log.w(TAG, "🔴 USB 연결은 되어있지만 카메라 통신 불가능한 상태")
                    return@launch
                }

                // 2. 카메라가 정말 꺼진 상태인지 재확인
                val currentSummary = try {
                    CameraNative.getCameraSummary()
                } catch (e: Exception) {
                    Log.e(TAG, "카메라 요약 정보 재확인 실패", e)
                    return@launch
                }

                val stillPoweredOff = try {
                    val json = JSONObject(currentSummary)
                    val summaryText = json.optString("summary", "")
                    val errorCount = summaryText.split("PTP error 200f").size - 1
                    val hasNoCaptureCapability = summaryText.contains("No Image Capture")

                    Log.d(TAG, "🔍 재확인: PTP 에러=$errorCount, 촬영불가=$hasNoCaptureCapability")
                    errorCount >= 10 && hasNoCaptureCapability
                } catch (e: Exception) {
                    Log.e(TAG, "카메라 상태 재확인 실패", e)
                    false
                }

                if (!stillPoweredOff) {
                    Log.w(TAG, "🟢 카메라가 다시 켜진 것으로 감지됨 - 테스트 중단")
                    return@launch
                }

                Log.d(TAG, "✅ 카메라 꺼진 상태 확인됨 - 파일 다운로드 테스트 계속 진행")

                // 3. 파일 목록 가져오기 테스트 (더 많은 파일 확인)
                val photoListJson = try {
                    CameraNative.getCameraFileListPaged(0, 50) // 첫 페이지, 50개
                } catch (e: Exception) {
                    Log.e(TAG, "📁 파일 목록 가져오기 실패", e)
                    null
                }

                if (photoListJson != null) {
                    Log.d(TAG, "📁 파일 목록 JSON 길이: ${photoListJson.length}")
                } else {
                    Log.d(TAG, "📁 파일 목록이 null임")
                }

                if (photoListJson.isNullOrEmpty() || photoListJson == "null") {
                    Log.d(TAG, "📁 카메라에 파일이 없거나 목록 가져오기 실패")
                    Log.d(TAG, "🔴 꺼진 카메라에서는 파일 접근이 불가능한 것으로 확인됨")
                    Log.d(TAG, "🔴 꺼진 카메라 파일 다운로드 테스트 완료")
                    return@launch
                }

                // 4. JSON 파싱하여 파일 목록 분석
                val json = try {
                    org.json.JSONObject(photoListJson)
                } catch (e: Exception) {
                    Log.e(TAG, "📁 JSON 파싱 실패", e)
                    return@launch
                }

                if (json.has("error")) {
                    Log.e(TAG, "📁 파일 목록 오류: ${json.getString("error")}")
                    Log.d(TAG, "🔴 꺼진 카메라에서는 파일 접근 권한이 없는 것으로 확인됨")
                    return@launch
                }

                val filesArray = json.optJSONArray("files")
                val totalFiles = json.optInt("totalFiles", 0)
                val currentPageFiles = if (filesArray != null) filesArray.length() else 0

                Log.d(TAG, "📁 === 꺼진 카메라 파일 목록 결과 ===")
                Log.d(TAG, "📁 전체 파일 수: ${totalFiles}개")
                Log.d(TAG, "📁 현재 페이지 파일 수: ${currentPageFiles}개")
                Log.d(TAG, "📁 ==============================")

                if (filesArray != null && filesArray.length() > 0) {
                    var jpgFound = false

                    Log.d(TAG, "😲 놀랍게도 카메라가 꺼진 상태에서도 파일 목록 접근 가능!")

                    // 5. 모든 파일을 확인해서 첫 번째 JPG 파일 찾기
                    for (i in 0 until filesArray.length()) {
                        try {
                            val fileObj = filesArray.getJSONObject(i)
                            val fileName = fileObj.optString("name", "")
                            val filePath = fileObj.optString("path", "")
                            val fileSize = fileObj.optLong("size", 0)

                            Log.d(TAG, "📁 파일 ${i + 1}: $fileName (${fileSize} bytes)")

                            // JPG 파일인지 확인 (대소문자 구분 없이)
                            if (!jpgFound && fileName.lowercase().endsWith(".jpg")) {
                                Log.d(TAG, "📸 첫 번째 JPG 파일 발견: $fileName")
                                jpgFound = true

                                if (filePath.isNotEmpty()) {
                                    // 6. 썸네일 가져오기 테스트
                                    Log.d(TAG, "🖼️ 꺼진 카메라에서 썸네일 가져오기 테스트...")
                                    val thumbnail = try {
                                        CameraNative.getCameraThumbnail(filePath)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "🖼️ 썸네일 가져오기 실패", e)
                                        null
                                    }

                                    if (thumbnail != null && thumbnail.isNotEmpty()) {
                                        Log.d(
                                            TAG,
                                            "🖼️ 꺼진 카메라에서 썸네일 가져오기 성공: ${thumbnail.size} bytes"
                                        )
                                    } else {
                                        Log.w(TAG, "🖼️ 꺼진 카메라에서 썸네일 가져오기 실패")
                                    }

                                    // 7. 실제 JPG 파일 다운로드 테스트
                                    if (fileSize < 50 * 1024 * 1024) { // 50MB 미만만 테스트
                                        Log.d(TAG, "⬇️ 꺼진 카메라에서 JPG 파일 다운로드 테스트 시작...")
                                        val imageData = try {
                                            CameraNative.downloadCameraPhoto(filePath)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "⬇️ JPG 파일 다운로드 실패", e)
                                            null
                                        }

                                        if (imageData != null && imageData.isNotEmpty()) {
                                            Log.d(TAG, "⬇️ 🎉 꺼진 카메라에서 JPG 파일 다운로드 성공!")
                                            Log.d(TAG, "⬇️ 다운로드된 파일 크기: ${imageData.size} bytes")
                                            Log.d(TAG, "⬇️ 파일명: $fileName")
                                            Log.d(TAG, "⬇️ ✨ 카메라 전원이 꺼져도 메모리카드 접근 가능함을 확인!")
                                        } else {
                                            Log.w(TAG, "⬇️ JPG 파일 다운로드 실패 - 데이터 없음")
                                        }
                                    } else {
                                        Log.d(
                                            TAG,
                                            "⬇️ JPG 파일이 너무 큼 (${fileSize} bytes) - 다운로드 테스트 건너뜀"
                                        )
                                    }
                                }
                                break // 첫 번째 JPG만 처리하고 종료
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "📁 파일 ${i + 1} 처리 중 오류", e)
                        }
                    }

                    if (!jpgFound) {
                        Log.w(TAG, "📸 JPG 파일을 찾을 수 없음")
                    }
                } else {
                    Log.d(TAG, "📁 파일 배열이 비어있음 - 꺼진 카메라에서는 파일 목록 접근 불가")
                }

                Log.d(TAG, "🔴 꺼진 카메라 파일 다운로드 테스트 완료")

            } catch (e: Exception) {
                Log.e(TAG, "🔴 꺼진 카메라 테스트 중 오류", e)
            }
        }
    }

    /**
     * 현재 연결된 카메라의 기능 정보를 새로고침합니다.
     */
    suspend fun refreshCameraCapabilities() {
        // 강제 새로고침을 위해 캐시 무효화
        invalidateCache()
        fetchCameraCapabilities()
    }

    /**
     * 라이브뷰 지원 여부를 빠르게 확인합니다
     */
    suspend fun isLiveViewSupported(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, widgetJson) = ensureMasterCameraData()
            val hasLiveViewSize = widgetJson.contains("liveviewsize", ignoreCase = true)
            val hasLiveView = widgetJson.contains("liveview", ignoreCase = true)

            Log.d(TAG, "라이브뷰 지원 확인 - 마스터 데이터 사용: ${hasLiveViewSize || hasLiveView}")
            hasLiveViewSize || hasLiveView
        } catch (e: Exception) {
            Log.e(TAG, "라이브뷰 지원 확인 실패", e)
            false
        }
    }

    /**
     * 특정 기능 지원 여부를 확일합니다
     */
    suspend fun hasCapability(capability: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val (abilities, widgets) = ensureMasterCameraData()
            val hasInAbilities = abilities.contains(capability, ignoreCase = true)
            val hasInWidgets = widgets.contains(capability, ignoreCase = true)

            Log.d(TAG, "$capability 지원 확인 - 마스터 데이터 사용: ${hasInAbilities || hasInWidgets}")
            hasInAbilities || hasInWidgets
        } catch (e: Exception) {
            Log.e(TAG, "$capability 지원 확인 실패", e)
            false
        }
    }

    /**
     * 위젯 JSON을 반환합니다
     */
    suspend fun buildWidgetJsonFromMaster(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, widgetJson) = ensureMasterCameraData()
            Log.d(TAG, "마스터 데이터에서 위젯 JSON 반환: ${widgetJson.length} chars")
            widgetJson
        } catch (e: Exception) {
            Log.e(TAG, "마스터 데이터에서 위젯 JSON 가져오기 실패", e)
            "{\"error\": \"마스터 데이터 접근 실패\"}"
        }
    }

    /**
     * 카메라 능력 정보를 반환합니다
     */
    suspend fun getCameraAbilitiesFromMaster(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val (abilities, _) = ensureMasterCameraData()
            Log.d(TAG, "마스터 데이터에서 카메라 능력 반환: ${abilities.length} chars")
            abilities
        } catch (e: Exception) {
            Log.e(TAG, "마스터 데이터에서 카메라 능력 가져오기 실패", e)
            "{\"error\": \"마스터 데이터 접근 실패\"}"
        }
    }

    /**
     * 캐시를 무효화합니다
     */
    private fun invalidateCache() {
        cachedCapabilities = null
        lastCapabilitiesFetch = 0
        cachedSummary = null
        lastSummaryFetch = 0
        masterCameraAbilities = null
        masterWidgetJson = null
        lastMasterFetch = 0
    }

    /**
     * 상태를 초기화합니다
     */
    fun reset() {
        _cameraCapabilities.value = null
        invalidateCache()
    }
}