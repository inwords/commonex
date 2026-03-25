package com.inwords.expenses.feature.expenses.domain

import com.inwords.expenses.core.observability.Observability
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal

internal class ExpenseExchangeResolver internal constructor(
    currenciesLocalStoreLazy: Lazy<CurrenciesLocalStore>,
    currencyRatesCacheLazy: Lazy<CurrencyRatesCache>,
) {

    private val currenciesLocalStore by currenciesLocalStoreLazy
    private val currencyRatesCache by currencyRatesCacheLazy
    private val currencyExchanger by lazy { CurrencyExchanger(currencyRatesCache) }

    suspend fun resolve(event: Event, originalCurrency: Currency): ((BigDecimal) -> BigDecimal)? {
        if (originalCurrency.id == event.primaryCurrencyId) {
            return { it }
        }

        val primaryCurrencyCode = currencyRatesCache.getCurrencyById(event.primaryCurrencyId)?.code
            ?: currenciesLocalStore.getCurrencyCodeById(event.primaryCurrencyId)
            ?: run {
                Observability.captureMessage("ExpenseExchangeResolver could not resolve the primary currency code for an event") {
                    event.serverId?.let { setContext("event_server_id", it) }
                }
                return null
            }

        return { amount ->
            currencyExchanger.exchange(amount, originalCurrency.code, primaryCurrencyCode)
        }
    }
}
