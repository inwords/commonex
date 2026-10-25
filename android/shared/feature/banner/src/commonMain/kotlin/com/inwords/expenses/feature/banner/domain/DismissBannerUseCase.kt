package com.inwords.expenses.feature.banner.domain

import com.inwords.expenses.feature.settings.api.SettingsRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class DismissBannerUseCase internal constructor(
    settingsRepositoryLazy: Lazy<SettingsRepository>,
    private val nowProvider: () -> Instant = { Clock.System.now() },
) {

    private val settingsRepository by settingsRepositoryLazy

    suspend fun dismissBanner(banner: Banner) {
        settingsRepository.setBannerDismissal(
            key = banner.dismissalKey,
            untilEpochMillis = when (banner.type) {
                BannerType.GeneralWarning -> Long.MAX_VALUE
                BannerType.UpdateRequired -> (nowProvider() + 24.hours).toEpochMilliseconds()
            },
        )
    }
}
