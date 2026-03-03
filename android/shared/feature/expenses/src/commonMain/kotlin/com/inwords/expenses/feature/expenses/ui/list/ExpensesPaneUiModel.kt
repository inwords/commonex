package com.inwords.expenses.feature.expenses.ui.list

import com.inwords.expenses.feature.events.ui.local.LocalEventsUiModel
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.ui.common.DebtShortUiModel
import kotlinx.collections.immutable.ImmutableList

internal sealed interface ExpensesPaneUiModel {

    data class Expenses(
        val eventId: Long,
        val eventName: String,
        val currentPersonId: Long,
        val currentPersonName: String,
        val debts: ImmutableList<DebtShortUiModel>,
        val expenses: ImmutableList<ExpenseUiModel>,
        val isRefreshing: Boolean,
    ) : ExpensesPaneUiModel {

        data class ExpenseUiModel(
            val expenseId: Long,
            val currencyText: String,
            val expenseType: ExpenseType,
            val personName: String,
            val isPaidByCurrentPerson: Boolean,
            val totalAmount: String,
            val timestamp: String,
            val description: String,
            val currentPersonPartAmount: String?,
        )
    }

    data class LocalEvents(
        val localEvents: LocalEventsUiModel
    ) : ExpensesPaneUiModel
}
