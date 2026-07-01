package com.inik.camcon.presentation.ui.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import coil.size.Size
import coil.transform.Transformation
import java.io.RandomAccessFile

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

        Log.d(
            "RawExifRot",
            "file=${filePath.substringAfterLast('/')} exif=$exifOri tiff=$tiffOri use=$orientation in=${input.width}x${input.height}"
        )

        val matrix = Matrix()
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
            else -> return input
        }
        return Bitmap.createBitmap(input, 0, 0, input.width, input.height, matrix, true)
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
}
