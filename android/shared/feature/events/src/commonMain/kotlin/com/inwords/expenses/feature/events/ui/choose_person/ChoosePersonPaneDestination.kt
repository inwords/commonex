package com.inwords.expenses.feature.events.ui.choose_person

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
data object ChoosePersonPaneDestination : Destination

internal fun EventsComponent.getChoosePersonPaneNavModule(
    navigationController: NavigationController,
    expensesScreenDestination: Destination,
): NavModule {
    return NavModule(ChoosePersonPaneDestination.serializer()) {
        entry<ChoosePersonPaneDestination> {
            val viewModel = viewModel<ChoosePersonViewModel>(factory = viewModelFactory {
                initializer {
                    ChoosePersonViewModel(
                        navigationController = navigationController,
                        getCurrentEventStateUseCase = getCurrentEventStateUseCaseLazy.value,
                        settingsRepository = settingsRepositoryLazy.value,
                        expensesScreenDestination = expensesScreenDestination,
                    )
                }
            })
            ChoosePersonPane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onPersonSelected = viewModel::onPersonSelected,
                onNavIconClicked = viewModel::onNavIconClicked,
            )
        }
    }
}
