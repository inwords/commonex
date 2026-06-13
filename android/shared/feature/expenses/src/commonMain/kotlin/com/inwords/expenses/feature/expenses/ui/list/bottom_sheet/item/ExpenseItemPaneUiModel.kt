package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import kotlinx.collections.immutable.ImmutableList

internal data class ExpenseItemPaneUiModel(
    val expenseId: Long,
    val description: String,
    val totalAmount: String,
    val primaryCurrencyCode: String,
    val personName: String,
    val timestamp: String,
    val originalCurrencyCode: String,
    val originalCurrencyName: String,
    val exchangeRate: String?,
    val split: ImmutableList<PersonSplitUiModel>,
    val canCorrect: Boolean,
    val statusText: String?,
) {

    data class PersonSplitUiModel(
        val personName: String,
        val amount: String,
    )
}
