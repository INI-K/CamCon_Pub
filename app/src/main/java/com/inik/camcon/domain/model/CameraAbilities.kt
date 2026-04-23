package com.inik.camcon.domain.model

import com.google.gson.annotations.SerializedName

/**
 * libgphoto2 CameraAbilities 정보
 *
 * `gp_camera_get_abilities()` API로 조회한 카메라 기능 정보
 *
 * 제조사/모델 상관없이 카메라가 지원하는 기능을 동적으로 파악
 */
data class CameraAbilitiesInfo(
    val model: String,
    val status: String,  // PRODUCTION, TESTING, EXPERIMENTAL, DEPRECATED
    @SerializedName("port_type")
    val portType: Int,   // GP_PORT_USB=1, GP_PORT_SERIAL=2, GP_PORT_PTPIP=16
    @SerializedName("usb_vendor")
    val usbVendor: String,
    @SerializedName("usb_product")
    val usbProduct: String,
    @SerializedName("usb_class")
    val usbClass: Int,

    // 비트마스크 원본 값
    val operations: Int,
    @SerializedName("file_operations")
    val fileOperations: Int,
    @SerializedName("folder_operations")
    val folderOperations: Int,

    // 파싱된 지원 기능
    val supports: CameraSupports
) {
    /**
     * 프로덕션 품질 드라이버인지
     */
    fun isProductionReady(): Boolean = status == "PRODUCTION"

    /**
     * USB 연결인지
     */
    fun isUsbConnection(): Boolean = (portType and 1) != 0  // GP_PORT_USB

    /**
     * PTPIP 연결인지
     */
    fun isPtpipConnection(): Boolean = (portType and 16) != 0  // GP_PORT_PTPIP

    /**
     * 제조사 감지 (모델명 기반)
     */
    fun getManufacturer(): String {
        return when {
            model.contains("Canon", ignoreCase = true) -> "Canon"
            model.contains("Nikon", ignoreCase = true) -> "Nikon"
            model.contains("Sony", ignoreCase = true) -> "Sony"
            model.contains("FUJIFILM", ignoreCase = true) ||
                    model.contains("Fuji", ignoreCase = true) -> "Fujifilm"

            model.contains("Olympus", ignoreCase = true) ||
                    model.contains("OMSYSTEM", ignoreCase = true) -> "Olympus"

            model.contains("Panasonic", ignoreCase = true) ||
                    model.contains("Lumix", ignoreCase = true) -> "Panasonic"

            model.contains("Pentax", ignoreCase = true) ||
                    model.contains("Ricoh", ignoreCase = true) -> "Pentax/Ricoh"

            model.contains("Leica", ignoreCase = true) -> "Leica"
            model.contains("Sigma", ignoreCase = true) -> "Sigma"
            else -> "Unknown"
        }
    }

    /**
     * Nikon STA 인증이 필요한 제조사인지
     */
    fun needsStaAuthentication(): Boolean {
        return getManufacturer() == "Nikon"
    }
}

/**
 * 카메라가 지원하는 기능들 (파싱됨)
 */
data class CameraSupports(
    // 촬영 기능
    @SerializedName("capture_image")
    val captureImage: Boolean,      // 원격 사진 촬영
    @SerializedName("capture_video")
    val captureVideo: Boolean,      // 원격 비디오 촬영
    @SerializedName("capture_audio")
    val captureAudio: Boolean,      // 오디오 녹음
    @SerializedName("capture_preview")
    val capturePreview: Boolean,    // 라이브뷰 미리보기
    @SerializedName("trigger_capture")
    val triggerCapture: Boolean,    // 트리거 촬영 (tethered)

    // 설정 기능
    val config: Boolean,             // 카메라 설정 변경

    // 파일 작업
    val delete: Boolean,             // 파일 삭제
    val preview: Boolean,            // 파일 미리보기
    val raw: Boolean,                // RAW 파일 액세스
    val audio: Boolean,              // 오디오 파일
    val exif: Boolean,               // EXIF 정보

    // 폴더 작업
    @SerializedName("delete_all")
    val deleteAll: Boolean,          // 전체 파일 삭제
    @SerializedName("put_file")
    val putFile: Boolean,            // 파일 업로드
    @SerializedName("make_dir")
    val makeDir: Boolean,            // 디렉토리 생성
    @SerializedName("remove_dir")
    val removeDir: Boolean           // 디렉토리 삭제
) {
    /**
     * 완전한 원격 제어 가능 여부
     */
    fun isFullyControllable(): Boolean {
        return captureImage && capturePreview && config
    }

    /**
     * 제한적 지원 (다운로드만)
     */
    fun isDownloadOnly(): Boolean {
        return !captureImage && !capturePreview && !config
    }

    /**
     * 라이브뷰 가능 여부
     */
    fun hasLiveview(): Boolean {
        return capturePreview
    }
}

/**
 * PTP DeviceInfo (gp_camera_get_summary에서 파싱)
 */
data class PtpDeviceInfo(
    val manufacturer: String,
    val model: String,
    val version: String,
    @SerializedName("serial_number")
    val serialNumber: String
) {
    /**
     * 유효한 시리얼 번호인지 (0으로만 구성된 더미 아님)
     */
    fun hasValidSerialNumber(): Boolean {
        return serialNumber.isNotEmpty() &&
                !serialNumber.matches(Regex("^0+$"))
    }
}

/**
 * 카메라 연결 모드 (AP/STA)
 */
enum class WirelessConnectionMode {
    AP,      // Access Point (카메라가 AP 생성)
    STA,     // Station (공유기 경유)
    UNKNOWN
}

/**
 * 카메라 제조사 Enum
 */
enum class CameraManufacturer {
    CANON,
    NIKON,
    SONY,
    FUJIFILM,
    OLYMPUS,
    PANASONIC,
    PENTAX_RICOH,
    LEICA,
    SIGMA,
    GOPRO,
    UNKNOWN;

    companion object {
        fun from(manufacturerStr: String): CameraManufacturer {
            return when {
                manufacturerStr.contains("Canon", ignoreCase = true) -> CANON
                manufacturerStr.contains("Nikon", ignoreCase = true) -> NIKON
                manufacturerStr.contains("Sony", ignoreCase = true) -> SONY
                manufacturerStr.contains("Fuji", ignoreCase = true) -> FUJIFILM
                manufacturerStr.contains("Olympus", ignoreCase = true) ||
                        manufacturerStr.contains("OMSYSTEM", ignoreCase = true) -> OLYMPUS

                manufacturerStr.contains("Panasonic", ignoreCase = true) ||
                        manufacturerStr.contains("Lumix", ignoreCase = true) -> PANASONIC

                manufacturerStr.contains("Pentax", ignoreCase = true) ||
                        manufacturerStr.contains("Ricoh", ignoreCase = true) -> PENTAX_RICOH

                manufacturerStr.contains("Leica", ignoreCase = true) -> LEICA
                manufacturerStr.contains("Sigma", ignoreCase = true) -> SIGMA
                manufacturerStr.contains("GoPro", ignoreCase = true) -> GOPRO
                else -> UNKNOWN
            }
        }
    }

    /**
     * 한글 이름
     */
    fun getDisplayName(): String = when (this) {
        CANON -> "캐논"
        NIKON -> "니콘"
        SONY -> "소니"
        FUJIFILM -> "후지필름"
        OLYMPUS -> "올림푸스"
        PANASONIC -> "파나소닉"
        PENTAX_RICOH -> "펜탁스/리코"
        LEICA -> "라이카"
        SIGMA -> "시그마"
        GOPRO -> "고프로"
        UNKNOWN -> "알 수 없음"
    }

    /**
     * STA 모드 인증이 필요한 제조사인지
     */
    fun needsStaAuthentication(): Boolean = this == NIKON
}
