package com.inwords.expenses.integration.databases.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.inwords.expenses.feature.events.data.db.entity.CurrencyRatesMetadataEntity
import com.inwords.expenses.feature.events.domain.model.SeededCurrencies
import com.inwords.expenses.integration.databases.data.SeededCurrencyMetadata
import com.inwords.expenses.integration.databases.data.rateScale
import com.inwords.expenses.integration.databases.data.rateUnscaledSqlLiteral

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `currency` ADD COLUMN `rate_unscaled` BLOB NOT NULL DEFAULT ${SeededCurrencies.USD.rateUnscaledSqlLiteral}"
        )
        connection.execSQL(
            "ALTER TABLE `currency` ADD COLUMN `rate_scale` INTEGER NOT NULL DEFAULT 0"
        )

        SeededCurrencies.all.forEach { currency ->
            connection.execSQL(
                "UPDATE `currency` SET `rate_unscaled` = ${currency.rateUnscaledSqlLiteral}, " +
                    "`rate_scale` = ${currency.rateScale} WHERE `code` = '${currency.code}'"
            )
        }

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `currency_rates_metadata` (
                `currency_rates_metadata_id` INTEGER NOT NULL,
                `etag` TEXT,
                `last_rates_update_utc_date` TEXT NOT NULL,
                PRIMARY KEY(`currency_rates_metadata_id`)
            )
            """.trimIndent()
        )
        connection.execSQL(
            "INSERT OR IGNORE INTO `currency_rates_metadata` " +
                "(`currency_rates_metadata_id`, `etag`, `last_rates_update_utc_date`) " +
                "VALUES (${CurrencyRatesMetadataEntity.SINGLETON_ID}, NULL, '${SeededCurrencyMetadata.snapshotLocalDate}')"
        )
    }
}
