package com.inwords.expenses.feature.menu.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.menu.api.MenuComponent
import kotlinx.serialization.Serializable

@Serializable
object InviteEventPaneDestination : Destination

internal fun MenuComponent.getInviteEventNavModule(navigationController: NavigationController): NavModule {
    return NavModule(InviteEventPaneDestination.serializer()) {
        entry<InviteEventPaneDestination> {
            val viewModel = viewModel<InviteEventViewModel>(factory = viewModelFactory {
                initializer {
                    InviteEventViewModel(
                        navigationController = navigationController,
                        getCurrentEventStateUseCase = getCurrentEventStateUseCaseLazy.value,
                        shareManagerLazy = shareManagerLazy,
                        createEventInvitationUseCaseLazy = createEventInvitationUseCaseLazy,
                    )
                }
            })
            InviteEventPane(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onBackClick = viewModel::onBackClick,
                onShareClick = viewModel::onShareClicked,
                onCopyClick = viewModel::onCopyClicked,
                onTextCopied = viewModel::onTextCopied,
            )
        }
    }
}
