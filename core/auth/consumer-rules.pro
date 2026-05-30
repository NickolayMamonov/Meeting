# ===== Tink (DataStore token store, R-044) =====
# tink-android поставляет собственные consumer-rules в AAR; здесь — только
# подавление warning'ов по опциональным зависимостям, которые Tink не тянет.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# ===== Сериализуемые типы :core:auth =====
# Правила kotlinx.serialization вынесены в :core:network/consumer-rules.pro
# (conditional @Serializable, пакет-агностичны). Дублировать здесь не нужно.