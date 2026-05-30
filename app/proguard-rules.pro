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
# kotlinx.serialization / Ktor — в consumer-rules модулей:
#   :core:network → Ktor + conditional @Serializable-правила (R-045). Пакет-агностичны,
#                   покрывают все DTO во всех модулях одной копией (consumer-rules
#                   мерджатся в единый R8-ран). Имена DTO и $$serializer обфусцируются.