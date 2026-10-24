package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.utils.currencyRateScale
import com.inwords.expenses.core.utils.divide
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CurrencyRatesCacheTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial cache state is seeded before store emits`() = runTest {
        val localStore = mockk<CurrenciesLocalStore> {
            every { getCurrencies() } returns emptyFlow()
        }
        val cache = CurrencyRatesCache(
            currenciesLocalStore = localStore,
            scope = backgroundScope,
        )

        val usd = cache.getCurrencyById(2L)
        val eurToUsd = cache.getDirectPairs()[CurrencyRatePair("EUR", "USD")]
        val usdToEur = cache.getDirectPairs()[CurrencyRatePair("USD", "EUR")]
        val usdToUsd = cache.getDirectPairs()[CurrencyRatePair("USD", "USD")]

        assertNotNull(usd)
        assertEquals("USD", usd.code)
        assertEquals(BigDecimal.parseString("0.8677"), usdToEur)
        assertEquals(
            BigDecimal.ONE.divide(BigDecimal.parseString("0.8677"), scale = currencyRateScale),
            eurToUsd,
        )
        assertEquals(null, usdToUsd)
    }

    @Test
    fun `store emissions replace seeded and previous currency ids and rates`() = runTest {
        val currencies = MutableStateFlow<List<Currency>>(emptyList())
        val localStore = mockk<CurrenciesLocalStore> {
            every { getCurrencies() } returns currencies
        }
        val cache = CurrencyRatesCache(localStore, backgroundScope)
        val usd = currency(102L, "USD", "2")
        val eur = currency(101L, "EUR", "0.5")

        currencies.value = listOf(usd, eur)
        runCurrent()

        assertNull(cache.getCurrencyById(2L))
        assertEquals(usd, cache.getCurrencyById(usd.id))
        assertEquals(eur, cache.getCurrencyById(eur.id))
        assertEquals(
            mapOf(
                CurrencyRatePair("USD", "EUR") to BigDecimal.parseString("0.25"),
                CurrencyRatePair("EUR", "USD") to BigDecimal.parseString("4"),
            ),
            cache.getDirectPairs(),
        )

        val refreshedUsd = usd.copy(id = 202L, rate = BigDecimal.parseString("4"))
        val gbp = currency(203L, "GBP", "2")
        currencies.value = listOf(refreshedUsd, gbp)
        runCurrent()

        assertNull(cache.getCurrencyById(usd.id))
        assertNull(cache.getCurrencyById(eur.id))
        assertEquals(refreshedUsd, cache.getCurrencyById(refreshedUsd.id))
        assertEquals(gbp, cache.getCurrencyById(gbp.id))
        assertEquals(
            mapOf(
                CurrencyRatePair("USD", "GBP") to BigDecimal.parseString("0.5"),
                CurrencyRatePair("GBP", "USD") to BigDecimal.parseString("2"),
            ),
            cache.getDirectPairs(),
        )
    }

    @Test
    fun `empty store emissions retain the last populated snapshot`() = runTest {
        val currencies = MutableStateFlow<List<Currency>>(emptyList())
        val localStore = mockk<CurrenciesLocalStore> {
            every { getCurrencies() } returns currencies
        }
        val cache = CurrencyRatesCache(localStore, backgroundScope)
        val seededUsd = cache.getCurrencyById(2L)
        val seededPairs = cache.getDirectPairs()

        runCurrent()

        assertEquals(seededUsd, cache.getCurrencyById(2L))
        assertEquals(seededPairs, cache.getDirectPairs())

        val usd = currency(102L, "USD", "2")
        val eur = currency(101L, "EUR", "0.5")
        currencies.value = listOf(usd, eur)
        runCurrent()
        val populatedPairs = cache.getDirectPairs()

        currencies.value = emptyList()
        runCurrent()

        assertNull(cache.getCurrencyById(2L))
        assertEquals(usd, cache.getCurrencyById(usd.id))
        assertEquals(eur, cache.getCurrencyById(eur.id))
        assertEquals(populatedPairs, cache.getDirectPairs())
    }

    private fun currency(id: Long, code: String, rate: String): Currency = Currency(
        id = id,
        serverId = "server-$code",
        code = code,
        name = code,
        rate = BigDecimal.parseString(rate),
    )
}
