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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
internal class ExpensesDaoPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "expenses_dao_persistence_test.db"
    private lateinit var database: AppDatabase

    private val currency = CurrencyEntity(
        currencyId = 100_001L,
        currencyServerId = "currency-server-id",
        code = "TST",
        name = "Test currency",
        rateUnscaled = BigDecimal.ONE.significand,
        rateScale = BigDecimal.ONE.exponent,
    )
    private val payer = PersonEntity(
        personId = 101L,
        personServerId = "payer-server-id",
        clientCreateId = "payer-client-id",
        name = "Alex",
    )
    private val participant = payer.copy(
        personId = 102L,
        personServerId = "participant-server-id",
        clientCreateId = "participant-client-id",
        name = "Sam",
    )
    private val expense = ExpenseEntity(
        expenseId = 501L,
        serverId = null,
        clientCreateId = "expense-client-id",
        eventId = 201L,
        currencyId = currency.currencyId,
        expenseType = ExpenseType.Spending,
        personId = payer.personId,
        isCustomRate = true,
        timestamp = Instant.parse("2026-03-28T12:34:56.789Z"),
        description = "Shared dinner",
    )

    @Before
    fun setUp() = runTest {
        context.deleteDatabase(databaseName)
        database = openDatabase()
        database.currenciesDao().insert(listOf(currency))
        database.personsDao().insert(listOf(payer, participant))
        database.eventsDao().insert(
            EventEntity(
                eventId = expense.eventId,
                eventServerId = "event-server-id",
                clientCreateId = "event-client-id",
                name = "Persistence test",
                pinCode = "1234",
                primaryCurrencyId = currency.currencyId,
            )
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun upsertAndTargetedUpdates_preserveExpenseDetailsAcrossReopening() = runTest {
        val payerSplit = split(
            id = 601L,
            personId = payer.personId,
            originalAmount = "12345678901234567890.123456789",
            exchangedAmount = "98765432109876543210.987654321",
        )
        val participantSplit = split(
            id = 602L,
            personId = participant.personId,
            originalAmount = "0.000000001",
            exchangedAmount = "0.000000123",
        )
        assertEquals(expense.expenseId, database.expensesDao().upsert(expense, listOf(payerSplit, participantSplit)))

        reopenDatabase()
        val inserted = requireNotNull(database.expensesDao().queryById(expense.expenseId))
        assertEquals(expense, inserted.expense)
        assertEquals(payer, inserted.person)
        assertEquals(currency, inserted.currency)
        assertEquals(
            listOf(payerSplit, participantSplit),
            inserted.expenseSplitWithPersons.map { it.expenseSplitEntity }.sortedBy { it.expenseSplitId },
        )
        assertEquals(
            mapOf(payer.personId to payer, participant.personId to participant),
            inserted.expenseSplitWithPersons.associate { it.expenseSplitEntity.personId to it.person },
        )

        val updatedExchangedAmount = BigDecimal.parseString("123.4567890123456789")
        assertEquals(1, database.expensesDao().updateExpenseServerId(expense.expenseId, "expense-server-id"))
        assertEquals(
            1,
            database.expensesDao().updateExpenseSplitExchangedAmount(
                payerSplit.expenseSplitId,
                updatedExchangedAmount.significand,
                updatedExchangedAmount.exponent,
            ),
        )

        reopenDatabase()
        val updated = requireNotNull(database.expensesDao().queryById(expense.expenseId))
        assertEquals(expense.copy(serverId = "expense-server-id"), updated.expense)
        assertEquals(payer, updated.person)
        assertEquals(currency, updated.currency)
        assertEquals(
            listOf(
                payerSplit.copy(
                    exchangedAmountUnscaled = updatedExchangedAmount.significand,
                    exchangedAmountScale = updatedExchangedAmount.exponent,
                ),
                participantSplit,
            ),
            updated.expenseSplitWithPersons.map { it.expenseSplitEntity }.sortedBy { it.expenseSplitId },
        )
    }

    @Test
    fun upsertExistingExpenseAndSplit_preservesTheirRelationshipAndDurableIdentity() = runTest {
        val originalSplit = split(601L, participant.personId, "10.25", "12.75")
        database.expensesDao().upsert(expense, listOf(originalSplit))
        val updatedExpense = expense.copy(description = "Updated dinner")
        val updatedSplit = split(originalSplit.expenseSplitId, participant.personId, "20.50", "25.50")

        database.expensesDao().upsert(updatedExpense, listOf(updatedSplit))

        reopenDatabase()
        val persisted = requireNotNull(database.expensesDao().queryById(expense.expenseId))
        assertEquals(updatedExpense, persisted.expense)
        assertEquals(expense.clientCreateId, persisted.expense.clientCreateId)
        assertEquals(listOf(updatedSplit), persisted.expenseSplitWithPersons.map { it.expenseSplitEntity })
        assertEquals(listOf(participant), persisted.expenseSplitWithPersons.map { it.person })
        assertEquals(listOf(updatedExpense), database.expensesDao().queryByEventId(expense.eventId).map { it.expense })
    }

    @Test
    fun upsert_rollsBackExistingExpenseAndSplitUpdatesWhenLaterSplitViolatesForeignKey() = runTest {
        val originalSplit = split(601L, participant.personId, "10.25", "12.75")
        database.expensesDao().upsert(expense, listOf(originalSplit))
        val updatedExpense = expense.copy(description = "Must roll back", serverId = "must-roll-back")
        val updatedSplit = split(originalSplit.expenseSplitId, participant.personId, "20.50", "25.50")
        val missingPersonSplit = split(602L, Long.MAX_VALUE, "30", "40")

        val failure = runCatching {
            database.expensesDao().upsert(updatedExpense, listOf(updatedSplit, missingPersonSplit))
        }.exceptionOrNull()

        assertNotNull("The missing split participant must violate its foreign key", failure)
        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("FOREIGN KEY constraint failed"))
        reopenDatabase()
        val persisted = requireNotNull(database.expensesDao().queryById(expense.expenseId))
        assertEquals(expense, persisted.expense)
        assertEquals(listOf(originalSplit), persisted.expenseSplitWithPersons.map { it.expenseSplitEntity })
        assertEquals(listOf(participant), persisted.expenseSplitWithPersons.map { it.person })
    }

    @Test
    fun upsert_rollsBackExpenseAndEarlierSplitsWhenLaterSplitViolatesForeignKey() = runTest {
        val validSplit = split(601L, payer.personId, "10.25", "12.75")
        val missingPersonSplit = split(602L, Long.MAX_VALUE, "20.50", "25.50")

        val failure = runCatching {
            database.expensesDao().upsert(expense, listOf(validSplit, missingPersonSplit))
        }.exceptionOrNull()

        assertNotNull("The missing split participant must violate its foreign key", failure)
        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("FOREIGN KEY constraint failed"))
        reopenDatabase()
        assertNull(database.expensesDao().queryById(expense.expenseId))
        assertTrue(database.expensesDao().queryByEventId(expense.eventId).isEmpty())

        val replacementSplit = split(603L, participant.personId, "5.25", "6.75")
        database.expensesDao().upsert(expense, listOf(replacementSplit))
        val recovered = requireNotNull(database.expensesDao().queryById(expense.expenseId))
        assertEquals(expense, recovered.expense)
        assertEquals(listOf(replacementSplit), recovered.expenseSplitWithPersons.map { it.expenseSplitEntity })
    }

    private fun split(id: Long, personId: Long, originalAmount: String, exchangedAmount: String): ExpenseSplitEntity {
        val original = BigDecimal.parseString(originalAmount)
        val exchanged = BigDecimal.parseString(exchangedAmount)
        return ExpenseSplitEntity(
            expenseSplitId = id,
            expenseId = expense.expenseId,
            personId = personId,
            originalAmountUnscaled = original.significand,
            originalAmountScale = original.exponent,
            exchangedAmountUnscaled = exchanged.significand,
            exchangedAmountScale = exchanged.exponent,
        )
    }

    private fun openDatabase(): AppDatabase = createAppDatabase(
        Room.databaseBuilder<AppDatabase>(context = context, name = databaseName)
    )

    private fun reopenDatabase() {
        database.close()
        database = openDatabase()
    }
}
