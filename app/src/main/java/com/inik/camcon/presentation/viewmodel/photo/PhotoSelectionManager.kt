package com.inik.camcon.presentation.viewmodel.photo

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사진 선택 및 멀티 선택 모드 관리 전용 매니저
 * 단일책임: 사진 선택 상태 관리만 담당
 */
@Singleton
class PhotoSelectionManager @Inject constructor() {

    companion object {
        private const val TAG = "사진선택매니저"
    }

    // 멀티 선택 모드 상태
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    // 선택된 사진들의 경로 집합
    private val _selectedPhotos = MutableStateFlow<Set<String>>(emptySet())
    val selectedPhotos: StateFlow<Set<String>> = _selectedPhotos.asStateFlow()

    /**
     * 멀티 선택 모드 시작
     * 일반적으로 사진을 롱클릭했을 때 호출됩니다.
     */
    fun startMultiSelectMode(initialPhotoPath: String) {
        Log.d(TAG, "멀티 선택 모드 시작: $initialPhotoPath")
        _isMultiSelectMode.value = true
        _selectedPhotos.value = setOf(initialPhotoPath)
    }

    /**
     * 멀티 선택 모드 종료
     */
    fun exitMultiSelectMode() {
        Log.d(TAG, "멀티 선택 모드 종료")
        _isMultiSelectMode.value = false
        _selectedPhotos.value = emptySet()
    }

    /**
     * 사진을 선택/해제 토글
     * 멀티 선택 모드에서 사진을 클릭했을 때 호출됩니다.
     */
    fun togglePhotoSelection(photoPath: String) {
        val currentSelection = _selectedPhotos.value
        val newSelection = if (currentSelection.contains(photoPath)) {
            currentSelection - photoPath
        } else {
            currentSelection + photoPath
        }

        Log.d(TAG, "사진 선택 토글: $photoPath, 선택된 사진 수: ${newSelection.size}")

        // 선택된 사진이 하나도 없으면 멀티 선택 모드를 종료
        if (newSelection.isEmpty()) {
            exitMultiSelectMode()
        } else {
            _selectedPhotos.value = newSelection
        }
    }

    /**
     * 특정 사진이 선택되어 있는지 확인
     */
    fun isPhotoSelected(photoPath: String): Boolean {
        return _selectedPhotos.value.contains(photoPath)
    }

    /**
     * 모든 사진을 선택
     */
    fun selectAllPhotos(allPhotoPaths: List<String>) {
        val allPhotoPathsSet = allPhotoPaths.toSet()
        Log.d(TAG, "모든 사진 선택: ${allPhotoPathsSet.size}개")
        _selectedPhotos.value = allPhotoPathsSet

        // 선택할 사진이 있으면 멀티 선택 모드도 활성화
        if (allPhotoPathsSet.isNotEmpty()) {
            _isMultiSelectMode.value = true
        }
    }

    /**
     * 모든 사진 선택 해제
     */
    fun deselectAllPhotos() {
        Log.d(TAG, "모든 사진 선택 해제")
        _selectedPhotos.value = emptySet()
    }

    /**
     * 선택된 사진 수 반환
     */
    fun getSelectedCount(): Int {
        return _selectedPhotos.value.size
    }

    /**
     * 선택된 사진 경로들 반환
     */
    fun getSelectedPaths(): Set<String> {
        return _selectedPhotos.value
    }

    /**
     * 특정 사진들을 선택 상태로 설정 (외부에서 직접 설정할 때 사용)
     */
    fun setSelectedPhotos(photoPaths: Set<String>) {
        Log.d(TAG, "선택된 사진 직접 설정: ${photoPaths.size}개")
        _selectedPhotos.value = photoPaths

        // 선택된 사진이 있으면 멀티 선택 모드 활성화, 없으면 비활성화
        _isMultiSelectMode.value = photoPaths.isNotEmpty()
    }

    /**
     * 선택 상태 초기화
     */
    fun clearSelection() {
        Log.d(TAG, "선택 상태 초기화")
        _isMultiSelectMode.value = false
        _selectedPhotos.value = emptySet()
    }

    /**
     * 현재 선택 상태 정보 로깅 (디버깅용)
     */
    fun logCurrentState() {
        Log.d(
            TAG, """
            현재 선택 상태:
            - 멀티 선택 모드: ${_isMultiSelectMode.value}
            - 선택된 사진 수: ${_selectedPhotos.value.size}
            - 선택된 사진들: ${_selectedPhotos.value.joinToString { it.substringAfterLast("/") }}
        """.trimIndent()
        )
    }
}