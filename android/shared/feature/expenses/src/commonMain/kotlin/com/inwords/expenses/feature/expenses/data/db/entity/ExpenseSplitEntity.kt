package com.inwords.expenses.feature.expenses.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.inwords.expenses.core.storage.utils.type_converter.BigIntegerConverter
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.ionspin.kotlin.bignum.integer.BigInteger

@Entity(
    tableName = ExpenseSplitEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = [ExpenseEntity.ColumnNames.ID],
            childColumns = [ExpenseSplitEntity.ColumnNames.EXPENSE_ID],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = [PersonEntity.ColumnNames.ID],
            childColumns = [ExpenseSplitEntity.ColumnNames.PERSON_ID],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = [ExpenseSplitEntity.ColumnNames.PERSON_ID]),
        Index(value = [ExpenseSplitEntity.ColumnNames.EXPENSE_ID]),
    ]
)
@TypeConverters(BigIntegerConverter::class)
data class ExpenseSplitEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(ColumnNames.ID)
    val expenseSplitId: Long = 0L,

    @ColumnInfo(ColumnNames.EXPENSE_ID)
    val expenseId: Long,

    @ColumnInfo(ColumnNames.PERSON_ID)
    val personId: Long,

    @ColumnInfo(ColumnNames.ORIGINAL_AMOUNT_UNSCALED)
    val originalAmountUnscaled: BigInteger,

    @ColumnInfo(ColumnNames.ORIGINAL_AMOUNT_SCALE)
    val originalAmountScale: Long,

    @ColumnInfo(name = ColumnNames.EXCHANGED_AMOUNT_UNSCALED)
    val exchangedAmountUnscaled: BigInteger,

    @ColumnInfo(name = ColumnNames.EXCHANGED_AMOUNT_SCALE)
    val exchangedAmountScale: Long
) {

    companion object {

        const val TABLE_NAME = "expense_split"
    }

    object ColumnNames {

        const val ID = "expense_split_id"
        const val EXPENSE_ID = "expense_id"
        const val PERSON_ID = "person_id"
        const val ORIGINAL_AMOUNT_UNSCALED = "original_amount_unscaled"
        const val ORIGINAL_AMOUNT_SCALE = "original_amount_scale"
        const val EXCHANGED_AMOUNT_UNSCALED = "exchanged_amount_unscaled"
        const val EXCHANGED_AMOUNT_SCALE = "exchanged_amount_scale"
    }
}
