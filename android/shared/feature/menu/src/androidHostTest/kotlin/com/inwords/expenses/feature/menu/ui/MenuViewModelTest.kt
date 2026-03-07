package com.inwords.expenses.feature.menu.ui

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.feature.events.domain.CreateShareTokenUseCase
import com.inwords.expenses.feature.events.domain.CreateShareTokenUseCase.CreateShareTokenResult
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.LeaveEventUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.EventShareToken
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.menu.ui.MenuDialogUiModel.ShareState
import com.inwords.expenses.feature.share.api.ShareManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
internal class MenuViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val navigationController = mockk<NavigationController>(relaxed = true) {
        justRun { popBackStack() }
    }
    private val currentEventFlow = MutableStateFlow<EventDetails?>(null)
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase> {
        every { currentEvent } returns currentEventFlow
    }
    private val leaveEventUseCase = mockk<LeaveEventUseCase>(relaxed = true)
    private val shareManager = mockk<ShareManager>(relaxed = true)
    private val createShareTokenUseCase = mockk<CreateShareTokenUseCase>(relaxed = true)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_whenNoEvent_hasEmptyEventNameAndIdleShareState() = runTest {
        currentEventFlow.value = null
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createShareTokenUseCaseLazy = lazy { createShareTokenUseCase },
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertEquals("", state.eventName)
            val shareState = assertIs<ShareState.Idle>(state.shareState)
            assertEquals(null, shareState.serverId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun state_whenEventEmitted_hasEventNameAndIdleWithServerId() = runTest {
        val event = Event(1L, "ev-1", "Trip", "1234", 1L)
        val details = EventDetails(
            event = event,
            persons = listOf(Person(1L, "p1", "Alice")),
            currencies = listOf(Currency(1L, null, "EUR", "Euro")),
            primaryCurrency = Currency(1L, null, "EUR", "Euro"),
        )
        currentEventFlow.value = details
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createShareTokenUseCaseLazy = lazy { createShareTokenUseCase },
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            val state = awaitItem()
            assertEquals("Trip", state.eventName)
            val shareState = assertIs<ShareState.Idle>(state.shareState)
            assertEquals("ev-1", shareState.serverId)
            assertEquals("1234", shareState.pinCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onJoinEventClicked_popsAndNavigatesToJoin() = runTest {
        currentEventFlow.value = null
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createShareTokenUseCaseLazy = lazy { createShareTokenUseCase },
            viewModelScope = backgroundScope,
        )

        viewModel.onJoinEventClicked()

        verify(exactly = 1) { navigationController.popBackStack() }
        verify(exactly = 1) { navigationController.navigateTo(any()) }
    }

    @Test
    fun onLeaveEventClicked_jobGuard_doesNotCallLeaveTwiceWhenCalledRapidly() = runTest {
        currentEventFlow.value = null
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createShareTokenUseCaseLazy = lazy { createShareTokenUseCase },
            viewModelScope = backgroundScope,
        )

        viewModel.onLeaveEventClicked()
        viewModel.onLeaveEventClicked()
        runCurrent()
        advanceUntilIdle()
        coVerify(exactly = 1) { leaveEventUseCase.leaveEvent() }
    }

    @Test
    fun onShareClicked_whenTokenCreated_endsInReadyState() = runTest {
        val event = Event(1L, "ev-1", "Trip", "1234", 1L)
        val details = EventDetails(
            event = event,
            persons = listOf(Person(1L, "p1", "Alice")),
            currencies = listOf(Currency(1L, null, "EUR", "Euro")),
            primaryCurrency = Currency(1L, null, "EUR", "Euro"),
        )
        currentEventFlow.value = details
        coEvery { createShareTokenUseCase.createShareToken(any(), any()) } returns CreateShareTokenResult.Created(
            EventShareToken("tok", kotlin.time.Instant.fromEpochMilliseconds(0))
        )
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createShareTokenUseCaseLazy = lazy { createShareTokenUseCase },
            stringProvider = object : StringProvider {
                override suspend fun getString(stringResource: StringResource): String = "share_message"
                override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String = "share_message"
            },
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            skipItems(1)
            awaitItem()
            viewModel.onShareClicked()
            runCurrent()
            advanceUntilIdle()
            val readyItem = awaitItem()
            val _ = assertIs<ShareState.Ready>(readyItem.shareState)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
