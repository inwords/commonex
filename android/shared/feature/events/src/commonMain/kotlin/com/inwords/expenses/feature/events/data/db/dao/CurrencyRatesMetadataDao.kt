package com.inwords.expenses.feature.events.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.inwords.expenses.feature.events.data.db.entity.CurrencyRatesMetadataEntity

@Dao
interface CurrencyRatesMetadataDao {

    @Query(
        "SELECT ${CurrencyRatesMetadataEntity.ColumnNames.ETAG} FROM ${CurrencyRatesMetadataEntity.TABLE_NAME} " +
            "WHERE ${CurrencyRatesMetadataEntity.ColumnNames.ID} = ${CurrencyRatesMetadataEntity.SINGLETON_ID}"
    )
    suspend fun queryETag(): String?

    @Query(
        "SELECT ${CurrencyRatesMetadataEntity.ColumnNames.LAST_RATES_UPDATE_UTC_DATE} FROM ${CurrencyRatesMetadataEntity.TABLE_NAME} " +
            "WHERE ${CurrencyRatesMetadataEntity.ColumnNames.ID} = ${CurrencyRatesMetadataEntity.SINGLETON_ID}"
    )
    suspend fun queryLastRatesUpdateUtcDate(): String

    @Query(
        "UPDATE ${CurrencyRatesMetadataEntity.TABLE_NAME} " +
            "SET ${CurrencyRatesMetadataEntity.ColumnNames.ETAG} = :eTag " +
            "WHERE ${CurrencyRatesMetadataEntity.ColumnNames.ID} = ${CurrencyRatesMetadataEntity.SINGLETON_ID}"
    )
    suspend fun updateETag(eTag: String?)

    @Query(
        "UPDATE ${CurrencyRatesMetadataEntity.TABLE_NAME} " +
            "SET ${CurrencyRatesMetadataEntity.ColumnNames.LAST_RATES_UPDATE_UTC_DATE} = :utcDate " +
            "WHERE ${CurrencyRatesMetadataEntity.ColumnNames.ID} = ${CurrencyRatesMetadataEntity.SINGLETON_ID}"
    )
    suspend fun updateLastRatesUpdateUtcDate(utcDate: String)

}
