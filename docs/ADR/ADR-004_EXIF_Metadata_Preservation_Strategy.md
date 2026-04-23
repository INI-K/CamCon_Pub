# ADR-004: EXIF 메타데이터 보존 전략

**날짜**: 2026-04-22  
**상태**: Accepted

## 컨텍스트

카메라에서 다운로드한 사진을 안드로이드 기기의 로컬 저장소에 저장할 때, EXIF 메타데이터(특히 회전 정보)가 손실되거나 역방향으로 적용되는 문제가 발생했다. 이는 사진 앱에서 세로 촬영 사진이 가로로 표시되는 사용자 경험 문제로 이어졌다.

### 문제점

1. **EXIF 회전 정보 역방향**: 원본 사진의 회전 방향과 반대로 적용됨
2. **메타데이터 손실**: 촬영 날짜, ISO, 셔터 스피드, 조리개 등의 정보 누락 가능
3. **플랫폼 간 불일치**: 카메라 → 안드로이드 → 사진 갤러리 간 회전 정보 처리 방식 차이

## 결정

### 1. EXIF 유틸리티 클래스 도입

`ExifHandlingUtils` 유틸리티 클래스에서 EXIF 처리 전담:
- 원본 EXIF 읽기 (카메라에서 다운로드한 바이너리 데이터)
- 회전 정보 정규화 (ExifInterface의 표준 범위: 1-8)
- 새 파일에 EXIF 기록

### 2. PhotoDownloadManager에서 EXIF 보존 로직

`PhotoDownloadManager.downloadPhoto()` 처리 흐름:
1. 카메라에서 원본 바이너리 다운로드
2. `ExifHandlingUtils.readExif(originalBytes)` → EXIF 태그 맵 추출
3. 안드로이드 파일에 저장
4. `ExifHandlingUtils.applyExif(savedFilePath, exifMap)` → 메타데이터 재적용

### 3. 회전 정보 정규화

EXIF Orientation 필드 정규화 알고리즘:
- 카메라 기준각 → Android MediaStore 기준각 변환
- 표준 범위(1-8) 준수로 모든 갤러리 앱 호환성 확보

### 4. 포맷별 전략

| 포맷 | EXIF 읽기 | EXIF 쓰기 | 비고 |
|------|----------|----------|------|
| JPEG | ExifInterface | ExifInterface | 표준 지원 |
| PNG | 명시적 파싱 | 메타데이터 청크 | 제한적 지원 |
| RAW | 원본 보존 | 사이드카 생성 | 별도 처리 |

## 결과

### 긍정적 결과

1. **메타데이터 보존**: 촬영 날짜, 장비, 노출 정보 유지
2. **올바른 회전 표시**: 갤러리/사진 앱에서 정확한 방향 표시
3. **사용자 신뢰도**: 다운로드 사진의 품질 보증
4. **향후 기능 확장**: 메타데이터 기반 정렬, 필터링 기능 가능

### 부정적 결과 / 주의사항

1. **처리 시간 증가**: EXIF 읽기/쓰기 오버헤드 (일반적으로 <50ms)
2. **포맷 호환성**: PNG/RAW 등 비표준 포맷의 EXIF 지원 제한적
3. **메모리 사용**: 대량 사진 배치 처리 시 EXIF 데이터 누적 가능

## 이행 현황

- 2026-04-22: 모든 변경사항 구현 및 테스트 완료
  - `ExifHandlingUtils.kt` 유틸리티 클래스 구현
    - `readExif(bytes: ByteArray)` → `Map<String, String>`
    - `applyExif(filePath: String, exifMap: Map<String, String>)`
    - 회전 정보 정규화 로직
  - `PhotoDownloadManager.downloadPhoto()` 에서 EXIF 보존 로직 추가
  - 회전 가능성 있는 모든 포맷에 대해 EXIF 처리 적용
  - 단위 테스트: `ExifHandlingTest`, `PhotoDownloadManagerExifTest`

## 참고

- 관련 이슈: C7 (EXIF 회전 역방향)
- 관련 파일:
  - `/app/src/main/java/com/inik/camcon/data/datasource/ExifHandlingUtils.kt`
  - `/app/src/main/java/com/inik/camcon/data/repository/managers/PhotoDownloadManager.kt`
- 참고 표준:
  - JPEG EXIF Orientation: ISO 12234-2
  - ExifInterface: Android Framework (androidx.exifinterface)
