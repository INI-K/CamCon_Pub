package com.inik.camcon.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CamCon Baseline Profile 생성기.
 *
 * 앱 콜드 스타트(SplashActivity) → 첫 프레임 렌더까지의 핫 경로를 기록한다.
 * SplashActivity가 로그인 상태를 검사해 LoginActivity 또는 MainActivity로 분기하므로,
 * 인증 없이 재현 가능한 구간(스플래시 + 첫 화면 진입 + 초기 정착)만 커버한다.
 *
 * CameraControl(MainActivity) 화면은 Firebase 로그인 세션이 필요하므로
 * 자동 프로파일에는 포함하지 않는다. 인증된 디바이스에서 확장하려면
 * generate {} 블록 안에서 로그인 후 MainActivity로 진입하는 단계를 추가한다.
 *
 * 실행(연결된 arm64 실기기 또는 arm64 GMD 필요):
 *   ./gradlew :baselineprofile:pixel6Api33BaselineProfileAndroidTest        // GMD
 *   ./gradlew generateBaselineProfile                                       // consumer(:app) 병합까지
 * 연결된 실기기로 돌릴 때는 :baselineprofile build.gradle의
 * baselineProfile { useConnectedDevices = true } 로 전환.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE_NAME,
        // 안정적인 콜드 스타트 프로파일을 위해 여러 번 반복 수집.
        maxIterations = 10,
        stableIterations = 3
    ) {
        pressHome()
        startActivityAndWait()

        // 첫 프레임/초기 컴포지션 정착 대기.
        // (스플래시 이후 로그인 또는 메인 컨텐츠가 그려질 때까지)
        device.waitForIdle()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 5_000)
    }

    private companion object {
        const val PACKAGE_NAME = "com.inik.camcon"
    }
}
