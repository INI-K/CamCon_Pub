package com.inik.camcon.presentation.ui.screens.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.rememberCoilZoomState

/**
 * 줌/팬 제스처를 지원하는 사진 컨테이너.
 * 상태 호이스팅을 위해 ZoomImage 라이브러리의 rememberCoilZoomState를 사용한다.
 *
 * @param imageRequest Coil ImageRequest (이미지 데이터 및 로딩 콜백 포함)
 * @param contentDescription 접근성 설명
 * @param modifier 레이아웃 수정자
 */
@Composable
fun PhotoZoomPanContainer(
    imageRequest: coil.request.ImageRequest,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val zoomState = rememberCoilZoomState()

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTransformGestures { _, _, _, _ ->
                // ZoomImage 라이브러리가 내부적으로 제스처를 처리함.
                // 향후 커스텀 제스처 로직이 필요하면 여기에 추가 가능.
            }
        }
    ) {
        CoilZoomAsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            zoomState = zoomState,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}
