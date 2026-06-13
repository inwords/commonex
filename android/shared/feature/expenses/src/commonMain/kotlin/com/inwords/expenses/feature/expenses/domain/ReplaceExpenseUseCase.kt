package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.utils.currencyRateScale
import com.inwords.expenses.core.utils.divide
import com.inwords.expenses.core.utils.sumOf
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.model.PersonWithAmount
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal

internal class ReplaceExpenseUseCase internal constructor(
    expensesLocalStoreLazy: Lazy<ExpensesLocalStore>,
    expenseDraftFactoryLazy: Lazy<ExpenseDraftFactory>,
) {

    private val expensesLocalStore by expensesLocalStoreLazy
    private val expenseDraftFactory by expenseDraftFactoryLazy

    suspend fun replaceEqualSplitExpense(
        event: Event,
        originalExpenseId: Long,
        wholeAmount: BigDecimal,
        expenseType: ExpenseType,
        description: String,
        selectedSubjectPersons: List<Person>,
        selectedCurrency: Currency,
        selectedPerson: Person,
        overrideRate: BigDecimal?,
    ): Boolean {
        val originalExpense = expensesLocalStore.getExpense(originalExpenseId) ?: return false
        if (expensesLocalStore.hasCorrectionFor(originalExpenseId)) return false
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
                replacesExpenseId = originalExpense.expenseId,
            ),
        )

        return true
    }

    suspend fun replaceCustomSplitExpense(
        event: Event,
        originalExpenseId: Long,
        expenseType: ExpenseType,
        description: String,
        selectedCurrency: Currency,
        selectedPerson: Person,
        personWithAmountSplit: List<PersonWithAmount>,
        overrideRate: BigDecimal?,
    ): Boolean {
        val originalExpense = expensesLocalStore.getExpense(originalExpenseId) ?: return false
        if (expensesLocalStore.hasCorrectionFor(originalExpenseId)) return false
        val exchanger = expenseDraftFactory.resolveExchanger(event, selectedCurrency, overrideRate) ?: return false
        val requestedSplit = expenseDraftFactory.buildCustomSplit(
            personWithAmountSplit = personWithAmountSplit,
            exchanger = exchanger,
        )
        val preservesOriginalExchange = originalExpense.canPreserveExchangeValues(
            expenseType = expenseType,
            selectedCurrency = selectedCurrency,
            selectedPerson = selectedPerson,
            requestedSplit = requestedSplit,
            overrideRate = overrideRate,
        )
        val subjectExpenseSplitWithPersons = if (preservesOriginalExchange) {
            requestedSplit.map { requested ->
                requested.copy(
                    exchangedAmount = originalExpense.subjectExpenseSplitWithPersons
                        .first { original -> original.person.id == requested.person.id }
                        .exchangedAmount,
                )
            }
        } else {
            requestedSplit
        }

        expensesLocalStore.upsert(
            event = event,
            expense = expenseDraftFactory.createExpense(
                currency = selectedCurrency,
                expenseType = expenseType,
                person = selectedPerson,
                subjectExpenseSplitWithPersons = subjectExpenseSplitWithPersons,
                isCustomRate = if (preservesOriginalExchange) originalExpense.isCustomRate else overrideRate != null,
                description = description,
                replacesExpenseId = originalExpense.expenseId,
            ),
        )

        return true
    }

    private fun Expense.canPreserveExchangeValues(
        expenseType: ExpenseType,
        selectedCurrency: Currency,
        selectedPerson: Person,
        requestedSplit: List<ExpenseSplitWithPerson>,
        overrideRate: BigDecimal?,
    ): Boolean {
        if (
            this.expenseType != expenseType ||
            currency.id != selectedCurrency.id ||
            person.id != selectedPerson.id ||
            subjectExpenseSplitWithPersons.size != requestedSplit.size
        ) {
            return false
        }
        val originalSplitsByPersonId = subjectExpenseSplitWithPersons.associateBy { it.person.id }
        if (requestedSplit.any { requested ->
                originalSplitsByPersonId[requested.person.id]?.originalAmount
                    ?.compareTo(requested.originalAmount) != 0
            }
        ) {
            return false
        }
        if (overrideRate == null) {
            return !isCustomRate
        }
        val originalAmount = subjectExpenseSplitWithPersons.sumOf { it.originalAmount }
        if (originalAmount == BigDecimal.ZERO) return false
        val effectiveRate = subjectExpenseSplitWithPersons.sumOf { it.exchangedAmount }
            .divide(other = originalAmount, scale = currencyRateScale)
        return overrideRate.compareTo(effectiveRate) == 0
    }
}
