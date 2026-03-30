package com.inwords.expenses.feature.expenses.ui.list

import androidx.compose.ui.text.intl.Locale
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.expenses.domain.DebtCalculator
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.model.ExpensesDetails
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

internal class ExpensesTimelineUiModelFactoryTest {

    private val primaryCurrency = Currency(
        id = 1L,
        serverId = "currency-1",
        code = "EUR",
        name = "Euro",
        rate = BigDecimal.ONE,
    )
    private val currentPerson = Person(
        id = 1L,
        serverId = "person-1",
        name = "Alex",
    )
    private val otherPerson = Person(
        id = 2L,
        serverId = "person-2",
        name = "Ben",
    )
    private val eventDetails = EventDetails(
        event = Event(
            id = 10L,
            serverId = "event-10",
            name = "Trip",
            pinCode = "1234",
            primaryCurrencyId = primaryCurrency.id,
        ),
        currencies = listOf(primaryCurrency),
        persons = listOf(currentPerson, otherPerson),
        primaryCurrency = primaryCurrency,
    )

    @Test
    fun `create groups operations by local day and preserves ordered input`() = runTest {
        val state = createFactory(
            timeZone = TimeZone.UTC,
            now = "2026-03-28T12:00:00Z",
        ).create(
            expensesDetails = expensesDetails(
                expenses = listOf(
                    expense(
                        expenseId = 4L,
                        expenseType = ExpenseType.Spending,
                        totalAmount = 40,
                        timestamp = "2026-03-28T20:00:00Z",
                        description = "Dinner",
                    ),
                    expense(
                        expenseId = 3L,
                        expenseType = ExpenseType.Replenishment,
                        totalAmount = 15,
                        timestamp = "2026-03-28T10:00:00Z",
                        description = "Refund",
                    ),
                    expense(
                        expenseId = 2L,
                        expenseType = ExpenseType.Spending,
                        totalAmount = 20,
                        timestamp = "2026-03-27T23:00:00Z",
                        description = "Museum",
                    ),
                ),
            ),
            currentPersonId = currentPerson.id,
            debts = emptyList(),
        )

        assertEquals(listOf("2026-03-28", "2026-03-27"), state.daySections.map { it.dayKey })
        assertEquals(listOf(4L, 3L), state.daySections[0].expenses.map { it.expenseId })
        assertEquals(listOf(2L), state.daySections[1].expenses.map { it.expenseId })
        assertEquals(listOf(true, false), state.dayChips.map { it.isSelected })
    }

    @Test
    fun `create hides day total when the day has only replenishments`() = runTest {
        val state = createFactory(
            timeZone = TimeZone.UTC,
            now = "2026-03-28T12:00:00Z",
        ).create(
            expensesDetails = expensesDetails(
                expenses = listOf(
                    expense(
                        expenseId = 2L,
                        expenseType = ExpenseType.Replenishment,
                        totalAmount = 15,
                        timestamp = "2026-03-28T10:00:00Z",
                        description = "Refund",
                    ),
                ),
            ),
            currentPersonId = currentPerson.id,
            debts = emptyList(),
        )

        assertNull(state.daySections.single().spendingTotal)
    }

    @Test
    fun `create total spending excludes replenishments`() = runTest {
        val state = createFactory(
            timeZone = TimeZone.UTC,
            now = "2026-03-28T12:00:00Z",
        ).create(
            expensesDetails = expensesDetails(
                expenses = listOf(
                    expense(
                        expenseId = 1L,
                        expenseType = ExpenseType.Spending,
                        totalAmount = 120,
                        timestamp = "2026-03-28T20:00:00Z",
                        description = "Dinner",
                    ),
                    expense(
                        expenseId = 2L,
                        expenseType = ExpenseType.Replenishment,
                        totalAmount = 15,
                        timestamp = "2026-03-28T10:00:00Z",
                        description = "Refund",
                    ),
                    expense(
                        expenseId = 3L,
                        expenseType = ExpenseType.Spending,
                        totalAmount = 60,
                        timestamp = "2026-03-27T23:00:00Z",
                        description = "Museum",
                    ),
                ),
            ),
            currentPersonId = currentPerson.id,
            debts = emptyList(),
        )

        assertEquals("180 EUR", state.totalSpending)
        assertEquals("120 EUR", state.daySections[0].spendingTotal)
        assertEquals("60 EUR", state.daySections[1].spendingTotal)
    }

    @Test
    fun `create uses today yesterday and cross year chip labels`() = runTest {
        val state = createFactory(
            timeZone = TimeZone.UTC,
            now = "2026-03-28T12:00:00Z",
        ).create(
            expensesDetails = expensesDetails(
                expenses = listOf(
                    expense(
                        expenseId = 1L,
                        expenseType = ExpenseType.Spending,
                        totalAmount = 120,
                        timestamp = "2026-03-28T20:00:00Z",
                        description = "Dinner",
                    ),
                    expense(
                        expenseId = 2L,
                        expenseType = ExpenseType.Spending,
                        totalAmount = 60,
                        timestamp = "2026-03-27T23:00:00Z",
                        description = "Museum",
                    ),
                    expense(
                        expenseId = 3L,
                        expenseType = ExpenseType.Spending,
                        totalAmount = 30,
                        timestamp = "2025-12-30T08:00:00Z",
                        description = "Train",
                    ),
                ),
            ),
            currentPersonId = currentPerson.id,
            debts = emptyList(),
        )

        assertEquals(listOf("Today", "Yesterday", "30.12.25"), state.dayChips.map { it.label })
        assertEquals("30 December 2025", state.daySections.last().headerLabel)
    }

    @Test
    fun `create groups by local day around midnight`() = runTest {
        val state = createFactory(
            timeZone = TimeZone.of("Europe/Belgrade"),
            now = "2026-03-28T12:00:00Z",
        ).create(
            expensesDetails = expensesDetails(
                expenses = listOf(
                    expense(
                        expenseId = 1L,
                        expenseType = ExpenseType.Spending,
                        totalAmount = 25,
                        timestamp = "2026-03-27T23:30:00Z",
                        description = "Late snack",
                    ),
                    expense(
                        expenseId = 2L,
                        expenseType = ExpenseType.Spending,
                        totalAmount = 15,
                        timestamp = "2026-03-27T21:00:00Z",
                        description = "Taxi",
                    ),
                ),
            ),
            currentPersonId = currentPerson.id,
            debts = emptyList(),
        )

        assertEquals(listOf("2026-03-28", "2026-03-27"), state.daySections.map { it.dayKey })
    }

    private fun createFactory(
        timeZone: TimeZone,
        now: String,
    ): ExpensesTimelineUiModelFactory {
        var stringRequestCount = 0
        return ExpensesTimelineUiModelFactory(
            stringProvider = object : StringProvider {
                override suspend fun getString(stringResource: StringResource): String {
                    return when (stringRequestCount++) {
                        0 -> "Today"
                        1 -> "Yesterday"
                        else -> error("Unexpected string resource request: $stringRequestCount")
                    }
                }

                override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String {
                    return getString(stringResource)
                }
            },
            timeZoneProvider = { timeZone },
            localeProvider = { Locale("en") },
            nowProvider = { Instant.parse(now) },
        )
    }

    private fun expensesDetails(expenses: List<Expense>): ExpensesDetails {
        return ExpensesDetails(
            event = eventDetails,
            expenses = expenses,
            debtCalculator = DebtCalculator(expenses, primaryCurrency),
        )
    }

    private fun expense(
        expenseId: Long,
        expenseType: ExpenseType,
        totalAmount: Int,
        timestamp: String,
        description: String,
    ): Expense {
        return Expense(
            expenseId = expenseId,
            serverId = "expense-$expenseId",
            currency = primaryCurrency,
            expenseType = expenseType,
            person = currentPerson,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(
                    expenseSplitId = expenseId,
                    expenseId = expenseId,
                    person = otherPerson,
                    originalAmount = totalAmount.toBigDecimal(),
                    exchangedAmount = totalAmount.toBigDecimal(),
                ),
            ),
            isCustomRate = false,
            timestamp = Instant.parse(timestamp),
            description = description,
        )
    }
}
