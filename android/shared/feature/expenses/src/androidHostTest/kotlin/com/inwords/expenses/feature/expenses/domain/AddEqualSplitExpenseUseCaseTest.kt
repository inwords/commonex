package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.testutils.TestClientCreateIdGenerator
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
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

internal class AddEqualSplitExpenseUseCaseTest {

    @Test
    fun `addExpense stores equal split expense with stored precision`() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", currency.id, "event-client-10")
        val alice = Person(1L, null, "Alice", "person-client-1")
        val bob = Person(2L, null, "Bob", "person-client-2")
        val capturedExpense = slot<Expense>()
        val expensesLocalStore = mockk<ExpensesLocalStore>()
        val expenseExchangeResolver = mockk<ExpenseExchangeResolver>()
        val clientCreateIdGenerator = TestClientCreateIdGenerator("expense-client-1")

        coEvery { expenseExchangeResolver.resolve(event, currency) } returns { amount -> amount }
        coEvery { expensesLocalStore.upsert(event, capture(capturedExpense)) } answers { capturedExpense.captured }

        AddEqualSplitExpenseUseCase(
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            expenseDraftFactoryLazy = lazyOf(
                ExpenseDraftFactory(
                    expenseExchangeResolverLazy = lazyOf(expenseExchangeResolver),
                    clientCreateIdGeneratorLazy = lazyOf(clientCreateIdGenerator),
                )
            ),
        ).addExpense(
            event = event,
            wholeAmount = 10.toBigDecimal(),
            expenseType = ExpenseType.Spending,
            description = "Dinner",
            selectedSubjectPersons = listOf(alice, bob),
            selectedCurrency = currency,
            selectedPerson = alice,
            overrideRate = null,
        )

        assertEquals(ExpenseType.Spending, capturedExpense.captured.expenseType)
        assertEquals("expense-client-1", capturedExpense.captured.clientCreateId)
        assertEquals("Dinner", capturedExpense.captured.description)
        assertEquals(false, capturedExpense.captured.isCustomRate)
        assertEquals(2, capturedExpense.captured.subjectExpenseSplitWithPersons.size)
        assertEquals(BigDecimal.parseString("5.000"), capturedExpense.captured.subjectExpenseSplitWithPersons[0].originalAmount)
        assertEquals(BigDecimal.parseString("5.000"), capturedExpense.captured.subjectExpenseSplitWithPersons[1].originalAmount)
    }

    @Test
    fun `addExpense stores custom exchange rate when override is provided`() = runTest {
        val primaryCurrency = Currency(1L, null, "USD", "US Dollar", BigDecimal.ONE)
        val selectedCurrency = Currency(2L, null, "EUR", "Euro", BigDecimal.parseString("0.85"))
        val event = Event(10L, null, "Trip", "1234", primaryCurrency.id, "event-client-10")
        val alice = Person(1L, null, "Alice", "person-client-1")
        val bob = Person(2L, null, "Bob", "person-client-2")
        val capturedExpense = slot<Expense>()
        val expensesLocalStore = mockk<ExpensesLocalStore>()
        val expenseExchangeResolver = mockk<ExpenseExchangeResolver>(relaxed = true)
        val clientCreateIdGenerator = TestClientCreateIdGenerator("expense-client-2")

        coEvery { expensesLocalStore.upsert(event, capture(capturedExpense)) } answers { capturedExpense.captured }

        AddEqualSplitExpenseUseCase(
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            expenseDraftFactoryLazy = lazyOf(
                ExpenseDraftFactory(
                    expenseExchangeResolverLazy = lazyOf(expenseExchangeResolver),
                    clientCreateIdGeneratorLazy = lazyOf(clientCreateIdGenerator),
                )
            ),
        ).addExpense(
            event = event,
            wholeAmount = 10.toBigDecimal(),
            expenseType = ExpenseType.Spending,
            description = "Dinner",
            selectedSubjectPersons = listOf(alice, bob),
            selectedCurrency = selectedCurrency,
            selectedPerson = alice,
            overrideRate = BigDecimal.parseString("1.25"),
        )

        assertEquals(true, capturedExpense.captured.isCustomRate)
        assertEquals("expense-client-2", capturedExpense.captured.clientCreateId)
        assertEquals(BigDecimal.parseString("6.25"), capturedExpense.captured.subjectExpenseSplitWithPersons[0].exchangedAmount)
        assertEquals(BigDecimal.parseString("6.25"), capturedExpense.captured.subjectExpenseSplitWithPersons[1].exchangedAmount)
    }

    @Test
    fun `addExpense does nothing when exchange resolver cannot resolve primary currency`() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", currency.id, "event-client-10")
        val alice = Person(1L, null, "Alice", "person-client-1")
        val expensesLocalStore = mockk<ExpensesLocalStore>(relaxed = true)
        val expenseExchangeResolver = mockk<ExpenseExchangeResolver>()
        val clientCreateIdGenerator = TestClientCreateIdGenerator("expense-client-3")

        coEvery { expenseExchangeResolver.resolve(event, currency) } returns null

        AddEqualSplitExpenseUseCase(
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            expenseDraftFactoryLazy = lazyOf(
                ExpenseDraftFactory(
                    expenseExchangeResolverLazy = lazyOf(expenseExchangeResolver),
                    clientCreateIdGeneratorLazy = lazyOf(clientCreateIdGenerator),
                )
            ),
        ).addExpense(
            event = event,
            wholeAmount = 10.toBigDecimal(),
            expenseType = ExpenseType.Spending,
            description = "Dinner",
            selectedSubjectPersons = listOf(alice),
            selectedCurrency = currency,
            selectedPerson = alice,
            overrideRate = null,
        )

        coVerify(exactly = 0) { expensesLocalStore.upsert(any(), any<Expense>()) }
    }
}
