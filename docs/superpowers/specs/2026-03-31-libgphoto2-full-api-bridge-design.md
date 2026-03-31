# libgphoto2 전체 API 브릿지 설계

## 개요

libgphoto2 공개 API(204개 함수) 중 CamCon에 미노출된 기능을 전부 Kotlin Clean Architecture로 연결한다.

**작업 범위:**
1. CameraNative.kt에 선언되었지만 앱에서 미호출인 87개 JNI 함수 → DataSource → Repository → UseCase → ViewModel 연결
2. libgphoto2 공개 API 중 C++에서도 미사용인 기능 → 신규 C++ JNI 바인딩 + Kotlin 전체 레이어 구현

**제약 조건:**
- 현재 단일 `app` 모듈 유지
- 패키지 구조는 향후 멀티모듈 분리 대비
- 기존 CameraRepository 및 NativeCameraDataSource는 수정하지 않음 (신규 추가만)
- MVVM Clean Architecture 패턴 준수

---

## 아키텍처

### 신규 Repository 6개 (도메인 영역별 분리)

| Repository | 책임 | JNI 함수 수 |
|-----------|------|------------|
| CameraAdvancedCaptureRepository | 벌브, 비디오, 인터벌, 듀얼, 트리거, 오디오 | 12 + 1 신규 |
| CameraFocusRepository | AF 모드, AF 영역, 수동 초점 | 4 |
| CameraFileRepository | RAW, 업로드, 폴더, 스토리지, 캐시 | 19 + 1 신규 |
| CameraConfigRepository | 위젯 트리, 설정 CRUD, 카메라 정보, 제조사 설정 | 8 + 3 신규 |
| CameraStreamingRepository | PTP 스트리밍 | 4 |
| CameraDiagnosticsRepository | 진단, 에러, 메모리, 진행률, 훅 | 12 + 3 신규 |

합계: 기존 미사용 59개 + 신규 C++ 8개 = **67개 함수**
(나머지 28개는 Mock Camera 13개 + PTP/IP 전용 5개 + 기존 인프라 10개 — 별도 취급)

### 레이어 흐름

```
Composable → ViewModel Manager → UseCase → Repository(interface) → RepositoryImpl → DataSource → CameraNative JNI → C++ → libgphoto2
```

### 패키지 구조 (단일 app 모듈, 멀티모듈 대비)

```
com.inik.camcon/
├── domain/
│   ├── model/
│   │   ├── capture/        IntervalCaptureStatus, AudioCapture, BulbCaptureState, VideoRecordingState
│   │   ├── focus/          FocusConfig, FocusArea
│   │   ├── file/           RawFileInfo, StorageInfo, CameraFileInfo, FileTransferProgress
│   │   ├── config/         CameraConfigTree, ConfigWidget, ConfigWidgetType, ManufacturerSetting
│   │   ├── streaming/      StreamingConfig, StreamFrame
│   │   └── diagnostics/    DiagnosticsReport, MemoryPoolStatus, TransferProgress
│   ├── repository/
│   │   ├── CameraRepository.kt                    (기존 유지)
│   │   ├── CameraAdvancedCaptureRepository.kt      (신규)
│   │   ├── CameraFocusRepository.kt                (신규)
│   │   ├── CameraFileRepository.kt                 (신규)
│   │   ├── CameraConfigRepository.kt               (신규)
│   │   ├── CameraStreamingRepository.kt            (신규)
│   │   └── CameraDiagnosticsRepository.kt          (신규)
│   └── usecase/
│       ├── camera/         (기존 유지)
│       ├── capture/        벌브, 비디오, 인터벌, 듀얼, 트리거, 오디오 UseCase
│       ├── focus/          AF 모드, AF 영역, MF UseCase
│       ├── file/           RAW, 업로드, 폴더, 스토리지 UseCase
│       ├── config/         설정 트리, 제조사 설정 UseCase
│       ├── streaming/      스트리밍 UseCase
│       └── diagnostics/    진단, 에러, 메모리 UseCase
├── data/
│   ├── datasource/nativesource/
│   │   ├── NativeCameraDataSource.kt               (기존 유지)
│   │   ├── NativeAdvancedCaptureDataSource.kt      (신규)
│   │   ├── NativeFocusDataSource.kt                (신규)
│   │   ├── NativeFileDataSource.kt                 (신규)
│   │   ├── NativeConfigDataSource.kt               (신규)
│   │   ├── NativeStreamingDataSource.kt            (신규)
│   │   └── NativeDiagnosticsDataSource.kt          (신규)
│   └── repository/
│       ├── CameraRepositoryImpl.kt                 (기존 유지)
│       ├── CameraAdvancedCaptureRepositoryImpl.kt  (신규)
│       ├── CameraFocusRepositoryImpl.kt            (신규)
│       ├── CameraFileRepositoryImpl.kt             (신규)
│       ├── CameraConfigRepositoryImpl.kt           (신규)
│       ├── CameraStreamingRepositoryImpl.kt        (신규)
│       └── CameraDiagnosticsRepositoryImpl.kt      (신규)
├── di/
│   └── RepositoryModule.kt                         (신규 바인딩 추가)
└── presentation/viewmodel/
    ├── CameraViewModel.kt                          (신규 매니저 위임 추가)
    ├── CameraAdvancedCaptureManager.kt             (신규)
    ├── CameraFocusManager.kt                       (신규)
    ├── CameraFileManager.kt                        (신규)
    ├── CameraStreamingManager.kt                   (신규)
    └── CameraDiagnosticsManager.kt                 (신규)
```

---

## Repository 인터페이스 상세

### CameraAdvancedCaptureRepository

```kotlin
interface CameraAdvancedCaptureRepository {
    suspend fun startBulbCapture(): Result<Boolean>
    suspend fun endBulbCapture(): Result<Boolean>
    suspend fun bulbCaptureWithDuration(seconds: Int): Result<Boolean>
    suspend fun startVideoRecording(): Result<Boolean>
    suspend fun stopVideoRecording(): Result<Boolean>
    suspend fun isVideoRecording(): Result<Boolean>
    suspend fun startIntervalCapture(intervalSeconds: Int, totalFrames: Int): Result<Boolean>
    suspend fun stopIntervalCapture(): Result<Boolean>
    suspend fun getIntervalCaptureStatus(): Result<IntervalCaptureStatus>
    suspend fun captureDualMode(keepRawOnCard: Boolean, downloadJpeg: Boolean): Result<Boolean>
    suspend fun triggerCapture(): Result<Boolean>
    suspend fun captureAudio(): Result<Boolean>
}
```

### CameraFocusRepository

```kotlin
interface CameraFocusRepository {
    suspend fun setAFMode(mode: String): Result<Boolean>
    suspend fun getAFMode(): Result<String>
    suspend fun setAFArea(x: Int, y: Int, width: Int, height: Int): Result<Boolean>
    suspend fun driveManualFocus(steps: Int): Result<Boolean>
}
```

### CameraFileRepository

```kotlin
interface CameraFileRepository {
    suspend fun downloadRawFile(folder: String, filename: String): Result<ByteArray>
    suspend fun downloadAllRawFiles(folder: String): Result<Int>
    suspend fun extractRawMetadata(folder: String, filename: String): Result<String>
    suspend fun extractRawThumbnail(folder: String, filename: String): Result<ByteArray>
    suspend fun filterRawFiles(folder: String, minSizeMB: Int, maxSizeMB: Int): Result<List<String>>
    suspend fun uploadFileToCamera(folder: String, filename: String, data: ByteArray): Result<Boolean>
    suspend fun deleteAllFilesInFolder(folder: String): Result<Boolean>
    suspend fun createFolder(parentFolder: String, folderName: String): Result<Boolean>
    suspend fun removeFolder(parentFolder: String, folderName: String): Result<Boolean>
    suspend fun readFileChunk(path: String, offset: Long, size: Int): Result<ByteArray>
    suspend fun downloadByObjectHandle(handle: Long): Result<ByteArray>
    suspend fun getDetailedStorageInfo(): Result<StorageInfo>
    suspend fun initializeCache(): Result<Boolean>
    suspend fun invalidateFileCache(): Result<Boolean>
    suspend fun getRecentCapturedPaths(maxCount: Int): Result<List<String>>
    suspend fun clearRecentCapturedPaths(): Result<Boolean>
    suspend fun setFileInfo(folder: String, filename: String, info: CameraFileInfo): Result<Boolean>
}
```

### CameraConfigRepository

```kotlin
interface CameraConfigRepository {
    suspend fun getConfigTree(): Result<CameraConfigTree>
    suspend fun listAllConfigs(): Result<List<String>>
    suspend fun getConfigValue(key: String): Result<String>
    suspend fun setConfigValue(key: String, value: String): Result<Boolean>
    suspend fun getConfigInt(key: String): Result<Int>
    suspend fun setConfigInt(key: String, value: Int): Result<Boolean>
    suspend fun getCameraManual(): Result<String>
    suspend fun getCameraAbout(): Result<String>
    suspend fun readGphotoSettings(): Result<String>
    suspend fun setManufacturerSetting(setting: ManufacturerSetting): Result<Boolean>
    suspend fun getManufacturerSetting(setting: ManufacturerSettingQuery): Result<String>
}
```

### CameraStreamingRepository

```kotlin
interface CameraStreamingRepository {
    suspend fun startStreaming(): Result<Boolean>
    suspend fun stopStreaming(): Result<Boolean>
    fun getStreamFrames(): Flow<StreamFrame>
    suspend fun setStreamingParameters(width: Int, height: Int, fps: Int): Result<Boolean>
}
```

### CameraDiagnosticsRepository

```kotlin
interface CameraDiagnosticsRepository {
    suspend fun diagnoseCameraIssues(): Result<String>
    suspend fun diagnoseUSBConnection(): Result<String>
    suspend fun getErrorHistory(count: Int): Result<String>
    suspend fun clearErrorHistory(): Result<Boolean>
    suspend fun getCameraFilePoolCount(): Result<Int>
    suspend fun clearCameraFilePool(): Result<Boolean>
    suspend fun getMemoryPoolStatus(): Result<MemoryPoolStatus>
    suspend fun getLogFilePath(): Result<String>
    suspend fun isLogFileActive(): Result<Boolean>
    suspend fun isOperationCanceled(): Result<Boolean>
    fun getTransferProgress(): Flow<TransferProgress>
    fun getStatusMessages(): Flow<String>
    suspend fun registerHookCallback(): Result<Boolean>
    suspend fun unregisterHookCallback(): Result<Boolean>
}
```

---

## 신규 C++ JNI 바인딩 (8개)

### 1. camera_about.cpp (신규 파일)
- `gp_camera_get_about()` → JNI `getCameraAbout()` → String (드라이버 정보)

### 2. camera_audio.cpp (신규 파일)  
- `gp_camera_capture(GP_CAPTURE_SOUND)` → JNI `captureAudio()` → int (결과 코드)

### 3. camera_context_callbacks.cpp (신규 파일)
- `gp_context_set_progress_funcs()` → JNI `setProgressCallback()` + `getTransferProgress()`
- `gp_context_set_cancel_func()` → JNI `setCancelCallback()`
- `gp_context_set_status_func()` → JNI `setStatusCallback()` + `getStatusMessage()`

### 4. camera_config.cpp (기존 파일 확장)
- `gp_widget_get_readonly()` → `buildWidgetJson()` 응답에 `readonly` 필드 추가
- `gp_widget_changed()` → `buildWidgetJson()` 응답에 `changed` 필드 추가

### 5. camera_files.cpp (기존 파일 확장)
- `gp_camera_file_set_info()` → JNI `setFileInfo()` → int (결과 코드)

---

## 도메인 모델

### capture/
```kotlin
data class IntervalCaptureStatus(val currentFrame: Int, val totalFrames: Int, val isRunning: Boolean)
data class BulbCaptureState(val isActive: Boolean, val elapsedSeconds: Int)
data class VideoRecordingState(val isRecording: Boolean)
```

### focus/
```kotlin
data class FocusConfig(val mode: String, val availableModes: List<String>)
data class FocusArea(val x: Int, val y: Int, val width: Int, val height: Int)
```

### file/
```kotlin
data class StorageInfo(val label: String, val description: String, val totalKB: Long, val freeKB: Long, val freeImages: Int, val storageType: String, val accessType: String, val filesystemType: String)
data class RawFileInfo(val folder: String, val filename: String, val sizeMB: Int)
data class CameraFileInfo(val permissions: Int, val mtime: Long, val width: Int, val height: Int)
data class FileTransferProgress(val current: Float, val total: Float, val percentage: Int)
```

### config/
```kotlin
data class CameraConfigTree(val widgets: List<ConfigWidget>)
data class ConfigWidget(val name: String, val label: String, val type: ConfigWidgetType, val value: String?, val choices: List<String>, val range: ConfigRange?, val readonly: Boolean, val changed: Boolean, val info: String?, val children: List<ConfigWidget>)
data class ConfigRange(val min: Float, val max: Float, val step: Float)
enum class ConfigWidgetType { WINDOW, SECTION, TEXT, RANGE, TOGGLE, RADIO, MENU, BUTTON, DATE }
sealed class ManufacturerSetting(val key: String, val value: String)
data class ManufacturerSettingQuery(val key: String)
```

### streaming/
```kotlin
data class StreamingConfig(val width: Int, val height: Int, val fps: Int)
data class StreamFrame(val data: ByteArray, val width: Int, val height: Int, val timestamp: Long)
```

### diagnostics/
```kotlin
data class DiagnosticsReport(val cameraIssues: String, val usbDiagnostics: String)
data class MemoryPoolStatus(val activeCount: Int, val totalAllocated: Long, val details: String)
data class TransferProgress(val current: Float, val total: Float, val percentage: Int, val description: String)
```

---

## DI 바인딩 (RepositoryModule.kt 확장)

```kotlin
@Binds abstract fun bindAdvancedCaptureRepository(impl: CameraAdvancedCaptureRepositoryImpl): CameraAdvancedCaptureRepository
@Binds abstract fun bindFocusRepository(impl: CameraFocusRepositoryImpl): CameraFocusRepository
@Binds abstract fun bindFileRepository(impl: CameraFileRepositoryImpl): CameraFileRepository
@Binds abstract fun bindConfigRepository(impl: CameraConfigRepositoryImpl): CameraConfigRepository
@Binds abstract fun bindStreamingRepository(impl: CameraStreamingRepositoryImpl): CameraStreamingRepository
@Binds abstract fun bindDiagnosticsRepository(impl: CameraDiagnosticsRepositoryImpl): CameraDiagnosticsRepository
```

DataSource는 AppModule.kt의 `@Provides @Singleton` 패턴 사용.

---

## UseCase 목록

### capture/ (12개)
StartBulbCaptureUseCase, EndBulbCaptureUseCase, BulbCaptureWithDurationUseCase, StartVideoRecordingUseCase, StopVideoRecordingUseCase, IsVideoRecordingUseCase, StartIntervalCaptureUseCase, StopIntervalCaptureUseCase, GetIntervalCaptureStatusUseCase, CaptureDualModeUseCase, TriggerCaptureUseCase, CaptureAudioUseCase

### focus/ (4개)
SetAFModeUseCase, GetAFModeUseCase, SetAFAreaUseCase, DriveManualFocusUseCase

### file/ (17개)
DownloadRawFileUseCase, DownloadAllRawFilesUseCase, ExtractRawMetadataUseCase, ExtractRawThumbnailUseCase, FilterRawFilesUseCase, UploadFileToCameraUseCase, DeleteAllFilesInFolderUseCase, CreateFolderUseCase, RemoveFolderUseCase, ReadFileChunkUseCase, DownloadByObjectHandleUseCase, GetDetailedStorageInfoUseCase, InitializeCacheUseCase, InvalidateFileCacheUseCase, GetRecentCapturedPathsUseCase, ClearRecentCapturedPathsUseCase, SetFileInfoUseCase

### config/ (11개)
GetConfigTreeUseCase, ListAllConfigsUseCase, GetConfigValueUseCase, SetConfigValueUseCase, GetConfigIntUseCase, SetConfigIntUseCase, GetCameraManualUseCase, GetCameraAboutUseCase, ReadGphotoSettingsUseCase, SetManufacturerSettingUseCase, GetManufacturerSettingUseCase

### streaming/ (4개)
StartStreamingUseCase, StopStreamingUseCase, GetStreamFramesUseCase, SetStreamingParametersUseCase

### diagnostics/ (14개)
DiagnoseCameraIssuesUseCase, DiagnoseUSBConnectionUseCase, GetErrorHistoryUseCase, ClearErrorHistoryUseCase, GetCameraFilePoolCountUseCase, ClearCameraFilePoolUseCase, GetMemoryPoolStatusUseCase, GetLogFilePathUseCase, IsLogFileActiveUseCase, IsOperationCanceledUseCase, GetTransferProgressUseCase, GetStatusMessagesUseCase, RegisterHookCallbackUseCase, UnregisterHookCallbackUseCase

합계: **62개 UseCase**

---

## ViewModel Manager 매핑

| Manager | Repository | UseCase 수 |
|---------|-----------|-----------|
| CameraAdvancedCaptureManager | CameraAdvancedCaptureRepository | 12 |
| CameraFocusManager | CameraFocusRepository | 4 |
| CameraFileManager | CameraFileRepository | 17 |
| (CameraSettingsManager 확장) | CameraConfigRepository | 11 |
| CameraStreamingManager | CameraStreamingRepository | 4 |
| CameraDiagnosticsManager | CameraDiagnosticsRepository | 14 |

CameraViewModel은 신규 Manager들을 생성자 주입받아 위임한다.

---

## Mock Camera (별도 취급)

13개 Mock 함수는 테스트 인프라로, `CameraMockRepository` + `CameraMockDataSource`로 분리.
디버그 빌드에서만 활성화. 향후 `:testing:camera-mock` 모듈 후보.

---

## PTP/IP 전용 (별도 취급)

5개 PTP/IP 함수 (clearPtpipSettings, resetPtpipGuid, setCameraInfoFromPtpip, maintainSessionForStaMode, initCameraWithSessionMaintenance)는 기존 PtpipDataSource 확장으로 처리.
