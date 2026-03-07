package com.inwords.expenses.integration.databases.data.migration

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.integration.databases.data.AppDatabase
import com.inwords.expenses.integration.databases.data.createAppDatabase
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
                    listOf(
                        CurrencyEntity(1, null, "EUR", "Euro"),
                        CurrencyEntity(2, null, "USD", "US Dollar"),
                        CurrencyEntity(3, null, "RUB", "Russian Ruble"),
                        CurrencyEntity(4, null, "JPY", "Japanese Yen"),
                        CurrencyEntity(5, null, "TRY", "Turkish Lira"),
                        CurrencyEntity(6, null, "AED", "UAE Dirham"),
                    ),
                    currencies
                )
            }

            db.close()
        }
    }
}
