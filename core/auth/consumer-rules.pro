# androidx.security.crypto — EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ===== Сериализуемые типы :core:auth =====
# Запросы/ответы auth API (OTP, токены).
-keep,includedescriptorclasses class com.whysoezzy.auth.**$$serializer { *; }
-keepclassmembers class com.whysoezzy.auth.** { *** Companion; }
-keepclasseswithmembers class com.whysoezzy.auth.** {
    kotlinx.serialization.KSerializer serializer(...);
}