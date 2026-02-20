package com.inwords.expenses.benchmarks.databases.benchmark.scenarios.transactions

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
internal class DbTransactionNestingBench {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun measureTopLevelTransactionsAbsolute() {
        DbBenchmarkCaseRunner.runAbsolute(
            benchmarkRule = benchmarkRule,
            context = targetContext,
            benchmarkCase = DbAbsoluteMetricCase(
                tag = TAG,
                aspect = "transaction_nesting",
                metric = "baseline",
                label = "top_level_${WORKLOAD_LABEL}",
                dbName = "db_tx_nesting_top_level.db",
                pragmas = DatabaseBenchmarkScenarioDefaults.pragmasBaseline,
                seedConfig = DatabaseBenchmarkScenarioDefaults.commonSeedConfig,
                work = { db, snapshot ->
                    DatabaseBenchmarkSupport.writeExpensesWithTopLevelTransactions(
                        db = db,
                        snapshot = snapshot,
                        transactionCount = DatabaseBenchmarkScenarioDefaults.TRANSACTION_COUNT,
                        insertsPerTransaction = DatabaseBenchmarkScenarioDefaults.INSERTS_PER_TRANSACTION,
                        namePrefix = "top-level",
                    )
                },
                verifyPragmas = { db ->
                    DatabaseBenchmarkSupport.verifyPragmaEquals(db, "synchronous", 1)
                },
            ),
        )
    }

    @Test
    fun measureNestedTransactionsAbsolute() {
        DbBenchmarkCaseRunner.runAbsolute(
            benchmarkRule = benchmarkRule,
            context = targetContext,
            benchmarkCase = DbAbsoluteMetricCase(
                tag = TAG,
                aspect = "transaction_nesting",
                metric = "variant",
                label = "nested_${WORKLOAD_LABEL}",
                dbName = "db_tx_nesting_nested.db",
                pragmas = DatabaseBenchmarkScenarioDefaults.pragmasBaseline,
                seedConfig = DatabaseBenchmarkScenarioDefaults.commonSeedConfig,
                work = { db, snapshot ->
                    DatabaseBenchmarkSupport.writeExpensesWithNestedTransactions(
                        db = db,
                        snapshot = snapshot,
                        transactionCount = DatabaseBenchmarkScenarioDefaults.TRANSACTION_COUNT,
                        insertsPerTransaction = DatabaseBenchmarkScenarioDefaults.INSERTS_PER_TRANSACTION,
                        namePrefix = "nested",
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
        private const val TAG = "DbTransactionNestingBench"
        private const val WORKLOAD_LABEL =
            "${DatabaseBenchmarkScenarioDefaults.TRANSACTION_COUNT}x" +
                "${DatabaseBenchmarkScenarioDefaults.INSERTS_PER_TRANSACTION}"
    }

}
