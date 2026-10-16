package com.inwords.expenses.feature.menu.ui

import app.cash.turbine.test
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.feature.events.domain.CreateEventInvitationUseCase
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.model.Currency
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.events.domain.model.EventDetails
import com.inwords.expenses.feature.events.domain.model.EventInvitation
import com.inwords.expenses.feature.events.domain.model.Person
import com.inwords.expenses.feature.events.domain.model.SeededCurrencies
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState
import com.inwords.expenses.feature.share.api.ShareManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class InviteEventViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val navigationController = mockk<NavigationController>(relaxed = true)
    private val currentEventFlow = MutableStateFlow<EventDetails?>(null)
    private val getCurrentEventStateUseCase = mockk<GetCurrentEventStateUseCase> {
        every { currentEvent } returns currentEventFlow
    }
    private val shareManager = mockk<ShareManager>(relaxed = true)
    private val createEventInvitationUseCase = mockk<CreateEventInvitationUseCase>()
    private val currency = Currency(1L, null, "EUR", "Euro", SeededCurrencies.usdToOtherRates.getValue("EUR"))
    private val eventDetails = EventDetails(
        event = Event(1L, "ev-1", "Trip", "1234", 1L, "event-client-1"),
        persons = listOf(Person(1L, "p1", "Alice", "person-client-1")),
        currencies = listOf(currency),
        primaryCurrency = currency,
    )
    private val formattingStringProvider = object : StringProvider {
        override suspend fun getString(stringResource: StringResource): String = "unused"
        override suspend fun getString(stringResource: StringResource, vararg formatArgs: Any): String {
            return formatArgs.joinToString("|")
        }
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onBackClick_popsInvitationScreen() = runTest {
        val viewModel = InviteEventViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            viewModelScope = backgroundScope,
        )

        viewModel.onBackClick()

        verify(exactly = 1) { navigationController.popBackStack() }
    }

    @Test
    fun onCopyClicked_acknowledgesClipboardAndReusesFallbackForSharingAndCopying() = runTest {
        currentEventFlow.value = eventDetails
        coEvery { createEventInvitationUseCase.createInvitation("ev-1", "1234") } returns EventInvitation(
            url = "https://commonex.ru/event/ev-1?pinCode=1234",
            expiresAt = null,
        )
        val viewModel = InviteEventViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
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
            assertEquals("Trip", viewModel.state.value.eventName)

            viewModel.onCopyClicked()
            runCurrent()
            advanceUntilIdle()

            val pending = assertIs<ShareState.PendingClipboardCopy>(viewModel.state.value.shareState)
            assertEquals("Trip|https://commonex.ru/event/ev-1?pinCode=1234", pending.shareText.fullText)

            viewModel.onTextCopied()
            runCurrent()
            advanceUntilIdle()

            assertEquals(pending.shareText, assertIs<ShareState.Ready>(viewModel.state.value.shareState).shareText)

            viewModel.onShareClicked()
            runCurrent()
            advanceUntilIdle()
            coVerify(exactly = 1) { shareManager.shareText(subject = "Trip", fullText = pending.shareText.fullText) }

            viewModel.onCopyClicked()
            runCurrent()
            advanceUntilIdle()
            assertEquals(pending.shareText, assertIs<ShareState.PendingClipboardCopy>(viewModel.state.value.shareState).shareText)
            coVerify(exactly = 1) { createEventInvitationUseCase.createInvitation("ev-1", "1234") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onShareClicked_formatsSecureInvitationAndOpensNativeSharing() = runTest {
        currentEventFlow.value = eventDetails
        coEvery { createEventInvitationUseCase.createInvitation("ev-1", "1234") } returns EventInvitation(
            url = "https://commonex.ru/event/ev-1?token=tok",
            expiresAt = Instant.fromEpochMilliseconds(0),
        )
        val viewModel = InviteEventViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            stringProvider = formattingStringProvider,
            viewModelScope = backgroundScope,
        )

        viewModel.state.test {
            awaitItem()
            runCurrent()

            viewModel.onShareClicked()
            runCurrent()
            advanceUntilIdle()

            val ready = assertIs<ShareState.Ready>(viewModel.state.value.shareState)
            assertEquals("Trip", ready.shareText.eventName)
            assertTrue(ready.shareText.fullText.startsWith("Trip|https://commonex.ru/event/ev-1?token=tok|"))
            coVerify(exactly = 1) { shareManager.shareText(subject = "Trip", fullText = ready.shareText.fullText) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shareAndCopy_whenEventIsOnlyLocal_doNotCreateInvitation() = runTest {
        currentEventFlow.value = eventDetails.copy(event = eventDetails.event.copy(serverId = null))
        val viewModel = InviteEventViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            viewModelScope = backgroundScope,
        )

        viewModel.state.test {
            awaitItem()
            runCurrent()
            assertFalse(assertIs<ShareState.Idle>(viewModel.state.value.shareState).isAvailable)

            viewModel.onShareClicked()
            viewModel.onCopyClicked()
            runCurrent()

            assertIs<ShareState.Idle>(viewModel.state.value.shareState)
            coVerify(exactly = 0) { createEventInvitationUseCase.createInvitation(any(), any()) }
            coVerify(exactly = 0) { shareManager.shareText(any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun state_whenEventChanges_discardsCachedInvitation() = runTest {
        currentEventFlow.value = eventDetails
        coEvery { createEventInvitationUseCase.createInvitation("ev-1", "1234") } returns EventInvitation(
            url = "https://commonex.ru/event/ev-1?pinCode=1234",
            expiresAt = null,
        )
        coEvery { createEventInvitationUseCase.createInvitation("ev-2", "5678") } returns EventInvitation(
            url = "https://commonex.ru/event/ev-2?pinCode=5678",
            expiresAt = null,
        )
        val viewModel = InviteEventViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            stringProvider = formattingStringProvider,
            viewModelScope = backgroundScope,
        )

        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onCopyClicked()
            runCurrent()
            assertIs<ShareState.PendingClipboardCopy>(viewModel.state.value.shareState)

            currentEventFlow.value = eventDetails.copy(
                event = eventDetails.event.copy(id = 2L, serverId = "ev-2", name = "Party", pinCode = "5678"),
            )
            runCurrent()
            assertEquals("Party", viewModel.state.value.eventName)
            assertTrue(assertIs<ShareState.Idle>(viewModel.state.value.shareState).isAvailable)

            viewModel.onCopyClicked()
            runCurrent()

            val pending = assertIs<ShareState.PendingClipboardCopy>(viewModel.state.value.shareState)
            assertEquals("Party|https://commonex.ru/event/ev-2?pinCode=5678", pending.shareText.fullText)
            coVerify(exactly = 1) { createEventInvitationUseCase.createInvitation("ev-2", "5678") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shareAndCopy_whileInvitationIsBeingCreated_onlyShareOnce() = runTest {
        currentEventFlow.value = eventDetails
        val invitation = CompletableDeferred<EventInvitation>()
        coEvery { createEventInvitationUseCase.createInvitation("ev-1", "1234") } coAnswers { invitation.await() }
        val viewModel = InviteEventViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            stringProvider = formattingStringProvider,
            viewModelScope = backgroundScope,
        )

        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onShareClicked()
            viewModel.onCopyClicked()
            viewModel.onShareClicked()
            runCurrent()

            assertIs<ShareState.Loading>(viewModel.state.value.shareState)
            coVerify(exactly = 1) { createEventInvitationUseCase.createInvitation("ev-1", "1234") }

            invitation.complete(EventInvitation(url = "https://commonex.ru/event/ev-1?pinCode=1234", expiresAt = null))
            runCurrent()

            assertIs<ShareState.Ready>(viewModel.state.value.shareState)
            coVerify(exactly = 1) {
                shareManager.shareText(subject = "Trip", fullText = "Trip|https://commonex.ru/event/ev-1?pinCode=1234")
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun state_whenEventChangesDuringInvitationCreation_doesNotShareOldEventAndAllowsNewCopy() = runTest {
        currentEventFlow.value = eventDetails
        val firstInvitation = CompletableDeferred<EventInvitation>()
        coEvery { createEventInvitationUseCase.createInvitation("ev-1", "1234") } coAnswers { firstInvitation.await() }
        coEvery { createEventInvitationUseCase.createInvitation("ev-2", "5678") } returns EventInvitation(
            url = "https://commonex.ru/event/ev-2?pinCode=5678",
            expiresAt = null,
        )
        val viewModel = InviteEventViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            stringProvider = formattingStringProvider,
            viewModelScope = backgroundScope,
        )

        viewModel.state.test {
            awaitItem()
            runCurrent()
            viewModel.onShareClicked()
            runCurrent()
            assertIs<ShareState.Loading>(viewModel.state.value.shareState)

            currentEventFlow.value = eventDetails.copy(
                event = eventDetails.event.copy(id = 2L, serverId = "ev-2", name = "Party", pinCode = "5678"),
            )
            runCurrent()
            assertTrue(assertIs<ShareState.Idle>(viewModel.state.value.shareState).isAvailable)

            firstInvitation.complete(EventInvitation(url = "https://commonex.ru/event/ev-1?pinCode=1234", expiresAt = null))
            runCurrent()
            coVerify(exactly = 0) { shareManager.shareText(any(), any()) }

            viewModel.onCopyClicked()
            runCurrent()
            val pending = assertIs<ShareState.PendingClipboardCopy>(viewModel.state.value.shareState)
            assertEquals("Party|https://commonex.ru/event/ev-2?pinCode=5678", pending.shareText.fullText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancelledShareCleanup_doesNotResetNewEventLoadingStateOrAllowDuplicateCopy() = runTest {
        currentEventFlow.value = eventDetails
        val firstCleanup = CompletableDeferred<Unit>()
        val secondInvitation = CompletableDeferred<EventInvitation>()
        coEvery { createEventInvitationUseCase.createInvitation("ev-1", "1234") } coAnswers {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { firstCleanup.await() }
            }
        }
        coEvery { createEventInvitationUseCase.createInvitation("ev-2", "5678") } coAnswers { secondInvitation.await() }
        val viewModel = InviteEventViewModel(
            navigationController = navigationController,
            getCurrentEventStateUseCase = getCurrentEventStateUseCase,
            shareManagerLazy = lazy { shareManager },
            createEventInvitationUseCaseLazy = lazy { createEventInvitationUseCase },
            stringProvider = formattingStringProvider,
            viewModelScope = backgroundScope,
        )

        try {
            viewModel.state.test {
                awaitItem()
                runCurrent()
                viewModel.onShareClicked()
                runCurrent()
                assertIs<ShareState.Loading>(viewModel.state.value.shareState)

                currentEventFlow.value = eventDetails.copy(
                    event = eventDetails.event.copy(id = 2L, serverId = "ev-2", name = "Party", pinCode = "5678"),
                )
                runCurrent()
                viewModel.onCopyClicked()
                runCurrent()
                assertIs<ShareState.Loading>(viewModel.state.value.shareState)

                firstCleanup.complete(Unit)
                runCurrent()
                assertIs<ShareState.Loading>(viewModel.state.value.shareState)

                viewModel.onCopyClicked()
                runCurrent()
                coVerify(exactly = 1) { createEventInvitationUseCase.createInvitation("ev-2", "5678") }

                secondInvitation.complete(EventInvitation(url = "https://commonex.ru/event/ev-2?pinCode=5678", expiresAt = null))
                runCurrent()
                val pending = assertIs<ShareState.PendingClipboardCopy>(viewModel.state.value.shareState)
                assertEquals("Party|https://commonex.ru/event/ev-2?pinCode=5678", pending.shareText.fullText)
                coVerify(exactly = 0) { shareManager.shareText(any(), any()) }
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            firstCleanup.complete(Unit)
        }
    }
}
