package com.inwords.expenses.benchmarks.databases.benchmark.template

import androidx.benchmark.junit4.BenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inwords.expenses.benchmarks.databases.benchmark.core.DatabaseBenchmarkScenarioDefaults
import com.inwords.expenses.benchmarks.databases.benchmark.core.DatabaseBenchmarkSupport
import com.inwords.expenses.benchmarks.databases.benchmark.core.DbAbsoluteMetricCase
import com.inwords.expenses.benchmarks.databases.benchmark.core.DbBenchmarkCaseRunner
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Ignore("Template class. Copy, rename, and remove @Ignore for real benchmark research.")
@RunWith(AndroidJUnit4::class)
internal class DatabaseResearchBenchmarkTemplateTest {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun benchmarkTemplateAbsolute() {
        DbBenchmarkCaseRunner.runAbsolute(
            benchmarkRule = benchmarkRule,
            context = targetContext,
            benchmarkCase = DbAbsoluteMetricCase(
                tag = TAG,
                aspect = "template_read",
                metric = "baseline",
                label = "template_label",
                dbName = "db_template_absolute.db",
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

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val TAG = "DatabaseResearchBenchmarkTemplate"
    }

}
