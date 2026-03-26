package com.inwords.expenses.feature.expenses.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.inwords.expenses.core.storage.utils.type_converter.InstantConverter
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.feature.events.data.db.entity.EventEntity
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.inwords.expenses.feature.expenses.data.db.type_converter.ExpenseTypeConverter
import com.inwords.expenses.feature.expenses.domain.model.ExpenseType
import kotlin.time.Instant

@Entity(
    tableName = ExpenseEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = [EventEntity.ColumnNames.ID],
            childColumns = [ExpenseEntity.ColumnNames.EVENT_ID],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = [CurrencyEntity.ColumnNames.ID],
            childColumns = [ExpenseEntity.ColumnNames.CURRENCY_ID],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = [PersonEntity.ColumnNames.ID],
            childColumns = [ExpenseEntity.ColumnNames.PERSON_ID],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = [ExpenseEntity.ColumnNames.EVENT_ID]),
        Index(value = [ExpenseEntity.ColumnNames.CURRENCY_ID]),
        Index(value = [ExpenseEntity.ColumnNames.PERSON_ID]),
    ]
)
@TypeConverters(InstantConverter::class, ExpenseTypeConverter::class)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(ColumnNames.ID)
    val expenseId: Long = 0L,

    @ColumnInfo(ColumnNames.SERVER_ID)
    val serverId: String?,

    @ColumnInfo(ColumnNames.EVENT_ID)
    val eventId: Long,

    @ColumnInfo(ColumnNames.CURRENCY_ID)
    val currencyId: Long,

    @ColumnInfo(ColumnNames.EXPENSE_TYPE)
    val expenseType: ExpenseType,

    @ColumnInfo(ColumnNames.PERSON_ID)
    val personId: Long,

    @ColumnInfo(ColumnNames.IS_CUSTOM_RATE)
    val isCustomRate: Boolean,

    @ColumnInfo(ColumnNames.TIMESTAMP)
    val timestamp: Instant,

    @ColumnInfo(ColumnNames.DESCRIPTION)
    val description: String,
) {

    companion object {

        const val TABLE_NAME = "expense"
    }

    object ColumnNames {

        const val ID = "expense_id"
        const val SERVER_ID = "expense_server_id"
        const val EVENT_ID = "event_id"
        const val CURRENCY_ID = "currency_id"
        const val EXPENSE_TYPE = "expense_type"
        const val PERSON_ID = "person_id"
        const val IS_CUSTOM_RATE = "is_custom_rate"
        const val TIMESTAMP = "timestamp"
        const val DESCRIPTION = "description"
    }
}
