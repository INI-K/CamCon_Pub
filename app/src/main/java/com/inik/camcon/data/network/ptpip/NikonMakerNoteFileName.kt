package com.inik.camcon.data.network.ptpip

/**
 * 수신한 Nikon JPEG/NEF 바이트에서 카메라 실제 "파일번호"와 촬영시각을 복원한다.
 *
 * STA(PC 연결) 모드는 카드 객체에 AccessLock(0x200F)을 걸어 GetObjectInfo(0x1008)로 실제 파일명을
 * 못 읽는다. 그러나 이미 vendor op(0x9421/0x9431)로 받은 원본 바이트 안에는 파일번호가 들어있다:
 *   - 파일번호: Nikon MakerNote(EXIF 0x927C, Type2) 내 FileInfo 블록(Nikon3 tag 0x00B8)의
 *     비암호화 FileNumber(블록 offset 8, u16). Z 시리즈 공통 구조(Exiv2 NikonFi / metadata-extractor).
 *   - 촬영시각: 표준 EXIF DateTimeOriginal(0x9003). MakerNote 복원 실패 시 시간순 합성용.
 *
 * 파일번호 자체는 견고하나 접두(DSC vs 사용자정의, sRGB 'DSC_' vs AdobeRGB '_DSC')는 FileInfo만으론
 * 단정 불가 → 호출부에서 기본 'DSC_'를 가정한다(한계). 모든 단계 bounds-check, 어떤 불일치든
 * null로 안전 폴백한다(회귀 위험 최소화).
 *
 * 표준 TIFF/EXIF IFD 파서(엔디안 인지). JPEG는 APP1 Exif 세그먼트에서, NEF(TIFF)는 선두에서 시작.
 */
object NikonMakerNoteFileName {

    /** 복원 결과. 둘 다 null이면 메타 복원 실패. */
    data class Info(val fileNumber: Int?, val dateTimeOriginal: String?)

    private const val TAG_EXIF_IFD = 0x8769         // ExifIFDPointer (LONG)
    private const val TAG_DATETIME_ORIGINAL = 0x9003 // DateTimeOriginal (ASCII)
    private const val TAG_MAKERNOTE = 0x927C        // MakerNote (UNDEFINED)
    private const val TAG_NIKON_FILEINFO = 0x00B8   // Nikon3 FileInfo (UNDEFINED)

    fun parse(bytes: ByteArray): Info = try {
        val tiffBase = locateTiff(bytes) ?: return Info(null, null)
        val le = when {
            match(bytes, tiffBase, 0x49, 0x49) -> true   // "II" little-endian
            match(bytes, tiffBase, 0x4D, 0x4D) -> false  // "MM" big-endian
            else -> return Info(null, null)
        }
        val ifd0 = u32(bytes, tiffBase + 4, le)
        val exifIfdRel = findExifIfdOffset(bytes, tiffBase, tiffBase + ifd0, le)
            ?: return Info(null, null)

        var dateTime: String? = null
        var makerAbs: Int? = null
        forEachEntry(bytes, tiffBase, tiffBase + exifIfdRel, le) { tag, _, count, valueAbs ->
            when (tag) {
                TAG_DATETIME_ORIGINAL -> dateTime = readAscii(bytes, valueAbs, count)
                TAG_MAKERNOTE -> makerAbs = valueAbs
            }
        }
        val fileNumber = makerAbs?.let { parseNikonFileNumber(bytes, it) }
        Info(fileNumber, dateTime?.takeIf { it.isNotBlank() })
    } catch (e: Exception) {
        Info(null, null)
    }

    /** IFD0를 훑어 ExifIFDPointer(0x8769) 값(=Exif IFD의 tiffBase 상대 오프셋)을 찾는다. */
    private fun findExifIfdOffset(bytes: ByteArray, tiffBase: Int, ifdAbs: Int, le: Boolean): Int? {
        var found: Int? = null
        forEachEntry(bytes, tiffBase, ifdAbs, le) { tag, _, _, valueAbs ->
            if (tag == TAG_EXIF_IFD && found == null) found = u32(bytes, valueAbs, le)
        }
        return found
    }

    /** Nikon Type2 MakerNote에서 FileInfo(0x00B8) 블록의 FileNumber(블록 offset 8, u16)를 추출. */
    private fun parseNikonFileNumber(bytes: ByteArray, mnAbs: Int): Int? {
        // "Nikon\0" + 버전 2B + 패딩 2B = 10바이트, 이후 임베디드 TIFF 헤더가 모든 내부 오프셋의 base.
        if (!matchAscii(bytes, mnAbs, "Nikon") || !inBounds(bytes, mnAbs + 5) || bytes[mnAbs + 5].toInt() != 0) {
            return null
        }
        val embBase = mnAbs + 10
        val le2 = when {
            match(bytes, embBase, 0x49, 0x49) -> true
            match(bytes, embBase, 0x4D, 0x4D) -> false
            else -> return null
        }
        val mnIfdRel = u32(bytes, embBase + 4, le2)
        var fileInfoAbs: Int? = null
        forEachEntry(bytes, embBase, embBase + mnIfdRel, le2) { tag, _, _, valueAbs ->
            if (tag == TAG_NIKON_FILEINFO && fileInfoAbs == null) fileInfoAbs = valueAbs
        }
        val fi = fileInfoAbs ?: return null
        // FileInfo: 0..3 Version("0100" 등), 6 DirectoryNumber(u16), 8 FileNumber(u16). 엔디안=임베디드 헤더.
        if (!inBounds(bytes, fi + 9)) return null
        return u16(bytes, fi + 8, le2)
    }

    /**
     * IFD 항목 순회. 각 항목 12B: tag(u16)·type(u16)·count(u32)·value(4B).
     * 값 크기 ≤4면 value 4B 자체가 인라인, 아니면 value=tiffBase 상대 오프셋.
     * @param base 오프셋 해석 기준(주 IFD는 tiffBase, MakerNote IFD는 임베디드 헤더 시작).
     */
    private fun forEachEntry(
        bytes: ByteArray,
        base: Int,
        ifdAbs: Int,
        le: Boolean,
        action: (tag: Int, type: Int, count: Int, valueAbs: Int) -> Unit
    ) {
        if (ifdAbs < 0 || ifdAbs + 2 > bytes.size) return
        val n = u16(bytes, ifdAbs, le)
        var p = ifdAbs + 2
        var i = 0
        while (i < n && p + 12 <= bytes.size) {
            val tag = u16(bytes, p, le)
            val type = u16(bytes, p + 2, le)
            val count = u32(bytes, p + 4, le)
            val size = typeSize(type) * count
            val valueAbs = if (size in 1..4) p + 8 else base + u32(bytes, p + 8, le)
            if (valueAbs in 0 until bytes.size) action(tag, type, count, valueAbs)
            p += 12
            i++
        }
    }

    private fun typeSize(type: Int): Int = when (type) {
        1, 2, 7 -> 1   // BYTE / ASCII / UNDEFINED
        3 -> 2         // SHORT
        4 -> 4         // LONG
        5, 10 -> 8     // RATIONAL / SRATIONAL
        else -> 1
    }

    private fun readAscii(bytes: ByteArray, pos: Int, count: Int): String? {
        if (count <= 0 || pos < 0 || pos + count > bytes.size) return null
        var end = pos
        val limit = pos + count
        while (end < limit && bytes[end].toInt() != 0) end++
        return if (end > pos) String(bytes, pos, end - pos, Charsets.US_ASCII) else null
    }

    /** JPEG면 APP1 Exif의 TIFF 헤더 시작 오프셋, NEF/TIFF면 0. 못 찾으면 null. */
    private fun locateTiff(bytes: ByteArray): Int? {
        if (bytes.size < 8) return null
        // NEF/TIFF: "II"+0x2A00 또는 "MM"+0x002A
        if ((match(bytes, 0, 0x49, 0x49) && (bytes[2].toInt() and 0xFF) == 0x2A && bytes[3].toInt() == 0) ||
            (match(bytes, 0, 0x4D, 0x4D) && bytes[2].toInt() == 0 && (bytes[3].toInt() and 0xFF) == 0x2A)
        ) return 0
        // JPEG: 0xFFD8 후 마커 스캔으로 APP1("Exif\0\0") 탐색
        if ((bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) return null
        var p = 2
        while (p + 4 <= bytes.size) {
            if ((bytes[p].toInt() and 0xFF) != 0xFF) return null
            val marker = bytes[p + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) return null // SOS/EOI 이후엔 EXIF 없음
            if (marker in 0xD0..0xD7) { p += 2; continue }    // RSTn: 길이 없음
            val segLen = ((bytes[p + 2].toInt() and 0xFF) shl 8) or (bytes[p + 3].toInt() and 0xFF)
            if (segLen < 2) return null
            if (marker == 0xE1) {
                val ex = p + 4
                if (ex + 6 <= bytes.size && matchAscii(bytes, ex, "Exif") &&
                    bytes[ex + 4].toInt() == 0 && bytes[ex + 5].toInt() == 0
                ) return ex + 6
            }
            p += 2 + segLen
        }
        return null
    }

    private fun inBounds(bytes: ByteArray, idx: Int): Boolean = idx in 0 until bytes.size

    private fun match(bytes: ByteArray, off: Int, b0: Int, b1: Int): Boolean =
        off >= 0 && off + 1 < bytes.size &&
            (bytes[off].toInt() and 0xFF) == b0 && (bytes[off + 1].toInt() and 0xFF) == b1

    private fun matchAscii(bytes: ByteArray, off: Int, s: String): Boolean {
        if (off < 0 || off + s.length > bytes.size) return false
        for (i in s.indices) if ((bytes[off + i].toInt() and 0xFF) != s[i].code) return false
        return true
    }

    private fun u16(b: ByteArray, off: Int, le: Boolean): Int {
        val a = b[off].toInt() and 0xFF
        val c = b[off + 1].toInt() and 0xFF
        return if (le) a or (c shl 8) else (a shl 8) or c
    }

    private fun u32(b: ByteArray, off: Int, le: Boolean): Int {
        val a = b[off].toInt() and 0xFF
        val c = b[off + 1].toInt() and 0xFF
        val d = b[off + 2].toInt() and 0xFF
        val e = b[off + 3].toInt() and 0xFF
        return if (le) a or (c shl 8) or (d shl 16) or (e shl 24)
        else (a shl 24) or (c shl 16) or (d shl 8) or e
    }
}
