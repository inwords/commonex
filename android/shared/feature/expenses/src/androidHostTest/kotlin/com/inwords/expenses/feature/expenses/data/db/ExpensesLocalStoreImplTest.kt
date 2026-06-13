package com.inwords.expenses.feature.expenses.data.db

import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.expenses.data.db.dao.ExpensesDao
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseSplitEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseSplitWithPersonQuery
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseWithDetailsQuery
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.model.ExpenseSplitWithPerson
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import com.inwords.expenses.feature.expenses.domain.store.ExpensePullItem
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

internal class ExpensesLocalStoreImplTest {

    @Test
    fun `reconcileCorrectionConflicts deletes pending chain and links inserted remote correction by local expense id`() = runTest {
        val event = Event(1L, "srv-event", "Trip", "1234", 1L, "event-client-1")
        val currentOriginal = expense(1L, "srv-original", null, null)
        val pendingReplacement = expense(2L, null, null, currentOriginal.expenseId)
        val pendingDescendant = expense(3L, null, null, pendingReplacement.expenseId)
        val remoteReplacement = expense(0L, "srv-replacement", null, null)
        val expensesDao = mockk<ExpensesDao>()
        val transactionHelper = mockk<TransactionHelper>()

        coEvery {
            transactionHelper.immediateWriteTransaction<List<Expense>>(any())
        } coAnswers {
            firstArg<suspend () -> List<Expense>>().invoke()
        }
        coEvery { expensesDao.queryByEventId(event.id) } returns listOf(
            currentOriginal.toQuery(event),
            pendingReplacement.toQuery(event),
            pendingDescendant.toQuery(event),
        )
        coEvery { expensesDao.deleteExpenses(listOf(pendingReplacement.expenseId, pendingDescendant.expenseId)) } returns 2
        coEvery { expensesDao.upsert(any<ExpenseEntity>(), any<List<ExpenseSplitEntity>>()) } returns 4L

        val result = ExpensesLocalStoreImpl(
            expensesDaoLazy = lazyOf(expensesDao),
            transactionHelperLazy = lazyOf(transactionHelper),
        ).reconcileCorrectionConflicts(
            event = event,
            expensesToUpsert = listOf(
                ExpensePullItem(
                    expense = remoteReplacement,
                    revertsExpenseServerId = null,
                    replacesExpenseServerId = currentOriginal.serverId,
                )
            ),
        )

        assertEquals(
            remoteReplacement.copy(expenseId = 4L, replacesExpenseId = currentOriginal.expenseId),
            result.single(),
        )
        coVerify(exactly = 1) {
            expensesDao.deleteExpenses(listOf(pendingReplacement.expenseId, pendingDescendant.expenseId))
        }
        coVerify(exactly = 0) { expensesDao.updateExpenseCorrectionLinks(any(), any(), any()) }
    }

    private fun expense(
        expenseId: Long,
        serverId: String?,
        revertsExpenseId: Long?,
        replacesExpenseId: Long?,
    ): Expense {
        val currency = Currency(1L, "srv-eur", "EUR", "Euro", BigDecimal.ONE)
        val person = com.inwords.expenses.feature.events.domain.model.Person(1L, "srv-person", "Alice", "person-client-1")
        return Expense(
            expenseId = expenseId,
            serverId = serverId,
            currency = currency,
            expenseType = ExpenseType.Spending,
            person = person,
            subjectExpenseSplitWithPersons = listOf(
                ExpenseSplitWithPerson(
                    expenseSplitId = expenseId * 10,
                    expenseId = expenseId,
                    person = person,
                    originalAmount = BigDecimal.parseString("10"),
                    exchangedAmount = BigDecimal.parseString("10"),
                )
            ),
            isCustomRate = false,
            timestamp = Instant.fromEpochMilliseconds(0),
            description = "Expense $expenseId",
            clientCreateId = "expense-client-$expenseId",
            revertsExpenseId = revertsExpenseId,
            replacesExpenseId = replacesExpenseId,
        )
    }

    private fun Expense.toQuery(event: Event): ExpenseWithDetailsQuery {
        val person = PersonEntity(
            personId = person.id,
            personServerId = person.serverId,
            clientCreateId = person.clientCreateId,
            name = person.name,
        )
        val amount = subjectExpenseSplitWithPersons.single().exchangedAmount
        return ExpenseWithDetailsQuery(
            expense = ExpenseEntity(
                expenseId = expenseId,
                serverId = serverId,
                clientCreateId = clientCreateId,
                eventId = event.id,
                currencyId = currency.id,
                expenseType = expenseType,
                personId = person.personId,
                isCustomRate = isCustomRate,
                timestamp = timestamp,
                description = description,
                revertsExpenseId = revertsExpenseId,
                replacesExpenseId = replacesExpenseId,
            ),
            person = person,
            expenseSplitWithPersons = listOf(
                ExpenseSplitWithPersonQuery(
                    expenseSplitEntity = ExpenseSplitEntity(
                        expenseSplitId = subjectExpenseSplitWithPersons.single().expenseSplitId,
                        expenseId = expenseId,
                        personId = person.personId,
                        originalAmountUnscaled = amount.significand,
                        originalAmountScale = amount.exponent,
                        exchangedAmountUnscaled = amount.significand,
                        exchangedAmountScale = amount.exponent,
                    ),
                    person = person,
                )
            ),
            currency = CurrencyEntity(
                currencyId = currency.id,
                currencyServerId = currency.serverId,
                code = currency.code,
                name = currency.name,
                rateUnscaled = currency.rate.significand,
                rateScale = currency.rate.exponent,
            ),
        )
    }
}
