package com.inwords.expenses.feature.events.data.db.store

import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.feature.events.data.db.converter.toDomain
import com.inwords.expenses.feature.events.data.db.converter.toEntity
import com.inwords.expenses.feature.events.data.db.dao.EventsDao
import com.inwords.expenses.feature.events.data.db.entity.EventCurrencyCrossRef
import com.inwords.expenses.feature.events.data.db.entity.EventPersonCrossRef
import com.inwords.expenses.feature.events.data.db.entity.EventWithDetailsQuery
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.inwords.expenses.feature.events.domain.store.local.EventsLocalStore
import com.inwords.expenses.feature.events.domain.store.local.PersonsLocalStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class EventsLocalStoreImpl(
    transactionHelperLazy: Lazy<TransactionHelper>,
    eventsDaoLazy: Lazy<EventsDao>,
    personsLocalStoreLazy: Lazy<PersonsLocalStore>,
    currenciesLocalStoreLazy: Lazy<CurrenciesLocalStore>,
) : EventsLocalStore {

    private val transactionHelper by transactionHelperLazy
    private val eventsDao by eventsDaoLazy
    private val personsLocalStore by personsLocalStoreLazy
    private val currenciesRepository by currenciesLocalStoreLazy

    override fun getEventsFlow(): Flow<List<Event>> {
        return eventsDao.queryAllEventsFlow().map { entities ->
            entities.map { entity -> entity.toDomain() }
        }.distinctUntilChanged()
    }

    override fun getEventWithDetailsFlow(eventId: Long): Flow<EventDetails?> {
        return eventsDao.queryEventWithDetailsByIdFlow(eventId).map { entity ->
            entity?.withSortedPersons()?.toDomain()
        }.distinctUntilChanged()
    }

    override suspend fun getEvent(eventId: Long): Event? {
        return eventsDao.queryEventById(eventId)?.toDomain()
    }

    override suspend fun getEventWithDetails(eventId: Long): EventDetails? {
        return eventsDao.queryEventWithDetailsById(eventId)?.withSortedPersons()?.toDomain()
    }

    override suspend fun getEventByServerId(eventServerId: String): Event? {
        return eventsDao.queryEventByServerId(eventServerId)?.toDomain()
    }

    override suspend fun getEventWithDetailsByServerId(eventServerId: String): EventDetails? {
        return eventsDao.queryEventWithDetailsByServerId(eventServerId)?.withSortedPersons()?.toDomain()
    }

    override suspend fun getEventPersons(eventId: Long): List<Person> {
        return eventsDao.queryEventPersonsById(eventId).map { it.toDomain() }
    }

    override suspend fun update(eventId: Long, newServerId: String): Boolean {
        return eventsDao.update(eventId, newServerId) >= 1
    }

    override suspend fun delete(eventId: Long): Boolean {
        return eventsDao.delete(eventId) >= 1
    }

    override suspend fun deepInsert(
        eventToInsert: Event,
        personsToInsert: List<Person>,
        prefetchedLocalCurrencies: List<Currency>?,
        inTransaction: Boolean
    ): EventDetails {
        return if (inTransaction) {
            transactionHelper.immediateWriteTransaction {
                deepInsertInternal(eventToInsert, personsToInsert, prefetchedLocalCurrencies)
            }
        } else {
            deepInsertInternal(eventToInsert, personsToInsert, prefetchedLocalCurrencies)
        }
    }

    override suspend fun insertPersonsWithCrossRefs(
        eventId: Long,
        persons: List<Person>,
        inTransaction: Boolean,
    ): List<Person> {
        return if (inTransaction) {
            transactionHelper.immediateWriteTransaction {
                insertPersonsWithCrossRefsInternal(eventId, persons)
            }
        } else {
            insertPersonsWithCrossRefsInternal(eventId, persons)
        }
    }

    private suspend fun deepInsertInternal(
        eventToInsert: Event,
        personsToInsert: List<Person>,
        prefetchedLocalCurrencies: List<Currency>?,
    ): EventDetails {
        val persons = personsLocalStore.insertWithoutCrossRefs(personsToInsert)
        val currencies = prefetchedLocalCurrencies ?: currenciesRepository.getCurrencies().first()

        val eventDetails = EventDetails(
            event = eventToInsert,
            persons = persons,
            currencies = currencies,
            primaryCurrency = currencies.first { it.id == eventToInsert.primaryCurrencyId },
        )

        val eventId = eventsDao.insert(eventDetails.toEntity()).takeIf { it != -1L }
        val resultEventDetails = if (eventId == null) {
            eventDetails
        } else {
            eventDetails.copy(event = eventDetails.event.copy(id = eventId))
        }

        val personCrossRefs = eventDetails.persons.map { person ->
            EventPersonCrossRef(eventId = resultEventDetails.event.id, personId = person.id)
        }
        eventsDao.insertPersonCrossRef(personCrossRefs)

        val currencyCrossRefs = eventDetails.currencies.map { currency ->
            EventCurrencyCrossRef(eventId = resultEventDetails.event.id, currencyId = currency.id)
        }
        eventsDao.insertCurrencyCrossRef(currencyCrossRefs)

        return resultEventDetails
    }

    private suspend fun insertPersonsWithCrossRefsInternal(
        eventId: Long,
        persons: List<Person>,
    ): List<Person> {
        val insertedPersons = personsLocalStore.insertWithoutCrossRefs(persons)
        val personCrossRefs = insertedPersons.map { person ->
            EventPersonCrossRef(eventId = eventId, personId = person.id)
        }
        eventsDao.insertPersonCrossRef(personCrossRefs)

        return insertedPersons
    }

    private fun EventWithDetailsQuery.withSortedPersons(): EventWithDetailsQuery {
        return copy(persons = persons.sortedBy { it.personId })
    }

}
