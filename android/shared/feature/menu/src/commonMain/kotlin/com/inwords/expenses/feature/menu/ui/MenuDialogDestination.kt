package com.inwords.expenses.feature.menu.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inwords.expenses.core.navigation.BottomSheetSceneStrategy.Companion.bottomSheet
import com.inwords.expenses.core.navigation.Destination
import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.feature.menu.api.MenuComponent
import kotlinx.serialization.Serializable

@Serializable
object MenuDialogDestination : Destination

@OptIn(ExperimentalMaterial3Api::class)
internal fun MenuComponent.getMenuDialogNavModule(
    navigationController: NavigationController,
): NavModule {
    return NavModule(MenuDialogDestination.serializer()) {
        entry<MenuDialogDestination>(metadata = bottomSheet()) {
            val viewModel = viewModel<MenuViewModel>(factory = viewModelFactory {
                initializer {
                    MenuViewModel(
                        navigationController = navigationController,
                        getCurrentEventStateUseCase = getCurrentEventStateUseCaseLazy.value,
                        leaveEventUseCase = leaveEventUseCaseLazy.value,
                        shareManagerLazy = shareManagerLazy,
                        createEventInvitationUseCaseLazy = createEventInvitationUseCaseLazy,
                    )
                }
            })
            MenuDialog(
                state = viewModel.state.collectAsStateWithLifecycle().value,
                onJoinEventClicked = viewModel::onJoinEventClicked,
                onLeaveEventClicked = viewModel::onLeaveEventClicked,
                onChoosePersonClicked = viewModel::onChoosePersonClicked,
                onAddParticipantsClicked = viewModel::onAddParticipantClicked,
                onShareClicked = viewModel::onShareClicked,
                onCopyClicked = viewModel::onCopyClicked,
                onTextCopied = viewModel::onTextCopied,
                onPrivacyPolicyClicked = viewModel::onPrivacyPolicyClicked,
                onTermsOfUseClicked = viewModel::onTermsOfUseClicked,
            )
        }
    }
}
