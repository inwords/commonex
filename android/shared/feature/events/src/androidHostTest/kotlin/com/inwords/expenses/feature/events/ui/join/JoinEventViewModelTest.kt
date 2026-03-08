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
import io.mockk.coEvery
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
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
            val _ = assertIs<JoinEventPaneUiModel.EventJoiningState.None>(initial.joining)
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
        val event = Event(1L, "ev-1", "Trip", "1234", 1L)
        val eventDetails = EventDetails(
            event = event,
            persons = listOf(Person(1L, "p1", "Alice")),
            currencies = listOf(Currency(1L, null, "EUR", "Euro")),
            primaryCurrency = Currency(1L, null, "EUR", "Euro")
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
        val joining = assertIs<JoinEventPaneUiModel.EventJoiningState.Error>(state.joining)
        assertEquals("error_message", joining.message)
    }

    @Test
    fun onNavIconClicked_popsBackStack() = runTest {
        val viewModel = createViewModel(viewModelScope = backgroundScope)
        viewModel.onNavIconClicked()
        verify(exactly = 1) { navigationController.popBackStack() }
    }
}
