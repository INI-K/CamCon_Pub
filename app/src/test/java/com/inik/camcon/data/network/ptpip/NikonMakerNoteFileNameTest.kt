package com.inik.camcon.data.network.ptpip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * NikonMakerNoteFileName 파서 검증.
 *
 * 실물 Z6 바이트는 미보유라 "외부 일치"는 검증 불가하지만, 합성 TIFF/EXIF/MakerNote를 구성해
 * 파서의 내부 정합성(엔디안, IFD 순회, MakerNote 임베디드 base offset, FileInfo offset)을 잠근다.
 * Nikon이 FileInfo를 문서대로 기록한다는 전제(Exiv2 NikonFi / metadata-extractor) 하에 동작 보장.
 */
class NikonMakerNoteFileNameTest {

    /** 리틀엔디안(II) TIFF/NEF 구조: DateTimeOriginal + Nikon MakerNote(FileInfo FileNumber=1234). 총 122B. */
    private fun buildLittleEndianTiff(): ByteArray {
        val buf = ByteBuffer.allocate(122).order(ByteOrder.LITTLE_ENDIAN)
        // TIFF 헤더
        buf.put(0, 'I'.code.toByte()); buf.put(1, 'I'.code.toByte())
        buf.putShort(2, 0x2A); buf.putInt(4, 8)
        // IFD0 @8: ExifIFDPointer(0x8769) LONG inline = 26
        buf.putShort(8, 1)
        buf.putShort(10, 0x8769.toShort()); buf.putShort(12, 4); buf.putInt(14, 1); buf.putInt(18, 26)
        buf.putInt(22, 0)
        // Exif IFD @26: DateTimeOriginal(0x9003)@56, MakerNote(0x927C)@76
        buf.putShort(26, 2)
        buf.putShort(28, 0x9003.toShort()); buf.putShort(30, 2); buf.putInt(32, 20); buf.putInt(36, 56)
        buf.putShort(40, 0x927C.toShort()); buf.putShort(42, 7); buf.putInt(44, 46); buf.putInt(48, 76)
        buf.putInt(52, 0)
        // DateTimeOriginal 값 @56 (20B, null 종단)
        putAscii(buf, 56, "2026:06:04 11:33:12")
        // MakerNote @76: "Nikon\0" + ver(02 10) + pad(00 00)
        putAscii(buf, 76, "Nikon")
        buf.put(82, 0x02); buf.put(83, 0x10); buf.put(84, 0); buf.put(85, 0)
        // 임베디드 TIFF 헤더 @86 (base) — 내부 오프셋의 기준
        buf.put(86, 'I'.code.toByte()); buf.put(87, 'I'.code.toByte())
        buf.putShort(88, 0x2A); buf.putInt(90, 8) // mn IFD rel embBase=8 → abs 94
        // MakerNote IFD @94: FileInfo(0x00B8) UNDEFINED count=10, rel offset 26 → abs 112
        buf.putShort(94, 1)
        buf.putShort(96, 0x00B8.toShort()); buf.putShort(98, 7); buf.putInt(100, 10); buf.putInt(104, 26)
        buf.putInt(108, 0)
        // FileInfo 블록 @112: version "0100", dir@(112+6)=118, fileNumber@(112+8)=120
        putAscii(buf, 112, "0100", terminate = false)
        buf.putShort(118, 105)   // DirectoryNumber
        buf.putShort(120, 1234)  // FileNumber
        return buf.array()
    }

    private fun putAscii(buf: ByteBuffer, off: Int, s: String, terminate: Boolean = true) {
        for (i in s.indices) buf.put(off + i, s[i].code.toByte())
        if (terminate) buf.put(off + s.length, 0)
    }

    /** TIFF를 JPEG APP1 Exif 세그먼트로 감싼다: FFD8 FFE1 [len BE] "Exif\0\0" + TIFF. */
    private fun wrapInJpeg(tiff: ByteArray): ByteArray {
        val segLen = 2 + 6 + tiff.size // len 필드(2) + "Exif\0\0"(6) + TIFF
        val out = ByteArray(12 + tiff.size) // SOI(2)+APP1(2)+len(2)+"Exif\0\0"(6)+TIFF
        out[0] = 0xFF.toByte(); out[1] = 0xD8.toByte()       // SOI
        out[2] = 0xFF.toByte(); out[3] = 0xE1.toByte()       // APP1
        out[4] = ((segLen ushr 8) and 0xFF).toByte(); out[5] = (segLen and 0xFF).toByte() // BE 길이
        val exif = "Exif".toByteArray(Charsets.US_ASCII)
        System.arraycopy(exif, 0, out, 6, 4)
        out[10] = 0; out[11] = 0
        System.arraycopy(tiff, 0, out, 12, tiff.size)
        return out
    }

    @Test
    fun `리틀엔디안 NEF에서 FileNumber와 DateTimeOriginal 복원`() {
        val info = NikonMakerNoteFileName.parse(buildLittleEndianTiff())
        assertEquals(1234, info.fileNumber)
        assertEquals("2026:06:04 11:33:12", info.dateTimeOriginal)
    }

    @Test
    fun `JPEG APP1로 감싼 경우에도 동일하게 복원`() {
        val info = NikonMakerNoteFileName.parse(wrapInJpeg(buildLittleEndianTiff()))
        assertEquals(1234, info.fileNumber)
        assertEquals("2026:06:04 11:33:12", info.dateTimeOriginal)
    }

    @Test
    fun `EXIF가 없는 임의 바이트는 둘 다 null`() {
        val info = NikonMakerNoteFileName.parse(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 5, 6))
        assertNull(info.fileNumber)
        assertNull(info.dateTimeOriginal)
    }

    @Test
    fun `MakerNote가 Nikon이 아니면 FileNumber는 null, 시각은 복원`() {
        val tiff = buildLittleEndianTiff()
        tiff[76] = 'X'.code.toByte() // "Nikon" 시그니처 훼손
        val info = NikonMakerNoteFileName.parse(tiff)
        assertNull(info.fileNumber)
        assertEquals("2026:06:04 11:33:12", info.dateTimeOriginal)
    }
}
