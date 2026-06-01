package com.inik.camcon.domain.model.file

/** 배치 썸네일 결과 1건. `data == null`이면 실패 / 미지원. */
data class CameraThumbnailResult(val path: String, val data: ByteArray?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CameraThumbnailResult) return false
        if (path != other.path) return false
        if (data == null) return other.data == null
        if (other.data == null) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}
