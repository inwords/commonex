package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.expenses.domain.model.ExpensesDetails
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class GetExpensesDetailsUseCase internal constructor(
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
) {
    private val expensesLocalStore by expensesLocalStoreLazy

    fun getExpensesDetails(eventDetails: EventDetails): Flow<ExpensesDetails> {
        return expensesLocalStore.getExpensesFlow(eventDetails.event.id)
            .map { expenses ->
                ExpensesDetails(
                    event = eventDetails,
                    expenses = expenses,
                    debtCalculator = DebtCalculator(expenses, eventDetails.primaryCurrency),
                )
            }
    }
}
