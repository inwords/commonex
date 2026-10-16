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
import com.inwords.expenses.feature.events.domain.model.Event
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState.Companion.canShare
import com.inwords.expenses.feature.share.api.ShareManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

internal class InviteEventViewModel(
    private val navigationController: NavigationController,
    getCurrentEventStateUseCase: GetCurrentEventStateUseCase,
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

    private val shareState = MutableStateFlow<ShareState>(ShareState.Idle(serverId = null, pinCode = ""))
    private var shareJob: Job? = null

    val state: StateFlow<EventSharingUiModel> = run {
        var lastEvent: Event? = null

        combine(
            getCurrentEventStateUseCase.currentEvent
                .onEach { details ->
                    val event = details?.event
                    if (lastEvent != event) {
                        lastEvent = event
                        shareJob?.cancel()
                        shareState.value = ShareState.Idle(serverId = event?.serverId, pinCode = event?.pinCode.orEmpty())
                    }
                },
            shareState,
        ) { details, shareState ->
            EventSharingUiModel(eventName = details?.event?.name.orEmpty(), shareState = shareState)
        }
    }.stateInWhileSubscribed(
        scope = viewModelScope + UI,
        initialValue = EventSharingUiModel(eventName = "", shareState = shareState.value),
    )

    fun onBackClick() {
        navigationController.popBackStack()
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

    private fun shareEvent(copyToClipboard: Boolean) {
        if (shareJob?.isActive == true) return

        val state = state.value
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
