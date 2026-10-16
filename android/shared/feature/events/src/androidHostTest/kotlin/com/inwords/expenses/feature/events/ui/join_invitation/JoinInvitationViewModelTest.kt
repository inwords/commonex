package com.inwords.expenses.feature.events.ui.join_invitation

import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.events.ui.join.JoinEventPaneDestination
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class JoinInvitationViewModelTest {

    private val navigationController = mockk<NavigationController>(relaxed = true)

    @Test
    fun confirm_invalidInvitation_showsValidationUntilEdited() {
        val viewModel = JoinInvitationViewModel(navigationController = navigationController)
        listOf(
            "not a link",
            "https://other.example/event/01ABC?token=abc123",
            "https://commonex.ru/other/01ABC?token=abc123",
            "ftp://commonex.ru/event/01ABC?token=abc123",
            "https://commonex.ru/event?token=abc123",
            "https://commonex.ru/event/01ABC/extra?token=abc123",
            "https://commonex.ru/event/01ABC",
            "https://commonex.ru/event/01ABC?pinCode=1234&token=abc123",
            "https://commonex.ru/event/01ABC?token=",
            "https://commonex.ru/event/IOU?pinCode=abc",
        ).forEach { invitation ->
            viewModel.onInvitationChanged(invitation)
            viewModel.onConfirmClicked()
            assertTrue(viewModel.state.value.isInvitationInvalid)
        }
        verify(exactly = 0) { navigationController.navigateTo(any()) }

        viewModel.onInvitationChanged("https://commonex.ru/event/01ABC?token=abc123")
        assertFalse(viewModel.state.value.isInvitationInvalid)
        assertEquals("https://commonex.ru/event/01ABC?token=abc123", viewModel.state.value.invitation)
    }

    @Test
    fun confirm_tokenInvitation_navigatesToExistingJoinFlow() {
        val viewModel = JoinInvitationViewModel(navigationController = navigationController)
        viewModel.onInvitationChanged("  https://commonex.ru/event/01ABC?token=abc123  ")
        viewModel.onConfirmClicked()

        verify { navigationController.navigateTo(JoinEventPaneDestination(eventId = "01ABC", token = "abc123")) }
    }

    @Test
    fun confirm_httpInvitation_navigatesToExistingJoinFlow() {
        val viewModel = JoinInvitationViewModel(navigationController = navigationController)
        viewModel.onInvitationChanged("http://commonex.ru/event/01ABC?token=abc123")
        viewModel.onConfirmClicked()

        verify { navigationController.navigateTo(JoinEventPaneDestination(eventId = "01ABC", token = "abc123")) }
    }

    @Test
    fun confirm_pinInvitation_navigatesToExistingJoinFlow() {
        val viewModel = JoinInvitationViewModel(navigationController = navigationController)
        viewModel.onInvitationChanged("https://commonex.ru/event/01ABC?pinCode=1234")
        viewModel.onConfirmClicked()

        verify { navigationController.navigateTo(JoinEventPaneDestination(eventId = "01ABC", pinCode = "1234")) }
    }

    @Test
    fun enterCode_navigatesToManualEntry() {
        val viewModel = JoinInvitationViewModel(navigationController = navigationController)
        viewModel.onEnterCodeClicked()

        verify { navigationController.navigateTo(JoinEventPaneDestination()) }
    }

    @Test
    fun back_returnsToPreviousScreen() {
        val viewModel = JoinInvitationViewModel(navigationController = navigationController)
        viewModel.onNavIconClicked()

        verify { navigationController.popBackStack() }
    }
}
