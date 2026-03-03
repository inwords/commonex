package com.inwords.expenses.feature.expenses.ui.list.bottom_sheet.item

import com.inwords.expenses.feature.expenses.domain.model.Expense
import kotlinx.collections.immutable.ImmutableList

internal data class ExpenseItemPaneUiModel(
    val expense: Expense,
    val description: String,
    val totalAmount: String,
    val primaryCurrencyCode: String,
    val personName: String,
    val timestamp: String,
    val originalCurrencyCode: String,
    val originalCurrencyName: String,
    val exchangeRate: String?,
    val split: ImmutableList<PersonSplitUiModel>,
) {

    data class PersonSplitUiModel(
        val personName: String,
        val amount: String,
    )
}
