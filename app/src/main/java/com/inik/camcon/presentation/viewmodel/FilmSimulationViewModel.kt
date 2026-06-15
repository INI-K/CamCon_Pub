package com.inik.camcon.presentation.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inik.camcon.domain.model.FilmLut
import com.inik.camcon.domain.usecase.FilmLutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 필름 시뮬레이션 상세 화면 전용 ViewModel.
 *
 * 실시간 미리보기(targetBitmap + lookupBitmap)와 갤러리 내보내기를 담당한다.
 * 선택된 LUT 적용/저장 등 무거운 로직은 [FilmLutUseCase] 에 위임한다.
 *
 * - targetBitmap: 사용자가 고른 사진을 다운스케일해 디코딩한 원본 비트맵(소유 → 회수 대상).
 * - lookupBitmap: UseCase 가 반환하는 512x512 룩업 비트맵(캐시 소유 → 회수 금지).
 */
@HiltViewModel
class FilmSimulationViewModel @Inject constructor(
    private val filmLutUseCase: FilmLutUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "FilmSimulationViewModel"
        private const val MAX_PREVIEW_EDGE = 1280
        const val MESSAGE_OK = "ok"
        const val MESSAGE_FAIL = "fail"
    }

    private val _availableLuts = MutableStateFlow<List<FilmLut>>(emptyList())
    val availableLuts: StateFlow<List<FilmLut>> = _availableLuts.asStateFlow()

    private val _targetBitmap = MutableStateFlow<Bitmap?>(null)
    val targetBitmap: StateFlow<Bitmap?> = _targetBitmap.asStateFlow()

    // 내보내기에 사용할 대상 이미지의 실제 파일 경로. targetBitmap 과 동일 생명주기로 ViewModel 이
    // 소유한다(Compose 로컬 상태로 두면 회전·재생성 후 비트맵은 살아 있는데 경로만 null 이 되어
    // 내보내기 버튼이 활성인데 동작하지 않는 불일치가 생긴다).
    private val _targetPath = MutableStateFlow<String?>(null)
    val targetPath: StateFlow<String?> = _targetPath.asStateFlow()

    private val _lookupBitmap = MutableStateFlow<Bitmap?>(null)
    val lookupBitmap: StateFlow<Bitmap?> = _lookupBitmap.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _availableLuts.value = filmLutUseCase.getAvailableLuts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "LUT 목록 로드 실패", e)
            }
        }
    }

    /**
     * 선택된 LUT 의 512x512 룩업 비트맵을 로드한다.
     * lutId 가 비어 있으면 룩업을 비워 원본(필터 없음) 미리보기를 보여준다.
     */
    fun loadLookup(lutId: String) {
        viewModelScope.launch {
            try {
                _lookupBitmap.value = if (lutId.isBlank()) {
                    null
                } else {
                    filmLutUseCase.loadLookupBitmap(lutId) as? Bitmap
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "룩업 비트맵 로드 실패", e)
                _lookupBitmap.value = null
            }
        }
    }

    /**
     * 미리보기 대상 이미지를 다운스케일 디코딩해 보관한다(긴 변 <= 1280px, ARGB_8888).
     * 이전에 소유하던 비트맵은 회수한다.
     */
    fun setTargetImage(path: String) {
        _targetPath.value = path
        viewModelScope.launch(Dispatchers.Default) {
            val decoded = decodeDownscaled(path)
            val previous = _targetBitmap.value
            _targetBitmap.value = decoded
            if (previous != null && previous != decoded && !previous.isRecycled) {
                previous.recycle()
            }
        }
    }

    private fun decodeDownscaled(path: String): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            if (longEdge > MAX_PREVIEW_EDGE) {
                while (longEdge / (sample * 2) >= MAX_PREVIEW_EDGE) {
                    sample *= 2
                }
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            Log.e(TAG, "대상 이미지 디코딩 실패", e)
            null
        }
    }

    /**
     * 선택된 LUT 를 [targetPath] 원본에 적용해 갤러리(Pictures/CamCon)에 저장한다.
     * MediaStore 실패 시 앱 외부 Pictures 디렉터리로 폴백한다.
     */
    fun export(targetPath: String, lutId: String, intensity: Float) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val tmp = filmLutUseCase.applyFilmLut(targetPath, lutId, intensity, 0)
                if (tmp == null) {
                    _message.value = MESSAGE_FAIL
                    return@launch
                }
                val tmpFile = File(tmp)
                val saved = withContext(Dispatchers.IO) {
                    saveToGallery(tmpFile)
                }
                _message.value = if (saved) MESSAGE_OK else MESSAGE_FAIL
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "필름 LUT 내보내기 실패", e)
                _message.value = MESSAGE_FAIL
            } finally {
                _isExporting.value = false
            }
        }
    }

    private fun saveToGallery(source: File): Boolean {
        val displayName = "camcon_film_${System.currentTimeMillis()}.jpg"
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/CamCon")
                // IS_PENDING 으로 기록 완료 전까지 갤러리/타 앱 노출·고아 엔트리를 막는다(repo 공통 패턴).
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                saveToExternalFiles(source)
            } else {
                val written = resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input ->
                        input.copyTo(output)
                    }
                    true
                } ?: false
                if (!written) {
                    // 스트림 열기 실패 → 보류 중 엔트리 제거 후 외부 파일 폴백
                    resolver.delete(uri, null, null)
                    return saveToExternalFiles(source)
                }
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null
                )
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore 저장 실패, 외부 파일 폴백", e)
            saveToExternalFiles(source)
        }
    }

    private fun saveToExternalFiles(source: File): Boolean {
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return false
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val target = File(dir, "camcon_film_${System.currentTimeMillis()}.jpg")
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "외부 파일 저장 실패", e)
            false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // targetBitmap 만 소유 비트맵이므로 회수한다. lookupBitmap 은 UseCase 캐시 소유 → 회수 금지.
        _targetBitmap.value?.let { if (!it.isRecycled) it.recycle() }
        _targetBitmap.value = null
        _targetPath.value = null
    }
}
