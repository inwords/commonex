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
internal class DbSynchronousMixedBench {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun measureSynchronousFullMixedAbsolute() {
        DbBenchmarkCaseRunner.runAbsolute(
            benchmarkRule = benchmarkRule,
            context = targetContext,
            benchmarkCase = DbAbsoluteMetricCase(
                tag = TAG,
                aspect = "synchronous_mixed_read_write",
                metric = "variant",
                label = "synchronous_full",
                dbName = "db_sync_mixed_full.db",
                pragmas = DatabaseBenchmarkScenarioDefaults.synchronousFull,
                seedConfig = DatabaseBenchmarkScenarioDefaults.commonSeedConfig,
                work = { db, snapshot ->
                    DatabaseBenchmarkSupport.mixedReadWriteExpenseFeed(
                        db = db,
                        snapshot = snapshot,
                        chunks = DatabaseBenchmarkScenarioDefaults.MIXED_CHUNKS,
                        readsPerChunk = DatabaseBenchmarkScenarioDefaults.MIXED_READS_PER_CHUNK,
                        writesPerChunk = DatabaseBenchmarkScenarioDefaults.MIXED_WRITES_PER_CHUNK,
                        namePrefix = "full-mixed",
                    )
                },
                verifyPragmas = { db ->
                    DatabaseBenchmarkSupport.verifyPragmaEquals(db, "synchronous", 2)
                },
            ),
        )
    }

    @Test
    fun measureSynchronousNormalMixedAbsolute() {
        DbBenchmarkCaseRunner.runAbsolute(
            benchmarkRule = benchmarkRule,
            context = targetContext,
            benchmarkCase = DbAbsoluteMetricCase(
                tag = TAG,
                aspect = "synchronous_mixed_read_write",
                metric = "baseline",
                label = "synchronous_normal",
                dbName = "db_sync_mixed_normal.db",
                pragmas = DatabaseBenchmarkScenarioDefaults.synchronousNormal,
                seedConfig = DatabaseBenchmarkScenarioDefaults.commonSeedConfig,
                work = { db, snapshot ->
                    DatabaseBenchmarkSupport.mixedReadWriteExpenseFeed(
                        db = db,
                        snapshot = snapshot,
                        chunks = DatabaseBenchmarkScenarioDefaults.MIXED_CHUNKS,
                        readsPerChunk = DatabaseBenchmarkScenarioDefaults.MIXED_READS_PER_CHUNK,
                        writesPerChunk = DatabaseBenchmarkScenarioDefaults.MIXED_WRITES_PER_CHUNK,
                        namePrefix = "normal-mixed",
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
        private const val TAG = "DbSynchronousMixedBench"
    }

}
