package com.inwords.expenses.feature.events.data.db.store

import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.feature.events.data.db.dao.EventsDao
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.feature.events.data.db.entity.EventEntity
import com.inwords.expenses.feature.events.data.db.entity.EventWithDetailsQuery
import com.inwords.expenses.feature.events.data.db.entity.PersonEntity
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.inwords.expenses.feature.events.domain.store.local.PersonsLocalStore
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun eventWithDetailsQuery(
        persons: List<PersonEntity>,
        primaryCurrency: CurrencyEntity = currencyEntity(id = 10L),
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

    private fun currencyEntity(id: Long): CurrencyEntity {
        return CurrencyEntity(
            currencyId = id,
            currencyServerId = "srv-eur",
            code = "EUR",
            name = "Euro",
            rateUnscaled = BigInteger.ONE,
            rateScale = 0L,
        )
    }
}
