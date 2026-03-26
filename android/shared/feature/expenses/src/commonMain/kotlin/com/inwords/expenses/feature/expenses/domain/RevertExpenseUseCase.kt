package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import kotlin.time.Clock

internal class RevertExpenseUseCase internal constructor(
    eventsLocalStoreLazy: Lazy<EventsLocalStore>,
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
) {

    private val eventsLocalStore by eventsLocalStoreLazy
    private val expensesLocalStore by expensesLocalStoreLazy

    suspend fun revertExpense(eventId: Long, expenseId: Long, description: String): Boolean {
        val event = eventsLocalStore.getEvent(eventId) ?: return false
        val originalExpense = expensesLocalStore.getExpense(expenseId) ?: return false
        val revertedExpense = Expense(
            expenseId = 0,
            serverId = null,
            currency = originalExpense.currency,
            expenseType = when (originalExpense.expenseType) {
                ExpenseType.Spending -> ExpenseType.Replenishment
                ExpenseType.Replenishment -> ExpenseType.Spending
            },
            person = originalExpense.person,
            subjectExpenseSplitWithPersons = originalExpense.subjectExpenseSplitWithPersons.map { split ->
                ExpenseSplitWithPerson(
                    expenseSplitId = 0,
                    expenseId = 0,
                    person = split.person,
                    originalAmount = split.originalAmount.negate(),
                    exchangedAmount = split.exchangedAmount.negate(),
                )
            },
            isCustomRate = originalExpense.isCustomRate,
            timestamp = Clock.System.now(),
            description = description,
        )

        expensesLocalStore.upsert(event, revertedExpense)

        return true
    }
}
