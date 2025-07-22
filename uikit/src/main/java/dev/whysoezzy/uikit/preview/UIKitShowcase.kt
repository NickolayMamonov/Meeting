package dev.whysoezzy.uikit.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.whysoezzy.uikit.components.avatars.UIKitAvatar
import dev.whysoezzy.uikit.components.avatars.UIKitAvatarWithInitials
import dev.whysoezzy.uikit.components.buttons.UIKitButton
import dev.whysoezzy.uikit.components.buttons.UIKitButtonState
import dev.whysoezzy.uikit.components.buttons.UIKitSubscribeButton
import dev.whysoezzy.uikit.components.cards.UIKitCommunityCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCard
import dev.whysoezzy.uikit.components.cards.UIKitEventCardTag
import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
import dev.whysoezzy.uikit.components.cards.UIKitPersonCard
import dev.whysoezzy.uikit.components.dividers.UIKitDivider
import dev.whysoezzy.uikit.components.inputs.UIKitInput
import dev.whysoezzy.uikit.components.layouts.UIKitOverlappingAvatars
import dev.whysoezzy.uikit.components.search.UIKitSearchBar
import dev.whysoezzy.uikit.components.search.UIKitSearchField
import dev.whysoezzy.uikit.components.tags.UIKitTag
import dev.whysoezzy.uikit.components.tags.UIKitTagGroup
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.components.text.TextBody1
import dev.whysoezzy.uikit.components.text.TextBody2
import dev.whysoezzy.uikit.components.text.TextHeading1
import dev.whysoezzy.uikit.components.text.TextHeading2
import dev.whysoezzy.uikit.components.toggles.UIKitToggle
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.SpacingTokens

@Composable
fun UIKitShowcase() {
    UIKitTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SpacingTokens.M)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.L)
        ) {
            // Typography Section
            TextHeading1(text = "UIKit Showcase")

            TextSection()

            HorizontalDivider()

            ButtonSection()

            HorizontalDivider()

            InputSection()

            HorizontalDivider()

            SearchSection()

            HorizontalDivider()

            ToggleSection()

            HorizontalDivider()

            TagSection()

            HorizontalDivider()

            CardSection()

            HorizontalDivider()

            LayoutSection()

            HorizontalDivider()

            AvatarSection()
        }
    }
}

@Composable
fun CardSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Cards")

        TextBody1(text = "Event Cards")
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            UIKitEventCard(
                imageUrl = "https://picsum.photos/212/148",
                title = "Android Meetup",
                date = "10 августа",
                address = "Кожевенная линия, 40",
                tags = listOf(
                    UIKitEventCardTag("Android", true),
                    UIKitEventCardTag("Kotlin")
                ),
                cardType = UIKitEventCardType.COMPACT
            )
        }

        TextBody1(text = "Community Cards")
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            var isSubscribed1 by remember { mutableStateOf(false) }
            UIKitCommunityCard(
                imageUrl = "https://picsum.photos/104/104",
                title = "Design",
                isSubscribed = isSubscribed1,
                onSubscribeClick = { isSubscribed1 = it }
            )

            UIKitCommunityCard(
                imageUrl = "https://picsum.photos/104/104",
                title = "Android Dev",
                isSubscribed = true,
                onSubscribeClick = { }
            )
        }

        TextBody1(text = "Person Cards")
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            UIKitPersonCard(
                name = "John Doe",
                role = "Designer",
                imageUrl = "https://picsum.photos/100/100?random=1"
            )

            UIKitPersonCard(
                name = "Jane Smith",
                role = "Developer",
                imageUrl = "https://picsum.photos/100/100?random=2"
            )
        }
    }
}

@Composable
fun LayoutSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Layouts")

        TextBody1(text = "Overlapping Avatars")
        val sampleAvatars = listOf(
            "https://picsum.photos/100/100?random=1",
            "https://picsum.photos/100/100?random=2",
            "https://picsum.photos/100/100?random=3",
            "https://picsum.photos/100/100?random=4",
            "https://picsum.photos/100/100?random=5",
            "https://picsum.photos/100/100?random=6"
        )

        UIKitOverlappingAvatars(
            avatarUrls = sampleAvatars
        )

        TextBody1(text = "Dividers")
        Column {
            TextBody2(text = "Section 1")
            UIKitDivider()
            TextBody2(text = "Section 2")
        }
    }
}

@Composable
fun SearchSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Search Components")

        var searchText1 by remember { mutableStateOf("") }
        UIKitSearchField(
            value = searchText1,
            onValueChange = { searchText1 = it },
            placeholder = "Search users..."
        )

        var searchText2 by remember { mutableStateOf("Sample query") }
        UIKitSearchField(
            value = searchText2,
            onValueChange = { searchText2 = it },
            placeholder = "Search events..."
        )

        UIKitSearchBar(
            placeholder = "Search in app...",
            onSearch = { }
        )
    }
}

@Composable
fun ToggleSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Toggles")

        var toggle1 by remember { mutableStateOf(false) }
        var toggle2 by remember { mutableStateOf(true) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIKitToggle(
                checked = toggle1,
                onCheckedChange = { toggle1 = it }
            )
            TextBody2(text = "Enable notifications")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIKitToggle(
                checked = toggle2,
                onCheckedChange = { toggle2 = it }
            )
            TextBody2(text = "Dark mode")
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIKitToggle(
                checked = true,
                onCheckedChange = { },
                enabled = false
            )
            TextBody2(text = "Disabled toggle")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Tags")

        TextBody1(text = "Different sizes")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.S),
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
        ) {
            UIKitTag(
                text = "Small",
                size = UIKitTagSize.SMALL
            )
            UIKitTag(
                text = "Medium",
                size = UIKitTagSize.MEDIUM
            )
            UIKitTag(
                text = "Large",
                size = UIKitTagSize.LARGE
            )
        }

        TextBody1(text = "Interactive tags")
        var selectedTags by remember {
            mutableStateOf(setOf("Android", "Design"))
        }

        UIKitTagGroup(
            tags = listOf("Android", "iOS", "Design", "Backend", "Frontend", "ML"),
            selectedTags = selectedTags,
            size = UIKitTagSize.MEDIUM,
            onTagClick = { tag ->
                selectedTags = if (selectedTags.contains(tag)) {
                    selectedTags - tag
                } else {
                    selectedTags + tag
                }
            }
        )
    }
}


@Composable
fun TextSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.S)
    ) {
        TextHeading2(text = "Typography")

        TextHeading1(text = "Heading 1")
        TextHeading2(text = "Heading 2")
        TextBody1(text = "Body Text 1 - SemiBold")
        TextBody2(text = "Body Text 2 - Normal")
    }
}

@Composable
fun ButtonSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Buttons")

        UIKitButton(
            text = "Primary Button",
            state = UIKitButtonState.PRIMARY
        )

        UIKitButton(
            text = "Secondary Button",
            state = UIKitButtonState.SECONDARY
        )

        UIKitButton(
            text = "Loading",
            state = UIKitButtonState.LOADING
        )

        UIKitButton(
            text = "Disabled Button",
            state = UIKitButtonState.DISABLED
        )

        TextBody1(text = "Subscribe Buttons")

        Row(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
        ) {
            var subscribed1 by remember { mutableStateOf(false) }
            UIKitSubscribeButton(
                selected = subscribed1,
                onSelectedChange = { subscribed1 = it }
            )

            UIKitSubscribeButton(
                selected = true,
                onSelectedChange = { }
            )
        }
    }
}

@Composable
fun InputSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Input Fields")

        var text1 by remember { mutableStateOf("") }
        UIKitInput(
            value = text1,
            onValueChange = { text1 = it },
            hint = "Enter your name"
        )

        var text2 by remember { mutableStateOf("Filled input") }
        UIKitInput(
            value = text2,
            onValueChange = { text2 = it },
            hint = "Hint text"
        )

        var text3 by remember { mutableStateOf("Invalid input") }
        UIKitInput(
            value = text3,
            onValueChange = { text3 = it },
            hint = "Enter text",
            isError = true,
            errorMessage = "This field is required"
        )
    }
}

@Composable
fun AvatarSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
    ) {
        TextHeading2(text = "Avatars")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIKitAvatar(
                imageUrl = "https://picsum.photos/200",
                size = SpacingTokens.XL
            )

            UIKitAvatar(
                imageUrl = "",
                size = SpacingTokens.XL
            )

            UIKitAvatarWithInitials(
                initials = "AB",
                size = SpacingTokens.XL
            )

            UIKitAvatarWithInitials(
                initials = "John Doe",
                size = SpacingTokens.L
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UIKitShowcasePreview() {
    UIKitShowcase()
}