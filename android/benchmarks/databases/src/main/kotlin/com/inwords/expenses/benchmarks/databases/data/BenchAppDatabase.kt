package com.inwords.expenses.benchmarks.databases.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

@Entity(tableName = "bench_event")
internal data class BenchEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "event_id")
    val eventId: Long = 0L,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "owner_name")
    val ownerName: String,
)

@Entity(
    tableName = "person",
    foreignKeys = [
        ForeignKey(
            entity = BenchEventEntity::class,
            parentColumns = ["event_id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["event_id"]),
    ],
)
internal data class BenchPersonEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "person_id")
    val personId: Long = 0L,
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "person_server_id")
    val personServerId: String?,
    @ColumnInfo(name = "name")
    val name: String,
)

@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(
            entity = BenchEventEntity::class,
            parentColumns = ["event_id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BenchPersonEntity::class,
            parentColumns = ["person_id"],
            childColumns = ["payer_person_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["payer_person_id"]),
        Index(value = ["event_id", "created_at"]),
    ],
)
internal data class BenchExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "expense_id")
    val expenseId: Long = 0L,
    @ColumnInfo(name = "event_id")
    val eventId: Long,
    @ColumnInfo(name = "payer_person_id")
    val payerPersonId: Long,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "note")
    val note: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

internal data class BenchExpenseFeedRow(
    @ColumnInfo(name = "expense_id")
    val expenseId: Long,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    @ColumnInfo(name = "person_name")
    val personName: String,
    @ColumnInfo(name = "event_title")
    val eventTitle: String,
    @ColumnInfo(name = "category")
    val category: String,
)

@Dao
internal interface BenchDataDao {

    @Insert
    fun insertEvents(events: List<BenchEventEntity>): List<Long>

    @Insert
    fun insertPersons(persons: List<BenchPersonEntity>): List<Long>

    @Insert
    fun insertExpenses(expenses: List<BenchExpenseEntity>): List<Long>

    @Query(
        """
        UPDATE expense
        SET amount_cents = :amountCents,
            category = :category,
            note = :note,
            created_at = :createdAt
        WHERE expense_id = :expenseId
        """
    )
    fun updateExpenseById(
        expenseId: Long,
        amountCents: Long,
        category: String,
        note: String,
        createdAt: Long,
    ): Int

    @Query("DELETE FROM expense")
    fun deleteExpenses()

    @Query("DELETE FROM person")
    fun deletePersons()

    @Query("DELETE FROM bench_event")
    fun deleteEvents()

    @Query("SELECT event_id FROM bench_event ORDER BY event_id ASC")
    fun eventIds(): List<Long>

    @Query("SELECT person_id FROM person WHERE event_id = :eventId ORDER BY person_id ASC")
    fun personIdsForEvent(eventId: Long): List<Long>

    @Query(
        """
        SELECT e.expense_id,
               e.amount_cents,
               p.name AS person_name,
               ev.title AS event_title,
               e.category
        FROM expense e
        INNER JOIN person p ON p.person_id = e.payer_person_id
        INNER JOIN bench_event ev ON ev.event_id = e.event_id
        WHERE e.event_id = :eventId
        ORDER BY e.expense_id DESC
        """
    )
    fun readExpenseFeed(eventId: Long): List<BenchExpenseFeedRow>

    @Query("SELECT COALESCE(SUM(amount_cents), 0) FROM expense WHERE event_id = :eventId")
    fun totalAmountForEvent(eventId: Long): Long

    @Query("SELECT COUNT(*) FROM expense")
    fun expenseCount(): Long
}

@Database(
    entities = [
        BenchEventEntity::class,
        BenchPersonEntity::class,
        BenchExpenseEntity::class,
    ],
    version = 1,
)
internal abstract class BenchAppDatabase : RoomDatabase() {
    abstract fun benchDataDao(): BenchDataDao
}

internal fun createBenchAppDatabase(
    context: Context,
    dbName: String,
    pragmasOnOpen: List<String>,
): BenchAppDatabase {
    return Room.databaseBuilder<BenchAppDatabase>(
        context = context,
        name = dbName,
    )
        .setQueryCoroutineContext(Dispatchers.IO.limitedParallelism(4))
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setDriver(BundledSQLiteDriver())
        .addCallback(BenchPragmasOnOpenCallback(pragmasOnOpen))
        .build()
}

private class BenchPragmasOnOpenCallback(
    private val pragmasOnOpen: List<String>,
) : RoomDatabase.Callback() {
    override fun onOpen(connection: SQLiteConnection) {
        pragmasOnOpen.forEach { pragma ->
            connection.execSQL("PRAGMA $pragma")
        }
    }
}
