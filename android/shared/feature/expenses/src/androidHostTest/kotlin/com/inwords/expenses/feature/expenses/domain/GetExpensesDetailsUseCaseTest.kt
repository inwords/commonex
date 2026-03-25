package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

internal class GetExpensesDetailsUseCaseTest {

    @Test
    fun `getExpensesDetails maps store expenses into ExpensesDetails`() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", currency.id)
        val alice = Person(1L, null, "Alice")
        val bob = Person(2L, null, "Bob")
        val eventDetails = EventDetails(
            event = event,
            currencies = listOf(currency),
            persons = listOf(alice, bob),
            primaryCurrency = currency,
        )
        val expense = Expense(
            expenseId = 77L,
            serverId = null,
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = alice,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(1L, 77L, alice, 10.toBigDecimal(), 10.toBigDecimal()),
                ExpenseSplitWithPerson(2L, 77L, bob, 10.toBigDecimal(), 10.toBigDecimal()),
            ),
            timestamp = Instant.fromEpochMilliseconds(0),
            description = "Lunch",
        )
        val expensesLocalStore = mockk<ExpensesLocalStore> {
            every { getExpensesFlow(event.id) } returns flowOf(listOf(expense))
        }

        val result = GetExpensesDetailsUseCase(
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
        ).getExpensesDetails(eventDetails).first()

        assertEquals(eventDetails, result.event)
        assertEquals(listOf(expense), result.expenses)
        assertEquals(1, result.debtCalculator.barterAccumulatedDebtSummaries.size)
        assertEquals("Bob", result.debtCalculator.barterAccumulatedDebtSummaries.single().debtor.name)
        assertEquals("Alice", result.debtCalculator.barterAccumulatedDebtSummaries.single().creditor.name)
    }
}
