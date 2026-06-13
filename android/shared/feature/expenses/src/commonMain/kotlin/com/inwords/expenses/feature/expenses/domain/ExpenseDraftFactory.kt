package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.utils.ClientCreateIdGenerator
import com.inwords.expenses.core.utils.normalizeAmount
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.model.PersonWithAmount
import com.ionspin.kotlin.bignum.decimal.BigDecimal

internal class ExpenseDraftFactory internal constructor(
    expenseExchangeResolverLazy: Lazy<ExpenseExchangeResolver>,
    clientCreateIdGeneratorLazy: Lazy<ClientCreateIdGenerator>,
) {

    private val expenseExchangeResolver by expenseExchangeResolverLazy
    private val clientCreateIdGenerator by clientCreateIdGeneratorLazy

    suspend fun resolveExchanger(
        event: Event,
        selectedCurrency: Currency,
        overrideRate: BigDecimal?,
    ): ((BigDecimal) -> BigDecimal)? {
        return overrideRate?.let { rate ->
            { amount: BigDecimal -> (amount * rate).normalizeAmount() }
        } ?: expenseExchangeResolver.resolve(event, selectedCurrency)
    }

    fun buildEqualSplit(
        wholeAmount: BigDecimal,
        selectedSubjectPersons: List<Person>,
        exchanger: (BigDecimal) -> BigDecimal,
    ): List<ExpenseSplitWithPerson> {
        val originalAmount = EqualSplitCalculator.calculateStoredAmount(
            amount = wholeAmount,
            selectedSubjectPersonsSize = selectedSubjectPersons.size,
        )
        return selectedSubjectPersons.map { person ->
            ExpenseSplitWithPerson(
                expenseSplitId = 0,
                expenseId = 0,
                person = person,
                originalAmount = originalAmount,
                exchangedAmount = exchanger.invoke(originalAmount),
            )
        }
    }

    fun buildCustomSplit(
        personWithAmountSplit: List<PersonWithAmount>,
        exchanger: (BigDecimal) -> BigDecimal,
    ): List<ExpenseSplitWithPerson> {
        return personWithAmountSplit.map { personWithAmount ->
            ExpenseSplitWithPerson(
                expenseSplitId = 0,
                expenseId = 0,
                person = personWithAmount.person,
                originalAmount = personWithAmount.amount,
                exchangedAmount = exchanger.invoke(personWithAmount.amount),
            )
        }
    }

    fun createExpense(
        currency: Currency,
        expenseType: ExpenseType,
        person: Person,
        subjectExpenseSplitWithPersons: List<ExpenseSplitWithPerson>,
        isCustomRate: Boolean,
        description: String,
        revertsExpenseId: Long? = null,
        replacesExpenseId: Long? = null,
    ): Expense {
        return Expense(
            expenseId = 0,
            serverId = null,
            clientCreateId = clientCreateIdGenerator.generate(),
            currency = currency,
            expenseType = expenseType,
            person = person,
            subjectExpenseSplitWithPersons = subjectExpenseSplitWithPersons,
            isCustomRate = isCustomRate,
            timestamp = ExpenseTimeBackdoor.now(),
            description = description,
            revertsExpenseId = revertsExpenseId,
            replacesExpenseId = replacesExpenseId,
        )
    }
}
