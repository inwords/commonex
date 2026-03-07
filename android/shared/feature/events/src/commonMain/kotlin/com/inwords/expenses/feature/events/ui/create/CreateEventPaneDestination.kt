package com.inwords.expenses.feature.events.ui.create

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
object CreateEventPaneDestination : Destination

internal fun EventsComponent.getCreateEventPaneNavModule(
    navigationController: NavigationController,
): NavModule {
    return NavModule(CreateEventPaneDestination.serializer()) {
        entry<CreateEventPaneDestination> {
            val viewModel = viewModel<CreateEventViewModel>(factory = viewModelFactory {
                initializer {
                    CreateEventViewModel(
                        navigationController = navigationController,
                        eventCreationStateHolder = eventCreationStateHolder.value,
                        getCurrenciesUseCase = getCurrenciesUseCaseLazy.value,
                    )
                }
            })
            CreateEventPane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onEventNameChanged = viewModel::onEventNameChanged,
                onCurrencyClicked = viewModel::onCurrencyClicked,
                onConfirmClicked = viewModel::onConfirmClicked,
                onNavIconClicked = viewModel::onNavIconClicked,
            )
        }
    }
}
