package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

internal class RevertExpenseUseCaseTest {

    @Test
    fun `revertExpense returns false when event is missing`() = runTest {
        val eventsLocalStore = mockk<EventsLocalStore>()
        val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)

        coEvery { eventsLocalStore.getEvent(10L) } returns null

        val result = RevertExpenseUseCase(
            eventsLocalStoreLazy = lazyOf(eventsLocalStore),
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
        ).revertExpense(
            eventId = 10L,
            expenseId = 20L,
            description = "Revert",
        )

        assertFalse(result)
        coVerify(exactly = 0) { expensesLocalStore.upsert(any(), any<Expense>()) }
    }

    @Test
    fun `revertExpense stores mirrored expense when source exists`() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", currency.id)
        val alice = Person(1L, null, "Alice")
        val bob = Person(2L, null, "Bob")
        val originalExpense = Expense(
            expenseId = 20L,
            serverId = null,
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = alice,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(1L, 20L, bob, 5.toBigDecimal(), 5.toBigDecimal()),
            ),
            timestamp = Instant.fromEpochMilliseconds(0),
            description = "Dinner",
        )
        val capturedExpense = slot<Expense>()
        val eventsLocalStore = mockk<EventsLocalStore>()
        val expensesLocalStore = mockk<ExpensesLocalStore>()

        coEvery { eventsLocalStore.getEvent(event.id) } returns event
        coEvery { expensesLocalStore.getExpense(originalExpense.expenseId) } returns originalExpense
        coEvery { expensesLocalStore.upsert(event, capture(capturedExpense)) } answers { capturedExpense.captured }

        val result = RevertExpenseUseCase(
            eventsLocalStoreLazy = lazyOf(eventsLocalStore),
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
        ).revertExpense(
            eventId = event.id,
            expenseId = originalExpense.expenseId,
            description = "Revert",
        )

        assertTrue(result)
        assertEquals(ExpenseType.Replenishment, capturedExpense.captured.expenseType)
        assertEquals(BigDecimal.parseString("-5"), capturedExpense.captured.subjectExpenseSplitWithPersons.single().originalAmount)
        assertEquals(BigDecimal.parseString("-5"), capturedExpense.captured.subjectExpenseSplitWithPersons.single().exchangedAmount)
        assertEquals("Revert", capturedExpense.captured.description)
    }
}
