package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ExpensesPaneTimelineAnchorTest {

    @Test
    fun `currentVisibleTimelineDayKey prefers header aligned below sticky chips`() {
        val currentDayKey = currentDayKey(
            visibleItems = listOf(
                TestLazyListItemInfo(index = 11, offset = 0, size = 56),
                TestLazyListItemInfo(index = 12, offset = 56, size = 40),
                TestLazyListItemInfo(index = 13, offset = 96, size = 48),
            ),
            stickyDayChipsHeightPx = 56,
            hasStickyDayChipsOverlay = true,
        )

        assertEquals("2025-07-07", currentDayKey)
    }

    @Test
    fun `currentVisibleTimelineDayKey keeps previous day when next header is only lower in viewport`() {
        val currentDayKey = currentDayKey(
            visibleItems = listOf(
                TestLazyListItemInfo(index = 11, offset = 0, size = 56),
                TestLazyListItemInfo(index = 12, offset = 96, size = 40),
                TestLazyListItemInfo(index = 13, offset = 136, size = 48),
            ),
            stickyDayChipsHeightPx = 56,
            hasStickyDayChipsOverlay = false,
        )

        assertEquals("2025-06-06", currentDayKey)
    }

    @Test
    fun `currentVisibleTimelineDayKey ignores excluded day chips row`() {
        val currentDayKey = currentDayKey(
            visibleItems = listOf(
                TestLazyListItemInfo(index = 3, offset = 0, size = 56),
                TestLazyListItemInfo(index = 11, offset = 0, size = 56),
                TestLazyListItemInfo(index = 12, offset = 56, size = 40),
            ),
            excludedItemIndex = 3,
            stickyDayChipsHeightPx = 56,
            hasStickyDayChipsOverlay = true,
        )

        assertEquals("2025-07-07", currentDayKey)
    }

    @Test
    fun `currentVisibleTimelineDayKey returns null for empty layout`() {
        val currentDayKey = currentDayKey(
            layout = ExpensesTimelineVisibleDayResolver.Layout(
                orderedDayKeys = emptyList(),
                orderedDayHeaderIndices = emptyList(),
                dayHeaderIndexByDayKey = emptyMap(),
            ),
            visibleItems = listOf(
                TestLazyListItemInfo(index = 11, offset = 0, size = 56),
            ),
        )

        assertEquals(null, currentDayKey)
    }

    @Test
    fun `currentVisibleTimelineDayKey returns null for empty visible items`() {
        val currentDayKey = currentDayKey(visibleItems = emptyList())

        assertEquals(null, currentDayKey)
    }

    @Test
    fun `currentVisibleTimelineDayKey returns first day before first header`() {
        val currentDayKey = currentDayKey(
            visibleItems = listOf(
                TestLazyListItemInfo(index = 8, offset = 0, size = 40),
                TestLazyListItemInfo(index = 9, offset = 40, size = 40),
            ),
        )

        assertEquals("2025-06-06", currentDayKey)
    }

    @Test
    fun `currentVisibleTimelineDayKey returns matching day when anchor hits header exactly`() {
        val currentDayKey = currentDayKey(
            visibleItems = listOf(
                TestLazyListItemInfo(index = 12, offset = 0, size = 40),
                TestLazyListItemInfo(index = 13, offset = 40, size = 48),
            ),
            stickyDayChipsHeightPx = 56,
            hasStickyDayChipsOverlay = false,
        )

        assertEquals("2025-07-07", currentDayKey)
    }

    @Test
    fun `currentVisibleTimelineDayKey uses next item below anchor when anchor is in a gap`() {
        val currentDayKey = currentDayKey(
            visibleItems = listOf(
                TestLazyListItemInfo(index = 11, offset = 0, size = 40),
                TestLazyListItemInfo(index = 12, offset = 80, size = 40),
                TestLazyListItemInfo(index = 13, offset = 120, size = 48),
            ),
            stickyDayChipsHeightPx = 56,
            hasStickyDayChipsOverlay = true,
        )

        assertEquals("2025-07-07", currentDayKey)
    }

    @Test
    fun `currentVisibleTimelineDayKey returns null when only excluded item is visible`() {
        val currentDayKey = currentDayKey(
            visibleItems = listOf(
                TestLazyListItemInfo(index = 3, offset = 0, size = 56),
            ),
            excludedItemIndex = 3,
            stickyDayChipsHeightPx = 56,
            hasStickyDayChipsOverlay = true,
        )

        assertEquals(null, currentDayKey)
    }

    private fun currentDayKey(
        visibleItems: List<TestLazyListItemInfo>,
        layout: ExpensesTimelineVisibleDayResolver.Layout = testLayout(),
        excludedItemIndex: Int? = null,
        stickyDayChipsHeightPx: Int = 56,
        hasStickyDayChipsOverlay: Boolean = false,
    ): String? {
        val visibleLayoutInfo = TestLazyListLayoutInfo(visibleItemsInfo = visibleItems)
        return ExpensesTimelineVisibleDayResolver.currentDayKey(
            visibleItems = visibleLayoutInfo.visibleItemsInfo,
            layout = layout,
            excludedItemIndex = excludedItemIndex,
            stickyDayChipsHeightPx = stickyDayChipsHeightPx,
            hasStickyDayChipsOverlay = hasStickyDayChipsOverlay,
        )
    }

    private fun testLayout(): ExpensesTimelineVisibleDayResolver.Layout {
        return ExpensesTimelineVisibleDayResolver.Layout(
            orderedDayKeys = listOf("2025-06-06", "2025-07-07"),
            orderedDayHeaderIndices = listOf(10, 12),
            dayHeaderIndexByDayKey = mapOf(
                "2025-06-06" to 10,
                "2025-07-07" to 12,
            ),
        )
    }

    private data class TestLazyListLayoutInfo(
        override val visibleItemsInfo: List<LazyListItemInfo>,
        override val viewportStartOffset: Int = 0,
        override val viewportEndOffset: Int = 1_000,
        override val totalItemsCount: Int = 20,
        override val viewportSize: IntSize = IntSize(width = 1_000, height = 1_000),
        override val orientation: Orientation = Orientation.Vertical,
        override val reverseLayout: Boolean = false,
        override val beforeContentPadding: Int = 0,
        override val afterContentPadding: Int = 0,
        override val mainAxisItemSpacing: Int = 0,
    ) : LazyListLayoutInfo

    private data class TestLazyListItemInfo(
        override val index: Int,
        override val offset: Int,
        override val size: Int,
        override val key: Any = index,
        override val contentType: Any? = null,
    ) : LazyListItemInfo
}
