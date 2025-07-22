# UIKit Module

UIKit модуль содержит все переиспользуемые UI компоненты, дизайн-токены и темы для приложения Meet.

## Структура

```
uikit/
├── components/          # UI компоненты
│   ├── buttons/        # Кнопки
│   ├── text/           # Текстовые компоненты
│   ├── inputs/         # Поля ввода
│   └── avatars/        # Аватары
├── theme/              # Темы приложения
├── tokens/             # Дизайн-токены
└── preview/            # Preview компонентов
```

## Основные компоненты

### Текстовые компоненты
- `TextHeading1` - Основной заголовок
- `TextHeading2` - Вторичный заголовок
- `TextBody1` - Основной текст (SemiBold)
- `TextBody2` - Обычный текст

### Кнопки
- `UIKitButton` - Основная кнопка с состояниями:
  - `PRIMARY` - Основная кнопка
  - `SECONDARY` - Вторичная кнопка
  - `LOADING` - Состояние загрузки
  - `DISABLED` - Отключенная кнопка

### Поля ввода
- `UIKitInput` - Поле ввода с поддержкой:
  - Подсказок (hint)
  - Состояния ошибки
  - Кастомных опций клавиатуры
  - Визуальных трансформаций

### Аватары
- `UIKitAvatar` - Аватар с изображением
- `UIKitAvatarWithInitials` - Аватар с инициалами

## Дизайн-токены

### Цвета (`ColorTokens`)
- Brand Colors: `BrandDark`, `BrandDefault`, `BrandLight`, etc.
- Neutral Colors: `NeutralActive`, `NeutralBody`, `NeutralWeak`, etc.
- Accent Colors: `AccentDanger`, `AccentWarning`, `AccentSuccess`, etc.

### Отступы (`SpacingTokens`)
- `XS` (4dp), `S` (8dp), `M` (16dp), `L` (24dp), `XL` (32dp), etc.

### Радиусы (`BorderRadiusTokens`)
- `XS` (4dp), `S` (8dp), `M` (12dp), `L` (16dp), `XL` (24dp), `Round` (50dp)

### Типографика (`TypographyTokens`)
- Heading1, Heading2, Subheading1, Subheading2
- BodyText1, BodyText2
- Metadata1, Metadata2, Metadata3

## Использование

### Импорт темы
```kotlin
import dev.whysoezzy.uikit.theme.UIKitTheme

@Composable
fun MyScreen() {
    UIKitTheme {
        // Ваш контент
    }
}
```

### Использование компонентов
```kotlin
// Прямой импорт компонентов
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState

UIKitButton(
    text = "Click me",
    state = UIKitButtonState.PRIMARY,
    onClick = { /* обработчик */ }
)

// Или через состояния из UIKit
import dev.whysoezzy.uikit.UIKit

UIKitButton(
    text = "Click me", 
    state = UIKit.States.ButtonState.PRIMARY,
    onClick = { /* обработчик */ }
)
```

### Доступ к токенам
```kotlin
import dev.whysoezzy.uikit.UIKit

Modifier.padding(UIKit.Tokens.Spacing.M)
color = UIKit.Tokens.Colors.BrandDefault
```

### Доступ к теме в компонентах
```kotlin
import dev.whysoezzy.uikit.theme.UIKitTheme

@Composable
fun MyComponent() {
    val colors = UIKitTheme.colors
    val typography = UIKitTheme.typography
    
    Text(
        text = "Hello",
        style = typography.heading1,
        color = colors.brandDefault
    )
}
```

## Preview
Для просмотра всех компонентов используйте `UIKitShowcase` в preview режиме Android Studio.

## Принципы

1. **Atomic Design** - компоненты организованы от простых к сложным
2. **Design Tokens** - все значения цветов, отступов, шрифтов хранятся в токенах
3. **Consistency** - единый источник истины для всех UI элементов
4. **Accessibility** - поддержка accessibility из коробки
5. **Preview Support** - каждый компонент имеет preview функции