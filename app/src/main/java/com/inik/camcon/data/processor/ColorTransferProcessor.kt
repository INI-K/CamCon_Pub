package com.inik.camcon.data.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.opengl.GLES20
import androidx.exifinterface.media.ExifInterface
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * MKL 색감 전송을 위한 커스텀 GPU 필터
 */
class GPUImageMKLColorTransferFilter(
    private val inputStats: Array<FloatArray>,
    private val referenceStats: Array<FloatArray>,
    private val intensity: Float = 0.3f
) : GPUImageFilter(NO_FILTER_VERTEX_SHADER, MKL_FRAGMENT_SHADER) {

    companion object {
        // MKL 색감 전송을 위한 커스텀 프래그먼트 셰이더
        const val MKL_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            
            // MKL 색감 전송 파라미터
            uniform vec3 inputMean;
            uniform vec3 inputStdDev;
            uniform vec3 referenceMean;
            uniform vec3 referenceStdDev;
            uniform float intensity;
            
            // RGB to Lab 색공간 변환
            vec3 rgbToLab(vec3 rgb) {
                // 감마 보정 제거
                vec3 rgbNorm = vec3(
                    rgb.r > 0.04045 ? pow((rgb.r + 0.055) / 1.055, 2.4) : rgb.r / 12.92,
                    rgb.g > 0.04045 ? pow((rgb.g + 0.055) / 1.055, 2.4) : rgb.g / 12.92,
                    rgb.b > 0.04045 ? pow((rgb.b + 0.055) / 1.055, 2.4) : rgb.b / 12.92
                );
                
                // RGB to XYZ 변환 (D65 illuminant)
                vec3 xyz = vec3(
                    rgbNorm.r * 0.4124564 + rgbNorm.g * 0.3575761 + rgbNorm.b * 0.1804375,
                    rgbNorm.r * 0.2126729 + rgbNorm.g * 0.7151522 + rgbNorm.b * 0.0721750,
                    rgbNorm.r * 0.0193339 + rgbNorm.g * 0.1191920 + rgbNorm.b * 0.9503041
                );
                
                // XYZ to Lab 변환
                vec3 xyzNorm = vec3(
                    xyz.x / 0.95047,
                    xyz.y / 1.00000,
                    xyz.z / 1.08883
                );
                
                vec3 f = vec3(
                    xyzNorm.x > 0.008856 ? pow(xyzNorm.x, 1.0/3.0) : (7.787 * xyzNorm.x + 16.0/116.0),
                    xyzNorm.y > 0.008856 ? pow(xyzNorm.y, 1.0/3.0) : (7.787 * xyzNorm.y + 16.0/116.0),
                    xyzNorm.z > 0.008856 ? pow(xyzNorm.z, 1.0/3.0) : (7.787 * xyzNorm.z + 16.0/116.0)
                );
                
                return vec3(
                    116.0 * f.y - 16.0,
                    500.0 * (f.x - f.y),
                    200.0 * (f.y - f.z)
                );
            }
            
            // Lab to RGB 색공간 변환
            vec3 labToRgb(vec3 lab) {
                // Lab to XYZ 변환
                float fy = (lab.x + 16.0) / 116.0;
                float fx = lab.y / 500.0 + fy;
                float fz = fy - lab.z / 200.0;
                
                vec3 xyzNorm = vec3(
                    pow(fx, 3.0) > 0.008856 ? pow(fx, 3.0) : (fx - 16.0/116.0) / 7.787,
                    pow(fy, 3.0) > 0.008856 ? pow(fy, 3.0) : (fy - 16.0/116.0) / 7.787,
                    pow(fz, 3.0) > 0.008856 ? pow(fz, 3.0) : (fz - 16.0/116.0) / 7.787
                );
                
                vec3 xyz = vec3(
                    xyzNorm.x * 0.95047,
                    xyzNorm.y * 1.00000,
                    xyzNorm.z * 1.08883
                );
                
                // XYZ to RGB 변환
                vec3 rgb = vec3(
                    xyz.x * 3.2404542 + xyz.y * -1.5371385 + xyz.z * -0.4985314,
                    xyz.x * -0.9692660 + xyz.y * 1.8760108 + xyz.z * 0.0415560,
                    xyz.x * 0.0556434 + xyz.y * -0.2040259 + xyz.z * 1.0572252
                );
                
                // 감마 보정 적용
                return vec3(
                    rgb.r > 0.0031308 ? 1.055 * pow(rgb.r, 1.0/2.4) - 0.055 : 12.92 * rgb.r,
                    rgb.g > 0.0031308 ? 1.055 * pow(rgb.g, 1.0/2.4) - 0.055 : 12.92 * rgb.g,
                    rgb.b > 0.0031308 ? 1.055 * pow(rgb.b, 1.0/2.4) - 0.055 : 12.92 * rgb.b
                );
            }
            
            void main() {
                vec4 color = texture2D(inputImageTexture, textureCoordinate);
                vec3 lab = rgbToLab(color.rgb);
                
                // MKL 색감 전송 공식 적용
                vec3 transferredLab = vec3(
                    inputStdDev.x > 0.001 ? (lab.x - inputMean.x) / inputStdDev.x * referenceStdDev.x + referenceMean.x : referenceMean.x,
                    inputStdDev.y > 0.001 ? (lab.y - inputMean.y) / inputStdDev.y * referenceStdDev.y + referenceMean.y : referenceMean.y,
                    inputStdDev.z > 0.001 ? (lab.z - inputMean.z) / inputStdDev.z * referenceStdDev.z + referenceMean.z : referenceMean.z
                );
                
                // 강도 조절 (선형 보간)
                vec3 finalLab = mix(lab, transferredLab, intensity);
                
                // RGB로 변환하여 출력
                vec3 finalRgb = labToRgb(finalLab);
                gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), color.a);
            }
        """
    }

    private var inputMeanLocation = 0
    private var inputStdDevLocation = 0
    private var referenceMeanLocation = 0
    private var referenceStdDevLocation = 0
    private var intensityLocation = 0

    override fun onInit() {
        super.onInit()
        inputMeanLocation = GLES20.glGetUniformLocation(program, "inputMean")
        inputStdDevLocation = GLES20.glGetUniformLocation(program, "inputStdDev")
        referenceMeanLocation = GLES20.glGetUniformLocation(program, "referenceMean")
        referenceStdDevLocation = GLES20.glGetUniformLocation(program, "referenceStdDev")
        intensityLocation = GLES20.glGetUniformLocation(program, "intensity")
    }

    override fun onInitialized() {
        super.onInitialized()
        updateUniforms()
    }

    private fun updateUniforms() {
        runOnDraw {
            // 입력 이미지 통계 전달
            GLES20.glUniform3f(
                inputMeanLocation,
                inputStats[0][0], inputStats[1][0], inputStats[2][0]
            )
            GLES20.glUniform3f(
                inputStdDevLocation,
                inputStats[0][1], inputStats[1][1], inputStats[2][1]
            )

            // 참조 이미지 통계 전달
            GLES20.glUniform3f(
                referenceMeanLocation,
                referenceStats[0][0], referenceStats[1][0], referenceStats[2][0]
            )
            GLES20.glUniform3f(
                referenceStdDevLocation,
                referenceStats[0][1], referenceStats[1][1], referenceStats[2][1]
            )

            // 강도 전달
            GLES20.glUniform1f(intensityLocation, intensity)
        }
    }
}

/**
 * MKL(Monge-Kantorovich Linear) 색감 전송 알고리즘을 구현하는 클래스
 * 참조 이미지의 색감을 입력 이미지에 적용합니다.
 */
@Singleton
class ColorTransferProcessor @Inject constructor() {

    /**
     * GPU MKL 색감 전송을 위한 개선된 필터 체인을 생성합니다.
     * @param inputStats 입력 이미지 통계
     * @param referenceStats 참조 이미지 통계
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.3)
     * @return MKL GPU 필터
     */
    private fun createMKLColorTransferFilter(
        inputStats: Array<FloatArray>,
        referenceStats: Array<FloatArray>,
        intensity: Float = 0.3f
    ): GPUImageFilter {
        return GPUImageMKLColorTransferFilter(inputStats, referenceStats, intensity)
    }

    /**
     * GPUImage를 사용하여 MKL 색감 전송을 수행합니다. (GPU 가속)
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceBitmap 참조할 색감의 이미지
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.3)
     * @return 색감이 적용된 결과 이미지
     */
    suspend fun applyColorTransferWithGPU(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        intensity: Float = 0.3f
    ): Bitmap? = withContext(Dispatchers.Main) {
        return@withContext try {
            val gpu = gpuImage
            if (gpu == null) {
                android.util.Log.w("ColorTransferProcessor", "⚠️ GPUImage가 초기화되지 않음 - CPU 폴백")
                return@withContext null
            }

            android.util.Log.d("ColorTransferProcessor", "🎮 MKL GPU 색감 전송 시작")

            // 참조 이미지 통계 계산
            val referenceStats = withContext(Dispatchers.Default) {
                val referenceSample = createSampleForStats(referenceBitmap, STATS_MAX_SIZE)
                val stats = calculateStatistics(
                    bitmapToLabPixelsOptimized(referenceSample)
                )
                if (referenceSample != referenceBitmap) {
                    referenceSample.recycle()
                }
                stats
            }

            // 입력 이미지 통계 계산
            val inputStats = withContext(Dispatchers.Default) {
                val inputSample = createSampleForStats(inputBitmap, STATS_MAX_SIZE)
                val stats = calculateStatistics(
                    bitmapToLabPixelsOptimized(inputSample)
                )
                if (inputSample != inputBitmap) {
                    inputSample.recycle()
                }
                stats
            }

            // MKL GPU 필터 생성
            val mklFilter = createMKLColorTransferFilter(inputStats, referenceStats, intensity)

            // GPU에서 MKL 색감 전송 적용
            gpu.setFilter(mklFilter)
            val result = gpu.getBitmapWithFilterApplied(inputBitmap)

            android.util.Log.d("ColorTransferProcessor", "✅ MKL GPU 색감 전송 완료")
            result

        } catch (e: Exception) {
            android.util.Log.w("ColorTransferProcessor", "❌ MKL GPU 색감 전송 실패: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * GPUImage를 사용하여 실시간 MKL 색감 전송을 수행합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceStats 미리 계산된 참조 이미지 통계
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.3)
     * @return 색감이 적용된 결과 이미지
     */
    suspend fun applyColorTransferWithGPUCached(
        inputBitmap: Bitmap,
        referenceStats: Array<FloatArray>,
        intensity: Float = 0.3f
    ): Bitmap? = withContext(Dispatchers.Main) {
        return@withContext try {
            val gpu = gpuImage ?: return@withContext null

            android.util.Log.d("ColorTransferProcessor", "🎮 MKL GPU 색감 전송 시작 (캐시된 통계)")

            // 입력 이미지 통계 계산
            val inputStats = withContext(Dispatchers.Default) {
                val inputSample = createSampleForStats(inputBitmap, STATS_MAX_SIZE)
                val stats = calculateStatistics(
                    bitmapToLabPixelsOptimized(inputSample)
                )
                if (inputSample != inputBitmap) {
                    inputSample.recycle()
                }
                stats
            }

            // MKL GPU 필터 생성
            val mklFilter = createMKLColorTransferFilter(inputStats, referenceStats, intensity)

            // GPU에서 MKL 색감 전송 적용
            gpu.setFilter(mklFilter)
            val result = gpu.getBitmapWithFilterApplied(inputBitmap)

            android.util.Log.d("ColorTransferProcessor", "✅ MKL GPU 색감 전송 완료 (캐시된 통계)")
            result

        } catch (e: Exception) {
            android.util.Log.w("ColorTransferProcessor", "❌ MKL GPU 색감 전송 실패: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    // GPUImage 인스턴스 (지연 초기화)
    private var gpuImage: GPUImage? = null

    // Context를 저장하기 위한 변수
    private var context: Context? = null

    /**
     * GPUImage를 초기화합니다.
     * @param context Android Context
     */
    fun initializeGPUImage(context: Context) {
        this.context = context
        if (gpuImage == null) {
            gpuImage = GPUImage(context)
        }
    }

    companion object {
        // 통계 계산을 위한 최대 이미지 크기 제한 (메모리 절약)
        private const val STATS_MAX_SIZE = 800 // 통계 계산용 샘플 크기를 줄여 성능 향상
        // 병렬 처리를 위한 청크 크기
        private const val CHUNK_SIZE = 5000 // 더 큰 청크 크기로 오버헤드 감소
        // 캐시 크기 제한
        private const val MAX_CACHE_SIZE = 10
    }

    // 네이티브 함수 선언
    private external fun applyColorTransferNative(
        inputBitmap: Bitmap,
        inputStats: FloatArray,
        referenceStats: FloatArray
    )

    private external fun calculateStatsNative(
        bitmap: Bitmap,
        sampleStep: Int
    ): FloatArray?

    // 참조 이미지 통계 캐시 (LRU 캐시 구현)
    private val referenceStatsCache = ConcurrentHashMap<String, Array<FloatArray>>()
    private val cacheAccessOrder = mutableListOf<String>()

    /**
     * 색감 전송을 수행합니다. (고성능 최적화 버전)
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceBitmap 참조할 색감의 이미지
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지 (원본 해상도 유지)
     */
    suspend fun applyColorTransfer(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        intensity: Float = 0.3f
    ): Bitmap = withContext(Dispatchers.Default) {

        try {
            // 메모리 사용량 모니터링
            val runtime = Runtime.getRuntime()
            val availableMemory =
                runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())

            // 메모리 부족 시 더 작은 샘플 사용
            val statsMaxSize = if (availableMemory < 150 * 1024 * 1024) 400 else STATS_MAX_SIZE

            // 병렬로 통계 계산 실행
            val inputStatsDeferred = async {
                val inputSample = createSampleForStats(inputBitmap, statsMaxSize)
                val inputStats = calculateStatisticsNative(inputSample, 2) ?: calculateStatistics(
                    bitmapToLabPixelsOptimized(inputSample)
                )

                // 샘플 이미지 메모리 해제
                if (inputSample != inputBitmap) {
                    inputSample.recycle()
                }
                inputStats
            }

            val referenceStatsDeferred = async {
                val referenceSample = createSampleForStats(referenceBitmap, statsMaxSize)
                val referenceStats =
                    calculateStatisticsNative(referenceSample, 2) ?: calculateStatistics(
                        bitmapToLabPixelsOptimized(referenceSample)
                    )

                // 샘플 이미지 메모리 해제
                if (referenceSample != referenceBitmap) {
                    referenceSample.recycle()
                }
                referenceStats
            }

            // 병렬 실행 완료 대기
            val (inputStats, referenceStats) = inputStatsDeferred.await() to referenceStatsDeferred.await()

            // 메모리 정리
            System.gc()

            // 고성능 색감 전송 적용
            applyColorTransferToImageParallel(
                inputBitmap,
                inputStats,
                referenceStats,
                intensity
            )

        } catch (e: OutOfMemoryError) {
            // 메모리 부족 시 폴백 처리
            System.gc()
            Thread.sleep(100)
            handleOutOfMemoryFallback(inputBitmap, referenceBitmap, intensity)
        }
    }

    /**
     * 색감 전송을 수행합니다. (최고 성능 최적화 버전)
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceBitmap 참조할 색감의 이미지
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지 (원본 해상도 유지)
     */
    suspend fun applyColorTransferOptimized(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        intensity: Float = 0.03f
    ): Bitmap = withContext(Dispatchers.Default) {

        try {
            // 메모리 사용량 모니터링
            val runtime = Runtime.getRuntime()
            val availableMemory =
                runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())

            // 메모리 부족 시 더 작은 샘플 사용
            val statsMaxSize = if (availableMemory < 150 * 1024 * 1024) 400 else STATS_MAX_SIZE

            // 병렬로 통계 계산 실행 (네이티브 우선)
            val inputStatsDeferred = async {
                val inputSample = createSampleForStats(inputBitmap, statsMaxSize)
                val inputStats = calculateStatisticsNative(inputSample, 2) ?: calculateStatistics(
                    bitmapToLabPixelsOptimized(inputSample)
                )

                // 샘플 이미지 메모리 해제
                if (inputSample != inputBitmap) {
                    inputSample.recycle()
                }
                inputStats
            }

            val referenceStatsDeferred = async {
                val referenceSample = createSampleForStats(referenceBitmap, statsMaxSize)
                val referenceStats =
                    calculateStatisticsNative(referenceSample, 2) ?: calculateStatistics(
                        bitmapToLabPixelsOptimized(referenceSample)
                    )

                // 샘플 이미지 메모리 해제
                if (referenceSample != referenceBitmap) {
                    referenceSample.recycle()
                }
                referenceStats
            }

            // 병렬 실행 완료 대기
            val (inputStats, referenceStats) = inputStatsDeferred.await() to referenceStatsDeferred.await()

            // 메모리 정리
            System.gc()

            // 네이티브 함수 시도
            val resultBitmap = inputBitmap.copy(inputBitmap.config ?: Bitmap.Config.ARGB_8888, true)
            val nativeSuccess = applyColorTransferNativeOptimized(
                resultBitmap,
                inputStats,
                referenceStats,
                intensity
            )

            // 네이티브 성공 시 결과 반환, 실패 시 코틀린 폴백
            if (nativeSuccess) {
                resultBitmap
            } else {
                // 네이티브 실패 시 결과 비트맵 해제
                resultBitmap.recycle()
                applyColorTransferToImageParallel(
                    inputBitmap,
                    inputStats,
                    referenceStats,
                    intensity
                )
            }

        } catch (e: OutOfMemoryError) {
            // 메모리 부족 시 폴백 처리
            System.gc()
            Thread.sleep(100)
            handleOutOfMemoryFallback(inputBitmap, referenceBitmap, intensity)
        }
    }

    /**
     * 색감 전송을 수행하고 결과를 파일로 저장합니다.
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceBitmap 참조할 색감의 이미지
     * @param originalImagePath 원본 이미지 파일 경로 (향후 EXIF 메타데이터 복사용)
     * @param outputPath 결과 이미지 저장 경로
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지, 실패 시 null
     */
    suspend fun applyColorTransferAndSave(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        originalImagePath: String,
        outputPath: String,
        intensity: Float = 0.03f
    ): Bitmap? = withContext(Dispatchers.Default) {
        try {
            // 고성능 색감 전송 적용 (원본 해상도 유지)
            val transferredBitmap =
                applyColorTransferOptimized(inputBitmap, referenceBitmap, intensity)

            // 결과 이미지를 파일로 저장
            val outputFile = File(outputPath)
            FileOutputStream(outputFile).use { outputStream ->
                transferredBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    95, // 원본 해상도이므로 품질 높게 설정
                    outputStream
                )
            }

            // UseCase에서 EXIF 복사 처리를 호출
            // copyExifMetadata(originalImagePath, outputPath)

            transferredBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 캐시된 참조 통계를 사용하여 색감 전송을 수행합니다. (LRU 캐시 적용)
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceStats 미리 계산된 참조 이미지 통계
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지 (원본 해상도 유지)
     */
    suspend fun applyColorTransferWithCachedStats(
        inputBitmap: Bitmap,
        referenceStats: Array<FloatArray>,
        intensity: Float = 0.03f
    ): Bitmap = withContext(Dispatchers.Default) {

        try {
            // 입력 이미지 통계 계산을 위한 샘플링 (성능 최적화)
            val inputSample = createSampleForStats(inputBitmap, STATS_MAX_SIZE)
            val inputLabPixels = bitmapToLabPixelsOptimized(inputSample)
            val inputStats = calculateStatistics(inputLabPixels)

            // 원본 해상도로 병렬 색감 전송 적용
            val result = applyColorTransferToImageParallel(
                inputBitmap,
                inputStats,
                referenceStats,
                intensity
            )

            // 샘플 이미지 메모리 해제
            if (inputSample != inputBitmap) {
                inputSample.recycle()
            }

            result
        } catch (e: OutOfMemoryError) {
            // 메모리 부족 시 폴백 처리
            handleOutOfMemoryFallbackWithStats(inputBitmap, referenceStats, intensity)
        }
    }

    /**
     * 캐시된 참조 통계를 사용하여 색감 전송을 수행합니다. (최고 성능 최적화 버전)
     * @param inputBitmap 색감을 적용할 입력 이미지
     * @param referenceStats 미리 계산된 참조 이미지 통계
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 색감이 적용된 결과 이미지 (원본 해상도 유지)
     */
    suspend fun applyColorTransferWithCachedStatsOptimized(
        inputBitmap: Bitmap,
        referenceStats: Array<FloatArray>,
        intensity: Float = 0.03f
    ): Bitmap = withContext(Dispatchers.Default) {

        try {
            // 입력 이미지 통계 계산 (네이티브 우선)
            val inputSample = createSampleForStats(inputBitmap, STATS_MAX_SIZE)
            val inputStats = calculateStatisticsNative(inputSample, 1) ?: calculateStatistics(
                bitmapToLabPixelsOptimized(inputSample)
            )

            // 원본 비트맵 복사본 생성
            val resultBitmap = inputBitmap.copy(inputBitmap.config ?: Bitmap.Config.ARGB_8888, true)

            // 네이티브 함수 시도
            val nativeSuccess = applyColorTransferNativeOptimized(
                resultBitmap,
                inputStats,
                referenceStats,
                intensity
            )

            // 네이티브 실패 시 코틀린 폴백
            val finalResult = if (nativeSuccess) {
                resultBitmap
            } else {
                applyColorTransferToImageParallel(
                    inputBitmap,
                    inputStats,
                    referenceStats,
                    intensity
                )
            }

            // 샘플 이미지 메모리 해제
            if (inputSample != inputBitmap) {
                inputSample.recycle()
            }

            finalResult
        } catch (e: OutOfMemoryError) {
            // 메모리 부족 시 폴백 처리
            handleOutOfMemoryFallbackWithStats(inputBitmap, referenceStats, intensity)
        }
    }

    /**
     * 참조 이미지의 통계를 캐시하고 반환합니다. (LRU 캐시 적용)
     * @param referenceImagePath 참조 이미지 파일 경로
     * @return 참조 이미지의 Lab 색공간 통계
     */
    suspend fun getCachedReferenceStats(referenceImagePath: String): Array<FloatArray>? =
        withContext(Dispatchers.Default) {
            try {
                // 캐시에서 확인
                referenceStatsCache[referenceImagePath]?.let { cachedStats ->
                    // 캐시 히트 - 액세스 순서 업데이트
                    synchronized(cacheAccessOrder) {
                        cacheAccessOrder.remove(referenceImagePath)
                        cacheAccessOrder.add(referenceImagePath)
                    }
                    return@withContext cachedStats
                }

                // 캐시 미스 - 새로 계산
                val referenceFile = File(referenceImagePath)
                if (!referenceFile.exists()) {
                    return@withContext null
                }

                val referenceBitmap = BitmapFactory.decodeFile(referenceImagePath)
                    ?: return@withContext null

                try {
                    // 참조 이미지 통계 계산
                    val referenceSample = createSampleForStats(referenceBitmap, STATS_MAX_SIZE)
                    val referenceStats =
                        calculateStatisticsNative(referenceSample, 1) ?: calculateStatistics(
                            bitmapToLabPixelsOptimized(referenceSample)
                        )

                    // 캐시에 저장 (LRU 관리)
                    synchronized(cacheAccessOrder) {
                        // 캐시 크기 제한
                        while (referenceStatsCache.size >= MAX_CACHE_SIZE) {
                            val oldestKey = cacheAccessOrder.removeAt(0)
                            referenceStatsCache.remove(oldestKey)
                        }

                        referenceStatsCache[referenceImagePath] = referenceStats
                        cacheAccessOrder.add(referenceImagePath)
                    }

                    // 메모리 정리
                    if (referenceSample != referenceBitmap) {
                        referenceSample.recycle()
                    }
                    referenceBitmap.recycle()

                    referenceStats
                } catch (e: Exception) {
                    referenceBitmap.recycle()
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * 캐시를 초기화합니다.
     */
    fun clearCache() {
        synchronized(cacheAccessOrder) {
            referenceStatsCache.clear()
            cacheAccessOrder.clear()
        }
    }

    /**
     * 통계 계산을 위한 샘플 이미지 생성 (개선된 버전)
     */
    private fun createSampleForStats(bitmap: Bitmap, statsMaxSize: Int = STATS_MAX_SIZE): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 이미지가 이미 작으면 그대로 반환
        if (width <= statsMaxSize && height <= statsMaxSize) {
            return bitmap
        }

        // 더 공격적인 다운샘플링으로 성능 향상
        return scaleDownBitmap(bitmap, statsMaxSize)
    }

    /**
     * 메모리 절약을 위해 비트맵 크기를 줄입니다
     */
    private fun scaleDownBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 이미지가 이미 작으면 그대로 반환
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        // 비율을 유지하면서 크기 조정
        val scale = min(
            maxSize.toFloat() / width,
            maxSize.toFloat() / height
        )

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 비트맵을 Lab 색공간 픽셀 배열로 변환 (통계 계산용)
     */
    private fun bitmapToLabPixels(bitmap: Bitmap): Array<FloatArray> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val labPixels = mutableListOf<FloatArray>()

        // 더 공격적인 샘플링으로 성능 향상
        val totalPixels = width * height
        val sampleStep = when {
            totalPixels > 100000 -> maxOf(1, totalPixels / 20000) // 최대 2만 픽셀 샘플링
            totalPixels > 50000 -> maxOf(1, totalPixels / 25000) // 최대 2.5만 픽셀 샘플링
            else -> 1 // 작은 이미지는 모든 픽셀 사용
        }

        for (i in pixels.indices step sampleStep) {
            val rgb = pixels[i]
            val r = Color.red(rgb) / 255.0f
            val g = Color.green(rgb) / 255.0f
            val b = Color.blue(rgb) / 255.0f

            val lab = rgbToLab(r, g, b)
            labPixels.add(floatArrayOf(lab[0], lab[1], lab[2]))
        }

        return labPixels.toTypedArray()
    }

    /**
     * 비트맵을 Lab 색공간 픽셀 배열로 변환 (최적화된 버전)
     */
    private fun bitmapToLabPixelsOptimized(bitmap: Bitmap): Array<FloatArray> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val labPixels = mutableListOf<FloatArray>()

        // 더 공격적인 샘플링으로 성능 향상
        val totalPixels = width * height
        val sampleStep = when {
            totalPixels > 100000 -> maxOf(1, totalPixels / 20000) // 최대 2만 픽셀 샘플링
            totalPixels > 50000 -> maxOf(1, totalPixels / 25000) // 최대 2.5만 픽셀 샘플링
            else -> 1 // 작은 이미지는 모든 픽셀 사용
        }

        for (i in pixels.indices step sampleStep) {
            val rgb = pixels[i]
            val r = Color.red(rgb) / 255.0f
            val g = Color.green(rgb) / 255.0f
            val b = Color.blue(rgb) / 255.0f

            val lab = rgbToLab(r, g, b)
            labPixels.add(floatArrayOf(lab[0], lab[1], lab[2]))
        }

        return labPixels.toTypedArray()
    }

    /**
     * 병렬 처리로 전체 이미지에 색감 전송을 적용합니다 (고성능 최적화)
     */
    private suspend fun applyColorTransferToImageParallel(
        inputBitmap: Bitmap,
        inputStats: Array<FloatArray>,
        referenceStats: Array<FloatArray>,
        intensity: Float = 0.03f
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val pixels = IntArray(width * height)
        inputBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 색감 전송 파라미터 미리 계산
        val transferParams = Array(3) { channel ->
            val inputMean = inputStats[channel][0]
            val inputStdDev = inputStats[channel][1]
            val referenceMean = referenceStats[channel][0]
            val referenceStdDev = referenceStats[channel][1]

            floatArrayOf(inputMean, inputStdDev, referenceMean, referenceStdDev)
        }

        // 메모리 사용량 모니터링
        val runtime = Runtime.getRuntime()
        val availableMemory = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        val pixelCount = pixels.size

        // 메모리 상태에 따른 처리 방식 결정
        if (availableMemory < 100 * 1024 * 1024 || pixelCount > 20_000_000) { // 100MB 미만이거나 2천만 픽셀 초과
            // 순차 처리 (메모리 절약)
            processPixelChunkSequential(pixels, 0, pixelCount, transferParams, intensity)
        } else {
            // 병렬 처리를 위한 청크 분할
            val numCores = Runtime.getRuntime().availableProcessors()
            val chunkSize = maxOf(CHUNK_SIZE, pixelCount / (numCores * 2))

            val deferredResults = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

            for (startIdx in 0 until pixelCount step chunkSize) {
                val endIdx = minOf(startIdx + chunkSize, pixelCount)

                val deferred = async {
                    processPixelChunk(pixels, startIdx, endIdx, transferParams, intensity)
                }
                deferredResults.add(deferred)
            }

            // 모든 청크 처리 완료 대기
            deferredResults.awaitAll()
        }

        // 결과 비트맵 생성
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * 순차 처리로 픽셀을 처리합니다 (메모리 절약용)
     */
    private fun processPixelChunkSequential(
        pixels: IntArray,
        startIdx: Int,
        endIdx: Int,
        transferParams: Array<FloatArray>,
        intensity: Float = 0.03f
    ) {
        for (i in startIdx until endIdx) {
            val rgb = pixels[i]
            val r = Color.red(rgb) / 255.0f
            val g = Color.green(rgb) / 255.0f
            val b = Color.blue(rgb) / 255.0f

            // RGB -> Lab 변환
            val lab = rgbToLab(r, g, b)

            // 색감 전송 적용 (강도 조절)
            val transferredLab = FloatArray(3)
            for (channel in 0..2) {
                val inputValue = lab[channel]
                val params = transferParams[channel]
                val inputMean = params[0]
                val inputStdDev = params[1]
                val referenceMean = params[2]
                val referenceStdDev = params[3]

                // MKL 색감 전송 공식 적용 (강도 조절)
                val normalizedValue = if (inputStdDev > 0.001f) {
                    (inputValue - inputMean) / inputStdDev
                } else {
                    0.0f
                }

                transferredLab[channel] = inputValue + (normalizedValue * referenceStdDev + referenceMean - inputValue) * intensity
            }

            // Lab -> RGB 변환
            val transferredRgb = labToRgb(transferredLab[0], transferredLab[1], transferredLab[2])

            // RGB 값을 0-255 범위로 클램핑
            val newR = (transferredRgb[0] * 255).toInt().coerceIn(0, 255)
            val newG = (transferredRgb[1] * 255).toInt().coerceIn(0, 255)
            val newB = (transferredRgb[2] * 255).toInt().coerceIn(0, 255)

            pixels[i] = Color.argb(255, newR, newG, newB)
        }
    }

    /**
     * 픽셀 청크를 처리합니다 (병렬 처리용)
     */
    private fun processPixelChunk(
        pixels: IntArray,
        startIdx: Int,
        endIdx: Int,
        transferParams: Array<FloatArray>,
        intensity: Float = 0.03f
    ) {
        for (i in startIdx until endIdx) {
            val rgb = pixels[i]
            val r = Color.red(rgb) / 255.0f
            val g = Color.green(rgb) / 255.0f
            val b = Color.blue(rgb) / 255.0f

            // RGB -> Lab 변환
            val lab = rgbToLab(r, g, b)

            // 색감 전송 적용 (강도 조절)
            val transferredLab = FloatArray(3)
            for (channel in 0..2) {
                val inputValue = lab[channel]
                val params = transferParams[channel]
                val inputMean = params[0]
                val inputStdDev = params[1]
                val referenceMean = params[2]
                val referenceStdDev = params[3]

                // MKL 색감 전송 공식 적용 (강도 조절)
                val normalizedValue = if (inputStdDev > 0.001f) {
                    (inputValue - inputMean) / inputStdDev
                } else {
                    0.0f
                }

                val transferredValue = normalizedValue * referenceStdDev + referenceMean
                // 강도 조절: 원본과 전송된 값 사이의 보간
                transferredLab[channel] = inputValue + (transferredValue - inputValue) * intensity
            }

            // Lab -> RGB 변환
            val transferredRgb = labToRgb(transferredLab[0], transferredLab[1], transferredLab[2])

            // RGB 값을 0-255 범위로 클램핑
            val newR = (transferredRgb[0] * 255).toInt().coerceIn(0, 255)
            val newG = (transferredRgb[1] * 255).toInt().coerceIn(0, 255)
            val newB = (transferredRgb[2] * 255).toInt().coerceIn(0, 255)

            pixels[i] = Color.argb(255, newR, newG, newB)
        }
    }

    /**
     * 메모리 부족 시 폴백 처리 (두 비트맵 모두 있는 경우)
     */
    private suspend fun handleOutOfMemoryFallback(
        inputBitmap: Bitmap,
        referenceBitmap: Bitmap,
        intensity: Float = 0.03f
    ): Bitmap {
        // 메모리 부족 시 폴백: 크기를 줄여서 처리
        val maxSize = 1920 // 첫 번째 폴백 크기

        try {
            // 메모리 상태 확인
            val runtime = Runtime.getRuntime()
            val availableMemory =
                runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())

            // 메모리 부족 정도에 따라 크기 조정
            val fallbackSize = when {
                availableMemory < 50 * 1024 * 1024 -> 720  // 50MB 미만 시 720p
                availableMemory < 100 * 1024 * 1024 -> 1080 // 100MB 미만 시 1080p
                else -> maxSize // 기본 1920p
            }

            val scaledInputBitmap = scaleDownBitmap(inputBitmap, fallbackSize)
            val scaledReferenceBitmap = scaleDownBitmap(referenceBitmap, fallbackSize)

            // 통계 계산도 더 작은 샘플로
            val inputLabPixels = bitmapToLabPixelsOptimized(scaledInputBitmap)
            val referenceLabPixels = bitmapToLabPixelsOptimized(scaledReferenceBitmap)

            val inputStats = calculateStatistics(inputLabPixels)
            val referenceStats = calculateStatistics(referenceLabPixels)

            val result = applyColorTransferToImageParallel(
                scaledInputBitmap,
                inputStats,
                referenceStats,
                intensity
            )

            // 스케일된 비트맵들 메모리 해제
            if (scaledInputBitmap != inputBitmap) {
                scaledInputBitmap.recycle()
            }
            if (scaledReferenceBitmap != referenceBitmap) {
                scaledReferenceBitmap.recycle()
            }

            return result
        } catch (e2: OutOfMemoryError) {
            // 두 번째 폴백: 더 작은 크기로 처리
            System.gc()
            Thread.sleep(100)

            val verySmallSize = 720 // 아주 작은 크기
            val verySmallInput = scaleDownBitmap(inputBitmap, verySmallSize)
            val verySmallReference = scaleDownBitmap(referenceBitmap, verySmallSize)

            // 가장 단순한 처리 방식 사용
            val inputLabPixels = bitmapToLabPixelsOptimized(verySmallInput)
            val referenceLabPixels = bitmapToLabPixelsOptimized(verySmallReference)

            val inputStats = calculateStatistics(inputLabPixels)
            val referenceStats = calculateStatistics(referenceLabPixels)

            // 단순한 색감 전송 적용 (병렬 처리 없이)
            val result = applyColorTransferToImageSimple(
                verySmallInput,
                inputStats,
                referenceStats,
                intensity
            )

            if (verySmallInput != inputBitmap) {
                verySmallInput.recycle()
            }
            if (verySmallReference != referenceBitmap) {
                verySmallReference.recycle()
            }

            return result
        }
    }

    /**
     * 메모리 부족 시 폴백 처리 (통계가 있는 경우)
     */
    private suspend fun handleOutOfMemoryFallbackWithStats(
        inputBitmap: Bitmap,
        referenceStats: Array<FloatArray>,
        intensity: Float = 0.03f
    ): Bitmap {
        // 메모리 부족 시 폴백: 크기를 줄여서 처리
        val maxSize = 1920 // 첫 번째 폴백 크기

        try {
            // 메모리 상태 확인
            val runtime = Runtime.getRuntime()
            val availableMemory =
                runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())

            // 메모리 부족 정도에 따라 크기 조정
            val fallbackSize = when {
                availableMemory < 50 * 1024 * 1024 -> 720  // 50MB 미만 시 720p
                availableMemory < 100 * 1024 * 1024 -> 1080 // 100MB 미만 시 1080p
                else -> maxSize // 기본 1920p
            }

            val scaledInputBitmap = scaleDownBitmap(inputBitmap, fallbackSize)

            val inputLabPixels = bitmapToLabPixelsOptimized(scaledInputBitmap)
            val inputStats = calculateStatistics(inputLabPixels)

            val result = applyColorTransferToImageParallel(
                scaledInputBitmap,
                inputStats,
                referenceStats,
                intensity
            )

            // 스케일된 비트맵 메모리 해제
            if (scaledInputBitmap != inputBitmap) {
                scaledInputBitmap.recycle()
            }

            return result
        } catch (e2: OutOfMemoryError) {
            // 두 번째 폴백: 더 작은 크기로 처리
            System.gc()
            Thread.sleep(100)

            val verySmallSize = 720 // 아주 작은 크기
            val verySmallInput = scaleDownBitmap(inputBitmap, verySmallSize)

            val inputLabPixels = bitmapToLabPixelsOptimized(verySmallInput)
            val inputStats = calculateStatistics(inputLabPixels)

            // 단순한 색감 전송 적용 (병렬 처리 없이)
            val result = applyColorTransferToImageSimple(
                verySmallInput,
                inputStats,
                referenceStats,
                intensity
            )

            if (verySmallInput != inputBitmap) {
                verySmallInput.recycle()
            }

            return result
        }
    }

    /**
     * 단순한 색감 전송 적용 (메모리 절약을 위한 비병렬 처리, 강도 조절 적용)
     */
    private suspend fun applyColorTransferToImageSimple(
        inputBitmap: Bitmap,
        inputStats: Array<FloatArray>,
        referenceStats: Array<FloatArray>,
        intensity: Float = 0.03f
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = inputBitmap.width
        val height = inputBitmap.height
        val pixels = IntArray(width * height)
        inputBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 색감 전송 파라미터 미리 계산
        val transferParams = Array(3) { channel ->
            val inputMean = inputStats[channel][0]
            val inputStdDev = inputStats[channel][1]
            val referenceMean = referenceStats[channel][0]
            val referenceStdDev = referenceStats[channel][1]

            floatArrayOf(inputMean, inputStdDev, referenceMean, referenceStdDev)
        }

        // 단순한 순차 처리 (메모리 절약, 강도 조절 적용)
        for (i in pixels.indices) {
            val rgb = pixels[i]
            val r = Color.red(rgb) / 255.0f
            val g = Color.green(rgb) / 255.0f
            val b = Color.blue(rgb) / 255.0f

            // RGB -> Lab 변환
            val lab = rgbToLab(r, g, b)

            // 색감 전송 적용 (강도 조절)
            val transferredLab = FloatArray(3)
            for (channel in 0..2) {
                val inputValue = lab[channel]
                val params = transferParams[channel]
                val inputMean = params[0]
                val inputStdDev = params[1]
                val referenceMean = params[2]
                val referenceStdDev = params[3]

                // MKL 색감 전송 공식 적용 (강도 보간 적용)
                val normalizedValue = if (inputStdDev > 0.001f) {
                    (inputValue - inputMean) / inputStdDev
                } else {
                    0.0f
                }

                val transferredValue = normalizedValue * referenceStdDev + referenceMean
                // 강도 조절: 원본과 전송된 값 사이의 보간
                transferredLab[channel] = inputValue + (transferredValue - inputValue) * intensity
            }

            // Lab -> RGB 변환
            val transferredRgb = labToRgb(transferredLab[0], transferredLab[1], transferredLab[2])

            // RGB 값을 0-255 범위로 클램핑
            val newR = (transferredRgb[0] * 255).toInt().coerceIn(0, 255)
            val newG = (transferredRgb[1] * 255).toInt().coerceIn(0, 255)
            val newB = (transferredRgb[2] * 255).toInt().coerceIn(0, 255)

            pixels[i] = Color.argb(255, newR, newG, newB)
        }

        // 결과 비트맵 생성
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * RGB를 Lab 색공간으로 변환
     */
    private fun rgbToLab(r: Float, g: Float, b: Float): FloatArray {
        // RGB to XYZ 변환
        val rNorm = if (r > 0.04045f) ((r + 0.055f) / 1.055f).pow(2.4f) else r / 12.92f
        val gNorm = if (g > 0.04045f) ((g + 0.055f) / 1.055f).pow(2.4f) else g / 12.92f
        val bNorm = if (b > 0.04045f) ((b + 0.055f) / 1.055f).pow(2.4f) else b / 12.92f

        val x = rNorm * 0.4124564f + gNorm * 0.3575761f + bNorm * 0.1804375f
        val y = rNorm * 0.2126729f + gNorm * 0.7151522f + bNorm * 0.0721750f
        val z = rNorm * 0.0193339f + gNorm * 0.1191920f + bNorm * 0.9503041f

        // XYZ to Lab 변환 (D65 illuminant)
        val xn = x / 0.95047f
        val yn = y / 1.00000f
        val zn = z / 1.08883f

        val fx = if (xn > 0.008856f) xn.pow(1.0f / 3.0f) else (7.787f * xn + 16.0f / 116.0f)
        val fy = if (yn > 0.008856f) yn.pow(1.0f / 3.0f) else (7.787f * yn + 16.0f / 116.0f)
        val fz = if (zn > 0.008856f) zn.pow(1.0f / 3.0f) else (7.787f * zn + 16.0f / 116.0f)

        val l = 116.0f * fy - 16.0f
        val a = 500.0f * (fx - fy)
        val labB = 200.0f * (fy - fz)

        return floatArrayOf(l, a, labB)
    }

    /**
     * Lab를 RGB 색공간으로 변환
     */
    private fun labToRgb(l: Float, a: Float, b: Float): FloatArray {
        // Lab to XYZ 변환
        val fy = (l + 16.0f) / 116.0f
        val fx = a / 500.0f + fy
        val fz = fy - b / 200.0f

        val xn = if (fx.pow(3.0f) > 0.008856f) fx.pow(3.0f) else (fx - 16.0f / 116.0f) / 7.787f
        val yn = if (fy.pow(3.0f) > 0.008856f) fy.pow(3.0f) else (fy - 16.0f / 116.0f) / 7.787f
        val zn = if (fz.pow(3.0f) > 0.008856f) fz.pow(3.0f) else (fz - 16.0f / 116.0f) / 7.787f

        val x = xn * 0.95047f
        val y = yn * 1.00000f
        val z = zn * 1.08883f

        // XYZ to RGB 변환
        val r = x * 3.2404542f + y * -1.5371385f + z * -0.4985314f
        val g = x * -0.9692660f + y * 1.8760108f + z * 0.0415560f
        val rgbB = x * 0.0556434f + y * -0.2040259f + z * 1.0572252f

        // 감마 보정
        val rFinal = if (r > 0.0031308f) 1.055f * r.pow(1.0f / 2.4f) - 0.055f else 12.92f * r
        val gFinal = if (g > 0.0031308f) 1.055f * g.pow(1.0f / 2.4f) - 0.055f else 12.92f * g
        val bFinal =
            if (rgbB > 0.0031308f) 1.055f * rgbB.pow(1.0f / 2.4f) - 0.055f else 12.92f * rgbB

        return floatArrayOf(rFinal, gFinal, bFinal)
    }

    /**
     * 각 채널별 통계 계산 (평균, 표준편차)
     */
    private fun calculateStatistics(labPixels: Array<FloatArray>): Array<FloatArray> {
        val stats = Array(3) { FloatArray(2) } // [채널][평균, 표준편차]

        for (channel in 0..2) {
            var sum = 0.0f
            var sumSquares = 0.0f

            for (pixel in labPixels) {
                val value = pixel[channel]
                sum += value
                sumSquares += value * value
            }

            val mean = sum / labPixels.size
            val variance = (sumSquares / labPixels.size) - (mean * mean)
            val stdDev = sqrt(variance)

            stats[channel][0] = mean
            stats[channel][1] = stdDev
        }

        return stats
    }

    /**
     * 네이티브 코드로 통계를 계산합니다 (고성능)
     * @param bitmap 통계를 계산할 비트맵
     * @param sampleStep 샘플링 간격
     * @return [L_mean, L_stdDev, a_mean, a_stdDev, b_mean, b_stdDev] 배열
     */
    private fun calculateStatisticsNative(bitmap: Bitmap, sampleStep: Int): Array<FloatArray>? {
        return try {
            // 네이티브 함수 일시 비활성화 - 코틀린 최적화 버전 사용
            null
            // val nativeStats = this.calculateStatsNative(bitmap, sampleStep)
            // if (nativeStats != null && nativeStats.size == 6) {
            //     Array(3) { channel ->
            //         floatArrayOf(
            //             nativeStats[channel * 2],     // 평균
            //             nativeStats[channel * 2 + 1]  // 표준편차
            //         )
            //     }
            // } else {
            //     null
            // }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 네이티브 코드로 색감 전송을 적용합니다 (최고 성능)
     * @param inputBitmap 입력 비트맵 (in-place 수정됨)
     * @param inputStats 입력 이미지 통계
     * @param referenceStats 참조 이미지 통계
     * @param intensity 색감 전송 강도 (0.0 ~ 1.0, 기본값 0.03)
     * @return 성공 여부
     */
    private fun applyColorTransferNativeOptimized(
        inputBitmap: Bitmap,
        inputStats: Array<FloatArray>,
        referenceStats: Array<FloatArray>,
        intensity: Float
    ): Boolean {
        return try {
            // 네이티브 함수 일시 비활성화 - 코틀린 최적화 버전 사용
            false
            // 통계 배열을 네이티브 함수 형식으로 변환
            // val inputStatsFlat = FloatArray(6)
            // val referenceStatsFlat = FloatArray(6)
            // 
            // for (i in 0..2) {
            //     inputStatsFlat[i * 2] = inputStats[i][0]      // 평균
            //     inputStatsFlat[i * 2 + 1] = inputStats[i][1]  // 표준편차
            //     referenceStatsFlat[i * 2] = referenceStats[i][0]
            //     referenceStatsFlat[i * 2 + 1] = referenceStats[i][1]
            // }
            // 
            // // 네이티브 함수 호출
            // this.applyColorTransferNative(inputBitmap, inputStatsFlat, referenceStatsFlat)
            // true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 원본 이미지의 EXIF 메타데이터를 결과 이미지로 복사합니다.
     * @param originalImagePath 원본 이미지 파일 경로
     * @param resultImagePath 결과 이미지 파일 경로
     */
    private fun copyExifMetadata(originalImagePath: String, resultImagePath: String) {
        try {
            val originalExif = ExifInterface(originalImagePath)
            val resultExif = ExifInterface(resultImagePath)

            // 중요한 EXIF 태그들을 복사
            val tagsToPreserve = arrayOf(
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_APERTURE_VALUE,
                ExifInterface.TAG_SHUTTER_SPEED_VALUE,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_CAMERA_OWNER_NAME,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                ExifInterface.TAG_USER_COMMENT
            )

            for (tag in tagsToPreserve) {
                originalExif.getAttribute(tag)?.let { value ->
                    resultExif.setAttribute(tag, value)
                }
            }

            // 처리된 이미지임을 표시
            resultExif.setAttribute(
                ExifInterface.TAG_SOFTWARE,
                "CamCon - Color Transfer Applied"
            )

            // EXIF 데이터 저장
            resultExif.saveAttributes()
        } catch (e: Exception) {
            // EXIF 복사 실패는 크리티컬하지 않으므로 로그만 출력
            println("EXIF 메타데이터 복사 실패: ${e.message}")
        }
    }
}