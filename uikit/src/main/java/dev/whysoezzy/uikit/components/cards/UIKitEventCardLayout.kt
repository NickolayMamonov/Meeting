package dev.whysoezzy.uikit.components.cards

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.whysoezzy.uikit.components.tags.UIKitTag
import dev.whysoezzy.uikit.components.tags.UIKitTagSize
import dev.whysoezzy.uikit.theme.UIKitTheme
import dev.whysoezzy.uikit.tokens.ColorTokens
import dev.whysoezzy.uikit.tokens.SpacingTokens
import dev.whysoezzy.uikit.tokens.TypographyTokens

internal data class EventCardMetrics(
    val preferredWidth: Dp,
    val preferredHeight: Dp,
    val preferredImageHeight: Dp,
) {
    companion object {
        fun forType(cardType: UIKitEventCardType): EventCardMetrics =
            when (cardType) {
                UIKitEventCardType.COMPACT -> EventCardMetrics(212.dp, 260.dp, 148.dp)
                UIKitEventCardType.WIDE -> EventCardMetrics(320.dp, 280.dp, 180.dp)
            }
    }
}

internal sealed interface EventTagRowPlan {
    data class AllVisible(
        val count: Int,
    ) : EventTagRowPlan

    data class PrefixWithOverflow(
        val visibleCount: Int,
        val hiddenCount: Int,
        val constrainedLastWidth: Int,
    ) : EventTagRowPlan

    data class IndicatorOnly(
        val hiddenCount: Int,
    ) : EventTagRowPlan

    data object Omitted : EventTagRowPlan
}

/**
 * Calculates the largest complete source prefix that can share a one-line row with a complete
 * overflow indicator. Widths are measured pixels; this function deliberately knows nothing about
 * strings, density, or Compose.
 */
internal fun calculateEventTagRowPlan(
    availableWidth: Int,
    horizontalGap: Int,
    naturalChipWidths: List<Int>,
    minimumReadableFinalChipWidth: Int,
    indicatorWidths: Map<Int, Int>,
): EventTagRowPlan {
    require(availableWidth >= 0) { "availableWidth must be non-negative" }
    require(horizontalGap >= 0) { "horizontalGap must be non-negative" }
    require(minimumReadableFinalChipWidth >= 0) { "minimumReadableFinalChipWidth must be non-negative" }
    require(naturalChipWidths.all { it >= 0 }) { "natural chip widths must be non-negative" }
    require(indicatorWidths.values.all { it >= 0 }) { "indicator widths must be non-negative" }

    if (naturalChipWidths.isEmpty() || availableWidth == 0) {
        return EventTagRowPlan.Omitted
    }

    val requiredIndicatorCounts = 1..naturalChipWidths.size
    require(indicatorWidths.keys.containsAll(requiredIndicatorCounts.toSet())) {
        "indicatorWidths must contain every hidden count from 1 through ${naturalChipWidths.size}"
    }

    val prefixSums = LongArray(naturalChipWidths.size + 1)
    naturalChipWidths.forEachIndexed { index, width ->
        prefixSums[index + 1] = prefixSums[index] + width.toLong()
    }

    fun gapCount(count: Int): Long = (count - 1).coerceAtLeast(0).toLong()

    val allWidth =
        prefixSums[naturalChipWidths.size] +
            gapCount(naturalChipWidths.size) * horizontalGap +
            0L
    if (allWidth <= availableWidth.toLong()) {
        return EventTagRowPlan.AllVisible(naturalChipWidths.size)
    }

    for (visibleCount in naturalChipWidths.size - 1 downTo 1) {
        val hiddenCount = naturalChipWidths.size - visibleCount
        val indicatorWidth = indicatorWidths.getValue(hiddenCount).toLong()
        val earlierCount = visibleCount - 1
        val earlierWidth =
            prefixSums[earlierCount] +
                gapCount(earlierCount) * horizontalGap
        val gapsAroundFinal =
            if (earlierCount > 0) {
                horizontalGap.toLong() * 2
            } else {
                horizontalGap.toLong()
            }
        val remainingForFinal =
            availableWidth.toLong() -
                earlierWidth -
                gapsAroundFinal -
                indicatorWidth
        if (remainingForFinal < 0) {
            continue
        }

        val naturalFinalWidth = naturalChipWidths[visibleCount - 1]
        val constrainedFinalWidth =
            minOf(
                naturalFinalWidth.toLong(),
                remainingForFinal,
            )
        if (naturalFinalWidth.toLong() <= remainingForFinal ||
            constrainedFinalWidth >= minimumReadableFinalChipWidth.toLong()
        ) {
            return EventTagRowPlan.PrefixWithOverflow(
                visibleCount = visibleCount,
                hiddenCount = hiddenCount,
                constrainedLastWidth = constrainedFinalWidth.toInt(),
            )
        }
    }

    val completeIndicatorWidth = indicatorWidths.getValue(naturalChipWidths.size)
    return if (completeIndicatorWidth <= availableWidth) {
        EventTagRowPlan.IndicatorOnly(naturalChipWidths.size)
    } else {
        EventTagRowPlan.Omitted
    }
}

private data class NaturalTagSlot(
    val index: Int,
)

private data class NaturalIndicatorSlot(
    val hiddenCount: Int,
)

private data class ConstrainedFinalTagSlot(
    val index: Int,
    val width: Int,
)

private object ReadabilityProbeSlot

private val EventTagRowHorizontalGap = 6.dp

private fun constrainDimension(
    preferred: Int,
    min: Int,
    max: Int,
): Int {
    val atLeastMinimum = preferred.coerceAtLeast(min)
    return if (max == Constraints.Infinity) atLeastMinimum else atLeastMinimum.coerceAtMost(max)
}

@Composable
internal fun BoundedEventTagRow(
    tags: List<UIKitEventCardTag>,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val tagGap = EventTagRowHorizontalGap.roundToPx()
        val rowWidth =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                constraints.minWidth
            }
        if (tags.isEmpty() || rowWidth == 0) {
            return@SubcomposeLayout layout(
                width = constrainDimension(rowWidth, constraints.minWidth, constraints.maxWidth),
                height = constrainDimension(0, constraints.minHeight, constraints.maxHeight),
            ) {}
        }

        val naturalTagMeasurables =
            tags.mapIndexed { index, tag ->
                subcompose(NaturalTagSlot(index)) {
                    UIKitTag(
                        text = tag.text,
                        size = UIKitTagSize.SMALL,
                        selected = tag.isSelected,
                        enabled = tag.isEnabled,
                    )
                }.single()
            }
        val naturalTagPlaceables =
            naturalTagMeasurables.map { measurable ->
                measurable.measure(
                    Constraints(
                        minWidth = 0,
                        maxWidth = rowWidth,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity,
                    ),
                )
            }

        val indicatorPlaceables =
            (1..tags.size).associateWith { hiddenCount ->
                subcompose(NaturalIndicatorSlot(hiddenCount)) {
                    UIKitTag(
                        text = "+$hiddenCount",
                        size = UIKitTagSize.SMALL,
                    )
                }.single().measure(
                    Constraints(
                        minWidth = 0,
                        maxWidth = Constraints.Infinity,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity,
                    ),
                )
            }
        val readableFinalChipWidth =
            subcompose(ReadabilityProbeSlot) {
                UIKitTag(text = "…", size = UIKitTagSize.SMALL)
            }.single()
                .measure(
                    Constraints(
                        minWidth = 0,
                        maxWidth = Constraints.Infinity,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity,
                    ),
                ).width

        val plan =
            calculateEventTagRowPlan(
                availableWidth = rowWidth,
                horizontalGap = tagGap,
                naturalChipWidths = naturalTagPlaceables.map { it.width },
                minimumReadableFinalChipWidth = readableFinalChipWidth,
                indicatorWidths = indicatorPlaceables.mapValues { it.value.width },
            )

        val constrainedFinal =
            when (plan) {
                is EventTagRowPlan.PrefixWithOverflow -> {
                    val index = plan.visibleCount - 1
                    subcompose(ConstrainedFinalTagSlot(index, plan.constrainedLastWidth)) {
                        UIKitTag(
                            text = tags[index].text,
                            size = UIKitTagSize.SMALL,
                            selected = tags[index].isSelected,
                            enabled = tags[index].isEnabled,
                        )
                    }.single().measure(
                        Constraints(
                            minWidth = plan.constrainedLastWidth,
                            maxWidth = plan.constrainedLastWidth,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity,
                        ),
                    )
                }

                else -> null
            }

        val placed =
            when (plan) {
                is EventTagRowPlan.AllVisible ->
                    naturalTagPlaceables.mapIndexed { index, placeable ->
                        placeable to index
                    } + emptyList()

                is EventTagRowPlan.PrefixWithOverflow -> {
                    val realTags =
                        naturalTagPlaceables
                            .take(plan.visibleCount - 1)
                            .mapIndexed { index, placeable -> placeable to index } +
                            listOf(constrainedFinal!! to (plan.visibleCount - 1))
                    realTags + listOf(indicatorPlaceables.getValue(plan.hiddenCount) to -1)
                }

                is EventTagRowPlan.IndicatorOnly ->
                    listOf(indicatorPlaceables.getValue(plan.hiddenCount) to -1)

                EventTagRowPlan.Omitted -> emptyList()
            }

        val naturalHeight = placed.maxOfOrNull { it.first.height } ?: 0
        val heightTooTight =
            constraints.hasBoundedHeight && naturalHeight > constraints.maxHeight
        val height =
            if (heightTooTight) {
                constrainDimension(0, constraints.minHeight, constraints.maxHeight)
            } else {
                constrainDimension(naturalHeight, constraints.minHeight, constraints.maxHeight)
            }
        layout(
            width = constrainDimension(rowWidth, constraints.minWidth, constraints.maxWidth),
            height = height,
        ) {
            if (heightTooTight) {
                return@layout
            }
            var x = 0
            placed.forEachIndexed { placedIndex, (placeable, _) ->
                placeable.placeRelative(x, 0)
                x += placeable.width
                if (placedIndex != placed.lastIndex) {
                    x += tagGap
                }
            }
        }
    }
}

internal data class EventCardBodyConfiguration(
    val titleLines: Int,
    val includeMetadata: Boolean,
    val includeTags: Boolean,
)

private data class TitleSlot(
    val lines: Int,
)

private object MetadataSlot

private object TagsSlot

private object ImageSlot

@Composable
internal fun MeasuredEventCardLayout(
    imageUrl: String,
    title: String,
    date: String,
    address: dev.whysoezzy.uikit.models.UIKitAddress,
    tags: List<UIKitEventCardTag>,
    metrics: EventCardMetrics,
    modifier: Modifier = Modifier,
) {
    val colorScheme = UIKitTheme.colors
    val metadata = "$date · ${address.address}"
    SubcomposeLayout(modifier = modifier) { constraints ->
        val cardWidth =
            constrainDimension(
                metrics.preferredWidth.roundToPx(),
                constraints.minWidth,
                constraints.maxWidth,
            )
        val cardHeight =
            constrainDimension(
                metrics.preferredHeight.roundToPx(),
                constraints.minHeight,
                constraints.maxHeight,
            )
        val textConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = cardWidth,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            )

        val titleTwoLines =
            subcompose(TitleSlot(2)) {
                Text(
                    text = title,
                    style = TypographyTokens.BodyText1,
                    color = colorScheme.neutralBody,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }.single().measure(textConstraints)
        val titleOneLine =
            subcompose(TitleSlot(1)) {
                Text(
                    text = title,
                    style = TypographyTokens.BodyText1,
                    color = colorScheme.neutralBody,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }.single().measure(textConstraints)
        val metadataPlaceable =
            subcompose(MetadataSlot) {
                Text(
                    text = metadata,
                    style = TypographyTokens.Metadata1,
                    color = colorScheme.neutralWeak,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }.single().measure(textConstraints)
        val tagsPlaceable =
            if (tags.isNotEmpty()) {
                subcompose(TagsSlot) {
                    BoundedEventTagRow(
                        tags = tags,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }.single().measure(
                    Constraints(
                        minWidth = cardWidth,
                        maxWidth = cardWidth,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity,
                    ),
                )
            } else {
                null
            }

        fun blockHeight(
            titlePlaceable: Placeable?,
            includeMetadata: Boolean,
            includeTags: Boolean,
        ): Int {
            val heights =
                buildList {
                    titlePlaceable?.let { add(it.height) }
                    if (includeMetadata) add(metadataPlaceable.height)
                    if (includeTags) tagsPlaceable?.let { add(it.height) }
                }
            return heights.sum() + (heights.size - 1).coerceAtLeast(0) * SpacingTokens.S.roundToPx()
        }

        var titlePlaceable: Placeable? = titleTwoLines
        var includeMetadata = true
        var includeTags = tagsPlaceable?.height?.let { it > 0 } == true
        if (blockHeight(titlePlaceable, includeMetadata, includeTags) > cardHeight) {
            includeTags = false
            if (blockHeight(titlePlaceable, includeMetadata, includeTags) > cardHeight) {
                titlePlaceable = titleOneLine
                if (blockHeight(titlePlaceable, includeMetadata, includeTags) > cardHeight) {
                    includeMetadata = false
                    if (blockHeight(titlePlaceable, includeMetadata, includeTags) > cardHeight) {
                        titlePlaceable = null
                    }
                }
            }
        }

        val bodyHeight = blockHeight(titlePlaceable, includeMetadata, includeTags)
        val sectionGap = SpacingTokens.S.roundToPx()
        val preferredImageHeight = metrics.preferredImageHeight.roundToPx()
        val imageHeight =
            if (titlePlaceable == null && !includeMetadata && !includeTags) {
                minOf(preferredImageHeight, cardHeight)
            } else {
                val remainingWithImageGap = cardHeight - bodyHeight - sectionGap
                if (remainingWithImageGap > 0) {
                    minOf(preferredImageHeight, remainingWithImageGap)
                } else {
                    0
                }
            }
        val imagePlaceable =
            if (imageHeight > 0) {
                subcompose(ImageSlot) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier,
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(ColorTokens.NeutralLine),
                        error = ColorPainter(ColorTokens.NeutralLine),
                    )
                }.single().measure(Constraints.fixed(cardWidth, imageHeight))
            } else {
                null
            }

        layout(cardWidth, cardHeight) {
            var y = 0
            if (imagePlaceable != null) {
                imagePlaceable.placeRelative(0, y)
                y += imagePlaceable.height
            }
            val bodyPlaceables =
                buildList {
                    titlePlaceable?.let { add(it) }
                    if (includeMetadata) add(metadataPlaceable)
                    if (includeTags) tagsPlaceable?.let { add(it) }
                }
            if (imagePlaceable != null && bodyPlaceables.isNotEmpty()) {
                y += sectionGap
            }
            bodyPlaceables.forEachIndexed { index, placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
                if (index != bodyPlaceables.lastIndex) {
                    y += sectionGap
                }
            }
        }
    }
}
