package com.inwords.expenses.benchmarks.databases.benchmark.core

import android.content.Context
import android.util.Log
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import com.inwords.expenses.benchmarks.databases.data.BenchAppDatabase
import com.inwords.expenses.benchmarks.databases.data.BenchEventEntity
import com.inwords.expenses.benchmarks.databases.data.BenchExpenseEntity
import com.inwords.expenses.benchmarks.databases.data.BenchPersonEntity
import com.inwords.expenses.benchmarks.databases.data.createBenchAppDatabase
import kotlinx.coroutines.runBlocking
import java.io.File

internal object DatabaseBenchmarkSupport {

    private const val CASE_RESULT_PREFIX = "DB_BENCH_CASE"

    private val categories = listOf(
        "food",
        "transport",
        "rent",
        "travel",
        "services",
        "misc",
    )

    data class SeedDatasetConfig(
        val eventCount: Int,
        val personsPerEvent: Int,
        val initialExpensesPerEvent: Int,
    )

    data class SeedSnapshot(
        val eventIds: List<Long>,
        val payerIdsByEvent: Map<Long, List<Long>>,
        val expenseIdsByEvent: Map<Long, List<Long>>,
        val boundedWriteExpenseIds: List<Long>,
        val initialExpenseCount: Int,
    )

    fun createDatabase(
        context: Context,
        dbName: String,
        pragmasOnOpen: List<String>,
    ): BenchAppDatabase {
        deleteDatabaseArtifacts(context, dbName)
        return createBenchAppDatabase(
            context = context,
            dbName = dbName,
            pragmasOnOpen = pragmasOnOpen,
        )
    }

    fun deleteDatabaseArtifacts(context: Context, dbName: String) {
        context.deleteDatabase(dbName)
        val dbPath = context.getDatabasePath(dbName).absolutePath
        File("${dbPath}-wal").delete()
        File("${dbPath}-shm").delete()
    }

    fun seedDataset(
        db: BenchAppDatabase,
        config: SeedDatasetConfig,
        namePrefix: String,
    ): SeedSnapshot {
        val dao = db.benchDataDao()
        lateinit var snapshot: SeedSnapshot

        db.runInTransaction {
            dao.deleteExpenses()
            dao.deletePersons()
            dao.deleteEvents()

            val events = List(config.eventCount) { eventIndex ->
                BenchEventEntity(
                    title = "$namePrefix-event-$eventIndex",
                    ownerName = "$namePrefix-owner-${eventIndex % 16}",
                )
            }
            val eventIds = dao.insertEvents(events)
            check(eventIds.size == config.eventCount) {
                "Unexpected event count: expected=${config.eventCount} actual=${eventIds.size}"
            }

            val persons = buildList(config.eventCount * config.personsPerEvent) {
                repeat(config.eventCount) { eventIndex ->
                    val eventId = eventIds[eventIndex]
                    repeat(config.personsPerEvent) { personIndex ->
                        add(
                            BenchPersonEntity(
                                eventId = eventId,
                                personServerId = null,
                                name = "$namePrefix-person-$eventIndex-$personIndex",
                            )
                        )
                    }
                }
            }
            val personIds = dao.insertPersons(persons)

            val payerIdsByEvent = eventIds.indices.associate { eventIndex ->
                val start = eventIndex * config.personsPerEvent
                val end = start + config.personsPerEvent
                eventIds[eventIndex] to personIds.subList(start, end).toList()
            }

            val initialExpenses = buildList(config.eventCount * config.initialExpensesPerEvent) {
                repeat(config.eventCount) { eventIndex ->
                    val eventId = eventIds[eventIndex]
                    val payerIds = payerIdsByEvent.getValue(eventId)
                    repeat(config.initialExpensesPerEvent) { expenseIndex ->
                        val sequence = eventIndex * config.initialExpensesPerEvent + expenseIndex
                        val payerId = payerIds[(expenseIndex + eventIndex) % payerIds.size]
                        val amountCents = ((sequence % 25_000) + 300).toLong()
                        val category = categories[sequence % categories.size]
                        add(
                            BenchExpenseEntity(
                                eventId = eventId,
                                payerPersonId = payerId,
                                amountCents = amountCents,
                                category = category,
                                note = "$namePrefix-seed-expense-$eventIndex-$expenseIndex",
                                createdAt = sequence.toLong(),
                            )
                        )
                    }
                }
            }
            val expenseIds = dao.insertExpenses(initialExpenses)
            check(expenseIds.size == initialExpenses.size) {
                "Unexpected seeded inserted expense ids: expected=${initialExpenses.size} actual=${expenseIds.size}"
            }

            val expenseIdsByEvent = eventIds.indices.associate { eventIndex ->
                val start = eventIndex * config.initialExpensesPerEvent
                val end = start + config.initialExpensesPerEvent
                eventIds[eventIndex] to expenseIds.subList(start, end).toList()
            }

            val expectedCount = (config.eventCount * config.initialExpensesPerEvent).toLong()
            val actualCount = dao.expenseCount()
            check(actualCount == expectedCount) {
                "Unexpected seeded expense count: expected=$expectedCount actual=$actualCount"
            }

            snapshot = SeedSnapshot(
                eventIds = eventIds,
                payerIdsByEvent = payerIdsByEvent,
                expenseIdsByEvent = expenseIdsByEvent,
                boundedWriteExpenseIds = expenseIds,
                initialExpenseCount = expectedCount.toInt(),
            )
        }

        return snapshot
    }

    fun verifyPragmaEquals(db: BenchAppDatabase, pragmaName: String, expected: Long) = runBlocking {
        val actual = readPragmaLong(db, pragmaName)
        check(actual == expected) {
            "PRAGMA $pragmaName expected=$expected actual=$actual"
        }
    }

    fun verifyPragmaAtLeast(db: BenchAppDatabase, pragmaName: String, minValue: Long) = runBlocking {
        val actual = readPragmaLong(db, pragmaName)
        check(actual >= minValue) {
            "PRAGMA $pragmaName expectedAtLeast=$minValue actual=$actual"
        }
    }

    fun checkpointWalTruncate(db: BenchAppDatabase) = runBlocking {
        db.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { checkpointStmt ->
                check(checkpointStmt.step()) { "wal_checkpoint(TRUNCATE) returned no rows" }
                val busy = checkpointStmt.getLong(0)
                check(busy == 0L) { "wal_checkpoint(TRUNCATE) busy=$busy" }
            }
        }
    }

    fun readExpenseFeed(
        db: BenchAppDatabase,
        snapshot: SeedSnapshot,
        queryLoops: Int,
    ): Long {
        val dao = db.benchDataDao()
        var processedRows = 0L

        repeat(queryLoops) { loopIndex ->
            val eventId = snapshot.eventIds[loopIndex % snapshot.eventIds.size]
            val rows = dao.readExpenseFeed(eventId = eventId)
            check(rows.isNotEmpty()) { "Expected readExpenseFeed to return rows" }
            processedRows += rows.size
            val totalAmount = dao.totalAmountForEvent(eventId)
            check(totalAmount > 0L) { "Expected positive total amount for eventId=$eventId" }
        }

        return processedRows
    }

    fun writeExpensesInSingleTransaction(
        db: BenchAppDatabase,
        snapshot: SeedSnapshot,
        count: Int,
        namePrefix: String,
    ): Long {
        val dao = db.benchDataDao()
        val expenseIds = snapshot.boundedWriteExpenseIds
        check(expenseIds.isNotEmpty()) { "No seeded expense ids available for bounded writes" }
        var updatedCount = 0L

        db.runInTransaction {
            repeat(count) { updateIndex ->
                val expenseId = expenseIds[updateIndex % expenseIds.size]
                val amountCents = ((updateIndex % 40_000) + 500).toLong()
                val category = categories[updateIndex % categories.size]
                val note = "$namePrefix-$updateIndex"
                val updatedRows = dao.updateExpenseById(
                    expenseId = expenseId,
                    amountCents = amountCents,
                    category = category,
                    note = note,
                    createdAt = 1_000_000L + updateIndex,
                )
                check(updatedRows == 1) {
                    "Expected one updated row, actual=$updatedRows expenseId=$expenseId"
                }
                updatedCount += updatedRows.toLong()
            }
        }

        val actual = dao.expenseCount()
        check(actual == snapshot.initialExpenseCount.toLong()) {
            "Unexpected expense count after bounded writes: expected=${snapshot.initialExpenseCount} actual=$actual"
        }

        return updatedCount
    }

    fun writeExpensesWithTopLevelTransactions(
        db: BenchAppDatabase,
        snapshot: SeedSnapshot,
        transactionCount: Int,
        insertsPerTransaction: Int,
        namePrefix: String,
    ): Long {
        val dao = db.benchDataDao()
        val expenseIds = snapshot.boundedWriteExpenseIds
        check(expenseIds.isNotEmpty()) { "No seeded expense ids available for bounded writes" }
        var updatedCount = 0L
        var sequence = 0

        repeat(transactionCount) { txIndex ->
            db.runInTransaction {
                repeat(insertsPerTransaction) { updateIndex ->
                    val ordinal = sequence + updateIndex
                    val expenseId = expenseIds[ordinal % expenseIds.size]
                    val amountCents = ((ordinal % 30_000) + 400).toLong()
                    val category = categories[ordinal % categories.size]
                    val note = "$namePrefix-$txIndex-$updateIndex"
                    val updatedRows = dao.updateExpenseById(
                        expenseId = expenseId,
                        amountCents = amountCents,
                        category = category,
                        note = note,
                        createdAt = 2_000_000L + ordinal,
                    )
                    check(updatedRows == 1) {
                        "Expected one updated row, actual=$updatedRows expenseId=$expenseId"
                    }
                    updatedCount += updatedRows.toLong()
                }
            }
            sequence += insertsPerTransaction
        }

        val actual = dao.expenseCount()
        check(actual == snapshot.initialExpenseCount.toLong()) {
            "Unexpected expense count after top-level bounded writes: expected=${snapshot.initialExpenseCount} actual=$actual"
        }
        return updatedCount
    }

    fun writeExpensesWithNestedTransactions(
        db: BenchAppDatabase,
        snapshot: SeedSnapshot,
        transactionCount: Int,
        insertsPerTransaction: Int,
        namePrefix: String,
    ): Long {
        val dao = db.benchDataDao()
        val expenseIds = snapshot.boundedWriteExpenseIds
        check(expenseIds.isNotEmpty()) { "No seeded expense ids available for bounded writes" }
        var updatedCount = 0L
        var sequence = 0

        db.runInTransaction {
            repeat(transactionCount) { txIndex ->
                db.runInTransaction {
                    repeat(insertsPerTransaction) { updateIndex ->
                        val ordinal = sequence + updateIndex
                        val expenseId = expenseIds[ordinal % expenseIds.size]
                        val amountCents = ((ordinal % 30_000) + 400).toLong()
                        val category = categories[ordinal % categories.size]
                        val note = "$namePrefix-$txIndex-$updateIndex"
                        val updatedRows = dao.updateExpenseById(
                            expenseId = expenseId,
                            amountCents = amountCents,
                            category = category,
                            note = note,
                            createdAt = 3_000_000L + ordinal,
                        )
                        check(updatedRows == 1) {
                            "Expected one updated row, actual=$updatedRows expenseId=$expenseId"
                        }
                        updatedCount += updatedRows.toLong()
                    }
                }
                sequence += insertsPerTransaction
            }
        }

        val actual = dao.expenseCount()
        check(actual == snapshot.initialExpenseCount.toLong()) {
            "Unexpected expense count after nested bounded writes: expected=${snapshot.initialExpenseCount} actual=$actual"
        }
        return updatedCount
    }

    fun mixedReadWriteExpenseFeed(
        db: BenchAppDatabase,
        snapshot: SeedSnapshot,
        chunks: Int,
        readsPerChunk: Int,
        writesPerChunk: Int,
        namePrefix: String,
    ): Long {
        val dao = db.benchDataDao()
        val expenseIdsByEvent = snapshot.expenseIdsByEvent
        var readRowCount = 0L
        var updatedCount = 0L
        var sequence = 0

        repeat(chunks) { chunkIndex ->
            repeat(readsPerChunk) { readIndex ->
                val eventId = snapshot.eventIds[(chunkIndex + readIndex) % snapshot.eventIds.size]
                val rows = dao.readExpenseFeed(eventId = eventId)
                check(rows.isNotEmpty()) { "Expected mixed read rows" }
                readRowCount += rows.size
            }

            val eventId = snapshot.eventIds[chunkIndex % snapshot.eventIds.size]
            val eventExpenseIds = expenseIdsByEvent.getValue(eventId)
            check(eventExpenseIds.isNotEmpty()) { "No seeded event expenses for eventId=$eventId" }
            db.runInTransaction {
                repeat(writesPerChunk) { writeIndex ->
                    val ordinal = sequence + writeIndex
                    val expenseId = eventExpenseIds[ordinal % eventExpenseIds.size]
                    val amountCents = ((ordinal % 35_000) + 700).toLong()
                    val category = categories[ordinal % categories.size]
                    val note = "$namePrefix-$chunkIndex-$writeIndex"
                    val updatedRows = dao.updateExpenseById(
                        expenseId = expenseId,
                        amountCents = amountCents,
                        category = category,
                        note = note,
                        createdAt = 4_000_000L + ordinal,
                    )
                    check(updatedRows == 1) {
                        "Expected one updated row, actual=$updatedRows expenseId=$expenseId"
                    }
                    updatedCount += updatedRows.toLong()
                }
            }
            sequence += writesPerChunk
            val totalAmount = dao.totalAmountForEvent(eventId)
            check(totalAmount > 0L) { "Expected positive total amount for eventId=$eventId" }
        }

        val actual = dao.expenseCount()
        check(actual == snapshot.initialExpenseCount.toLong()) {
            "Unexpected expense count after mixed bounded writes: expected=${snapshot.initialExpenseCount} actual=$actual"
        }

        return readRowCount + updatedCount
    }

    fun logCaseResult(
        tag: String,
        aspect: String,
        metric: String,
        label: String,
    ) {
        Log.i(
            tag,
            "$CASE_RESULT_PREFIX " +
                "aspect=$aspect " +
                "metric=$metric " +
                "label=$label " +
                "primaryMetric=time_nanos_*"
        )
    }

    private suspend fun readPragmaLong(db: BenchAppDatabase, pragmaName: String): Long {
        return db.useReaderConnection { connection ->
            connection.usePrepared("PRAGMA $pragmaName") { pragmaStmt ->
                check(pragmaStmt.step()) { "PRAGMA $pragmaName returned no rows" }
                pragmaStmt.getLong(0)
            }
        }
    }

}
