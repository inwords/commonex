package com.inwords.expenses.feature.expenses.ui.converter

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

internal class ExpenseToUiModelTest {

    @Test
    fun `toUiModel should include current person part from exchanged amount`() {
        val currentPerson = Person(id = 1, serverId = "p1", name = "Alex", clientCreateId = "person-1")
        val anotherPerson = Person(id = 2, serverId = "p2", name = "Ben", clientCreateId = "person-2")

        val expense = Expense(
            expenseId = 10L,
            serverId = "e1",
            currency = Currency(id = 1, serverId = "c1", code = "USD", name = "US Dollar", rate = BigDecimal.ONE),
            expenseType = ExpenseType.Spending,
            person = currentPerson,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(
                    expenseSplitId = 1,
                    expenseId = 10L,
                    person = currentPerson,
                    originalAmount = 50.toBigDecimal(),
                    exchangedAmount = 45.toBigDecimal(),
                ),
                ExpenseSplitWithPerson(
                    expenseSplitId = 2,
                    expenseId = 10L,
                    person = anotherPerson,
                    originalAmount = 70.toBigDecimal(),
                    exchangedAmount = 63.toBigDecimal(),
                )
            ),
            isCustomRate = false,
            timestamp = Clock.System.now(),
            description = "Lunch",
            clientCreateId = "expense-10",
        )

        val uiModel = expense.toUiModel(primaryCurrencyCode = "EUR", currentPersonId = currentPerson.id)

        assertEquals("EUR", uiModel.currencyCode)
        assertEquals("-108", uiModel.totalAmount)
        assertEquals("120 USD", uiModel.originalAmount)
        assertEquals("-45", uiModel.currentPersonPartAmount)
        assertTrue(uiModel.timeText.isNotBlank())
    }

    @Test
    fun `toUiModel should hide current person part when current person is not in split`() {
        val expenseOwner = Person(id = 1, serverId = "p1", name = "Alex", clientCreateId = "person-1")
        val splitPerson = Person(id = 2, serverId = "p2", name = "Ben", clientCreateId = "person-2")

        val expense = Expense(
            expenseId = 11L,
            serverId = "e2",
            currency = Currency(id = 1, serverId = "c1", code = "USD", name = "US Dollar", rate = BigDecimal.ONE),
            expenseType = ExpenseType.Spending,
            person = expenseOwner,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(
                    expenseSplitId = 3,
                    expenseId = 11L,
                    person = splitPerson,
                    originalAmount = 40.toBigDecimal(),
                    exchangedAmount = 40.toBigDecimal(),
                )
            ),
            isCustomRate = false,
            timestamp = Clock.System.now(),
            description = "Taxi",
            clientCreateId = "expense-11",
        )

        val uiModel = expense.toUiModel(primaryCurrencyCode = "USD", currentPersonId = 999L)

        assertEquals("USD", uiModel.currencyCode)
        assertNull(uiModel.originalAmount)
        assertNull(uiModel.currentPersonPartAmount)
    }
}
