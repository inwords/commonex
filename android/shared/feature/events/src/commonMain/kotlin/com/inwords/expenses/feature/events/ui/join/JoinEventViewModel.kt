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
import expenses.shared.core.ui_design.generated.resources.error_other
import expenses.shared.feature.events.generated.resources.Res
import expenses.shared.feature.events.generated.resources.events_join_error_expired_token
import expenses.shared.feature.events.generated.resources.events_join_error_invalid_credentials
import expenses.shared.feature.events.generated.resources.events_join_error_invalid_token
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

    private val eventIdRegex = "[0-9A-HJKMNP-TV-Z]".toRegex()

    private var confirmJob: Job? = null

    private val initialState = JoinEventPaneUiModel(
        eventId = initialEventId.filteredEventId(),
        eventAccessCode = initialPinCode.filteredPinCode(),
        joining = EventJoiningState.None,
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
            value.copy(eventId = eventId.filteredEventId(), joining = EventJoiningState.None)
        }
    }

    fun onEventAccessCodeChanged(eventAccessCode: String) {
        state.update { value ->
            value.copy(eventAccessCode = eventAccessCode.filteredPinCode(), joining = EventJoiningState.None)
        }
    }

    fun onConfirmClicked(eventToken: String? = null) {
        confirmJob?.cancel()

        val state = state.updateAndGet { currentState ->
            currentState.copy(joining = EventJoiningState.Joining)
        }
        confirmJob = viewModelScope.launch {
            val result = joinEventUseCase.joinEvent(
                eventServerId = state.eventId,
                accessCode = state.eventAccessCode.ifBlank { null }.takeIf { eventToken == null },
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

                            JoinEventResult.Error.OtherError -> DesignRes.string.error_other
                        }
                    )
                    this@JoinEventViewModel.state.update { currentState ->
                        currentState.copy(joining = EventJoiningState.Error(errorMessage))
                    }
                }
            }
        }
    }

    fun onNavIconClicked() {
        navigationController.popBackStack()
    }

    private fun String.filteredEventId(): String {
        return this.uppercase().filter { it.toString().matches(eventIdRegex) }
    }

    private fun String.filteredPinCode(): String {
        return this.filter { it.isDigit() }
    }

    private fun String.filteredToken(): String {
        return this.filter { it.isLetterOrDigit() }
    }

}
