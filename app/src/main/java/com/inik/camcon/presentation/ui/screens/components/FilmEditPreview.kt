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
import androidx.compose.ui.unit.dp
import com.inik.camcon.R

/**
 * 편집 화면 대형 프리뷰(설계 §3.3).
 *
 * **프리뷰 == 내보내기(WYSIWYG)**: [rendered] 는 [FilmEditorViewModel] 이 내보내기와 **동일 경로**
 * (`FilmAdjustmentProcessor.apply`, PixelBuffer)로 렌더한 결과 비트맵이다. 여기서는 Compose [Image] 로
 * 표시만 한다. (이전엔 GPUImageView+필터그룹으로 라이브 렌더했으나, 일부 GPU 에서 필터그룹 중간 FBO/
 * 레터박스 영역이 미초기화 노이즈로 깨지는 문제가 있어 PixelBuffer 렌더-투-비트맵으로 전환.)
 *
 * **원본 비교**: 꾹 누르면([pointerInput] press) 필터 미적용 [original] 을 보여주고, 떼면 [rendered] 로 복귀.
 *
 * 메모리: [rendered]·[original] 모두 표시만 한다(회수 금지). [rendered] 는 VM 소유(렌더 시 교체·회수),
 * [original] 은 VM 소유 previewBitmap.
 */
@Composable
fun FilmEditPreview(
    rendered: Bitmap?,
    original: Bitmap?,
    modifier: Modifier = Modifier
) {
    if (original == null || original.isRecycled) {
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
        // 렌더 결과가 아직 없으면(첫 렌더 전/GPU 실패) 원본을 보여준다.
        val shown = if (showOriginal) original else (rendered ?: original)
        if (shown != null && !shown.isRecycled) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
