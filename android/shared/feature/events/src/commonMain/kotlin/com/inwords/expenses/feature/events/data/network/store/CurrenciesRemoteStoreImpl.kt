package com.inwords.expenses.feature.events.data.network.store

import com.inwords.expenses.core.network.ConditionalGetResult
import com.inwords.expenses.core.network.HostConfig
import com.inwords.expenses.core.network.getConditional
import com.inwords.expenses.core.network.requestWithExceptionHandling
import com.inwords.expenses.core.network.toIoResult
import com.inwords.expenses.core.network.url
import com.inwords.expenses.core.observability.Observability
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.core.utils.SuspendLazy
import com.inwords.expenses.core.utils.normalizeCurrencyRate
import com.inwords.expenses.feature.events.data.network.dto.CurrenciesWithRatesResponseDto
import com.inwords.expenses.feature.events.data.network.dto.CurrencyDto
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.SeededCurrencies
import com.inwords.expenses.feature.events.domain.store.remote.CurrenciesRemoteStore
import com.inwords.expenses.feature.events.domain.store.remote.CurrenciesRemoteStore.GetCurrenciesResult
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.HttpClient
import io.ktor.serialization.ContentConvertException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class CurrenciesRemoteStoreImpl(
    private val client: SuspendLazy<HttpClient>,
    private val hostConfig: HostConfig,
) : CurrenciesRemoteStore {

    override suspend fun getCurrencies(eTag: String?): IoResult<GetCurrenciesResult> {
        return client.requestWithExceptionHandling {
            val response = getConditional<CurrenciesWithRatesResponseDto>(eTag) {
                url(hostConfig) {
                    pathSegments = listOf("api", "v3", "user", "currencies", "all")
                }
            }
            when (response) {
                is ConditionalGetResult.Modified -> GetCurrenciesResult.Modified(
                    currencies = response.body.currencies.map { it.toCurrency(response.body.exchangeRate) },
                    eTag = response.eTag,
                )

                is ConditionalGetResult.NotModified -> GetCurrenciesResult.NotModified(
                    eTag = response.eTag,
                )
            }
        }.toIoResult()
    }

    private fun CurrencyDto.toCurrency(exchangeRate: JsonObject): Currency {
        val rate = exchangeRate[code]?.jsonPrimitive?.content?.let { rawRate ->
            try {
                BigDecimal.parseString(rawRate).normalizeCurrencyRate()
            } catch (exception: ArithmeticException) {
                Observability.captureException(exception) {
                    setMessage("CurrenciesRemoteStore received an invalid exchange rate from the backend")
                    setContext("currency_code", code)
                    setContext("raw_rate", rawRate)
                }
                null
            }
        } ?: throw ContentConvertException("Missing or invalid exchange rate for $code")

        return Currency(
            id = 0L,
            serverId = id,
            code = code,
            name = SeededCurrencies.nameForCode(code),
            rate = rate,
        )
    }

}
