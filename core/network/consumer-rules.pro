# ===== kotlinx.serialization (база) =====
# Эти правила нужны всем модулям, использующим @Serializable.
# :core:network — api-владелец kotlinx-serialization-json, поэтому база здесь.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== Сериализуемые типы :core:network =====
-keep,includedescriptorclasses class com.whysoezzy.network.**$$serializer { *; }
-keepclassmembers class com.whysoezzy.network.** { *** Companion; }
-keepclasseswithmembers class com.whysoezzy.network.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== Ktor =====
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**