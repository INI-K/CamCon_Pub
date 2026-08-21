package com.inik.camcon.data.datasource.nativesource

import com.inik.camcon.domain.model.LiveViewAfInfo

/**
 * 라이브뷰 이벤트를 처리하는 콜백 인터페이스
 */
interface LiveViewCallback {
    /**
     * 네이티브 프레임 펌프가 **실제로 메인 루프에 진입한** 순간 1회 호출된다.
     *
     * 첫 프레임 워치독의 기준점이다. PTP/IP 는 커맨드 채널이 하나뿐이라 라이브뷰 시작 명령이
     * 유휴 이벤트 롱폴(~5초) 뒤에 줄을 선다. 워치독을 "시작 요청" 시점부터 재면 그 대기가
     * 예산을 다 먹어, 카메라는 멀쩡한데 타임아웃으로 죽는다(Z8 실측 2026-08-19: 유휴 상태
     * 3회 전부 실패, 촬영 직후 2회 전부 성공 — 사진이 채널을 비워준 것이 유일한 차이).
     * 큐에서 기다린 시간은 카메라 책임이 아니므로 예산에서 제외한다.
     *
     * 기본 구현은 no-op — 이 신호가 필요 없는 구현체는 재정의하지 않아도 된다.
     */
    fun onLiveViewStarted() {}

    /**
     * 라이브뷰 프레임 수신 시 호출
     * @param frame JPEG 프레임 데이터 (ByteArray)
     */
    fun onLiveViewFrame(frame: ByteArray)

    /**
     * 프레임에 딸려 온 AF 정보. **니콘의 확장 라이브뷰(0x9428)에서만** 전달된다.
     *
     * 니콘 MTP 명세 9.7.1 의 LiveViewObject 표시정보(선두 1024바이트)를 그대로 옮긴 것이다.
     * 0x9203 폴백이나 다른 제조사에서는 이 콜백이 호출되지 않는다 — 헤더 구조 자체가 다르고,
     * 같은 오프셋을 남의 형식에 적용하면 오류 없이 좌표만 틀린다.
     *
     * 좌표계는 [wholeWidth] × [wholeHeight] 이다. 화면에 그릴 때는 표시 크기 대비로 환산한다.
     * 유효한 프레임은 [frames] 에 담긴 것뿐이다(명세상 96칸 중 앞의 areaCount 개만 유효).
     *
     * 기본 구현은 no-op — AF 표시가 필요 없는 구현체는 재정의하지 않아도 된다.
     */
    fun onLiveViewAfInfo(info: LiveViewAfInfo?) {}

    /**
     * 네이티브가 실제로 호출하는 진입점. JNI 에서 데이터 클래스와 List 를 직접 만들면 매 프레임
     * 객체 생성·메서드 조회 비용이 붙으므로, 원시 타입과 IntArray 로 받아 여기서 조립한다.
     *
     * @param frames 프레임당 4개씩(중심X, 중심Y, 폭, 높이) 이어 붙인 배열. 길이는 4의 배수다.
     *
     * 재정의하지 말 것 — [onLiveViewAfInfo] 를 재정의한다.
     */
    fun onLiveViewAfInfoRaw(
        wholeWidth: Int,
        wholeHeight: Int,
        focusJudgement: Int,
        afModeState: Int,
        trackingState: Int,
        selectedSubjectIndex: Int,
        frames: IntArray
    ) {
        // 좌표 기준계가 없으면 그릴 수 없다 — 네이티브가 "표시할 것 없음"을 이렇게 알린다.
        // null 을 그대로 전달해 구독자가 이전 박스를 지우도록 한다.
        if (wholeWidth <= 0 || wholeHeight <= 0) {
            onLiveViewAfInfo(null)
            return
        }
        val list = ArrayList<LiveViewAfInfo.AfFrame>(frames.size / 4)
        var i = 0
        while (i + 3 < frames.size) {
            list.add(
                LiveViewAfInfo.AfFrame(
                    centerX = frames[i],
                    centerY = frames[i + 1],
                    width = frames[i + 2],
                    height = frames[i + 3]
                )
            )
            i += 4
        }
        onLiveViewAfInfo(
            LiveViewAfInfo(
                wholeWidth = wholeWidth,
                wholeHeight = wholeHeight,
                focusJudgement = focusJudgement,
                afModeState = afModeState,
                trackingState = trackingState,
                selectedSubjectIndex = selectedSubjectIndex,
                frames = list
            )
        )
    }

    /**
     * 라이브뷰 중 사진 캡처 시 호출 (앱 내 캡처 — gp_camera_capture 경로)
     * @param path 저장된 파일 경로
     */
    fun onLivePhotoCaptured(path: String)

    /**
     * 라이브뷰 중 물리 셔터로 촬영된 사진 감지 시 호출 (FILE_ADDED 이벤트)
     * @param filePath 카메라 내 전체 경로 (folder/name)
     * @param fileName 파일명
     */
    fun onPhotoCaptured(filePath: String, fileName: String)

    /**
     * 라이브뷰 중 물리 셔터 사진 다운로드 완료 시 호출
     * @param filePath 카메라 내 전체 경로
     * @param fileName 파일명
     * @param imageData 다운로드된 이미지 바이너리 데이터
     */
    fun onPhotoDownloaded(filePath: String, fileName: String, imageData: ByteArray)

    /**
     * 실전송시간을 동반한 다운로드 완료 통지. 네이티브는 이 4-인자 시그니처를 우선 호출한다.
     *
     * [transferMs] = wait_for_event 소요 + 파일 다운로드 소요. 소니 PTP/IP 는 wire 전송이
     * wait_for_event **내부**(GetObject 인라인)에서 끝나 Kotlin 측 시계 창(markDownloading→
     * markProcessing)이 콜백 배관 지연(~20ms)만 재고, 그 결과 11MB 가 550MB/s 로 표시됐다
     * (A7C 실측 2026-08-18). 합산 창은 소니(wait 에 전송 포함)·니콘(다운로드 소요에 포함)
     * 양쪽에서 실전송을 덮는다. 기본 구현은 3-인자로 위임 — 기존 구현체는 수정 불필요.
     */
    fun onPhotoDownloaded(filePath: String, fileName: String, imageData: ByteArray, transferMs: Long) =
        onPhotoDownloaded(filePath, fileName, imageData)
}
