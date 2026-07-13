package com.inik.camcon.presentation.viewmodel.photo

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.inik.camcon.di.IoDispatcher
import com.inik.camcon.utils.Constants
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 갤러리(미리보기 탭) 다운로드 결과를 기기 저장소(MediaStore, `DCIM/CamCon/<카메라서브폴더>`)에
 * 영속화하는 저장 포트.
 *
 * minSdk29(스코프드 스토리지 강제)이므로 MediaStore `RELATIVE_PATH` 로만 저장한다. 저장 위치·폴더
 * 규약은 테더링 수신 경로([com.inik.camcon.data.repository.managers.PhotoDownloadManager])와
 * 동일하게 맞춘다.
 *
 * NOTE(아키텍처 부채): MediaStore 접근은 본래 data 레이어 소관이다. 파일 소유권 제약으로 이번에는
 * presentation 에 최소 중복 구현으로 두었다. 후속으로 도메인 UseCase → `PhotoDownloadManager`
 * (`handleNativePhotoDownload`, 게이팅·FREE 리사이즈·MediaStore 저장을 이미 수행) 재사용으로
 * 승격해 중복과 레이어 위반을 제거할 것을 권장한다. 이 포트는 이름 충돌 리네임 감지·SD카드 볼륨
 * 선택 같은 [PhotoDownloadManager] 의 고급 처리는 포함하지 않는다(기본 저장만).
 */
@Singleton
class GalleryDownloadStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "갤러리다운로드저장"
    }

    /**
     * [imageData] 를 [cameraPath] 의 파일명·서브폴더 규약으로 `DCIM/CamCon` 에 저장한다.
     *
     * @param cameraPath 카메라 내부 경로(예: `/store_00010001/DCIM/105NIKON/DSC_0001.JPG`).
     * @param imageData  저장할 바이트(FREE 티어는 이미 2000px 로 리사이즈된 바이트가 전달된다).
     * @return 저장 성공 시 상대 경로 문자열, 실패 시 null.
     */
    suspend fun save(cameraPath: String, imageData: ByteArray): String? = withContext(ioDispatcher) {
        try {
            val baseName = sanitizeFileName(cameraPath) ?: run {
                Log.w(TAG, "유효하지 않은 카메라 파일명 — 저장 중단: ${LogMask.path(cameraPath)}")
                return@withContext null
            }
            val subFolder = extractCameraSubFolder(cameraPath)
            val relativeBase = Constants.FilePaths.getMediaStoreRelativePath()
            val relativePath =
                if (subFolder.isNotEmpty()) "$relativeBase/$subFolder" else relativeBase

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, baseName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeTypeFor(baseName))
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: run {
                    Log.e(TAG, "MediaStore URI 생성 실패: $baseName")
                    return@withContext null
                }

            val written = resolver.openOutputStream(uri)?.use { out ->
                out.write(imageData)
                true
            } ?: false

            if (!written) {
                Log.e(TAG, "MediaStore 스트림 오픈 실패 — row 롤백: $baseName")
                runCatching { resolver.delete(uri, null, null) }
                return@withContext null
            }

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )

            val savedPath = "$relativePath/$baseName"
            Log.d(TAG, "MediaStore 저장 성공: ${LogMask.path(savedPath)} (${imageData.size} bytes)")
            savedPath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore 저장 실패: ${LogMask.path(cameraPath)}", e)
            null
        }
    }

    /**
     * 카메라 보고 경로에서 저장에 안전한 basename 만 취한다(경로 traversal·제어문자 방어).
     */
    private fun sanitizeFileName(cameraPath: String): String? {
        val base = File(cameraPath).name
        if (base.isBlank() || base == "." || base == "..") return null
        if (base.any { it.isISOControl() }) return null
        return base
    }

    /**
     * 카메라 내부 경로에서 `DCIM` 다음 폴더를 서브폴더로 추출한다.
     * 예: `/store_00010001/DCIM/105NIKON/DSC_0001.JPG` → `105NIKON`.
     * `DCIM` 바로 뒤가 파일명뿐이면(서브폴더 없음) 빈 문자열.
     */
    private fun extractCameraSubFolder(cameraPath: String): String {
        val parts = cameraPath.split("/")
        val dcimIndex = parts.indexOfFirst { it.equals(Constants.FilePaths.DCIM_BASE_DIR, ignoreCase = true) }
        return if (dcimIndex >= 0 && dcimIndex + 1 < parts.size - 1) parts[dcimIndex + 1] else ""
    }

    private fun mimeTypeFor(fileName: String): String {
        return when (fileName.substringAfterLast(".", "").lowercase()) {
            in Constants.ImageProcessing.JPEG_EXTENSIONS -> Constants.MimeTypes.IMAGE_JPEG
            "png" -> Constants.MimeTypes.IMAGE_PNG
            "nef" -> Constants.MimeTypes.IMAGE_NEF
            "cr2" -> Constants.MimeTypes.IMAGE_CR2
            "arw" -> Constants.MimeTypes.IMAGE_ARW
            "dng" -> Constants.MimeTypes.IMAGE_DNG
            "orf" -> Constants.MimeTypes.IMAGE_ORF
            "rw2" -> Constants.MimeTypes.IMAGE_RW2
            "raf" -> Constants.MimeTypes.IMAGE_RAF
            else -> Constants.MimeTypes.IMAGE_JPEG
        }
    }
}
