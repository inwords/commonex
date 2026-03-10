package com.inwords.expenses.feature.events.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = CurrencyRatesMetadataEntity.TABLE_NAME)
data class CurrencyRatesMetadataEntity(
    @PrimaryKey
    @ColumnInfo(ColumnNames.ID)
    val id: Int,

    @ColumnInfo(ColumnNames.ETAG)
    val eTag: String?,

    @ColumnInfo(ColumnNames.LAST_RATES_UPDATE_UTC_DATE)
    val lastRatesUpdateUtcDate: String,
) {
    companion object {
        const val TABLE_NAME = "currency_rates_metadata"
        const val SINGLETON_ID = 1
    }

    object ColumnNames {
        const val ID = "currency_rates_metadata_id"

        const val ETAG = "etag"
        const val LAST_RATES_UPDATE_UTC_DATE = "last_rates_update_utc_date"
    }
}
