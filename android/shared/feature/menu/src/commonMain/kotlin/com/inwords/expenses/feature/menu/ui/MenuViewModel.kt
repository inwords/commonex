package com.inwords.expenses.feature.menu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.DefaultStringProvider
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.UI
import com.inwords.expenses.core.utils.stateInWhileSubscribed
import com.inwords.expenses.feature.events.domain.CreateEventInvitationUseCase
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.LeaveEventUseCase
import com.inwords.expenses.feature.events.ui.add_participants.AddParticipantsToEventPaneDestination
import com.inwords.expenses.feature.events.ui.choose_person.ChoosePersonPaneDestination
import com.inwords.expenses.feature.events.ui.join_invitation.JoinInvitationPaneDestination
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState.Companion.canShare
import com.inwords.expenses.feature.share.api.ShareManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MenuViewModel(
    private val navigationController: NavigationController,
    getCurrentEventStateUseCase: GetCurrentEventStateUseCase,
    private val leaveEventUseCase: LeaveEventUseCase,
    shareManagerLazy: Lazy<ShareManager>,
    createEventInvitationUseCaseLazy: Lazy<CreateEventInvitationUseCase>,
    stringProvider: StringProvider = DefaultStringProvider,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    private val shareManager by shareManagerLazy

    private val shareTextProvider = EventShareTextProvider(
        createEventInvitationUseCaseLazy = createEventInvitationUseCaseLazy,
        stringProvider = stringProvider,
    )

    private val emptyState = MenuDialogUiModel(
        eventSharing = EventSharingUiModel(
            eventName = "",
            shareState = ShareState.Idle(serverId = null, pinCode = "")
        ),
    )

    private val shareState = MutableStateFlow(emptyState.eventSharing.shareState)

    private var leaveEventJob: Job? = null
    private var shareJob: Job? = null

    @OptIn(FlowPreview::class)
    val state: StateFlow<MenuDialogUiModel> = run {
        var lastEventServerId: String? = null

        combine(
            getCurrentEventStateUseCase.currentEvent
                .onEach { event ->
                    val serverId = event?.event?.serverId
                    if (lastEventServerId != serverId) {
                        // Event changed, reset share state
                        lastEventServerId = serverId

                        this.shareState.value = ShareState.Idle(
                            serverId = serverId,
                            pinCode = event?.event?.pinCode.orEmpty(),
                        )
                    }
                },
            shareState
                .debounce { state ->
                    // Debounce only when loading to avoid multiple rapid clicks
                    if (state is ShareState.Loading) 100 else 0
                }
        ) { event, shareState ->
            event ?: return@combine emptyState

            MenuDialogUiModel(
                eventSharing = EventSharingUiModel(
                    eventName = event.event.name,
                    shareState = shareState
                ),
            )
        }
    }.stateInWhileSubscribed(scope = viewModelScope, initialValue = emptyState)

    fun onJoinEventClicked() {
        navigationController.popBackStack()
        navigationController.navigateTo(destination = JoinInvitationPaneDestination)
    }

    fun onLeaveEventClicked() {
        if (leaveEventJob != null) return
        leaveEventJob = viewModelScope.launch {
            leaveEventUseCase.leaveEvent()
            navigationController.popBackStack()
            leaveEventJob = null
        }
    }

    fun onChoosePersonClicked() {
        navigationController.popBackStack()
        navigationController.navigateTo(ChoosePersonPaneDestination)
    }

    fun onAddParticipantClicked() {
        navigationController.popBackStack()
        navigationController.navigateTo(AddParticipantsToEventPaneDestination)
    }

    fun onShareClicked() {
        shareEvent(copyToClipboard = false)
    }

    fun onCopyClicked() {
        shareEvent(copyToClipboard = true)
    }

    fun onTextCopied() {
        shareState.update { currentState ->
            if (currentState is ShareState.PendingClipboardCopy) {
                ShareState.Ready(currentState.shareText)
            } else {
                currentState
            }
        }
    }

    fun onPrivacyPolicyClicked() {
        // URL opening is handled in the composable using openUriSafe
        navigationController.popBackStack()
    }

    fun onTermsOfUseClicked() {
        // URL opening is handled in the composable using openUriSafe
        navigationController.popBackStack()
    }

    private fun shareEvent(copyToClipboard: Boolean) {
        if (shareJob?.isActive == true) return

        val state = state.value.eventSharing
        if (!state.shareState.canShare) return

        shareState.value = ShareState.Loading
        shareJob = viewModelScope.launch {
            val shareText = shareTextProvider.getOrCreateShareTextForState(state.eventName, state.shareState) ?: return@launch

            if (copyToClipboard) {
                shareState.value = ShareState.PendingClipboardCopy(shareText)
            } else {
                withContext(UI) {
                    shareManager.shareText(subject = shareText.eventName, fullText = shareText.fullText)
                }
                shareState.value = ShareState.Ready(shareText)
            }
        }
    }

}
