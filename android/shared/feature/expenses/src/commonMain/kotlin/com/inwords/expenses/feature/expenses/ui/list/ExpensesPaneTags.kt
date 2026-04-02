package com.inwords.expenses.feature.expenses.ui.list

object ExpensesPaneTags {

    const val TOP_APP_BAR = "expenses_top_app_bar"
    const val MENU_BUTTON = "expenses_menu_button"
    const val TIMELINE_LIST = "expenses_timeline_list"
    const val TOTAL_SPENDING_VALUE = "expenses_total_spending_value"
    const val DAY_CHIPS_ROW = "expenses_day_chips_row"
    const val DAY_CHIP_PREFIX = "expenses_day_chip_"

    private const val DAY_HEADER_PREFIX = "expenses_day_header_"
    private const val DAY_HEADER_TOTAL_PREFIX = "expenses_day_header_total_"
    private const val TIMELINE_DAY_HEADER_KEY_PREFIX = "expenses_timeline_day_header_"

    fun dayChip(dayKey: String): String = "$DAY_CHIP_PREFIX$dayKey"
    fun dayHeader(dayKey: String): String = "$DAY_HEADER_PREFIX$dayKey"
    fun dayHeaderTotal(dayKey: String): String = "$DAY_HEADER_TOTAL_PREFIX$dayKey"
    fun timelineDayHeaderKey(dayKey: String): String = "$TIMELINE_DAY_HEADER_KEY_PREFIX$dayKey"
}
