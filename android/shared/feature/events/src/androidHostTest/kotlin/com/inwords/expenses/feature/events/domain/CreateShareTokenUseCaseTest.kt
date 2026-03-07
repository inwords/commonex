package com.inwords.expenses.feature.events.domain

import com.inwords.expenses.feature.events.domain.model.EventShareToken
import com.inwords.expenses.feature.events.domain.store.remote.EventsRemoteStore
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Instant

internal class CreateShareTokenUseCaseTest {

    private val eventsRemoteStore = mockk<EventsRemoteStore>(relaxed = true)
    private val useCase = CreateShareTokenUseCase(lazy { eventsRemoteStore })

    private val token = EventShareToken(
        token = "share-token-123",
        expiresAt = Instant.fromEpochMilliseconds(0),
    )

    @Test
    fun createShareToken_whenRemoteReturnsToken_returnsCreated() = runTest {
        coEvery {
            eventsRemoteStore.createEventShareToken("ev-1", "1234")
        } returns CreateShareTokenUseCase.CreateShareTokenResult.Created(token)

        val result = useCase.createShareToken("ev-1", "1234")

        val created = assertIs<CreateShareTokenUseCase.CreateShareTokenResult.Created>(result)
        assert(created.token.token == "share-token-123")
    }

    @Test
    fun createShareToken_whenRemoteFails_returnsRemoteFailed() = runTest {
        coEvery {
            eventsRemoteStore.createEventShareToken(any(), any())
        } returns CreateShareTokenUseCase.CreateShareTokenResult.RemoteFailed

        val result = useCase.createShareToken("ev-1", "1234")

        val _ = assertIs<CreateShareTokenUseCase.CreateShareTokenResult.RemoteFailed>(result)
    }
}
