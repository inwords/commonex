package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal

class AddEqualSplitExpenseUseCase internal constructor(
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
    expenseDraftFactoryLazy: Lazy<ExpenseDraftFactory>,
) {

    private val expensesLocalStore by expensesLocalStoreLazy
    private val expenseDraftFactory by expenseDraftFactoryLazy

    suspend fun addExpense(
        event: Event,
        wholeAmount: BigDecimal,
        expenseType: ExpenseType,
        description: String,
        selectedSubjectPersons: List<Person>,
        selectedCurrency: Currency,
        selectedPerson: Person,
        overrideRate: BigDecimal?,
    ): Boolean {
        val exchanger = expenseDraftFactory.resolveExchanger(event, selectedCurrency, overrideRate) ?: return false
        val subjectExpenseSplitWithPersons = expenseDraftFactory.buildEqualSplit(
            wholeAmount = wholeAmount,
            selectedSubjectPersons = selectedSubjectPersons,
            exchanger = exchanger,
        )

        expensesLocalStore.upsert(
            event = event,
            expense = expenseDraftFactory.createExpense(
                currency = selectedCurrency,
                expenseType = expenseType,
                person = selectedPerson,
                subjectExpenseSplitWithPersons = subjectExpenseSplitWithPersons,
                isCustomRate = overrideRate != null,
                description = description,
            ),
        )

        return true
    }
}
