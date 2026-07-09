package com.inik.camcon.data.datasource

import android.content.Context
import android.util.Log
import com.inik.camcon.utils.LogMask
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * libgphoto2 포트(iolib)·드라이버(camlib) 플러그인을 앱 private 디렉토리에 추출/보증하는 공용 설치기.
 *
 * nativeLibDir 는 read-only 이고, AAB(App Bundle)로 배포하면 네이티브 .so 는 base 가 아니라
 * config.<abi>.apk split 에 들어간다. `sourceDir` 만 스캔하면 iolib 를 0개 복사해 "No iolibs found"
 * (스토어 설치본 PTP/IP 전멸)가 되므로 `splitSourceDirs` 까지 함께 스캔한다.
 *
 * USB([com.inik.camcon.data.datasource.usb.UsbConnectionManager])와 Wi-Fi PTP/IP
 * ([com.inik.camcon.data.datasource.ptpip.PtpipDataSource])가 동일 추출 로직을 공유해
 * 자가 재추출 가드를 일원화한다(중복 복붙 제거).
 */
@Singleton
class Libgphoto2PluginInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun baseDir(): File = context.getDir(PLUGIN_BASE_DIR, Context.MODE_PRIVATE)

    /** 카메라 드라이버(camlib) 버전 디렉토리. */
    fun camlibDir(): File = File(baseDir(), CAMLIB_SUBDIR)

    /** 포트 I/O(iolib) 버전 디렉토리. */
    fun iolibDir(): File = File(baseDir(), IOLIB_SUBDIR)

    /** libgphoto2 에 넘길 pluginDir 인자(포트:드라이버, 콜론 구분). */
    fun pluginDirArg(): String = "${iolibDir().absolutePath}:${camlibDir().absolutePath}"

    /** 포트(iolib)·드라이버(camlib) .so 가 모두 하나 이상 존재하면 true. */
    fun arePluginsPresent(): Boolean = hasSo(iolibDir()) && hasSo(camlibDir())

    private fun hasSo(dir: File): Boolean =
        dir.listFiles()?.any { it.isFile && it.name.endsWith(".so") } == true

    /**
     * 플러그인 디렉토리를 보증하고 [pluginDirArg] 를 반환한다.
     * 이미 iolib/camlib 가 있으면 그대로 두고(멱등), 비어 있으면 APK(+split)에서 추출한다.
     */
    fun ensurePluginDirs(): String {
        try {
            val camlibDir = camlibDir()
            val iolibDir = iolibDir()

            if (arePluginsPresent()) {
                Log.d(TAG, "libgphoto2 플러그인 이미 존재: ${LogMask.path(baseDir().absolutePath)}")
                return pluginDirArg()
            }

            Log.d(TAG, "libgphoto2 플러그인 추출 시작: ${LogMask.path(baseDir().absolutePath)}")
            camlibDir.mkdirs()
            iolibDir.mkdirs()

            var iolibCount = 0
            var camlibCount = 0

            val apkPaths = buildList {
                add(context.applicationInfo.sourceDir)
                context.applicationInfo.splitSourceDirs?.let { addAll(it) }
            }
            for (apkPath in apkPaths) {
                val apkFile = ZipFile(apkPath)
                try {
                    val entries = apkFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name

                        // lib/arm64-v8a/ 하위의 .so 파일만 처리
                        if (!entryName.startsWith("lib/arm64-v8a/") || !entryName.endsWith(".so")) {
                            continue
                        }

                        val fileName = entryName.substringAfterLast("/")

                        when {
                            fileName.startsWith(IOLIB_PREFIX) -> {
                                val targetFile = File(iolibDir, fileName.removePrefix(IOLIB_PREFIX))
                                if (!targetFile.exists()) {
                                    targetFile.parentFile?.mkdirs()
                                    apkFile.getInputStream(entry).use { input ->
                                        targetFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    iolibCount++
                                }
                            }

                            fileName.startsWith(CAMLIB_PREFIX) -> {
                                val targetFile = File(camlibDir, fileName.removePrefix(CAMLIB_PREFIX))
                                if (!targetFile.exists()) {
                                    targetFile.parentFile?.mkdirs()
                                    apkFile.getInputStream(entry).use { input ->
                                        targetFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    camlibCount++
                                }
                            }
                        }
                    }
                } finally {
                    apkFile.close()
                }
            }

            Log.d(TAG, "플러그인 복사 완료: I/O=$iolibCount, Camera=$camlibCount")
            if (!arePluginsPresent()) {
                Log.e(TAG, "❌ 플러그인 추출 후에도 iolib/camlib 부재 (설치본 손상 또는 split 누락)")
            }
            return pluginDirArg()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 플러그인 디렉토리 생성 실패", e)
            return pluginDirArg()
        }
    }

    companion object {
        private const val TAG = "libgphoto2설치기"
        private const val PLUGIN_BASE_DIR = "gphoto2_plugins"
        private const val CAMLIB_SUBDIR = "libgphoto2/2.5.34"
        private const val IOLIB_SUBDIR = "libgphoto2_port/0.12.2"
        private const val IOLIB_PREFIX = "libgphoto2_port_iolib_"
        private const val CAMLIB_PREFIX = "libgphoto2_camlib_"
    }
}
