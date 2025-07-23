package com.inik.camcon.presentation.ui.screens.components

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 사진 정보 다이얼로그 관련 유틸리티
 * 사진의 메타데이터와 EXIF 정보를 표시하는 다이얼로그를 제공합니다.
 */
object PhotoInfoDialog {

    /**
     * 사진 정보 다이얼로그를 표시하는 함수
     */
    fun showPhotoInfoDialog(
        context: Context,
        photo: CameraPhoto,
        viewModel: PhotoPreviewViewModel?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // EXIF 정보 가져오기
                val exifInfo = viewModel?.getCameraPhotoExif(photo.path)

                withContext(Dispatchers.Main) {
                    val dialogView = LayoutInflater.from(context)
                        .inflate(R.layout.dialog_photo_info, null)

                    // 기본 정보 설정
                    setupBasicInfo(dialogView, photo)

                    // EXIF 정보 파싱 및 표시
                    val exifContainer = dialogView.findViewById<LinearLayout>(R.id.exif_container)
                    setupExifInfo(exifInfo, exifContainer, context)

                    AlertDialog.Builder(context)
                        .setTitle("사진 정보")
                        .setView(dialogView)
                        .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            } catch (e: Exception) {
                Log.e("PhotoInfo", "사진 정보 로드 오류", e)
                withContext(Dispatchers.Main) {
                    showErrorDialog(context)
                }
            }
        }
    }

    /**
     * 기본 사진 정보 설정
     */
    private fun setupBasicInfo(dialogView: android.view.View, photo: CameraPhoto) {
        dialogView.findViewById<TextView>(R.id.tv_photo_name).text = photo.name
        dialogView.findViewById<TextView>(R.id.tv_photo_path).text = photo.path
        dialogView.findViewById<TextView>(R.id.tv_photo_size).text =
            "${photo.size} bytes (${String.format("%.2f MB", photo.size / 1024.0 / 1024.0)})"

        // 수정 시간 포맷팅
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = try {
            dateFormat.format(Date(photo.date * 1000L))
        } catch (e: Exception) {
            "알 수 없음"
        }
        dialogView.findViewById<TextView>(R.id.tv_photo_date).text = formattedDate
    }

    /**
     * EXIF 정보 설정
     */
    private fun setupExifInfo(
        exifInfo: String?,
        exifContainer: LinearLayout,
        context: Context
    ) {
        if (!exifInfo.isNullOrEmpty() && exifInfo != "{}") {
            try {
                // 간단한 JSON 파싱 (org.json 사용 없이)
                parseAndDisplayExif(exifInfo, exifContainer, context)
            } catch (e: Exception) {
                Log.e("PhotoInfo", "EXIF 파싱 오류", e)
                addExifItem(exifContainer, context, "EXIF 정보", "파싱 오류")
            }
        } else {
            addExifItem(exifContainer, context, "EXIF 정보", "없음")
        }
    }

    /**
     * EXIF 정보를 파싱하여 표시하는 함수
     */
    private fun parseAndDisplayExif(
        exifJson: String,
        container: LinearLayout,
        context: Context
    ) {
        // 간단한 JSON 파싱
        val entries = mutableMapOf<String, String>()

        val cleanJson = exifJson.trim().removePrefix("{").removeSuffix("}")
        if (cleanJson.isNotEmpty()) {
            val pairs = cleanJson.split(",")
            for (pair in pairs) {
                val keyValue = pair.split(":")
                if (keyValue.size == 2) {
                    val key = keyValue[0].trim().removeSurrounding("\"")
                    val value = keyValue[1].trim().removeSurrounding("\"")
                    entries[key] = value
                }
            }
        }

        if (entries.isNotEmpty()) {
            displayExifEntries(entries, container, context)
        } else {
            addExifItem(container, context, "EXIF 정보", "없음")
        }
    }

    /**
     * EXIF 항목들을 표시
     */
    private fun displayExifEntries(
        entries: Map<String, String>,
        container: LinearLayout,
        context: Context
    ) {
        entries["width"]?.let { width ->
            addExifItem(container, context, "너비", "${width}px")
        }

        entries["height"]?.let { height ->
            addExifItem(container, context, "높이", "${height}px")
        }

        entries["orientation"]?.let { orientation ->
            val orientationText = getOrientationText(orientation)
            addExifItem(container, context, "방향", orientationText)
        }

        // 추가적인 EXIF 정보들
        entries["make"]?.let { make ->
            addExifItem(container, context, "제조사", make)
        }

        entries["model"]?.let { model ->
            addExifItem(container, context, "모델", model)
        }

        entries["iso"]?.let { iso ->
            addExifItem(container, context, "ISO", iso)
        }

        entries["exposure_time"]?.let { exposureTime ->
            addExifItem(container, context, "노출시간", exposureTime)
        }

        entries["f_number"]?.let { fNumber ->
            addExifItem(container, context, "조리개", "f/$fNumber")
        }

        entries["focal_length"]?.let { focalLength ->
            addExifItem(container, context, "초점거리", "${focalLength}mm")
        }

        entries["datetime"]?.let { datetime ->
            addExifItem(container, context, "촬영일시", datetime)
        }
    }

    /**
     * 방향 정보를 텍스트로 변환
     */
    private fun getOrientationText(orientation: String): String {
        return when (orientation) {
            "1" -> "정상 (0°)"
            "3" -> "180° 회전"
            "6" -> "시계방향 90° 회전"
            "8" -> "반시계방향 90° 회전"
            else -> "알 수 없음 ($orientation)"
        }
    }

    /**
     * EXIF 항목을 추가하는 헬퍼 함수
     */
    private fun addExifItem(
        container: LinearLayout,
        context: Context,
        label: String,
        value: String
    ) {
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.item_photo_info, container, false)

        itemView.findViewById<TextView>(R.id.tv_info_label).text = label
        itemView.findViewById<TextView>(R.id.tv_info_value).text = value

        container.addView(itemView)
    }

    /**
     * 오류 다이얼로그 표시
     */
    private fun showErrorDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("오류")
            .setMessage("사진 정보를 불러올 수 없습니다.")
            .setPositiveButton("확인") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}