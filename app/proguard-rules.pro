# 앱별 프로가드 규칙
# build.gradle의 proguardFiles 설정에서 이 파일을 참조

# 디버깅을 위한 소스 파일과 라인 번호 정보 유지
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Signature, Annotation 등 중요한 메타데이터 유지
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

# === Firebase 기본 규칙 ===
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# === Google Sign-In 구체적 규칙 ===
# Google Sign-In API 클래스들 보호
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.auth.api.credentials.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }

# Google Sign-In 관련 예외 및 상태 코드 클래스 보호
-keep class com.google.android.gms.common.api.ApiException { *; }
-keep class com.google.android.gms.common.api.Status { *; }
-keep class com.google.android.gms.common.ConnectionResult { *; }

# Firebase Auth 관련 클래스들
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.auth.GoogleAuthProvider { *; }
-keep class com.google.firebase.auth.FirebaseAuth { *; }
-keep class com.google.firebase.auth.FirebaseUser { *; }

# Google Play Services Tasks API
-keep class com.google.android.gms.tasks.** { *; }

# JSON Web Token 관련 클래스들 (Google Sign-In에서 사용)
#-keep class com.google.api.client.json.** { *; }
#-keep class com.google.api.client.util.** { *; }

# Gson이 사용하는 Google Sign-In 관련 모델 클래스들
#-keep class * extends com.google.api.client.json.GenericJson { *; }
#-keep class * extends com.google.api.client.util.GenericData { *; }

# === Hilt/Dagger 기본 규칙 ===
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class **_HiltComponents$* { *; }
-keep class **_Hilt* { *; }

# === Compose 기본 규칙 ===
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.runtime.** { *; }

# ModifierLocalProvider 충돌 방지
-keep interface androidx.compose.ui.modifier.ModifierLocalProvider { *; }
-keep class androidx.compose.ui.modifier.ModifierLocalProvider$DefaultImpls { *; }

# Compose 컴파일러 생성 코드 보호
-keep class **$Companion { *; }
-keepclassmembers class ** {
    *** Companion;
}

# Lambda 표현식 보호
-keepclassmembers class ** {
    private static synthetic *** lambda$*(...);
}

# Compose State 관련
-keepclassmembers class androidx.compose.** { *; }

# === 앱 도메인 모델 유지 ===
-keep class com.inik.camcon.data.model.** { *; }
-keep class com.inik.camcon.domain.model.** { *; }

# === Gson 직렬화 규칙 ===
-keepattributes *Annotation*
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# === 일반적인 최적화 방지 규칙 ===
# Enum 클래스 유지
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Native 메소드 유지
-keepclasseswithmembernames class * {
    native <methods>;
}

# === JNI 인터페이스 및 콜백 보호 ===
# CameraNative 객체와 모든 메서드 유지
-keep class com.inik.camcon.CameraNative { *; }

# JNI 콜백 인터페이스들 완전 보호
-keep interface com.inik.camcon.NativeErrorCallback { *; }
-keep interface com.inik.camcon.CameraCleanupCallback { *; }
-keep interface com.inik.camcon.data.datasource.nativesource.CameraCaptureListener { *; }
-keep interface com.inik.camcon.data.datasource.nativesource.LiveViewCallback { *; }

# JNI 콜백을 구현하는 모든 클래스의 콜백 메서드 보호
-keep class ** implements com.inik.camcon.NativeErrorCallback { 
    public void onNativeError(int, java.lang.String);
}
-keep class ** implements com.inik.camcon.CameraCleanupCallback { 
    public void onCleanupComplete(boolean, java.lang.String);
}
-keep class ** implements com.inik.camcon.data.datasource.nativesource.CameraCaptureListener { 
    public void onPhotoCaptured(java.lang.String, java.lang.String);
}
-keep class ** implements com.inik.camcon.data.datasource.nativesource.LiveViewCallback { 
    public void onLiveViewFrame(byte[]);
    public void onLiveViewError(java.lang.String);
}

# 익명 클래스와 람다로 생성된 콜백들도 보호
-keepclassmembers class ** {
    public void onNativeError(int, java.lang.String);
    public void onCleanupComplete(boolean, java.lang.String);
    public void onPhotoCaptured(java.lang.String, java.lang.String);
    public void onLiveViewFrame(byte[]);
    public void onLiveViewError(java.lang.String);
}

# === 경고 무시 ===
-dontwarn java.lang.invoke.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**