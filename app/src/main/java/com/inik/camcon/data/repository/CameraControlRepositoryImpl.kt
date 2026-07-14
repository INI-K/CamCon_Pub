package com.inik.camcon.data.repository

import android.util.Log
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.datasource.ptpip.PtpipDataSource
import com.inik.camcon.data.datasource.usb.UsbCameraManager
import com.inik.camcon.data.repository.managers.CameraConnectionManager
import com.inik.camcon.data.repository.managers.CameraEventManager
import com.inik.camcon.data.repository.managers.PhotoDownloadManager
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.LiveViewQuality
import com.inik.camcon.domain.model.PaginatedCameraPhotos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카메라 설정·포커스·사진 접근·능력 정보 담당 sub-impl.
 *
 * H8 분해: 원본 CameraRepositoryImpl의 Settings(3) + Focus(3) + PhotoAccess(5) + Abilities(3) +
 * Subscription/Raw(2) + FileList(1) = 17개 override 이동.
 *
 * `_cameraSettings` StateFlow 소유. Facade·Capture 측에서 `getCachedSettings()`로 읽기 전용 접근.
 * 이벤트 리스너 재시작 훅은 Facade가 `onEventListenerRestart` 콜백으로 주입 (Capture에 위임).
 */
@Singleton
class CameraControlRepositoryImpl @Inject constructor(
    private val nativeDataSource: NativeCameraDataSource,
    private val ptpipDataSource: PtpipDataSource,
    private val usbCameraManager: UsbCameraManager,
    private val connectionManager: CameraConnectionManager,
    private val eventManager: CameraEventManager,
    private val downloadManager: PhotoDownloadManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "카메라제어레포"
    }

    private val _cameraSettings = MutableStateFlow<CameraSettings?>(null)

    /** Facade·Capture에서 참조하는 캐시된 설정 (PTPIP 콜백·createCapturedPhoto 등). */
    fun getCachedSettings(): CameraSettings? = _cameraSettings.value

    suspend fun getCameraSettings(): Result<CameraSettings> {
        return withContext(ioDispatcher) {
            try {
                // 라이브 값(노출 스트립)을 위해 캐시 우선 반환을 제거 — 매 호출마다 fresh 위젯 JSON에서 파싱.
                val widgetJson = getWidgetJsonFromSource()
                val settings = parseWidgetJsonToSettings(widgetJson)
                    ?: return@withContext Result.failure(
                        UnsupportedOperationException("위젯 JSON 값 파싱 미구현 - 설정을 확인할 수 없습니다")
                    )

                withContext(Dispatchers.Main) {
                    _cameraSettings.value = settings
                }

                // 라이브뷰 중 이벤트 푸시 + 1초 안전망이 자주 호출 → 로그 도배 방지 위해 무로깅.
                Result.success(settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 설정 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    /**
     * 위젯 JSON 가져오기. 라이브 설정값을 위해 항상 fresh 네이티브를 우선한다.
     * (마스터 데이터는 연결 시점 캐시라 ISO/SS/조리개 등이 갱신되지 않으므로 폴백으로만 사용)
     */
    private suspend fun getWidgetJsonFromSource(): String {
        // 노출 스트립은 노출 프로퍼티 몇 개만 필요 → 경량 getter 우선(전체 트리보다 빠르고 LV를 덜 끊음).
        val light = nativeDataSource.getLiveExposureJson()
        if (light.isNotBlank() && !light.contains("\"error\"") && light.contains("\"name\"")) {
            return light
        }
        // 일시적 사유(coalesce 드랍/타임아웃)는 무거운 전체 트리로 폴백하지 말고 ""을 반환해
        // 직전 캐시를 유지한다(파싱 null → 조용히 skip). LV 중 호출 연쇄·끊김 방지.
        if (light.contains("\"timeout\"") || light.contains("\"coalesced\"")) {
            return ""
        }
        // 폴백: 전체 트리(fresh) → 마스터 데이터
        val full = nativeDataSource.buildWidgetJson()
        if (full.isNotBlank() && !full.contains("\"error\"")) {
            return full
        }
        return usbCameraManager.buildWidgetJsonFromMaster()
    }

    /**
     * 위젯 JSON 트리를 CameraSettings로 변환.
     *
     * 네이티브 buildWidgetJson 이 이제 타입별 "value"를 직렬화하므로(camera_config.cpp) 실제 값을 파싱한다.
     * 관심 설정(iso/shutterspeed/f-number/whitebalance/exposurecompensation/focusmode)을 이름으로 찾는다.
     * 하나도 못 읽으면 null(설정 미상)을 반환해 가짜 값 기록을 막는다.
     */
    private fun parseWidgetJsonToSettings(widgetJson: String): CameraSettings? {
        return try {
            if (widgetJson.isBlank()) return null
            val root = org.json.JSONObject(widgetJson)

            // 이름 → 노드 인덱스(재귀)
            val byName = HashMap<String, org.json.JSONObject>()
            fun walk(node: org.json.JSONObject) {
                node.optString("name", "").takeIf { it.isNotEmpty() }?.let { byName[it] = node }
                val children = node.optJSONArray("children") ?: return
                for (i in 0 until children.length()) {
                    children.optJSONObject(i)?.let { walk(it) }
                }
            }
            walk(root)
            if (byName.isEmpty()) return null

            fun value(vararg names: String): String {
                for (nm in names) {
                    val node = byName[nm] ?: continue
                    val v = node.opt("value")
                    if (v != null && v.toString().isNotBlank()) return v.toString()
                }
                return ""
            }
            fun choices(vararg names: String): List<String> {
                for (nm in names) {
                    val arr = byName[nm]?.optJSONArray("choices") ?: continue
                    val out = ArrayList<String>(arr.length())
                    for (i in 0 until arr.length()) out.add(arr.optString(i))
                    if (out.isNotEmpty()) return out
                }
                return emptyList()
            }

            // ISO 표시 게이팅:
            // - d054(Auto ISO on/off) == "1" → 본체가 스스로 올린 실효 ISO = d0b5(ISOControlSensitivity) 우선.
            // - d054 존재 && != "1" (Auto ISO 명시적 OFF) → manual "iso"(0x500F)로 강제(d0b5 무시).
            // - d054 부재(Z6/Z7 등 게이팅 정보 없는 기종, 또는 d0b5/d054 모두 미연결) → 기존 우선순위 그대로(회귀 0).
            val autoIso = value("d054")
            val iso = when {
                autoIso == "1" -> value("d0b5", "iso", "isospeed")
                autoIso.isNotEmpty() -> value("iso", "isospeed", "d0b5")
                else -> value("d0b5", "iso", "isospeed")
            }
            val shutter = value("shutterspeed", "shutterspeed2")
            val aperture = value("f-number", "aperture")
            val wb = value("whitebalance", "whitebalance2")
            val focus = value("focusmode", "focusmode2", "autofocusmode")
            val ev = value("exposurecompensation", "exposurecompensation2")
            // 노출 모드(PASM) — 카메라가 expprogram 위젯을 주지 않으면 "" → null(미판독, UI 자동 숨김).
            val exposureMode = value("expprogram").takeIf { it.isNotEmpty() }
                ?.let { normalizeExposureMode(it) }

            // 모두 비어있으면 미상 — 가짜 값 방지
            if (iso.isEmpty() && shutter.isEmpty() && aperture.isEmpty() && wb.isEmpty() && ev.isEmpty()) {
                return null
            }

            val available = HashMap<String, List<String>>()
            choices("iso").takeIf { it.isNotEmpty() }?.let { available["iso"] = it }
            choices("shutterspeed", "shutterspeed2").takeIf { it.isNotEmpty() }?.let { available["shutterspeed"] = it }
            choices("f-number", "aperture").takeIf { it.isNotEmpty() }?.let { available["f-number"] = it }
            choices("whitebalance", "whitebalance2").takeIf { it.isNotEmpty() }?.let { available["whitebalance"] = it }
            choices("exposurecompensation", "exposurecompensation2").takeIf { it.isNotEmpty() }?.let {
                available["exposurecompensation"] = it
            }

            CameraSettings(
                iso = iso,
                shutterSpeed = shutter,
                aperture = aperture,
                whiteBalance = wb,
                focusMode = focus,
                exposureCompensation = ev,
                exposureMode = exposureMode,
                availableSettings = available
            )
        } catch (e: Exception) {
            Log.e(TAG, "위젯 JSON 파싱 실패", e)
            null
        }
    }

    /**
     * expprogram 위젯 값을 노출 모드 1글자(P/A/S/M)로 정규화.
     * 카메라별로 "Manual"/"Aperture Priority"(long-form) 또는 이미 "M"/"A"(short-form)로 올 수 있다.
     * 알려진 long-form만 축약하고, 매핑에 없는 값(제조사 특수 모드 등)은 원본을 그대로 유지한다.
     */
    private fun normalizeExposureMode(raw: String): String {
        val t = raw.trim()
        return when (t.lowercase()) {
            "p", "program", "program ae", "program auto", "auto", "programmed auto" -> "P"
            "a", "av", "aperture", "aperture priority", "aperture-priority", "aperture priority ae" -> "A"
            "s", "tv", "shutter", "shutter priority", "shutter-priority", "shutter priority ae" -> "S"
            "m", "manual", "manual exposure" -> "M"
            else -> t
        }
    }

    suspend fun getCameraInfo(): Result<String> {
        return withContext(ioDispatcher) {
            try {
                val summary = nativeDataSource.getCameraSummary()
                Result.success(summary.name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 정보 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    suspend fun updateCameraSetting(key: String, value: String): Result<Boolean> {
        return withContext(ioDispatcher) {
            try {
                // UI 키 → libgphoto2 위젯명. 대부분 동일하고 조리개만 불일치("aperture" → "f-number").
                val configName = when (key) {
                    "aperture" -> "f-number"
                    else -> key
                }
                com.inik.camcon.utils.LogcatManager.d(TAG, "카메라 설정 업데이트: $key($configName) = $value")

                val code = nativeDataSource.setConfigString(configName, value)
                if (code == 0) {
                    Result.success(true)
                } else {
                    // 성공 위장 제거: 네이티브 실패(gp code != 0)를 그대로 전파해 UI가 에러를 안내하게 한다.
                    Result.failure(
                        IllegalStateException("카메라 설정 변경 실패 ($key=$value, gp code=$code)")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 설정 업데이트 실패", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getCameraCapabilities(): Result<CameraCapabilities?> {
        return withContext(ioDispatcher) {
            try {
                val capabilities = connectionManager.cameraCapabilities.value
                    ?: nativeDataSource.getCameraCapabilities()
                Result.success(capabilities)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 기능 정보 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    // ── Focus ──

    suspend fun autoFocus(): Result<Boolean> {
        return withContext(ioDispatcher) {
            try {
                com.inik.camcon.utils.LogcatManager.d(TAG, "자동초점 시작")
                val result = nativeDataSource.autoFocus()
                com.inik.camcon.utils.LogcatManager.d(TAG, "자동초점 결과: $result")
                Result.success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "자동초점 실패", e)
                Result.failure(e)
            }
        }
    }

    suspend fun manualFocus(x: Float, y: Float): Result<Boolean> {
        // TODO: 수동 초점 기능 구현
        return Result.success(true)
    }

    suspend fun setFocusPoint(x: Float, y: Float): Result<Boolean> {
        // TODO: 초점 포인트 설정 기능 구현
        return Result.success(true)
    }

    // ── Photo Access ──

    suspend fun downloadPhotoFromCamera(photoId: String): Result<com.inik.camcon.domain.model.CapturedPhoto> {
        return downloadManager.downloadPhotoFromCamera(
            photoId = photoId,
            cameraCapabilities = connectionManager.cameraCapabilities.value,
            cameraSettings = _cameraSettings.value
        )
    }

    suspend fun getCameraPhotos(
        onEventListenerRestart: suspend () -> Unit
    ): Result<List<CameraPhoto>> {
        val result = downloadManager.getCameraPhotos()

        // 이벤트 리스너가 중지되었을 가능성이 있으므로 안전하게 재시작
        if (connectionManager.isConnected.value && result.isSuccess) {
            com.inik.camcon.utils.LogcatManager.d(TAG, "사진 목록 가져오기 후 이벤트 리스너 상태 확인 및 재시작")
            kotlinx.coroutines.delay(500)

            if (!eventManager.isRunning()) {
                try {
                    com.inik.camcon.utils.LogcatManager.d(TAG, "이벤트 리스너 재시작 시도")
                    onEventListenerRestart()
                } catch (e: Exception) {
                    Log.w(TAG, "이벤트 리스너 재시작 실패, 나중에 다시 시도", e)
                }
            } else {
                com.inik.camcon.utils.LogcatManager.d(TAG, "이벤트 리스너가 이미 실행 중")
            }
        }

        return result
    }

    suspend fun getCameraPhotosPaged(
        page: Int,
        pageSize: Int,
        onEventListenerRestart: suspend () -> Unit
    ): Result<PaginatedCameraPhotos> {
        return downloadManager.getCameraPhotosPaged(
            page = page,
            pageSize = pageSize,
            isPhotoPreviewMode = eventManager.isPhotoPreviewMode(),
            onEventListenerRestart = {
                if (!eventManager.isRunning()) {
                    onEventListenerRestart()
                }
            }
        )
    }

    suspend fun getCameraThumbnail(photoPath: String): Result<ByteArray> {
        // Wi-Fi(PTPIP)에서도 네이티브 세션이 살아있으므로 썸네일 조회를 허용한다.
        // (USB 전용 플래그만 보면 PTPIP에서 항상 "카메라 연결 안됨"으로 실패)
        val isPtpip = connectionManager.isPtpipConnected.value
        return downloadManager.getCameraThumbnail(
            photoPath = photoPath,
            isConnected = connectionManager.isConnected.value || isPtpip,
            isInitializing = connectionManager.isInitializing.value,
            isNativeCameraConnected = usbCameraManager.isNativeCameraConnected.value || isPtpip
        )
    }

    // ── Abilities / Device Info ──

    fun getCameraAbilitiesInfo(): com.inik.camcon.domain.model.CameraAbilitiesInfo? {
        return nativeDataSource.getUsbCameraAbilities()
            ?: ptpipDataSource.getCurrentAbilities()
    }

    fun getCameraDeviceInfoDetail(): com.inik.camcon.domain.model.PtpDeviceInfo? {
        return nativeDataSource.getUsbCameraDeviceInfo()
            ?: ptpipDataSource.getCurrentDeviceInfo()
    }

    // ── Subscription / Raw / FileList (C-3 수정: CameraViewModel CameraNative 직접 호출 제거) ──

    suspend fun setSubscriptionTier(tier: com.inik.camcon.domain.model.SubscriptionTier): Result<Unit> = try {
        val tierInt = when (tier) {
            com.inik.camcon.domain.model.SubscriptionTier.FREE -> 0
            com.inik.camcon.domain.model.SubscriptionTier.BASIC -> 1
            com.inik.camcon.domain.model.SubscriptionTier.PRO -> 2
            com.inik.camcon.domain.model.SubscriptionTier.REFERRER -> 2
            com.inik.camcon.domain.model.SubscriptionTier.ADMIN -> 2
        }
        nativeDataSource.setSubscriptionTier(tierInt)
        Log.d(TAG, "구독 티어 설정 완료: $tier (네이티브: $tierInt)")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "구독 티어 설정 실패", e)
        Result.failure(e)
    }

    suspend fun setRawFileDownloadEnabled(enabled: Boolean): Result<Unit> = try {
        nativeDataSource.setRawFileDownloadEnabled(enabled)
        Log.d(TAG, "RAW 파일 다운로드 설정 완료: $enabled")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "RAW 파일 다운로드 설정 실패", e)
        Result.failure(e)
    }

    suspend fun setLiveViewQuality(quality: LiveViewQuality): Result<Unit> = try {
        nativeDataSource.setLiveViewQuality(quality.value)
        Log.d(TAG, "라이브뷰 화질 설정 완료: $quality (네이티브: ${quality.value})")
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "라이브뷰 화질 설정 실패", e)
        Result.failure(e)
    }

    // 라이브뷰 stop이 진행 중인지 여부. 화질 변경 후 안전 재시작 시 stop 완료를 폴링하는 용도.
    fun isLiveViewStopping(): Boolean = nativeDataSource.isLiveViewStopping()

    suspend fun getCameraFileListNow(): Result<List<String>> = try {
        val fileList = nativeDataSource.getCameraFileListNow()
        Log.d(TAG, "카메라 파일 목록 조회 완료: ${fileList.size}개")
        Result.success(fileList)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "카메라 파일 목록 조회 실패", e)
        Result.failure(e)
    }

    // ── C3 라운드 1: Native Gateway (Presentation→JNI 직접 호출 래핑, 2026-04-23) ──

    suspend fun isNativeLibrariesLoaded(): Boolean = nativeDataSource.isLibrariesLoaded()

    suspend fun setupNativeEnvironment(pluginDir: String): Boolean =
        nativeDataSource.setupEnvironmentPaths(pluginDir)

    suspend fun getLibGphoto2Version(): String =
        withContext(ioDispatcher) { nativeDataSource.getLibGphoto2Version() }

    suspend fun startNativeLog(logPath: String, level: Int): Boolean =
        nativeDataSource.startLogFile(logPath, level)

    suspend fun stopNativeLog(): Boolean = nativeDataSource.stopLogFile()

    suspend fun readNativeLog(filePath: String): String =
        nativeDataSource.getLogFileContent(filePath)

    suspend fun getCameraAbilitiesJson(): String? = nativeDataSource.getCameraAbilitiesJson()

    suspend fun getCameraDeviceInfoJson(): String? = nativeDataSource.getCameraDeviceInfoJson()

    suspend fun deleteGphotoSettings(): String = nativeDataSource.deleteGphotoSettings()

    suspend fun resumeNativeOperations() {
        nativeDataSource.resumeOperations()
    }

    suspend fun downloadCameraPhoto(photoPath: String): ByteArray? =
        withContext(ioDispatcher) { nativeDataSource.downloadCameraPhoto(photoPath) }

    suspend fun getCameraPhotoExifJson(photoPath: String): String? =
        nativeDataSource.getCameraPhotoExifJson(photoPath)
}
