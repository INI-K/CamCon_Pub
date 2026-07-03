package com.inik.camcon.presentation.ui.screens.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.inik.camcon.R
import com.inik.camcon.presentation.theme.Radius

/**
 * 편집 화면 대형 프리뷰(설계 §3.3).
 *
 * [rendered] 는 [FilmEditorViewModel] 이 PixelBuffer 경로로 **강도 1.0 고정** 렌더한 "풀 룩"이고,
 * 여기서 [original] 위에 **알파 = [intensity]** 로 합성해 표시한다. GPUImageLookupFilter 셰이더의
 * `mix(original, lut, intensity)` 와 동일 수식이라, 강도 드래그가 GPU 재렌더 없이 **실시간**으로
 * 원본과 섞이는 모습이 보인다. (조정 8종이 비중립이면 합성 순서 차이로 드래그 중 근사이며,
 * 내보내기는 항상 정확 경로로 렌더된다. 조정 기본값=중립에서는 완전 일치.)
 *
 * **원본 비교**: 꾹 누르면([pointerInput] press) 오버레이를 숨겨 [original] 을 보여주고, 떼면 복귀.
 *
 * 메모리: [rendered]·[original] 모두 표시만 한다(회수 금지). [rendered] 는 VM 소유(렌더 시 교체·회수),
 * [original] 은 VM 소유 previewBitmap.
 */
@Composable
fun FilmEditPreview(
    rendered: Bitmap?,
    original: Bitmap?,
    intensity: Float,
    modifier: Modifier = Modifier
) {
    if (original == null || original.isRecycled) {
        Box(
            modifier = modifier.background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                RoundedCornerShape(Radius.md)
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

    var showOriginal by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    showOriginal = true
                    tryAwaitRelease()
                    showOriginal = false
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        // 아래 레이어: 원본(항상). 렌더 결과가 아직 없으면(첫 렌더 전/GPU 실패) 이대로 보인다.
        Image(
            bitmap = original.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        // 위 레이어: 풀 룩(강도 1 렌더)을 강도만큼 알파로 합성 — 강도 드래그 실시간 반영.
        // 꾹 누르면(원본 비교) 오버레이를 숨긴다.
        if (!showOriginal && rendered != null && !rendered.isRecycled && intensity > 0f) {
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = intensity.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
