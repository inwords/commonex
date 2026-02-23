package com.inwords.expenses.benchmarks.databases.benchmark.scenarios.synchronous

import androidx.benchmark.junit4.BenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inwords.expenses.benchmarks.databases.benchmark.core.DatabaseBenchmarkScenarioDefaults
import com.inwords.expenses.benchmarks.databases.benchmark.core.DatabaseBenchmarkSupport
import com.inwords.expenses.benchmarks.databases.benchmark.core.DbAbsoluteMetricCase
import com.inwords.expenses.benchmarks.databases.benchmark.core.DbBenchmarkCaseRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class DbSynchronousWriteBench {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun measureSynchronousFullWriteAbsolute() {
        DbBenchmarkCaseRunner.runAbsolute(
            benchmarkRule = benchmarkRule,
            context = targetContext,
            benchmarkCase = DbAbsoluteMetricCase(
                tag = TAG,
                aspect = "synchronous_write_isolated",
                metric = "variant",
                label = "synchronous_full",
                dbName = "db_sync_write_full.db",
                pragmas = DatabaseBenchmarkScenarioDefaults.synchronousFull,
                seedConfig = DatabaseBenchmarkScenarioDefaults.commonSeedConfig,
                work = { db, snapshot ->
                    DatabaseBenchmarkSupport.writeExpensesInSingleTransaction(
                        db = db,
                        snapshot = snapshot,
                        count = DatabaseBenchmarkScenarioDefaults.ISOLATED_WRITE_UPDATE_COUNT,
                        namePrefix = "full-write",
                    )
                },
                verifyPragmas = { db ->
                    DatabaseBenchmarkSupport.verifyPragmaEquals(db, "synchronous", 2)
                },
            ),
        )
    }

    @Test
    fun measureSynchronousNormalWriteAbsolute() {
        DbBenchmarkCaseRunner.runAbsolute(
            benchmarkRule = benchmarkRule,
            context = targetContext,
            benchmarkCase = DbAbsoluteMetricCase(
                tag = TAG,
                aspect = "synchronous_write_isolated",
                metric = "baseline",
                label = "synchronous_normal",
                dbName = "db_sync_write_normal.db",
                pragmas = DatabaseBenchmarkScenarioDefaults.synchronousNormal,
                seedConfig = DatabaseBenchmarkScenarioDefaults.commonSeedConfig,
                work = { db, snapshot ->
                    DatabaseBenchmarkSupport.writeExpensesInSingleTransaction(
                        db = db,
                        snapshot = snapshot,
                        count = DatabaseBenchmarkScenarioDefaults.ISOLATED_WRITE_UPDATE_COUNT,
                        namePrefix = "normal-write",
                    )
                },
                verifyPragmas = { db ->
                    DatabaseBenchmarkSupport.verifyPragmaEquals(db, "synchronous", 1)
                },
            ),
        )
    }

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        private const val TAG = "DbSynchronousWriteBench"
    }

}
