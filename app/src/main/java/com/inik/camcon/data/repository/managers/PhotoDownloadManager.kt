package com.inik.camcon.data.repository.managers

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.inik.camcon.BuildConfig
import android.util.Log
import com.inik.camcon.data.datasource.local.AppPreferencesDataSource
import com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource
import com.inik.camcon.data.util.ExifCaptureTime
import com.inik.camcon.domain.model.CameraCapabilities
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.domain.model.CameraSettings
import com.inik.camcon.domain.model.CapturedPhoto
import com.inik.camcon.domain.model.PaginatedCameraPhotos
import com.inik.camcon.domain.model.SubscriptionTier
import com.inik.camcon.domain.usecase.ColorTransferUseCase
import com.inik.camcon.domain.usecase.FilmLutUseCase
import com.inik.camcon.domain.usecase.GetSubscriptionUseCase
import com.inik.camcon.domain.usecase.ValidateFeatureAccessUseCase
import com.inik.camcon.domain.usecase.ValidateImageFormatUseCase
import com.inik.camcon.domain.manager.PhotoCaptureEventManager
import com.inik.camcon.utils.Constants
import com.inik.camcon.utils.LogMask
import com.inik.camcon.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class PhotoDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nativeDataSource: NativeCameraDataSource,
    private val appPreferencesDataSource: AppPreferencesDataSource,
    private val colorTransferUseCase: ColorTransferUseCase,
    private val filmLutUseCase: FilmLutUseCase,
    private val photoCaptureEventManager: PhotoCaptureEventManager,
    private val getSubscriptionUseCase: GetSubscriptionUseCase,
    private val validateImageFormatUseCase: ValidateImageFormatUseCase,
    private val validateFeatureAccessUseCase: ValidateFeatureAccessUseCase,
    private val transferProgressTracker: TransferProgressTracker,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "사진다운로드매니저"
        private const val FREE_TIER_MAX_DIMENSION = 2000 // FREE 티어 최대 장축 크기

        /**
         * 요청 파일명과 MediaStore 가 실제 부여한 DISPLAY_NAME 이 다른지 판정(순수 함수, 단위 테스트 대상).
         *
         * MediaStore 는 같은 relativePath 에 물리 파일이 이미 존재하면 DISPLAY_NAME 충돌을 조용히
         * "name (1).JPG" 로 리네임할 수 있어, 사진 내용은 요청 컷인데 파일명이 밀려 저장되는 버그를 만든다.
         *
         * - actual 이 null 이면(재조회 실패) 불일치로 보지 않는다 — 조회 실패가 저장 실패를 의미하진 않으므로
         *   불필요한 재시도/삭제를 피한다.
         * - 비교는 대소문자 무시(일부 파일시스템/제조사 MediaStore 가 확장자 대소문자를 정규화).
         */
        @JvmStatic
        @androidx.annotation.VisibleForTesting
        fun isDisplayNameMismatch(requested: String, actual: String?): Boolean {
            if (actual == null) return false
            return !requested.equals(actual, ignoreCase = true)
        }
    }

    /**
     * 색감 전송 직렬화 게이트.
     *
     * @Singleton 이므로 연사/듀얼슬롯에서 색감 전송이 동시에 여러 건 실행되면
     * 각 건이 4096px 비트맵을 디코딩해 동시 메모리 사용이 누적, OOM 위험이 커진다.
     * 한 번에 한 건만 처리하도록 직렬화한다.
     */
    private val colorTransferSemaphore = Semaphore(1)

    /**
     * 필름 시뮬레이션·색감 전송의 무거운 디코드/LUT/재인코딩 전용 워커 디스패처.
     *
     * 24MP(6048×4024 ≈ 93MB) 풀 디코드 → LUT → 재인코딩은 CPU·메모리를 크게 먹는다.
     * 이를 [ioDispatcher](공유 IO 풀, 기본 우선순위)에서 돌리면 UI 렌더/입력 스레드와 CPU 를
     * 다투어 저사양(3GB) 기기에서 프레임 드랍이 발생한다. 단일 스레드 + [THREAD_PRIORITY_BACKGROUND]
     * 로 직렬화·저우선순위화하여, 다운로드(별도 IO 경로)는 막지 않으면서 UI 우선순위를 보장한다.
     * ([colorTransferSemaphore] 가 동시성 1 을 이미 보장하므로 스레드도 1 개면 충분하다.)
     *
     * @Singleton 이므로 프로세스 수명과 함께 유지되는 데몬 스레드 1 개만 쓴다.
     */
    private val imageProcessingDispatcher: CoroutineDispatcher by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread({
                // Linux nice 값을 백그라운드로 낮춰 UI/렌더 스레드에 CPU 를 양보한다(Android 권장 방식).
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                r.run()
            }, "camcon-image-proc").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }

    /**
     * 포맷/티어 게이팅 단일 지점 방어선(CLAUDE.md §2).
     *
     * capture/liveview 직접 콜백 등 CameraEventManager 게이팅 래퍼를 거치지 않는 경로가
     * 이 저장 진입부로 직행할 수 있으므로, [ValidateImageFormatUseCase]로 다시 한 번 검증한다.
     * 차단 대상은 '티어 사유'뿐이다 — 미지원 RAW(티어/설정)와 티어 미지원 일반 포맷
     * (예: FREE의 PNG). 미지 확장자는 기존 통과 동작을 유지해 촬영 직콜백의 비정형
     * 파일명을 새로 막지 않는다.
     *
     * @return 저장을 진행해도 되면 true, 티어 게이팅으로 차단해야 하면 false.
     */
    private suspend fun isDownloadAllowedByGating(fileName: String): Boolean {
        val result = validateImageFormatUseCase.validateFormat(fileName)
        if (result.isSupported) {
            return true
        }
        // 티어와 무관한 미지 포맷(needsUpgrade=false, RAW 아님)은 기존 동작대로 통과.
        if (!result.isRawFile && !result.needsUpgrade) {
            return true
        }
        Log.w(
            TAG,
            "⛔ 포맷 게이팅 차단 — 저장 중단: $fileName" +
                " (RAW=${result.isRawFile}, 제조사: ${result.manufacturer})"
        )
        return false
    }

    suspend fun getCameraPhotos(): Result<List<CameraPhoto>> {
        return withContext(ioDispatcher) {
            try {
                Log.d("사진다운로드매니저", "카메라 사진 목록 가져오기 시작")

                // 네이티브 데이터소스를 통해 카메라의 사진 목록 가져오기
                val nativePhotos = nativeDataSource.getCameraPhotos()

                // 사진 정보를 CameraPhoto 모델로 변환
                val cameraPhotos = nativePhotos.map { nativePhoto ->
                    CameraPhoto(
                        path = nativePhoto.path,
                        name = nativePhoto.name,
                        size = nativePhoto.size,
                        date = nativePhoto.date,
                        width = nativePhoto.width,
                        height = nativePhoto.height,
                        thumbnailPath = nativePhoto.thumbnailPath
                    )
                }

                Log.d("사진다운로드매니저", "카메라 사진 목록 가져오기 완료: ${cameraPhotos.size}개")
                Result.success(cameraPhotos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("사진다운로드매니저", "카메라 사진 목록 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getCameraPhotosPaged(
        page: Int,
        pageSize: Int,
        isPhotoPreviewMode: Boolean,
        onEventListenerRestart: suspend () -> Unit
    ): Result<PaginatedCameraPhotos> {
        return withContext(ioDispatcher) {
            try {
                Log.d("사진다운로드매니저", "페이징 카메라 사진 목록 가져오기 시작 (페이지: $page, 크기: $pageSize)")

                // 네이티브 데이터소스를 통해 페이징된 카메라 사진 목록 가져오기
                val paginatedNativePhotos = nativeDataSource.getCameraPhotosPaged(page, pageSize)

                // 이벤트 리스너 재시작 처리
                Log.d("사진다운로드매니저", "페이징 사진 목록 가져오기 후 이벤트 리스너 상태 확인")
                kotlinx.coroutines.delay(500)

                // 사진 미리보기 모드에서는 이벤트 리스너 재시작 금지
                if (isPhotoPreviewMode) {
                    Log.d("사진다운로드매니저", "사진 미리보기 모드 - 이벤트 리스너 재시작 생략")
                } else {
                    try {
                        Log.d("사진다운로드매니저", "이벤트 리스너 재시작 시도")
                        onEventListenerRestart()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("사진다운로드매니저", "이벤트 리스너 재시작 실패", e)
                    }
                }

                // 사진 정보를 도메인 모델로 변환
                val cameraPhotos = paginatedNativePhotos.photos.map { nativePhoto ->
                    CameraPhoto(
                        path = nativePhoto.path,
                        name = nativePhoto.name,
                        size = nativePhoto.size,
                        date = nativePhoto.date,
                        width = nativePhoto.width,
                        height = nativePhoto.height,
                        thumbnailPath = nativePhoto.thumbnailPath
                    )
                }

                val domainPaginatedPhotos = PaginatedCameraPhotos(
                    photos = cameraPhotos,
                    currentPage = paginatedNativePhotos.currentPage,
                    pageSize = paginatedNativePhotos.pageSize,
                    totalItems = paginatedNativePhotos.totalItems,
                    totalPages = paginatedNativePhotos.totalPages,
                    hasNext = paginatedNativePhotos.hasNext
                )

                Log.d(
                    "사진다운로드매니저",
                    "페이징 카메라 사진 목록 가져오기 완료: ${cameraPhotos.size}개 (페이지 ${paginatedNativePhotos.currentPage}/${paginatedNativePhotos.totalPages})"
                )
                Result.success(domainPaginatedPhotos)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("사진다운로드매니저", "페이징 카메라 사진 목록 가져오기 실패", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getCameraThumbnail(
        photoPath: String,
        isConnected: Boolean,
        isInitializing: Boolean,
        isNativeCameraConnected: Boolean
    ): Result<ByteArray> {
        return withContext(ioDispatcher) {
            try {
                Log.d("사진다운로드매니저", "썸네일 가져오기 시작: ${LogMask.path(photoPath)}")

                // 카메라가 현재 초기화 중인지 확인 (초기화 중에는 대기)
                if (isInitializing) {
                    Log.w("사진다운로드매니저", "getCameraThumbnail: 카메라 초기화 중 - 짧게 대기 후 재시도")
                    kotlinx.coroutines.delay(100)  // 초기화 완료 대기 (300ms -> 100ms로 단축)

                    // 초기화가 완료되지 않았으면 실패 처리
                    if (isInitializing) {
                        Log.w("사진다운로드매니저", "getCameraThumbnail: 카메라 초기화 지속 중 - 썸네일 로딩 불가")
                        return@withContext Result.failure(Exception("카메라 초기화 중 - 썸네일 로딩 불가"))
                    }
                }

                // 네이티브 카메라 연결 상태 확인
                if (!isConnected || !isNativeCameraConnected) {
                    Log.w("사진다운로드매니저", "getCameraThumbnail: 카메라 연결 안됨 - 썸네일 로딩 불가")
                    return@withContext Result.failure(Exception("카메라가 연결되지 않음"))
                }

                // 폴더와 파일명 분리
                val file = File(photoPath)
                val folderPath = file.parent ?: ""
                val fileName = file.name
                Log.d("사진다운로드매니저", "썸네일 요청: 폴더=${LogMask.path(folderPath)}, 파일=$fileName")

                // 카메라 사용 중 상황을 고려한 재시도 로직 개선
                var retryCount = 0
                val maxRetries = 3  // 재시도 횟수 증가
                var lastException: Exception? = null

                while (retryCount <= maxRetries) {
                    try {
                        // 네이티브 호출 전 상태 재확인
                        if (!isConnected) {
                            throw Exception("카메라 연결이 끊어짐")
                        }

                        val thumbnailData = nativeDataSource.getCameraThumbnail(photoPath)

                        if (thumbnailData != null && thumbnailData.isNotEmpty()) {
                            Log.d("사진다운로드매니저", "네이티브 다운로드 성공: ${thumbnailData.size} bytes")

                            return@withContext Result.success(thumbnailData)
                        } else {
                            throw Exception("썸네일 데이터가 비어있음")
                        }

                    } catch (e: Exception) {
                        lastException = e
                        retryCount++

                        if (retryCount <= maxRetries) {
                            Log.w(
                                "사진다운로드매니저",
                                "썸네일 가져오기 실패 (시도 $retryCount/$maxRetries): ${e.message}"
                            )

                            // 카메라 사용 중인 경우 더 짧은 간격으로 재시도
                            val delayMs = when {
                                e.message?.contains(
                                    "사용 중",
                                    true
                                ) == true -> 200L  // 카메라 사용 중일 때 짧게 대기
                                retryCount == 1 -> 300L
                                retryCount == 2 -> 500L
                                else -> 800L
                            }
                            kotlinx.coroutines.delay(delayMs)

                            // 카메라 상태 재확인
                            if (!isConnected) {
                                Log.w("사진다운로드매니저", "카메라 연결이 끊어져서 썸네일 재시도 중단")
                                break
                            }
                        }
                    }
                }

                // 모든 재시도 실패
                Log.e("사진다운로드매니저", "썸네일 재시도 $maxRetries 실패: ${LogMask.path(photoPath)}", lastException)

                return@withContext Result.failure(lastException ?: Exception("썸네일을 가져올 수 없습니다"))

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("사진다운로드매니저", "썸네일 가져오기 중 예외", e)
                return@withContext Result.failure(e)
            }
        }
    }

    suspend fun downloadPhotoFromCamera(
        photoId: String,
        cameraCapabilities: CameraCapabilities?,
        cameraSettings: CameraSettings?
    ): Result<CapturedPhoto> {
        return withContext(ioDispatcher) {
            try {
                val isRaw = validateImageFormatUseCase.isRawFile(photoId)
                val startedAtMs = System.currentTimeMillis()
                Log.d(
                    "사진다운로드매니저",
                    "카메라에서 사진 다운로드 시작: ${LogMask.path(photoId)}${if (isRaw) " [RAW]" else ""}"
                )

                // 네이티브 코드를 통해 실제 파일 데이터 다운로드
                val imageData = nativeDataSource.downloadCameraPhoto(photoId)

                if (imageData != null && imageData.isNotEmpty()) {
                    Log.d(
                        "사진다운로드매니저",
                        "네이티브 다운로드 성공: ${imageData.size} bytes" +
                            "${if (isRaw) " [RAW]" else ""} (${System.currentTimeMillis() - startedAtMs}ms)"
                    )

                    // 임시 파일 생성
                    val fileName = photoId.substringAfterLast("/")
                    val tempFile = File(context.cacheDir, "temp_downloads/$fileName")

                    // 디렉토리 생성 - 실패 시 대체 경로 사용
                    if (!tempFile.parentFile?.exists()!!) {
                        val created = tempFile.parentFile?.mkdirs() ?: false
                        if (!created) {
                            Log.w("사진다운로드매니저", "디렉토리 생성 실패, 캐시 루트 사용")
                            val fallbackFile = File(context.cacheDir, fileName)
                            // 데이터를 파일로 저장 - 안전한 쓰기
                            try {
                                fallbackFile.writeBytes(imageData)
                                Log.d("사진다운로드매니저", "대체 경로 파일 저장 완료: ${LogMask.path(fallbackFile.absolutePath)}")
                            } catch (e: Exception) {
                                Log.e("사진다운로드매니저", "대체 경로 파일 저장 실패", e)
                                return@withContext Result.failure(Exception("파일 저장 실패: ${e.message}"))
                            }

                            // 후처리 (MediaStore 저장 등)
                            val finalPath = postProcessPhoto(fallbackFile.absolutePath, fileName)

                            val capturedPhoto = CapturedPhoto(
                                id = UUID.randomUUID().toString(),
                                filePath = finalPath,
                                thumbnailPath = null,
                                // 정렬 기준 안정화: 원본 바이트의 EXIF 촬영 시각 사용, 실패 시 현재 시각 폴백
                                captureTime = ExifCaptureTime.parseMillis(imageData)
                                    ?: System.currentTimeMillis(),
                                cameraModel = cameraCapabilities?.model ?: "알 수 없음",
                                settings = cameraSettings,
                                size = imageData.size.toLong(),
                                width = 0,
                                height = 0,
                                isDownloading = false,
                                downloadCompleteTime = System.currentTimeMillis()
                            )

                            Log.d("사진다운로드매니저", "✅ 카메라에서 사진 다운로드 완료: ${LogMask.path(finalPath)}")
                            return@withContext Result.success(capturedPhoto)
                        }
                    }

                    // 데이터를 파일로 저장 - 안전한 쓰기
                    try {
                        tempFile.writeBytes(imageData)
                        Log.d("사진다운로드매니저", "임시 파일 저장 완료: ${LogMask.path(tempFile.absolutePath)}")
                    } catch (e: Exception) {
                        Log.e("사진다운로드매니저", "임시 파일 저장 실패", e)
                        return@withContext Result.failure(Exception("파일 저장 실패: ${e.message}"))
                    }

                    // 후처리 (MediaStore 저장 등)
                    val finalPath = postProcessPhoto(tempFile.absolutePath, fileName)

                    val capturedPhoto = CapturedPhoto(
                        id = UUID.randomUUID().toString(),
                        filePath = finalPath,
                        thumbnailPath = null,
                        // 정렬 기준 안정화: 원본 바이트의 EXIF 촬영 시각 사용, 실패 시 현재 시각 폴백
                        captureTime = ExifCaptureTime.parseMillis(imageData)
                            ?: System.currentTimeMillis(),
                        cameraModel = cameraCapabilities?.model ?: "알 수 없음",
                        settings = cameraSettings,
                        size = imageData.size.toLong(),
                        width = 0,
                        height = 0,
                        isDownloading = false,
                        downloadCompleteTime = System.currentTimeMillis()
                    )

                    Log.d("사진다운로드매니저", "✅ 카메라에서 사진 다운로드 완료: ${LogMask.path(finalPath)}")
                    Result.success(capturedPhoto)
                } else {
                    Log.e("사진다운로드매니저", "네이티브 다운로드 실패: 데이터가 비어있음")
                    Result.failure(Exception("카메라에서 사진 데이터를 가져올 수 없습니다"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("사진다운로드매니저", "카메라에서 사진 다운로드 실패", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Native에서 다운로드된 바이트 배열을 파일로 저장
     */
    suspend fun handleNativePhotoDownload(
        filePath: String,
        fileName: String,
        imageData: ByteArray,
        cameraCapabilities: CameraCapabilities? = null,
        cameraSettings: CameraSettings? = null
    ): CapturedPhoto? {
        return withContext(ioDispatcher) {
            var tempFile: File? = null
            var processedFile: File? = null
            var colorTransferredFile: File? = null
            var filmLutFile: File? = null
            var processedPath: String? = null

            try {
                // 전송 진행 카운트(요구 E3): 후처리·저장 단계 시작. 동일 fileName 이 DOWNLOADING 이었다면 PROCESSING 으로 전이.
                // markProcessing 과 아래 finally 의 markDone 을 동일 try/finally 경계에 두어
                // 디스패치 취소 시에도 markDone 이 반드시 짝지어지게 한다(누수 방지).
                transferProgressTracker.markProcessing(fileName)

                Log.d(TAG, "📦 Native 다운로드 데이터 처리 시작: $fileName")
                // Log.d(TAG, "   데이터 크기: ${imageData.size / 1024}KB")

                // RAW 게이팅 단일 지점 방어 — 미지원 RAW 는 저장하지 않는다.
                if (!isDownloadAllowedByGating(fileName)) {
                    return@withContext null
                }

                if (validateImageFormatUseCase.isRawFile(fileName)) {
                    Log.i(TAG, "💾 RAW 수신 저장 시작: $fileName (${imageData.size / 1024}KB)")
                }

                val startTime = System.currentTimeMillis()

                val extension = fileName.substringAfterLast(".", "").lowercase()
                Log.d(TAG, "✓ Native 다운로드 데이터 확인: $fileName")
                // Log.d(TAG, "   확장자: $extension")
                // Log.d(TAG, "   원본 크기: ${imageData.size / 1024}KB")

                // 카메라 폴더 구조 추출 (예: /store_00010001/DCIM/105KAY_1/KY6_0035.JPG → 105KAY_1)
                val cameraSubFolder = extractCameraSubFolder(filePath)
                // Log.d(TAG, "   추출된 카메라 서브폴더: $cameraSubFolder")

                // 현재 구독 티어 확인
                val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()

                // 필름/색감 플래그를 티어 조회 직후로 모아 resolveActivePipeline 1회로 판정한다.
                // (비허용 티어에서 '둘 다 ON' 이면 색감을 이번 다운로드에 한해 마스킹 — 영속화 없음.
                //  신규 다운로드 경로 추가 시에도 두 플래그를 datasource 에서 직접 읽지 말고 이 경유로 처리할 것.)
                val rawColorOn = appPreferencesDataSource.isColorTransferEnabled.first()
                val rawFilmOn = appPreferencesDataSource.isFilmSimulationEnabled.first()
                val activePipeline =
                    validateFeatureAccessUseCase.resolveActivePipeline(currentTier, rawFilmOn, rawColorOn)

                // 색감 전송 적용 확인 (JPEG 파일만)
                val isColorTransferEnabled = activePipeline.colorEnabled
                val referenceImagePath =
                    appPreferencesDataSource.colorTransferReferenceImagePath.first()
                val colorTransferIntensity = appPreferencesDataSource.colorTransferIntensity.first()

                // 임시 파일 생성하여 이미지 데이터 저장
                tempFile = File(context.cacheDir, "temp_native_downloads/$fileName")
                if (!tempFile.parentFile?.exists()!!) {
                    tempFile.parentFile?.mkdirs()
                }
                tempFile.writeBytes(imageData)

                processedPath = tempFile.absolutePath

                // FREE 티어 사용자를 위한 이미지 리사이즈 처리 (JPEG 파일만)
                if (currentTier == SubscriptionTier.FREE && extension in Constants.ImageProcessing.JPEG_EXTENSIONS) {
                    Log.d(TAG, "🎯 FREE 티어 - 이미지 리사이즈 적용: $fileName")

                    val resizeSuccess = try {
                        val resizedFile =
                            File(tempFile.parent, "${tempFile.nameWithoutExtension}_resized.jpg")
                        processedFile = resizedFile
                        resizeImageForFreeTier(tempFile.absolutePath, resizedFile.absolutePath)
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "❌ FREE 티어 리사이즈 중 메모리 부족", e)
                        false
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ FREE 티어 리사이즈 처리 중 오류", e)
                        false
                    }

                    if (resizeSuccess) {
                        processedPath = (processedFile ?: tempFile).absolutePath
                        Log.d(TAG, "✅ FREE 티어 리사이즈 완료")

                        // 원본 임시 파일 삭제 (공간 절약)
                        tempFile.delete()
                        tempFile = null
                    } else {
                        // FREE 티어인데 리사이즈에 실패하면 원본(2000px 초과) 저장은 게이팅 우회이므로 저장을 중단한다.
                        Log.w(TAG, "⛔ FREE 티어 리사이즈 실패 — 원본 저장 방지, 다운로드 중단: $fileName")
                        return@withContext null
                    }
                }

                // 색감 전송 적용 (리사이즈된 이미지 또는 원본에 적용)
                if (isColorTransferEnabled &&
                    referenceImagePath != null &&
                    File(referenceImagePath).exists() &&
                    extension in Constants.ImageProcessing.JPEG_EXTENSIONS
                ) {
                    Log.d(TAG, "🎨 색감 전송 적용 시작: $fileName")
                    Log.d(TAG, "   색감 전송 강도: $colorTransferIntensity")

                    try {
                        // 메모리 상태 확인 - 색감 전송 전
                        val runtime = Runtime.getRuntime()
                        val freeMemory = runtime.freeMemory()
                        val totalMemory = runtime.totalMemory()
                        val maxMemory = runtime.maxMemory()
                        val usedMemory = totalMemory - freeMemory
                        val availableMemory = maxMemory - usedMemory

                        // 메모리 부족 시 색감 전송 스킵
                        if (availableMemory < 50 * 1024 * 1024) { // 50MB 미만
                            Log.w(TAG, "⚠️ 메모리 부족으로 색감 전송 스킵")
                        } else {
                            // 색감 전송 적용
                            val currentProcessedFile = File(processedPath)
                            // 캡처 클로저(withPermit) 안에서 non-null 스마트캐스트가 불가능한
                            // var colorTransferredFile 를 안전하게 쓰기 위해 지역 val 로 고정한다.
                            val ctOutputFile = File(
                                currentProcessedFile.parent,
                                "${currentProcessedFile.nameWithoutExtension}_color_transferred.jpg"
                            )
                            colorTransferredFile = ctOutputFile

                            val transferResult =
                                colorTransferSemaphore.withPermit {
                                    withContext(imageProcessingDispatcher) {
                                        colorTransferUseCase.applyColorTransferAndSave(
                                            currentProcessedFile.absolutePath,
                                            referenceImagePath,
                                            currentProcessedFile.absolutePath,
                                            ctOutputFile.absolutePath,
                                            colorTransferIntensity
                                        )
                                    }
                                }

                            if (transferResult != null) {
                                processedPath = ctOutputFile.absolutePath
                                Log.d(TAG, "✅ 색감 전송 적용 완료: ${ctOutputFile.name}")

                                // 이전 처리된 파일 삭제 (공간 절약)
                                if (currentProcessedFile.absolutePath != tempFile?.absolutePath) {
                                    currentProcessedFile.delete()
                                    processedFile = null
                                }
                            } else {
                                Log.w(TAG, "⚠️ 색감 전송 실패, 이전 처리된 이미지 사용")
                            }
                        }
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "❌ 메모리 부족으로 색감 전송 실패", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 색감 전송 처리 중 오류", e)
                    }
                }

                // 필름 시뮬레이션 적용 (색감 전송 결과 또는 이전 단계 산출물에 LUT 적용)
                // 티어 마스킹된 값 사용(위 resolveActivePipeline 1회 판정) — datasource 직접 read 금지.
                // 선택 LUT 이 현재 티어에서 잠겨 있으면 "" 로 마스킹 → 아래 isNotEmpty 가드가 필름 스텝을 스킵(영속화 없음).
                val isFilmSimEnabled = activePipeline.filmEnabled
                val selectedFilmLutId = validateFeatureAccessUseCase.resolveEffectiveLutId(
                    currentTier, appPreferencesDataSource.selectedFilmLutId.first()
                )
                val filmSimIntensity = appPreferencesDataSource.filmSimulationIntensity.first()
                val filmInputPath = processedPath
                if (isFilmSimEnabled &&
                    selectedFilmLutId.isNotEmpty() &&
                    filmInputPath != null &&
                    extension in Constants.ImageProcessing.JPEG_EXTENSIONS
                ) {
                    Log.d(TAG, "🎞️ 필름 시뮬레이션 적용 시작: $fileName (강도=$filmSimIntensity)")
                    try {
                        val runtime = Runtime.getRuntime()
                        val availableMemory =
                            runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
                        if (availableMemory < 50 * 1024 * 1024) { // 50MB 미만
                            Log.w(TAG, "⚠️ 메모리 부족으로 필름 시뮬레이션 스킵")
                        } else {
                            val currentProcessedFile = File(filmInputPath)
                            val filmOutputFile = File(
                                currentProcessedFile.parent,
                                "${currentProcessedFile.nameWithoutExtension}_film.jpg"
                            )
                            filmLutFile = filmOutputFile

                            val filmResult = colorTransferSemaphore.withPermit {
                                withContext(imageProcessingDispatcher) {
                                    filmLutUseCase.applyFilmLutAndSave(
                                        currentProcessedFile.absolutePath,
                                        selectedFilmLutId,
                                        currentProcessedFile.absolutePath,
                                        filmOutputFile.absolutePath,
                                        filmSimIntensity
                                    )
                                }
                            }

                            if (filmResult != null) {
                                processedPath = filmOutputFile.absolutePath
                                Log.d(TAG, "✅ 필름 시뮬레이션 적용 완료: ${filmOutputFile.name}")
                            } else {
                                Log.w(TAG, "⚠️ 필름 시뮬레이션 실패, 이전 처리된 이미지 사용")
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: OutOfMemoryError) {
                        Log.e(TAG, "❌ 메모리 부족으로 필름 시뮬레이션 실패", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 필름 시뮬레이션 처리 중 오류", e)
                    }
                }

                // SAF를 사용한 후처리 (Android 10+에서 MediaStore로 이동)
                val fileNameWithFolder = if (cameraSubFolder.isNotEmpty()) {
                    "$cameraSubFolder/$fileName"
                } else {
                    fileName
                }
                // Log.d(TAG, "📂 후처리 전 파일명 정보:")
                // Log.d(TAG, "   원본 파일명: $fileName")
                // Log.d(TAG, "   카메라 서브폴더: $cameraSubFolder")
                // Log.d(TAG, "   폴더 포함 파일명: $fileNameWithFolder")
                // Log.d(TAG, "   임시 파일 경로: $processedPath")

                val finalPath = postProcessPhoto(processedPath!!, fileNameWithFolder)
                Log.d(TAG, "✅ Native 사진 후처리 완료: ${LogMask.path(finalPath)}")

                val capturedPhoto = CapturedPhoto(
                    id = UUID.randomUUID().toString(),
                    filePath = finalPath,
                    thumbnailPath = null,
                    // 정렬 기준 안정화: 원본 바이트(재인코딩 이전)의 EXIF 촬영 시각 사용, 실패 시 현재 시각 폴백
                    captureTime = ExifCaptureTime.parseMillis(imageData) ?: System.currentTimeMillis(),
                    cameraModel = cameraCapabilities?.model ?: "알 수 없음",
                    settings = cameraSettings,
                    size = imageData.size.toLong(),
                    width = 0,
                    height = 0,
                    isDownloading = false,
                    downloadCompleteTime = System.currentTimeMillis()
                )

                // 사진 촬영 이벤트 발생
                photoCaptureEventManager.emitPhotoCaptured()

                val downloadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "✅ Native 사진 저장 완료: $fileName (${downloadTime}ms)")

                capturedPhoto
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "❌ Native 사진 저장 중 메모리 부족: $fileName", e)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ Native 사진 저장 실패: $fileName", e)
                null
            } finally {
                // 전송 진행 카운트(요구 E3): 성공·실패·OOM 무관 처리 종료 → 큐에서 제거.
                transferProgressTracker.markDone(fileName)

                // 메모리 정리 - 모든 임시 객체 해제
                try {
                    // 임시 파일들 정리
                    tempFile?.takeIf { it.exists() }?.delete()
                    processedFile?.takeIf {
                        it.exists() && processedPath?.let { path ->
                            it != File(
                                path
                            )
                        } ?: true
                    }?.delete()
                    colorTransferredFile?.takeIf {
                        it.exists() && processedPath?.let { path ->
                            it != File(
                                path
                            )
                        } ?: true
                    }?.delete()
                    filmLutFile?.takeIf {
                        it.exists() && processedPath?.let { path ->
                            it != File(
                                path
                            )
                        } ?: true
                    }?.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "파일 정리 중 오류 (무시): ${e.message}")
                }

                // GC 호출 제거 - 시스템이 자동으로 관리하도록 함
            }
        }
    }

    /**
     * JPEG 및 RAW 사진 다운로드를 비동기로 처리
     */
    suspend fun handlePhotoDownload(
        photo: CapturedPhoto,
        fullPath: String,
        fileName: String,
        cameraCapabilities: CameraCapabilities?,
        cameraSettings: CameraSettings?,
        onPhotoDownloaded: (CapturedPhoto) -> Unit,
        onDownloadFailed: (String) -> Unit
    ) {
        var processedFile: File? = null

        try {
            Log.d(TAG, "사진 다운로드 시작: $fileName")
            if (BuildConfig.DEBUG) {
                // PII: 사용자 사진 절대 경로 — DEBUG 빌드에서만 출력
                Log.d(TAG, "   전체 경로: $fullPath")
            }

            // RAW 게이팅 단일 지점 방어 — 미지원 RAW 는 저장하지 않는다.
            if (!isDownloadAllowedByGating(fileName)) {
                onDownloadFailed(fileName)
                return
            }

            val startTime = System.currentTimeMillis()

            // 카메라 내부 경로인지 확인 (/store_로 시작하거나 DCIM이 포함된 경우)
            val isCameraInternalPath = fullPath.startsWith("/store_") || fullPath.contains("/DCIM/")

            if (isCameraInternalPath) {
                Log.d(TAG, "카메라 내부 경로 감지 - 네이티브 다운로드 사용: ${LogMask.path(fullPath)}")

                // 네이티브 데이터소스를 통해 카메라에서 직접 다운로드
                val downloadResult = downloadPhotoFromCamera(
                    photoId = fullPath,
                    cameraCapabilities = cameraCapabilities,
                    cameraSettings = cameraSettings
                )

                if (downloadResult.isSuccess) {
                    val downloadedPhoto = downloadResult.getOrNull()!!
                    Log.d(TAG, "✅ 카메라에서 직접 다운로드 완료: $fileName")
                    onPhotoDownloaded(downloadedPhoto)

                    // 사진 촬영 이벤트 발생
                    photoCaptureEventManager.emitPhotoCaptured()
                } else {
                    Log.e(TAG, "❌ 카메라에서 직접 다운로드 실패: $fileName")
                    onDownloadFailed(fileName)
                }
                return
            }

            // 로컬 파일 시스템 경로인 경우 기존 로직 사용
            Log.d(TAG, "📁 로컬 파일 시스템 경로 처리: ${LogMask.path(fullPath)}")
            val file = File(fullPath)
            if (!file.exists()) {
                Log.e(TAG, "❌ 사진 파일을 찾을 수 없음: ${LogMask.path(fullPath)}")
                onDownloadFailed(fileName)
                return
            }

            val fileSize = file.length()
            if (fileSize == 0L) {
                Log.e(TAG, "❌ 사진 파일이 비어있음: ${LogMask.path(fullPath)}")
                onDownloadFailed(fileName)
                return
            }

            val extension = fileName.substringAfterLast(".", "").lowercase()
            Log.d(TAG, "✓ 사진 파일 확인 완료: $fileName")
            Log.d(TAG, "   확장자: $extension")
            Log.d(TAG, "   크기: ${fileSize / 1024}KB")

            // 현재 구독 티어 확인
            val currentTier = getSubscriptionUseCase.getSubscriptionTier().first()

            // 필름/색감 플래그를 티어 조회 직후로 모아 resolveActivePipeline 1회로 판정한다.
            // (비허용 티어에서 '둘 다 ON' 이면 색감을 이번 다운로드에 한해 마스킹 — 영속화 없음.
            //  신규 다운로드 경로 추가 시에도 두 플래그를 datasource 에서 직접 읽지 말고 이 경유로 처리할 것.)
            val rawColorOn = appPreferencesDataSource.isColorTransferEnabled.first()
            val rawFilmOn = appPreferencesDataSource.isFilmSimulationEnabled.first()
            val activePipeline =
                validateFeatureAccessUseCase.resolveActivePipeline(currentTier, rawFilmOn, rawColorOn)

            // 색감 전송 적용 확인 (JPEG 파일만)
            val isColorTransferEnabled = activePipeline.colorEnabled
            val referenceImagePath =
                appPreferencesDataSource.colorTransferReferenceImagePath.first()
            val colorTransferIntensity = appPreferencesDataSource.colorTransferIntensity.first()

            var processedPath = fullPath

            // FREE 티어 사용자를 위한 이미지 리사이즈 처리 (JPEG 파일만)
            if (currentTier == SubscriptionTier.FREE && extension in Constants.ImageProcessing.JPEG_EXTENSIONS) {
                Log.d(TAG, "🎯 FREE 티어 - 이미지 리사이즈 적용: $fileName")

                val resizeSuccess = try {
                    val resizedFile = File(file.parent, "${file.nameWithoutExtension}_resized.jpg")
                    processedFile = resizedFile
                    resizeImageForFreeTier(file.absolutePath, resizedFile.absolutePath)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "❌ FREE 티어 리사이즈 중 메모리 부족", e)
                    false
                } catch (e: Exception) {
                    Log.e(TAG, "❌ FREE 티어 리사이즈 처리 중 오류", e)
                    false
                }

                if (resizeSuccess) {
                    processedPath = (processedFile ?: file).absolutePath
                    Log.d(TAG, "✅ FREE 티어 리사이즈 완료")

                    // 원본 파일 삭제 (공간 절약)
                    file.delete()
                } else {
                    // FREE 티어인데 리사이즈에 실패하면 원본(2000px 초과) 저장은 게이팅 우회이므로 저장을 중단한다.
                    Log.w(TAG, "⛔ FREE 티어 리사이즈 실패 — 원본 저장 방지, 다운로드 중단: $fileName")
                    onDownloadFailed(fileName)
                    return
                }
            }

            // 색감 전송 적용 (리사이즈된 이미지 또는 원본에 적용)
            if (isColorTransferEnabled &&
                referenceImagePath != null &&
                File(referenceImagePath).exists() &&
                extension in Constants.ImageProcessing.JPEG_EXTENSIONS
            ) {
                Log.d(TAG, "🎨 색감 전송 적용 시작: $fileName")
                Log.d(TAG, "   색감 전송 강도: $colorTransferIntensity")

                try {
                    // 색감 전송 적용
                    val processedFile = File(processedPath)
                    val colorTransferredFile = File(
                        processedFile.parent,
                        "${processedFile.nameWithoutExtension}_color_transferred.jpg"
                    )

                    val transferResult = colorTransferSemaphore.withPermit {
                        withContext(imageProcessingDispatcher) {
                            colorTransferUseCase.applyColorTransferAndSave(
                                processedFile.absolutePath, // 입력 파일 경로 (리사이즈된 이미지 또는 원본)
                                referenceImagePath, // 참조 이미지 경로
                                processedFile.absolutePath, // 원본 이미지 경로 (EXIF 메타데이터 복사용)
                                colorTransferredFile.absolutePath, // 출력 파일 경로
                                colorTransferIntensity // 사용자 설정 강도
                            )
                        }
                    }

                    if (transferResult != null) {
                        processedPath = colorTransferredFile.absolutePath
                        Log.d(TAG, "✅ 색감 전송 적용 완료: ${colorTransferredFile.name}")

                        // 이전 처리된 파일 삭제 (공간 절약)
                        if (processedFile.absolutePath != fullPath) {
                            processedFile.delete()
                        }
                    } else {
                        Log.w(TAG, "⚠️ 색감 전송 실패, 이전 처리된 이미지 사용")
                    }
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "❌ 메모리 부족으로 색감 전송 실패", e)
                    // 메모리 부족 시 강제 GC 실행 및 메모리 정리
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 색감 전송 처리 중 오류", e)
                    // 오류 발생 시 이전 처리된 이미지 사용
                }
            } else {
                if (isColorTransferEnabled) {
                    Log.d(TAG, "⚠️ 색감 전송 활성화되어 있지만 참조 이미지가 없음")
                }
            }

            // 필름 시뮬레이션 적용 (색감 전송 결과 또는 이전 단계 산출물에 LUT 적용)
            // 티어 마스킹된 값 사용(위 resolveActivePipeline 1회 판정) — datasource 직접 read 금지.
            // 선택 LUT 이 현재 티어에서 잠겨 있으면 "" 로 마스킹 → 아래 isNotEmpty 가드가 필름 스텝을 스킵(영속화 없음).
            val isFilmSimEnabled = activePipeline.filmEnabled
            val selectedFilmLutId = validateFeatureAccessUseCase.resolveEffectiveLutId(
                currentTier, appPreferencesDataSource.selectedFilmLutId.first()
            )
            val filmSimIntensity = appPreferencesDataSource.filmSimulationIntensity.first()
            if (isFilmSimEnabled &&
                selectedFilmLutId.isNotEmpty() &&
                extension in Constants.ImageProcessing.JPEG_EXTENSIONS
            ) {
                Log.d(TAG, "🎞️ 필름 시뮬레이션 적용 시작: $fileName (강도=$filmSimIntensity)")
                try {
                    val currentFilmFile = File(processedPath)
                    val filmOutputFile = File(
                        currentFilmFile.parent,
                        "${currentFilmFile.nameWithoutExtension}_film.jpg"
                    )

                    val filmResult = colorTransferSemaphore.withPermit {
                        withContext(imageProcessingDispatcher) {
                            filmLutUseCase.applyFilmLutAndSave(
                                currentFilmFile.absolutePath,
                                selectedFilmLutId,
                                currentFilmFile.absolutePath,
                                filmOutputFile.absolutePath,
                                filmSimIntensity
                            )
                        }
                    }

                    if (filmResult != null) {
                        processedPath = filmOutputFile.absolutePath
                        Log.d(TAG, "✅ 필름 시뮬레이션 적용 완료: ${filmOutputFile.name}")

                        // 이전 처리된 파일 삭제 (원본은 보존)
                        if (currentFilmFile.absolutePath != fullPath) {
                            currentFilmFile.delete()
                        }
                    } else {
                        Log.w(TAG, "⚠️ 필름 시뮬레이션 실패, 이전 처리된 이미지 사용")
                        // 실패(예: 기록 중 I/O 오류)로 부분 생성된 출력 파일을 정리.
                        // 이 오버로드는 finally 가 없어 명시적으로 삭제한다.
                        if (filmOutputFile.exists()) {
                            filmOutputFile.delete()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "❌ 메모리 부족으로 필름 시뮬레이션 실패", e)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 필름 시뮬레이션 처리 중 오류", e)
                }
            }

            // SAF를 사용한 후처리 (Android 10+에서 MediaStore로 이동)
            val finalPath = postProcessPhoto(processedPath, fileName)
            Log.d(TAG, "✅ 사진 후처리 완료: ${LogMask.path(finalPath)}")

            // 즉시 UI에 임시 사진 정보 추가 (썸네일 없이)
            val tempPhoto = photo.copy(
                filePath = finalPath,
                isDownloading = false
            )

            // UI 업데이트
            withContext(Dispatchers.Main) {
                onPhotoDownloaded(tempPhoto)
            }

            val downloadTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ 사진 다운로드 완료: $fileName (${downloadTime}ms)")

            // 사진 촬영 이벤트 발생
            photoCaptureEventManager.emitPhotoCaptured()

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ 사진 다운로드 실패: $fileName", e)
            onDownloadFailed(fileName)
        }
    }

    /**
     * 사진 다운로드 처리 - Native 바이트 배열 지원 버전
     */
    suspend fun handlePhotoDownload(
        photo: CapturedPhoto,
        fullPath: String,
        fileName: String,
        cameraCapabilities: CameraCapabilities?,
        cameraSettings: CameraSettings?,
        imageData: ByteArray,
        onPhotoDownloaded: (CapturedPhoto) -> Unit,
        onDownloadFailed: (String) -> Unit
    ) {
        // Native에서 받은 바이트 배열을 사용하여 처리
        val result = handleNativePhotoDownload(
            filePath = fullPath,
            fileName = fileName,
            imageData = imageData,
            cameraCapabilities = cameraCapabilities,
            cameraSettings = cameraSettings
        )

        if (result != null) {
            onPhotoDownloaded(result)
        } else {
            onDownloadFailed(fileName)
        }
    }

    /**
     * FREE 티어 사용자를 위한 이미지 리사이즈 처리
     * 장축 기준 2000픽셀로 리사이즈하고 모든 EXIF 정보 보존
     */
    private suspend fun resizeImageForFreeTier(inputPath: String, outputPath: String): Boolean {
        return withContext(ioDispatcher) {
            var originalBitmap: Bitmap? = null
            var resizedBitmap: Bitmap? = null
            var rotatedBitmap: Bitmap? = null

            try {
                Log.d(TAG, "🔧 FREE 티어 이미지 리사이즈 시작: ${LogMask.path(inputPath)}")

                // 메모리 상태 확인 (간소화)
                val runtime = Runtime.getRuntime()
                val availableMemory =
                    runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())

                // 메모리 부족 시: 원본을 그대로 복사하면 FREE 2000px 제한이 우회되므로 실패 처리한다.
                // (호출부는 false 를 받으면 원본 대신 다운로드 자체를 실패로 간주해야 함)
                if (availableMemory < 30 * 1024 * 1024) { // 30MB 미만
                    Log.w(TAG, "메모리 부족으로 리사이즈 불가 — 원본 저장 방지: 사용가능 ${availableMemory / 1024 / 1024}MB")
                    return@withContext false
                }

                // 원본 이미지 크기 확인
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(inputPath, options)

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight
                val maxDimension = max(originalWidth, originalHeight)

                Log.d(TAG, "원본 이미지 크기: ${originalWidth}x${originalHeight}")

                // 이미 작은 이미지인 경우 리사이즈하지 않음
                if (maxDimension <= FREE_TIER_MAX_DIMENSION) {
                    Log.d(TAG, "이미 작은 이미지 - 리사이즈 불필요")
                    return@withContext File(inputPath).copyTo(File(outputPath), overwrite = true)
                        .exists()
                }

                // 리사이즈 비율 계산
                val scale = FREE_TIER_MAX_DIMENSION.toFloat() / maxDimension.toFloat()
                val newWidth = (originalWidth * scale).toInt()
                val newHeight = (originalHeight * scale).toInt()

                Log.d(TAG, "리사이즈 목표 크기: ${newWidth}x${newHeight} (비율: $scale)")

                // 메모리 효율적인 리사이즈를 위한 샘플링
                val sampleSize =
                    calculateInSampleSize(originalWidth, originalHeight, newWidth, newHeight)

                options.apply {
                    inJustDecodeBounds = false
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565 // 메모리 절약 - ARGB_8888에서 RGB_565로 변경
                }

                originalBitmap = BitmapFactory.decodeFile(inputPath, options)
                if (originalBitmap == null) {
                    Log.e(TAG, "이미지 디코딩 실패: ${LogMask.path(inputPath)}")
                    return@withContext false
                }

                try {
                    // 정확한 크기로 최종 리사이즈
                    resizedBitmap =
                        Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)

                    // 원본 비트맵 즉시 해제 (메모리 절약)
                    if (resizedBitmap != originalBitmap) {
                        originalBitmap.recycle()
                        originalBitmap = null
                    }

                    // EXIF 정보 읽기 (회전 정보)
                    val originalExif = ExifInterface(inputPath)
                    val orientation = originalExif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )

                    // 회전 적용
                    rotatedBitmap = rotateImageIfRequired(resizedBitmap, orientation)

                    // 리사이즈된 비트맵 즉시 해제 (회전된 것과 다른 경우)
                    if (rotatedBitmap != resizedBitmap) {
                        resizedBitmap.recycle()
                        resizedBitmap = null
                    }

                    // 파일로 저장
                    FileOutputStream(outputPath).use { out ->
                        val compressFormat = Bitmap.CompressFormat.JPEG
                        val compressQuality = 92 // 품질을 약간 낮춰서 메모리 절약
                        rotatedBitmap.compress(compressFormat, compressQuality, out)
                    }

                    // 모든 EXIF 정보를 새 파일에 복사
                    copyAllExifData(inputPath, outputPath, newWidth, newHeight)

                    val outputFile = File(outputPath)
                    val finalSize = outputFile.length()
                    Log.d(TAG, "✅ FREE 티어 리사이즈 완료 (EXIF 보존) - 최종 크기: ${finalSize / 1024}KB")

                    true

                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "❌ 리사이즈 중 메모리 부족", e)
                    // 메모리 정리 
                    resizedBitmap?.recycle()
                    rotatedBitmap?.recycle()
                    false
                } finally {
                    // 메모리 정리
                    rotatedBitmap?.recycle()
                    rotatedBitmap = null
                }

            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "❌ 메모리 부족으로 리사이즈 실패", e)
                return@withContext false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "❌ 이미지 리사이즈 실패", e)
                return@withContext false
            } finally {
                // 모든 비트맵 객체 해제
                try {
                    originalBitmap?.recycle()
                    resizedBitmap?.recycle()
                    rotatedBitmap?.recycle()
                } catch (e: Exception) {
                    Log.w(TAG, "비트맵 해제 중 오류 (무시): ${e.message}")
                }

                // GC 호출 제거 - 시스템이 자동으로 관리
            }
        }
    }

    /**
     * 원본 이미지의 모든 EXIF 정보를 리사이즈된 이미지에 복사
     * 이미지 크기 정보는 새로운 값으로 업데이트
     */
    private fun copyAllExifData(
        originalPath: String,
        newPath: String,
        newWidth: Int,
        newHeight: Int
    ) {
        try {
            Log.d(TAG, "EXIF 정보 복사 시작: ${LogMask.path(originalPath)} -> ${LogMask.path(newPath)}")

            val originalExif = ExifInterface(originalPath)
            val newExif = ExifInterface(newPath)

            // 복사할 EXIF 태그들 - Android ExifInterface에서 지원하는 태그들만
            val tagsToPreserve = arrayOf(
                // 카메라 정보
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,

                // 촬영 설정
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                ExifInterface.TAG_APERTURE_VALUE,
                ExifInterface.TAG_SHUTTER_SPEED_VALUE,
                ExifInterface.TAG_BRIGHTNESS_VALUE,
                ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
                ExifInterface.TAG_MAX_APERTURE_VALUE,
                ExifInterface.TAG_METERING_MODE,
                ExifInterface.TAG_LIGHT_SOURCE,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_SCENE_CAPTURE_TYPE,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
                ExifInterface.TAG_EXPOSURE_MODE,
                ExifInterface.TAG_GAIN_CONTROL,
                ExifInterface.TAG_CONTRAST,
                ExifInterface.TAG_SATURATION,
                ExifInterface.TAG_SHARPNESS,

                // 날짜/시간 정보
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_SUBSEC_TIME,
                ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,

                // GPS 정보
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_GPS_PROCESSING_METHOD,
                ExifInterface.TAG_GPS_SPEED,
                ExifInterface.TAG_GPS_SPEED_REF,
                ExifInterface.TAG_GPS_TRACK,
                ExifInterface.TAG_GPS_TRACK_REF,
                ExifInterface.TAG_GPS_IMG_DIRECTION,
                ExifInterface.TAG_GPS_IMG_DIRECTION_REF,

                // 기타 메타데이터
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                ExifInterface.TAG_USER_COMMENT,

                // 색상 공간 및 렌더링
                ExifInterface.TAG_COLOR_SPACE,
                ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION,
                ExifInterface.TAG_REFERENCE_BLACK_WHITE,
                ExifInterface.TAG_WHITE_POINT,
                ExifInterface.TAG_PRIMARY_CHROMATICITIES,
                ExifInterface.TAG_Y_CB_CR_COEFFICIENTS,
                ExifInterface.TAG_Y_CB_CR_POSITIONING,
                ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING,

                // 방향 정보 (변경되지 않음 - 회전은 이미 적용됨)
                ExifInterface.TAG_ORIENTATION
            )

            var copiedCount = 0
            // 모든 태그 복사
            for (tag in tagsToPreserve) {
                val value = originalExif.getAttribute(tag)
                if (value != null) {
                    newExif.setAttribute(tag, value)
                    copiedCount++
                }
            }


            // 새로운 이미지 크기 정보 설정 (필수)
            newExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, newWidth.toString())
            newExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, newHeight.toString())
            newExif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, newWidth.toString())
            newExif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, newHeight.toString())

            // 처리 소프트웨어 정보 추가
            newExif.setAttribute(ExifInterface.TAG_SOFTWARE, "CamCon (Free Tier Resize)")

            // EXIF 정보 저장
            newExif.saveAttributes()

            Log.d(TAG, "✅ EXIF 정보 복사 완료: ${copiedCount}개 태그 복사됨")
            Log.d(TAG, "   새 이미지 크기 정보: ${newWidth}x${newHeight}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ EXIF 정보 복사 실패", e)
            // EXIF 복사 실패해도 이미지 리사이즈는 성공으로 처리
        }
    }

    /**
     * 메모리 효율적인 샘플링 크기 계산
     */
    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Issue 1: 파일 경로에서 EXIF 정보를 읽어 이미지 회전 처리
     * 파일 경로 기반 오버로드 — 디코딩 후 회전 적용
     */
    private fun rotateImageIfRequired(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "EXIF 회전: 파일 미존재 - ${LogMask.path(filePath)}")
                return
            }

            // EXIF 정보 읽기
            val exifInterface = ExifInterface(filePath)
            val orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            // 회전 필요 없으면 조기 반환
            if (orientation == ExifInterface.ORIENTATION_NORMAL) {
                Log.d(TAG, "EXIF 회전: 정상 방향 (회전 불필요)")
                return
            }

            Log.d(TAG, "EXIF 회전: orientation=$orientation")

            // 이미지 디코딩 (원본 경로이므로 화질 보존을 위해 풀해상도 유지.
            // 픽셀 회전은 원본+회전본 2× 피크가 불가피하므로 OOM 시 회전을 생략한다 —
            // EXIF orientation 태그는 남아 있어 뷰어가 자동 회전으로 올바르게 표시한다.)
            val bitmap = BitmapFactory.decodeFile(filePath)
            if (bitmap == null) {
                Log.w(TAG, "EXIF 회전: 비트맵 디코딩 실패")
                return
            }

            // 회전된 비트맵 생성
            val rotatedBitmap = rotateImageIfRequired(bitmap, orientation)

            // 회전된 이미지를 파일에 저장
            if (rotatedBitmap != bitmap) {
                saveRotatedImage(filePath, rotatedBitmap)
                // 원본 비트맵은 rotateImageIfRequired 내에서 이미 recycle됨
            } else {
                bitmap.recycle()
            }
        } catch (e: OutOfMemoryError) {
            // 고화소(45MP=183MB×2) 회전 중 OOM — 크래시 대신 회전 생략 (EXIF로 표시 보정됨)
            Log.e(TAG, "EXIF 회전 중 메모리 부족 - 픽셀 회전 생략", e)
        } catch (e: Exception) {
            Log.e(TAG, "EXIF 회전 처리 중 오류", e)
        }
    }

    /**
     * 회전된 비트맵을 파일에 저장
     */
    private fun saveRotatedImage(filePath: String, bitmap: Bitmap) {
        try {
            // 1. 원본 EXIF 메타데이터 읽기 (compress 전에!)
            val originalExif = ExifInterface(filePath)
            val tagsToPreserve = arrayOf(
                // 카메라 정보
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
                // 촬영 설정
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                ExifInterface.TAG_APERTURE_VALUE,
                ExifInterface.TAG_SHUTTER_SPEED_VALUE,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_EXPOSURE_MODE,
                ExifInterface.TAG_METERING_MODE,
                // 날짜/시간 정보
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_SUBSEC_TIME,
                ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
                // GPS 정보
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP
            )
            val savedTags = tagsToPreserve.associateWith { originalExif.getAttribute(it) }
                .filterValues { it != null }

            // 2. 회전된 비트맵 저장 (EXIF 새로 생성됨)
            FileOutputStream(filePath).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                fos.flush()
            }

            // 3. 보존된 EXIF 태그 복사 및 orientation=NORMAL 설정
            val newExif = ExifInterface(filePath)
            savedTags.forEach { (tag, value) ->
                if (value != null) newExif.setAttribute(tag, value)
            }
            newExif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            newExif.saveAttributes()

            Log.d(TAG, "회전된 이미지 저장 완료 (EXIF 보존): ${LogMask.path(filePath)}")
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "회전된 이미지 저장 중 오류", e)
            bitmap.recycle()
        }
    }

    /**
     * EXIF 정보에 따른 이미지 회전 처리 (비트맵 기반 오버로드)
     */
    private fun rotateImageIfRequired(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        var rotationDegrees = 0f
        var swapDimensions = false

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                Log.d(TAG, "EXIF 90도 회전 적용")
                rotationDegrees = 90f
                swapDimensions = true
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                Log.d(TAG, "EXIF 180도 회전 적용")
                rotationDegrees = 180f
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                Log.d(TAG, "EXIF 270도 회전 적용")
                rotationDegrees = 270f
                swapDimensions = true
            }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                Log.d(TAG, "EXIF 수평 반전 적용")
                matrix.preScale(-1f, 1f)
                return processTransformedBitmap(bitmap, matrix, swapDimensions = false)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                Log.d(TAG, "EXIF 수직 반전 적용")
                matrix.preScale(1f, -1f)
                return processTransformedBitmap(bitmap, matrix, swapDimensions = false)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                Log.d(TAG, "EXIF 전치 적용 (90도 회전 + 수평 반전)")
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
                return processTransformedBitmap(bitmap, matrix, swapDimensions = true)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                Log.d(TAG, "EXIF 역전치 적용 (270도 회전 + 수평 반전)")
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
                return processTransformedBitmap(bitmap, matrix, swapDimensions = true)
            }
            else -> return bitmap // 회전 불필요
        }

        return try {
            // 메모리 상태 확인
            val runtime = Runtime.getRuntime()
            val availableMemory =
                runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())

            // 메모리 부족 시 회전 생략
            if (availableMemory < 20 * 1024 * 1024) { // 20MB 미만
                Log.w(TAG, "메모리 부족으로 이미지 회전 생략: 사용가능 ${availableMemory / 1024 / 1024}MB")
                return bitmap
            }

            // 회전 후 비트맵 크기 계산
            val newWidth = if (swapDimensions) bitmap.height else bitmap.width
            val newHeight = if (swapDimensions) bitmap.width else bitmap.height

            // 회전 중심을 비트맵 중앙으로 설정
            matrix.postRotate(rotationDegrees, bitmap.width / 2f, bitmap.height / 2f)

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )

            // 원본 비트맵과 다른 경우에만 원본 해제
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }

            rotatedBitmap
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "이미지 회전 중 메모리 부족", e)
            // 원본 반환 (GC 호출 제거)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "이미지 회전 중 오류", e)
            bitmap // 회전 실패 시 원본 반환
        }
    }

    /**
     * EXIF 변환(반전, 전치) 적용 헬퍼 메서드
     */
    private fun processTransformedBitmap(
        bitmap: Bitmap,
        matrix: Matrix,
        swapDimensions: Boolean
    ): Bitmap {
        return try {
            // 메모리 상태 확인
            val runtime = Runtime.getRuntime()
            val availableMemory =
                runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())

            if (availableMemory < 20 * 1024 * 1024) { // 20MB 미만
                Log.w(TAG, "메모리 부족으로 이미지 변환 생략: 사용가능 ${availableMemory / 1024 / 1024}MB")
                return bitmap
            }

            // 변환 후 비트맵 크기 계산
            val newWidth = if (swapDimensions) bitmap.height else bitmap.width
            val newHeight = if (swapDimensions) bitmap.width else bitmap.height

            val transformedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )

            // 원본 비트맵과 다른 경우에만 원본 해제
            if (transformedBitmap != bitmap) {
                bitmap.recycle()
            }

            transformedBitmap
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "이미지 변환 중 메모리 부족", e)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "이미지 변환 중 오류", e)
            bitmap
        }
    }

    /**
     * 저장소 권한 확인
     */
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: 세분화된 미디어 권한
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-12: 기존 저장소 권한
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            // Android 5 이하: 권한 확인 불필요
            true
        }
    }

    /**
     * SAF를 고려한 저장 디렉토리 결정
     */
    fun getSaveDirectory(): String {
        return try {
            // 권한 확인
            if (!hasStoragePermission()) {
                Log.w("사진다운로드매니저", "저장소 권한 없음, 내부 저장소 사용")
                return File(
                    context.cacheDir,
                    Constants.FilePaths.TEMP_CACHE_DIR
                ).apply { mkdirs() }.absolutePath
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: SAF 사용하므로 임시 디렉토리 반환 (후처리에서 MediaStore 사용)
                val tempDir = File(context.cacheDir, Constants.FilePaths.TEMP_CACHE_DIR)
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                Log.d("사진다운로드매니저", "✅ SAF 사용 - 임시 디렉토리: ${LogMask.path(tempDir.absolutePath)}")
                tempDir.absolutePath
            } else {
                // Android 9 이하: 직접 외부 저장소 접근 - 우선순위 시스템 사용
                val externalPath = Constants.FilePaths.findAvailableExternalStoragePath()
                val externalDir = File(externalPath)

                if (!externalDir.exists()) {
                    externalDir.mkdirs()
                }

                if (externalDir.exists() && externalDir.canWrite()) {
                    val storageType = Constants.FilePaths.getStorageType(externalPath)
                    Log.d("사진다운로드매니저", "✅ 외부 저장소 사용: ${LogMask.path(externalPath)} (타입: $storageType)")
                    externalPath
                } else {
                    // 외부 저장소를 사용할 수 없으면 내부 저장소
                    val internalDir = File(context.filesDir, "photos")
                    if (!internalDir.exists()) {
                        internalDir.mkdirs()
                    }
                    Log.w("사진다운로드매니저", "⚠️ 내부 저장소 사용: ${LogMask.path(internalDir.absolutePath)}")
                    internalDir.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e("사진다운로드매니저", "저장 디렉토리 결정 실패, 기본값 사용", e)
            context.filesDir.absolutePath
        }
    }

    /**
     * 사진 후처리 - SAF를 사용하여 최종 저장소에 저장
     */
    private suspend fun postProcessPhoto(tempFilePath: String, fileName: String): String {
        return withContext(ioDispatcher) {
            try {
                Log.d(TAG, "📝 postProcessPhoto 시작: 임시=${LogMask.path(tempFilePath)}, 파일명=$fileName, SDK=${Build.VERSION.SDK_INT}")

                // Issue 1: EXIF 회전 역방향 — non-resize 경로에서 회전 적용
                rotateImageIfRequired(tempFilePath)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+: MediaStore API 사용
                    val result = saveToMediaStore(tempFilePath, fileName)
                    Log.d(TAG, "   ✅ MediaStore 저장 결과: ${LogMask.path(result)}")
                    result
                } else {
                    // Android 9 이하: 이미 올바른 위치에 저장되어 있음
                    Log.d(TAG, "   → Android 9 이하 - 기존 경로 사용")
                    tempFilePath
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "사진 후처리 실패", e)
                tempFilePath // 실패 시 원본 경로 반환
            }
        }
    }

    /**
     * MediaStore를 사용하여 사진을 외부 저장소에 저장 (카메라 폴더 구조 유지)
     */
    private fun saveToMediaStore(tempFilePath: String, fileName: String): String {
        return try {
            Log.d(TAG, "💾 saveToMediaStore 시작: 임시=${LogMask.path(tempFilePath)}, 파일명=$fileName")

            val tempFile = File(tempFilePath)
            if (!tempFile.exists()) {
                Log.e(TAG, "❌ 임시 파일이 존재하지 않음: ${LogMask.path(tempFilePath)}")
                return tempFilePath
            }

            // 카메라 폴더 구조 분석 (예: 105KAY_1/KY6_0035.JPG)
            val file = File(fileName)
            val subFolderPath = file.parent ?: ""
            val baseFileName = file.name

            // 파일 확장자에 따른 MIME 타입 결정
            val extension = baseFileName.substringAfterLast(".", "").lowercase()
            val mimeType = when (extension) {
                in Constants.ImageProcessing.JPEG_EXTENSIONS -> Constants.MimeTypes.IMAGE_JPEG
                "nef" -> Constants.MimeTypes.IMAGE_NEF
                "cr2" -> Constants.MimeTypes.IMAGE_CR2
                "arw" -> Constants.MimeTypes.IMAGE_ARW
                "dng" -> Constants.MimeTypes.IMAGE_DNG
                "orf" -> Constants.MimeTypes.IMAGE_ORF
                "rw2" -> Constants.MimeTypes.IMAGE_RW2
                "raf" -> Constants.MimeTypes.IMAGE_RAF
                else -> Constants.MimeTypes.IMAGE_JPEG // 기본값
            }

            // MediaStore 저장 폴더: DCIM/CamCon/(카메라서브폴더)
            val camconRelativeBase = Constants.FilePaths.getMediaStoreRelativePath()
            val relativePath = if (subFolderPath.isNotEmpty()) {
                "$camconRelativeBase/$subFolderPath"
            } else {
                camconRelativeBase
            }

            Log.d(TAG, "   MediaStore 경로: $relativePath")

            // 기존 파일이 존재하는지 확인하고 삭제 (IS_PENDING row·소유권 밖 row 포함)
            deleteExistingFileInMediaStore(baseFileName, relativePath)

            // 1차 저장 시도. MediaStore 가 이름 충돌 시 조용히 "name (1).JPG" 로 리네임하면
            // 요청명과 실제 DISPLAY_NAME 이 어긋난다(사진 내용/파일명 불일치 버그).
            // allowMismatch=false → 리네임 시 row 롤백 후 null 반환(정리 후 재시도).
            var savedPath =
                insertAndVerify(tempFile, baseFileName, mimeType, relativePath, fileName, allowMismatch = false)

            if (savedPath == null) {
                // 리네임 감지됨 → 물리 고아 파일까지 정리 후 1회 재시도.
                Log.w(TAG, "⚠️ MediaStore 리네임 감지 — 충돌 파일 정리 후 재시도: $baseFileName")
                deleteExistingFileInMediaStore(baseFileName, relativePath)
                deleteOrphanPhysicalFile(relativePath, baseFileName)
                // 마지막 시도: 여기서도 리네임되면 사진 유실 대신 실제 저장명을 그대로 채택하고 에러 로그.
                savedPath =
                    insertAndVerify(tempFile, baseFileName, mimeType, relativePath, fileName, allowMismatch = true)
            }

            if (savedPath != null) {
                tempFile.delete()
                Log.d(TAG, "✅ MediaStore 저장 성공: ${LogMask.path(savedPath)}")
                savedPath
            } else {
                Log.e(TAG, "MediaStore 저장 실패(URI 생성 실패)")
                tempFilePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore 저장 실패", e)
            tempFilePath
        }
    }

    /**
     * baseFileName 으로 MediaStore insert 후, 실제 저장된 DISPLAY_NAME 이 요청명과 일치하는지 검증한다.
     *
     * MediaStore 는 같은 relativePath 에 물리 파일이 이미 존재하면 DISPLAY_NAME 충돌을 조용히
     * "name (1).JPG" 로 리네임할 수 있다. 이 경우 사진 내용은 요청 컷인데 파일명이 다음 번호로
     * 밀려 저장/표시되는 불일치 버그가 생긴다. 여기서 리네임을 감지하면:
     * - allowMismatch=false → 삽입한 row 를 되돌리고 null 반환(호출부가 충돌 정리 후 재시도).
     * - allowMismatch=true  → 사진 유실 방지를 위해 리네임된 파일을 그대로 채택하고 에러 로그 후
     *   실제 저장 경로를 반환한다(마지막 시도).
     *
     * @return 저장 경로(DATA). URI 생성 실패, 또는 allowMismatch=false 이고 리네임됐으면 null.
     */
    private fun insertAndVerify(
        tempFile: File,
        baseFileName: String,
        mimeType: String,
        relativePath: String,
        fileNameWithFolder: String,
        allowMismatch: Boolean
    ): String? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, baseFileName) // 실제 파일명만
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath) // 폴더 구조 포함
            put(MediaStore.Images.Media.IS_PENDING, 1) // 저장 중 상태로 설정
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: run {
            Log.e(TAG, "MediaStore URI 생성 실패")
            return null
        }

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            FileInputStream(tempFile).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        // 저장 완료 후 IS_PENDING 플래그 제거
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null
        )

        // insert 후 실제 DISPLAY_NAME 재조회 → 요청명과 비교(리네임 감지)
        val actualDisplayName = queryDisplayName(uri)
        if (isDisplayNameMismatch(baseFileName, actualDisplayName)) {
            if (!allowMismatch) {
                Log.w(
                    TAG,
                    "⚠️ MediaStore DISPLAY_NAME 불일치: 요청=$baseFileName, 실제=$actualDisplayName — row 롤백 후 재시도"
                )
                runCatching { context.contentResolver.delete(uri, null, null) }
                return null
            }
            // 마지막 시도에서도 리네임됨 → 유실 방지 위해 실제 저장명 채택(내용/파일명 어긋남을 명확히 경고).
            Log.e(
                TAG,
                "❌ MediaStore DISPLAY_NAME 불일치 미해소 — 실제 저장명 채택: 요청=$baseFileName, 실제=$actualDisplayName"
            )
        }

        // 실제 저장된 파일 경로: 방금 insert 한 URI 의 실경로(DATA)를 우선 사용한다.
        // buildActualSavedPath(파일명 조립 합성경로)는, 같은 이름의 물리파일이 남아 있어
        // MediaStore 가 "KAY_0011 (1).JPG" 로 리네임해도 옛 "KAY_0011.JPG"(직전 컷 stale)를
        // 가리켜, 수신사진 목록/미리보기에 이전 촬영 이미지가 표시되는 버그를 유발하므로 DATA 를 우선한다.
        return getPathFromUri(uri) ?: buildActualSavedPath(fileNameWithFolder)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "DISPLAY_NAME 조회 실패", e)
            null
        }
    }

    /**
     * MediaStore 에서 동일 파일명·경로의 기존 row 를 모두 삭제한다.
     *
     * 기본 EXTERNAL_CONTENT_URI 쿼리는 IS_PENDING=0 row 만 노출하므로, 이전 세션에서 남은
     * pending row 나 소유권 밖 row 가 있으면 "기존 파일 없음" 으로 오판해 insert 가 리네임된다.
     * [MediaStore.QUERY_ARG_MATCH_PENDING] = MATCH_INCLUDE 로 pending row 까지 포함해 조회·삭제한다.
     */
    private fun deleteExistingFileInMediaStore(fileName: String, relativePath: String) {
        try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection =
                "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf(fileName, relativePath)

            // QUERY_ARG_MATCH_PENDING / MATCH_INCLUDE 는 API 30(R)부터. API 29 는 pending row 조회가
            // 불가능하므로 기본 selection 으로만 조회(리네임 감지+재시도 안전망이 잔여 케이스를 커버).
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = android.os.Bundle().apply {
                    putString(
                        android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                        selection
                    )
                    putStringArray(
                        android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                        selectionArgs
                    )
                    putInt(
                        MediaStore.QUERY_ARG_MATCH_PENDING,
                        MediaStore.MATCH_INCLUDE
                    )
                }
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    queryArgs,
                    null
                )
            } else {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
            }

            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val existingUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    val deleted = runCatching {
                        context.contentResolver.delete(existingUri, null, null)
                    }.getOrDefault(0)
                    if (deleted > 0) {
                        Log.d(TAG, "✅ 기존 파일 row 삭제: $relativePath/$fileName (ID: $id)")
                    } else {
                        Log.w(TAG, "⚠️ 기존 파일 row 삭제 실패: $relativePath/$fileName (ID: $id)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "기존 파일 검색/삭제 실패: $fileName", e)
        }
    }

    /**
     * MediaStore row 는 없지만 디스크에 물리적으로 남아 있는 고아 파일을 삭제한다.
     * (row 삭제로도 리네임이 안 풀리는, 물리 파일만 존재하는 케이스 대비 최후 정리)
     */
    private fun deleteOrphanPhysicalFile(relativePath: String, baseFileName: String) {
        try {
            val normalized = relativePath.trimEnd('/')
            val orphan = File("/storage/emulated/0/$normalized/$baseFileName")
            if (orphan.exists()) {
                val deleted = orphan.delete()
                Log.w(
                    TAG,
                    "고아 물리 파일 정리: ${LogMask.path(orphan.absolutePath)} → ${if (deleted) "삭제됨" else "삭제 실패"}"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "고아 물리 파일 정리 중 오류 (무시): ${e.message}")
        }
    }

    /**
     * URI를 실제 파일 경로로 변환
     */
    private fun getPathFromUri(uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DATA),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    it.getString(columnIndex)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "URI 경로 변환 실패", e)
            null
        }
    }

    /**
     * 카메라 폴더 구조를 반영한 실제 저장 경로 생성
     * 예: 105KAY_1/KY6_0035.JPG → /storage/emulated/0/DCIM/CamCon/105KAY_1/KY6_0035.JPG
     */
    private fun buildActualSavedPath(fileName: String): String {
        return try {
            val camconBase = "/storage/emulated/0/DCIM/CamCon"
            val file = File(fileName)
            val subFolderPath = file.parent ?: ""
            val baseFileName = file.name

            if (subFolderPath.isNotEmpty()) {
                "$camconBase/$subFolderPath/$baseFileName"
            } else {
                "$camconBase/$baseFileName"
            }
        } catch (e: Exception) {
            Log.e(TAG, "파일 경로 생성 실패", e)
            fileName // 폴백으로 파일명만 반환
        }
    }

    /**
     * 카메라 내부 경로에서 서브폴더를 추출
     * 예: /store_00010001/DCIM/105KAY_1/KY6_0035.JPG → 105KAY_1
     */
    private fun extractCameraSubFolder(filePath: String): String {
        return try {
            // DCIM 다음의 첫 번째 폴더를 서브폴더로 사용
            val pathParts = filePath.split("/")
            val dcimIndex = pathParts.indexOfFirst { it.equals("DCIM", ignoreCase = true) }

            if (dcimIndex >= 0 && dcimIndex + 1 < pathParts.size) {
                val subFolder = pathParts[dcimIndex + 1]
                Log.d(TAG, "카메라 서브폴더 추출: ${LogMask.path(filePath)} → $subFolder")
                subFolder
            } else {
                Log.w(TAG, "DCIM 폴더를 찾을 수 없음: ${LogMask.path(filePath)}")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "카메라 서브폴더 추출 실패: ${LogMask.path(filePath)}", e)
            ""
        }
    }
}