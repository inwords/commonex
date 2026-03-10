package com.inwords.expenses.feature.events.domain.task

import com.inwords.expenses.core.storage.utils.TransactionHelper
import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.store.local.CurrenciesLocalStore
import com.inwords.expenses.feature.events.domain.store.remote.CurrenciesRemoteStore
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
internal class CurrenciesPullTaskTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val transactionHelper = mockk<TransactionHelper>()
    private val currenciesLocalStore = mockk<CurrenciesLocalStore>(relaxed = true)
    private val currenciesRemoteStore = mockk<CurrenciesRemoteStore>()

    private val task = CurrenciesPullTask(
        transactionHelperLazy = lazy { transactionHelper },
        currenciesLocalStoreLazy = lazy { currenciesLocalStore },
        currenciesRemoteStoreLazy = lazy { currenciesRemoteStore },
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pullCurrencies preserves local ids while updating serverId and rate`() = runTest {
        val localCurrency = Currency(
            id = 42L,
            serverId = null,
            code = "EUR",
            name = "Euro",
            rate = BigDecimal.parseString("0.8"),
        )
        val remoteCurrency = localCurrency.copy(
            id = 0L,
            serverId = "srv-eur",
            rate = BigDecimal.parseString("0.8677"),
        )
        coEvery { currenciesLocalStore.getCurrenciesETag() } returns null
        coEvery { currenciesRemoteStore.getCurrencies(null) } returns IoResult.Success(
            CurrenciesRemoteStore.GetCurrenciesResult.Modified(
                currencies = listOf(remoteCurrency),
                eTag = "\"rates-v1\"",
            )
        )
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(localCurrency))
        coEvery { currenciesLocalStore.insert(any()) } answers { firstArg() }
        coEvery { transactionHelper.immediateWriteTransaction<List<Currency>>(any()) } coAnswers {
            firstArg<suspend () -> List<Currency>>().invoke()
        }

        val result = task.pullCurrencies()

        val currencies = (result as IoResult.Success).data
        assertEquals(42L, currencies.single().id)
        assertEquals("srv-eur", currencies.single().serverId)
        assertEquals("0.8677", currencies.single().rate.toStringExpanded())
        coVerify(exactly = 1) { currenciesLocalStore.setCurrenciesETag("\"rates-v1\"") }
        coVerify(exactly = 1) { currenciesLocalStore.setCurrenciesLastRatesUpdateUtcDate(any()) }
    }

    @Test
    fun `pullCurrencies on 304 keeps local currencies and writes resolved etag`() = runTest {
        val localCurrency = Currency(
            id = 42L,
            serverId = "srv-eur",
            code = "EUR",
            name = "Euro",
            rate = BigDecimal.parseString("0.8677"),
        )
        coEvery { currenciesLocalStore.getCurrenciesETag() } returns "\"cached-etag\""
        coEvery { currenciesRemoteStore.getCurrencies("\"cached-etag\"") } returns IoResult.Success(
            CurrenciesRemoteStore.GetCurrenciesResult.NotModified(
                eTag = "\"cached-etag\"",
            )
        )
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(localCurrency))
        coEvery { transactionHelper.immediateWriteTransaction<List<Currency>>(any()) } coAnswers {
            firstArg<suspend () -> List<Currency>>().invoke()
        }

        val result = task.pullCurrencies()

        assertEquals(listOf(localCurrency), assertIs<IoResult.Success<List<Currency>>>(result).data)
        coVerify(exactly = 1) { currenciesLocalStore.setCurrenciesETag("\"cached-etag\"") }
        coVerify(exactly = 0) { currenciesLocalStore.setCurrenciesLastRatesUpdateUtcDate(any()) }
        coVerify(exactly = 0) { currenciesLocalStore.insert(any()) }
    }

    @Test
    fun `pullCurrencies on 304 without etag preserves cached validator`() = runTest {
        val localCurrency = Currency(
            id = 42L,
            serverId = "srv-eur",
            code = "EUR",
            name = "Euro",
            rate = BigDecimal.parseString("0.8677"),
        )
        coEvery { currenciesLocalStore.getCurrenciesETag() } returns "\"cached-etag\""
        coEvery { currenciesRemoteStore.getCurrencies("\"cached-etag\"") } returns IoResult.Success(
            CurrenciesRemoteStore.GetCurrenciesResult.NotModified(
                eTag = null,
            )
        )
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(localCurrency))
        coEvery { transactionHelper.immediateWriteTransaction<List<Currency>>(any()) } coAnswers {
            firstArg<suspend () -> List<Currency>>().invoke()
        }

        val result = task.pullCurrencies()

        assertEquals(listOf(localCurrency), assertIs<IoResult.Success<List<Currency>>>(result).data)
        coVerify(exactly = 1) { currenciesLocalStore.setCurrenciesETag("\"cached-etag\"") }
        coVerify(exactly = 0) { currenciesLocalStore.setCurrenciesLastRatesUpdateUtcDate(any()) }
        coVerify(exactly = 0) { currenciesLocalStore.insert(any()) }
    }

    @Test
    fun `pullCurrencies returns remote error unchanged`() = runTest {
        coEvery { currenciesLocalStore.getCurrenciesETag() } returns "\"cached-etag\""
        coEvery { currenciesRemoteStore.getCurrencies("\"cached-etag\"") } returns IoResult.Error.Retry

        val result = task.pullCurrencies()

        assertEquals(IoResult.Error.Retry, result)
        coVerify(exactly = 0) { currenciesLocalStore.setCurrenciesETag(any()) }
        coVerify(exactly = 0) { currenciesLocalStore.setCurrenciesLastRatesUpdateUtcDate(any()) }
        coVerify(exactly = 0) { currenciesLocalStore.insert(any()) }
    }

    @Test
    fun `pullCurrencies with unchanged remote currencies does not insert`() = runTest {
        val localCurrency = Currency(
            id = 42L,
            serverId = "srv-eur",
            code = "EUR",
            name = "Euro",
            rate = BigDecimal.parseString("0.8677"),
        )
        coEvery { currenciesLocalStore.getCurrenciesETag() } returns "\"cached-etag\""
        coEvery { currenciesRemoteStore.getCurrencies("\"cached-etag\"") } returns IoResult.Success(
            CurrenciesRemoteStore.GetCurrenciesResult.Modified(
                currencies = listOf(localCurrency.copy(id = 0L)),
                eTag = "\"rates-v1\"",
            )
        )
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(localCurrency))
        coEvery { transactionHelper.immediateWriteTransaction<List<Currency>>(any()) } coAnswers {
            firstArg<suspend () -> List<Currency>>().invoke()
        }

        val result = task.pullCurrencies()

        assertEquals(listOf(localCurrency), assertIs<IoResult.Success<List<Currency>>>(result).data)
        coVerify(exactly = 1) { currenciesLocalStore.setCurrenciesETag("\"rates-v1\"") }
        coVerify(exactly = 1) { currenciesLocalStore.setCurrenciesLastRatesUpdateUtcDate(any()) }
        coVerify(exactly = 0) { currenciesLocalStore.insert(any()) }
    }

    @Test
    fun `pullCurrencies on 200 without etag clears stored validator`() = runTest {
        val localCurrency = Currency(
            id = 42L,
            serverId = "srv-eur",
            code = "EUR",
            name = "Euro",
            rate = BigDecimal.parseString("0.8"),
        )
        val remoteCurrency = localCurrency.copy(rate = BigDecimal.parseString("0.8677"))
        coEvery { currenciesLocalStore.getCurrenciesETag() } returns "\"old-etag\""
        coEvery { currenciesRemoteStore.getCurrencies("\"old-etag\"") } returns IoResult.Success(
            CurrenciesRemoteStore.GetCurrenciesResult.Modified(
                currencies = listOf(remoteCurrency),
                eTag = null,
            )
        )
        coEvery { currenciesLocalStore.getCurrencies() } returns flowOf(listOf(localCurrency))
        coEvery { currenciesLocalStore.insert(any()) } answers { firstArg() }
        coEvery { transactionHelper.immediateWriteTransaction<List<Currency>>(any()) } coAnswers {
            firstArg<suspend () -> List<Currency>>().invoke()
        }

        val result = task.pullCurrencies()

        assertEquals("0.8677", assertIs<IoResult.Success<List<Currency>>>(result).data.single().rate.toStringExpanded())
        coVerify(exactly = 1) { currenciesLocalStore.setCurrenciesETag(null) }
        coVerify(exactly = 1) { currenciesLocalStore.setCurrenciesLastRatesUpdateUtcDate(any()) }
    }
}
