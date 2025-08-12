# CamCon - R8/ProGuard rules (lean)

# 빌드 성능 최적화
-dontoptimize

# ---- [선택] 디버깅 편의용 심볼 ----
# 릴리스 크기/속도가 중요하면 아래 두 줄을 주석 처리하세요.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- 리플렉션/직렬화를 위한 메타데이터 ----
-keepattributes *Annotation*,Signature,RuntimeVisibleAnnotations,AnnotationDefault

# ---- 앱 DTO/도메인 모델 (Gson 반사 사용 시 필수) ----
-keep class com.inik.camcon.data.model.** { *; }
-keep class com.inik.camcon.domain.model.** { *; }

# ---- Gson 어댑터/직렬화자 ----
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ---- Enum 기본 멤버 보존(일반적 안전장치) ----
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Native/JNI ----
-keepclasseswithmembernames class * { native <methods>; }

# CamCon JNI 브리지
-keep class com.inik.camcon.CameraNative { *; }

# 콜백 인터페이스
-keep interface com.inik.camcon.NativeErrorCallback { *; }
-keep interface com.inik.camcon.CameraCleanupCallback { *; }
-keep interface com.inik.camcon.data.datasource.nativesource.CameraCaptureListener { *; }
-keep interface com.inik.camcon.data.datasource.nativesource.LiveViewCallback { *; }

# 콜백 구현체의 필수 메서드 시그니처 보존
-keepclassmembers class ** implements com.inik.camcon.NativeErrorCallback {
    public void onNativeError(int, java.lang.String);
}
-keepclassmembers class ** implements com.inik.camcon.CameraCleanupCallback {
    public void onCleanupComplete(boolean, java.lang.String);
}
-keepclassmembers class ** implements com.inik.camcon.data.datasource.nativesource.CameraCaptureListener {
    public void onPhotoCaptured(java.lang.String, java.lang.String);
}
-keepclassmembers class ** implements com.inik.camcon.data.datasource.nativesource.LiveViewCallback {
    public void onLiveViewFrame(byte[]);
    public void onLiveViewError(java.lang.String);
}

# ---- 경고 무시 ----
-dontwarn java.lang.invoke.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- [옵션] Google Sign-In/Firebase 이슈 시 최소 보강 ----
# 대부분의 Google/Firebase 라이브러리는 consumer-rules를 포함하므로 불필요합니다.
# 특정 런타임 이슈가 있을 때만 필요한 최소 범위만 주석 해제하세요.
#-keep class com.google.android.gms.common.api.ApiException { *; }
#-keep class com.google.android.gms.common.api.Status { *; }
#-keep class com.google.android.gms.auth.api.identity.SignInClient { *; }
#-keep class com.google.android.gms.tasks.Task { *; }