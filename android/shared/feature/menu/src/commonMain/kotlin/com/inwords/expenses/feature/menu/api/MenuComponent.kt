package com.inwords.expenses.feature.menu.api

import com.inwords.expenses.core.navigation.NavModule
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.utils.Component
import com.inwords.expenses.feature.events.domain.CreateEventInvitationUseCase
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.LeaveEventUseCase
import com.inwords.expenses.feature.menu.ui.getInviteEventNavModule
import com.inwords.expenses.feature.menu.ui.getMenuDialogNavModule
import com.inwords.expenses.feature.share.api.ShareManager

class MenuComponent(private val deps: Deps) : Component {

    interface Deps {

        val getCurrentEventStateUseCaseLazy: Lazy<GetCurrentEventStateUseCase>
        val leaveEventUseCaseLazy: Lazy<LeaveEventUseCase>
        val createEventInvitationUseCaseLazy: Lazy<CreateEventInvitationUseCase>

        val shareManagerLazy: Lazy<ShareManager>
    }

    internal val getCurrentEventStateUseCaseLazy get() = deps.getCurrentEventStateUseCaseLazy
    internal val leaveEventUseCaseLazy get() = deps.leaveEventUseCaseLazy
    internal val shareManagerLazy get() = deps.shareManagerLazy
    internal val createEventInvitationUseCaseLazy get() = deps.createEventInvitationUseCaseLazy

    fun getNavModules(navigationController: NavigationController): List<NavModule> {
        return listOf(
            getMenuDialogNavModule(navigationController),
            getInviteEventNavModule(navigationController),
        )
    }
}
