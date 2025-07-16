package com.inik.camcon.data.datasource.nativesource

import android.content.Context
import android.util.Log
import com.inik.camcon.CameraNative
import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.model.CameraCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
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

    // 카메라 이벤트 리스닝 시작
    fun listenCameraEvents(callback: CameraCaptureListener) {
        Log.d(TAG, "카메라 이벤트 리스닝 시작")
        CameraNative.listenCameraEvents(callback)
    }

    // 카메라 이벤트 리스닝 중지
    fun stopListenCameraEvents() {
        Log.d(TAG, "카메라 이벤트 리스닝 중지")
        CameraNative.stopListenCameraEvents()
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
    fun initCameraWithFd(fd: Int, nativeLibDir: String): Int {
        Log.d(TAG, "카메라 초기화 (FD 기반) 시작: fd=$fd, libDir=$nativeLibDir")
        // 올바른 네이티브 라이브러리 경로 설정
        val applicationInfo = context.applicationInfo
        val correctNativeLibDir = applicationInfo.nativeLibraryDir
        Log.d(TAG, "실제 네이티브 라이브러리 경로: $correctNativeLibDir")

        val result = CameraNative.initCameraWithFd(fd, correctNativeLibDir)
        Log.d(TAG, "카메라 초기화 (FD 기반) 완료: 결과 코드=$result")
        return result
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
        CameraNative.closeCamera()
    }

    // 카메라 감지
    fun detectCamera(): String = CameraNative.detectCamera()

    // 카메라 연결 상태 반환
    fun isCameraConnected(): Boolean = CameraNative.isCameraConnected()

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
    fun buildWidgetJson(): String = CameraNative.buildWidgetJson()

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

    // 페이징을 지원하는 카메라 사진 목록 가져오기
    fun getCameraPhotosPaged(page: Int, pageSize: Int = 20): PaginatedCameraPhotos {
        return try {
            Log.d(TAG, "=== 페이징 카메라 사진 목록 가져오기 시작 (페이지: $page, 크기: $pageSize) ===")

            // 먼저 카메라 연결 상태 확인
            val isConnected = try {
                CameraNative.isCameraConnected()
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 상태 확인 실패", e)
                false
            }

            Log.d(TAG, "카메라 연결 상태: $isConnected")

            if (!isConnected) {
                Log.w(TAG, "카메라가 연결되지 않음, 테스트 데이터 반환")
                return createTestPhotosPaged(page, pageSize)
            }

            // 카메라 이벤트 리스너 일시 중지 (리소스 경합 방지)
            var needsListenerRestart = false
            try {
                Log.d(TAG, "카메라 이벤트 리스너 일시 중지")
                CameraNative.stopListenCameraEvents()
                needsListenerRestart = true

                // 리스너가 완전히 중지될 때까지 충분히 대기
                Thread.sleep(300)
            } catch (e: Exception) {
                Log.w(TAG, "이벤트 리스너 중지 실패 (이미 중지되었을 수 있음)", e)
            }

            // 페이징된 네이티브 메서드 호출
            val photoListJson = try {
                Log.d(TAG, "CameraNative.getCameraFileListPaged() 호출 (페이지: $page, 크기: $pageSize)")

                // 타임아웃을 위한 별도 스레드 사용 (페이징은 더 빠르므로 타임아웃 단축)
                val result = callWithTimeout(30000) {  // 30초 타임아웃 (페이징이므로 더 여유있게)
                    CameraNative.getCameraFileListPaged(page, pageSize)
                }

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
                Log.d(TAG, "카메라에 사진이 없거나 목록을 가져올 수 없음, 테스트 데이터 반환")
                return createTestPhotosPaged(page, pageSize)
            }

            // JSON 파싱
            val json = try {
                JSONObject(photoListJson)
            } catch (e: Exception) {
                Log.e(TAG, "JSON 파싱 실패: $photoListJson", e)
                return createTestPhotosPaged(page, pageSize)
            }

            if (json.has("error")) {
                val error = json.getString("error")
                Log.e(TAG, "카메라 사진 목록 가져오기 오류: $error")
                return createTestPhotosPaged(page, pageSize)
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
            } else {
                Log.d(TAG, "파일 배열이 비어있음 (페이지 $currentPage), 테스트 데이터 반환")
                return createTestPhotosPaged(page, pageSize)
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
            createTestPhotosPaged(page, pageSize)
        }
    }

    // 기존 메서드는 첫 번째 페이지만 반환하도록 수정
    fun getCameraPhotos(): List<NativeCameraPhoto> {
        Log.d(TAG, "getCameraPhotos 호출 - 페이징 버전으로 위임 (첫 페이지)")
        return getCameraPhotosPaged(0, 50).photos
    }

    // 페이징된 테스트 데이터 생성
    private fun createTestPhotosPaged(page: Int, pageSize: Int): PaginatedCameraPhotos {
        val allTestPhotos = createTestPhotos()
        val startIndex = page * pageSize
        val endIndex = minOf(startIndex + pageSize, allTestPhotos.size)

        val pagePhotos = if (startIndex < allTestPhotos.size) {
            allTestPhotos.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        val totalPages = (allTestPhotos.size + pageSize - 1) / pageSize
        val hasNext = page < totalPages - 1

        return PaginatedCameraPhotos(
            photos = pagePhotos,
            currentPage = page,
            pageSize = pageSize,
            totalItems = allTestPhotos.size,
            totalPages = totalPages,
            hasNext = hasNext
        )
    }

    // 썸네일만 가져오는 최적화된 함수
    fun getCameraThumbnailOptimized(photoPath: String): ByteArray? {
        return try {
            Log.d(TAG, "썸네일 최적화 요청: $photoPath")

            // 썸네일 캐시 확인 (메모리 절약)
            val thumbnailData = CameraNative.getCameraThumbnail(photoPath)

            if (thumbnailData != null && thumbnailData.isNotEmpty()) {
                Log.d(TAG, "썸네일 로드 성공: ${thumbnailData.size} bytes")
                thumbnailData
            } else {
                Log.w(TAG, "썸네일 로드 실패: $photoPath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "썸네일 로드 중 예외", e)
            null
        }
    }

    // 카메라에서 사진 목록 가져오기
    fun getCameraPhotosOriginal(): List<NativeCameraPhoto> {
        return try {
            Log.d(TAG, "=== 카메라 사진 목록 가져오기 시작 ===")

            // 먼저 카메라 연결 상태 확인
            val isConnected = try {
                CameraNative.isCameraConnected()
            } catch (e: Exception) {
                Log.e(TAG, "카메라 연결 상태 확인 실패", e)
                false
            }

            Log.d(TAG, "카메라 연결 상태: $isConnected")

            if (!isConnected) {
                Log.w(TAG, "카메라가 연결되지 않음, 테스트 데이터 반환")
                return createTestPhotos()
            }

            // 카메라 이벤트 리스너 일시 중지 (리소스 경합 방지)
            var needsListenerRestart = false
            try {
                Log.d(TAG, "카메라 이벤트 리스너 일시 중지")
                CameraNative.stopListenCameraEvents()
                needsListenerRestart = true

                // 리스너가 완전히 중지될 때까지 충분히 대기
                Thread.sleep(300)
            } catch (e: Exception) {
                Log.w(TAG, "이벤트 리스너 중지 실패 (이미 중지되었을 수 있음)", e)
            }

            // 네이티브 메서드를 통해 카메라 파일 목록 가져오기
            val photoListJson = try {
                Log.d(TAG, "CameraNative.getCameraFileList() 호출 (타임아웃: 15초)")

                // 타임아웃을 위한 별도 스레드 사용
                val result = callWithTimeout(15000) {
                    CameraNative.getCameraFileList()
                }

                Log.d(TAG, "네이티브 메서드 호출 성공, 결과 길이: ${result?.length ?: 0}")
                result
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "네이티브 메서드 getCameraFileList가 구현되지 않음", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "네이티브 메서드 호출 중 예외 발생 (타임아웃 또는 리소스 경합)", e)
                null
            }

            // 이벤트 리스너 재시작은 Repository에서 처리하도록 함 (JNI 스레드 문제 방지)
            if (needsListenerRestart) {
                Log.d(TAG, "이벤트 리스너 재시작은 Repository에서 처리됩니다")
            }

            Log.d(TAG, "카메라 파일 목록 JSON: $photoListJson")

            if (photoListJson.isNullOrEmpty() || photoListJson == "null") {
                Log.d(TAG, "카메라에 사진이 없거나 목록을 가져올 수 없음, 테스트 데이터 반환")
                return createTestPhotos()
            }

            // JSON 파싱
            val json = try {
                JSONObject(photoListJson)
            } catch (e: Exception) {
                Log.e(TAG, "JSON 파싱 실패: $photoListJson", e)
                return createTestPhotos()
            }

            if (json.has("error")) {
                val error = json.getString("error")
                Log.e(TAG, "카메라 사진 목록 가져오기 오류: $error")

                // 오류가 있어도 빈 목록 대신 테스트 데이터 반환 (개발/테스트용)
                Log.d(TAG, "오류 발생으로 테스트 데이터 반환")
                return createTestPhotos()
            }

            val photos = mutableListOf<NativeCameraPhoto>()
            val filesArray = json.optJSONArray("files")

            if (filesArray != null && filesArray.length() > 0) {
                Log.d(TAG, "파일 배열에서 ${filesArray.length()}개 항목 처리")

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
                            thumbnailPath = fileObj.optString("thumbnail", null)
                        )

                        if (photo.path.isNotEmpty() && photo.name.isNotEmpty()) {
                            photos.add(photo)
                            Log.d(TAG, "사진 추가: ${photo.name} (${photo.size} bytes)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "파일 항목 $i 처리 중 오류", e)
                    }
                }
            } else {
                Log.d(TAG, "파일 배열이 비어있음 (실제 카메라에 사진이 없을 수 있음), 테스트 데이터 반환")
                return createTestPhotos()
            }

            Log.d(TAG, "=== 카메라 사진 목록 가져오기 완료: ${photos.size}개 ===")

            // 실제 사진이 없으면 테스트 데이터 반환
            if (photos.isEmpty()) {
                Log.d(TAG, "실제 사진이 없으므로 테스트 데이터 반환")
                return createTestPhotos()
            }

            photos
        } catch (e: Exception) {
            Log.e(TAG, "카메라 사진 목록 가져오기 실패", e)
            // 오류 발생 시에도 테스트 데이터 반환 (개발/테스트용)
            Log.d(TAG, "오류 발생으로 테스트 데이터 반환")
            createTestPhotos()
        }
    }

    // 타임아웃과 함께 함수 호출하는 헬퍼 함수
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

    // 테스트용 사진 데이터 생성
    private fun createTestPhotos(): List<NativeCameraPhoto> {
        return listOf(
            NativeCameraPhoto(
                path = "/store_00020001/DCIM/100CANON/IMG_0001.JPG",
                name = "IMG_0001.JPG",
                size = 4567890,
                date = System.currentTimeMillis() - 3600000,
                width = 6000,
                height = 4000
            ),
            NativeCameraPhoto(
                path = "/store_00020001/DCIM/100CANON/IMG_0002.JPG",
                name = "IMG_0002.JPG",
                size = 5123456,
                date = System.currentTimeMillis() - 1800000,
                width = 6000,
                height = 4000
            ),
            NativeCameraPhoto(
                path = "/store_00020001/DCIM/100CANON/IMG_0003.CR2",
                name = "IMG_0003.CR2",
                size = 25123456,
                date = System.currentTimeMillis() - 900000,
                width = 6000,
                height = 4000
            )
        )
    }

    // 카메라에서 썸네일 가져오기
    fun getCameraThumbnail(photoPath: String): ByteArray? {
        return try {
            Log.d(TAG, "썸네일 가져오기: $photoPath")
            val thumbnail = try {
                CameraNative.getCameraThumbnail(photoPath)
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "네이티브 메서드 getCameraThumbnail이 구현되지 않음")
                null
            }

            if (thumbnail is ByteArray && thumbnail.isNotEmpty()) {
                Log.d(TAG, "썸네일 가져오기 성공: ${thumbnail.size} 바이트")
                thumbnail
            } else {
                Log.d(TAG, "썸네일이 없거나 가져오기 실패")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "썸네일 가져오기 실패", e)
            null
        }
    }

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
}