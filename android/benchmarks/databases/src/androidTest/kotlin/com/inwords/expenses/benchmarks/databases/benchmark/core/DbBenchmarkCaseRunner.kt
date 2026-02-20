package com.inwords.expenses.benchmarks.databases.benchmark.core

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import com.inwords.expenses.benchmarks.databases.data.BenchAppDatabase
import org.junit.Assert.assertTrue

internal data class DbAbsoluteMetricCase(
    val tag: String,
    val aspect: String,
    val metric: String,
    val label: String,
    val dbName: String,
    val pragmas: List<String>,
    val seedConfig: DatabaseBenchmarkSupport.SeedDatasetConfig,
    val work: (BenchAppDatabase, DatabaseBenchmarkSupport.SeedSnapshot) -> Long,
    val verifyPragmas: (db: BenchAppDatabase) -> Unit = {},
)

internal object DbBenchmarkCaseRunner {

    fun runAbsolute(
        benchmarkRule: BenchmarkRule,
        context: Context,
        benchmarkCase: DbAbsoluteMetricCase,
    ) {
        val db = DatabaseBenchmarkSupport.createDatabase(
            context = context,
            dbName = benchmarkCase.dbName,
            pragmasOnOpen = benchmarkCase.pragmas,
        )

        try {
            benchmarkCase.verifyPragmas(db)
            val snapshot = prepareRunState(
                db = db,
                seedConfig = benchmarkCase.seedConfig,
                namePrefix = benchmarkCase.dbName,
            )

            var lastWorkResult = 0L

            benchmarkRule.measureRepeated {
                lastWorkResult = benchmarkCase.work(db, snapshot)
            }

            assertTrue("Benchmark work result should be positive", lastWorkResult > 0L)

            DatabaseBenchmarkSupport.logCaseResult(
                tag = benchmarkCase.tag,
                aspect = benchmarkCase.aspect,
                metric = benchmarkCase.metric,
                label = benchmarkCase.label,
            )
        } finally {
            db.close()
            DatabaseBenchmarkSupport.deleteDatabaseArtifacts(context, benchmarkCase.dbName)
        }
    }

    private fun prepareRunState(
        db: BenchAppDatabase,
        seedConfig: DatabaseBenchmarkSupport.SeedDatasetConfig,
        namePrefix: String,
    ): DatabaseBenchmarkSupport.SeedSnapshot {
        val snapshot = DatabaseBenchmarkSupport.seedDataset(
            db = db,
            config = seedConfig,
            namePrefix = "$namePrefix-seed",
        )
        DatabaseBenchmarkSupport.checkpointWalTruncate(db)
        return snapshot
    }

}
