package com.inik.camcon.presentation.ui.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import coil.size.Size
import coil.size.pxOrElse
import coil.transform.Transformation
import java.io.RandomAccessFile
import kotlin.math.max

/**
 * RAW(NEF 등) 표시 방향 보정용 Coil Transformation.
 *
 * JPG 는 다운로드 후처리에서 EXIF orientation 대로 **비트맵을 물리 회전**하고 orientation=NORMAL 로
 * 저장하므로 세로컷도 똑바로 뜬다. 그러나 RAW 는 원본을 건드리지 않으므로(센서 원본=가로) Coil 기본
 * 디코더가 임베디드 프리뷰를 뽑을 때 방향을 반영하지 않아 **세로컷이 눕는다.**
 *
 * androidx ExifInterface 가 NEF 의 IFD0 orientation(0x0112)을 NORMAL 로 잘못 읽는 케이스가 있어,
 * 여기서는 **TIFF 헤더(IFD0)를 직접 파싱**해 orientation 을 확실히 얻는다(헤더 수백 바이트만 읽음 —
 * RAW 픽셀 디코딩·libraw 불필요). 그 값으로 이미 디코딩된 프리뷰 비트맵을 표준 EXIF 매핑으로 회전한다.
 *
 * ⚠️ NEF 임베디드 프리뷰는 풀 해상도(예 8256×5504=45MP≈181MB)로 디코딩될 수 있어, 회전 결과를 그대로
 * 두면 Canvas 최대 draw 크기(≈100MB)를 초과해 크래시(too large bitmap)한다. 따라서 결과를 타깃 크기
 * (상한 4096)로 **다운스케일**해 크래시와 대형 비트맵 OOM 을 방지한다.
 */
class RawExifRotationTransformation(private val filePath: String) : Transformation {

    override val cacheKey: String = "raw_exif_rotation:$filePath"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val exifOri = try {
            ExifInterface(filePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_UNDEFINED
        }

        val tiffOri = readTiffOrientation(filePath)
        // TIFF IFD0 직접 파싱값 우선(ExifInterface 가 NEF orientation 을 못 읽는 경우 대응).
        val orientation = if (tiffOri != ExifInterface.ORIENTATION_UNDEFINED) tiffOri else exifOri

        val matrix = Matrix()
        var needRotate = true
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.preScale(-1f, 1f)
            }
            else -> needRotate = false
        }

        val oriented = if (needRotate) {
            Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true)
        } else {
            input
        }

        // 타깃 크기(상한 4096)로 다운스케일 — Canvas too-large 크래시 + 대형 비트맵 OOM 방지.
        val cap = max(size.width.pxOrElse { 0 }, size.height.pxOrElse { 0 })
            .let { if (it in 1..MAX_DIMENSION) it else MAX_DIMENSION }
        val longest = max(oriented.width, oriented.height)

        Log.d(
            "RawExifRot",
            "file=${filePath.substringAfterLast('/')} ori=$orientation(exif=$exifOri tiff=$tiffOri) in=${input.width}x${input.height} out=${oriented.width}x${oriented.height} cap=$cap"
        )

        if (longest <= cap) return oriented

        val scale = cap.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1),
            true
        )
        // 중간 회전 비트맵은 회수(Coil 소유인 input 은 절대 회수하지 않음).
        if (scaled !== oriented && oriented !== input) oriented.recycle()
        return scaled
    }

    /**
     * TIFF/NEF 헤더의 IFD0 orientation(태그 0x0112, SHORT)을 직접 읽는다. 실패 시 UNDEFINED.
     */
    private fun readTiffOrientation(path: String): Int {
        return try {
            RandomAccessFile(path, "r").use { raf ->
                val h = ByteArray(8)
                raf.readFully(h)
                val little = (h[0].toInt() and 0xFF) == 0x49 && (h[1].toInt() and 0xFF) == 0x49 // "II"
                val big = (h[0].toInt() and 0xFF) == 0x4D && (h[1].toInt() and 0xFF) == 0x4D // "MM"
                if (!little && !big) return ExifInterface.ORIENTATION_UNDEFINED

                fun u16(b: ByteArray, o: Int): Int = if (little) {
                    (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
                } else {
                    ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
                }

                fun u32(b: ByteArray, o: Int): Long = if (little) {
                    (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
                            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
                } else {
                    ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
                            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)
                }

                val ifd0Off = u32(h, 4)
                if (ifd0Off <= 0 || ifd0Off > raf.length() - 2) return ExifInterface.ORIENTATION_UNDEFINED
                raf.seek(ifd0Off)
                val cntB = ByteArray(2)
                raf.readFully(cntB)
                val n = u16(cntB, 0)
                if (n <= 0 || n > 1000) return ExifInterface.ORIENTATION_UNDEFINED
                val entries = ByteArray(n * 12)
                raf.readFully(entries)
                for (i in 0 until n) {
                    val e = i * 12
                    if (u16(entries, e) == 0x0112) {
                        return u16(entries, e + 8) // SHORT value, inline
                    }
                }
                ExifInterface.ORIENTATION_UNDEFINED
            }
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_UNDEFINED
        }
    }

    companion object {
        private const val MAX_DIMENSION = 4096

        /**
         * RAW 파일일 때만 Transformation 을 반환한다(아니면 null → transformations 미적용).
         * 확장자 판정은 표시 방향 보정 전용이며 구독 게이팅(ValidateImageFormatUseCase)과 무관 —
         * UI 에서 SubscriptionUtils.isRawFile 직접 호출 금지 규약(정적 회귀 테스트)을 지키기 위한 진입점.
         */
        fun forPathOrNull(path: String): RawExifRotationTransformation? {
            val ext = path.substringAfterLast('.', "").lowercase()
            return if (ext in com.inik.camcon.domain.util.SubscriptionUtils.getSupportedRawExtensions()) {
                RawExifRotationTransformation(path)
            } else {
                null
            }
        }
    }
}
