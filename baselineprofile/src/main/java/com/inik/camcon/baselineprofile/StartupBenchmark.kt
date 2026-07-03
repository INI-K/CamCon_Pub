package com.inik.camcon.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 콜드 스타트 시간 측정 벤치마크.
 *
 * Baseline Profile 적용 전/후 효과를 비교하기 위한 계측.
 *   - CompilationMode.None()               : 프로파일/AOT 없음(기준선)
 *   - CompilationMode.Partial(BaselineProfile) : 생성된 Baseline Profile 적용
 *
 * 실행(연결된 arm64 실기기 또는 arm64 GMD 필요):
 *   ./gradlew :baselineprofile:pixel6Api33BenchmarkAndroidTest
 * 연결된 실기기: :baselineprofile 을 connectedAndroidTest 로 실행하되
 * 벤치마크는 minified(release) 앱 필요 — CI에서 benchmarkRelease 변형으로 수행.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() =
        startup(CompilationMode.Partial())

    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 5_000)
    }

    private companion object {
        const val PACKAGE_NAME = "com.inik.camcon"
    }
}
