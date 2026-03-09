package com.inwords.expenses.integration.databases.data.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.feature.events.domain.model.SeededCurrencies
import com.inwords.expenses.integration.databases.data.AppDatabase
import com.inwords.expenses.integration.databases.data.SeededCurrencyMetadata
import com.inwords.expenses.integration.databases.data.createAppDatabase
import com.inwords.expenses.integration.databases.data.rateScale
import com.inwords.expenses.integration.databases.data.rateUnscaled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Database migration tests. For each new migration in the migrations/ folder,
 * add a test that creates the DB at the previous version (with seed data if required),
 * runs the migration via createAppDatabase, and asserts schema and data.
 */
@RunWith(AndroidJUnit4::class)
internal class MigrationTest {

    private val testDb = "app_db.db"
    private val focusedMigrationDb = "app_db_migration_2_3.db"

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create the earliest version of the database.
        helper.createDatabase(testDb, 1).apply {
            execSQL("INSERT INTO currency (currency_id, currency_server_id, code, name) VALUES (1, NULL, 'EUR', 'Euro')")
            execSQL("INSERT INTO currency (currency_id, currency_server_id, code, name) VALUES (2, NULL, 'USD', 'US Dollar')")
            execSQL("INSERT INTO currency (currency_id, currency_server_id, code, name) VALUES (3, NULL, 'RUB', 'Russian Ruble')")
            execSQL("INSERT INTO currency (currency_id, currency_server_id, code, name) VALUES (4, NULL, 'JPY', 'Japanese Yen')")
            execSQL("INSERT INTO currency (currency_id, currency_server_id, code, name) VALUES (5, NULL, 'TRY', 'Turkish Lira')")

            close()
        }

        // Open latest version of the database. Room validates the schema
        // once all migrations execute.
        createAppDatabase(
            Room.databaseBuilder<AppDatabase>(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                name = testDb
            )
        ).also { db ->
            runBlocking {
                val currencies = db.currenciesDao().queryAll().first()
                assertEquals(
                    SeededCurrencies.all.map { currency ->
                        CurrencyEntity(
                            currencyId = currency.id,
                            currencyServerId = null,
                            code = currency.code,
                            name = currency.name,
                            rateUnscaled = currency.rateUnscaled,
                            rateScale = currency.rateScale,
                        )
                    },
                    currencies
                )

                assertEquals(
                    null,
                    db.currencyRatesMetadataDao().queryETag()
                )
                assertEquals(
                    SeededCurrencyMetadata.snapshotLocalDate.toString(),
                    db.currencyRatesMetadataDao().queryLastRatesUpdateUtcDate()
                )
            }

            db.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3_preservesExistingCurrencyIdentityAndAddsMetadata() {
        helper.createDatabase(focusedMigrationDb, 2).apply {
            execSQL("INSERT INTO currency (currency_id, currency_server_id, code, name) VALUES (10, 'srv-eur', 'EUR', 'Euro legacy')")
            execSQL("INSERT INTO currency (currency_id, currency_server_id, code, name) VALUES (11, NULL, 'USD', 'US Dollar legacy')")
            execSQL("INSERT INTO currency (currency_id, currency_server_id, code, name) VALUES (12, 'srv-aed', 'AED', 'Dirham legacy')")

            close()
        }

        createAppDatabase(
            Room.databaseBuilder<AppDatabase>(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                name = focusedMigrationDb
            )
        ).also { db ->
            runBlocking {
                val currencies = db.currenciesDao().queryAll().first()
                assertEquals(
                    listOf(
                        seededCurrencyEntity(
                            id = 10,
                            serverId = "srv-eur",
                            code = "EUR",
                            name = "Euro legacy",
                        ),
                        seededCurrencyEntity(
                            id = 11,
                            serverId = null,
                            code = "USD",
                            name = "US Dollar legacy",
                        ),
                        seededCurrencyEntity(
                            id = 12,
                            serverId = "srv-aed",
                            code = "AED",
                            name = "Dirham legacy",
                        ),
                    ),
                    currencies
                )

                assertEquals(
                    null,
                    db.currencyRatesMetadataDao().queryETag()
                )
                assertEquals(
                    SeededCurrencyMetadata.snapshotLocalDate.toString(),
                    db.currencyRatesMetadataDao().queryLastRatesUpdateUtcDate()
                )
            }

            db.close()
        }
    }

    private fun seededCurrencyEntity(
        id: Long,
        serverId: String?,
        code: String,
        name: String,
    ): CurrencyEntity {
        val seededCurrency = SeededCurrencies.all.first { it.code == code }
        return CurrencyEntity(
            currencyId = id,
            currencyServerId = serverId,
            code = code,
            name = name,
            rateUnscaled = seededCurrency.rateUnscaled,
            rateScale = seededCurrency.rateScale,
        )
    }
}
