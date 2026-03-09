package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.utils.currencyRateScale
import com.inwords.expenses.core.utils.divide
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}
