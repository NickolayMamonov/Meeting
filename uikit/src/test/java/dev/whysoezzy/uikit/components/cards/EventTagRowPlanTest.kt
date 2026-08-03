package dev.whysoezzy.uikit.components.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTagRowPlanTest {
    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun emptyOrZeroWidthIsOmitted() {
        assertEquals(
            EventTagRowPlan.Omitted,
            calculateEventTagRowPlan(100, 6, emptyList(), 10, emptyMap()),
        )
        assertEquals(
            EventTagRowPlan.Omitted,
            calculateEventTagRowPlan(0, 6, listOf(20), 10, mapOf(1 to 12)),
        )
    }

    @Test
    fun invalidMeasurementsFailFast() {
        assertIllegalArgument {
            calculateEventTagRowPlan(-1, 6, listOf(20), 10, mapOf(1 to 12))
        }
        assertIllegalArgument {
            calculateEventTagRowPlan(100, -1, listOf(20), 10, mapOf(1 to 12))
        }
        assertIllegalArgument {
            calculateEventTagRowPlan(100, 6, listOf(-1), 10, mapOf(1 to 12))
        }
        assertIllegalArgument {
            calculateEventTagRowPlan(100, 6, listOf(20), -1, mapOf(1 to 12))
        }
        assertIllegalArgument {
            calculateEventTagRowPlan(100, 6, listOf(20), 10, mapOf(1 to -1))
        }
        assertIllegalArgument {
            calculateEventTagRowPlan(100, 6, listOf(20, 20), 10, mapOf(1 to 12))
        }
    }

    @Test
    fun allVisibleFitsExactlyAndWithSpareWidth() {
        val exact = mapOf(1 to 20, 2 to 24)
        assertEquals(
            EventTagRowPlan.AllVisible(2),
            calculateEventTagRowPlan(56, 6, listOf(20, 30), 10, exact),
        )
        assertEquals(
            EventTagRowPlan.AllVisible(2),
            calculateEventTagRowPlan(100, 6, listOf(20, 30), 10, exact),
        )
    }

    @Test
    fun singleLongChipIsCappedButHasNoOverflow() {
        assertEquals(
            EventTagRowPlan.AllVisible(1),
            calculateEventTagRowPlan(100, 6, listOf(100), 20, mapOf(1 to 24)),
        )
    }

    @Test
    fun largestPrefixAndOnlyFinalChipCanShrink() {
        assertEquals(
            EventTagRowPlan.PrefixWithOverflow(1, 1, 19),
            calculateEventTagRowPlan(55, 6, listOf(40, 50), 12, mapOf(1 to 30, 2 to 40)),
        )
        assertEquals(
            EventTagRowPlan.PrefixWithOverflow(2, 1, 20),
            calculateEventTagRowPlan(60, 5, listOf(20, 24, 20), 8, mapOf(1 to 10, 2 to 20, 3 to 20)),
        )
    }

    @Test
    fun blankAndDuplicateSourceEntriesRemainCounted() {
        val widths = listOf(20, 0, 20)
        val result = calculateEventTagRowPlan(30, 5, widths, 10, mapOf(1 to 10, 2 to 10, 3 to 10))
        assertEquals(EventTagRowPlan.PrefixWithOverflow(1, 2, 15), result)
        assertEquals(listOf(20, 0, 20), widths)
        assertNotSame(widths, widths.toList())
    }

    @Test
    fun indicatorOnlyAndOmittedNeverUseTruncatedIndicator() {
        assertEquals(
            EventTagRowPlan.IndicatorOnly(2),
            calculateEventTagRowPlan(15, 5, listOf(20, 20), 20, mapOf(1 to 10, 2 to 15)),
        )
        assertEquals(
            EventTagRowPlan.Omitted,
            calculateEventTagRowPlan(15, 5, listOf(20, 20), 20, mapOf(1 to 10, 2 to 16)),
        )
    }

    @Test
    fun suppliedIndicatorWidthsHandleNineToTenBoundary() {
        val natural = List(10) { 10 }
        val narrowNineIndicator =
            mapOf(1 to 8, 2 to 8, 3 to 8, 4 to 8, 5 to 8, 6 to 8, 7 to 8, 8 to 8, 9 to 8, 10 to 16)
        val wideNineIndicator = narrowNineIndicator + (9 to 21)

        assertEquals(
            EventTagRowPlan.PrefixWithOverflow(1, 9, 10),
            calculateEventTagRowPlan(20, 2, natural, 6, narrowNineIndicator),
        )
        assertEquals(
            EventTagRowPlan.IndicatorOnly(10),
            calculateEventTagRowPlan(20, 2, natural, 6, wideNineIndicator),
        )
    }

    @Test
    fun boundaryAndLargeArithmeticRemainDeterministic() {
        val widths = listOf(Int.MAX_VALUE, Int.MAX_VALUE)
        val indicators = mapOf(1 to Int.MAX_VALUE, 2 to Int.MAX_VALUE)
        val first = calculateEventTagRowPlan(Int.MAX_VALUE, Int.MAX_VALUE, widths, 1, indicators)
        val second = calculateEventTagRowPlan(Int.MAX_VALUE, Int.MAX_VALUE, widths, 1, indicators)
        assertEquals(EventTagRowPlan.IndicatorOnly(2), first)
        assertEquals(first, second)
        assertTrue(first === second || first == second)
    }
}
