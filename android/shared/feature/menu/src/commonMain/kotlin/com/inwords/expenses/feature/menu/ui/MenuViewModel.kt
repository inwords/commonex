package com.inwords.expenses.feature.menu.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inwords.expenses.core.navigation.NavigationController
import com.inwords.expenses.core.ui.utils.DefaultStringProvider
import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.core.ui.utils.formatLocalDate
import com.inwords.expenses.core.ui.utils.getFullDateFormat
import com.inwords.expenses.core.utils.IO
import com.inwords.expenses.core.utils.UI
import com.inwords.expenses.core.utils.stateInWhileSubscribed
import com.inwords.expenses.feature.events.domain.CreateShareTokenUseCase
import com.inwords.expenses.feature.events.domain.CreateShareTokenUseCase.CreateShareTokenResult
import com.inwords.expenses.feature.events.domain.GetCurrentEventStateUseCase
import com.inwords.expenses.feature.events.domain.LeaveEventUseCase
import com.inwords.expenses.feature.events.ui.add_participants.AddParticipantsToEventPaneDestination
import com.inwords.expenses.feature.events.ui.choose_person.ChoosePersonPaneDestination
import com.inwords.expenses.feature.events.ui.join.JoinEventPaneDestination
import com.inwords.expenses.feature.menu.ui.MenuDialogUiModel.ShareState
import com.inwords.expenses.feature.menu.ui.MenuDialogUiModel.ShareText
import com.inwords.expenses.feature.share.api.ShareManager
import expenses.shared.feature.menu.generated.resources.Res
import expenses.shared.feature.menu.generated.resources.menu_share_fallback_message
import expenses.shared.feature.menu.generated.resources.menu_share_secure_message
import kotlinx.coroutines.CoroutineScope
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
    createShareTokenUseCaseLazy: Lazy<CreateShareTokenUseCase>,
    private val stringProvider: StringProvider = DefaultStringProvider,
    viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + IO),
) : ViewModel(viewModelScope = viewModelScope) {

    private val shareManager by shareManagerLazy
    private val createShareTokenUseCase by createShareTokenUseCaseLazy

    private val emptyState = MenuDialogUiModel(
        eventName = "",
        shareState = ShareState.Idle(serverId = null, pinCode = "")
    )

    private val shareState = MutableStateFlow(emptyState.shareState)

    private var leaveEventJob: Job? = null
    private var shareJob: Job? = null

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
                eventName = event.event.name,
                shareState = shareState,
            )
        }
    }.stateInWhileSubscribed(scope = viewModelScope, initialValue = emptyState)

    fun onJoinEventClicked() {
        navigationController.popBackStack()
        navigationController.navigateTo(destination = JoinEventPaneDestination())
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
        if (shareJob != null) return

        val state = state.value
        val eventName = state.eventName

        shareState.value = ShareState.Loading

        shareJob = viewModelScope.launch {
            val shareText = getOrCreateShareTextForState(eventName, state.shareState) ?: return@launch

            withContext(UI) {
                shareManager.shareText(subject = shareText.eventName, fullText = shareText.fullText)
            }

            shareState.value = ShareState.Ready(shareText)
        }.apply {
            invokeOnCompletion { shareJob = null }
        }
    }

    fun onCopyClicked() {
        if (shareJob != null) return

        val state = state.value
        val eventName = state.eventName

        shareState.value = ShareState.Loading

        shareJob = viewModelScope.launch {
            val shareText = getOrCreateShareTextForState(eventName, state.shareState) ?: return@launch

            shareState.value = ShareState.PendingClipboardCopy(shareText)
        }.apply {
            invokeOnCompletion { shareJob = null }
        }
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

    private suspend fun getOrCreateShareTextForState(
        eventName: String,
        shareState: ShareState,
    ): ShareText? {
        return when (shareState) {
            is ShareState.Idle -> {
                shareState.serverId ?: return null
                createShareTokenAndGenerateShareText(eventName = eventName, serverId = shareState.serverId, pinCode = shareState.pinCode)
            }

            is ShareState.Ready -> shareState.shareText
            is ShareState.PendingClipboardCopy -> shareState.shareText

            ShareState.Loading -> null
        }
    }

    private suspend fun createShareTokenAndGenerateShareText(eventName: String, serverId: String, pinCode: String): ShareText {
        val fullText = when (val tokenResult = createShareTokenUseCase.createShareToken(serverId, pinCode)) {
            is CreateShareTokenResult.Created -> {
                val token = tokenResult.token.token
                val shareUrl = "https://commonex.ru/event/$serverId?token=$token"
                val expiresDate = tokenResult.token.expiresAt.formatLocalDate(getFullDateFormat())

                stringProvider.getString(Res.string.menu_share_secure_message, eventName, shareUrl, expiresDate)
            }

            is CreateShareTokenResult.RemoteFailed -> {
                val shareUrl = "https://commonex.ru/event/$serverId?pinCode=$pinCode"

                stringProvider.getString(Res.string.menu_share_fallback_message, eventName, shareUrl)
            }
        }
        return ShareText(eventName = eventName, fullText = fullText)
    }

}