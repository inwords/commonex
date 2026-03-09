package com.inwords.expenses.feature.events.domain.store.remote

import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency

internal interface CurrenciesRemoteStore {

    suspend fun getCurrencies(eTag: String?): IoResult<GetCurrenciesResult>

    sealed interface GetCurrenciesResult {
        data class Modified(
            val currencies: List<Currency>,
            val eTag: String?,
        ) : GetCurrenciesResult

        data class NotModified(
            val eTag: String?,
        ) : GetCurrenciesResult
    }

}
