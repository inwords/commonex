package com.inwords.expenses.feature.events.ui.join_invitation

import androidx.lifecycle.ViewModel
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.navigation.deeplinkHostPath
import com.inwords.expenses.feature.events.ui.join.JoinEventPaneDestination
import com.inwords.expenses.feature.events.ui.join.filteredEventId
import com.inwords.expenses.feature.events.ui.join.filteredPinCode
import com.inwords.expenses.feature.events.ui.join.filteredToken
import io.ktor.http.URLProtocol
import io.ktor.http.parseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

internal class JoinInvitationViewModel(
    private val navigationController: NavigationController,
) : ViewModel() {

    val state: StateFlow<JoinInvitationPaneUiModel>
        field = MutableStateFlow(JoinInvitationPaneUiModel(invitation = "", isInvitationInvalid = false))

    fun onInvitationChanged(invitation: String) {
        state.value = JoinInvitationPaneUiModel(invitation = invitation, isInvitationInvalid = false)
    }

    fun onConfirmClicked() {
        val destination = parseJoinDestination(state.value.invitation)
        if (destination == null) {
            state.update { it.copy(isInvitationInvalid = true) }
        } else {
            navigationController.navigateTo(destination = destination)
        }
    }

    fun onEnterCodeClicked() {
        navigationController.navigateTo(destination = JoinEventPaneDestination())
    }

    fun onNavIconClicked() {
        navigationController.popBackStack()
    }

    private fun parseJoinDestination(invitation: String): JoinEventPaneDestination? {
        val url = parseUrl(invitation.trim()) ?: return null
        if (url.protocol != URLProtocol.HTTPS && url.protocol != URLProtocol.HTTP) return null
        if (url.host != deeplinkHostPath || url.segments.size != 2 || url.segments[0] != "event") return null

        val eventId = url.segments[1]
        val pinCode = url.parameters["pinCode"].orEmpty()
        val token = url.parameters["token"].orEmpty()
        if (eventId.filteredEventId().isBlank()) return null
        if (pinCode.isNotBlank() && token.isNotBlank()) return null
        if (pinCode.filteredPinCode().isBlank() && token.filteredToken().isBlank()) return null

        return JoinEventPaneDestination(eventId = eventId, pinCode = pinCode, token = token)
    }
}
