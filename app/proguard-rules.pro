# Читаемые stack trace в crash-репортах
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== kotlinx.serialization =====
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.whysoezzy.**$$serializer { *; }
-keepclassmembers class com.whysoezzy.** { *** Companion; }
-keepclasseswithmembers class com.whysoezzy.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.whysoezzy.**$$serializer { *; }
-keepclassmembers class dev.whysoezzy.** { *** Companion; }
-keepclasseswithmembers class dev.whysoezzy.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== Ktor =====
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ===== Koin =====
-keep class org.koin.** { *; }
-keepclassmembers class org.koin.** { *; }
-dontwarn org.koin.**

# ===== Kotlin =====
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }