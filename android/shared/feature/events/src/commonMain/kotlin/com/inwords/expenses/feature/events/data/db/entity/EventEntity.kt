package com.inwords.expenses.feature.events.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = EventEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = [CurrencyEntity.ColumnNames.ID],
            childColumns = [EventEntity.ColumnNames.PRIMARY_CURRENCY],
            onDelete = ForeignKey.RESTRICT
        ),
    ],
    indices = [
        Index(value = [EventEntity.ColumnNames.PRIMARY_CURRENCY]),
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(ColumnNames.ID)
    val eventId: Long = 0L,

    @ColumnInfo(ColumnNames.SERVER_ID)
    val eventServerId: String?,

    @ColumnInfo(ColumnNames.CLIENT_CREATE_ID)
    val clientCreateId: String,

    @ColumnInfo(ColumnNames.NAME)
    val name: String,

    @ColumnInfo(ColumnNames.PIN_CODE)
    val pinCode: String,

    @ColumnInfo(ColumnNames.PRIMARY_CURRENCY)
    val primaryCurrencyId: Long,
) {
    companion object {

        const val TABLE_NAME = "event"
    }

    object ColumnNames {

        const val ID = "event_id"
        const val SERVER_ID = "event_server_id"
        const val CLIENT_CREATE_ID = "client_create_id"
        const val NAME = "name"
        const val PIN_CODE = "pin_code"
        const val PRIMARY_CURRENCY = "primary_currency_id"
    }
}
