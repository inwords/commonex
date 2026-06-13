package com.inwords.expenses.feature.expenses.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.inwords.expenses.core.storage.utils.type_converter.BigIntegerConverter
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseSplitEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseWithDetailsQuery
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(BigIntegerConverter::class)
interface ExpensesDao {

    @Transaction
    suspend fun upsert(expenseEntity: ExpenseEntity, subjectPersonSplitEntities: List<ExpenseSplitEntity>): Long {
        val expenseId = upsert(expenseEntity)

        upsert(
            subjectPersonSplitEntities.map { personSplit ->
                personSplit.copy(expenseId = expenseId)
            }
        )

        return expenseId
    }

    @Upsert
    suspend fun upsert(expenseEntity: ExpenseEntity): Long

    @Upsert
    suspend fun upsert(subjectPersonSplitEntities: List<ExpenseSplitEntity>)

    @Query("DELETE FROM ${ExpenseEntity.TABLE_NAME} WHERE ${ExpenseEntity.ColumnNames.ID} = :expenseId")
    suspend fun deleteExpense(expenseId: Long): Int

    @Query("DELETE FROM ${ExpenseEntity.TABLE_NAME} WHERE ${ExpenseEntity.ColumnNames.ID} IN (:expenseIds)")
    suspend fun deleteExpenses(expenseIds: List<Long>): Int

    @Query(
        "SELECT EXISTS(" +
            "SELECT 1 FROM ${ExpenseEntity.TABLE_NAME} " +
            "WHERE ${ExpenseEntity.ColumnNames.REVERTS_EXPENSE_ID} = :expenseId " +
            "OR ${ExpenseEntity.ColumnNames.REPLACES_EXPENSE_ID} = :expenseId" +
            ")",
    )
    suspend fun hasCorrectionFor(expenseId: Long): Boolean

    @Query(
        "SELECT EXISTS(" +
            "SELECT 1 FROM ${ExpenseEntity.TABLE_NAME} " +
            "WHERE ${ExpenseEntity.ColumnNames.REVERTS_EXPENSE_ID} = :expenseId " +
            "OR ${ExpenseEntity.ColumnNames.REPLACES_EXPENSE_ID} = :expenseId" +
            ")",
    )
    fun hasCorrectionForFlow(expenseId: Long): Flow<Boolean>

    @Transaction
    @Query(
        "SELECT * FROM ${ExpenseEntity.TABLE_NAME} " +
            "WHERE ${ExpenseEntity.ColumnNames.REVERTS_EXPENSE_ID} = :targetExpenseId " +
            "OR ${ExpenseEntity.ColumnNames.REPLACES_EXPENSE_ID} = :targetExpenseId " +
            "LIMIT 1",
    )
    fun queryCorrectionForTargetFlow(targetExpenseId: Long): Flow<ExpenseWithDetailsQuery?>

    @Query(
        "UPDATE ${ExpenseSplitEntity.TABLE_NAME} SET " +
            "${ExpenseSplitEntity.ColumnNames.EXCHANGED_AMOUNT_UNSCALED} = " +
            ":exchangedAmountUnscaled, " +
            "${ExpenseSplitEntity.ColumnNames.EXCHANGED_AMOUNT_SCALE} = " +
            ":exchangedAmountScale " +
            "WHERE ${ExpenseSplitEntity.ColumnNames.ID} = :expenseSplitId"
    )
    suspend fun updateExpenseSplitExchangedAmount(
        expenseSplitId: Long,
        exchangedAmountUnscaled: BigInteger,
        exchangedAmountScale: Long
    ): Int

    @Query(
        "UPDATE ${ExpenseEntity.TABLE_NAME} SET ${ExpenseEntity.ColumnNames.SERVER_ID} = :serverId " +
            "WHERE ${ExpenseEntity.ColumnNames.ID} = :expenseId"
    )
    suspend fun updateExpenseServerId(expenseId: Long, serverId: String): Int

    @Query(
        "UPDATE ${ExpenseEntity.TABLE_NAME} SET " +
            "${ExpenseEntity.ColumnNames.REVERTS_EXPENSE_ID} = :revertsExpenseId, " +
            "${ExpenseEntity.ColumnNames.REPLACES_EXPENSE_ID} = :replacesExpenseId " +
            "WHERE ${ExpenseEntity.ColumnNames.ID} = :expenseId"
    )
    suspend fun updateExpenseCorrectionLinks(
        expenseId: Long,
        revertsExpenseId: Long?,
        replacesExpenseId: Long?,
    ): Int

    @Transaction
    @Query(
        "SELECT * FROM ${ExpenseEntity.TABLE_NAME} " +
            "WHERE ${ExpenseEntity.ColumnNames.EVENT_ID} = :eventId " +
            "ORDER BY ${ExpenseEntity.ColumnNames.TIMESTAMP} DESC, ${ExpenseEntity.ColumnNames.ID} DESC"
    )
    fun queryByEventIdFlow(eventId: Long): Flow<List<ExpenseWithDetailsQuery>>

    @Transaction
    @Query(
        "SELECT * FROM ${ExpenseEntity.TABLE_NAME} " +
            "WHERE ${ExpenseEntity.ColumnNames.EVENT_ID} = :eventId " +
            "ORDER BY ${ExpenseEntity.ColumnNames.TIMESTAMP} DESC, ${ExpenseEntity.ColumnNames.ID} DESC"
    )
    suspend fun queryByEventId(eventId: Long): List<ExpenseWithDetailsQuery>

    @Transaction
    @Query("SELECT * FROM ${ExpenseEntity.TABLE_NAME} WHERE ${ExpenseEntity.ColumnNames.ID} = :expenseId")
    fun queryByIdFlow(expenseId: Long): Flow<ExpenseWithDetailsQuery?>

    @Transaction
    @Query("SELECT * FROM ${ExpenseEntity.TABLE_NAME} WHERE ${ExpenseEntity.ColumnNames.ID} = :expenseId")
    suspend fun queryById(expenseId: Long): ExpenseWithDetailsQuery?

}
