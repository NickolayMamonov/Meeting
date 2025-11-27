# UIKit Blocks

Коллекция готовых UI блоков для использования в приложении.

## Доступные блоки

### UIKitAddressMapBlock

Блок с адресом и картой для отображения местоположения события.

**Основные параметры:**

- `address` - адрес места проведения
- `latitude`, `longitude` - координаты для карты
- `nearestMetro` - ближайшая станция метро (опционально)
- `onMapClick` - колбэк при клике на карту

### UIKitParticipantsBlock

Блок с информацией об участниках события.

**Основные параметры:**

- `participantAvatars` - список URL аватаров участников
- `participantCount` - общее количество участников
- `onParticipantsClick` - колбэк при клике на блок

### UIKitCommunityBlock

Блок с информацией о сообществе/организаторе.

**Основные параметры:**

- `communityName` - название сообщества
- `communityDescription` - описание сообщества
- `communityImageUrl` - URL изображения сообщества
- `onCommunityClick` - колбэк при клике на блок

### UIKitUserProfileBlock

Блок с основной информацией о пользователе для экрана профиля.

**Основные параметры:**

- `name`, `surname` - имя и фамилия пользователя
- `description` - описание/био пользователя
- `avatarUrl` - URL аватара пользователя (опционально)
- `avatarSize` - размер аватара в dp

**Пример использования:**

```kotlin
UIKitUserProfileBlock(
    name = "Иван",
    surname = "Петров",
    description = "Senior Android Developer",
    avatarUrl = "https://example.com/avatar.jpg"
)
```

### UIKitUserMeetingsBlock

Блок с горизонтальным списком встреч пользователя.

**Основные параметры:**

- `title` - заголовок блока (по умолчанию "Мои встречи")
- `meetings` - список встреч пользователя
- `onMeetingClick` - колбэк при клике на встречу

**Пример использования:**

```kotlin
UIKitUserMeetingsBlock(
    title = "Предстоящие встречи",
    meetings = userMeetings,
    onMeetingClick = { meetingId -> /* навигация к встрече */ }
)
```

### UIKitUserCommunitiesBlock

Блок с горизонтальным списком сообществ пользователя.

**Основные параметры:**

- `title` - заголовок блока (по умолчанию "Мои сообщества")
- `communities` - список сообществ пользователя
- `onCommunityClick` - колбэк при клике на сообщество

**Пример использования:**

```kotlin
UIKitUserCommunitiesBlock(
    title = "Подписки",
    communities = userCommunities,
    onCommunityClick = { communityId -> /* навигация к сообществу */ }
)
```

## Топбары

### ProfileTopBar

Топбар для экрана профиля с поддержкой разных режимов (собственный/чужой профиль).

**Основные параметры:**

- `title` - заголовок (обычно имя пользователя)
- `isOwnProfile` - является ли профиль собственным
- `onBackClick` - колбэк при клике на кнопку назад
- `onEditClick` - колбэк при клике на кнопку редактирования (только для собственного профиля)
- `onShareClick` - колбэк при клике на кнопку поделиться

**Пример использования:**

```kotlin
ProfileTopBar(
    title = "Иван Петров",
    isOwnProfile = true,
    onBackClick = { /* назад */ },
    onEditClick = { /* редактировать */ },
    onShareClick = { /* поделиться */ }
)
```

## Кастомизация

Все блоки поддерживают кастомизацию через параметры:

- Размеры изображений и аватаров
- Цвета фона
- Радиусы скругления
- Заголовки блоков
- Кастомные modifier'ы

## Использование в экране профиля

Пример полного экрана профиля:

```kotlin
@Composable
fun ProfileScreen(userId: Long?, isOwnProfile: Boolean) {
    LazyColumn {
        item {
            UIKitUserProfileBlock(
                name = "Иван",
                surname = "Петров",
                description = "Senior Android Developer",
                avatarUrl = "https://example.com/avatar.jpg"
            )
        }
        
        item {
            UIKitSocialMediaList(
                socialMedias = socialMedias,
                onSocialMediaClick = { url -> /* открыть ссылку */ }
            )
        }
        
        item {
            UIKitUserMeetingsBlock(
                meetings = meetings,
                onMeetingClick = { id -> /* навигация */ }
            )
        }
        
        item {
            UIKitUserCommunitiesBlock(
                communities = communities,
                onCommunityClick = { id -> /* навигация */ }
            )
        }
    }
}
```

## Структура данных

Блоки используют модели из domain слоя:

- `MeetingInfo` - информация о встрече
- `CommunityInfo` - информация о сообществе
- `SocialMediaInfo` - информация о социальных сетях

Подробности смотрите в документации к каждому компоненту.
