package com.inwords.expenses.feature.menu.ui

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.feature.events.domain.CreateEventInvitationUseCase
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.LeaveEventUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.EventInvitation
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.model.SeededCurrencies
import com.inwords.expenses.feature.events.ui.join_invitation.JoinInvitationPaneDestination
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState
import com.inwords.expenses.feature.share.api.ShareManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    private val createEventInvitationUseCase = mockk<CreateEventInvitationUseCase>()

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
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem().eventSharing
            assertEquals("", state.eventName)
            val shareState = assertIs<ShareState.Idle>(state.shareState)
            assertFalse(shareState.isAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun state_whenEventEmitted_hasEventNameAndAvailableSharing() = runTest {
        val event = Event(1L, "ev-1", "Trip", "1234", 1L, "event-client-1")
        val details = EventDetails(
            event = event,
            persons = listOf(Person(1L, "p1", "Alice", "person-client-1")),
            currencies = listOf(Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR"))),
            primaryCurrency = Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR")),
        )
        currentEventFlow.value = details
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            runCurrent()
            val state = viewModel.state.value.eventSharing
            assertEquals("Trip", state.eventName)
            val shareState = assertIs<ShareState.Idle>(state.shareState)
            assertTrue(shareState.isAvailable)
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
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            viewModelScope = backgroundScope,
        )

        viewModel.onJoinEventClicked()

        verify(exactly = 1) { navigationController.popBackStack() }
        verify(exactly = 1) { navigationController.navigateTo(JoinInvitationPaneDestination) }
    }

    @Test
    fun onLeaveEventClicked_jobGuard_doesNotCallLeaveTwiceWhenCalledRapidly() = runTest {
        currentEventFlow.value = null
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
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
        val event = Event(1L, "ev-1", "Trip", "1234", 1L, "event-client-1")
        val details = EventDetails(
            event = event,
            persons = listOf(Person(1L, "p1", "Alice", "person-client-1")),
            currencies = listOf(Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR"))),
            primaryCurrency = Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR")),
        )
        currentEventFlow.value = details
        coEvery { createEventInvitationUseCase.createInvitation("ev-1", "1234") } returns EventInvitation(
            url = "https://commonex.ru/event/ev-1?token=tok",
            expiresAt = kotlin.time.Instant.fromEpochMilliseconds(0),
        )
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            stringProvider = formattingStringProvider,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onShareClicked()
            runCurrent()
            advanceUntilIdle()
            val readyItem = viewModel.state.value.eventSharing
            val ready = assertIs<ShareState.Ready>(readyItem.shareState)
            assertEquals("Trip", ready.shareText.eventName)
            assertTrue(ready.shareText.fullText.startsWith("Trip|https://commonex.ru/event/ev-1?token=tok|"))
            coVerify(exactly = 1) {
                shareManager.shareText(subject = "Trip", fullText = ready.shareText.fullText)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onCopyClicked_whenInvitationHasNoExpiry_reusesFallbackAfterClipboardAcknowledgement() = runTest {
        val event = Event(1L, "ev-1", "Trip", "1234", 1L, "event-client-1")
        val details = EventDetails(
            event = event,
            persons = listOf(Person(1L, "p1", "Alice", "person-client-1")),
            currencies = listOf(Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR"))),
            primaryCurrency = Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR")),
        )
        currentEventFlow.value = details
        coEvery { createEventInvitationUseCase.createInvitation("ev-1", "1234") } returns EventInvitation(
            url = "https://commonex.ru/event/ev-1?pinCode=1234",
            expiresAt = null,
        )
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            stringProvider = formattingStringProvider,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            runCurrent()

            viewModel.onCopyClicked()
            runCurrent()
            advanceUntilIdle()

            val pendingCopy = assertIs<ShareState.PendingClipboardCopy>(viewModel.state.value.eventSharing.shareState)
            assertEquals("Trip", pendingCopy.shareText.eventName)
            assertEquals("Trip|https://commonex.ru/event/ev-1?pinCode=1234", pendingCopy.shareText.fullText)

            viewModel.onTextCopied()
            runCurrent()
            advanceUntilIdle()
            assertEquals(pendingCopy.shareText, assertIs<ShareState.Ready>(viewModel.state.value.eventSharing.shareState).shareText)

            viewModel.onShareClicked()
            runCurrent()
            advanceUntilIdle()
            coVerify(exactly = 1) {
                shareManager.shareText(subject = "Trip", fullText = pendingCopy.shareText.fullText)
            }
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { createEventInvitationUseCase.createInvitation("ev-1", "1234") }
    }

    @Test
    fun state_whenEventChanges_resetsPreviouslyGeneratedShareState() = runTest {
        val firstEvent = Event(1L, "ev-1", "Trip", "1234", 1L, "event-client-1")
        val secondEvent = Event(2L, "ev-2", "Party", "5678", 1L, "event-client-2")
        val firstDetails = EventDetails(
            event = firstEvent,
            persons = listOf(Person(1L, "p1", "Alice", "person-client-1")),
            currencies = listOf(Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR"))),
            primaryCurrency = Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR")),
        )
        val secondDetails = EventDetails(
            event = secondEvent,
            persons = listOf(Person(2L, "p2", "Bob", "person-client-2")),
            currencies = listOf(Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR"))),
            primaryCurrency = Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR")),
        )
        currentEventFlow.value = firstDetails
        coEvery { createEventInvitationUseCase.createInvitation("ev-1", "1234") } returns EventInvitation(
            url = "https://commonex.ru/event/ev-1?token=tok",
            expiresAt = kotlin.time.Instant.fromEpochMilliseconds(0),
        )
        coEvery { createEventInvitationUseCase.createInvitation("ev-2", "5678") } returns EventInvitation(
            url = "https://commonex.ru/event/ev-2?pinCode=5678",
            expiresAt = null,
        )
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            stringProvider = formattingStringProvider,
            viewModelScope = backgroundScope,
        )
        runCurrent()
        advanceUntilIdle()

        viewModel.state.test {
            awaitItem()
            runCurrent()
            val initialState = viewModel.state.value.eventSharing
            assertEquals("Trip", initialState.eventName)
            val initialShareState = assertIs<ShareState.Idle>(initialState.shareState)
            assertTrue(initialShareState.isAvailable)

            viewModel.onShareClicked()
            runCurrent()
            advanceUntilIdle()

            val readyState = assertIs<ShareState.Ready>(viewModel.state.value.eventSharing.shareState)
            assertTrue(readyState.shareText.fullText.startsWith("Trip|https://commonex.ru/event/ev-1?token=tok|"))

            currentEventFlow.value = secondDetails
            runCurrent()
            advanceUntilIdle()

            val resetState = viewModel.state.value.eventSharing
            assertEquals("Party", resetState.eventName)
            val resetShareState = assertIs<ShareState.Idle>(resetState.shareState)
            assertTrue(resetShareState.isAvailable)

            viewModel.onShareClicked()
            runCurrent()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                shareManager.shareText(subject = "Party", fullText = "Party|https://commonex.ru/event/ev-2?pinCode=5678")
            }
            coVerify(exactly = 1) { createEventInvitationUseCase.createInvitation("ev-2", "5678") }

            currentEventFlow.value = firstDetails
            runCurrent()
            viewModel.onCopyClicked()
            runCurrent()

            val pendingCopy = assertIs<ShareState.PendingClipboardCopy>(viewModel.state.value.eventSharing.shareState)
            assertEquals(readyState.shareText, pendingCopy.shareText)
            coVerify(exactly = 2) { createEventInvitationUseCase.createInvitation("ev-1", "1234") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shareAndCopy_whenRequestIsInProgress_doesNotCreateAnotherInvitation() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR"))
        currentEventFlow.value = EventDetails(
            event = Event(1L, "ev-1", "Trip", "1234", 1L, "event-client-1"),
            persons = emptyList(),
            currencies = listOf(currency),
            primaryCurrency = currency,
        )
        val invitation = CompletableDeferred<EventInvitation>()
        coEvery { createEventInvitationUseCase.createInvitation("ev-1", "1234") } coAnswers { invitation.await() }
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            stringProvider = formattingStringProvider,
            viewModelScope = backgroundScope,
        )

        viewModel.state.test {
            awaitItem()
            runCurrent()
            awaitItem()
            viewModel.onShareClicked()
            viewModel.onCopyClicked()
            runCurrent()
            assertIs<ShareState.Loading>(awaitItem().eventSharing.shareState)

            viewModel.onCopyClicked()
            viewModel.onShareClicked()
            runCurrent()
            coVerify(exactly = 1) { createEventInvitationUseCase.createInvitation("ev-1", "1234") }

            invitation.complete(EventInvitation(url = "https://commonex.ru/event/ev-1?pinCode=1234", expiresAt = null))
            runCurrent()
            val ready = assertIs<ShareState.Ready>(viewModel.state.value.eventSharing.shareState)
            assertEquals("Trip|https://commonex.ru/event/ev-1?pinCode=1234", ready.shareText.fullText)
            coVerify(exactly = 1) { shareManager.shareText(subject = "Trip", fullText = ready.shareText.fullText) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shareAndCopy_whenEventIsOnlyLocal_doNotCreateInvitation() = runTest {
        val currency = Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR"))
        currentEventFlow.value = EventDetails(
            event = Event(1L, null, "Trip", "1234", 1L, "event-client-1"),
            persons = listOf(Person(1L, "p1", "Alice", "person-client-1")),
            currencies = listOf(currency),
            primaryCurrency = currency,
        )
        val viewModel = MenuViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            leaveEventUseCase = leaveEventUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            viewModelScope = backgroundScope,
        )

        viewModel.state.test {
            awaitItem()
            runCurrent()
            assertFalse(assertIs<ShareState.Idle>(viewModel.state.value.eventSharing.shareState).isAvailable)

            viewModel.onShareClicked()
            viewModel.onCopyClicked()
            runCurrent()

            assertIs<ShareState.Idle>(viewModel.state.value.eventSharing.shareState)
            coVerify(exactly = 0) { createEventInvitationUseCase.createInvitation(any(), any()) }
            coVerify(exactly = 0) { shareManager.shareText(any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private val formattingStringProvider = object : StringProvider {
        override suspend fun getString(stringResource: StringResource): String = "unused"

        override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String {
            return formatArgs.joinToString("|")
        }
    }
}
