package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.foundation.lazy.LazyListItemInfo

/**
 * Resolves which timeline day should be considered "current" from the visible lazy-list rows.
 */
internal object ExpensesTimelineVisibleDayResolver {

    /**
     * Static day-section layout derived from the same structure emitted by [ExpensesPane]'s list.
     */
    data class Layout(
        val orderedDayKeys: List<String>,
        val orderedDayHeaderIndices: List<Int>,
        val dayHeaderIndexByDayKey: Map<String, Int>,
    )

    fun currentDayKey(
        visibleItems: List<LazyListItemInfo>,
        layout: Layout,
        excludedItemIndex: Int?,
        stickyDayChipsHeightPx: Int,
        hasStickyDayChipsOverlay: Boolean,
    ): String? {
        if (layout.orderedDayKeys.isEmpty()) {
            return null
        }
        val anchor = anchorItemIndex(
            visibleItems = visibleItems,
            excludedItemIndex = excludedItemIndex,
            stickyDayChipsHeightPx = stickyDayChipsHeightPx,
            hasStickyDayChipsOverlay = hasStickyDayChipsOverlay,
        ) ?: return null
        return layout.dayKeyForAnchor(anchor)
    }

    /**
     * The semantic timeline anchor is the content line directly below the pinned chips row
     * (when present), or the top content line otherwise. This avoids treating a partially visible
     * tail item from the previous day as the current section when a day header has already landed
     * below the pinned chips.
     *
     * Non-content controls such as the inline day-chips row can be ignored through
     * [excludedItemIndex].
     *
     * [LazyListItemInfo.offset] is relative to the lazy list container. With top content padding, the
     * first content line is at `0`, while items visible inside the padding area can have negative
     * offsets.
     */
    private fun anchorItemIndex(
        visibleItems: List<LazyListItemInfo>,
        excludedItemIndex: Int?,
        stickyDayChipsHeightPx: Int,
        hasStickyDayChipsOverlay: Boolean,
    ): Int? {
        if (visibleItems.isEmpty()) {
            return null
        }
        val anchorOffset = when {
            stickyDayChipsHeightPx <= 0 -> 0
            hasStickyDayChipsOverlay -> stickyDayChipsHeightPx
            else -> 0
        }
        var topmostVisibleIndex: Int? = null
        var topmostVisibleOffset = Int.MAX_VALUE
        var nextBelowAnchorIndex: Int? = null
        var nextBelowAnchorOffset = Int.MAX_VALUE
        for (item in visibleItems) {
            if (item.index == excludedItemIndex) {
                continue
            }
            if (item.offset < topmostVisibleOffset) {
                topmostVisibleOffset = item.offset
                topmostVisibleIndex = item.index
            }
            if (item.offset <= anchorOffset && anchorOffset < item.offset + item.size) {
                return item.index
            }
            if (item.offset in anchorOffset until nextBelowAnchorOffset) {
                nextBelowAnchorOffset = item.offset
                nextBelowAnchorIndex = item.index
            }
        }
        return nextBelowAnchorIndex ?: topmostVisibleIndex
    }

    /**
     * Maps a lazy item index (the scroll anchor row) to the calendar day that should drive chip
     * selection.
     */
    private fun Layout.dayKeyForAnchor(anchorItemIndex: Int): String? {
        val headerIndex = orderedDayHeaderIndices.binarySearch(anchorItemIndex)
        val dayIndex = if (headerIndex >= 0) {
            headerIndex
        } else {
            (-headerIndex - 2).coerceAtLeast(0)
        }
        return orderedDayKeys.getOrNull(dayIndex)
    }
}
