package com.inwords.expenses.feature.banner.domain

import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.settings.api.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

internal class BannerUseCasesTest {

    @Test
    fun `an eligible message preserves its type and text`() = runTest {
        val repository = FakeBannerRepository(warning)
        val useCase = GetBannerUseCase(repository, lazyOf(FakeSettingsRepository()), nowProvider = { now })

        assertEquals(warning, useCase.getBanner())
    }

    @Test
    fun `empty responses and request failures return no banner`() = runTest {
        for (result in listOf(IoResult.Success(BannerResult(null)), IoResult.Error.Retry, IoResult.Error.Failure)) {
            val repository = FakeBannerRepository().apply { this.result = result }
            val useCase = GetBannerUseCase(repository, lazyOf(FakeSettingsRepository()), nowProvider = { now })

            assertNull(useCase.getBanner())
        }
    }

    @Test
    fun `acknowledging a warning keeps it hidden across new use cases without losing other dismissals`() = runTest {
        val settings = FakeSettingsRepository(mapOf("UpdateRequired:Earlier update" to 100L))
        val dismissUseCase = DismissBannerUseCase(lazyOf(settings), nowProvider = { now })
        dismissUseCase.dismissBanner(warning)

        val getUseCase = GetBannerUseCase(FakeBannerRepository(warning), lazyOf(settings), nowProvider = { now })
        assertNull(getUseCase.getBanner())
        assertEquals(
            mapOf("UpdateRequired:Earlier update" to 100L, "GeneralWarning:Warning" to Long.MAX_VALUE),
            settings.dismissals.value,
        )
    }

    @Test
    fun `a changed message or type is not hidden by an earlier acknowledgement`() = runTest {
        val settings = FakeSettingsRepository(mapOf("GeneralWarning:Warning" to Long.MAX_VALUE))

        for (banner in listOf(warning.copy(text = "New warning"), warning.copy(type = BannerType.UpdateRequired))) {
            val useCase = GetBannerUseCase(FakeBannerRepository(banner), lazyOf(settings), nowProvider = { now })

            assertEquals(banner, useCase.getBanner())
        }
    }

    @Test
    fun `dismissing an update snoozes that message until exactly 24 hours later`() = runTest {
        val settings = FakeSettingsRepository()
        val dismissUseCase = DismissBannerUseCase(lazyOf(settings), nowProvider = { now })
        dismissUseCase.dismissBanner(updateRequired)

        val deadline = now + 24.hours
        assertEquals(mapOf("UpdateRequired:Update CommonEx" to deadline.toEpochMilliseconds()), settings.dismissals.value)

        val beforeExpiry = GetBannerUseCase(FakeBannerRepository(updateRequired), lazyOf(settings)) { deadline - 1.milliseconds }
        assertNull(beforeExpiry.getBanner())

        val atExpiry = GetBannerUseCase(FakeBannerRepository(updateRequired), lazyOf(settings)) { deadline }
        assertEquals(updateRequired, atExpiry.getBanner())
    }

    @Test
    fun `a changed update message appears while an earlier update is snoozed`() = runTest {
        val settings = FakeSettingsRepository(mapOf("UpdateRequired:Update CommonEx" to (now + 24.hours).toEpochMilliseconds()))
        val changed = updateRequired.copy(text = "New update notice")
        val useCase = GetBannerUseCase(FakeBannerRepository(changed), lazyOf(settings), nowProvider = { now })

        assertEquals(changed, useCase.getBanner())
    }

    private val warning = Banner(BannerType.GeneralWarning, "Warning")
    private val updateRequired = Banner(BannerType.UpdateRequired, "Update CommonEx")
    private val now = Instant.parse("2026-10-25T12:00:00Z")

    private class FakeBannerRepository(banner: Banner? = null) : BannerRepository {
        var result: IoResult<BannerResult> = IoResult.Success(BannerResult(banner))

        override suspend fun getBanner(): IoResult<BannerResult> = result
    }

    private class FakeSettingsRepository(initialDismissals: Map<String, Long> = emptyMap()) : SettingsRepository {
        val dismissals = MutableStateFlow(initialDismissals)

        override fun getBannerDismissals(): Flow<Map<String, Long>> = dismissals

        override suspend fun setBannerDismissal(key: String, untilEpochMillis: Long) {
            dismissals.value += key to untilEpochMillis
        }

        override suspend fun setCurrentEventAndPerson(eventId: Long, personId: Long) = Unit
        override suspend fun setCurrentEventId(eventId: Long) = Unit
        override fun getCurrentEventId(): Flow<Long?> = flowOf(null)
        override suspend fun setCurrentPersonId(userId: Long) = Unit
        override fun getCurrentPersonId(): Flow<Long?> = flowOf(null)
        override suspend fun clearCurrentEventAndPerson() = Unit
    }
}
