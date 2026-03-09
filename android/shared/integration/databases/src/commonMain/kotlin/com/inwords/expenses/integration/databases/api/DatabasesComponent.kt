package com.inwords.expenses.integration.databases.api

import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.Component
import com.inwords.expenses.feature.events.data.db.dao.CurrenciesDao
import com.inwords.expenses.feature.events.data.db.dao.CurrencyRatesMetadataDao
import com.inwords.expenses.feature.events.data.db.dao.EventsDao
import com.inwords.expenses.feature.events.data.db.dao.PersonsDao
import com.inwords.expenses.feature.expenses.data.db.dao.ExpensesDao
import com.inwords.expenses.integration.databases.data.RoomDatabaseBuilderFactory
import com.inwords.expenses.integration.databases.data.createAppDatabase

class DatabasesComponent internal constructor(
    private val roomDatabaseBuilderFactory: RoomDatabaseBuilderFactory
) : Component {

    private val roomDb = lazy {
        createAppDatabase(roomDatabaseBuilderFactory.getDatabaseBuilder())
    }

    val transactionHelper: TransactionHelper by lazy {
        TransactionHelper(roomDb)
    }

    // Room DAOs are lazy initialized by Room itself
    val eventsDao: EventsDao get() = roomDb.value.eventsDao()
    val expensesDao: ExpensesDao get() = roomDb.value.expensesDao()
    val currenciesDao: CurrenciesDao get() = roomDb.value.currenciesDao()
    val currencyRatesMetadataDao: CurrencyRatesMetadataDao get() = roomDb.value.currencyRatesMetadataDao()
    val personsDao: PersonsDao get() = roomDb.value.personsDao()
}
