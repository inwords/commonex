package com.inwords.expenses.feature.expenses.data.db

import app.cash.turbine.test
import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.data.db.dao.ExpensesDao
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseSplitEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseSplitWithPersonQuery
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseWithDetailsQuery
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

internal class ExpensesLocalStoreImplTest {

    private val dao = mockk<ExpensesDao>()
    private val transactionHelper = mockk<TransactionHelper>()
    private val store = ExpensesLocalStoreImpl(lazy { dao }, lazy { transactionHelper })
    private val payer = Person(11L, "payer-server", "Alex", "payer-client")
    private val subject = Person(12L, "subject-server", "Sam", "subject-client")
    private val currency = Currency(21L, "currency-server", "EUR", "Euro", BigDecimal.parseString("0.8677"))
    private val event = Event(31L, "event-server", "Trip", "1234", currency.id, "event-client")
    private val expense = Expense(
        expenseId = 41L,
        serverId = "expense-server",
        clientCreateId = "expense-client",
        currency = currency,
        expenseType = ExpenseType.Replenishment,
        person = payer,
        subjectExpenseSplitWithPersons = listOf(
            ExpenseSplitWithPerson(
                expenseSplitId = 51L,
                expenseId = 41L,
                person = subject,
                originalAmount = BigDecimal.parseString("12345678901234567890.123456789"),
                exchangedAmount = BigDecimal.parseString("0.000000123"),
            )
        ),
        isCustomRate = true,
        timestamp = Instant.parse("2026-01-02T03:04:05.678Z"),
        description = "Refund",
    )
    private val entity = ExpenseEntity(
        expenseId = expense.expenseId,
        serverId = "expense-server",
        clientCreateId = "expense-client",
        eventId = event.id,
        currencyId = currency.id,
        expenseType = ExpenseType.Replenishment,
        personId = payer.id,
        isCustomRate = true,
        timestamp = expense.timestamp,
        description = "Refund",
    )
    private val splitEntity = ExpenseSplitEntity(
        expenseSplitId = 51L,
        expenseId = expense.expenseId,
        personId = subject.id,
        originalAmountUnscaled = BigDecimal.parseString("12345678901234567890.123456789").significand,
        originalAmountScale = BigDecimal.parseString("12345678901234567890.123456789").exponent,
        exchangedAmountUnscaled = BigDecimal.parseString("0.000000123").significand,
        exchangedAmountScale = BigDecimal.parseString("0.000000123").exponent,
    )
    private val query = ExpenseWithDetailsQuery(
        expense = entity,
        person = PersonEntity(payer.id, payer.serverId, payer.clientCreateId, payer.name),
        expenseSplitWithPersons = listOf(
            ExpenseSplitWithPersonQuery(
                splitEntity,
                PersonEntity(subject.id, subject.serverId, subject.clientCreateId, subject.name),
            )
        ),
        currency = CurrencyEntity(
            currency.id, currency.serverId, currency.code, currency.name,
            BigDecimal.parseString("0.867713").significand,
            BigDecimal.parseString("0.867713").exponent,
        ),
    )

    @Test
    fun `reads preserve expense identity payer split precision and normalized currency rate`() = runTest {
        coEvery { dao.queryById(expense.expenseId) } returns query
        coEvery { dao.queryByEventId(event.id) } returns listOf(query)
        coEvery { dao.queryById(999L) } returns null

        assertEquals(expense, store.getExpense(expense.expenseId))
        assertEquals(listOf(expense), store.getExpenses(event.id))
        assertNull(store.getExpense(999L))
    }

    @Test
    fun `expense flow maps missing inserted and deleted rows without duplicate domain emissions`() = runTest {
        val rows = MutableSharedFlow<ExpenseWithDetailsQuery?>(replay = 1)
        rows.tryEmit(null)
        every { dao.queryByIdFlow(expense.expenseId) } returns rows

        store.getExpenseFlow(expense.expenseId).test {
            assertNull(awaitItem())
            rows.emit(query)
            assertEquals(expense, awaitItem())
            rows.emit(query.copy())
            rows.emit(null)
            assertNull(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `inserting a new expense preserves money and identities and returns the generated local id`() = runTest {
        val fresh = expense.copy(
            expenseId = 0L,
            subjectExpenseSplitWithPersons = expense.subjectExpenseSplitWithPersons.map {
                it.copy(expenseSplitId = 0L, expenseId = 0L)
            },
        )
        coEvery { dao.upsert(any<ExpenseEntity>(), any()) } returns 71L

        val inserted = store.upsert(event, fresh)

        assertEquals(fresh.copy(expenseId = 71L), inserted)
        coVerify(exactly = 1) {
            dao.upsert(entity.copy(expenseId = 0L), listOf(splitEntity.copy(expenseSplitId = 0L, expenseId = 0L)))
        }
    }

    @Test
    fun `upserting an existing expense retains its parent key and split relationship`() = runTest {
        coEvery { dao.upsert(any<ExpenseEntity>(), any()) } returns -1L

        val updated = store.upsert(event, expense)

        assertEquals(expense, updated)
        coVerify(exactly = 1) { dao.upsert(entity, listOf(splitEntity)) }
    }

    @Test
    fun `batch upsert writes both distinct expenses inside one transaction and returns their generated identities`() = runTest {
        val first = expense.copy(
            expenseId = 0L,
            serverId = null,
            subjectExpenseSplitWithPersons = expense.subjectExpenseSplitWithPersons.map {
                it.copy(expenseSplitId = 0L, expenseId = 0L)
            },
        )
        val secondAmount = BigDecimal.parseString("99.25")
        val secondExchange = BigDecimal.parseString("123.5")
        val second = first.copy(
            clientCreateId = "second-client",
            expenseType = ExpenseType.Spending,
            description = "Dinner",
            subjectExpenseSplitWithPersons = first.subjectExpenseSplitWithPersons.map {
                it.copy(originalAmount = secondAmount, exchangedAmount = secondExchange)
            },
        )
        val firstEntity = entity.copy(expenseId = 0L, serverId = null)
        val firstSplit = splitEntity.copy(expenseSplitId = 0L, expenseId = 0L)
        val secondEntity = firstEntity.copy(
            clientCreateId = "second-client",
            expenseType = ExpenseType.Spending,
            description = "Dinner",
        )
        val secondSplit = firstSplit.copy(
            originalAmountUnscaled = secondAmount.significand,
            originalAmountScale = secondAmount.exponent,
            exchangedAmountUnscaled = secondExchange.significand,
            exchangedAmountScale = secondExchange.exponent,
        )
        var insideTransaction = false
        var completedWrites = 0
        coEvery { transactionHelper.immediateWriteTransaction<List<Expense>>(any()) } coAnswers {
            assertFalse(insideTransaction)
            insideTransaction = true
            try {
                firstArg<suspend () -> List<Expense>>().invoke().also {
                    assertEquals(2, completedWrites)
                }
            } finally {
                insideTransaction = false
            }
        }
        coEvery { dao.upsert(firstEntity, listOf(firstSplit)) } coAnswers {
            assertTrue(insideTransaction)
            completedWrites++
            71L
        }
        coEvery { dao.upsert(secondEntity, listOf(secondSplit)) } coAnswers {
            assertTrue(insideTransaction)
            completedWrites++
            72L
        }

        val result = store.upsert(event, listOf(first, second))

        assertEquals(listOf(first.copy(expenseId = 71L), second.copy(expenseId = 72L)), result)
        coVerify(exactly = 1) { transactionHelper.immediateWriteTransaction<List<Expense>>(any()) }
        coVerify(exactly = 1) { dao.upsert(firstEntity, listOf(firstSplit)) }
        coVerify(exactly = 1) { dao.upsert(secondEntity, listOf(secondSplit)) }
    }

    @Test
    fun `targeted writes preserve decimal precision and report whether a row was affected`() = runTest {
        val amount = BigDecimal.parseString("98765432109876543210.000000123")
        coEvery { dao.updateExpenseServerId(41L, "synced-server") } returns 1
        coEvery { dao.updateExpenseServerId(999L, "missing-server") } returns 0
        coEvery { dao.updateExpenseSplitExchangedAmount(51L, amount.significand, amount.exponent) } returns 1
        coEvery { dao.updateExpenseSplitExchangedAmount(999L, amount.significand, amount.exponent) } returns 0

        assertTrue(store.updateExpenseServerId(41L, "synced-server"))
        assertFalse(store.updateExpenseServerId(999L, "missing-server"))
        assertTrue(store.updateExpenseSplitExchangedAmount(51L, amount))
        assertFalse(store.updateExpenseSplitExchangedAmount(999L, amount))

        coVerify(exactly = 1) { dao.updateExpenseServerId(41L, "synced-server") }
        coVerify(exactly = 1) { dao.updateExpenseServerId(999L, "missing-server") }
        coVerify(exactly = 1) { dao.updateExpenseSplitExchangedAmount(51L, amount.significand, amount.exponent) }
        coVerify(exactly = 1) { dao.updateExpenseSplitExchangedAmount(999L, amount.significand, amount.exponent) }
    }
}
