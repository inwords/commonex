package com.inwords.expenses.feature.events.ui.add_participants

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.events.api.EventsComponent
import kotlinx.serialization.Serializable

@Serializable
object AddParticipantsToEventPaneDestination : Destination

internal fun EventsComponent.getAddParticipantsToEventPaneNavModule(
    navigationController: NavigationController,
): NavModule {
    return NavModule(AddParticipantsToEventPaneDestination.serializer()) {
        entry<AddParticipantsToEventPaneDestination> {
            val viewModel = viewModel<AddParticipantsToEventViewModel>(factory = viewModelFactory {
                initializer {
                    AddParticipantsToEventViewModel(
                        navigationController = navigationController,
                        addParticipantsToCurrentEventUseCase = addParticipantsToCurrentEventUseCaseLazy.value,
                    )
                }
            })
            AddParticipantsToEventPane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onParticipantNameChanged = viewModel::onParticipantNameChanged,
                onAddParticipantClicked = viewModel::onAddParticipantClicked,
                onConfirmClicked = viewModel::onConfirmClicked,
                onNavIconClicked = viewModel::onNavIconClicked,
            )
        }
    }
}
