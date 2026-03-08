package com.inwords.expenses.feature.events.ui.add_persons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.feature.events.domain.CreateEventUseCase
import com.inwords.expenses.feature.events.domain.EventCreationStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class AddPersonsViewModel(
    private val navigationController: NavigationController,
    private val eventCreationStateHolder: EventCreationStateHolder,
    private val createEventUseCase: CreateEventUseCase,
    private val expensesScreenDestination: Destination,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    private var confirmJob: Job? = null

    val state: StateFlow<AddPersonsPaneUiModel>
        field = MutableStateFlow(AddPersonsPaneUiModel("", emptyList()))

    fun onOwnerNameChanged(ownerName: String) {
        state.update { value ->
            value.copy(ownerName = ownerName)
        }
    }

    fun onParticipantNameChanged(index: Int, participantName: String) {
        state.update { value ->
            value.copy(persons = value.persons.toMutableList().apply {
                set(index, participantName)
            })
        }
    }

    fun onAddParticipantClicked() {
        state.update { value ->
            value.copy(persons = value.persons + "")
        }
    }

    fun onConfirmClicked() {
        // TODO mvp
        confirmJob?.cancel()
        confirmJob = viewModelScope.launch {
            val state = state.value

            eventCreationStateHolder.draftOwner(state.ownerName)
            eventCreationStateHolder.draftOtherPersons(state.persons)
            createEventUseCase.createEvent()

            navigationController.popBackStack(
                toDestination = expensesScreenDestination,
                inclusive = false
            )
        }
    }

    fun onNavIconClicked() {
        navigationController.popBackStack()
    }
}