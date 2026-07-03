# :baselineprofile

CamCon용 Baseline Profile / Macrobenchmark 생성 모듈.

앱 콜드 스타트 경로를 미리 프로파일링해 `.so`/DEX 핫 경로를 AOT 컴파일 힌트로
`:app`에 병합한다. 런타임 설치는 `androidx.profileinstaller`가 담당한다(별도 코드 불필요).

## 전제 조건

- **arm64 디바이스 필수.** CamCon은 `arm64-v8a` 전용 네이티브(libgphoto2 등)를 사용하므로
  x86/x86_64 에뮬레이터에서는 앱이 기동하지 못한다.
  → 연결된 실기기(arm64) 또는 Apple Silicon 호스트의 arm64 GMD(aosp)만 유효.
- **`key.properties` 필요.** Baseline Profile 생성은 release 파생 변형
  (`nonMinifiedRelease`)을 빌드한다. `app/build.gradle`의 서명 가드(H20)가
  서명 미구성 release 빌드를 차단하므로, 프로파일을 생성하려면 루트에
  `key.properties`(keyAlias/keyPassword/storeFile/storePassword)가 있어야 한다.
- **JAVA_HOME**: JBR 21 고정.
  ```bash
  export JAVA_HOME=/Users/ini-k/Library/Java/JavaVirtualMachines/jbr-21.0.9/Contents/Home
  ```

## 프로파일 생성

### A) Gradle Managed Device (기본 — arm64 호스트 권장)

```bash
# baseline-prof.txt 생성 + :app release에 자동 병합까지
./gradlew :app:generateReleaseBaselineProfile

# 또는 producer 모듈 단독 실행
./gradlew :baselineprofile:pixel6Api33BaselineProfileAndroidTest
```

생성 결과: `app/src/release/generated/baselineProfiles/baseline-prof.txt`
(다음 release 빌드부터 자동 소비됨).

### B) 연결된 실기기(arm64)

`baselineprofile/build.gradle`에서 전환:

```groovy
baselineProfile {
    useConnectedDevices = true
}
```

그 뒤:

```bash
adb devices          # arm64 기기 연결 확인
./gradlew :app:generateReleaseBaselineProfile
```

## 스타트업 벤치마크(프로파일 효과 측정)

```bash
# GMD
./gradlew :baselineprofile:pixel6Api33BenchmarkAndroidTest

# 결과: startupNoCompilation vs startupBaselineProfile 의 timeToInitialDisplay 비교
```

## 범위 / 한계

- 자동 프로파일은 **콜드 스타트(SplashActivity → 첫 프레임)** 만 커버한다.
  SplashActivity가 Firebase 로그인 상태로 분기하므로, 인증 없이는 CameraControl
  (MainActivity)까지 자동 도달할 수 없다.
- CameraControl/라이브뷰 경로까지 프로파일을 넓히려면 인증된 디바이스에서
  `BaselineProfileGenerator.generate {}` 블록에 로그인 → MainActivity 진입 단계를
  추가한다. USB/PTP-IP/JNI 실제 카메라 경로는 실물 장비가 없으면 프로파일링 불가.
