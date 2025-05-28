package com.inik.camcon.domain.model

// 카메라 설정 모델
data class CameraSettings(
    val iso: String,
    val shutterSpeed: String,
    val aperture: String,
    val whiteBalance: String,
    val focusMode: String,
    val exposureCompensation: String
)

// 촬영 모드
enum class ShootingMode {
    SINGLE,
    BURST,
    TIMELAPSE,
    BULB,
    HDR_BRACKET
}

// 타임랩스 설정
data class TimelapseSettings(
    val interval: Int, // seconds
    val totalShots: Int,
    val duration: Int // minutes
)

// 브라켓팅 설정
data class BracketingSettings(
    val shots: Int,
    val evStep: Float // EV step size
)

// 라이브뷰 프레임
data class LiveViewFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LiveViewFrame

        if (!data.contentEquals(other.data)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

// 촬영된 사진 정보
data class CapturedPhoto(
    val id: String,
    val filePath: String,
    val thumbnailPath: String?,
    val captureTime: Long,
    val cameraModel: String,
    val settings: CameraSettings?,
    val size: Long,
    val width: Int,
    val height: Int
)