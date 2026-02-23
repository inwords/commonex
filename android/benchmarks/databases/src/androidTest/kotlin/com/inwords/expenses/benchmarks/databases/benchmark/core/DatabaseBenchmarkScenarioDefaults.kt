package com.inwords.expenses.benchmarks.databases.benchmark.core

internal object DatabaseBenchmarkScenarioDefaults {

    val commonSeedConfig = DatabaseBenchmarkSupport.SeedDatasetConfig(
        eventCount = 32,
        personsPerEvent = 12,
        initialExpensesPerEvent = 240,
    )

    val pragmasBaseline = listOf(
        "synchronous=NORMAL",
    )

    val pragmasWithExtras = listOf(
        "synchronous=NORMAL",
        "cache_size=-8192",
        "mmap_size=134217728",
        "journal_size_limit=67108864",
    )

    val synchronousFull = listOf(
        "synchronous=FULL",
    )

    val synchronousNormal = listOf(
        "synchronous=NORMAL",
    )

    const val READ_QUERY_LOOPS = 10
    const val ISOLATED_WRITE_UPDATE_COUNT = 10
    const val MIXED_CHUNKS = 5
    const val MIXED_READS_PER_CHUNK = 4
    const val MIXED_WRITES_PER_CHUNK = 3
    const val TRANSACTION_COUNT = 50
    const val INSERTS_PER_TRANSACTION = 1
}
