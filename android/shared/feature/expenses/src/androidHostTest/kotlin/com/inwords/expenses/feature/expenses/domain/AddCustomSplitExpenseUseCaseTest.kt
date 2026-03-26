package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.model.PersonWithAmount
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

internal class AddCustomSplitExpenseUseCaseTest {

    @Test
    fun `addExpense preserves custom split amounts and applies exchanger`() = runTest {
        val primaryCurrency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val originalCurrency = Currency(2L, null, "USD", "US Dollar", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", primaryCurrency.id)
        val alice = Person(1L, null, "Alice")
        val bob = Person(2L, null, "Bob")
        val capturedExpense = slot<Expense>()
        val expensesLocalStore = mockk<ExpensesLocalStore>()
        val expenseExchangeResolver = mockk<ExpenseExchangeResolver>()

        coEvery { expenseExchangeResolver.resolve(event, originalCurrency) } returns { amount ->
            amount * BigDecimal.parseString("2")
        }
        coEvery { expensesLocalStore.upsert(event, capture(capturedExpense)) } answers { capturedExpense.captured }

        AddCustomSplitExpenseUseCase(
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            expenseExchangeResolverLazy = lazyOf(expenseExchangeResolver),
        ).addExpense(
            event = event,
            expenseType = ExpenseType.Spending,
            description = "Taxi",
            selectedCurrency = originalCurrency,
            selectedPerson = alice,
            personWithAmountSplit = listOf(
                PersonWithAmount(alice, 2.toBigDecimal()),
                PersonWithAmount(bob, 3.toBigDecimal()),
            ),
            overrideRate = null,
        )

        assertEquals(false, capturedExpense.captured.isCustomRate)
        assertEquals(2, capturedExpense.captured.subjectExpenseSplitWithPersons.size)
        assertEquals(2.toBigDecimal(), capturedExpense.captured.subjectExpenseSplitWithPersons[0].originalAmount)
        assertEquals(4.toBigDecimal(), capturedExpense.captured.subjectExpenseSplitWithPersons[0].exchangedAmount)
        assertEquals(3.toBigDecimal(), capturedExpense.captured.subjectExpenseSplitWithPersons[1].originalAmount)
        assertEquals(6.toBigDecimal(), capturedExpense.captured.subjectExpenseSplitWithPersons[1].exchangedAmount)
    }

    @Test
    fun `addExpense stores rounded exchanged amounts when custom rate override is provided`() = runTest {
        val primaryCurrency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        val originalCurrency = Currency(2L, null, "USD", "US Dollar", BigDecimal.ONE)
        val event = Event(10L, null, "Trip", "1234", primaryCurrency.id)
        val alice = Person(1L, null, "Alice")
        val bob = Person(2L, null, "Bob")
        val capturedExpense = slot<Expense>()
        val expensesLocalStore = mockk<ExpensesLocalStore>()
        val expenseExchangeResolver = mockk<ExpenseExchangeResolver>(relaxed = true)

        coEvery { expensesLocalStore.upsert(event, capture(capturedExpense)) } answers { capturedExpense.captured }

        AddCustomSplitExpenseUseCase(
            expensesLocalStoreLazy = lazyOf(expensesLocalStore),
            expenseExchangeResolverLazy = lazyOf(expenseExchangeResolver),
        ).addExpense(
            event = event,
            expenseType = ExpenseType.Spending,
            description = "Taxi",
            selectedCurrency = originalCurrency,
            selectedPerson = alice,
            personWithAmountSplit = listOf(
                PersonWithAmount(alice, BigDecimal.parseString("2.22")),
                PersonWithAmount(bob, BigDecimal.parseString("3.33")),
            ),
            overrideRate = BigDecimal.parseString("1.25"),
        )

        assertEquals(true, capturedExpense.captured.isCustomRate)
        assertEquals(BigDecimal.parseString("2.78"), capturedExpense.captured.subjectExpenseSplitWithPersons[0].exchangedAmount)
        assertEquals(BigDecimal.parseString("4.16"), capturedExpense.captured.subjectExpenseSplitWithPersons[1].exchangedAmount)
    }
}
