//package dev.whysoezzy.features_meetings.data
//
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.lazy.LazyRow
//import androidx.compose.foundation.lazy.items
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.tooling.preview.Preview
//import dev.whysoezzy.uikit.components.cards.UIKitCommunityCard
//import dev.whysoezzy.uikit.components.cards.UIKitEventCard
//import dev.whysoezzy.uikit.components.cards.UIKitEventCardType
//import dev.whysoezzy.uikit.components.text.TextHeading2
//import dev.whysoezzy.uikit.theme.UIKitTheme
//import dev.whysoezzy.uikit.tokens.SpacingTokens
//
///**
// * Preview компоненты с использованием mock данных
// */
//object MockPreviews {
//
//    @Composable
//    fun HeroEventsPreview() {
//        UIKitTheme {
//            LazyRow(
//                contentPadding = PaddingValues(SpacingTokens.M),
//                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//            ) {
//                items(MockData.heroEvents.take(3)) { event ->
//                    UIKitEventCard(
//                        imageUrl = event.imageUrl,
//                        title = event.title,
//                        subtitle = event.description,
//                        tags = event.tags.map { it.text },
//                        cardType = UIKitEventCardType.WIDE,
//                        onCardClick = { }
//                    )
//                }
//            }
//        }
//    }
//
//    @Composable
//    fun PopularEventsPreview() {
//        UIKitTheme {
//            Column(
//                modifier = Modifier.padding(SpacingTokens.M),
//                verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//            ) {
//                TextHeading2(text = "Популярные события")
//
//                LazyRow(
//                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//                ) {
//                    items(MockData.popularEvents) { event ->
//                        UIKitEventCard(
//                            imageUrl = event.imageUrl,
//                            title = event.title,
//                            subtitle = event.description,
//                            tags = event.tags.map { it.name },
//                            cardType = UIKitEventCardType.COMPACT,
//                            onCardClick = { }
//                        )
//                    }
//                }
//            }
//        }
//    }
//
//    @Composable
//    @Preview(showBackground = true, name = "Communities")
//    fun CommunitiesPreview() {
//        UIKitTheme {
//            Column(
//                modifier = Modifier.padding(SpacingTokens.M),
//                verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//            ) {
//                TextHeading2(text = "Рекомендуемые сообщества")
//
//                LazyRow(
//                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//                ) {
//                    items(MockData.recommendedCommunities) { community ->
//                        UIKitCommunityCard(
//                            imageUrl = community.imageUrl,
//                            title = community.title,
//                            isSubscribed = false,
//                            onSubscribeClick = { },
//                            onCardClick = { }
//                        )
//                    }
//                }
//            }
//        }
//    }
//
//    @Composable
//    @Preview(showBackground = true, name = "All Events")
//    fun AllEventsPreview() {
//        UIKitTheme {
//            Column(
//                modifier = Modifier.padding(SpacingTokens.M),
//                verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//            ) {
//                TextHeading2(text = "Все события")
//
//                MockData.allEvents.take(3).forEach { event ->
//                    UIKitEventCard(
//                        imageUrl = event.imageUrl,
//                        title = event.title,
//                        subtitle = event.description,
//                        tags = event.tags.map { it.name },
//                        cardType = UIKitEventCardType.WIDE,
//                        onCardClick = { },
//                        modifier = Modifier.fillMaxWidth()
//                    )
//                }
//            }
//        }
//    }
//
//    @Composable
//    @Preview(showBackground = true, name = "Ad Blocks")
//    fun AdBlocksPreview() {
//        UIKitTheme {
//            Column(
//                modifier = Modifier.padding(SpacingTokens.M),
//                verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//            ) {
//                MockData.adBlocks.forEach { adBlock ->
//                    AdBlockComponentNew(
//                        adBlock = adBlock,
//                        onAdClick = { },
//                        onCommunitySubscribe = { _, _ -> }
//                    )
//                }
//            }
//        }
//    }
//
//    @Composable
//    @Preview(showBackground = true, name = "Event Card Types")
//    fun EventCardTypesPreview() {
//        UIKitTheme {
//            Column(
//                modifier = Modifier.padding(SpacingTokens.M),
//                verticalArrangement = Arrangement.spacedBy(SpacingTokens.M)
//            ) {
//                val sampleEvent = MockData.heroEvents.first()
//
//                TextHeading2(text = "Wide Card")
//                UIKitEventCard(
//                    imageUrl = sampleEvent.imageUrl,
//                    title = sampleEvent.title,
//                    subtitle = sampleEvent.description,
//                    tags = sampleEvent.tags.map { it.name },
//                    cardType = UIKitEventCardType.WIDE,
//                    onCardClick = { }
//                )
//
//                TextHeading2(text = "Compact Card")
//                UIKitEventCard(
//                    imageUrl = sampleEvent.imageUrl,
//                    title = sampleEvent.title,
//                    subtitle = sampleEvent.description,
//                    tags = sampleEvent.tags.map { it.name },
//                    cardType = UIKitEventCardType.COMPACT,
//                    onCardClick = { }
//                )
//            }
//        }
//    }
//}