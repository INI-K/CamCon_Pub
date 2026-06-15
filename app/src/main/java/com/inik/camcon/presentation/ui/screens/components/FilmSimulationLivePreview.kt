package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.inik.camcon.R
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLookupFilter

/**
 * 필름 시뮬레이션 실시간 미리보기.
 *
 * [GPUImageView] 로 [targetBitmap] 을 렌더링하고, [lookupBitmap] 룩업 테이블을
 * [GPUImageLookupFilter] 로 적용한다. LUT/강도 변경은 디바운스 없이 프레임 즉시 반영된다.
 *
 * 메모리: [GPUImageLookupFilter.setBitmap] 은 입력 비트맵을 회수하지 않고 GL 텍스처로만 업로드하므로,
 * 캐시가 소유하는 [lookupBitmap] 을 복사 없이 그대로 넘긴다. 필터는 [lookupBitmap] 단위로 remember 해
 * 강도(intensity)만 바뀔 때는 [GPUImageLookupFilter.setIntensity] 만 호출(텍스처 재업로드·할당 없음).
 */
@Composable
fun FilmSimulationLivePreview(
    targetBitmap: Bitmap?,
    lookupBitmap: Bitmap?,
    intensity: Float,
    modifier: Modifier = Modifier
) {
    if (targetBitmap == null) {
        Box(
            modifier = modifier
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.fs_no_target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }

    // targetBitmap 이 바뀌면 새 GPUImageView 를 만들기 위해 key 로 감싼다.
    key(targetBitmap) {
        // lookupBitmap 이 바뀔 때만 필터를 재생성한다(강도는 update 에서 setIntensity 로 갱신).
        val filter = remember(lookupBitmap) {
            val lk = lookupBitmap
            if (lk != null && !lk.isRecycled) {
                GPUImageLookupFilter().apply { setBitmap(lk) }
            } else {
                GPUImageFilter()
            }
        }
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                GPUImageView(ctx).apply {
                    setScaleType(GPUImage.ScaleType.CENTER_INSIDE)
                    setImage(targetBitmap)
                    setFilter(filter)
                }
            },
            update = { view ->
                if (view.filter !== filter) {
                    view.filter = filter
                }
                (filter as? GPUImageLookupFilter)?.setIntensity(intensity.coerceIn(0f, 1f))
                view.requestRender()
            }
        )
    }
}
