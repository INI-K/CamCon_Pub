package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.domain.model.CameraCaptureCallback

/**
 * 카메라 캡처 이벤트를 처리하는 리스너 인터페이스.
 *
 * JNI 네이티브 코드와의 호환성을 위해 data 레이어에 유지한다.
 * domain의 [CameraCaptureCallback]을 확장하여 레이어 간 계약을 공유한다.
 */
interface CameraCaptureListener : CameraCaptureCallback
