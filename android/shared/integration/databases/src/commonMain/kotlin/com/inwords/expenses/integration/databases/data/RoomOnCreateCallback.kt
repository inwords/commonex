package com.inwords.expenses.integration.databases.data

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.inwords.expenses.feature.events.data.db.entity.CurrencyRatesMetadataEntity
import com.inwords.expenses.feature.events.domain.model.SeededCurrencies

internal class RoomOnCreateCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        SeededCurrencies.all.forEach { currency ->
            connection.execSQL(
                "INSERT INTO currency " +
                    "(currency_id, currency_server_id, code, name, rate_unscaled, rate_scale) " +
                    "VALUES (${currency.id}, NULL, '${currency.code}', '${currency.name}', " +
                    "${currency.rateUnscaledSqlLiteral}, ${currency.rateScale})"
            )
        }
        connection.execSQL(
            "INSERT OR IGNORE INTO currency_rates_metadata " +
                "(currency_rates_metadata_id, etag, last_rates_update_utc_date) " +
                "VALUES (${CurrencyRatesMetadataEntity.SINGLETON_ID}, NULL, '${SeededCurrencyMetadata.snapshotLocalDate}')"
        )
    }
}
