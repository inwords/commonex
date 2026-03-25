package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.model.PersonWithAmount
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import kotlin.time.Clock

internal class AddCustomSplitExpenseUseCase internal constructor(
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
    expenseExchangeResolverLazy: Lazy<ExpenseExchangeResolver>,
) {

    private val expensesLocalStore by expensesLocalStoreLazy
    private val expenseExchangeResolver by expenseExchangeResolverLazy

    suspend fun addExpense(
        event: Event,
        expenseType: ExpenseType,
        description: String,
        selectedCurrency: Currency,
        selectedPerson: Person,
        personWithAmountSplit: List<PersonWithAmount>,
    ) {
        val exchanger = expenseExchangeResolver.resolve(event, selectedCurrency) ?: return
        val subjectExpenseSplitWithPersons = personWithAmountSplit.map { personWithAmount ->
            ExpenseSplitWithPerson(
                expenseSplitId = 0,
                expenseId = 0,
                person = personWithAmount.person,
                originalAmount = personWithAmount.amount,
                exchangedAmount = exchanger.invoke(personWithAmount.amount),
            )
        }

        expensesLocalStore.upsert(
            event = event,
            expense = Expense(
                expenseId = 0,
                serverId = null,
                currency = selectedCurrency,
                expenseType = expenseType,
                person = selectedPerson,
                subjectExpenseSplitWithPersons = subjectExpenseSplitWithPersons,
                timestamp = Clock.System.now(),
                description = description,
            ),
        )
    }
}
