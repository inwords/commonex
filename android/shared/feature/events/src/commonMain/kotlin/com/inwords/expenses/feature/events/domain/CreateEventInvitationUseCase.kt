package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.feature.events.domain.CreateShareTokenUseCase.CreateShareTokenResult
import com.inwords.expenses.feature.events.domain.model.EventInvitation

class CreateEventInvitationUseCase internal constructor(
    createShareTokenUseCaseLazy: Lazy<CreateShareTokenUseCase>,
) {

    private val createShareTokenUseCase by createShareTokenUseCaseLazy

    suspend fun createInvitation(eventServerId: String, pinCode: String): EventInvitation {
        return when (val result = createShareTokenUseCase.createShareToken(eventServerId, pinCode)) {
            is CreateShareTokenResult.Created -> EventInvitation(
                url = "https://commonex.ru/event/$eventServerId?token=${result.token.token}",
                expiresAt = result.token.expiresAt,
            )

            CreateShareTokenResult.RemoteFailed -> EventInvitation(
                url = "https://commonex.ru/event/$eventServerId?pinCode=$pinCode",
                expiresAt = null,
            )
        }
    }
}
