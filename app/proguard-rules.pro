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

# === Hilt/Dagger 기본 규칙 ===
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class **_HiltComponents$* { *; }
-keep class **_Hilt* { *; }

# === Compose 기본 규칙 ===
-keep @androidx.compose.runtime.Stable class * { *; }

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

# === 경고 무시 ===
-dontwarn java.lang.invoke.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**