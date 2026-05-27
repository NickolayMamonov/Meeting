# Читаемые stack trace в crash-репортах
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== Koin =====
# Koin использует рефлексию для definition resolution во всех модулях с module { }.
# Семантически принадлежит каждому DI-модулю, но прагматично оставляем в :app
# как единую точку DI runtime'а (избегаем дублирования в 11 consumer-rules).
-keep class org.koin.** { *; }
-keepclassmembers class org.koin.** { *; }
-dontwarn org.koin.**

# ===== Kotlin runtime =====
# Общая поддержка kotlin-stdlib (метаданные, lazy delegate, when-mappings).
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# ===== kotlinx.coroutines =====
# :core:common — java-library без consumer-rules; coroutines используются повсеместно,
# поэтому правила живут в :app.
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ===== Библиотечные правила =====
# kotlinx.serialization / Ktor / DTO-namespace'ы — в consumer-rules модулей:
#   :core:network         → Ktor + ErrorResponse serializer + kotlinx.serialization base
#   :core:auth            → com.whysoezzy.auth.data.dto.**$$serializer
#   :core:data            → com.whysoezzy.data.dto.**$$serializer (split-package owner)
#   :features:*/data      → ничего (split-package с :core:data покрывает)