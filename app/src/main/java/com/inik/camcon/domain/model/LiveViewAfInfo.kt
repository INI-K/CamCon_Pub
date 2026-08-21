package com.inik.camcon.domain.model

/**
 * 라이브뷰 프레임에 딸려 오는 AF 표시 정보.
 *
 * 출처는 니콘 MTP 명세 9.7.1 의 LiveViewObject(with version) 표시정보 영역(선두 1024바이트)이며,
 * 확장 라이브뷰 명령(GetLiveViewImageEx, 0x9428)으로 받은 프레임에만 존재한다.
 *
 * ⚠️ 구세대 명령(GetLiveViewImage, 0x9203)의 데이터셋은 명세 9.7.2 로 **구조가 다르다**.
 * 버전 필드가 없어 모든 오프셋이 밀리므로, 같은 파서를 태우면 오류 없이 좌표만 어긋난다.
 * 그래서 네이티브가 0x9428 로 받은 프레임에서만 이 객체를 만들어 올린다.
 */
data class LiveViewAfInfo(
    /** 좌표 기준이 되는 전체 폭. 명세의 "Whole size / Horizontal". */
    val wholeWidth: Int,
    /** 좌표 기준이 되는 전체 높이. 명세의 "Whole size / Vertical". */
    val wholeHeight: Int,
    /** 합초 판정. 0=정보 없음, 1=미합초, 2=합초. */
    val focusJudgement: Int,
    /** AF 모드 상태. 0=기타, 1=피사체 검출 AF, 2=오토에어리어·타깃추적·3D추적. */
    val afModeState: Int,
    /** 추적 상태. 0=대기, 2=추적 중. */
    val trackingState: Int,
    /** 피사체 검출 시 선택된 피사체 인덱스(0~34). */
    val selectedSubjectIndex: Int,
    /** 유효한 AF 프레임 목록. 명세상 96칸 중 "AF area number" 만큼만 유효하다. */
    val frames: List<AfFrame>
) {
    /**
     * AF 프레임 하나. 좌표는 [wholeWidth] × [wholeHeight] 기준이고 **중심점**이다.
     * 화면에 그릴 때는 중심에서 크기의 절반씩 벌려 사각형을 만든다.
     */
    data class AfFrame(
        val centerX: Int,
        val centerY: Int,
        val width: Int,
        val height: Int
    )

    /** 합초 상태인가. 표시 색을 가르는 데 쓴다. */
    val isFocused: Boolean get() = focusJudgement == FOCUS_JUDGEMENT_FOCUSED

    companion object {
        const val FOCUS_JUDGEMENT_NO_INFO = 0
        const val FOCUS_JUDGEMENT_NOT_FOCUSED = 1
        const val FOCUS_JUDGEMENT_FOCUSED = 2

        const val AF_MODE_OTHER = 0
        const val AF_MODE_SUBJECT_DETECTION = 1
        const val AF_MODE_AUTO_AREA_OR_TRACKING = 2

        const val TRACKING_WAITING = 0
        const val TRACKING_ACTIVE = 2
    }
}
