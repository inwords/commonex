package com.inwords.expenses.feature.events.domain.store.local

import com.inwords.expenses.feature.events.domain.model.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface CurrenciesLocalStore {

    fun getCurrencies(): Flow<List<Currency>>

    suspend fun getCurrencyCodeById(currencyId: Long): String?

    suspend fun insert(currencies: List<Currency>): List<Currency>

    suspend fun getCurrenciesETag(): String?

    suspend fun setCurrenciesETag(eTag: String?)

    suspend fun getCurrenciesLastRatesUpdateUtcDate(): LocalDate

    suspend fun setCurrenciesLastRatesUpdateUtcDate(utcDate: LocalDate)
}
