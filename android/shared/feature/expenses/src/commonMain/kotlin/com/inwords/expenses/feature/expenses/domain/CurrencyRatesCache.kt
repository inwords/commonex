package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.collectIn
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.SeededCurrencies
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class CurrencyRatesCache(
    currenciesLocalStore: CurrenciesLocalStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) {

    private val cacheState = AtomicReference(CacheState.seeded())

    init {
        currenciesLocalStore.getCurrencies().collectIn(scope) { currencies ->
            if (currencies.isNotEmpty()) {
                cacheState.store(
                    CacheState(
                        currenciesById = currencies.associateBy(Currency::id),
                        directPairs = currencies.toDirectPairs(),
                    )
                )
            }
        }
    }

    fun getCurrencyById(currencyId: Long): Currency? {
        return cacheState.load().currenciesById[currencyId]
    }

    fun getDirectPairs(): Map<CurrencyRatePair, BigDecimal> {
        return cacheState.load().directPairs
    }

    private data class CacheState(
        val currenciesById: Map<Long, Currency>,
        val directPairs: Map<CurrencyRatePair, BigDecimal>,
    ) {

        companion object {

            fun seeded(): CacheState {
                return CacheState(
                    currenciesById = SeededCurrencies.all.associateBy(Currency::id),
                    directPairs = SeededCurrencies.all.toDirectPairs(),
                )
            }
        }
    }
}
