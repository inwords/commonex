package com.inwords.expenses.feature.events.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.DefaultStringProvider
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.feature.events.domain.JoinEventUseCase
import com.inwords.expenses.feature.events.domain.JoinEventUseCase.JoinEventResult
import com.inwords.expenses.feature.events.ui.choose_person.ChoosePersonPaneDestination
import com.inwords.expenses.feature.events.ui.join.JoinEventPaneUiModel.EventJoiningState
import com.inwords.expenses.feature.events.ui.join_invitation.JoinInvitationPaneDestination
import expenses.shared.core.ui_design.generated.resources.error_other
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.events_join_error_expired_token
import expenses.shared.feature.events.generated.resources.events_join_error_invalid_credentials
import expenses.shared.feature.events.generated.resources.events_join_error_invalid_token
import expenses.shared.feature.events.generated.resources.events_join_error_update_required
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import expenses.shared.core.ui_design.generated.resources.Res as DesignRes

internal class JoinEventViewModel(
    private val navigationController: NavigationController,
    private val joinEventUseCase: JoinEventUseCase,
    private val stringProvider: StringProvider = DefaultStringProvider,
    initialEventId: String,
    initialPinCode: String,
    initialToken: String,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    private var confirmJob: Job? = null

    private val initialState = JoinEventPaneUiModel(
        eventId = initialEventId.filteredEventId(),
        eventAccessCode = initialPinCode.filteredPinCode(),
        joiningState = EventJoiningState.None,
    )
    val state: StateFlow<JoinEventPaneUiModel>
        field = MutableStateFlow(initialState)

    init {
        // Auto-trigger join if we have initial token or accessCode
        if (initialState.eventId.isNotBlank()) {
            val filteredToken = initialToken.filteredToken().ifBlank { null }
            if (filteredToken != null || initialState.eventAccessCode.isNotBlank()) {
                onConfirmClicked(eventToken = filteredToken)
            }
        }
    }

    fun onEventIdChanged(eventId: String) {
        state.update { value ->
            if (value.joiningState == EventJoiningState.Joining) {
                value
            } else {
                value.copy(eventId = eventId.filteredEventId(), joiningState = EventJoiningState.None)
            }
        }
    }

    fun onEventAccessCodeChanged(eventAccessCode: String) {
        state.update { value ->
            if (value.joiningState == EventJoiningState.Joining) {
                value
            } else {
                value.copy(eventAccessCode = eventAccessCode.filteredPinCode(), joiningState = EventJoiningState.None)
            }
        }
    }

    fun onUseInvitationClicked() {
        if (confirmJob?.isActive == true) return

        navigationController.navigateTo(
            destination = JoinInvitationPaneDestination,
            popUpTo = JoinInvitationPaneDestination,
            launchSingleTop = true,
        )
    }

    fun onConfirmClicked(eventToken: String? = null) {
        if (confirmJob?.isActive == true) return

        val currentState = state.value
        val accessCode = currentState.eventAccessCode.ifBlank { null }.takeIf { eventToken == null }
        if (currentState.eventId.isBlank() || (accessCode == null && eventToken == null)) {
            return
        }

        val state = state.updateAndGet { currentState ->
            currentState.copy(joiningState = EventJoiningState.Joining)
        }
        confirmJob = viewModelScope.launch {
            val result = joinEventUseCase.joinEvent(
                eventServerId = state.eventId,
                accessCode = accessCode,
                token = eventToken,
            )

            when (result) {
                is JoinEventResult.NewCurrentEvent -> navigationController.navigateTo(
                    destination = ChoosePersonPaneDestination
                )

                is JoinEventResult.Error -> {
                    val errorMessage = stringProvider.getString(
                        when (result) {
                            JoinEventResult.Error.EventNotFound,
                            JoinEventResult.Error.InvalidAccessCode -> Res.string.events_join_error_invalid_credentials

                            JoinEventResult.Error.InvalidToken -> Res.string.events_join_error_invalid_token
                            JoinEventResult.Error.TokenExpired -> Res.string.events_join_error_expired_token
                            JoinEventResult.Error.UpdateRequired -> Res.string.events_join_error_update_required

                            JoinEventResult.Error.OtherError -> DesignRes.string.error_other
                        }
                    )
                    this@JoinEventViewModel.state.update { currentState ->
                        currentState.copy(joiningState = EventJoiningState.Error(errorMessage))
                    }
                }
            }
        }.apply {
            invokeOnCompletion {
                confirmJob = null
                this@JoinEventViewModel.state.update { currentState ->
                    if (currentState.joiningState == EventJoiningState.Joining) {
                        currentState.copy(joiningState = EventJoiningState.None)
                    } else {
                        currentState
                    }
                }
            }
        }
    }

    fun onNavIconClicked() {
        navigationController.popBackStack()
    }

}
