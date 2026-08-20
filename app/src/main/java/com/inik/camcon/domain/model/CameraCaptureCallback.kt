package com.inik.camcon.domain.model

/**
 * 카메라 캡처 이벤트를 처리하는 콜백 인터페이스.
 *
 * domain 레이어에 위치하여 presentation과 data 양쪽에서 참조 가능.
 * data 레이어의 CameraCaptureListener는 이 인터페이스를 확장한다.
 */
interface CameraCaptureCallback {
    /** 플러시 완료 시 호출 */
    fun onFlushComplete()

    /**
     * 사진 캡처 완료 시 호출
     * @param filePath 저장된 파일 경로
     * @param fileName 파일명
     */
    fun onPhotoCaptured(filePath: String, fileName: String)

    /**
     * 사진 다운로드 완료 시 호출 (Native에서 직접 다운로드)
     * @param filePath 카메라 내부 파일 경로
     * @param fileName 파일명
     * @param imageData 다운로드된 이미지 데이터
     */
    fun onPhotoDownloaded(filePath: String, fileName: String, imageData: ByteArray)

    /**
     * 실전송시간(ms)을 동반한 다운로드 완료 통지 — 네이티브가 우선 호출하는 4-인자 시그니처.
     * 기본 구현은 3-인자로 위임하므로 기존 구현체는 수정 불필요. 상세는 LiveViewCallback 문서 참조.
     */
    fun onPhotoDownloaded(filePath: String, fileName: String, imageData: ByteArray, transferMs: Long) =
        onPhotoDownloaded(filePath, fileName, imageData)

    /**
     * 캡처 실패 시 호출
     * @param errorCode 에러 코드
     */
    fun onCaptureFailed(errorCode: Int)

    /**
     * 셔터는 동작했지만 파일을 그 자리에서 가져올 수 없어 백그라운드 전송(이벤트 리스너의
     * 전송큐 경로)에 배달을 위임했을 때 호출된다. **촬영 실패가 아니다** — Nikon Wi-Fi
     * (WT3T 프로파일)에서 촬영 직후 객체 조회가 잠기는 경우(GetObjectInfo 0x200F, 패치
     * 0029 관용 경로)와 RAW 필터로 즉시 수신분이 없는 경우가 여기에 해당한다.
     * 기본 구현 무동작 — 캡처 코루틴을 실패로 끝내지 않으려는 구현만 override.
     * @param fileName 카메라가 보고한 원본 파일명(비어 있을 수 있음)
     */
    fun onCaptureDeferred(fileName: String) {}

    /** USB 디바이스가 분리되었을 때 호출 */
    fun onUsbDisconnected()

    /**
     * 카메라 본체에서 디바이스 프로퍼티(설정)가 변경됐을 때 호출 (이벤트 푸시 — 폴링 대체).
     * @param configName 변경된 gphoto2 config 이름 (예: "iso", "shutterspeed", "f-number")
     * 기본 구현 무동작 — 관심 있는 구현만 override.
     */
    fun onPropertyChanged(configName: String) {}

    /**
     * PTP/IP 세션이 '비자발적으로' 끊겼을 때(카메라 OFF/소켓 death 등 네이티브 이벤트 루프
     * 비정상 종료) 호출된다. 사용자가 요청한 정상 stop과는 구분되어 발화된다.
     * 기본 구현 무동작 — PTPIP 리스너 구현만 override.
     */
    fun onPtpipConnectionLost() {}
}
