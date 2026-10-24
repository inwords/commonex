package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExpenseExchangeResolverTest {

    private val usd = currency(102L, "USD", "2")
    private val eur = currency(101L, "EUR", "0.5")
    private val currencies = MutableStateFlow(listOf(usd, eur))
    private val localStore = mockk<CurrenciesLocalStore> {
        every { getCurrencies() } returns currencies
    }

    @Test
    fun `same currency returns the original amount without looking up its code`() = runTest {
        val cache = CurrencyRatesCache(localStore, backgroundScope)
        val resolver = resolver(cache)
        val uncachedCurrency = usd.copy(id = 999L)
        val amount = BigDecimal.parseString("123.456789")

        val exchange = assertNotNull(resolver.resolve(event(uncachedCurrency.id), uncachedCurrency))

        assertSame(amount, exchange(amount))
        coVerify(exactly = 0) { localStore.getCurrencyCodeById(any()) }
    }

    @Test
    fun `cached primary currency converts with cached rates without a store lookup`() = runTest {
        val cache = CurrencyRatesCache(localStore, backgroundScope)
        val resolver = resolver(cache)
        runCurrent()

        val exchange = assertNotNull(resolver.resolve(event(eur.id), usd.copy(rate = BigDecimal.parseString("99"))))

        assertEquals(BigDecimal.parseString("10"), exchange(BigDecimal.parseString("40")))
        coVerify(exactly = 0) { localStore.getCurrencyCodeById(any()) }
    }

    @Test
    fun `uncached primary currency falls back to its stored code`() = runTest {
        val cache = CurrencyRatesCache(localStore, backgroundScope)
        val resolver = resolver(cache)
        coEvery { localStore.getCurrencyCodeById(999L) } returns "EUR"
        runCurrent()

        val exchange = assertNotNull(resolver.resolve(event(999L), usd))

        assertEquals(BigDecimal.parseString("10"), exchange(BigDecimal.parseString("40")))
        coVerify(exactly = 1) { localStore.getCurrencyCodeById(999L) }
    }

    @Test
    fun `missing primary currency code returns no converter`() = runTest {
        val cache = CurrencyRatesCache(localStore, backgroundScope)
        val resolver = resolver(cache)
        coEvery { localStore.getCurrencyCodeById(999L) } returns null
        runCurrent()

        assertNull(resolver.resolve(event(999L), usd))

        coVerify(exactly = 1) { localStore.getCurrencyCodeById(999L) }
    }

    @Test
    fun `resolved converter uses the latest rates after cache refresh`() = runTest {
        val cache = CurrencyRatesCache(localStore, backgroundScope)
        val resolver = resolver(cache)
        runCurrent()
        val exchange = assertNotNull(resolver.resolve(event(eur.id), usd))
        val amount = BigDecimal.parseString("40")
        assertEquals(BigDecimal.parseString("10"), exchange(amount))

        currencies.value = listOf(usd, eur.copy(rate = BigDecimal.parseString("1.5")))
        runCurrent()

        assertEquals(BigDecimal.parseString("30"), exchange(amount))
        coVerify(exactly = 0) { localStore.getCurrencyCodeById(any()) }
    }

    private fun resolver(cache: CurrencyRatesCache): ExpenseExchangeResolver = ExpenseExchangeResolver(
        currenciesLocalStoreLazy = lazy { localStore },
        currencyRatesCacheLazy = lazy { cache },
    )

    private fun event(primaryCurrencyId: Long): Event = Event(
        id = 1L,
        serverId = "event-server-id",
        name = "Trip",
        pinCode = "1234",
        primaryCurrencyId = primaryCurrencyId,
        clientCreateId = "event-client-id",
    )

    private fun currency(id: Long, code: String, rate: String): Currency = Currency(
        id = id,
        serverId = "server-$code",
        code = code,
        name = code,
        rate = BigDecimal.parseString(rate),
    )
}
