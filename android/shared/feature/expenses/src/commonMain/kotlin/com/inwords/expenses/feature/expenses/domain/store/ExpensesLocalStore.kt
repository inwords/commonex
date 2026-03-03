package com.inwords.expenses.feature.expenses.domain.store

import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.expenses.domain.model.Expense
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.flow.Flow

interface ExpensesLocalStore {

    fun getExpensesFlow(eventId: Long): Flow<List<Expense>>

    suspend fun getExpenses(eventId: Long): List<Expense>

    fun getExpenseFlow(expenseId: Long): Flow<Expense?>

    suspend fun getExpense(expenseId: Long): Expense?

    suspend fun upsert(event: Event, expense: Expense): Expense

    suspend fun upsert(event: Event, expenses: List<Expense>): List<Expense>

    suspend fun updateExpenseSplitExchangedAmount(expenseSplitId: Long, exchangedAmount: BigDecimal): Boolean

    suspend fun updateExpenseServerId(expenseId: Long, serverId: String): Boolean
}
