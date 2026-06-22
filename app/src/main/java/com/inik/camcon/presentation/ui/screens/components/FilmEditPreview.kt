package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.inik.camcon.R
import com.inik.camcon.domain.model.FilmEdit
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup

/**
 * 편집 화면 대형 프리뷰(설계 §3.3). [GPUImageView] 로 [previewBitmap] 을 렌더링하고,
 * `(lookupBitmap, edit)` → [buildFilters] 단일 빌더로 만든 [GPUImageFilterGroup] 을 적용한다.
 *
 * **프리뷰 == 내보내기**: 필터 구성은 반드시 [buildFilters](=`FilmEditorViewModel.buildPreviewFilters`
 * → `FilmAdjustmentProcessor.buildFilters`)로만 만든다. 내보내기도 동일 빌더를 쓰므로 화면과 결과가 일치한다.
 *
 * **원본 비교**: 프리뷰를 꾹 누르면([pointerInput] press) 빈 필터로 전환해 원본을 보여주고, 떼면 복귀한다.
 *
 * 메모리: [previewBitmap] 은 VM 소유(여기서 회수 금지). [lookupBitmap] 은 캐시 소유(회수 금지).
 * GPUImageView 가 파괴될 때 GL 리소스를 정리하므로 필터를 명시 해제하지 않는다.
 * **앱 전역 GPUImage 싱글톤은 건드리지 않는다**(releaseGpu 호출 금지 — 자동적용 경로와 공유).
 */
@Composable
fun FilmEditPreview(
    previewBitmap: Bitmap?,
    lookupBitmap: Bitmap?,
    edit: FilmEdit,
    buildFilters: (Bitmap?, FilmEdit) -> List<GPUImageFilter>,
    modifier: Modifier = Modifier
) {
    if (previewBitmap == null || previewBitmap.isRecycled) {
        Box(
            modifier = modifier.background(
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

    // 꾹 누르는 동안 원본(필터 없음) 표시.
    var showOriginal by remember { mutableStateOf(false) }
    val currentShowOriginal by rememberUpdatedState(showOriginal)

    // previewBitmap 이 바뀌면 새 GPUImageView 를 만든다.
    key(previewBitmap) {
        AndroidView(
            modifier = modifier.pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        showOriginal = true
                        // press 가 release(또는 cancel)될 때까지 대기 후 복귀.
                        tryAwaitRelease()
                        showOriginal = false
                    }
                )
            },
            factory = { ctx ->
                GPUImageView(ctx).apply {
                    setScaleType(GPUImage.ScaleType.CENTER_INSIDE)
                    setImage(previewBitmap)
                }
            },
            update = { view ->
                val filters = if (currentShowOriginal) {
                    emptyList()
                } else {
                    buildFilters(lookupBitmap, edit)
                }
                view.filter = if (filters.isEmpty()) {
                    GPUImageFilter()
                } else {
                    GPUImageFilterGroup(filters)
                }
                view.requestRender()
            }
        )
    }
}
