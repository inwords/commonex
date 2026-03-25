package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import kotlinx.coroutines.flow.Flow

class GetExpensesUseCase internal constructor(
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
) {
    private val expensesLocalStore by expensesLocalStoreLazy

    fun getExpensesFlow(eventId: Long): Flow<List<Expense>> {
        return expensesLocalStore.getExpensesFlow(eventId)
    }
}
