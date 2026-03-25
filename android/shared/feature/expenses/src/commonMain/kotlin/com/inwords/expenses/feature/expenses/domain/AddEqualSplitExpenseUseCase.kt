package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlin.time.Clock

class AddEqualSplitExpenseUseCase internal constructor(
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
    expenseExchangeResolverLazy: Lazy<ExpenseExchangeResolver>,
) {

    private val expensesLocalStore by expensesLocalStoreLazy
    private val expenseExchangeResolver by expenseExchangeResolverLazy

    suspend fun addExpense(
        event: Event,
        wholeAmount: BigDecimal,
        expenseType: ExpenseType,
        description: String,
        selectedSubjectPersons: List<Person>,
        selectedCurrency: Currency,
        selectedPerson: Person,
    ) {
        val exchanger = expenseExchangeResolver.resolve(event, selectedCurrency) ?: return
        val originalAmount = EqualSplitCalculator.calculateStoredAmount(
            amount = wholeAmount,
            selectedSubjectPersonsSize = selectedSubjectPersons.size,
        )
        val subjectExpenseSplitWithPersons = selectedSubjectPersons.map { person ->
            ExpenseSplitWithPerson(
                expenseSplitId = 0,
                expenseId = 0,
                person = person,
                originalAmount = originalAmount,
                exchangedAmount = exchanger.invoke(originalAmount),
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
