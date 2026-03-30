package com.inwords.expenses.integration.databases.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.feature.events.data.db.entity.EventEntity
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseSplitEntity
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
internal class ExpensesDaoOrderingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "expenses_dao_ordering_test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun queryByEventId_shouldReturnExpensesOrderedByTimestampDescAndIdDesc() = runBlocking {
        val database = createAppDatabase(
            Room.databaseBuilder<AppDatabase>(
                context = context,
                name = databaseName,
            )
        )

        try {
            val currencyId = database.currenciesDao()
                .insert(
                    listOf(
                        CurrencyEntity(
                            currencyId = 0L,
                            currencyServerId = null,
                            code = "TST",
                            name = "Test currency",
                            rateUnscaled = BigDecimal.ONE.significand,
                            rateScale = BigDecimal.ONE.exponent,
                        )
                    )
                )
                .single()
            val eventId = database.eventsDao().insert(
                EventEntity(
                    eventId = 0L,
                    eventServerId = null,
                    name = "Timeline order test",
                    pinCode = "1234",
                    primaryCurrencyId = currencyId,
                )
            )
            val otherEventId = database.eventsDao().insert(
                EventEntity(
                    eventId = 0L,
                    eventServerId = null,
                    name = "Other event",
                    pinCode = "5678",
                    primaryCurrencyId = currencyId,
                )
            )
            val personId = database.personsDao()
                .insert(
                    listOf(
                        PersonEntity(
                            personId = 0L,
                            personServerId = null,
                            name = "Alex",
                        )
                    )
                )
                .single()

            val firstInsertedExpenseId = insertExpense(
                database = database,
                eventId = eventId,
                currencyId = currencyId,
                personId = personId,
                timestamp = Instant.parse("2026-03-28T12:00:00Z"),
                description = "First same timestamp",
            )
            val secondInsertedExpenseId = insertExpense(
                database = database,
                eventId = eventId,
                currencyId = currencyId,
                personId = personId,
                timestamp = Instant.parse("2026-03-28T12:00:00Z"),
                description = "Second same timestamp",
            )
            val olderExpenseId = insertExpense(
                database = database,
                eventId = eventId,
                currencyId = currencyId,
                personId = personId,
                timestamp = Instant.parse("2026-03-27T09:00:00Z"),
                description = "Older expense",
            )
            insertExpense(
                database = database,
                eventId = otherEventId,
                currencyId = currencyId,
                personId = personId,
                timestamp = Instant.parse("2026-03-29T09:00:00Z"),
                description = "Other event expense",
            )

            val queryOrder = database.expensesDao()
                .queryByEventId(eventId)
                .map { expenseWithDetails -> expenseWithDetails.expense.expenseId }
            val flowOrder = database.expensesDao()
                .queryByEventIdFlow(eventId)
                .first()
                .map { expenseWithDetails -> expenseWithDetails.expense.expenseId }

            val expectedOrder = listOf(
                secondInsertedExpenseId,
                firstInsertedExpenseId,
                olderExpenseId,
            )
            assertEquals(expectedOrder, queryOrder)
            assertEquals(expectedOrder, flowOrder)
        } finally {
            database.close()
        }
    }

    private suspend fun insertExpense(
        database: AppDatabase,
        eventId: Long,
        currencyId: Long,
        personId: Long,
        timestamp: Instant,
        description: String,
    ): Long {
        val amount = BigDecimal.parseString("10")
        return database.expensesDao().upsert(
            expenseEntity = ExpenseEntity(
                expenseId = 0L,
                serverId = null,
                eventId = eventId,
                currencyId = currencyId,
                expenseType = ExpenseType.Spending,
                personId = personId,
                isCustomRate = false,
                timestamp = timestamp,
                description = description,
            ),
            subjectPersonSplitEntities = listOf(
                ExpenseSplitEntity(
                    expenseSplitId = 0L,
                    expenseId = 0L,
                    personId = personId,
                    originalAmountUnscaled = amount.significand,
                    originalAmountScale = amount.exponent,
                    exchangedAmountUnscaled = amount.significand,
                    exchangedAmountScale = amount.exponent,
                )
            ),
        )
    }
}
