package com.inwords.expenses.integration.databases.data.benchmark

import android.util.Log
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inwords.expenses.integration.databases.data.AppDatabase
import com.inwords.expenses.integration.databases.data.createAppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@Ignore("Template class. Copy, rename, and remove @Ignore for real benchmark research.")
@RunWith(AndroidJUnit4::class)
internal class DatabaseResearchBenchmarkTemplateTest {

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
    )

    @Test
    fun benchmarkTemplate() = runBlocking {
        val dbName = "template_research.db"
        targetContext.deleteDatabase(dbName)

        helper.createDatabase(dbName, 2).close()

        val db = createAppDatabase(
            builder = Room.databaseBuilder<AppDatabase>(
                context = targetContext,
                name = dbName,
            )
        )

        try {
            repeat(30) {
                db.currenciesDao().queryAll().first()
            }

            val sampleNs = measureNanoTime {
                repeat(100) {
                    db.currenciesDao().queryAll().first()
                }
            }

            Log.i(TAG, "template sampleMs=${sampleNs / 1_000_000.0}")
            assertTrue(sampleNs > 0L)
        } finally {
            db.close()
            targetContext.deleteDatabase(dbName)
        }
    }

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val TAG = "RoomResearchTemplate"
    }
}
