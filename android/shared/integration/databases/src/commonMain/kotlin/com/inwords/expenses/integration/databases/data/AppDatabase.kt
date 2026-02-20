package com.inwords.expenses.integration.databases.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.feature.events.data.db.dao.CurrenciesDao
import com.inwords.expenses.feature.events.data.db.dao.EventsDao
import com.inwords.expenses.feature.events.data.db.dao.PersonsDao
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.feature.events.data.db.entity.EventCurrencyCrossRef
import com.inwords.expenses.feature.events.data.db.entity.EventEntity
import com.inwords.expenses.feature.events.data.db.entity.EventPersonCrossRef
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.inwords.expenses.feature.expenses.data.db.dao.ExpensesDao
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseEntity
import com.inwords.expenses.feature.expenses.data.db.entity.ExpenseSplitEntity
import com.inwords.expenses.integration.databases.data.migration.MIGRATION_1_2

@Database(
    entities = [
        EventEntity::class,
        ExpenseEntity::class,
        ExpenseSplitEntity::class,
        CurrencyEntity::class,
        PersonEntity::class,
        EventCurrencyCrossRef::class,
        EventPersonCrossRef::class,
    ],
    autoMigrations = [

    ],
    version = 2
)
@ConstructedBy(AppDatabaseConstructor::class)
internal abstract class AppDatabase : RoomDatabase() {

    abstract fun eventsDao(): EventsDao
    abstract fun expensesDao(): ExpensesDao
    abstract fun currenciesDao(): CurrenciesDao
    abstract fun personsDao(): PersonsDao
}

internal fun createAppDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .setQueryCoroutineContext(IO.limitedParallelism(4))
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(MIGRATION_1_2)
        .setDriver(BundledSQLiteDriver())
        .addCallback(RoomOnCreateCallback())
        .build()
}
