package com.inik.camcon.data.datasource.nativesource

import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraAbilitiesInfo
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.PtpDeviceInfo
import com.inik.camcon.di.ApplicationScope
import com.inik.camcon.domain.manager.CameraStateObserver
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 네이티브 기능을 호출하는 데이터소스
 */
@Singleton
class NativeCameraDataSource @Inject constructor(
    private val cameraStateObserver: CameraStateObserver,
    @ApplicationScope private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "네이티브_카메라_데이터소스"
    }

    // 이벤트 리스너 중복 호출 방지용
    private val isStoppingEventListener = AtomicBoolean(false)
    private val isClosingCamera = AtomicBoolean(false)

    // closeCamera 직렬화를 위한 Mutex
    private val closeCameraMutex = Mutex()

    // 카메라 이벤트 리스닝 중지
    fun stopListenCameraEvents() {
        // 중복 호출 방지
        if (!isStoppingEventListener.compareAndSet(false, true)) {
            Log.d(TAG, "카메라 이벤트 리스닝 중지가 이미 진행 중 - 중복 방지")
            return
        }

        try {
            Log.d(TAG, "카메라 이벤트 리스닝 중지")
            CameraNative.stopListenCameraEvents()
        } catch (e: Exception) {
            Log.e(TAG, "카메라 이벤트 리스닝 중지 중 오류", e)
        } finally {
            // 1초 후 상태 리셋
            scope.launch(ioDispatcher) {
                delay(1000)
                isStoppingEventListener.set(false)
                Log.d(TAG, "이벤트 리스너 중지 상태 리셋")
            }
        }
    }

    // 라이브러리 로딩 테스트
    fun testLibraryLoad(): String = CameraNative.testLibraryLoad()

    // LibGphoto2 버전 정보
    fun getLibGphoto2Version(): String = CameraNative.getLibGphoto2Version()

    // 포트 정보 반환
    fun getPortInfo(): String = CameraNative.getPortInfo()

    // 카메라 초기화
    fun initCamera(): String {
        // 라이브러리 확인
        if (!CameraNative.isLibrariesLoaded()) {
            throw IllegalStateException("네이티브 라이브러리가 로딩되지 않았습니다")
        }

        Log.d(TAG, "카메라 초기화 시작")
        val result = CameraNative.initCamera()
        Log.d(TAG, "카메라 초기화 완료: 결과=$result")
        return result
    }

    /**
     * USB 카메라 Abilities 저장
     */
    private var usbCameraAbilities: CameraAbilitiesInfo? = null
    private var usbCameraDeviceInfo: PtpDeviceInfo? = null

    /**
     * 현재 USB 카메라 Abilities 조회
     */
    fun getUsbCameraAbilities(): CameraAbilitiesInfo? =
        usbCameraAbilities

    /**
     * 현재 USB 카메라 DeviceInfo 조회
     */
    fun getUsbCameraDeviceInfo(): PtpDeviceInfo? =
        usbCameraDeviceInfo

    /**
     * DeviceInfo JSON 파싱
     */
    private fun parseDeviceInfo(json: String): PtpDeviceInfo {
        try {
            val obj = JSONObject(json)
            return PtpDeviceInfo(
                manufacturer = obj.getString("manufacturer"),
                model = obj.getString("model"),
                version = obj.getString("version"),
                serialNumber = obj.getString("serial_number")
            )
        } catch (e: Exception) {
            Log.e(TAG, "DeviceInfo 파싱 실패", e)
            throw e
        }
    }

    // 카메라 요약 정보를 받아 Domain 모델인 Camera로 변환
    fun getCameraSummary(): Camera {
        // 네이티브에서 카메라 요약 정보 가져오기
        val summary = CameraNative.getCameraSummary()
        Log.d(TAG, "카메라 요약 정보: $summary")

        // 카메라 전원 상태 확인
        val isPoweredOff = isCameraPoweredOff(summary)
        if (isPoweredOff) {
            Log.w(TAG, "카메라가 꺼진 상태로 감지됨 - 카메라 상태 점검 알러트 표시")
            showCameraStatusAlert()
        }

        return try {
            // JSON 파싱 시도
            val json = JSONObject(summary)
            Camera(
                id = "1",
                name = json.optString("model", "알 수 없음"),
                isActive = !json.has("에러") && !isPoweredOff
            )
        } catch (e: Exception) {
            // JSON 파싱 실패 시 기본값 반환
            Log.w(TAG, "카메라 요약 정보 JSON 파싱 실패, 기본값 사용", e)
            val parts = summary.split(",")
            Camera(
                id = parts.getOrNull(0) ?: "0",
                name = parts.getOrNull(1) ?: "알 수 없음",
                isActive = parts.getOrNull(2)?.toBoolean() ?: false
            )
        }
    }

    /**
     * 카메라가 꺼진 상태인지 확인
     */
    private fun isCameraPoweredOff(summary: String): Boolean {
        return try {
            val json = JSONObject(summary)
            val summaryText = json.optString("summary", "")

            // PTP error 200f가 많이 발견되면 전원 꺼짐
            val errorCount = summaryText.split("PTP error 200f").size - 1
            val hasNoCaptureCapability = summaryText.contains("No Image Capture")

            Log.d(TAG, "전원 상태 확인: PTP 에러 개수=$errorCount, 촬영불가=$hasNoCaptureCapability")

            errorCount >= 5 && hasNoCaptureCapability
        } catch (e: Exception) {
            Log.e(TAG, "전원 상태 확인 실패", e)
            false
        }
    }

    /**
     * 카메라 상태 점검 알러트를 사용자에게 표시
     */
    private fun showCameraStatusAlert() {
        Log.w(TAG, "카메라 상태 점검 필요 - 점검 다이얼로그 표시")
        cameraStateObserver.showCameraStatusCheckDialog(true)
    }

    // 카메라 종료 (suspend — 완료 대기 보장)
    suspend fun closeCamera() {
        closeCameraMutex.withLock {
            // 중복 호출 방지
            if (!isClosingCamera.compareAndSet(false, true)) {
                Log.d(TAG, "카메라 종료가 이미 진행 중 - 중복 방지")
                return
            }

            try {
                withContext(ioDispatcher) {
                    Log.d(TAG, "카메라 종료")
                    CameraNative.closeCamera()
                    Log.d(TAG, "카메라 종료 완료")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "카메라 종료 중 오류", e)
            } finally {
                isClosingCamera.set(false)
                Log.d(TAG, "카메라 종료 상태 리셋")
            }
        }
    }

    // 카메라 감지
    fun detectCamera(): String = CameraNative.detectCamera()

    // 카메라 기능 목록 반환 (쉼표로 구분된 문자열)
    fun listCameraAbilities(): String = CameraNative.listCameraAbilities()

    // 캡처 요청
    fun requestCapture() {
        CameraNative.requestCapture()
    }

    // 자동초점
    fun autoFocus(): Boolean = CameraNative.autoFocus() == 0

    // 자동 카메라 감지 결과
    fun cameraAutoDetect(): String = CameraNative.cameraAutoDetect()

    // 위젯 JSON 빌드 결과
    fun buildWidgetJson(): String {
        // 라이브뷰 중 설정 스트립 폴링이 호출 → 로그 도배 방지 위해 무로깅.
        return CameraNative.buildWidgetJson()
    }

    // 라이브뷰 노출 스트립용 경량 조회(필요 노출 프로퍼티만, 캐시 없음). 전체 트리보다 빠르고 LV를 덜 끊는다.
    fun getLiveExposureJson(): String = CameraNative.getLiveExposureJson()

    // 카메라 설정 쿼리
    fun queryConfig() {
        CameraNative.queryConfig()
    }

    // 범용 설정 변경 (libgphoto2 위젯명 → 값). gp 결과 코드 반환(0=성공). withContext는 호출 레포에서 감쌈.
    fun setConfigString(key: String, value: String): Int = CameraNative.setConfigString(key, value)

    // 비동기 사진 촬영: 결과는 콜백으로 전달됨
    fun capturePhotoAsync(callback: CameraCaptureListener, saveDir: String) {
        CameraNative.capturePhotoAsync(callback, saveDir)
    }

    // 라이브뷰 시작 (콜백을 통해 프레임 전달)
    fun startLiveView(callback: LiveViewCallback) {
        CameraNative.startLiveView(callback)
    }

    // 라이브뷰 종료
    fun stopLiveView() {
        CameraNative.stopLiveView()
    }

    // 라이브뷰 stop이 진행 중인지 여부 (stop 완료까지 true). 화질 변경 재시작 시 폴링용.
    fun isLiveViewStopping(): Boolean = CameraNative.isLiveViewStopping()

    // 카메라 초기화 상태 반환
    fun isCameraInitialized(): Boolean {
        return try {
            CameraNative.isCameraInitialized()
        } catch (e: Exception) {
            Log.e(TAG, "카메라 초기화 상태 확인 실패", e)
            false
        }
    }

    // 페이징을 지원하는 카메라 사진 목록 가져오기
    suspend fun getCameraPhotosPaged(page: Int, pageSize: Int = 20): PaginatedCameraPhotos {
        return try {
            Log.d(TAG, "페이징 카메라 사진 목록 가져오기 시작 (페이지: $page, 크기: $pageSize)")

            // 카메라 초기화 상태 확인 (타임아웃 없음)
            val isInitialized = try {
                Log.d(TAG, "카메라 초기화 상태 확인 시작")
                isCameraInitialized()
            } catch (e: Exception) {
                Log.e(TAG, "카메라 초기화 상태 확인 실패", e)
                false
            }

            Log.d(TAG, "카메라 초기화 상태: $isInitialized")

            // 인프라 오류는 빈 목록으로 위장하지 않고 예외로 전파한다.
            // (빈 갤러리는 "사진 0장"의 정상 응답에만 사용 — 호출 체인이 Result로 감싸
            // ViewModel이 오류 상태를 표시할 수 있다. 무음 빈 화면 방지.)
            if (!isInitialized) {
                throw IllegalStateException("카메라가 초기화되지 않아 사진 목록을 가져올 수 없습니다")
            }

            // 페이징된 네이티브 메서드 호출 (타임아웃 없음)
            Log.d(TAG, "CameraNative.getCameraFileListPaged() 호출 (페이지: $page, 크기: $pageSize)")
            val photoListJson = CameraNative.getCameraFileListPaged(page, pageSize)
            Log.d(TAG, "페이징 네이티브 메서드 호출 성공, 결과 길이: ${photoListJson.length}")

            // 네이티브는 항상 JSON(성공 데이터 또는 {"error":...})을 반환하므로
            // 빈 문자열/"null"은 비정상 응답이다.
            if (photoListJson.isEmpty() || photoListJson == "null") {
                throw IllegalStateException("네이티브 파일 목록 응답이 비어 있음")
            }

            // JSON 파싱
            val json = try {
                JSONObject(photoListJson)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 파일 목록 JSON 파싱 실패 (len=${photoListJson.length}, head=${photoListJson.take(20)})", e)
                throw IllegalStateException("카메라 파일 목록 응답 파싱 실패", e)
            }

            if (json.has("error")) {
                val error = json.getString("error")
                Log.e(TAG, "카메라 사진 목록 가져오기 오류: $error")
                throw IllegalStateException("카메라 사진 목록 오류: $error")
            }

            // 페이징 정보 추출
            val currentPage = json.optInt("page", page)
            val currentPageSize = json.optInt("pageSize", pageSize)
            val totalFiles = json.optInt("totalFiles", 0)
            val totalPages = json.optInt("totalPages", 0)
            val hasNext = json.optBoolean("hasNext", false)

            val photos = mutableListOf<NativeCameraPhoto>()
            val filesArray = json.optJSONArray("files")

            if (filesArray != null && filesArray.length() > 0) {
                Log.d(TAG, "파일 배열에서 ${filesArray.length()}개 항목 처리 (페이지 $currentPage)")

                for (i in 0 until filesArray.length()) {
                    try {
                        val fileObj = filesArray.getJSONObject(i)

                        val photo = NativeCameraPhoto(
                            path = fileObj.optString("path", ""),
                            name = fileObj.optString("name", ""),
                            size = fileObj.optLong("size", 0),
                            date = fileObj.optLong("date", System.currentTimeMillis()),
                            width = fileObj.optInt("width", 0),
                            height = fileObj.optInt("height", 0),
                            thumbnailPath = null  // 썸네일은 동적으로 로드
                        )

                        if (photo.path.isNotEmpty() && photo.name.isNotEmpty()) {
                            photos.add(photo)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "파일 항목 $i 처리 중 오류", e)
                    }
                }
            }

            Log.d(
                TAG,
                "페이징 카메라 사진 목록 가져오기 완료: ${photos.size}개 (페이지 $currentPage/$totalPages)"
            )

            PaginatedCameraPhotos(
                photos = photos,
                currentPage = currentPage,
                pageSize = currentPageSize,
                totalItems = totalFiles,
                totalPages = totalPages,
                hasNext = hasNext
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 인프라 오류를 빈 목록으로 위장하면 갤러리가 무음으로 빈 화면이 된다 —
            // 호출 체인(PhotoDownloadManager)이 Result.failure 로 감싸 UI에 오류를 전달한다.
            Log.e(TAG, "페이징 카메라 사진 목록 가져오기 실패", e)
            throw e
        }
    }

    // 기존 메서드는 첫 번째 페이지만 반환하도록 수정
    suspend fun getCameraPhotos(): List<NativeCameraPhoto> {
        Log.d(TAG, "getCameraPhotos 호출 - 페이징 버전으로 위임 (첫 페이지)")
        return getCameraPhotosPaged(0, 100).photos
    }

    // 썸네일 가져오기 함수 (타임아웃 없음)
    fun getCameraThumbnail(photoPath: String): ByteArray? {
        return try {
            Log.d(TAG, "썸네일 가져오기: ${LogMask.path(photoPath)}")

            // 카메라 초기화 상태 확인
            if (!isCameraInitialized()) {
                Log.w(TAG, "카메라가 초기화되지 않음 - 썸네일 가져오기 실패")
                return null
            }

            // 직접 호출 (타임아웃 제거)
            val thumbnail = CameraNative.getCameraThumbnail(photoPath)

            if (thumbnail != null && thumbnail.isNotEmpty()) {
                Log.d(TAG, "썸네일 가져오기 성공: ${thumbnail.size} 바이트")
                thumbnail
            } else {
                Log.w(TAG, "썸네일이 없거나 가져오기 실패")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "썸네일 가져오기 실패", e)
            null
        }
    }

    // 실제 파일 다운로드 함수 (타임아웃 없음)
    fun downloadCameraPhoto(photoPath: String): ByteArray? {
        return try {
            Log.d(TAG, "실제 파일 다운로드: ${LogMask.path(photoPath)}")

            // 카메라 초기화 상태 확인
            if (!isCameraInitialized()) {
                Log.w(TAG, "카메라가 초기화되지 않음 - 파일 다운로드 실패")
                return null
            }

            // 직접 호출 (타임아웃 제거)
            val imageData = CameraNative.downloadCameraPhoto(photoPath)

            if (imageData != null && imageData.isNotEmpty()) {
                Log.d(TAG, "실제 파일 다운로드 성공: ${imageData.size} 바이트")
                imageData
            } else {
                Log.w(TAG, "실제 파일이 없거나 다운로드 실패")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "실제 파일 다운로드 실패", e)
            null
        }
    }

    // 네이티브 카메라 사진 정보 데이터 클래스
    data class NativeCameraPhoto(
        val path: String,              // 사진 경로
        val name: String,              // 파일 이름
        val size: Long,                // 파일 크기
        val date: Long,                // 촬영 날짜
        val width: Int,                // 이미지 너비
        val height: Int,               // 이미지 높이
        val thumbnailPath: String? = null  // 썸네일 경로
    )

    data class PaginatedCameraPhotos(
        val photos: List<NativeCameraPhoto>,
        val currentPage: Int,
        val pageSize: Int,
        val totalItems: Int,
        val totalPages: Int,
        val hasNext: Boolean
    )

    // 카메라 기능 정보를 가져오는 새로운 함수
    fun getCameraCapabilities(): CameraCapabilities? {
        return try {
            // getCameraAbilities()를 사용하여 정확한 정보 가져오기
            val abilitiesJson = CameraNative.getCameraAbilities()
            if (abilitiesJson == null) {
                Log.e(TAG, "getCameraAbilities() 반환값이 null")
                return null
            }

            Log.d(TAG, "Abilities JSON 길이: ${abilitiesJson.length}")

            val json = JSONObject(abilitiesJson)

            if (json.has("error")) {
                Log.e(TAG, "카메라 기능 정보 가져오기 오류: ${json.getString("error")}")
                return null
            }

            var model = json.optString("model", "알 수 없음")
            // libgphoto2 abilities 원본 모델명(연결 검증 집계 매칭 키). DeviceInfo override로
            // 덮어써지는 표시용 model과 달리 라이브러리 원본을 그대로 보존한다. 없으면 blank.
            val abilitiesModel = json.optString("model", "")
            val supportsObj = json.optJSONObject("supports")

            // Abilities의 모델명은 제네릭 드라이버명("PTP/IP Camera")이므로
            // 캐시된 DeviceInfo에서 실제 카메라 모델명을 가져와 우선 사용
            val cachedDeviceInfo = usbCameraDeviceInfo
            if (cachedDeviceInfo != null && cachedDeviceInfo.model.isNotEmpty()) {
                model = if (cachedDeviceInfo.manufacturer.isNotEmpty()) {
                    "${cachedDeviceInfo.manufacturer} ${cachedDeviceInfo.model}"
                } else {
                    cachedDeviceInfo.model
                }
            } else {
                // 캐시 없으면 한 번만 조회
                try {
                    val deviceInfoJson = CameraNative.getCameraDeviceInfo()
                    if (deviceInfoJson != null) {
                        val parsed = parseDeviceInfo(deviceInfoJson)
                        usbCameraDeviceInfo = parsed
                        if (parsed.model.isNotEmpty()) {
                            model = if (parsed.manufacturer.isNotEmpty()) {
                                "${parsed.manufacturer} ${parsed.model}"
                            } else {
                                parsed.model
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DeviceInfo 조회 실패, Abilities 모델명 사용: $model")
                }
            }

            if (supportsObj == null) {
                Log.e(TAG, "supports 객체가 없음")
                return null
            }

            // supports 객체에서 기능 정보 추출
            val canCaptureImage = supportsObj.optBoolean("capture_image", false)
            val canCaptureVideo = supportsObj.optBoolean("capture_video", false)
            val canLiveView = supportsObj.optBoolean("capture_preview", false)
            val canConfig = supportsObj.optBoolean("config", false)
            val canTriggerCapture = supportsObj.optBoolean("trigger_capture", false)
            val canDelete = supportsObj.optBoolean("delete", false)
            val canPreview = supportsObj.optBoolean("preview", false)

            Log.d(
                TAG,
                "파싱된 카메라 기능: 모델=$model 사진=$canCaptureImage 비디오=$canCaptureVideo " +
                    "라이브뷰=$canLiveView 설정=$canConfig 트리거=$canTriggerCapture"
            )

            val capabilities = CameraCapabilities(
                model = model,
                canCapturePhoto = canCaptureImage,
                canCaptureVideo = canCaptureVideo,
                canLiveView = canLiveView,
                canTriggerCapture = canTriggerCapture,
                supportsAutofocus = canConfig,
                supportsManualFocus = canConfig,
                supportsFocusPoint = canConfig,
                supportsBurstMode = canCaptureImage && canTriggerCapture,
                supportsTimelapse = canCaptureImage && canTriggerCapture,
                supportsBracketing = canCaptureImage && canConfig,
                supportsBulbMode = canCaptureImage,
                canDownloadFiles = true,  // 기본적으로 가능
                canDeleteFiles = canDelete,
                canPreviewFiles = canPreview,
                availableIsoSettings = emptyList(), // TODO: 위젯에서 파싱 필요
                availableShutterSpeeds = emptyList(), // TODO: 위젯에서 파싱 필요
                availableApertures = emptyList(), // TODO: 위젯에서 파싱 필요
                availableWhiteBalanceSettings = emptyList(), // TODO: 위젯에서 파싱 필요
                supportsRemoteControl = canConfig,
                supportsConfigChange = canConfig,
                batteryLevel = null,
                abilitiesModel = abilitiesModel.ifBlank { null }
            )

            capabilities
        } catch (e: Exception) {
            Log.e(TAG, "카메라 기능 정보 가져오기 실패", e)
            null
        }
    }

    // 최신 파일만 가져오는 최적화된 함수 (촬영 후 사용)
    fun getLatestCameraFile(): NativeCameraPhoto? {
        return try {
            Log.d(TAG, "최신 카메라 파일 가져오기 시작")

            val latestFileJson = try {
                CameraNative.getLatestCameraFile()
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "네이티브 메서드 getLatestCameraFile이 구현되지 않음", e)
                return null
            }

            if (latestFileJson.isNullOrEmpty()) {
                Log.w(TAG, "최신 파일 정보가 비어있음")
                return null
            }

            // JSON 파싱
            val json = try {
                JSONObject(latestFileJson)
            } catch (e: Exception) {
                Log.e(TAG, "최신 파일 JSON 파싱 실패 (len=${latestFileJson.length}, head=${latestFileJson.take(20)})", e)
                return null
            }

            if (!json.optBoolean("success", false)) {
                Log.w(TAG, "최신 파일 가져오기 실패: ${json.optString("error", "알 수 없는 오류")}")
                return null
            }

            val photo = NativeCameraPhoto(
                path = json.optString("path", ""),
                name = json.optString("path", "").substringAfterLast("/"),
                size = json.optLong("size", 0),
                date = json.optLong("date", System.currentTimeMillis()),
                width = 0,
                height = 0,
                thumbnailPath = null
            )

            Log.d(TAG, "최신 파일 가져오기 성공: ${photo.name}")
            photo

        } catch (e: Exception) {
            Log.e(TAG, "최신 파일 가져오기 실패", e)
            null
        }
    }

    // C-3 수정: Subscription tier와 RAW 파일 설정
    fun setSubscriptionTier(tierInt: Int) {
        Log.d(TAG, "구독 티어 설정: $tierInt")
        CameraNative.setSubscriptionTier(tierInt)
    }

    fun setRawFileDownloadEnabled(enabled: Boolean) {
        Log.d(TAG, "RAW 파일 다운로드 설정: $enabled")
        CameraNative.setRawFileDownloadEnabled(enabled)
    }

    fun setLiveViewQuality(quality: Int) {
        Log.d(TAG, "라이브뷰 화질 설정: $quality")
        CameraNative.setLiveViewQuality(quality)
    }

    // C-3 수정: 카메라 연결/초기화 상태 확인 (기존 메서드 이용)
    fun isCameraConnectedNow(): Boolean {
        Log.d(TAG, "카메라 연결 상태 확인")
        return CameraNative.isCameraConnected()
    }

    // C-3 수정: 카메라 파일 목록 가져오기
    fun getCameraFileListNow(): List<String> {
        Log.d(TAG, "카메라 파일 목록 가져오기")
        val fileListJson = CameraNative.getCameraFileList()
        // getCameraFileList()는 JSON 문자열을 반환할 수 있음
        // 기존 CameraViewModel에서는 String으로 사용했으므로 리스트로 변환 필요
        return fileListJson.split(",").filter { it.isNotEmpty() }
    }

    // ── C3 라운드 1: Presentation→JNI 직접 호출 래핑 (2026-04-23) ──

    suspend fun isLibrariesLoaded(): Boolean = withContext(ioDispatcher) {
        CameraNative.isLibrariesLoaded()
    }

    suspend fun setupEnvironmentPaths(pluginDir: String): Boolean = withContext(ioDispatcher) {
        CameraNative.setupEnvironmentPaths(pluginDir)
    }

    // 파일 로그 정책 단일 소유자: 마지막으로 설정한 파일 로그 레벨(baseline).
    // Wi-Fi 연결이 init 구간에 레벨을 일시 상향한 뒤 이 baseline으로 복원한다(하드-0 금지).
    @Volatile
    private var baselineFileLevel: Int = CameraNative.GP_LOG_ERROR

    /** Wi-Fi 연결 등 일시적 레벨 상향 후 복원에 쓰는 현재 파일 로그 baseline 레벨. */
    fun currentFileLevel(): Int = baselineFileLevel

    suspend fun startLogFile(logPath: String, level: Int): Boolean = withContext(ioDispatcher) {
        val started = CameraNative.startLogFile(logPath)
        if (started) {
            CameraNative.setLogLevel(level)
            baselineFileLevel = level
        }
        started
    }

    suspend fun stopLogFile(): Boolean = withContext(ioDispatcher) {
        val stopped = CameraNative.stopLogFile()
        // 파일 로그를 끄면 콜백 레벨도 알려진 조용한 상태(ERROR)로 되돌린다.
        // (기존 stopLogFile은 콜백을 마지막 레벨에 그대로 둬 "꺼도 안 조용한" 비일관 상태였다.)
        CameraNative.setLogLevel(CameraNative.GP_LOG_ERROR)
        baselineFileLevel = CameraNative.GP_LOG_ERROR
        stopped
    }

    suspend fun getLogFileContent(filePath: String): String = withContext(ioDispatcher) {
        CameraNative.getLogFileContent(filePath)
    }

    suspend fun getCameraAbilitiesJson(): String? = withContext(ioDispatcher) {
        CameraNative.getCameraAbilities()
    }

    suspend fun getCameraDeviceInfoJson(): String? = withContext(ioDispatcher) {
        CameraNative.getCameraDeviceInfo()
    }

    suspend fun deleteGphotoSettings(): String = withContext(ioDispatcher) {
        CameraNative.deleteGphotoSettings()
    }

    suspend fun resumeOperations() = withContext(ioDispatcher) {
        CameraNative.resumeOperations()
    }

    suspend fun getCameraPhotoExifJson(photoPath: String): String? = withContext(ioDispatcher) {
        CameraNative.getCameraPhotoExif(photoPath)
    }
}