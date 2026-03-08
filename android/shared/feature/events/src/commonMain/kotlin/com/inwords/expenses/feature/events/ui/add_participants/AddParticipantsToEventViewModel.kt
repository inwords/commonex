package com.inwords.expenses.feature.events.ui.add_participants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.feature.events.domain.AddParticipantsToCurrentEventUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class AddParticipantsToEventViewModel(
    private val navigationController: NavigationController,
    private val addParticipantsToCurrentEventUseCase: AddParticipantsToCurrentEventUseCase,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    private var confirmJob: Job? = null

    val state: StateFlow<AddParticipantsToEventPaneUiModel>
        field = MutableStateFlow(AddParticipantsToEventPaneUiModel(listOf("")))

    fun onParticipantNameChanged(index: Int, participantName: String) {
        state.update { model ->
            model.copy(participants = model.participants.toMutableList().apply {
                set(index, participantName)
            })
        }
    }

    fun onAddParticipantClicked() {
        state.update { value ->
            value.copy(participants = value.participants + "")
        }
    }

    fun onConfirmClicked() {
        val currentState = state.value
        if (!currentState.isConfirmEnabled) return

        confirmJob?.cancel()
        confirmJob = viewModelScope.launch {
            addParticipantsToCurrentEventUseCase.addParticipants(currentState.participants)
            navigationController.popBackStack()
        }
    }

    fun onNavIconClicked() {
        navigationController.popBackStack()
    }
}
