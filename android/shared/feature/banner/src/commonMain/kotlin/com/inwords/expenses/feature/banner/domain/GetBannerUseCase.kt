package com.inwords.expenses.feature.banner.domain

import com.inwords.expenses.core.utils.IoResult
import com.inwords.expenses.feature.settings.api.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Instant

class GetBannerUseCase internal constructor(
    private val bannerRepository: BannerRepository,
    settingsRepositoryLazy: Lazy<SettingsRepository>,
    private val nowProvider: () -> Instant = { Clock.System.now() },
) {

    private val settingsRepository by settingsRepositoryLazy

    suspend fun getBanner(): Banner? {
        val banner = when (val result = bannerRepository.getBanner()) {
            is IoResult.Success -> result.data.banner
            is IoResult.Error -> null
        } ?: return null

        val dismissedUntil = settingsRepository.getBannerDismissals().first()[banner.dismissalKey]
        return banner.takeUnless { dismissedUntil != null && dismissedUntil > nowProvider().toEpochMilliseconds() }
    }
}
