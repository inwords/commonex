package com.inwords.expenses.feature.events.domain.task

import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.inwords.expenses.feature.events.domain.store.remote.CurrenciesRemoteStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class CurrenciesPullTask internal constructor(
    transactionHelperLazy: Lazy<TransactionHelper>,
    currenciesLocalStoreLazy: Lazy<CurrenciesLocalStore>,
    currenciesRemoteStoreLazy: Lazy<CurrenciesRemoteStore>,
) {

    private val transactionHelper by transactionHelperLazy
    private val currenciesLocalStore by currenciesLocalStoreLazy
    private val currenciesRemoteStore by currenciesRemoteStoreLazy

    suspend fun pullCurrencies(): IoResult<List<Currency>> = withContext(IO) {
        val eTag = currenciesLocalStore.getCurrenciesETag()
        val remoteResult = when (val networkResult = currenciesRemoteStore.getCurrencies(eTag)) {
            is IoResult.Success -> networkResult.data
            is IoResult.Error -> return@withContext networkResult
        }

        val date = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val currencies = transactionHelper.immediateWriteTransaction {
            when (remoteResult) {
                is CurrenciesRemoteStore.GetCurrenciesResult.Modified -> {
                    currenciesLocalStore.setCurrenciesETag(remoteResult.eTag)
                    currenciesLocalStore.setCurrenciesLastRatesUpdateUtcDate(date)

                    updateLocalCurrencies(remoteResult.currencies)
                }

                is CurrenciesRemoteStore.GetCurrenciesResult.NotModified -> {
                    currenciesLocalStore.setCurrenciesETag(remoteResult.eTag ?: eTag)

                    currenciesLocalStore.getCurrencies().first()
                }
            }
        }

        IoResult.Success(currencies)
    }

    private suspend fun updateLocalCurrencies(
        networkCurrencies: List<Currency>
    ): List<Currency> {
        val localCurrencies = currenciesLocalStore.getCurrencies().first()
        val localCurrenciesMap = localCurrencies.associateBy { it.code }

        var hasUpdates = false
        val updatedCurrencies = networkCurrencies.map { networkCurrency ->
            val localCurrency = localCurrenciesMap[networkCurrency.code]
            if (localCurrency != null) {
                val updatedCurrency = localCurrency.copy(
                    serverId = networkCurrency.serverId,
                    name = preserveCurrencyName(localCurrency, networkCurrency),
                    rate = networkCurrency.rate,
                )
                if (updatedCurrency != localCurrency) {
                    hasUpdates = true
                    updatedCurrency
                } else {
                    localCurrency
                }
            } else {
                hasUpdates = true
                networkCurrency
            }
        }

        return if (hasUpdates) {
            currenciesLocalStore.insert(updatedCurrencies)
        } else {
            localCurrencies
        }
    }

    private fun preserveCurrencyName(localCurrency: Currency, networkCurrency: Currency): String {
        return if (networkCurrency.name == networkCurrency.code) {
            localCurrency.name
        } else {
            networkCurrency.name
        }
    }

}
