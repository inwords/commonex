package com.inwords.expenses.feature.events.ui.join_invitation

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
data object JoinInvitationPaneDestination : Destination

@Suppress("UnusedReceiverParameter")
internal fun EventsComponent.getJoinInvitationPaneNavModule(
    navigationController: NavigationController,
): NavModule {
    return NavModule(JoinInvitationPaneDestination.serializer()) {
        entry<JoinInvitationPaneDestination> {
            val viewModel = viewModel<JoinInvitationViewModel>(factory = viewModelFactory {
                initializer {
                    JoinInvitationViewModel(navigationController = navigationController)
                }
            })
            JoinInvitationPane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onInvitationChanged = viewModel::onInvitationChanged,
                onConfirmClicked = viewModel::onConfirmClicked,
                onEnterCodeClicked = viewModel::onEnterCodeClicked,
                onNavIconClicked = viewModel::onNavIconClicked,
            )
        }
    }
}
