package com.inik.camcon.domain.model

/**
 * 촬영은 성공했지만 파일이 즉시 회수되지 못하고 백그라운드 전송(이벤트 리스너의
 * 전송큐 경로)에 배달이 위임됐음을 나타낸다. **오류가 아니다** — 캡처 코루틴의
 * 반환 타입(Result)에 "성공했지만 사진 없음"이 없어서 실패 채널로 전달하되,
 * 소비자([com.inik.camcon.presentation.viewmodel.CameraOperationsManager])가 이
 * 타입만 골라 에러 UI 없이 처리한다. 실제 사진은 외부 셔터 파이프라인
 * (FILE_ADDED → 다운로드 → capturedPhotos StateFlow)으로 도착한다.
 *
 * 발생 조건: Nikon Wi-Fi WT3T 프로파일에서 촬영 직후 객체 조회 잠금
 * (GetObjectInfo 0x200F, libgphoto2 패치 0029 관용 경로) 또는 RAW 필터로
 * 즉시 수신분이 없는 경우.
 *
 * @param fileName 카메라가 보고한 원본 파일명(비어 있을 수 있음)
 */
class CaptureDeferredException(
    val fileName: String
) : Exception("촬영 완료 — 파일은 백그라운드 전송으로 배달 예정: $fileName")
