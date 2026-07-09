package com.inik.camcon.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 무옵션 [BitmapFactory.decodeFile] 풀디코드를 대체하는 레이어 중립 헬퍼.
 *
 * data/presentation 양쪽에서 소비하므로 `data.util`이 아닌 `utils`(인프라)에 둔다.
 * (data → presentation 역방향 의존 회피 목적)
 *
 * `inSampleSize` 로직은 [PhotoDownloadManager]/[PhotoImageManager]의 기존
 * `calculateInSampleSize`(power-of-2, 바이트 동일)와 같다.
 */
object BitmapDecodeUtils {

    /**
     * 요청 크기 이하가 될 때까지 2의 거듭제곱 `inSampleSize`를 계산한다.
     * (기존 PhotoDownloadManager:1330 / PhotoImageManager:576 과 동일 로직)
     */
    fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
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
     * `inJustDecodeBounds`로 원본 크기만 먼저 읽고, [calculateInSampleSize]로 결정한
     * `inSampleSize`를 적용해 다운샘플 디코딩한다.
     *
     * 요청 크기 이하 원본이면 `inSampleSize=1`이라 동작이 기존 풀디코드와 동일하다.
     */
    fun decodeSampled(
        path: String,
        reqWidth: Int,
        reqHeight: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, boundsOptions)

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                reqWidth,
                reqHeight
            )
            inPreferredConfig = config
        }
        return BitmapFactory.decodeFile(path, decodeOptions)
    }
}
