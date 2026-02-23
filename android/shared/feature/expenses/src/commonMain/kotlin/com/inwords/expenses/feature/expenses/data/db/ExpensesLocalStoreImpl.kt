package com.inwords.expenses.feature.expenses.data.db

import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.expenses.data.db.converter.toDomain
import com.inwords.expenses.feature.expenses.data.db.converter.toEntity
import com.inwords.expenses.feature.expenses.data.db.dao.ExpensesDao
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.inwords.expenses.feature.expenses.domain.store.ExpensesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class ExpensesLocalStoreImpl(
    expensesDaoLazy: Lazy<ExpensesDao>,
    transactionHelperLazy: Lazy<TransactionHelper>,
) : ExpensesLocalStore {

    private val expensesDao by expensesDaoLazy
    private val transactionHelper by transactionHelperLazy

    override fun getExpensesFlow(eventId: Long): Flow<List<Expense>> {
        return expensesDao.queryByEventIdFlow(eventId).map { entities ->
            entities.map { entity -> entity.toDomain() }
        }.distinctUntilChanged()
    }

    override suspend fun getExpenses(eventId: Long): List<Expense> {
        return expensesDao.queryByEventId(eventId).map { entity -> entity.toDomain() }
    }

    override suspend fun getExpense(expenseId: Long): Expense? {
        return expensesDao.queryById(expenseId)?.toDomain()
    }

    override suspend fun upsert(event: Event, expense: Expense): Expense {
        val id = expensesDao.upsert(
            expenseEntity = expense.toEntity(event),
            subjectPersonSplitEntities = expense.subjectExpenseSplitWithPersons.map { it.toEntity() },
        )

        return id.takeIf { it != -1L }?.let { expense.copy(expenseId = it) } ?: expense
    }

    override suspend fun upsert(event: Event, expenses: List<Expense>): List<Expense> {
        return transactionHelper.immediateWriteTransaction {
            expenses.map { expense -> upsert(event, expense) }
        }
    }

    override suspend fun updateExpenseSplitExchangedAmount(
        expenseSplitId: Long,
        exchangedAmount: BigDecimal
    ): Boolean {
        return expensesDao.updateExpenseSplitExchangedAmount(
            expenseSplitId = expenseSplitId,
            exchangedAmountUnscaled = exchangedAmount.significand,
            exchangedAmountScale = exchangedAmount.exponent,
        ) >= 1
    }

    override suspend fun updateExpenseServerId(expenseId: Long, serverId: String): Boolean {
        return expensesDao.updateExpenseServerId(expenseId, serverId) >= 1
    }

}
