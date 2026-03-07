package com.inwords.expenses.feature.events.ui.add_persons

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
object AddPersonsPaneDestination : Destination

internal fun EventsComponent.getAddPersonsPaneNavModule(
    navigationController: NavigationController,
    expensesPaneDestination: Destination,
): NavModule {
    return NavModule(AddPersonsPaneDestination.serializer()) {
        entry<AddPersonsPaneDestination> {
            val viewModel = viewModel<AddPersonsViewModel>(factory = viewModelFactory {
                initializer {
                    AddPersonsViewModel(
                        navigationController = navigationController,
                        eventCreationStateHolder = eventCreationStateHolder.value,
                        createEventUseCase = createEventUseCaseLazy.value,
                        expensesScreenDestination = expensesPaneDestination
                    )
                }
            })
            AddPersonsPane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onOwnerNameChanged = viewModel::onOwnerNameChanged,
                onParticipantNameChanged = viewModel::onParticipantNameChanged,
                onAddParticipantClicked = viewModel::onAddParticipantClicked,
                onConfirmClicked = viewModel::onConfirmClicked,
                onNavIconClicked = viewModel::onNavIconClicked,
            )
        }
    }
}
