package com.inwords.expenses.feature.events.data.db.store

import app.cash.turbine.test
import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.feature.events.data.db.dao.EventsDao
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.feature.events.data.db.entity.EventCurrencyCrossRef
import com.inwords.expenses.feature.events.data.db.entity.EventEntity
import com.inwords.expenses.feature.events.data.db.entity.EventPersonCrossRef
import com.inwords.expenses.feature.events.data.db.entity.EventWithDetailsQuery
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.inwords.expenses.feature.events.domain.store.local.PersonsLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class EventsLocalStoreImplTest {

    private val transactionHelper = mockk<TransactionHelper>(relaxed = true)
    private val eventsDao = mockk<EventsDao>()
    private val personsLocalStore = mockk<PersonsLocalStore>(relaxed = true)
    private val currenciesLocalStore = mockk<CurrenciesLocalStore>(relaxed = true)

    private val store = EventsLocalStoreImpl(
        transactionHelperLazy = lazy { transactionHelper },
        eventsDaoLazy = lazy { eventsDao },
        personsLocalStoreLazy = lazy { personsLocalStore },
        currenciesLocalStoreLazy = lazy { currenciesLocalStore },
    )

    @Test
    fun `getEventWithDetails returns persons sorted by local id`() = runTest {
        val query = eventWithDetailsQuery(
            persons = listOf(personEntity(id = 3L), personEntity(id = 1L), personEntity(id = 2L)),
        )
        coEvery { eventsDao.queryEventWithDetailsById(1L) } returns query

        val result = store.getEventWithDetails(1L)

        assertEquals(listOf(1L, 2L, 3L), result?.persons?.map { it.id })
        coVerify(exactly = 0) { eventsDao.queryEventPersonsById(any()) }
    }

    @Test
    fun `getEventWithDetailsByServerId returns persons sorted by local id`() = runTest {
        val query = eventWithDetailsQuery(
            persons = listOf(personEntity(id = 2L), personEntity(id = 1L)),
        )
        coEvery { eventsDao.queryEventWithDetailsByServerId("srv-event") } returns query

        val result = store.getEventWithDetailsByServerId("srv-event")

        assertEquals(listOf(1L, 2L), result?.persons?.map { it.id })
        coVerify(exactly = 0) { eventsDao.queryEventPersonsById(any()) }
    }

    @Test
    fun `getEventWithDetailsFlow emits persons sorted by local id`() = runTest {
        val query = eventWithDetailsQuery(
            persons = listOf(personEntity(id = 4L), personEntity(id = 1L), personEntity(id = 3L)),
        )
        every { eventsDao.queryEventWithDetailsByIdFlow(1L) } returns flowOf(query)

        val result = store.getEventWithDetailsFlow(1L).first()

        assertEquals(listOf(1L, 3L, 4L), result?.persons?.map { it.id })
        coVerify(exactly = 0) { eventsDao.queryEventPersonsById(any()) }
    }

    @Test
    fun `deepInsert uses generated event and person ids for every relationship in its transaction`() = runTest {
        val event = event(id = 0L)
        val alex = Person(0L, null, "Alex", "alex-client")
        val sam = Person(5L, "sam-server", "Sam", "sam-client")
        val people = listOf(alex.copy(id = 101L), sam)
        val eur = Currency(10L, "eur-server", "EUR", "Euro", BigDecimal.parseString("0.9"))
        val usd = Currency(20L, "usd-server", "USD", "Dollar", BigDecimal.ONE)
        val currencies = listOf(usd, eur)
        var insideTransaction = false
        coEvery { transactionHelper.immediateWriteTransaction<EventDetails>(any()) } coAnswers {
            insideTransaction = true
            try {
                firstArg<suspend () -> EventDetails>().invoke()
            } finally {
                insideTransaction = false
            }
        }
        coEvery { personsLocalStore.insertWithoutCrossRefs(listOf(alex, sam)) } coAnswers {
            assertTrue(insideTransaction)
            people
        }
        coEvery { eventsDao.insert(any()) } coAnswers {
            assertTrue(insideTransaction)
            91L
        }
        coEvery { eventsDao.insertPersonCrossRef(any()) } coAnswers {
            assertTrue(insideTransaction)
        }
        coEvery { eventsDao.insertCurrencyCrossRef(any()) } coAnswers {
            assertTrue(insideTransaction)
        }

        val result = store.deepInsert(event, listOf(alex, sam), currencies, inTransaction = true)

        assertEquals(EventDetails(event.copy(id = 91L), currencies, people, eur), result)
        coVerify(exactly = 1) { eventsDao.insert(EventEntity(0L, "event-server", "event-client", "Trip", "1234", 10L)) }
        coVerify(exactly = 1) {
            eventsDao.insertPersonCrossRef(listOf(EventPersonCrossRef(91L, 101L), EventPersonCrossRef(91L, 5L)))
        }
        coVerify(exactly = 1) {
            eventsDao.insertCurrencyCrossRef(listOf(EventCurrencyCrossRef(91L, 20L), EventCurrencyCrossRef(91L, 10L)))
        }
        verify { currenciesLocalStore wasNot Called }
    }

    @Test
    fun `deepInsert of an existing event retains its id and loads currencies without nesting a caller transaction`() = runTest {
        val event = event(id = 77L)
        val alex = Person(101L, "alex-server", "Alex", "alex-client")
        val eur = Currency(10L, "eur-server", "EUR", "Euro", BigDecimal.parseString("0.9"))
        coEvery { personsLocalStore.insertWithoutCrossRefs(listOf(alex)) } returns listOf(alex)
        every { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(eur))
        coEvery { eventsDao.insert(any()) } returns -1L
        coEvery { eventsDao.insertPersonCrossRef(any()) } returns Unit
        coEvery { eventsDao.insertCurrencyCrossRef(any()) } returns Unit

        val result = store.deepInsert(event, listOf(alex), prefetchedLocalCurrencies = null, inTransaction = false)

        assertEquals(EventDetails(event, listOf(eur), listOf(alex), eur), result)
        coVerify(exactly = 1) { eventsDao.insert(EventEntity(77L, "event-server", "event-client", "Trip", "1234", 10L)) }
        coVerify(exactly = 1) { eventsDao.insertPersonCrossRef(listOf(EventPersonCrossRef(77L, 101L))) }
        coVerify(exactly = 1) { eventsDao.insertCurrencyCrossRef(listOf(EventCurrencyCrossRef(77L, 10L))) }
        coVerify(exactly = 0) { transactionHelper.immediateWriteTransaction<EventDetails>(any()) }
    }

    @Test
    fun `adding participants links the returned local identities to the existing event`() = runTest {
        val fresh = Person(0L, null, "Alex", "alex-client")
        val inserted = fresh.copy(id = 101L)
        coEvery { transactionHelper.immediateWriteTransaction<List<Person>>(any()) } coAnswers {
            firstArg<suspend () -> List<Person>>().invoke()
        }
        coEvery { personsLocalStore.insertWithoutCrossRefs(listOf(fresh)) } returns listOf(inserted)
        coEvery { eventsDao.insertPersonCrossRef(any()) } returns Unit

        val result = store.insertPersonsWithCrossRefs(77L, listOf(fresh), inTransaction = true)

        assertEquals(listOf(inserted), result)
        coVerify(exactly = 1) { eventsDao.insertPersonCrossRef(listOf(EventPersonCrossRef(77L, 101L))) }
        coVerify(exactly = 1) { transactionHelper.immediateWriteTransaction<List<Person>>(any()) }
    }

    @Test
    fun `event details flow hydrates complete identities ignores person order changes and reports disappearance`() = runTest {
        val rows = MutableSharedFlow<EventWithDetailsQuery?>(replay = 1)
        rows.tryEmit(null)
        every { eventsDao.queryEventWithDetailsByIdFlow(1L) } returns rows
        val query = eventWithDetailsQuery(listOf(personEntity(3L), personEntity(1L)))
        val eur = Currency(10L, "srv-eur", "EUR", "Euro", BigDecimal.ONE)
        val expected = EventDetails(
            event = Event(1L, "srv-event", "Trip", "1234", eur.id, "event-client-1"),
            currencies = listOf(eur),
            persons = listOf(
                Person(1L, "srv-person-1", "Person1", "person-client-1"),
                Person(3L, "srv-person-3", "Person3", "person-client-3"),
            ),
            primaryCurrency = eur,
        )

        store.getEventWithDetailsFlow(1L).test {
            assertNull(awaitItem())
            rows.emit(query)
            assertEquals(expected, awaitItem())
            rows.emit(query.copy(persons = query.persons.reversed()))
            rows.emit(null)
            assertNull(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `event list flow retains unrelated events when a synced event disappears`() = runTest {
        val first = eventWithDetailsQuery(emptyList()).event.copy(eventServerId = null)
        val second = first.copy(eventId = 2L, eventServerId = "second-server", clientCreateId = "second-client", name = "Other")
        val rows = MutableSharedFlow<List<EventEntity>>(replay = 1)
        rows.tryEmit(listOf(first, second))
        every { eventsDao.queryAllEventsFlow() } returns rows
        val firstEvent = Event(1L, null, "Trip", "1234", 10L, "event-client-1")
        val secondEvent = Event(2L, "second-server", "Other", "1234", 10L, "second-client")

        store.getEventsFlow().test {
            assertEquals(listOf(firstEvent, secondEvent), awaitItem())
            rows.emit(listOf(first.copy(eventServerId = "first-server"), second))
            assertEquals(listOf(firstEvent.copy(serverId = "first-server"), secondEvent), awaitItem())
            rows.emit(listOf(second))
            assertEquals(listOf(secondEvent), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `missing events return no partial hydration for local or server identity lookups`() = runTest {
        coEvery { eventsDao.queryEventById(77L) } returns null
        coEvery { eventsDao.queryEventByServerId("missing-server") } returns null
        coEvery { eventsDao.queryEventWithDetailsById(77L) } returns null
        coEvery { eventsDao.queryEventWithDetailsByServerId("missing-server") } returns null

        assertNull(store.getEvent(77L))
        assertNull(store.getEventByServerId("missing-server"))
        assertNull(store.getEventWithDetails(77L))
        assertNull(store.getEventWithDetailsByServerId("missing-server"))
    }

    @Test
    fun `deepInsert rejects a missing primary currency before persisting an event or its relationships`() = runTest {
        val wrongCurrency = Currency(20L, "usd-server", "USD", "Dollar", BigDecimal.ONE)
        coEvery { transactionHelper.immediateWriteTransaction<EventDetails>(any()) } coAnswers {
            firstArg<suspend () -> EventDetails>().invoke()
        }
        coEvery { personsLocalStore.insertWithoutCrossRefs(emptyList()) } returns emptyList()

        assertFailsWith<NoSuchElementException> {
            store.deepInsert(event(0L), emptyList(), listOf(wrongCurrency), inTransaction = true)
        }

        coVerify(exactly = 1) { transactionHelper.immediateWriteTransaction<EventDetails>(any()) }
        coVerify(exactly = 0) { eventsDao.insert(any()) }
        coVerify(exactly = 0) { eventsDao.insertPersonCrossRef(any()) }
        coVerify(exactly = 0) { eventsDao.insertCurrencyCrossRef(any()) }
    }

    private fun event(id: Long): Event = Event(id, "event-server", "Trip", "1234", 10L, "event-client")

    private fun eventWithDetailsQuery(
        persons: List<PersonEntity>,
        primaryCurrency: CurrencyEntity = currencyEntity(),
    ): EventWithDetailsQuery {
        return EventWithDetailsQuery(
            event = EventEntity(
                eventId = 1L,
                eventServerId = "srv-event",
                clientCreateId = "event-client-1",
                name = "Trip",
                pinCode = "1234",
                primaryCurrencyId = primaryCurrency.currencyId,
            ),
            persons = persons,
            currencies = listOf(primaryCurrency),
            primaryCurrency = primaryCurrency,
        )
    }

    private fun personEntity(id: Long): PersonEntity {
        return PersonEntity(
            personId = id,
            personServerId = "srv-person-$id",
            clientCreateId = "person-client-$id",
            name = "Person$id",
        )
    }

    private fun currencyEntity(): CurrencyEntity {
        return CurrencyEntity(
            currencyId = 10L,
            currencyServerId = "srv-eur",
            code = "EUR",
            name = "Euro",
            rateUnscaled = BigInteger.ONE,
            rateScale = 0L,
        )
    }
}
