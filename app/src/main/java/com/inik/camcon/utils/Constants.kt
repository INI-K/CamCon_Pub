package com.inik.camcon.utils

/**
 * 앱 전체에서 사용되는 상수들을 관리합니다
 */
object Constants {

    /**
     * 파일 저장 관련 경로 - 외부 저장소 우선순위 시스템
     */
    object FilePaths {
        // 기본 앱 폴더명
        const val APP_FOLDER_NAME = "CamCon"
        const val DCIM_BASE_DIR = "DCIM"

        /**
         * 외부 저장소 우선순위 경로들 (SD카드 → 내장 외부 저장소)
         */
        val EXTERNAL_STORAGE_PRIORITY_PATHS = listOf(
            // 1순위: SD카드 경로들
            "/storage/sdcard1/$DCIM_BASE_DIR/$APP_FOLDER_NAME",        // SD카드
            "/storage/external_sd/$DCIM_BASE_DIR/$APP_FOLDER_NAME",    // 외부 SD
            "/storage/extSdCard/$DCIM_BASE_DIR/$APP_FOLDER_NAME",      // 외부 SD 다른 경로
            "/storage/usbdisk/$DCIM_BASE_DIR/$APP_FOLDER_NAME",        // USB
            "/storage/usb/$DCIM_BASE_DIR/$APP_FOLDER_NAME",            // USB 다른 경로
            "/mnt/external_sd/$DCIM_BASE_DIR/$APP_FOLDER_NAME",        // 마운트된 SD
            "/mnt/usb_storage/$DCIM_BASE_DIR/$APP_FOLDER_NAME",        // 마운트된 USB

            // 2순위: 내장 외부 저장소 (기본)
            "/storage/emulated/0/$DCIM_BASE_DIR/$APP_FOLDER_NAME"
        )

        /**
         * 기본 내장 외부 저장소 경로
         */
        const val DEFAULT_EXTERNAL_STORAGE_PATH = "/storage/emulated/0"

        /**
         * MediaStore 경로 (Android 10+)
         */
        fun getMediaStoreRelativePath(): String = "$DCIM_BASE_DIR/$APP_FOLDER_NAME"

        /**
         * 사용 가능한 외부 저장소 경로 찾기
         */
        fun findAvailableExternalStoragePath(): String {
            // 우선순위에 따라 사용 가능한 경로 찾기
            for (path in EXTERNAL_STORAGE_PRIORITY_PATHS) {
                val parentPath = path.substring(0, path.lastIndexOf("/$DCIM_BASE_DIR"))
                val parentDir = java.io.File(parentPath)

                // 부모 디렉토리가 존재하고 쓰기 가능한지 확인
                if (parentDir.exists() && parentDir.canWrite()) {
                    // 실제 저장 디렉토리 생성 시도
                    val targetDir = java.io.File(path)
                    if (targetDir.exists() || targetDir.mkdirs()) {
                        return path
                    }
                }
            }

            // 모든 경로 실패 시 기본 경로 반환
            return "$DEFAULT_EXTERNAL_STORAGE_PATH/$DCIM_BASE_DIR/$APP_FOLDER_NAME"
        }

        // 사용자 설정 가능한 경로 키
        const val DOWNLOAD_PATH_PREFERENCE_KEY = "custom_download_path"

        // 임시 파일 저장 디렉토리 (앱 내부)
        const val TEMP_CACHE_DIR = "temp_photos"
        const val TEMP_DOWNLOADS_DIR = "temp_downloads"
        const val SHARED_PHOTOS_DIR = "shared_photos"

        // 색감 전송 결과 저장 디렉토리
        const val COLOR_TRANSFER_DIR = "color_transferred"

        /**
         * 컨텍스트를 사용한 동적 경로 생성
         */
        fun getAppSpecificDownloadDir(context: android.content.Context): String {
            return "$DCIM_BASE_DIR/${context.getString(com.inik.camcon.R.string.app_name)}"
        }

        /**
         * 사용자 설정을 고려한 다운로드 경로
         */
        fun getDownloadPath(
            context: android.content.Context,
            preferences: android.content.SharedPreferences? = null
        ): String {
            return preferences?.getString(DOWNLOAD_PATH_PREFERENCE_KEY, null)
                ?: findAvailableExternalStoragePath()
        }

        /**
         * 저장소 타입 확인
         */
        fun getStorageType(path: String): StorageType {
            return when {
                path.contains("/storage/sdcard1/") ||
                        path.contains("/storage/external_sd/") ||
                        path.contains("/storage/extSdCard/") ||
                        path.contains("/mnt/external_sd/") -> StorageType.SD_CARD

                path.contains("/storage/usbdisk/") ||
                        path.contains("/storage/usb/") ||
                        path.contains("/mnt/usb_storage/") -> StorageType.USB_STORAGE

                path.contains("/storage/emulated/0/") -> StorageType.INTERNAL_EXTERNAL

                else -> StorageType.UNKNOWN
            }
        }

        enum class StorageType {
            SD_CARD,           // SD카드
            USB_STORAGE,       // USB 저장소
            INTERNAL_EXTERNAL, // 내장 외부 저장소
            UNKNOWN           // 알 수 없음
        }
    }

    /**
     * 네트워크 및 연결 관련
     */
    object Network {
        // USB 연결 관련
        const val USB_PERMISSION_TIMEOUT = 5000L // 5초

        // PTPIP 연결 관련
        const val PTPIP_DEFAULT_PORT = 15740
        const val PTPIP_CONNECTION_TIMEOUT = 10000L // 10초

        // 재시도 관련
        const val MAX_RETRY_COUNT = 3
        const val RETRY_DELAY_MS = 1000L
    }

    /**
     * 이미지 처리 관련
     */
    object ImageProcessing {
        // 썸네일 크기
        const val THUMBNAIL_MAX_WIDTH = 200
        const val THUMBNAIL_MAX_HEIGHT = 200

        // 미리보기 이미지 크기
        const val PREVIEW_MAX_WIDTH = 1920
        const val PREVIEW_MAX_HEIGHT = 1080

        // 지원하는 이미지 포맷
        val SUPPORTED_IMAGE_EXTENSIONS = listOf(
            "jpg", "jpeg", "png",
            "nef", "cr2", "arw", "dng", "orf", "rw2", "raf"
        )

        val RAW_EXTENSIONS = listOf(
            "nef", "cr2", "arw", "dng", "orf", "rw2", "raf"
        )

        val JPEG_EXTENSIONS = listOf(
            "jpg", "jpeg"
        )
    }

    /**
     * UI 관련 상수
     */
    object UI {
        // 페이징 관련
        const val DEFAULT_PAGE_SIZE = 50
        const val PREFETCH_PAGE_SIZE = 50

        // 애니메이션 지연
        const val ANIMATION_DELAY_MS = 200L
        const val THUMBNAIL_LOAD_DELAY_MS = 100L

        // 토스트 메시지 지속 시간
        const val TOAST_DURATION_SHORT = 2000
        const val TOAST_DURATION_LONG = 3500

        // 다이얼로그 타이틀
        const val DIALOG_TITLE_PHOTO_INFO = "사진 정보"
        const val DIALOG_TITLE_ERROR = "오류"
        const val DIALOG_TITLE_CONFIRM = "확인"

        // PhotoInfoDialog 전용 상수들
        const val PHOTO_INFO_DIALOG_TITLE = "사진 정보"
        const val ERROR_DIALOG_TITLE = "오류"
        const val DIALOG_POSITIVE_BUTTON_TEXT = "확인"

        // EXIF 관련 라벨
        const val EXIF_INFO_LABEL = "EXIF 정보"
        const val WIDTH_LABEL = "너비"
        const val HEIGHT_LABEL = "높이"
        const val ORIENTATION_LABEL = "방향"
        const val MAKE_LABEL = "제조사"
        const val MODEL_LABEL = "모델"
        const val ISO_LABEL = "ISO"
        const val EXPOSURE_TIME_LABEL = "노출시간"
        const val F_NUMBER_LABEL = "조리개"
        const val FOCAL_LENGTH_LABEL = "초점거리"
        const val DATETIME_LABEL = "촬영일시"

        // 방향 정보
        const val ORIENTATION_NORMAL = "정상 (0°)"
        const val ORIENTATION_ROTATED_180 = "180° 회전"
        const val ORIENTATION_ROTATED_90_CLOCKWISE = "시계방향 90° 회전"
        const val ORIENTATION_ROTATED_90_COUNTERCLOCKWISE = "반시계방향 90° 회전"

        // 공통 값
        const val UNKNOWN_VALUE = "알 수 없음"
        const val NO_EXIF_INFO = "없음"
        const val PARSING_ERROR = "파싱 오류"
    }

    /**
     * 로그 태그
     */
    object LogTags {
        const val CAMERA_REPOSITORY = "카메라레포지토리"
        const val PHOTO_PREVIEW = "PhotoPreviewViewModel"
        const val PHOTO_INFO = "PhotoInfo"
        const val STFALCON_VIEWER = "StfalconViewer"
        const val THUMBNAIL_ADAPTER = "ThumbnailAdapter"
        const val USB_MANAGER = "UsbCameraManager"
        const val PTPIP_MANAGER = "PtpipManager"
        const val COLOR_TRANSFER = "ColorTransfer"
    }

    /**
     * 메시지 문자열
     */
    object Messages {
        // 다운로드 관련
        const val DOWNLOAD_START = "다운로드를 시작합니다"
        const val DOWNLOAD_SUCCESS = "다운로드가 완료되었습니다"
        const val DOWNLOAD_FAILED = "다운로드에 실패했습니다"
        const val DOWNLOAD_STARTED_MESSAGE = "다운로드를 시작합니다:"
        const val DOWNLOAD_STARTED_LOG = "네이티브 다운로드 시작:"

        // 공유 관련
        const val SHARE_TITLE = "사진 공유하기"
        const val SHARE_FAILED = "공유 기능을 사용할 수 없습니다"
        const val SHARE_DATA_ERROR = "이미지 데이터를 가져올 수 없습니다"
        const val SHARE_PHOTO_TITLE = "사진 공유하기"
        const val SHARE_FAILED_MESSAGE = "공유 기능을 사용할 수 없습니다"
        const val IMAGE_DATA_LOAD_FAILED_MESSAGE = "이미지 데이터를 가져올 수 없습니다"
        const val SHARE_INTENT_STARTED_LOG = "사진 공유 인텐트 실행:"
        const val SHARE_INTENT_ERROR_LOG = "공유 인텐트 실행 오류"
        const val SHARE_PREPARATION_ERROR_LOG = "공유 준비 오류"

        // 연결 관련
        const val CAMERA_NOT_CONNECTED = "카메라가 연결되지 않았습니다. 카메라를 연결해주세요."
        const val CAMERA_CONNECTION_LOST = "카메라 연결이 해제되었습니다"
        const val USB_PERMISSION_REQUIRED = "USB 권한이 필요합니다"

        // 에러 메시지
        const val ERROR_PHOTO_INFO_LOAD = "사진 정보를 불러올 수 없습니다."
        const val ERROR_PHOTO_LOAD = "사진을 불러오는데 실패했습니다"
        const val ERROR_THUMBNAIL_LOAD = "썸네일을 불러오는데 실패했습니다"
        const val ERROR_LOADING_PHOTO_INFO_MESSAGE = "사진 정보를 불러올 수 없습니다."
    }

    /**
     * MIME 타입
     */
    object MimeTypes {
        const val IMAGE_JPEG = "image/jpeg"
        const val IMAGE_PNG = "image/png"
        const val IMAGE_NEF = "image/x-nikon-nef"
        const val IMAGE_CR2 = "image/x-canon-cr2"
        const val IMAGE_ARW = "image/x-sony-arw"
        const val IMAGE_DNG = "image/x-adobe-dng"
        const val IMAGE_ORF = "image/x-olympus-orf"
        const val IMAGE_RW2 = "image/x-panasonic-rw2"
        const val IMAGE_RAF = "image/x-fuji-raf"
        const val IMAGE_WILDCARD = "image/*"
    }

    /**
     * FileProvider 관련
     */
    object FileProvider {
        const val AUTHORITY_SUFFIX = ".fileprovider"

        fun getAuthority(packageName: String): String {
            return "$packageName$AUTHORITY_SUFFIX"
        }
    }

    /**
     * 구독 관련 상수
     */
    object Subscription {
        // Google Play Console 구독 상품 ID들
        const val BASIC_MONTHLY_PRODUCT_ID = "camcon_basic_monthly"
        const val BASIC_YEARLY_PRODUCT_ID = "camcon_basic_yearly"
        const val PRO_MONTHLY_PRODUCT_ID = "camcon_pro_monthly"
        const val PRO_YEARLY_PRODUCT_ID = "camcon_pro_yearly"

        // 구독 등급별 지원 포맷
        val FREE_SUPPORTED_FORMATS = listOf("jpg", "jpeg")
        val BASIC_SUPPORTED_FORMATS = listOf("jpg", "jpeg", "png")
        val PRO_SUPPORTED_FORMATS = listOf(
            "jpg",
            "jpeg",
            "png",
            "webp",
            "raw",
            "nef",
            "cr2",
            "arw",
            "dng",
            "orf",
            "rw2",
            "raf"
        )

        // 구독 등급 메시지
        const val FREE_TIER_MESSAGE = "무료 버전에서는 JPG 포맷만 지원됩니다"
        const val UPGRADE_REQUIRED_MESSAGE = "이 기능을 사용하려면 업그레이드가 필요합니다"
        const val FORMAT_NOT_SUPPORTED_MESSAGE = "현재 구독에서 지원하지 않는 포맷입니다"

        // 기능 제한 키
        const val FEATURE_RAW_PROCESSING = "raw_processing"
        const val FEATURE_PNG_EXPORT = "png_export"
        const val FEATURE_WEBP_EXPORT = "webp_export"
        const val FEATURE_ADVANCED_FILTERS = "advanced_filters"
        const val FEATURE_BATCH_PROCESSING = "batch_processing"

        // Firebase 컬렉션 이름
        const val USERS_COLLECTION = "users"
        const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
    }
}