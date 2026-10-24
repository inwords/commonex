package com.inwords.expenses.feature.events.data.db.store

import app.cash.turbine.test
import com.inwords.expenses.feature.events.data.db.dao.CurrenciesDao
import com.inwords.expenses.feature.events.data.db.dao.CurrencyRatesMetadataDao
import com.inwords.expenses.feature.events.data.db.entity.CurrencyEntity
import com.inwords.expenses.feature.events.domain.model.Currency
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

internal class CurrenciesLocalStoreImplTest {

    private val dao = mockk<CurrenciesDao>()
    private val metadataDao = mockk<CurrencyRatesMetadataDao>()
    private val store = CurrenciesLocalStoreImpl(lazy { dao }, lazy { metadataDao })
    private val eur = Currency(0L, "eur-server", "EUR", "Euro", BigDecimal.parseString("0.867713"))
    private val usd = Currency(22L, "usd-server", "USD", "Dollar", BigDecimal.ONE)
    private val normalizedRate = BigDecimal.parseString("0.8677")

    @Test
    fun `mixed currency inserts and updates retain identity while persisting normalized rates`() = runTest {
        coEvery { dao.insert(any()) } returns listOf(101L, -1L)

        val result = store.insert(listOf(eur, usd))

        assertEquals(listOf(eur.copy(id = 101L), usd), result)
        coVerify(exactly = 1) {
            dao.insert(
                listOf(
                    CurrencyEntity(0L, "eur-server", "EUR", "Euro", normalizedRate.significand, normalizedRate.exponent),
                    CurrencyEntity(22L, "usd-server", "USD", "Dollar", BigInteger.parseString("10000"), 0L),
                )
            )
        }
    }

    @Test
    fun `currency flow preserves stored precision and emits subsequent rate changes without duplicates`() = runTest {
        val persistedRate = BigDecimal.parseString("0.867713")
        val entity = CurrencyEntity(101L, "eur-server", "EUR", "Euro", persistedRate.significand, persistedRate.exponent)
        val rows = MutableSharedFlow<List<CurrencyEntity>>(replay = 1)
        rows.tryEmit(listOf(entity))
        every { dao.queryAll() } returns rows

        store.getCurrencies().test {
            assertEquals(listOf(eur.copy(id = 101L)), awaitItem())
            rows.emit(listOf(entity.copy()))
            val changedRate = BigDecimal.parseString("0.9")
            rows.emit(listOf(entity.copy(rateUnscaled = changedRate.significand, rateScale = changedRate.exponent)))
            assertEquals(listOf(eur.copy(id = 101L, rate = changedRate)), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `refresh dates preserve leap days and year boundaries in storage and domain`() = runTest {
        coEvery { metadataDao.queryLastRatesUpdateUtcDate() } returnsMany listOf("2024-02-29", "2025-01-01")
        coEvery { metadataDao.updateLastRatesUpdateUtcDate(any()) } returns Unit

        assertEquals(LocalDate(2024, 2, 29), store.getCurrenciesLastRatesUpdateUtcDate())
        assertEquals(LocalDate(2025, 1, 1), store.getCurrenciesLastRatesUpdateUtcDate())
        store.setCurrenciesLastRatesUpdateUtcDate(LocalDate(2024, 2, 29))
        store.setCurrenciesLastRatesUpdateUtcDate(LocalDate(2025, 1, 1))

        coVerify(exactly = 1) { metadataDao.updateLastRatesUpdateUtcDate("2024-02-29") }
        coVerify(exactly = 1) { metadataDao.updateLastRatesUpdateUtcDate("2025-01-01") }
    }
}
