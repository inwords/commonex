package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.feature.events.domain.CreateShareTokenUseCase.CreateShareTokenResult
import com.inwords.expenses.feature.events.domain.model.EventInvitation
import com.inwords.expenses.feature.events.domain.model.EventShareToken
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

internal class CreateEventInvitationUseCaseTest {

    private val createShareTokenUseCase = mockk<CreateShareTokenUseCase>()
    private val useCase = CreateEventInvitationUseCase(
        createShareTokenUseCaseLazy = lazyOf(createShareTokenUseCase),
    )

    @Test
    fun createInvitation_whenTokenCreated_returnsSecureLinkAndExpiry() = runTest {
        val token = EventShareToken(
            token = "share123",
            expiresAt = Instant.fromEpochMilliseconds(1_000),
        )
        coEvery { createShareTokenUseCase.createShareToken("01ABC", "1234") } returns CreateShareTokenResult.Created(token)

        val result = useCase.createInvitation(eventServerId = "01ABC", pinCode = "1234")

        assertEquals(
            EventInvitation(url = "https://commonex.ru/event/01ABC?token=share123", expiresAt = token.expiresAt),
            result,
        )
        coVerify(exactly = 1) { createShareTokenUseCase.createShareToken("01ABC", "1234") }
    }

    @Test
    fun createInvitation_whenTokenCreationFails_returnsPinLinkWithoutExpiry() = runTest {
        coEvery { createShareTokenUseCase.createShareToken("01ABC", "1234") } returns CreateShareTokenResult.RemoteFailed

        val result = useCase.createInvitation(eventServerId = "01ABC", pinCode = "1234")

        assertEquals(
            EventInvitation(url = "https://commonex.ru/event/01ABC?pinCode=1234", expiresAt = null),
            result,
        )
        coVerify(exactly = 1) { createShareTokenUseCase.createShareToken("01ABC", "1234") }
    }
}
