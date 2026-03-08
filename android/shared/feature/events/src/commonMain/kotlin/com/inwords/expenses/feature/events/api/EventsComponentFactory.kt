package com.inwords.expenses.feature.events.api

import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.SuspendLazy
import com.inwords.expenses.feature.events.data.db.dao.CurrenciesDao
import com.inwords.expenses.feature.events.data.db.dao.EventsDao
import com.inwords.expenses.feature.events.data.db.dao.PersonsDao
import com.inwords.expenses.feature.settings.api.SettingsRepository
import io.ktor.client.HttpClient

expect class EventsComponentFactory {

    interface Deps : EventsComponentFactoryCommonDeps

    fun create(): EventsComponent
}

interface EventsComponentFactoryCommonDeps {

    val eventsDao: EventsDao
    val personsDao: PersonsDao
    val currenciesDao: CurrenciesDao

    val transactionHelper: TransactionHelper

    val client: SuspendLazy<HttpClient>
    val hostConfig: HostConfig

    val settingsRepositoryLazy: Lazy<SettingsRepository>

    val hooks: EventHooks
}
