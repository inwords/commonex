package com.inwords.expenses.feature.menu.ui

import com.inwords.expenses.core.ui.utils.StringProvider
import com.inwords.expenses.core.ui.utils.formatLocalDate
import com.inwords.expenses.core.ui.utils.getFullDateFormat
import com.inwords.expenses.feature.events.domain.CreateEventInvitationUseCase
import com.inwords.expenses.feature.events.domain.model.EventInvitation
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareState
import com.inwords.expenses.feature.menu.ui.EventSharingUiModel.ShareText
import expenses.shared.feature.menu.generated.resources.Res
import expenses.shared.feature.menu.generated.resources.menu_share_fallback_message
import expenses.shared.feature.menu.generated.resources.menu_share_secure_message

internal class EventShareTextProvider(
    createEventInvitationUseCaseLazy: Lazy<CreateEventInvitationUseCase>,
    private val stringProvider: StringProvider,
) {

    private val createEventInvitationUseCase by createEventInvitationUseCaseLazy

    suspend fun getOrCreateShareTextForState(eventName: String, shareState: ShareState): ShareText? {
        return when (shareState) {
            is ShareState.Idle -> {
                val serverId = shareState.serverId ?: return null
                createShareText(
                    eventName = eventName,
                    invitation = createEventInvitationUseCase.createInvitation(
                        eventServerId = serverId,
                        pinCode = shareState.pinCode,
                    ),
                )
            }

            is ShareState.Ready -> shareState.shareText
            is ShareState.PendingClipboardCopy -> shareState.shareText

            ShareState.Loading -> null
        }
    }

    private suspend fun createShareText(eventName: String, invitation: EventInvitation): ShareText {
        val expiresAt = invitation.expiresAt
        val fullText = if (expiresAt == null) {
            stringProvider.getString(Res.string.menu_share_fallback_message, eventName, invitation.url)
        } else {
            stringProvider.getString(
                Res.string.menu_share_secure_message,
                eventName,
                invitation.url,
                expiresAt.formatLocalDate(getFullDateFormat()),
            )
        }
        return ShareText(eventName = eventName, fullText = fullText)
    }
}
