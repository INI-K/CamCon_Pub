package com.inik.camcon.presentation.ui.screens.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.inik.camcon.R
import com.inik.camcon.domain.model.CameraPhoto
import com.inik.camcon.presentation.theme.BodySmall
import com.inik.camcon.presentation.theme.HeadingM
import com.inik.camcon.presentation.theme.HeadingS
import com.inik.camcon.presentation.theme.IconSize
import com.inik.camcon.presentation.theme.MonoHero
import com.inik.camcon.presentation.theme.MonoNumeric
import com.inik.camcon.presentation.theme.MonoReadout
import com.inik.camcon.presentation.theme.Spacing
import com.inik.camcon.presentation.viewmodel.PhotoPreviewViewModel
import com.inik.camcon.utils.LogMask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoInfoBottomSheetContent(
    photo: CameraPhoto,
    viewModel: PhotoPreviewViewModel?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exifInfo = remember { mutableStateOf<String?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(photo.path) {
        withContext(Dispatchers.IO) {
            try {
                val info = if (viewModel != null) {
                    viewModel.getCameraPhotoExif(photo.path)
                } else {
                    readExifFromFile(context, photo.path, photo.uri)
                }

                exifInfo.value = info
            } catch (e: Exception) {
                Log.e("PhotoInfoDialog", "EXIF 정보 로드 실패", e)
                exifInfo.value = null
            } finally {
                isLoading.value = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.base)
            .padding(bottom = Spacing.xl)
    ) {
        PhotoInfoBottomSheetHeader()

        PhotoInfoExposureHero(exifInfo = exifInfo.value, isLoading = isLoading.value)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            PhotoInfoDateRow(photo = photo, exifInfo = exifInfo.value, isLoading = isLoading.value)

            PhotoInfoFileRow(photo = photo, exifInfo = exifInfo.value, isLoading = isLoading.value)

            PhotoInfoExifRow(exifInfo = exifInfo.value, isLoading = isLoading.value)
        }
    }
}

/**
 * 시트 제목 — 아래 노출 Hero 의 eyebrow 로 강등한다(20sp → 14sp).
 * 한글 문자열이라 트래킹 1.4 의 MicroLabel 대신 HeadingS 를 쓴다.
 */
@Composable
private fun PhotoInfoBottomSheetHeader(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.fullscreen_viewer_detail_info),
        style = HeadingS,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.xs)
    )
}

/**
 * 시트 Hero — 이 화면의 존재 이유("이 컷이 어떤 노출로 찍혔나")를 주 판독값으로 세운다.
 * 주값(셔터 우선)은 MonoHero(38sp), 나머지는 MonoReadout(16sp)라 본문 14sp 대비 2.7배.
 * 노출 정보가 없거나 로딩 중이면 자리를 비워 더미 수치를 만들지 않는다.
 */
@Composable
private fun PhotoInfoExposureHero(
    exifInfo: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val exifJson = exifInfo.orEmpty()
    if (isLoading || exifJson.isEmpty() || exifJson == "{}") return

    val readouts = remember(exifJson) {
        runCatching {
            val entries = parseExifInfo(exifJson)
            listOfNotNull(
                entries["exposure_time"]?.takeIf { it.isNotBlank() }
                    ?.let { formatShutterSpeed(it) },
                entries["f_number"]?.takeIf { it.isNotBlank() }?.let { formatAperture(it) },
                entries["iso"]?.takeIf { it.isNotBlank() }?.let { "ISO $it" }
            )
        }.getOrElse { emptyList() }
    }

    if (readouts.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = readouts.first(),
            style = MonoHero,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )

        if (readouts.size > 1) {
            Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
                readouts.drop(1).forEach { readout ->
                    Text(
                        text = readout,
                        style = MonoReadout,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoInfoDateRow(
    photo: CameraPhoto,
    exifInfo: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // 필수3 — 날짜 fallback 문자열 i18n (비-Composable formatPhotoDate 에 주입).
    val unknownDate = stringResource(R.string.gallery_v2_date_unknown)
    val loadingDate = stringResource(R.string.fullscreen_viewer_loading_date)
    val formattedDate by remember(exifInfo, isLoading, unknownDate, loadingDate) {
        derivedStateOf {
            formatPhotoDate(photo, exifInfo, isLoading, unknownDate, loadingDate)
        }
    }

    InfoRow(
        modifier = modifier,
        icon = {
            Icon(
                Icons.Outlined.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(IconSize.lg),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        content = {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    )
}

@Composable
private fun PhotoInfoFileRow(
    photo: CameraPhoto,
    exifInfo: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    InfoRow(
        modifier = modifier,
        icon = {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(IconSize.lg),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        content = {
            Column {
                Text(
                    text = photo.name,
                    style = HeadingM,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                // 용량과 해상도를 한 문자열로 붙이면 사진을 넘길 때마다 두 수치의 x 위치가 흔들린다.
                // 각각 독립 모노(tnum) 슬롯으로 분리해 자릿수가 바뀌어도 자리가 고정되게 한다.
                val dimensions = if (!isLoading && !exifInfo.isNullOrEmpty() && exifInfo != "{}") {
                    remember(exifInfo) { formatPixelSize(exifInfo) }
                } else {
                    null
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        text = String.format(
                            Locale.getDefault(),
                            "%.1f MB",
                            photo.size / 1024.0 / 1024.0
                        ),
                        style = MonoNumeric,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (dimensions != null) {
                        Text(
                            text = dimensions,
                            style = MonoNumeric,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val internalStorageLabel = stringResource(R.string.gallery_v2_internal_storage)
                val folderPath = photo.path.substringBeforeLast("/")
                    .replace("/storage/emulated/0", internalStorageLabel)

                Text(
                    text = folderPath,
                    style = BodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun PhotoInfoExifRow(
    exifInfo: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    InfoRow(
        modifier = modifier,
        icon = {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(IconSize.lg),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        content = {
            if (isLoading) {
                Text(
                    text = stringResource(R.string.fullscreen_viewer_exif_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ExifInfoContent(exifInfo = exifInfo)
            }
        }
    )
}

@Composable
fun InfoRow(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.base)
    ) {
        icon()
        content()
    }
}

@Composable
fun ExifInfoContent(
    exifInfo: String?,
    modifier: Modifier = Modifier
) {
    if (exifInfo.isNullOrEmpty() || exifInfo == "{}") {
        // 필수3 — 미다운로드/데이터 없음은 "불러오는 중"이 아니라 안내 상태로 구분 표시.
        Text(
            text = stringResource(R.string.gallery_v2_exif_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        val exifEntries = remember(exifInfo) {
            runCatching { parseExifInfo(exifInfo) }.getOrNull()
        }

        if (exifEntries == null) {
            Log.e("ExifInfoContent", "Failed to parse EXIF info")
            Text(
                text = stringResource(R.string.fullscreen_viewer_exif_parse_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            ExifEntriesList(exifEntries = exifEntries, modifier = modifier)
        }
    }
}

/**
 * 파싱된 EXIF 항목을 i18n 라벨로 렌더링(필수3).
 * ExifInfoContent / PhotoExifPanel 공통 사용으로 중복 제거.
 */
@Composable
fun ExifEntriesList(
    exifEntries: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        val cameraModel = exifEntries["camera_model"]
        if (!cameraModel.isNullOrBlank()) {
            ExifField(
                stringResource(R.string.gallery_v2_exif_camera),
                cameraModel,
                isNumeric = false
            )
        }

        val iso = exifEntries["iso"]
        if (!iso.isNullOrBlank()) {
            ExifField(stringResource(R.string.gallery_v2_exif_iso), iso)
        }

        val exposureTime = exifEntries["exposure_time"]
        if (!exposureTime.isNullOrBlank()) {
            ExifField(
                stringResource(R.string.gallery_v2_exif_shutter),
                formatShutterSpeed(exposureTime)
            )
        }

        val fNumber = exifEntries["f_number"]
        if (!fNumber.isNullOrBlank()) {
            ExifField(stringResource(R.string.gallery_v2_exif_aperture), formatAperture(fNumber))
        }

        val focalLength = exifEntries["focal_length"]
        if (!focalLength.isNullOrBlank()) {
            ExifField(
                stringResource(R.string.gallery_v2_exif_focal_length),
                formatFocalLength(focalLength)
            )
        }

        val whiteBalance = exifEntries["white_balance"]
        if (!whiteBalance.isNullOrBlank()) {
            ExifField(
                stringResource(R.string.gallery_v2_exif_white_balance),
                formatWhiteBalanceLabel(whiteBalance),
                isNumeric = false
            )
        }

        val flash = exifEntries["flash"]
        if (!flash.isNullOrBlank()) {
            ExifField(
                stringResource(R.string.gallery_v2_exif_flash),
                formatFlashLabel(flash),
                isNumeric = false
            )
        }

        val dateTimeOriginal = exifEntries["date_time_original"]
        if (!dateTimeOriginal.isNullOrBlank()) {
            ExifField(stringResource(R.string.gallery_v2_exif_date), dateTimeOriginal)
        }
    }
}

private fun readExifFromFile(
    context: android.content.Context,
    filePath: String,
    uri: String?
): String? {
    return try {
        // 스코프드 스토리지(API29+)에서 raw 경로가 막히면 MediaStore content URI 로 EXIF 관통.
        val exif = if (uri != null) {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                androidx.exifinterface.media.ExifInterface(it)
            } ?: return null
        } else {
            val file = java.io.File(filePath)
            if (!file.exists()) return null
            androidx.exifinterface.media.ExifInterface(filePath)
        }
        val exifMap = mutableMapOf<String, Any>()

        exifMap["width"] = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, 0
        )
        exifMap["height"] = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, 0
        )
        exifMap["camera_model"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_MODEL
        ) ?: ""
        exifMap["iso"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY
        ) ?: ""
        exifMap["exposure_time"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME
        ) ?: ""
        exifMap["f_number"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER
        ) ?: ""
        exifMap["focal_length"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH
        ) ?: ""
        exifMap["white_balance"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_WHITE_BALANCE
        ) ?: ""
        exifMap["flash"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_FLASH
        ) ?: ""
        exifMap["date_time_original"] = exif.getAttribute(
            androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL
        ) ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME) ?: ""

        // 표시용 픽셀 크기. EXIF 를 우선하고, 없으면 헤더만 읽어 채운다.
        readPixelSize(context, exif, filePath, uri)?.let { (width, height) ->
            exifMap["pixel_width"] = width.toString()
            exifMap["pixel_height"] = height.toString()
        }

        com.google.gson.Gson().toJson(exifMap)
    } catch (e: Exception) {
        Log.e("readExifFromFile", "Failed to read EXIF from file: ${LogMask.path(filePath)}", e)
        null
    }
}

/**
 * 사진의 **표시 방향 기준** 픽셀 크기를 구한다.
 *
 * 1순위는 EXIF `ImageWidth`/`ImageLength` 다. 값이 없는 파일(재인코딩된 JPEG 등)에서는
 * [android.graphics.BitmapFactory.Options.inJustDecodeBounds] 로 **헤더만** 읽는다. 픽셀을
 * 실제로 디코딩하지 않으므로 수동 디코딩 금지 규약(18da262)에 걸리지 않는다.
 *
 * EXIF orientation 이 90·270(전치 포함)이면 가로세로를 바꿔서 돌려준다. 그렇게 해야 화면에
 * 서 있는 세로 사진이 "8256 × 5504"로 누워 보이지 않는다(C7 전례와 같은 축 교환).
 *
 * @return 표시 기준 (가로, 세로). 어느 경로로도 못 구하면 null — 호출자가 그 줄을 생략한다.
 */
private fun readPixelSize(
    context: android.content.Context,
    exif: androidx.exifinterface.media.ExifInterface,
    filePath: String,
    uri: String?
): Pair<Int, Int>? {
    var width = exif.getAttributeInt(
        androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, 0
    )
    var height = exif.getAttributeInt(
        androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, 0
    )

    if (width <= 0 || height <= 0) {
        val bounds = decodeBoundsOnly(context, filePath, uri) ?: return null
        width = bounds.first
        height = bounds.second
    }
    if (width <= 0 || height <= 0) return null

    val rotated = when (
        exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )
    ) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90,
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270,
        androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE,
        androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> true

        else -> false
    }

    return if (rotated) height to width else width to height
}

/** 헤더만 읽어 원본 픽셀 크기를 구한다. 디코딩된 비트맵은 만들지 않는다. */
private fun decodeBoundsOnly(
    context: android.content.Context,
    filePath: String,
    uri: String?
): Pair<Int, Int>? {
    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    return try {
        if (uri != null) {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            android.graphics.BitmapFactory.decodeFile(filePath, options)
        }
        // RAW 처럼 BitmapFactory 가 못 읽는 포맷은 -1 이 남는다.
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    } catch (e: Exception) {
        Log.d("readPixelSize", "헤더 크기 조회 실패: ${LogMask.path(filePath)}", e)
        null
    }
}

/**
 * 상세정보에 넣을 픽셀 크기 문구를 만든다. 예: `8256 × 5504 · 45.4MP`.
 *
 * ⚠️ Gson 은 숫자 값을 `Double` 로 되돌리는데 [parseExifInfo] 의 반환 타입은 `Map<String, String>`
 * 이라, 카메라 경로가 넣은 숫자 항목에 문자열 연산을 하면 ClassCastException 이 난다. 그래서
 * 여기서는 값을 [Any] 로 받아 [toString] 을 거쳐 다룬다.
 */
private fun formatPixelSize(exifJson: String): String? {
    val entries = runCatching {
        @Suppress("UNCHECKED_CAST")
        com.google.gson.Gson().fromJson(exifJson, Map::class.java) as? Map<String, Any?>
    }.getOrNull() ?: return null

    fun dimension(vararg keys: String): Int? = keys.asSequence()
        .mapNotNull { entries[it]?.toString()?.toDoubleOrNull()?.toInt() }
        .firstOrNull { it > 0 }

    // pixel_* 는 이 파일이 방향까지 반영해 넣은 값이고, width/height 는 카메라 경로가 넣는 원본 값이다.
    val width = dimension("pixel_width", "width") ?: return null
    val height = dimension("pixel_height", "height") ?: return null

    val megaPixels = width.toLong() * height.toLong() / 1_000_000.0
    return String.format(Locale.getDefault(), "%d × %d · %.1fMP", width, height, megaPixels)
}

private fun formatPhotoDate(
    photo: CameraPhoto,
    exifInfo: String?,
    isLoading: Boolean,
    unknownDate: String,
    loadingDate: String
): String {
    // 표시 포맷은 기기 로케일을 따른다(필수3: 한국어 고정 제거).
    val displayFormat = SimpleDateFormat("yyyy.MM.dd a h:mm", Locale.getDefault())
    return if (!isLoading && !exifInfo.isNullOrEmpty() && exifInfo != "{}") {
        try {
            val exifEntries = parseExifInfo(exifInfo)
            val dateTimeOriginal = exifEntries["date_time_original"]

            if (dateTimeOriginal != null) {
                val exifFormat =
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())

                try {
                    val parsedDate = exifFormat.parse(dateTimeOriginal)
                    if (parsedDate != null) {
                        displayFormat.format(parsedDate)
                    } else {
                        unknownDate
                    }
                } catch (e: Exception) {
                    Log.e("PhotoInfoDialog", "EXIF 날짜 파싱 예외", e)
                    unknownDate
                }
            } else {
                displayFormat.format(Date(photo.date))
            }
        } catch (e: Exception) {
            Log.w("PhotoInfoDialog", "EXIF 정보 파싱 실패", e)
            displayFormat.format(Date(photo.date))
        }
    } else {
        if (isLoading) {
            loadingDate
        } else {
            displayFormat.format(Date(photo.date))
        }
    }
}
