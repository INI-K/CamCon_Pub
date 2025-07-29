# GitHub Actions 자동 동기화 설정 가이드

이 가이드는 프라이빗 레포지토리에서 퍼블릭 레포지토리로 자동 동기화를 설정하는 방법을 설명합니다.

## 📋 필요한 설정

### 1. GitHub Personal Access Token 생성

1. GitHub에서 **Settings** → **Developer settings** → **Personal access tokens** → **Tokens (classic)
   ** 이동
2. **Generate new token (classic)** 클릭
3. 다음 권한들을 선택:
    - `repo` (Full control of private repositories)
    - `workflow` (Update GitHub Action workflows)
    - `write:packages` (Upload packages to GitHub Package Registry)

### 2. Repository Secrets 설정

프라이빗 레포지토리 (`INI-K/CamCon`)에서:

1. **Settings** → **Secrets and variables** → **Actions** 이동
2. **New repository secret** 클릭
3. 다음 Secret을 추가:

   ```
   Name: PUBLIC_REPO_TOKEN
   Value: [위에서 생성한 Personal Access Token]
   ```

### 3. 퍼블릭 레포지토리 확인

퍼블릭 레포지토리 (`INI-K/CamCon_Pub`)가 존재하는지 확인:

- URL: https://github.com/INI-K/CamCon_Pub
- 존재하지 않으면 새로 생성

## 🔄 동기화 작동 방식

### 트리거 조건

- `main` 브랜치에 푸시할 때
- `develop` 브랜치에 푸시할 때
- `main` 브랜치로 Pull Request가 머지될 때

### 제외되는 파일들

- `app/src/main/cpp/` - 네이티브 C++ 코드
- `app/src/main/jniLibs/` - JNI 라이브러리 파일들
- `Complete_arm64-v8a/` - ARM64 라이브러리
- `Key/` - 키 파일들 (보안)
- `key.properties` - 키 설정 파일
- `app/google-services.json` - Google 서비스 설정
- `local.properties` - 로컬 설정
- `build_libgphoto2.sh` - 빌드 스크립트
- `app/release/` - 릴리스 빌드 결과물
- `*.log` - 모든 로그 파일들

### 포함되는 파일들

- Android 애플리케이션 소스코드 (Java/Kotlin)
- UI 리소스 및 레이아웃
- Gradle 설정 파일들 (민감하지 않은 파일들만)
- 문서 및 README 파일들

## 🔧 수동 실행 방법

만약 수동으로 동기화를 실행하고 싶다면:

1. 프라이빗 레포지토리에서 **Actions** 탭 이동
2. **Sync to Public Repository** 워크플로 선택
3. **Run workflow** 버튼 클릭

## 📝 문제 해결

### Token 권한 오류

- Personal Access Token의 권한을 다시 확인
- Token이 만료되지 않았는지 확인
- Repository Secret이 올바르게 설정되었는지 확인

### 퍼블릭 레포지토리 접근 오류

- 퍼블릭 레포지토리가 존재하는지 확인
- 레포지토리 이름이 정확한지 확인 (`INI-K/CamCon_Pub`)

### 동기화 실패

- GitHub Actions 로그를 확인하여 구체적인 오류 메시지 파악
- 네트워크 문제인지, 권한 문제인지 확인

## ⚠️ 주의사항

1. **보안**: 민감한 정보가 포함된 파일들은 자동으로 제외됩니다
2. **덮어쓰기**: 퍼블릭 레포지토리의 내용은 매번 프라이빗 레포지토리 내용으로 덮어써집니다
3. **브랜치**: 현재는 `main` 브랜치로만 동기화됩니다
4. **충돌**: 퍼블릭 레포지토리에서 직접 수정한 내용은 동기화 시 사라집니다

## 📊 동기화 상태 확인

동기화 후 퍼블릭 레포지토리에 생성되는 파일들:

- `README.md` - 퍼블릭 버전 안내
- `.github/SYNC_STATUS.md` - 동기화 상태 정보
- `.gitignore` - 제외 파일 목록

---

설정 완료 후 이 파일은 삭제해도 됩니다.