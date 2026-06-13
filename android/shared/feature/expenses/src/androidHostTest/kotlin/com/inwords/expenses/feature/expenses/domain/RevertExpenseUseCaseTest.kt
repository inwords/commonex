package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.testutils.TestClientCreateIdGenerator
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
        val clientCreateIdGenerator = TestClientCreateIdGenerator("reverted-expense-client-1")

        coEvery { eventsLocalStore.getEvent(10L) } returns null

        val result = RevertExpenseUseCase(
            eventsLocalStoreLazy = lazyOf(eventsLocalStore),
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            clientCreateIdGeneratorLazy = lazyOf(clientCreateIdGenerator),
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
        val event = Event(10L, null, "Trip", "1234", currency.id, "event-client-10")
        val alice = Person(1L, null, "Alice", "person-client-1")
        val bob = Person(2L, null, "Bob", "person-client-2")
        val originalExpense = Expense(
            expenseId = 20L,
            serverId = null,
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = alice,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(1L, 20L, bob, 5.toBigDecimal(), 5.toBigDecimal()),
            ),
            isCustomRate = true,
            timestamp = Instant.fromEpochMilliseconds(0),
            description = "Dinner",
            clientCreateId = "original-expense-client-id",
            revertsExpenseId = null,
            replacesExpenseId = null,
        )
        val capturedExpense = slot<Expense>()
        val eventsLocalStore = mockk<EventsLocalStore>()
        val expensesLocalStore = mockk<ExpensesLocalStore>()
        val clientCreateIdGenerator = TestClientCreateIdGenerator("reverted-expense-client-1")

        coEvery { eventsLocalStore.getEvent(event.id) } returns event
        coEvery { expensesLocalStore.getExpense(originalExpense.expenseId) } returns originalExpense
        coEvery { expensesLocalStore.hasCorrectionFor(originalExpense.expenseId) } returns false
        coEvery { expensesLocalStore.upsert(event, capture(capturedExpense)) } answers { capturedExpense.captured }

        val result = RevertExpenseUseCase(
            eventsLocalStoreLazy = lazyOf(eventsLocalStore),
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            clientCreateIdGeneratorLazy = lazyOf(clientCreateIdGenerator),
        ).revertExpense(
            eventId = event.id,
            expenseId = originalExpense.expenseId,
            description = "Revert",
        )

        assertTrue(result)
        assertEquals(ExpenseType.Replenishment, capturedExpense.captured.expenseType)
        assertEquals("reverted-expense-client-1", capturedExpense.captured.clientCreateId)
        assertTrue(capturedExpense.captured.isCustomRate)
        assertEquals(BigDecimal.parseString("-5"), capturedExpense.captured.subjectExpenseSplitWithPersons.single().originalAmount)
        assertEquals(BigDecimal.parseString("-5"), capturedExpense.captured.subjectExpenseSplitWithPersons.single().exchangedAmount)
        assertEquals("Revert", capturedExpense.captured.description)
        assertEquals(originalExpense.expenseId, capturedExpense.captured.revertsExpenseId)
        assertEquals(null, capturedExpense.captured.replacesExpenseId)
    }

    @Test
    fun `revertExpense can revert a reversal by linking to the reversal expense`() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", currency.id, "event-client-10")
        val alice = Person(1L, null, "Alice", "person-client-1")
        val bob = Person(2L, null, "Bob", "person-client-2")
        val reversalExpense = Expense(
            expenseId = 21L,
            serverId = null,
            currency = currency,
            expenseType = ExpenseType.Replenishment,
            person = alice,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(1L, 21L, bob, (-5).toBigDecimal(), (-5).toBigDecimal()),
            ),
            isCustomRate = true,
            timestamp = Instant.fromEpochMilliseconds(0),
            description = "Revert dinner",
            clientCreateId = "reversal-expense-client-id",
            revertsExpenseId = 20L,
            replacesExpenseId = null,
        )
        val capturedExpense = slot<Expense>()
        val eventsLocalStore = mockk<EventsLocalStore>()
        val expensesLocalStore = mockk<ExpensesLocalStore>()
        val clientCreateIdGenerator = TestClientCreateIdGenerator("revert-of-reversal-client-1")

        coEvery { eventsLocalStore.getEvent(event.id) } returns event
        coEvery { expensesLocalStore.getExpense(reversalExpense.expenseId) } returns reversalExpense
        coEvery { expensesLocalStore.hasCorrectionFor(reversalExpense.expenseId) } returns false
        coEvery { expensesLocalStore.upsert(event, capture(capturedExpense)) } answers { capturedExpense.captured }

        val result = RevertExpenseUseCase(
            eventsLocalStoreLazy = lazyOf(eventsLocalStore),
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            clientCreateIdGeneratorLazy = lazyOf(clientCreateIdGenerator),
        ).revertExpense(
            eventId = event.id,
            expenseId = reversalExpense.expenseId,
            description = "Restore dinner",
        )

        assertTrue(result)
        assertEquals(ExpenseType.Spending, capturedExpense.captured.expenseType)
        assertEquals(BigDecimal.parseString("5"), capturedExpense.captured.subjectExpenseSplitWithPersons.single().originalAmount)
        assertEquals(reversalExpense.expenseId, capturedExpense.captured.revertsExpenseId)
    }

    @Test
    fun `revertExpense returns false when source already has a correction`() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", currency.id, "event-client-10")
        val alice = Person(1L, null, "Alice", "person-client-1")
        val originalExpense = Expense(
            expenseId = 20L,
            serverId = "srv-original",
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = alice,
            subjectExpenseSplitWithPersons = emptyList(),
            isCustomRate = false,
            timestamp = Instant.fromEpochMilliseconds(0),
            description = "Dinner",
            clientCreateId = "original-expense-client-id",
            revertsExpenseId = null,
            replacesExpenseId = null,
        )
        val existingReplacement = originalExpense.copy(
            expenseId = 21L,
            clientCreateId = "replacement-client-id",
            replacesExpenseId = originalExpense.expenseId,
        )
        val eventsLocalStore = mockk<EventsLocalStore>()
        val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)

        coEvery { eventsLocalStore.getEvent(event.id) } returns event
        coEvery { expensesLocalStore.getExpense(originalExpense.expenseId) } returns originalExpense
        coEvery { expensesLocalStore.hasCorrectionFor(originalExpense.expenseId) } returns true

        val result = RevertExpenseUseCase(
            eventsLocalStoreLazy = lazyOf(eventsLocalStore),
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            clientCreateIdGeneratorLazy = lazyOf(TestClientCreateIdGenerator("reverted-expense-client-1")),
        ).revertExpense(
            eventId = event.id,
            expenseId = originalExpense.expenseId,
            description = "Revert",
        )

        assertFalse(result)
        coVerify(exactly = 0) { expensesLocalStore.upsert(any(), any<Expense>()) }
    }
}
