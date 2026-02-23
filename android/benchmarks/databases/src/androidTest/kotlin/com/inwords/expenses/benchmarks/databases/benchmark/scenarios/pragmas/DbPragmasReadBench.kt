package com.inwords.expenses.benchmarks.databases.benchmark.scenarios.pragmas

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
internal class DbPragmasReadBench {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun measureBaselineReadNoExtraPragmas() {
        DbBenchmarkCaseRunner.runAbsolute(
            benchmarkRule = benchmarkRule,
            context = targetContext,
            benchmarkCase = DbAbsoluteMetricCase(
                tag = TAG,
                aspect = "pragmas_read_isolated",
                metric = "baseline",
                label = "no_extra_pragmas",
                dbName = "db_pragmas_read_baseline.db",
                pragmas = DatabaseBenchmarkScenarioDefaults.pragmasBaseline,
                seedConfig = DatabaseBenchmarkScenarioDefaults.commonSeedConfig,
                work = { db, snapshot ->
                    DatabaseBenchmarkSupport.readExpenseFeed(
                        db = db,
                        snapshot = snapshot,
                        queryLoops = DatabaseBenchmarkScenarioDefaults.READ_QUERY_LOOPS,
                    )
                },
                verifyPragmas = { db ->
                    DatabaseBenchmarkSupport.verifyPragmaEquals(db, "synchronous", 1)
                },
            ),
        )
    }

    @Test
    fun measureVariantReadWithExtraPragmas() {
        DbBenchmarkCaseRunner.runAbsolute(
            benchmarkRule = benchmarkRule,
            context = targetContext,
            benchmarkCase = DbAbsoluteMetricCase(
                tag = TAG,
                aspect = "pragmas_read_isolated",
                metric = "variant",
                label = "cache_mmap_journal_limit",
                dbName = "db_pragmas_read_variant.db",
                pragmas = DatabaseBenchmarkScenarioDefaults.pragmasWithExtras,
                seedConfig = DatabaseBenchmarkScenarioDefaults.commonSeedConfig,
                work = { db, snapshot ->
                    DatabaseBenchmarkSupport.readExpenseFeed(
                        db = db,
                        snapshot = snapshot,
                        queryLoops = DatabaseBenchmarkScenarioDefaults.READ_QUERY_LOOPS,
                    )
                },
                verifyPragmas = { db ->
                    DatabaseBenchmarkSupport.verifyPragmaEquals(db, "synchronous", 1)
                    DatabaseBenchmarkSupport.verifyPragmaEquals(db, "cache_size", -8_192)
                    DatabaseBenchmarkSupport.verifyPragmaAtLeast(db, "mmap_size", 1)
                    DatabaseBenchmarkSupport.verifyPragmaAtLeast(db, "journal_size_limit", 1)
                },
            ),
        )
    }

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        private const val TAG = "DbPragmasReadBench"
    }

}
