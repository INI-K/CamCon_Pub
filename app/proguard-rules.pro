# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ========== CamCon 네이티브 라이브러리 보호 규칙 ==========

# CameraNative 클래스와 모든 네이티브 메서드 보호
-keep class com.inik.camcon.CameraNative {
    public static <methods>;
    native <methods>;
}

# 네이티브 메서드를 가진 모든 클래스 보호
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# JNI 콜백 인터페이스들 보호
-keep class com.inik.camcon.NativeErrorCallback {
    public <methods>;
}

-keep class com.inik.camcon.CameraCleanupCallback {
    public <methods>;
}

# 카메라 관련 콜백 클래스들 보호
-keep class com.inik.camcon.data.datasource.nativesource.CameraCaptureListener {
    public <methods>;
}

-keep class com.inik.camcon.data.datasource.nativesource.LiveViewCallback {
    public <methods>;
}

# 네이티브에서 접근하는 데이터 클래스들 보호
-keep class com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource$** {
    *;
}

# Hilt 관련 보호 (네이티브 데이터소스 DI)
-keep class com.inik.camcon.data.datasource.nativesource.NativeCameraDataSource {
    public <init>(...);
    public <methods>;
}

# 시스템 로드 라이브러리 메서드 보호
-keep class java.lang.System {
    public static void loadLibrary(java.lang.String);
    public static void load(java.lang.String);
}

# 네이티브 라이브러리에서 사용되는 Android API 보호
-keep class android.content.Context {
    public java.io.File getFilesDir();
    public android.content.pm.ApplicationInfo getApplicationInfo();
}

# JSON 파싱 관련 보호 (네이티브에서 JSON 반환)
-keep class org.json.** {
    public <methods>;
}

# 바이트 배열 관련 보호 (이미지 데이터 전송)
-keep class ** {
    public byte[] *(...);
}

# 로그 관련 보호
-keep class android.util.Log {
    public static int d(java.lang.String, java.lang.String);
    public static int e(java.lang.String, java.lang.String);
    public static int e(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int w(java.lang.String, java.lang.String);
    public static int w(java.lang.String, java.lang.String, java.lang.Throwable);
}

# 스레드 관련 보호 (네이티브 콜백에서 사용)
-keep class java.lang.Thread {
    public <init>(java.lang.Runnable);
    public void start();
    public static void sleep(long);
}

# 원자적 연산 클래스 보호 (동기화에 사용)
-keep class java.util.concurrent.atomic.AtomicBoolean {
    public <methods>;
}

# 뮤텍스 관련 보호
-keep class kotlinx.coroutines.sync.Mutex {
    public <methods>;
}

# 네이티브 라이브러리 빌드 관련 보호
-keep class com.inik.camcon.** {
    public <init>(...);
}

# ExceptionInInitializerError 방지를 위한 정적 초기화 블록 보호
-keepclassmembers class com.inik.camcon.CameraNative {
    <clinit>();
}

# ========== 릴리즈 빌드 최적화 방지 ==========

# 네이티브 라이브러리 관련 패키지는 최적화하지 않음
-keep,allowobfuscation class com.inik.camcon.data.datasource.nativesource.**

# 사용하지 않는 것처럼 보이는 메서드들도 보호 (네이티브에서 호출될 수 있음)
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# 디버그 정보 유지 (릴리즈 모드 디버깅용)
-keepattributes SourceFile,LineNumberTable,*Annotation*

# 내부 클래스 이름 유지 (네이티브 콜백에서 필요)
-keepattributes InnerClasses,EnclosingMethod