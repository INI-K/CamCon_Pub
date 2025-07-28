package com.inik.camcon.presentation.ui.screens.components

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import com.inik.camcon.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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

                    // 버튼 리스너 설정
                    setupActionButtons(dialogView, context, photo, viewModel)

                    AlertDialog.Builder(context)
                        .setTitle(Constants.UI.PHOTO_INFO_DIALOG_TITLE)
                        .setView(dialogView)
                        .setPositiveButton(Constants.UI.DIALOG_POSITIVE_BUTTON_TEXT) { dialog, _ -> dialog.dismiss() }
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
            Constants.UI.UNKNOWN_VALUE
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
                addExifItem(
                    exifContainer,
                    context,
                    Constants.UI.EXIF_INFO_LABEL,
                    Constants.UI.PARSING_ERROR
                )
            }
        } else {
            addExifItem(
                exifContainer,
                context,
                Constants.UI.EXIF_INFO_LABEL,
                Constants.UI.NO_EXIF_INFO
            )
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
            addExifItem(container, context, Constants.UI.EXIF_INFO_LABEL, Constants.UI.NO_EXIF_INFO)
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
        // 카메라 정보 우선 표시
        entries["make"]?.let { make ->
            addExifItem(container, context, Constants.UI.MAKE_LABEL, make)
        }

        entries["model"]?.let { model ->
            addExifItem(container, context, Constants.UI.MODEL_LABEL, model)
        }

        // 촬영 설정 정보 (주요 카메라 설정)
        entries["iso"]?.let { iso ->
            val isoValue = try {
                val isoNumber = iso.toIntOrNull()
                if (isoNumber != null) "ISO $isoNumber" else "ISO $iso"
            } catch (e: Exception) {
                "ISO $iso"
            }
            addExifItem(container, context, Constants.UI.ISO_LABEL, isoValue)
        }

        entries["exposure_time"]?.let { exposureTime ->
            val shutterSpeed = formatShutterSpeed(exposureTime)
            addExifItem(container, context, "셔터스피드", shutterSpeed)
        }

        entries["f_number"]?.let { fNumber ->
            val aperture = formatAperture(fNumber)
            addExifItem(container, context, "조리개", aperture)
        }

        entries["focal_length"]?.let { focalLength ->
            val formattedFocalLength = formatFocalLength(focalLength)
            addExifItem(container, context, "초점거리", formattedFocalLength)
        }

        // 이미지 크기 정보
        val width = entries["width"]
        val height = entries["height"]
        if (width != null && height != null) {
            addExifItem(container, context, "해상도", "${width} × ${height}px")
        } else {
            width?.let { w -> addExifItem(container, context, Constants.UI.WIDTH_LABEL, "${w}px") }
            height?.let { h ->
                addExifItem(
                    container,
                    context,
                    Constants.UI.HEIGHT_LABEL,
                    "${h}px"
                )
            }
        }

        // 방향 정보
        entries["orientation"]?.let { orientation ->
            val orientationText = getOrientationText(orientation)
            addExifItem(container, context, Constants.UI.ORIENTATION_LABEL, orientationText)
        }

        // 촬영 일시
        entries["datetime"]?.let { datetime ->
            val formattedDateTime = formatDateTime(datetime)
            addExifItem(container, context, "촬영일시", formattedDateTime)
        }

        // 추가 EXIF 정보들
        entries["white_balance"]?.let { whiteBalance ->
            addExifItem(container, context, "화이트밸런스", formatWhiteBalance(whiteBalance))
        }

        entries["flash"]?.let { flash ->
            addExifItem(container, context, "플래시", formatFlash(flash))
        }

        entries["metering_mode"]?.let { meteringMode ->
            addExifItem(container, context, "측광모드", formatMeteringMode(meteringMode))
        }

        entries["exposure_mode"]?.let { exposureMode ->
            addExifItem(container, context, "노출모드", formatExposureMode(exposureMode))
        }

        entries["scene_type"]?.let { sceneType ->
            addExifItem(container, context, "장면모드", sceneType)
        }
    }

    /**
     * 셔터 스피드를 읽기 쉬운 형태로 포맷팅
     */
    private fun formatShutterSpeed(exposureTime: String): String {
        return try {
            val time = exposureTime.toDoubleOrNull()
            when {
                time == null -> exposureTime
                time >= 1.0 -> "${time.toInt()}초"
                time > 0 -> {
                    val fraction = 1.0 / time
                    if (fraction > 1000) {
                        "1/${fraction.toInt()}"
                    } else {
                        "1/${String.format("%.0f", fraction)}"
                    }
                }

                else -> exposureTime
            }
        } catch (e: Exception) {
            exposureTime
        }
    }

    /**
     * 조리개 값을 포맷팅
     */
    private fun formatAperture(fNumber: String): String {
        return try {
            val aperture = fNumber.toDoubleOrNull()
            if (aperture != null) {
                "f/${String.format("%.1f", aperture)}"
            } else {
                "f/$fNumber"
            }
        } catch (e: Exception) {
            "f/$fNumber"
        }
    }

    /**
     * 초점거리를 포맷팅
     */
    private fun formatFocalLength(focalLength: String): String {
        return try {
            val focal = focalLength.toDoubleOrNull()
            if (focal != null) {
                "${String.format("%.0f", focal)}mm"
            } else {
                "${focalLength}mm"
            }
        } catch (e: Exception) {
            "${focalLength}mm"
        }
    }

    /**
     * 날짜시간을 포맷팅
     */
    private fun formatDateTime(datetime: String): String {
        return try {
            // EXIF 날짜 형식 처리: "YYYY:MM:DD HH:MM:SS"
            if (datetime.contains(":") && datetime.length >= 19) {
                val parts = datetime.split(" ")
                if (parts.size >= 2) {
                    val datePart = parts[0].replace(":", "-")
                    val timePart = parts[1]
                    "$datePart $timePart"
                } else {
                    datetime.replace(":", "-")
                }
            } else {
                datetime
            }
        } catch (e: Exception) {
            datetime
        }
    }

    /**
     * 화이트밸런스 값을 포맷팅
     */
    private fun formatWhiteBalance(whiteBalance: String): String {
        return when (whiteBalance) {
            "0" -> "자동"
            "1" -> "수동"
            else -> whiteBalance
        }
    }

    /**
     * 플래시 정보를 포맷팅
     */
    private fun formatFlash(flash: String): String {
        return try {
            val flashValue = flash.toIntOrNull() ?: return flash
            when {
                flashValue and 0x01 == 0 -> "플래시 없음"
                flashValue and 0x01 == 1 -> "플래시 사용"
                else -> flash
            }
        } catch (e: Exception) {
            flash
        }
    }

    /**
     * 측광모드를 포맷팅
     */
    private fun formatMeteringMode(meteringMode: String): String {
        return when (meteringMode) {
            "0" -> "알 수 없음"
            "1" -> "평균"
            "2" -> "중앙중점"
            "3" -> "스팟"
            "4" -> "멀티스팟"
            "5" -> "패턴"
            "6" -> "부분"
            else -> meteringMode
        }
    }

    /**
     * 노출모드를 포맷팅
     */
    private fun formatExposureMode(exposureMode: String): String {
        return when (exposureMode) {
            "0" -> "자동노출"
            "1" -> "수동노출"
            "2" -> "자동브라케팅"
            else -> exposureMode
        }
    }

    /**
     * 방향 정보를 텍스트로 변환
     */
    private fun getOrientationText(orientation: String): String {
        return when (orientation) {
            "1" -> Constants.UI.ORIENTATION_NORMAL
            "3" -> Constants.UI.ORIENTATION_ROTATED_180
            "6" -> Constants.UI.ORIENTATION_ROTATED_90_CLOCKWISE
            "8" -> Constants.UI.ORIENTATION_ROTATED_90_COUNTERCLOCKWISE
            else -> Constants.UI.UNKNOWN_VALUE
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
     * 액션 버튼들 설정
     */
    private fun setupActionButtons(
        dialogView: android.view.View,
        context: Context,
        photo: CameraPhoto,
        viewModel: PhotoPreviewViewModel?
    ) {
        val downloadButton = dialogView.findViewById<Button>(R.id.btn_download)
        val shareButton = dialogView.findViewById<Button>(R.id.btn_share)

        // 다운로드 버튼 클릭 리스너
        downloadButton.setOnClickListener {
            downloadPhoto(context, photo, viewModel)
        }

        // 공유 버튼 클릭 리스너
        shareButton.setOnClickListener {
            sharePhoto(context, photo, viewModel)
        }
    }

    /**
     * 사진 다운로드 기능
     */
    private fun downloadPhoto(
        context: Context,
        photo: CameraPhoto,
        viewModel: PhotoPreviewViewModel?
    ) {
        // ViewModel의 네이티브 다운로드 기능 사용
        viewModel?.downloadPhoto(photo)

        Toast.makeText(
            context,
            Constants.Messages.DOWNLOAD_STARTED_MESSAGE + " ${photo.name}",
            Toast.LENGTH_SHORT
        ).show()

        Log.d("PhotoInfo", Constants.Messages.DOWNLOAD_STARTED_LOG + " ${photo.path}")
    }

    /**
     * 사진 공유 기능
     */
    private fun sharePhoto(
        context: Context,
        photo: CameraPhoto,
        viewModel: PhotoPreviewViewModel?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 고화질 이미지 데이터 가져오기 (썸네일보다 우선)
                val fullImageData = viewModel?.fullImageCache?.value?.get(photo.path)
                val imageData = fullImageData ?: viewModel?.getThumbnail(photo.path)

                if (imageData != null) {
                    // 임시 파일 생성
                    val cacheDir = File(context.cacheDir, Constants.FilePaths.SHARED_PHOTOS_DIR)
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs()
                    }

                    val tempFile = File(cacheDir, "share_${photo.name}")
                    FileOutputStream(tempFile).use { fos ->
                        fos.write(imageData)
                    }

                    withContext(Dispatchers.Main) {
                        try {
                            // FileProvider를 사용하여 URI 생성
                            val fileUri = FileProvider.getUriForFile(
                                context,
                                Constants.FileProvider.getAuthority(context.packageName),
                                tempFile
                            )

                            // 공유 인텐트 생성
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = Constants.MimeTypes.IMAGE_WILDCARD
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            val chooser =
                                Intent.createChooser(
                                    shareIntent,
                                    Constants.Messages.SHARE_PHOTO_TITLE
                                )
                            context.startActivity(chooser)

                            Log.d(
                                "PhotoInfo",
                                Constants.Messages.SHARE_INTENT_STARTED_LOG + " ${tempFile.name}"
                            )
                        } catch (e: Exception) {
                            Log.e("PhotoInfo", Constants.Messages.SHARE_INTENT_ERROR_LOG, e)
                            Toast.makeText(
                                context,
                                Constants.Messages.SHARE_FAILED_MESSAGE,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            Constants.Messages.IMAGE_DATA_LOAD_FAILED_MESSAGE,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("PhotoInfo", Constants.Messages.SHARE_PREPARATION_ERROR_LOG, e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        Constants.Messages.SHARE_FAILED_MESSAGE + " ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 오류 다이얼로그 표시
     */
    private fun showErrorDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(Constants.UI.ERROR_DIALOG_TITLE)
            .setMessage(Constants.Messages.ERROR_LOADING_PHOTO_INFO_MESSAGE)
            .setPositiveButton(Constants.UI.DIALOG_POSITIVE_BUTTON_TEXT) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}