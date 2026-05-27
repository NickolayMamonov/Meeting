# ===== Сериализуемые типы :core:data =====
# Public cross-feature DTO (CommunityInfoDto, MeetingInfoDto, UserInfoDto, PersonDto,
# TagDto и т.д.).
#
# ВАЖНО: пакет com.whysoezzy.data.dto — split-package между :core:data и тремя
# :features:*/data модулями (см. roadmap, часть "Архитектурные размышления" →
# "Развести split-package"). До разрыва split-package одно правило здесь
# покрывает classes из всех 4 модулей.
-keep,includedescriptorclasses class com.whysoezzy.data.**$$serializer { *; }
-keepclassmembers class com.whysoezzy.data.** { *** Companion; }
-keepclasseswithmembers class com.whysoezzy.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}