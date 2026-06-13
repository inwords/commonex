package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.testutils.TestClientCreateIdGenerator
import com.inwords.expenses.core.utils.normalizeAmount
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

internal class ReplaceExpenseUseCaseTest {

    @Test
    fun `replaceEqualSplitExpense stores replacement linked to original without mutating original`() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", currency.id, "event-client-10")
        val alice = Person(1L, null, "Alice", "person-client-1")
        val bob = Person(2L, null, "Bob", "person-client-2")
        val originalExpense = Expense(
            expenseId = 20L,
            serverId = "srv-original",
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = alice,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(1L, 20L, bob, 5.toBigDecimal(), 5.toBigDecimal()),
            ),
            isCustomRate = false,
            timestamp = Instant.fromEpochMilliseconds(0),
            description = "Dinner",
            clientCreateId = "original-expense-client-id",
            revertsExpenseId = null,
            replacesExpenseId = null,
        )
        val originalSnapshot = originalExpense.copy()
        val capturedExpense = slot<Expense>()
        val expensesLocalStore = mockk<ExpensesLocalStore>()
        val expenseExchangeResolver = mockk<ExpenseExchangeResolver>()
        val clientCreateIdGenerator = TestClientCreateIdGenerator("replacement-expense-client-1")

        coEvery { expensesLocalStore.getExpense(originalExpense.expenseId) } returns originalExpense
        coEvery { expensesLocalStore.hasCorrectionFor(originalExpense.expenseId) } returns false
        coEvery { expenseExchangeResolver.resolve(event, currency) } returns { amount -> amount }
        coEvery { expensesLocalStore.upsert(event, capture(capturedExpense)) } answers { capturedExpense.captured }

        val result = ReplaceExpenseUseCase(
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            expenseDraftFactoryLazy = lazyOf(
                ExpenseDraftFactory(
                    expenseExchangeResolverLazy = lazyOf(expenseExchangeResolver),
                    clientCreateIdGeneratorLazy = lazyOf(clientCreateIdGenerator),
                )
            ),
        ).replaceEqualSplitExpense(
            event = event,
            originalExpenseId = originalExpense.expenseId,
            wholeAmount = 12.toBigDecimal(),
            expenseType = ExpenseType.Spending,
            description = "Corrected dinner",
            selectedSubjectPersons = listOf(alice, bob),
            selectedCurrency = currency,
            selectedPerson = alice,
            overrideRate = null,
        )

        assertTrue(result)
        assertEquals(originalSnapshot, originalExpense)
        assertEquals("replacement-expense-client-1", capturedExpense.captured.clientCreateId)
        assertEquals(originalExpense.expenseId, capturedExpense.captured.replacesExpenseId)
        assertEquals(null, capturedExpense.captured.revertsExpenseId)
        assertEquals("Corrected dinner", capturedExpense.captured.description)
        assertEquals(BigDecimal.parseString("6.000"), capturedExpense.captured.subjectExpenseSplitWithPersons[0].originalAmount)
        assertEquals(BigDecimal.parseString("6.000"), capturedExpense.captured.subjectExpenseSplitWithPersons[1].originalAmount)
    }

    @Test
    fun `replaceEqualSplitExpense returns false when original is missing`() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", currency.id, "event-client-10")
        val alice = Person(1L, null, "Alice", "person-client-1")
        val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)
        val expenseExchangeResolver = mockk<ExpenseExchangeResolver>(relaxed = true)
        val clientCreateIdGenerator = TestClientCreateIdGenerator("replacement-expense-client-1")

        coEvery { expensesLocalStore.getExpense(20L) } returns null

        val result = ReplaceExpenseUseCase(
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            expenseDraftFactoryLazy = lazyOf(
                ExpenseDraftFactory(
                    expenseExchangeResolverLazy = lazyOf(expenseExchangeResolver),
                    clientCreateIdGeneratorLazy = lazyOf(clientCreateIdGenerator),
                )
            ),
        ).replaceEqualSplitExpense(
            event = event,
            originalExpenseId = 20L,
            wholeAmount = 12.toBigDecimal(),
            expenseType = ExpenseType.Spending,
            description = "Corrected dinner",
            selectedSubjectPersons = listOf(alice),
            selectedCurrency = currency,
            selectedPerson = alice,
            overrideRate = null,
        )

        assertFalse(result)
    }

    @Test
    fun `replaceEqualSplitExpense returns false when original already has a correction`() = runTest {
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
        val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)
        val expenseExchangeResolver = mockk<ExpenseExchangeResolver>(relaxed = true)

        coEvery { expensesLocalStore.getExpense(originalExpense.expenseId) } returns originalExpense
        coEvery { expensesLocalStore.hasCorrectionFor(originalExpense.expenseId) } returns true

        val result = ReplaceExpenseUseCase(
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            expenseDraftFactoryLazy = lazyOf(
                ExpenseDraftFactory(
                    expenseExchangeResolverLazy = lazyOf(expenseExchangeResolver),
                    clientCreateIdGeneratorLazy = lazyOf(TestClientCreateIdGenerator("new-replacement-client-id")),
                )
            ),
        ).replaceEqualSplitExpense(
            event = event,
            originalExpenseId = originalExpense.expenseId,
            wholeAmount = 12.toBigDecimal(),
            expenseType = ExpenseType.Spending,
            description = "Second correction",
            selectedSubjectPersons = listOf(alice),
            selectedCurrency = currency,
            selectedPerson = alice,
            overrideRate = null,
        )

        assertFalse(result)
    }

    @Test
    fun `replaceCustomSplitExpense preserves independently rounded exchange values for description-only edit`() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", currency.id, "event-client-10")
        val alice = Person(1L, null, "Alice", "person-client-1")
        val bob = Person(2L, null, "Bob", "person-client-2")
        val originalExpense = Expense(
            expenseId = 20L,
            serverId = "srv-original",
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = alice,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(
                    expenseSplitId = 1L,
                    expenseId = 20L,
                    person = alice,
                    originalAmount = BigDecimal.parseString("0.47"),
                    exchangedAmount = BigDecimal.parseString("0.00"),
                ),
                ExpenseSplitWithPerson(
                    expenseSplitId = 2L,
                    expenseId = 20L,
                    person = bob,
                    originalAmount = BigDecimal.parseString("8.49"),
                    exchangedAmount = BigDecimal.parseString("0.09"),
                ),
            ),
            isCustomRate = false,
            timestamp = Instant.fromEpochMilliseconds(0),
            description = "Original",
            clientCreateId = "original-expense-client-id",
            revertsExpenseId = null,
            replacesExpenseId = null,
        )
        val capturedExpense = slot<Expense>()
        val expensesLocalStore = mockk<ExpensesLocalStore>()
        val expenseExchangeResolver = mockk<ExpenseExchangeResolver>()

        coEvery { expensesLocalStore.getExpense(originalExpense.expenseId) } returns originalExpense
        coEvery { expensesLocalStore.hasCorrectionFor(originalExpense.expenseId) } returns false
        coEvery { expenseExchangeResolver.resolve(event, currency) } returns { amount ->
            (amount * BigDecimal.parseString("0.0101")).normalizeAmount()
        }
        coEvery { expensesLocalStore.upsert(event, capture(capturedExpense)) } answers { capturedExpense.captured }

        val result = ReplaceExpenseUseCase(
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            expenseDraftFactoryLazy = lazyOf(
                ExpenseDraftFactory(
                    expenseExchangeResolverLazy = lazyOf(expenseExchangeResolver),
                    clientCreateIdGeneratorLazy = lazyOf(TestClientCreateIdGenerator("replacement-expense-client-1")),
                )
            ),
        ).replaceCustomSplitExpense(
            event = event,
            originalExpenseId = originalExpense.expenseId,
            expenseType = originalExpense.expenseType,
            description = "Updated description",
            selectedCurrency = originalExpense.currency,
            selectedPerson = originalExpense.person,
            personWithAmountSplit = originalExpense.subjectExpenseSplitWithPersons.map { split ->
                com.inwords.expenses.feature.expenses.domain.model.PersonWithAmount(split.person, split.originalAmount)
            },
            overrideRate = BigDecimal.parseString("0.0100"),
        )

        assertTrue(result)
        assertEquals(
            originalExpense.subjectExpenseSplitWithPersons.map { it.exchangedAmount },
            capturedExpense.captured.subjectExpenseSplitWithPersons.map { it.exchangedAmount },
        )
        assertFalse(capturedExpense.captured.isCustomRate)
    }
}
