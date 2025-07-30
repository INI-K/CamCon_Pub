package com.inik.camcon.data.datasource.nativesource

import android.content.Context
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 네이티브 기능을 호출하는 데이터소스
 */
@Singleton
class NativeCameraDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "네이티브_카메라_데이터소스"
    }

    // initCameraWithFd 중복 호출 방지용 Mutex
    private val initCameraWithFdMutex = Mutex()

    // 카메라 이벤트 리스닝 시작
    fun listenCameraEvents(callback: CameraCaptureListener) {
        Log.d(TAG, "카메라 이벤트 리스닝 시작")
        CameraNative.listenCameraEvents(callback)
    }

    // 이벤트 리스너 중복 호출 방지용
    private val isStoppingEventListener = AtomicBoolean(false)
    private val isClosingCamera = AtomicBoolean(false)

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
            Thread {
                Thread.sleep(1000)
                isStoppingEventListener.set(false)
                Log.d(TAG, "이벤트 리스너 중지 상태 리셋")
            }.start()
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
        Log.d(TAG, "카메라 초기화 시작")
        val result = CameraNative.initCamera()
        Log.d(TAG, "카메라 초기화 완료: 결과=$result")
        return result
    }

    // 파일 디스크립터 기반 초기화
    fun initCameraWithFd(fd: Int, nativeLibDir: String): Int = runBlocking {
        initCameraWithFdMutex.withLock {
            Log.d(TAG, "카메라 초기화 (FD 기반) 시작: fd=$fd, libDir=$nativeLibDir")
            // 올바른 네이티브 라이브러리 경로 설정
            val applicationInfo = context.applicationInfo
            val correctNativeLibDir = applicationInfo.nativeLibraryDir
            Log.d(TAG, "실제 네이티브 라이브러리 경로: $correctNativeLibDir")

            val result = CameraNative.initCameraWithFd(fd, correctNativeLibDir)
            Log.d(TAG, "카메라 초기화 (FD 기반) 완료: 결과 코드=$result")
            result
        }
    }

    // 동기식 사진 촬영 (성공시 0 이라고 가정)
    fun capturePhoto(): Boolean = CameraNative.capturePhoto() == 0

    // 카메라 요약 정보를 받아 Domain 모델인 Camera로 변환
    fun getCameraSummary(): Camera {
        // 예시: summary 문자열이 "id,name,isActive" 형식
        val summary = CameraNative.getCameraSummary()
        val parts = summary.split(",")
        return Camera(
            id = parts.getOrNull(0) ?: "0",
            name = parts.getOrNull(1) ?: "알 수 없음",
            isActive = parts.getOrNull(2)?.toBoolean() ?: false
        )
    }

    // 카메라 종료
    fun closeCamera() {
        // 중복 호출 방지
        if (!isClosingCamera.compareAndSet(false, true)) {
            Log.d(TAG, "카메라 종료가 이미 진행 중 - 중복 방지")
            return
        }

        try {
            Log.d(TAG, "카메라 종료")
            CameraNative.closeCamera()
        } catch (e: Exception) {
            Log.e(TAG, "카메라 종료 중 오류", e)
        } finally {
            // 2초 후 상태 리셋
            Thread {
                Thread.sleep(2000)
                isClosingCamera.set(false)
                Log.d(TAG, "카메라 종료 상태 리셋")
            }.start()
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
        Log.d(TAG, "⚠️ buildWidgetJson 호출 - NativeCameraDataSource에서 직접 네이티브 호출 (마스터 데이터 미사용)")
        val result = CameraNative.buildWidgetJson()
        Log.d(TAG, "직접 네이티브 호출 완료 - 위젯 JSON 길이: ${result.length}")
        return result
    }

    // 카메라 설정 쿼리
    fun queryConfig() {
        CameraNative.queryConfig()
    }

    // 지원하는 카메라 목록 반환
    fun getSupportedCameras(): Array<String>? = CameraNative.getSupportedCameras()

    // 지정된 모델의 상세 정보 반환
    fun getCameraDetails(model: String): Array<String>? = CameraNative.getCameraDetails(model)

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
    fun getCameraPhotosPaged(page: Int, pageSize: Int = 20): PaginatedCameraPhotos {
        return try {
            Log.d(TAG, "=== 페이징 카메라 사진 목록 가져오기 시작 (페이지: $page, 크기: $pageSize) ===")

            // 카메라 이벤트 리스너 일시 중지 (리소스 경합 방지)
            var needsListenerRestart = false
            try {
                Log.d(TAG, "카메라 이벤트 리스너 일시 중지")
                CameraNative.stopListenCameraEvents()
                needsListenerRestart = true

                // 리스너가 완전히 중지될 때까지 충분히 대기
                Thread.sleep(1000)
                Log.d(TAG, "이벤트 리스너 중지 대기 완료")
            } catch (e: Exception) {
                Log.w(TAG, "이벤트 리스너 중지 실패 (이미 중지되었을 수 있음)", e)
            }

            // 카메라 초기화 상태 확인 (타임아웃 없음)
            val isInitialized = try {
                Log.d(TAG, "카메라 초기화 상태 확인 시작")
                isCameraInitialized()
            } catch (e: Exception) {
                Log.e(TAG, "카메라 초기화 상태 확인 실패", e)
                false
            }

            Log.d(TAG, "카메라 초기화 상태: $isInitialized")

            if (!isInitialized) {
                Log.w(TAG, "카메라가 초기화되지 않음")
                return PaginatedCameraPhotos(
                    photos = emptyList(),
                    currentPage = page,
                    pageSize = pageSize,
                    totalItems = 0,
                    totalPages = 0,
                    hasNext = false
                )
            }

            // 페이징된 네이티브 메서드 호출 (타임아웃 없음)
            val photoListJson = try {
                Log.d(TAG, "CameraNative.getCameraFileListPaged() 호출 (페이지: $page, 크기: $pageSize)")

                // 직접 호출 (타임아웃 제거)
                val result = CameraNative.getCameraFileListPaged(page, pageSize)

                Log.d(TAG, "페이징 네이티브 메서드 호출 성공, 결과 길이: ${result?.length ?: 0}")
                result
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "네이티브 메서드 getCameraFileListPaged가 구현되지 않음", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "페이징 네이티브 메서드 호출 중 예외 발생", e)
                null
            }

            // 이벤트 리스너 재시작은 Repository에서 처리하도록 함
            if (needsListenerRestart) {
                Log.d(TAG, "이벤트 리스너 재시작은 Repository에서 처리됩니다")
            }

            Log.d(TAG, "카메라 파일 목록 JSON: $photoListJson")

            if (photoListJson.isNullOrEmpty() || photoListJson == "null") {
                Log.d(TAG, "카메라에 사진이 없거나 목록을 가져올 수 없음")
                return PaginatedCameraPhotos(
                    photos = emptyList(),
                    currentPage = page,
                    pageSize = pageSize,
                    totalItems = 0,
                    totalPages = 0,
                    hasNext = false
                )
            }

            // JSON 파싱
            val json = try {
                JSONObject(photoListJson)
            } catch (e: Exception) {
                Log.e(TAG, "JSON 파싱 실패: $photoListJson", e)
                return PaginatedCameraPhotos(
                    photos = emptyList(),
                    currentPage = page,
                    pageSize = pageSize,
                    totalItems = 0,
                    totalPages = 0,
                    hasNext = false
                )
            }

            if (json.has("error")) {
                val error = json.getString("error")
                Log.e(TAG, "카메라 사진 목록 가져오기 오류: $error")
                return PaginatedCameraPhotos(
                    photos = emptyList(),
                    currentPage = page,
                    pageSize = pageSize,
                    totalItems = 0,
                    totalPages = 0,
                    hasNext = false
                )
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
                            Log.d(TAG, "사진 추가: ${photo.name} (${photo.size} bytes)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "파일 항목 $i 처리 중 오류", e)
                    }
                }
            }

            Log.d(
                TAG,
                "=== 페이징 카메라 사진 목록 가져오기 완료: ${photos.size}개 (페이지 $currentPage/$totalPages) ==="
            )

            PaginatedCameraPhotos(
                photos = photos,
                currentPage = currentPage,
                pageSize = currentPageSize,
                totalItems = totalFiles,
                totalPages = totalPages,
                hasNext = hasNext
            )

        } catch (e: Exception) {
            Log.e(TAG, "페이징 카메라 사진 목록 가져오기 실패", e)
            PaginatedCameraPhotos(
                photos = emptyList(),
                currentPage = page,
                pageSize = pageSize,
                totalItems = 0,
                totalPages = 0,
                hasNext = false
            )
        }
    }

    // 기존 메서드는 첫 번째 페이지만 반환하도록 수정
    fun getCameraPhotos(): List<NativeCameraPhoto> {
        Log.d(TAG, "getCameraPhotos 호출 - 페이징 버전으로 위임 (첫 페이지)")
        return getCameraPhotosPaged(0, 100).photos
    }

    // 썸네일 가져오기 함수 (타임아웃 없음)
    fun getCameraThumbnail(photoPath: String): ByteArray? {
        return try {
            Log.d(TAG, "썸네일 가져오기: $photoPath")

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
            Log.d(TAG, "실제 파일 다운로드: $photoPath")

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

    // 타임아웃과 함께 함수 호출하는 헬퍼 함수 (미사용)
    /*
    private fun <T> callWithTimeout(timeoutMs: Long, action: () -> T): T? {
        return try {
            val result = arrayOfNulls<Any>(1)
            val exception = arrayOfNulls<Exception>(1)

            val thread = Thread {
                try {
                    result[0] = action()
                } catch (e: Exception) {
                    exception[0] = e
                }
            }

            thread.start()
            thread.join(timeoutMs)

            if (thread.isAlive) {
                thread.interrupt()
                throw Exception("작업이 ${timeoutMs}ms 내에 완료되지 않음 (타임아웃)")
            }

            exception[0]?.let { throw it }

            @Suppress("UNCHECKED_CAST")
            result[0] as T?
        } catch (e: Exception) {
            throw e
        }
    }
    */

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
            val summaryJson = CameraNative.getCameraSummary()
            val json = JSONObject(summaryJson as String)

            if (json.has("error")) {
                Log.e(TAG, "카메라 기능 정보 가져오기 오류: ${json.getString("error")}")
                return null
            }

            val model = json.optString("model", "알 수 없음")
            val supportsLiveView = json.optBoolean("supportsLiveView", false)
            val canTriggerCapture = json.optBoolean("canTriggerCapture", true)

            // 추가 기능 정보를 가져오기 위해 abilities 조회
            val abilitiesJson = CameraNative.listCameraAbilities()
            val abilities = try {
                JSONObject(abilitiesJson as String)
            } catch (e: Exception) {
                Log.e(TAG, "기능 정보 파싱 실패: $abilitiesJson", e)
                JSONObject()
            }

            CameraCapabilities(
                model = model,
                canCapturePhoto = canTriggerCapture,
                canCaptureVideo = abilities.optBoolean("captureVideo", false),
                canLiveView = supportsLiveView,
                canTriggerCapture = canTriggerCapture,
                supportsAutofocus = true, // TODO: 실제 값으로 대체 필요
                supportsManualFocus = abilities.optBoolean("config", false),
                supportsFocusPoint = false, // TODO: 위젯에서 확인 필요
                supportsBurstMode = abilities.optBoolean("burstMode", false),
                supportsTimelapse = abilities.optBoolean("timelapse", false),
                supportsBracketing = false, // TODO: 위젯에서 확인 필요
                supportsBulbMode = abilities.optBoolean("bulbMode", false),
                canDownloadFiles = abilities.optBoolean("fileDownload", true),
                canDeleteFiles = abilities.optBoolean("fileDelete", false),
                canPreviewFiles = abilities.optBoolean("filePreview", false),
                availableIsoSettings = emptyList(), // TODO: 위젯에서 파싱 필요
                availableShutterSpeeds = emptyList(), // TODO: 위젯에서 파싱 필요
                availableApertures = emptyList(), // TODO: 위젯에서 파싱 필요
                availableWhiteBalanceSettings = emptyList(), // TODO: 위젯에서 파싱 필요
                supportsRemoteControl = abilities.optBoolean("remoteControl", false),
                supportsConfigChange = abilities.optBoolean("configChange", false),
                batteryLevel = null
            )
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
                Log.e(TAG, "최신 파일 JSON 파싱 실패: $latestFileJson", e)
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
}