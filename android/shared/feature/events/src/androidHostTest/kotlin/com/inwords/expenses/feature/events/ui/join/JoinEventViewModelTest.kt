package com.inwords.expenses.feature.events.ui.join

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.feature.events.domain.JoinEventUseCase
import com.inwords.expenses.feature.events.domain.JoinEventUseCase.JoinEventResult
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.ui.join_invitation.JoinInvitationPaneDestination
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
internal class JoinEventViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val navigationController = mockk<NavigationController>(relaxed = true) {
        justRun { popBackStack() }
    }
    private val joinEventUseCase = mockk<JoinEventUseCase>(relaxed = true)
    private val stringProvider = object : StringProvider {
        override suspend fun getString(stringResource: StringResource): String = "error_message"
        override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String = "error_message"
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        initialEventId: String = "",
        initialPinCode: String = "",
        initialToken: String = "",
        viewModelScope: CoroutineScope = CoroutineScope(testDispatcher),
    ) = JoinEventViewModel(
        navigationController = navigationController,
        joinEventUseCase = joinEventUseCase,
        stringProvider = stringProvider,
        initialEventId = initialEventId,
        initialPinCode = initialPinCode,
        initialToken = initialToken,
        viewModelScope = viewModelScope,
    )

    @Test
    fun initialState_withEmptyParams_hasNoneJoiningState() = runTest {
        val viewModel = createViewModel(viewModelScope = backgroundScope)
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            val initial = awaitItem()
            assertEquals("", initial.eventId)
            assertEquals("", initial.eventAccessCode)
            val _ = assertIs<JoinEventPaneUiModel.EventJoiningState.None>(initial.joiningState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onEventIdChanged_filtersAndUpdatesState() = runTest {
        val viewModel = createViewModel(viewModelScope = backgroundScope)
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.onEventIdChanged("01abc-xyz")
            val state = awaitItem()
            assertEquals("01ABCXYZ", state.eventId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onEventAccessCodeChanged_filtersToDigitsOnly() = runTest {
        val viewModel = createViewModel(viewModelScope = backgroundScope)
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.onEventAccessCodeChanged("12a34b")
            val state = awaitItem()
            assertEquals("1234", state.eventAccessCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onConfirmClicked_success_navigatesToChoosePerson() = runTest {
        val event = Event(id = 1L, serverId = "ev-1", clientCreateId = "event-client-1", name = "Trip", pinCode = "1234", primaryCurrencyId = 1L)
        val eventDetails = EventDetails(
            event = event,
            persons = listOf(Person(id = 1L, serverId = "p1", clientCreateId = "person-client-1", name = "Alice")),
            currencies = listOf(Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)),
            primaryCurrency = Currency(1L, null, "EUR", "Euro", BigDecimal.ONE)
        )
        coEvery { joinEventUseCase.joinEvent(any(), any(), any()) } returns JoinEventResult.NewCurrentEvent(eventDetails)

        val viewModel = createViewModel(viewModelScope = backgroundScope)
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.onEventIdChanged("01EVENTID")
            viewModel.onEventAccessCodeChanged("1234")
            awaitItem()
            viewModel.onConfirmClicked()
            runCurrent()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 1) { navigationController.navigateTo(any()) }
    }

    @Test
    fun onConfirmClicked_error_updatesStateToError() = runTest {
        coEvery { joinEventUseCase.joinEvent(any(), any(), any()) } returns JoinEventResult.Error.InvalidAccessCode

        val viewModel = createViewModel(viewModelScope = backgroundScope)
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            viewModel.onEventIdChanged("01EV")
            viewModel.onEventAccessCodeChanged("0000")
            awaitItem()
            viewModel.onConfirmClicked()
            runCurrent()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        val state = viewModel.state.value
        val joining = assertIs<JoinEventPaneUiModel.EventJoiningState.Error>(state.joiningState)
        assertEquals("error_message", joining.message)
    }

    @Test
    fun onConfirmClicked_withoutAccessCodeOrToken_doesNotJoin() = runTest {
        val viewModel = createViewModel(viewModelScope = backgroundScope)
        runCurrent()
        advanceUntilIdle()

        viewModel.onEventIdChanged("01EV")
        viewModel.onConfirmClicked()
        runCurrent()
        advanceUntilIdle()

        coVerify(exactly = 0) { joinEventUseCase.joinEvent(any(), any(), any()) }
        assertIs<JoinEventPaneUiModel.EventJoiningState.None>(viewModel.state.value.joiningState)
    }

    @Test
    fun onConfirmClicked_withoutEventId_doesNotJoin() = runTest {
        val viewModel = createViewModel(viewModelScope = backgroundScope)
        runCurrent()
        advanceUntilIdle()

        viewModel.onEventAccessCodeChanged("1234")
        viewModel.onConfirmClicked()
        runCurrent()
        advanceUntilIdle()

        coVerify(exactly = 0) { joinEventUseCase.joinEvent(any(), any(), any()) }
        assertIs<JoinEventPaneUiModel.EventJoiningState.None>(viewModel.state.value.joiningState)
    }

    @Test
    fun onConfirmClicked_whileJoining_keepsOneRequestAndItsCredentials() = runTest {
        val joining = CompletableDeferred<JoinEventResult>()
        coEvery { joinEventUseCase.joinEvent(any(), any(), any()) } coAnswers { joining.await() }
        val viewModel = createViewModel(viewModelScope = backgroundScope)
        viewModel.onEventIdChanged("01ABC")
        viewModel.onEventAccessCodeChanged("1234")

        viewModel.onConfirmClicked()
        viewModel.onConfirmClicked()
        viewModel.onEventIdChanged("99XYZ")
        viewModel.onEventAccessCodeChanged("5678")
        viewModel.onUseInvitationClicked()
        runCurrent()

        assertIs<JoinEventPaneUiModel.EventJoiningState.Joining>(viewModel.state.value.joiningState)
        assertEquals("01ABC", viewModel.state.value.eventId)
        assertEquals("1234", viewModel.state.value.eventAccessCode)
        coVerify(exactly = 1) { joinEventUseCase.joinEvent("01ABC", "1234", null) }
        verify(exactly = 0) {
            navigationController.navigateTo(JoinInvitationPaneDestination, JoinInvitationPaneDestination, true)
        }

        joining.complete(JoinEventResult.Error.InvalidAccessCode)
        runCurrent()
        viewModel.onEventAccessCodeChanged("5678")
        assertIs<JoinEventPaneUiModel.EventJoiningState.None>(viewModel.state.value.joiningState)
        assertEquals("5678", viewModel.state.value.eventAccessCode)
    }

    @Test
    fun initialToken_autoJoinsWithoutAnAccessCode() = runTest {
        coEvery { joinEventUseCase.joinEvent(any(), any(), any()) } returns JoinEventResult.Error.InvalidToken
        createViewModel(initialEventId = "01ABC", initialToken = "abc123", viewModelScope = backgroundScope)
        runCurrent()

        coVerify(exactly = 1) { joinEventUseCase.joinEvent("01ABC", null, "abc123") }
    }

    @Test
    fun onUseInvitationClicked_navigatesToInvitationEntry() = runTest {
        val viewModel = createViewModel(viewModelScope = backgroundScope)
        viewModel.onUseInvitationClicked()

        verify {
            navigationController.navigateTo(JoinInvitationPaneDestination, JoinInvitationPaneDestination, true)
        }
    }

    @Test
    fun onConfirmClicked_cancelledOperation_clearsLoadingAndAllowsRetry() = runTest {
        coEvery { joinEventUseCase.joinEvent(any(), any(), any()) } throws CancellationException("join cancelled")
        val viewModel = createViewModel(viewModelScope = backgroundScope)
        viewModel.onEventIdChanged("01ABC")
        viewModel.onEventAccessCodeChanged("1234")
        viewModel.onConfirmClicked()
        runCurrent()

        assertIs<JoinEventPaneUiModel.EventJoiningState.None>(viewModel.state.value.joiningState)
        coEvery { joinEventUseCase.joinEvent(any(), any(), any()) } returns JoinEventResult.Error.InvalidAccessCode
        viewModel.onEventAccessCodeChanged("5678")
        viewModel.onConfirmClicked()
        runCurrent()

        coVerify { joinEventUseCase.joinEvent("01ABC", "5678", null) }
        assertIs<JoinEventPaneUiModel.EventJoiningState.Error>(viewModel.state.value.joiningState)
    }

    @Test
    fun initialAccessCode_autoJoinsWithoutAToken() = runTest {
        coEvery { joinEventUseCase.joinEvent(any(), any(), any()) } returns JoinEventResult.Error.InvalidAccessCode
        createViewModel(initialEventId = "01ABC", initialPinCode = "1234", viewModelScope = backgroundScope)
        runCurrent()

        coVerify(exactly = 1) { joinEventUseCase.joinEvent("01ABC", "1234", null) }
    }

    @Test
    fun onNavIconClicked_popsBackStack() = runTest {
        val viewModel = createViewModel(viewModelScope = backgroundScope)
        viewModel.onNavIconClicked()
        verify(exactly = 1) { navigationController.popBackStack() }
    }
}
