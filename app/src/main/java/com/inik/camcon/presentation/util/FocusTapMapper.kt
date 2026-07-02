package com.inik.camcon.presentation.util

import kotlin.math.min
import kotlin.math.roundToInt

/** 카메라 AF 포인트 좌표(디코드 라이브뷰 이미지 픽셀 공간). */
data class CameraFocusPoint(val x: Int, val y: Int)

/** ContentScale.Fit 으로 컨테이너 안에 중앙 배치된 콘텐츠의 실제 표시 사각형(px). */
data class FittedRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * ContentScale.Fit(비율 유지·중앙 정렬) 기준으로 컨테이너 안에 실제로 그려지는 콘텐츠 사각형을 계산한다.
 * 레터박스/필러박스 여백을 뺀 영상 영역이며, 그리드·오버레이를 이 안에만 그릴 때 쓴다.
 *
 * 180° 회전은 같은 종횡비의 중앙-정렬 사각형을 그대로 두므로 회전 여부와 무관하다.
 *
 * @param boxW,boxH  컨테이너 px 크기
 * @param contentW,contentH  콘텐츠(비트맵) 원본 px
 * @return 표시 사각형. 입력이 유효하지 않으면 null.
 */
fun computeFittedRect(boxW: Int, boxH: Int, contentW: Int, contentH: Int): FittedRect? {
    if (boxW <= 0 || boxH <= 0 || contentW <= 0 || contentH <= 0) return null
    val scale = min(boxW.toFloat() / contentW, boxH.toFloat() / contentH)
    val dispW = contentW * scale
    val dispH = contentH * scale
    return FittedRect(
        left = (boxW - dispW) / 2f,
        top = (boxH - dispH) / 2f,
        width = dispW,
        height = dispH
    )
}

/**
 * 라이브뷰 탭 위치를 카메라 AF 포인트 좌표로 변환하는 순수 함수(단위테스트 대상).
 *
 * 화면 탭(컴포넌트 px) → ContentScale.Fit 레터박스 보정 → 디코드 JPEG 픽셀 → (회전 시 180° 반전).
 * 반환은 **디코드 JPEG 픽셀 좌표**다. 카메라 AF 격자(ImageWidth/Height)로의 스케일 보정은
 * 네이티브 setAFArea가 런타임에 격자를 자동 탐지해 처리한다(여기서는 하지 않는다).
 *
 * @param tapX,tapY  detectTapGestures가 준 컴포넌트-로컬 px (회전 적용 전 레이아웃 공간)
 * @param boxW,boxH  프리뷰 컴포넌트 px 크기
 * @param bmpW,bmpH  디코드 라이브뷰 비트맵 px (LiveViewFrame.width/height는 0이라 쓰지 말 것)
 * @param rotated    프리뷰 180° 회전 여부
 * @return 카메라 좌표. 탭이 레터박스 여백이면 null(무시).
 */
fun mapTapToCameraPoint(
    tapX: Float,
    tapY: Float,
    boxW: Int,
    boxH: Int,
    bmpW: Int,
    bmpH: Int,
    rotated: Boolean
): CameraFocusPoint? {
    if (boxW <= 0 || boxH <= 0 || bmpW <= 0 || bmpH <= 0) return null

    val scale = min(boxW.toFloat() / bmpW, boxH.toFloat() / bmpH)
    val dispW = bmpW * scale
    val dispH = bmpH * scale
    val offX = (boxW - dispW) / 2f
    val offY = (boxH - dispH) / 2f

    var ix = (tapX - offX) / scale
    var iy = (tapY - offY) / scale

    // 레터박스/필러박스 여백 탭은 무시
    if (ix < 0f || ix > bmpW || iy < 0f || iy > bmpH) return null

    if (rotated) {
        ix = bmpW - ix
        iy = bmpH - iy
    }

    return CameraFocusPoint(
        x = ix.roundToInt().coerceIn(0, bmpW),
        y = iy.roundToInt().coerceIn(0, bmpH)
    )
}
